// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.hud;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.attribute.AttributeMath;
import dev.raskol.classes.attribute.AttributeType;
import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.config.RaskolConfig;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.NamespacedKey;
import org.bukkit.GameMode;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.scheduler.BukkitTask;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 1.7.0 пакет 2 (финал p2.6): строгая компоновка 1.6.x + градиентные полосы
 * + искра регена (последний залитый сегмент вспыхивает spark-цветом при росте).
 *
 * 1.7.0.3: STR-РЕГЕН HP (Dota-подобный): applyStrRegen тикает вместе с баром
 * (hp-display.update-period-ticks, дефолт 10):
 *   rate = STR × regen-per-str (вне боя);
 *   в бою (окно combat-window-seconds после урона) rate ×= regen-combat-factor;
 *   кап regen-cap-pct от maxHP в секунду.
 * 1.7.0.4: НЕРФ РЕГЕНА ВДВОЕ — дефолт regen-per-str 0.05 → 0.025
 * (баланс-фидбек: «слишком быстро восстанавливается HP даже на жреце»):
 *   воин 40 ур. (STR 60): 1.5 HP/с вне боя / 0.525 в бою;
 *   жрец 40 ур. (STR 21): 0.52 HP/с вне боя.
 * Реген идёт прямым setHealth БЕЗ события RegainHealth: не спамит ресурс
 * жреца и проки лечения (grace) — осознанное закрытие дыры в экономике.
 * Мёртвые/зрители не регенят; реген работает в любом hp-display.mode.
 *
 * Компоновка бара (как в 1.6.x):
 *   ❬ ❤ ▰▰▱▱▱▱▱▱ 82/234 ❭ ❬  ▰▰▰▰▰▰▰ 100/100 ❭
 * Все цвета — в конфиге hp-display.colors/gradient/regen-spark.
 *
 * FIX 1.7.0-p2.1: Attribute резолвится через RegistryAccess (Paper 1.21.4).
 * FIX 1.7.0-p2.5: ClassTheme.primary()/secondary() возвращают TextColor.
 */
public final class HpBarService implements Listener {

    /** max_health из реестра атрибутов Paper (1.21.4-safe). */
    private static final Attribute MAX_HEALTH = RegistryAccess.registryAccess()
            .getRegistry(RegistryKey.ATTRIBUTE)
            .get(NamespacedKey.minecraft("max_health"));

    /** Состояние игрока для детекта регена (рост HP/ресурса). */
    private record State(double lastHp, double lastRes) {}

    private final RaskolClasses plugin;
    private final NamespacedKey maxHpKey;
    private final Map<UUID, State> lastTick = new ConcurrentHashMap<>();

    public HpBarService(RaskolClasses plugin) {
        this.plugin = plugin;
        this.maxHpKey = new NamespacedKey(plugin, "max_hp");
    }

    /* -------------------------------- конфиг -------------------------------- */

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

    /** Цвет из конфига (hex-строка) с фолбэком; битый hex не роняет HUD. */
    private TextColor color(String path, String fallback) {
        String hex = plugin.getConfig().getString(path, fallback);
        if (hex != null) {
            try {
                TextColor parsed = TextColor.fromHexString(hex);
                if (parsed != null) {
                    return parsed;
                }
            } catch (IllegalArgumentException ignored) {
                // битый hex → фолбэк
            }
        }
        return TextColor.fromHexString(fallback);
    }

    /* -------------------------------- задача -------------------------------- */

    public BukkitTask start() {
        return plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, period(), period());
    }

    private void tick() {
        boolean unified = !"vanilla".equals(mode());
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (player.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            applyMaxHealth(player);
            applyStrRegen(player);
            applyHearts(player, unified);
            if (unified) {
                sendUnifiedActionbar(player);
            }
        }
        lastTick.keySet().removeIf(uuid -> plugin.getServer().getPlayer(uuid) == null);
    }

    /* ----------------------------- применение maxHP ----------------------------- */

    private void applyMaxHealth(Player player) {
        if (MAX_HEALTH == null) {
            return;
        }
        AttributeInstance instance = player.getAttribute(MAX_HEALTH);
        if (instance == null) {
            return;
        }
        double target = plugin.getAttributes().maxHp(player.getUniqueId());
        if (!Double.isFinite(target) || target < 1.0) {
            target = 20.0;
        }
        double base = instance.getBaseValue();
        double delta = target - base;

        AttributeModifier existing = null;
        for (AttributeModifier m : instance.getModifiers()) {
            if (maxHpKey.equals(m.getKey())) {
                existing = m;
                break;
            }
        }
        if (existing == null || Math.abs(existing.getAmount() - delta) > 0.01) {
            if (existing != null) {
                instance.removeModifier(existing);
            }
            if (Math.abs(delta) > 0.01) {
                instance.addModifier(new AttributeModifier(
                        maxHpKey, delta, AttributeModifier.Operation.ADD_NUMBER,
                        EquipmentSlotGroup.ANY));
            }
        }
        double max = instance.getValue();
        if (player.getHealth() > max) {
            player.setHealth(max);
        }
    }

    /* --------------------------- 1.7.0.3/4: STR-реген --------------------------- */

    /**
     * Dota-подобный реген HP от СИЛЫ (нерф 1.7.0.4: дефолт 0.025 HP/с за STR).
     * Прямой setHealth без RegainHealthEvent (не спамит ресурс жреца и grace).
     */
    private void applyStrRegen(Player player) {
        if (player.isDead()) {
            return;
        }
        double max = maxOf(player);
        double hp = player.getHealth();
        if (hp >= max) {
            return;
        }
        UUID uuid = player.getUniqueId();
        double str = plugin.getAttributes().value(uuid, AttributeType.STR);
        double perStr = plugin.getConfig().getDouble("attributes.hp.regen-per-str", 0.025);
        double combatFactor = plugin.getConfig().getDouble("attributes.hp.regen-combat-factor", 0.35);
        double capPct = plugin.getConfig().getDouble("attributes.hp.regen-cap-pct", 1.5);

        PlayerClass pc = plugin.getClassProvider().getClassOf(player);
        long windowMs = (pc != null
                ? plugin.getRaskolConfig().combatWindowSeconds(pc)
                : 5) * 1000L;
        boolean inCombat = plugin.getResources().stateOf(uuid).isInCombat(windowMs);

        double perSec = AttributeMath.strRegenPerSecond(
                str, perStr, max, inCombat, combatFactor, capPct);
        if (perSec <= 0.0) {
            return;
        }
        double perTick = perSec * period() / 20.0;
        double newHp = Math.min(max, hp + perTick);
        if (newHp > hp) {
            player.setHealth(newHp);
        }
    }

    /* -------------------------------- сердца -------------------------------- */

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

    /* --------------------------- совмещённая строка --------------------------- */

    private void sendUnifiedActionbar(Player player) {
        double max = maxOf(player);
        double hp = Math.max(0.0, player.getHealth());
        double res = plugin.getResources().getValue(player.getUniqueId());
        PlayerClass pc = plugin.getClassProvider().getClassOf(player);
        int len = gaugeLength();
        boolean gauge = gaugeEnabled();
        double hpFraction = max <= 0 ? 0 : hp / max;

        TextColor frame = color("hp-display.colors.frame", "#8B5A3C");
        TextColor empty = color("hp-display.colors.gauge-empty", "#6E5232");
        TextColor hpFill = color("hp-display.colors.hp-fill", "#A32020");
        TextColor numbers = color("hp-display.colors.numbers", "#D6CDBE");
        TextColor spark = color("hp-display.regen-spark.color", "#F5E6B8");

        TextColor hpStart = color("hp-display.gradient.hp-start", "#5C0F0F");
        TextColor hpEnd = color("hp-display.gradient.hp-end", "#C82020");

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

        UUID uuid = player.getUniqueId();
        State prev = lastTick.get(uuid);
        boolean sparkActive = sparkEnabled() && prev != null;
        boolean hpRegen = sparkActive && hp > prev.lastHp() + 0.5;
        boolean resRegen = sparkActive && res > prev.lastRes() + 0.5;
        lastTick.put(uuid, new State(hp, res));

        Component line = Component.text("❬ ", frame)
                .append(Component.text("❤ ", hpEnd));
        if (gauge) {
            line = line.append(gradientBar(hpFraction, len,
                    gradientEnabled() ? hpStart : hpEnd, hpEnd, empty,
                    hpRegen ? spark : null))
                    .append(Component.text(" ", frame));
        }
        line = line.append(Component.text((int) hp + "/" + (int) max, numbers))
                .append(Component.text(" ❭ ❬ ", frame))
                .append(Component.text(symbol + " ", resSymbol));
        if (gauge) {
            line = line.append(gradientBar(res / 100.0, len,
                    gradientEnabled() ? resStart : resEnd, empty,
                    resRegen ? spark : null))
                    .append(Component.text(" ", frame));
        }
        line = line.append(Component.text((int) res + "/100", numbers))
                .append(Component.text(" ❭", frame));
        player.sendActionBar(line);
    }

    /** Полоса с градиентом и опциональной искрой регена. */
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
        };
    }

    private double maxOf(Player player) {
        if (MAX_HEALTH == null) {
            return 20.0;
        }
        AttributeInstance instance = player.getAttribute(MAX_HEALTH);
        double max = instance != null ? instance.getValue() : 20.0;
        return Double.isFinite(max) && max > 0.0 ? max : 20.0;
    }

    /* -------------------------------- события -------------------------------- */

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() != GameMode.SPECTATOR) {
            applyMaxHealth(player);
            applyHearts(player, !"vanilla".equals(mode()));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastTick.remove(event.getPlayer().getUniqueId());
    }
}
