// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.hud;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.combat.dot.DotInstance;
import dev.raskol.classes.combat.dot.DotService;
import dev.raskol.classes.combat.school.School;
import dev.raskol.classes.config.RaskolConfig;
import dev.raskol.classes.resource.ResourceState;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentBuilder;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * ActionBar-HUD в тёмной стилизации:
 * ❬ Энергия ☠ ▰▰▰▰▱▱▱▱▱▱ 30/100 ❭ 🔥×2·3с 🩸·1с
 *
 * 1.5.6: список активных кулдаунов убран из actionbar — теперь он живёт
 * полоской перезарядки на свитках в хотбаре (ScrollCooldownTask).
 * Строка стала фиксированной длины: не прыгает и не съезжает при кастах.
 *
 * 1.12.6: DoT-строка после ресурсного бара — активные школьные DoT'ы на
 * носителе (сам игрок). Формат: иконка школы ×стеки ·секунды; разделитель
 * из messages.hud.dot-separator; иконки из messages.hud.dot-icons.
 *
 * O1: dirty-rendering — sendActionBar не шлётся, пока кадр не изменился.
 * O4/O9: статика и сегменты бара кэшируются.
 * O8: SPECTATOR и скрытые (vanished/disappeared) игроки пропускаются.
 * §4.3: искра ▸ по бару при регене (кадр = тик).
 * §4.5: аура полного ресурса — 1 партикл темы раз в 20 тиков.
 */
public final class HudService {

    private static final int BAR_CELLS = 10;
    private static final TextColor FRAME_COLOR = TextColor.fromHexString("#555555");
    private static final Component FRAME_OPEN = Component.text("❬ ", FRAME_COLOR);
    private static final Component FRAME_CLOSE = Component.text(" ❭", FRAME_COLOR);
    private static final Component EMPTY_CELL = Component.text('▱').color(NamedTextColor.DARK_GRAY);
    private static final TextColor DOT_COLOR = TextColor.fromHexString("#D6CDBE");
    private static final Component NO_DOTS = Component.empty();

    private final RaskolClasses plugin;
    private final Map<UUID, Boolean> overrides = new HashMap<>();

    private final Map<UUID, String> lastFrameKey = new HashMap<>();
    private final Map<UUID, Integer> sparkPosition = new HashMap<>();
    private final Map<UUID, Integer> lastResourceInt = new HashMap<>();

    private final Map<PlayerClass, Component> headCache = new EnumMap<>(PlayerClass.class);
    private final Map<PlayerClass, Component[]> cellCache = new EnumMap<>(PlayerClass.class);
    /** 1.12.6: иконки школ кэшируются статически — читаются из messages.hud.dot-icons. */
    private final Map<School, String> dotIconCache = new EnumMap<>(School.class);

    private boolean enabledInConfig = true;
    private long frame = 0;

    public HudService(RaskolClasses plugin) {
        this.plugin = plugin;
        applyConfig();
    }

    public final void applyConfig() {
        this.enabledInConfig = plugin.getRaskolConfig().isHudEnabled();
        headCache.clear();
        cellCache.clear();
        dotIconCache.clear();
    }

    public BukkitTask start() {
        long period = Math.max(1L, plugin.getRaskolConfig().hudUpdatePeriodTicks());
        return new BukkitRunnable() {
            @Override
            public void run() {
                frame++;
                for (Player player : Bukkit.getOnlinePlayers()) {
                    tickFor(player);
                }
            }
        }.runTaskTimer(plugin, period, period);
    }

    public boolean toggle(Player player) {
        boolean visible = isVisible(player);
        overrides.put(player.getUniqueId(), !visible);
        return !visible;
    }

    public boolean isVisible(Player player) {
        Boolean override = overrides.get(player.getUniqueId());
        return override != null ? override : enabledInConfig;
    }

    private void tickFor(Player player) {
        if (!isVisible(player)) {
            return;
        }
        if (plugin.getRaskolConfig().hudSkipSpectator()
                && player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        if (player.hasMetadata("vanished") || player.hasMetadata("disappeared")) {
            return;
        }

        PlayerClass pc = plugin.getClassProvider().getClassOf(player);
        if (pc == null) {
            return;
        }
        UUID id = player.getUniqueId();
        double value = plugin.getResources().getValue(id);
        int valueInt = (int) value;
        int filled = (int) Math.round(value / ResourceState.MAX_VALUE * BAR_CELLS);

        Integer previous = lastResourceInt.put(id, valueInt);
        if (previous != null && valueInt > previous) {
            sparkPosition.put(id, 0);
        }
        Integer spark = sparkPosition.get(id);
        if (spark != null) {
            if (spark >= BAR_CELLS) {
                sparkPosition.remove(id);
                spark = null;
            } else {
                sparkPosition.put(id, spark + 1);
            }
        }

        // 1.12.6: DoT-строка на игроке-носителе
        Component dotsComponent = renderDots(id);

        // 1.12.6: хэш DoT-строки входит в ключ кадра — O1 dirty-rendering
        // корректно реагирует на появление/снятие/тик DoT'ов
        boolean animating = spark != null;
        StringBuilder keyBuilder = new StringBuilder(48)
                .append(pc.name()).append('|').append(filled)
                .append('|').append(valueInt);
        if (spark != null) {
            keyBuilder.append("|sp").append(spark);
        }
        if (dotsComponent != NO_DOTS) {
            keyBuilder.append("|d").append(dotSnapshotHash(id));
        }
        if (animating) {
            keyBuilder.append("#f").append(frame);
        }
        String key = keyBuilder.toString();
        if (key.equals(lastFrameKey.get(id)) && frame % 2 != 0) {
            return;
        }
        lastFrameKey.put(id, key);

        player.sendActionBar(render(pc, valueInt, filled, spark, dotsComponent));

        if (plugin.getRaskolConfig().hudFullResourceAura()
                && valueInt >= ResourceState.MAX_VALUE && frame % 2 == 0) {
            player.getWorld().spawnParticle(plugin.getRaskolConfig().themeOf(pc).particle(),
                    player.getLocation().add(0, 1, 0), 1, 0.35, 0.5, 0.35, 0.01);
        }
    }

    private Component render(PlayerClass pc, int valueInt, int filled,
                             Integer spark, Component dotsComponent) {
        RaskolConfig.ClassTheme theme = plugin.getRaskolConfig().themeOf(pc);
        Component[] cells = cellsOf(pc, theme);

        ComponentBuilder<?, ?> bar = Component.text();
        for (int i = 0; i < BAR_CELLS; i++) {
            if (spark != null && i == spark) {
                bar.append(Component.text('▸').color(theme.secondary()));
            } else if (i < filled) {
                bar.append(cells[i]);
            } else {
                bar.append(EMPTY_CELL);
            }
        }

        ComponentBuilder<?, ?> out = Component.text()
                .append(FRAME_OPEN)
                .append(headOf(pc, theme))
                .append(bar)
                .append(Component.text(" " + valueInt + "/100", theme.primary()))
                .append(FRAME_CLOSE);
        if (dotsComponent != NO_DOTS) {
            out.append(Component.text(" ", DOT_COLOR)).append(dotsComponent);
        }
        return out.build();
    }

    private Component headOf(PlayerClass pc, RaskolConfig.ClassTheme theme) {
        Component cached = headCache.get(pc);
        if (cached == null) {
            cached = Component.text(pc.getResourceName() + " " + theme.symbol() + " ",
                    theme.primary());
            headCache.put(pc, cached);
        }
        return cached;
    }

    private Component[] cellsOf(PlayerClass pc, RaskolConfig.ClassTheme theme) {
        Component[] cached = cellCache.get(pc);
        if (cached == null) {
            cached = new Component[BAR_CELLS];
            for (int i = 0; i < BAR_CELLS; i++) {
                float t = (float) i / (BAR_CELLS - 1);
                cached[i] = Component.text('▰')
                        .color(TextColor.lerp(t, theme.primary(), theme.secondary()));
            }
            cellCache.put(pc, cached);
        }
        return cached;
    }

    /* ------------------------------ 1.12.6: DoT-строка ------------------------------ */

    /**
     * Иконка школы из messages.hud.dot-icons.<school>. Фолбэк:
     * FIRE→🔥, FROST→❄, NATURE→🌿, PHYSICAL→🩸, SHADOW→☾, HOLY→✦, ARCANE→✧, TRUE→◈.
     */
    private String iconOf(School school) {
        String cached = dotIconCache.get(school);
        if (cached != null) {
            return cached;
        }
        String path = "messages.hud.dot-icons." + school.id();
        String fromCfg = plugin.getConfig().getString(path, null);
        String icon = (fromCfg != null && !fromCfg.isEmpty()) ? fromCfg : defaultIcon(school);
        dotIconCache.put(school, icon);
        return icon;
    }

    private static String defaultIcon(School school) {
        return switch (school) {
            case FIRE -> "🔥";
            case FROST -> "❄";
            case NATURE -> "🌿";
            case PHYSICAL -> "🩸";
            case SHADOW -> "☾";
            case HOLY -> "✦";
            case ARCANE -> "✧";
            case TRUE -> "◈";
        };
    }

    /**
     * Одна клетка DoT-строки: "🔥×2·3с". Формат из messages.hud.dot-format.
     * {icon} — иконка школы; {stacks} — "×N" при стеках >1, пусто при 1; {sec} — ceil.
     */
    private Component dotCell(DotInstance inst, long now) {
        School school = inst.def().school();
        String icon = iconOf(school);
        long sec = DotService.remainingSeconds(inst, now);
        if (sec <= 0L) {
            sec = 1L;
        }
        String stacksTpl = plugin.getConfig().getString(
                "messages.hud.dot-stacks-x", "×{n}");
        String stacksEmpty = plugin.getConfig().getString(
                "messages.hud.dot-stacks-empty", "");
        String stacksPart = inst.stacks() > 1
                ? stacksTpl.replace("{n}", String.valueOf(inst.stacks()))
                : stacksEmpty;
        String tpl = plugin.getConfig().getString(
                "messages.hud.dot-format", "{icon}{stacks}·{sec}с");
        String text = tpl
                .replace("{icon}", icon)
                .replace("{stacks}", stacksPart)
                .replace("{sec}", String.valueOf(sec));
        return Component.text(text, DOT_COLOR);
    }

    /**
     * DoT-строка для игрока-носителя: все активные Dot'ы, отсортированные
     * по id (стабильный порядок), склеенные через messages.hud.dot-separator.
     */
    private Component renderDots(UUID targetUuid) {
        List<DotInstance> dots = plugin.getCombat().dots().activeDotsOf(targetUuid);
        if (dots.isEmpty()) {
            return NO_DOTS;
        }
        dots.sort((a, b) -> a.def().id().compareTo(b.def().id()));
        String sep = plugin.getConfig().getString("messages.hud.dot-separator", " ");
        long now = System.currentTimeMillis();
        ComponentBuilder<?, ?> out = Component.text();
        boolean first = true;
        for (DotInstance inst : dots) {
            if (!first) {
                out.append(Component.text(sep, DOT_COLOR));
            }
            out.append(dotCell(inst, now));
            first = false;
        }
        return out.build();
    }

    /**
     * Короткий хэш DoT-состояния для ключа кадра (O1 dirty-rendering).
     * Состав: id|stacks|remainingSec для каждого Dot, склеенные ';'.
     */
    private String dotSnapshotHash(UUID targetUuid) {
        List<DotInstance> dots = plugin.getCombat().dots().activeDotsOf(targetUuid);
        if (dots.isEmpty()) {
            return "";
        }
        long now = System.currentTimeMillis();
        StringBuilder sb = new StringBuilder(dots.size() * 12);
        for (DotInstance inst : dots) {
            sb.append(inst.def().id())
                    .append(':').append(inst.stacks())
                    .append(':').append(DotService.remainingSeconds(inst, now))
                    .append(';');
        }
        return sb.toString();
    }
}
