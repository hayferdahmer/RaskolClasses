// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.hud;

import dev.raskol.classes.RaskolClasses;
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
 * 1.7.0 пакет 2 (редизайн p2.6 «живая готика»): совмещённый ActionBar-HUD.
 *
 *   ❬ ❤ ██████████▰▰▰▰ 93/235 ❭ ❬  ██████████ 100/100 ❭
 *
 * Исправления по отзыву:
 *  - ТОЛЩИНА: сегменты — полновесные █ (полоса читается как литая, не волосяная);
 *  - АСИММЕТРИЯ: HP-бар длиннее ресурс-бара (gauge-length-hp 14 vs gauge-length-res 10);
 *  - ЦИФРЫ: приглушённый тёплый пергамент вместо белого; show-numbers:false —
 *    чистые полосы без цифр (кегль actionbar фиксирован клиентом — это ограничение
 *    vanilla, лечится только ресурс-паком, трек B);
 *  - ГРАДИЕНТ: каждый залитый сегмент красится интерполяцией hex между
 *    hp-start→hp-end (HP) и theme.primary→theme.secondary (ресурс: у мага —
 *    синий перелив в духе AuraSkills);
 *  - ИСКРА РЕГЕНА: при росте значения свежезалитые сегменты вспыхивают
 *    colors.spark и плавно гаснут за 600 мс (fade по времени).
 *
 * Пустые сегменты — почти чёрный теплый empty: полоса строгая, без неона.
 * Рамка ❬ ❭ — бронза бренда.
 *
 * Сердца: healthScale = hearts-scale (дефолт 20 = один ряд на весь пул).
 * Полное скрытие сердец сервером невозможно (Paper отклоняет scale 0) —
 * только ресурспаком. mode=vanilla: строка не шлётся, ресурс рисует HudService.
 *
 * Применение maxHP: AttributeModifier ADD_NUMBER raskolclasses:max_hp;
 * пересчёт каждые hp-display.update-period-ticks (дефолт 10).
 * FIX 1.7.0-p2.1: Attribute через RegistryAccess (Paper 1.21.4).
 */
public final class HpBarService implements Listener {

    /** max_health из реестра атрибутов Paper (1.21.4-safe). */
    private static final Attribute MAX_HEALTH = RegistryAccess.registryAccess()
            .getRegistry(RegistryKey.ATTRIBUTE)
            .get(NamespacedKey.minecraft("max_health"));

    /** Длительность вспышки-искры при регене, мс. */
    private static final long SPARK_MS = 600L;

    /** Состояние полос игрока: последние значения и окна искры. */
    private static final class BarState {
        double lastHp = -1;
        double lastRes = -1;
        long sparkHpUntil;
        long sparkResUntil;
        int sparkHpFrom;
        int sparkResFrom;
    }

    private final RaskolClasses plugin;
    private final NamespacedKey maxHpKey;
    private final Map<UUID, BarState> states = new ConcurrentHashMap<>();

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
            return 20.0; // Paper требует scale > 0; 0/мусор → безопасные 20
        }
        return Math.min(20.0, v);
    }

    private boolean gaugeEnabled() {
        return plugin.getConfig().getBoolean("hp-display.gauge", true);
    }

    private boolean showNumbers() {
        return plugin.getConfig().getBoolean("hp-display.show-numbers", true);
    }

    private int hpLength() {
        return Math.max(4, Math.min(24,
                plugin.getConfig().getInt("hp-display.gauge-length-hp", 14)));
    }

    private int resLength() {
        return Math.max(4, Math.min(24,
                plugin.getConfig().getInt("hp-display.gauge-length-res", 10)));
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
        long now = System.currentTimeMillis();
        boolean unified = !"vanilla".equals(mode());
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (player.getGameMode() == GameMode.SPECTATOR) {
                continue; // зрителям ни строки, ни scale-правок
            }
            applyMaxHealth(player);
            applyHearts(player, unified);
            if (unified) {
                sendUnifiedActionbar(player, now);
            }
        }
        // гигиена: состояния оффлайн-игроков не держим
        states.keySet().removeIf(uuid -> plugin.getServer().getPlayer(uuid) == null);
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

    /**
     * Сердца: в unified-режиме — ровно один ряд (scale 20 по умолчанию);
     * в vanilla — возвращаем клиенту дефолтное отображение.
     */
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
                // ядро отклонило scale — оставляем дефолт, строка всё равно несёт числа
                player.setHealthScaled(false);
            }
        }
    }

    /* --------------------------- совмещённая строка --------------------------- */

    private void sendUnifiedActionbar(Player player, long now) {
        double max = maxOf(player);
        double hp = Math.max(0.0, player.getHealth());
        double res = plugin.getResources().getValue(player.getUniqueId());
        PlayerClass pc = plugin.getClassProvider().getClassOf(player);
        int hpLen = hpLength();
        int resLen = resLength();
        boolean gauge = gaugeEnabled();
        double hpFraction = max <= 0 ? 0 : Math.max(0.0, Math.min(1.0, hp / max));
        double resFraction = Math.max(0.0, Math.min(1.0, res / 100.0));

        BarState st = states.computeIfAbsent(player.getUniqueId(), k -> new BarState());
        // искра регена: значение выросло → запоминаем окно и стартовый индекс
        if (st.lastHp >= 0 && hp > st.lastHp + 0.01) {
            st.sparkHpFrom = Math.max(0, (int) Math.floor(
                    (st.lastHp / Math.max(1.0, max)) * hpLen));
            st.sparkHpUntil = now + SPARK_MS;
        }
        if (st.lastRes >= 0 && res > st.lastRes + 0.01) {
            st.sparkResFrom = Math.max(0, (int) Math.floor((st.lastRes / 100.0) * resLen));
            st.sparkResUntil = now + SPARK_MS;
        }
        st.lastHp = hp;
        st.lastRes = res;

        TextColor frame = color("hp-display.colors.frame", "#8B5A3C");
        TextColor empty = color("hp-display.colors.empty", "#241C17");
        TextColor hpStart = color("hp-display.colors.hp-start", "#5A0F0F");
        TextColor hpEnd = color("hp-display.colors.hp-end", "#C43B3B");
        TextColor numbers = color("hp-display.colors.numbers", "#B7A880");
        TextColor spark = color("hp-display.colors.spark", "#FFD700");

        RaskolConfig.ClassTheme theme = plugin.getRaskolConfig().themeOf(pc);
        TextColor resStart = theme != null && theme.primary() != null
                ? theme.primary()
                : color("hp-display.colors.res-fill", "#C8A24A");
        TextColor resEnd = theme != null && theme.secondary() != null
                ? theme.secondary()
                : resStart;
        TextColor resSymbol = resEnd;
        String symbol = symbolOf(pc);

        Component line = Component.text("❬ ", frame)
                .append(Component.text("❤ ", hpEnd));
        if (gauge) {
            line = line.append(gauge(hpFraction, hpLen, hpStart, hpEnd, empty,
                    st.sparkHpUntil, st.sparkHpFrom, spark, now))
                    .append(Component.text(" ", frame));
        }
        if (showNumbers()) {
            line = line.append(Component.text((int) hp + "/" + (int) max, numbers));
        }
        line = line.append(Component.text(" ❭ ❬ ", frame))
                .append(Component.text(symbol + " ", resSymbol));
        if (gauge) {
            line = line.append(gauge(resFraction, resLen, resStart, resEnd, empty,
                    st.sparkResUntil, st.sparkResFrom, spark, now))
                    .append(Component.text(" ", frame));
        }
        if (showNumbers()) {
            line = line.append(Component.text((int) res + "/100", numbers));
        }
        line = line.append(Component.text(" ❭", frame));
        player.sendActionBar(line);
    }

    /**
     * Литая полоса из █: залитые сегменты — градиент from→to по индексу,
     * поверх — искра spark (fade по оставшемуся времени окна) для сегментов
     * от sparkFrom; пустые — almost-black empty.
     */
    private static Component gauge(double fraction, int len,
                                   TextColor from, TextColor to, TextColor empty,
                                   long sparkUntil, int sparkFrom, TextColor spark, long now) {
        int filled = (int) Math.round(Math.max(0.0, Math.min(1.0, fraction)) * len);
        Component c = Component.empty();
        for (int i = 0; i < len; i++) {
            if (i < filled) {
                double t = len <= 1 ? 1.0 : i / (double) (len - 1);
                TextColor col = lerp(from, to, t);
                if (now < sparkUntil && i >= sparkFrom) {
                    double fade = (sparkUntil - now) / (double) SPARK_MS; // 1 → 0
                    col = lerp(col, spark, Math.max(0.0, Math.min(1.0, fade)));
                }
                c = c.append(Component.text("█", col));
            } else {
                c = c.append(Component.text("█", empty));
            }
        }
        return c;
    }

    /** Линейная интерполяция двух hex-цветов. */
    private static TextColor lerp(TextColor a, TextColor b, double t) {
        double k = Math.max(0.0, Math.min(1.0, t));
        int av = a.value();
        int bv = b.value();
        int ar = (av >> 16) & 0xFF;
        int ag = (av >> 8) & 0xFF;
        int ab = av & 0xFF;
        int br = (bv >> 16) & 0xFF;
        int bg = (bv >> 8) & 0xFF;
        int bb = bv & 0xFF;
        int r = (int) Math.round(ar + (br - ar) * k);
        int g = (int) Math.round(ag + (bg - ag) * k);
        int bl = (int) Math.round(ab + (bb - ab) * k);
        return TextColor.fromHexString(String.format(Locale.ROOT, "#%02X%02X%02X", r, g, bl));
    }

    /** Эмблема класса из конфига (theme.symbol), фолбэк по классу. */
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
        states.remove(event.getPlayer().getUniqueId());
    }
}
