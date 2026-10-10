// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.hud;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.ability.WarlockAbilities;
import dev.raskol.classes.attribute.AttributeMath;
import dev.raskol.classes.attribute.AttributeService;
import dev.raskol.classes.attribute.AttributeType;
import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.config.RaskolConfig;
import dev.raskol.classes.event.CustomHealEvent;
import dev.raskol.classes.resource.ResourceState;
import dev.raskol.classes.storage.SafeStorage;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * 1.9.3-r: математика единиц HP живёт в AttributeService; здесь HUD, реген, персист.
 * 1.14.1: heal(target, amount, healer) + CustomHealEvent; saveAll() для onDisable.
 * 1.14.3 (3B): кэш дельты max-health-модификатора (нет сетевого спама).
 * 1.14.4-fix2: ОТЛОЖЕННОЕ восстановление HP на join (pendingRestore), т.к. LuckPerms
 *         отдаёт класс позже PlayerJoinEvent; иначе formula=100 и HP съедались релогом.
 * 1.14.6-fix (Спринт 1, P0-8d): onQuit НЕ перезаписывает health.yml, если pendingRestore
 *         ещё не применился (applies==0): ранее релог в первые ~5 с писал в файл
 *         неподтверждённый (клампнутый к 100) ratio и портил сохранённое здоровье.
 * 1.14.7 (Спринт 4, P1-7): дисковый IO health.yml переведён на dirty-flush.
 *         saveHealth() обновляет in-memory healthStore и помечает игрока dirty;
 *         flushDirty() пишет файл один раз при наличии dirty — периодически
 *         (storage.health-flush-seconds, дефолт 60) и при onDisable (saveAll).
 *         onQuit с применённым restore делает одиночный flush для надёжности
 *         против краша; onQuit до restore (applies==0) по-прежнему не пишет файл.
 * 1.14.7 (Спринт 4, P1-8): шкала ресурса в unified-actionbar нормируется на
 *         ResourceState.getEffectiveMax(), а не на константу MAX_VALUE=100.
 *         Текст рядом с баром показывает «res/effectiveMax» вместо «res/100»;
 *         gradientBar клампует fraction в [0,1], поэтому ресурс выше 100
 *         (через узлы resource_max + setCeiling) корректно растягивает бар.
 */
public final class HpBarService implements Listener {

    private static final Logger LOGGER = Logger.getLogger("RaskolClasses");
    private static final double LEGACY_MAX_VALUE = 100.0;
    private static final long RESTORE_TIMEOUT_MS = 5_000L;
    private static final long REAPPLY_WINDOW_MS = 3_000L;
    private static final int REAPPLY_MAX = 3;
    private static final int DEFAULT_FLUSH_SECONDS = 60;

    private record State(double lastHp, double lastRes) {
    }

    private record Pending(double ratio, long joinedAt, long lastApplyAt, int applies) {
    }

    private final RaskolClasses plugin;
    private final NamespacedKeyHolder maxHpKey;
    private final File healthFile;
    private final YamlConfiguration healthStore;

    private final Map<UUID, State> lastTick = new ConcurrentHashMap<>();
    private final Map<UUID, Double> lastAppliedDelta = new ConcurrentHashMap<>();
    private final Map<UUID, Pending> pendingRestore = new ConcurrentHashMap<>();

    /** 1.14.7 (P1-7): игроки, чьи HP-записи обновлены в памяти, но не записаны на диск. */
    private final Set<UUID> dirtySinceLastFlush = new HashSet<>();
    /** 1.14.7 (P1-7): есть хотя бы один dirty — нужно вызвать SafeStorage.saveAtomic. */
    private volatile boolean storeDirty = false;

    private long tickCounter = 0L;
    private long lastFlushAt = System.currentTimeMillis();

    private record NamespacedKeyHolder(org.bukkit.NamespacedKey key) {
    }

    public HpBarService(RaskolClasses plugin) {
        this.plugin = plugin;
        this.maxHpKey = new NamespacedKeyHolder(new org.bukkit.NamespacedKey(plugin, "max_hp"));
        this.healthFile = new File(plugin.getDataFolder(), "health.yml");
        this.healthStore = SafeStorage.loadWithFallback(healthFile, LOGGER);
        LOGGER.info("HpBarService: deferred-restore (1.14.4-fix2) + dirty-flush (1.14.7 P1-7) active");
    }

    private AttributeService attrs() {
        return plugin.getAttributes();
    }

    public double formulaMaxHp(UUID uuid) {
        return attrs().maxHp(uuid);
    }

    public double carrierMaxHp(Player player) {
        return attrs().carrierMaxHp(player);
    }

    public double scale(Player player) {
        return attrs().scale(player);
    }

    public double currentFormulaHp(Player player) {
        return attrs().currentFormulaHp(player);
    }

    public void heal(LivingEntity target, double formulaAmount, Player healer) {
        if (target instanceof Player p && WarlockAbilities.isAntihealed(p.getUniqueId())) {
            return;
        }
        CustomHealEvent event = new CustomHealEvent(healer, target, formulaAmount);
        plugin.getServer().getPluginManager().callEvent(event);
        if (event.isCancelled() || event.getAmount() <= 0.0) {
            return;
        }
        attrs().healFormula(target, event.getAmount());
    }

    public void heal(LivingEntity target, double formulaAmount) {
        heal(target, formulaAmount, null);
    }

    private String mode() {
        return plugin.getConfig().getString("hp-display.mode", "actionbar")
                .toLowerCase(Locale.ROOT);
    }

    private double heartsScale() {
        double v = plugin.getConfig().getDouble("hp-display.hearts-scale", 20.0);
        if (!Double.isFinite(v) || v <= 0.0) {
            return 20.0;
        }
        return Math.min(20.0, v);
    }

    private boolean gaugeEnabled() {
        return plugin.getConfig().getBoolean("hp-display.gauge", true);
    }

    private int gaugeLength() {
        return Math.max(4, Math.min(20, plugin.getConfig().getInt("hp-display.gauge-length", 10)));
    }

    private boolean gradientEnabled() {
        return plugin.getConfig().getBoolean("hp-display.gradient.enabled", true);
    }

    private boolean sparkEnabled() {
        return plugin.getConfig().getBoolean("hp-display.regen-spark.enabled", true);
    }

    private int period() {
        return Math.max(1, plugin.getConfig().getInt("hp-display.update-period-ticks", 10));
    }

    /** 1.14.7 (P1-7): интервал flush в миллисекундах (дефолт 60 с). */
    private long flushIntervalMillis() {
        int secs = plugin.getConfig().getInt("storage.health-flush-seconds", DEFAULT_FLUSH_SECONDS);
        if (secs <= 0) {
            secs = DEFAULT_FLUSH_SECONDS;
        }
        return secs * 1000L;
    }

    private TextColor color(String path, String fallback) {
        String hex = plugin.getConfig().getString(path, fallback);
        if (hex != null) {
            try {
                TextColor parsed = TextColor.fromHexString(hex);
                if (parsed != null) {
                    return parsed;
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
        return TextColor.fromHexString(fallback);
    }

    public BukkitTask start() {
        return plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, period(), period());
    }

    /**
     * 1.14.7 (P1-7): обновляет in-memory healthStore для всех онлайн-игроков
     * и выполняет один flush на диск. Вызывается из onDisable.
     */
    public void saveAll() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            saveHealth(player);
        }
        flushDirty();
    }

    private void tick() {
        tickCounter++;
        if (tickCounter % 600L == 0L) {
            long now = System.currentTimeMillis();
            pendingRestore.entrySet().removeIf(e -> now - e.getValue().joinedAt() > 60_000L);
        }
        boolean unified = !"vanilla".equals(mode());
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (player.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            UUID uuid = player.getUniqueId();
            applyMaxHealth(player);
            applyStrRegen(player);
            tickPendingRestore(player, uuid);
            applyHearts(player, unified);
            if (unified) {
                sendUnifiedActionbar(player);
            }
        }
        lastTick.keySet().removeIf(id -> plugin.getServer().getPlayer(id) == null);
        lastAppliedDelta.keySet().removeIf(id -> plugin.getServer().getPlayer(id) == null);
        pendingRestore.keySet().removeIf(id -> plugin.getServer().getPlayer(id) == null);
        dirtySinceLastFlush.removeIf(id -> plugin.getServer().getPlayer(id) == null);

        // 1.14.7 (P1-7): периодический flush dirty-игроков на диск.
        long now = System.currentTimeMillis();
        if (storeDirty && now - lastFlushAt >= flushIntervalMillis()) {
            flushDirty();
        }
    }

    private void tickPendingRestore(Player player, UUID uuid) {
        Pending p = pendingRestore.get(uuid);
        if (p == null) {
            return;
        }
        long now = System.currentTimeMillis();
        boolean classKnown = plugin.getClassProvider().getClassOf(player) != null;
        boolean timedOut = now - p.joinedAt() > RESTORE_TIMEOUT_MS;
        if (!classKnown && !timedOut) {
            return;
        }
        double formula = attrs().maxHp(uuid);
        if (formula <= 0.0) {
            return;
        }
        double target = Math.max(1.0, p.ratio() * formula);
        double current = attrs().currentFormulaHp(player);
        boolean firstApply = p.applies() == 0;
        boolean needsReapply = !firstApply
                && now - p.lastApplyAt() <= REAPPLY_WINDOW_MS
                && p.applies() < REAPPLY_MAX
                && target - current > 1.0;
        if (firstApply || needsReapply) {
            double hpVanilla = target * attrs().scale(player);
            double carrier = attrs().carrierMaxHp(player);
            player.setHealth(Math.min(hpVanilla, carrier));
            pendingRestore.put(uuid, new Pending(p.ratio(), p.joinedAt(), now, p.applies() + 1));
            if (firstApply) {
                LOGGER.info("[hp-restore] " + player.getName() + ": ratio="
                        + String.format(Locale.ROOT, "%.3f", p.ratio())
                        + " formula=" + (int) formula + " hp=" + (int) target
                        + (classKnown ? " (class ready)" : " (timeout)"));
            }
            return;
        }
        if (p.applies() > 0 && (Math.abs(target - current) <= 1.0
                || p.applies() >= REAPPLY_MAX
                || now - p.lastApplyAt() > REAPPLY_WINDOW_MS)) {
            pendingRestore.remove(uuid);
        }
    }

    private void applyMaxHealth(Player player) {
        if (AttributeService.maxHealthAttr() == null) {
            return;
        }
        AttributeInstance instance = player.getAttribute(AttributeService.maxHealthAttr());
        if (instance == null) {
            return;
        }
        double target = attrs().targetCarrier(player.getUniqueId());

        AttributeModifier ours = null;
        for (AttributeModifier m : instance.getModifiers()) {
            if (maxHpKey.key().equals(m.getKey())) {
                ours = m;
                break;
            }
        }
        double ourAmount = ours != null ? ours.getAmount() : 0.0;
        double othersValue = instance.getValue() - ourAmount;
        double delta = target - othersValue;

        Double cachedDelta = lastAppliedDelta.get(player.getUniqueId());
        boolean needsUpdate = ours == null || cachedDelta == null || Math.abs(cachedDelta - delta) > 0.01;

        if (needsUpdate) {
            if (ours != null) {
                instance.removeModifier(ours);
            }
            if (Math.abs(delta) > 0.01) {
                instance.addModifier(new AttributeModifier(
                        maxHpKey.key(), delta, AttributeModifier.Operation.ADD_NUMBER,
                        EquipmentSlotGroup.ANY));
            }
            lastAppliedDelta.put(player.getUniqueId(), delta);
        }
        double carrier = instance.getValue();
        if (player.getHealth() > carrier) {
            player.setHealth(carrier);
        }
    }

    /**
     * 1.14.7 (P1-7): in-memory обновление healthStore + dirty-mark.
     * Файл на диск НЕ пишется — flushDirty() сделает это один раз при следующем
     * периодическом flush или на saveAll/onQuit-с-applies.
     */
    private void saveHealth(Player player) {
        double formula = attrs().maxHp(player.getUniqueId());
        if (formula <= 0.0) {
            return;
        }
        double ratio = Math.max(0.0, Math.min(1.0, attrs().currentFormulaHp(player) / formula));
        healthStore.set(player.getUniqueId().toString(), ratio);
        dirtySinceLastFlush.add(player.getUniqueId());
        storeDirty = true;
    }

    /**
     * 1.14.7 (P1-7): одна атомарная запись файла на диск, если есть dirty-игроки.
     * Сбрасывает флаг и множество. Вызывается из tick (периодически),
     * saveAll (onDisable) и onQuit (одиночный игрок с применённым restore).
     */
    private void flushDirty() {
        if (!storeDirty) {
            return;
        }
        SafeStorage.saveAtomic(healthStore, healthFile, LOGGER);
        dirtySinceLastFlush.clear();
        storeDirty = false;
        lastFlushAt = System.currentTimeMillis();
    }

    private double readRatio(UUID uuid) {
        String key = uuid.toString();
        if (!healthStore.isSet(key)) {
            return 1.0;
        }
        return Math.max(0.0, Math.min(1.0, healthStore.getDouble(key, 1.0)));
    }

    private void applyStrRegen(Player player) {
        if (player.isDead()) {
            return;
        }
        UUID uuid = player.getUniqueId();
        double formula = attrs().maxHp(uuid);
        double hpFormula = attrs().currentFormulaHp(player);
        if (hpFormula >= formula) {
            return;
        }
        double str = attrs().value(uuid, AttributeType.STR);
        double perStr = plugin.getConfig().getDouble("attributes.hp.regen-per-str", 0.025);
        double combatFactor = plugin.getConfig().getDouble("attributes.hp.regen-combat-factor", 0.35);
        double capPct = plugin.getConfig().getDouble("attributes.hp.regen-cap-pct", 1.5);

        PlayerClass pc = plugin.getClassProvider().getClassOf(player);
        long windowMs = (pc != null
                ? plugin.getRaskolConfig().combatWindowSeconds(pc)
                : 5) * 1000L;
        boolean inCombat = plugin.getResources().stateOf(uuid).isInCombat(windowMs);

        double perSec = AttributeMath.strRegenPerSecond(
                str, perStr, formula, inCombat, combatFactor, capPct);
        if (perSec <= 0.0) {
            return;
        }
        double perTick = perSec * period() / 20.0;
        double newHpFormula = Math.min(formula, hpFormula + perTick);
        double newHpVanilla = newHpFormula * attrs().scale(player);
        double carrier = attrs().carrierMaxHp(player);
        if (newHpVanilla > player.getHealth()) {
            player.setHealth(Math.min(newHpVanilla, carrier));
        }
    }

    private void applyHearts(Player player, boolean unified) {
        if (!unified) {
            if (player.isHealthScaled()) {
                player.setHealthScaled(false);
            }
            return;
        }
        double scale = heartsScale();
        if (!player.isHealthScaled() || Math.abs(player.getHealthScale() - scale) > 0.001) {
            try {
                player.setHealthScaled(true);
                player.setHealthScale(scale);
            } catch (IllegalArgumentException e) {
                player.setHealthScaled(false);
            }
        }
    }

    /**
     * 1.14.7 (P1-8): шкала ресурса нормируется на ResourceState.getEffectiveMax(),
     * а не на константу MAX_VALUE=100. Текст рядом с баром показывает
     * «res/effectiveMax» — корректно растягивает бар для потолка > 100
     * (через узлы resource_max + setCeiling из spec2).
     */
    private void sendUnifiedActionbar(Player player) {
        UUID uuid = player.getUniqueId();
        double formula = attrs().maxHp(uuid);
        double hpFormula = attrs().currentFormulaHp(player);
        ResourceState state = plugin.getResources().stateOf(uuid);
        double res = state.getValue();
        double effectiveMax = state.getEffectiveMax();
        // Защита от деления на ноль: ResourceState инициализирует effectiveMax=100
        // и setCeiling игнорирует ≤ 0 / NaN — но на всякий случай.
        if (effectiveMax <= 0.0 || !Double.isFinite(effectiveMax)) {
            effectiveMax = LEGACY_MAX_VALUE;
        }
        PlayerClass pc = plugin.getClassProvider().getClassOf(player);
        int len = gaugeLength();
        boolean gauge = gaugeEnabled();
        double hpFraction = formula <= 0 ? 0 : hpFormula / formula;
        double resFraction = Math.max(0.0, Math.min(1.0, res / effectiveMax));

        TextColor frame = color("hp-display.colors.frame", "#8B5A3C");
        TextColor empty = color("hp-display.colors.gauge-empty", "#6E5232");
        TextColor hpEnd = color("hp-display.gradient.hp-end", "#C82020");
        TextColor numbers = color("hp-display.colors.numbers", "#D6CDBE");
        TextColor spark = color("hp-display.regen-spark.color", "#F5E6B8");
        TextColor hpStart = color("hp-display.gradient.hp-start", "#5C0F0F");

        RaskolConfig.ClassTheme theme = plugin.getRaskolConfig().themeOf(pc);
        TextColor resStart = theme != null && theme.primary() != null
                ? theme.primary()
                : color("hp-display.gradient.res-start", "#8B5A3C");
        TextColor resEnd = theme != null && theme.secondary() != null
                ? theme.secondary()
                : color("hp-display.gradient.res-end", "#D6A642");
        TextColor resSymbol = theme != null && theme.secondary() != null
                ? theme.secondary()
                : numbers;
        String symbol = symbolOf(pc);

        State prev = lastTick.get(uuid);
        boolean sparkActive = sparkEnabled() && prev != null;
        boolean hpRegen = sparkActive && hpFormula > prev.lastHp() + 0.5;
        boolean resRegen = sparkActive && res > prev.lastRes() + 0.5;
        lastTick.put(uuid, new State(hpFormula, res));

        Component line = Component.text("❬ ", frame)
                .append(Component.text("❤ ", hpEnd));
        if (gauge) {
            line = line.append(gradientBar(hpFraction, len,
                            gradientEnabled() ? hpStart : hpEnd, hpEnd, empty,
                            hpRegen ? spark : null))
                    .append(Component.text(" ", frame));
        }
        line = line.append(Component.text((int) hpFormula + "/" + (int) formula, numbers))
                .append(Component.text(" ❭ ❬ ", frame))
                .append(Component.text(symbol + " ", resSymbol));
        if (gauge) {
            line = line.append(gradientBar(resFraction, len,
                            gradientEnabled() ? resStart : resEnd, resEnd, empty,
                            resRegen ? spark : null))
                    .append(Component.text(" ", frame));
        }
        line = line.append(Component.text((int) res + "/" + (int) effectiveMax, numbers))
                .append(Component.text(" ❭", frame));
        player.sendActionBar(line);
    }

    private static Component gradientBar(double fraction, int len,
                                         TextColor start, TextColor end,
                                         TextColor empty, TextColor sparkColor) {
        double clamped = Math.max(0.0, Math.min(1.0, fraction));
        int filled = (int) Math.round(clamped * len);
        Component c = Component.empty();
        int lastFilledIndex = filled - 1;
        for (int i = 0; i < len; i++) {
            if (i < filled) {
                TextColor segColor;
                if (sparkColor != null && i == lastFilledIndex) {
                    segColor = sparkColor;
                } else if (len == 1) {
                    segColor = end;
                } else {
                    float t = (float) i / (float) (len - 1);
                    segColor = lerpRgb(t, start, end);
                }
                c = c.append(Component.text("▰", segColor));
            } else {
                c = c.append(Component.text("▱", empty));
            }
        }
        return c;
    }

    private static TextColor lerpRgb(float t, TextColor a, TextColor b) {
        float tt = Math.max(0f, Math.min(1f, t));
        int ar = (a.value() >> 16) & 0xFF, ag = (a.value() >> 8) & 0xFF, ab = a.value() & 0xFF;
        int br = (b.value() >> 16) & 0xFF, bg = (b.value() >> 8) & 0xFF, bb = b.value() & 0xFF;
        int r = Math.round(ar + (br - ar) * tt);
        int g = Math.round(ag + (bg - ag) * tt);
        int bl = Math.round(ab + (bb - ab) * tt);
        return TextColor.color(r, g, bl);
    }

    private String symbolOf(PlayerClass pc) {
        if (pc == null) {
            return "✦";
        }
        String s = plugin.getConfig().getString(
                "classes." + pc.name() + ".theme.symbol", "");
        if (s != null && !s.isEmpty()) {
            return s;
        }
        return switch (pc) {
            case WARRIOR -> "⚔";
            case HUNTER -> "➳";
            case PRIEST -> "✚";
            case MAGE -> "✦";
            case ROGUE -> "☠";
            case WARLOCK -> "☾";
        };
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        UUID uuid = player.getUniqueId();
        applyMaxHealth(player);
        pendingRestore.put(uuid, new Pending(readRatio(uuid), System.currentTimeMillis(), 0L, 0));
        applyHearts(player, !"vanilla".equals(mode()));
    }

    /**
     * 1.14.6-fix (P0-8d): если pendingRestore ещё не применился (applies==0),
     * здоровье в health.yml НЕ перезаписывается: текущее значение — артефакт
     * клампа к formula=100 до загрузки класса, а не реальное HP игрока.
     * Старый ratio из файла переживёт релог и применится при следующем входе.
     *
     * 1.14.7 (P1-7): при applies > 0 помечаем dirty и делаем одиночный flush —
     * это защищает от потери данных при внезапном краше сервера между
     * периодическими flush. Массовый выход (shutdown) идёт через saveAll() →
     * один flush на всех.
     */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        Pending p = pendingRestore.remove(uuid);
        if (p != null && p.applies() == 0) {
            LOGGER.info("[hp-restore] " + player.getName()
                    + ": выход до применения restore — health.yml не перезаписан (ratio="
                    + String.format(Locale.ROOT, "%.3f", p.ratio()) + ")");
            lastTick.remove(uuid);
            lastAppliedDelta.remove(uuid);
            return;
        }
        saveHealth(player);
        // 1.14.7 (P1-7): одиночный flush на диск при выходе игрока с применённым restore.
        flushDirty();
        lastTick.remove(uuid);
        lastAppliedDelta.remove(uuid);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && player.getGameMode() != GameMode.SPECTATOR) {
                pendingRestore.remove(player.getUniqueId());
                applyMaxHealth(player);
                player.setHealth(attrs().carrierMaxHp(player));
                applyHearts(player, !"vanilla".equals(mode()));
            }
        });
    }
}
