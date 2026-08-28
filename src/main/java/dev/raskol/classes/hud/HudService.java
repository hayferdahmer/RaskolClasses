// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.hud;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.ability.AbilityDef;
import dev.raskol.classes.classsystem.PlayerClass;
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

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * ActionBar-HUD в тёмной стилизации (§4):
 * ❬ Класс ⚔ ▰▰▰▰▰▱▱▱▱▱ 30/100 ⠋ Щит-стена 12с ❭
 *
 * O1: dirty-rendering — sendActionBar не шлётся, пока кадр не изменился
 *     и нет активной анимации. O4/O9: статика, сегменты бара и «Xс» кэшируются.
 * O8: SPECTATOR и скрытые (vanish) игроки пропускаются.
 * §4.3: braille-спиннер у активного КД, искра ▸ по бару при регене (кадр = тик).
 * §4.5: аура полного ресурса — 1 партикл темы раз в 20 тиков.
 */
public final class HudService {

    private static final int BAR_CELLS = 10;
    private static final char[] SPINNER = {'⠋', '⠙', '⠸', '⠴', '⠧', '⠇'};
    private static final TextColor FRAME_COLOR = TextColor.fromHexString("#555555");
    private static final Component FRAME_OPEN = Component.text("❬ ", FRAME_COLOR);
    private static final Component FRAME_CLOSE = Component.text(" ❭", FRAME_COLOR);
    private static final Component EMPTY_CELL = Component.text('▱').color(NamedTextColor.DARK_GRAY);

    private final RaskolClasses plugin;
    private final Map<UUID, Boolean> overrides = new HashMap<>();

    /** O1: последний ключ кадра на игрока. */
    private final Map<UUID, String> lastFrameKey = new HashMap<>();
    /** Искра регена: позиция ▸ (0..9), null — нет. */
    private final Map<UUID, Integer> sparkPosition = new HashMap<>();
    private final Map<UUID, Integer> lastResourceInt = new HashMap<>();

    /** O4/O9: кэши статики, сегментов и «Xс». */
    private final Map<PlayerClass, Component> headCache = new EnumMap<>(PlayerClass.class);
    private final Map<PlayerClass, Component[]> cellCache = new EnumMap<>(PlayerClass.class);
    private final Map<Integer, Component> secondsCache = new HashMap<>();

    private boolean enabledInConfig = true;
    private long frame = 0;

    public HudService(RaskolClasses plugin) {
        this.plugin = plugin;
        applyConfig();
    }

    /** Перечитать hud.* и сбросить кэши тем (старт и /rc reload). */
    public final void applyConfig() {
        this.enabledInConfig = plugin.getRaskolConfig().isHudEnabled();
        headCache.clear();
        cellCache.clear();
        secondsCache.clear();
    }

    /** Таск с периодом hud.update-period-ticks (O5). Ссылку отменяет onDisable. */
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

    /** /rc hud: инверсия персональной настройки. Возвращает новое состояние. */
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
        // O8: зрители и скрытые игроки actionbar не получают
        if (plugin.getRaskolConfig().hudSkipSpectator()
                && player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        // O8: vanish EssentialsX ("vanished") и CMI ("disappeared") — через
        // метадату, потому что Player#isVanished в Bukkit API отсутствует
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

        // Искра (§4.3): ресурс вырос с прошлого тика — запускаем ▸ по бару
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

        // Тикающие кулдауны
        List<AbilityDef> cooling = new ArrayList<>(2);
        for (AbilityDef def : plugin.getAbilities().getAbilities(pc)) {
            if (plugin.getCooldowns().getRemainingMillis(id, def.id()) > 0L) {
                cooling.add(def);
            }
        }

        boolean animating = !cooling.isEmpty() || spark != null;

        // O1: ключ кадра. Пока идёт анимация — каждый тик новый кадр (спиннер),
        // в простое ключ стабилен и sendActionBar не вызывается.
        StringBuilder keyBuilder = new StringBuilder(64)
                .append(pc.name()).append('|').append(filled)
                .append('|').append(valueInt).append('|').append(cooling.size());
        for (AbilityDef def : cooling) {
            keyBuilder.append('|').append(def.id()).append(':')
                    .append(plugin.getCooldowns().getRemainingMillis(id, def.id()) / 1000L);
        }
        if (spark != null) {
            keyBuilder.append("|sp").append(spark);
        }
        if (animating) {
            keyBuilder.append("#f").append(frame);
        }
                String key = keyBuilder.toString();
        // O1-фикс: статичный кадр всё равно повторяем каждые 2 тика-кадра (~1 с),
        // иначе actionbar гаснет и HUD «исчезает» в простое.
        if (key.equals(lastFrameKey.get(id)) && frame % 2 != 0) {
            return;
        }
        lastFrameKey.put(id, key);

        player.sendActionBar(render(id, pc, valueInt, filled, cooling, spark));

        // §4.5: аура полного ресурса — 1 партикл темы раз в 20 тиков (каждый 2-й кадр)
        if (plugin.getRaskolConfig().hudFullResourceAura()
                && valueInt >= ResourceState.MAX_VALUE && frame % 2 == 0) {
            player.getWorld().spawnParticle(plugin.getRaskolConfig().themeOf(pc).particle(),
                    player.getLocation().add(0, 1, 0), 1, 0.35, 0.5, 0.35, 0.01);
        }
    }

    private Component render(UUID playerId, PlayerClass pc, int valueInt, int filled,
                             List<AbilityDef> cooling, Integer spark) {
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

        ComponentBuilder<?, ?> line = Component.text();
        line.append(FRAME_OPEN)
                .append(headOf(pc, theme))
                .append(bar)
                .append(Component.text(" " + valueInt + "/100", theme.primary()));

        if (!cooling.isEmpty()) {
            // §4.3: braille-спиннер у ближайших кулдаунов
            line.append(Component.text(" " + SPINNER[(int) (frame % SPINNER.length)],
                    theme.secondary()));
            for (AbilityDef def : cooling) {
                long seconds = plugin.getCooldowns()
                        .getRemainingMillis(playerId, def.id()) / 1000L + 1L;
                line.append(Component.text(" " + def.displayName(), NamedTextColor.GRAY));
                // O9: «Xс» из кэша, не собираем каждый тик
                line.append(secondsCache.computeIfAbsent((int) seconds,
                        s -> Component.text(" " + s + "с", NamedTextColor.GRAY)));
            }
        }
        line.append(FRAME_CLOSE);
        return line.build();
    }

    /** O4: «Энергия ☠ » — один раз на класс, сбрасывается при reload. */
    private Component headOf(PlayerClass pc, RaskolConfig.ClassTheme theme) {
        Component cached = headCache.get(pc);
        if (cached == null) {
            cached = Component.text(pc.getResourceName() + " " + theme.symbol() + " ",
                    theme.primary());
            headCache.put(pc, cached);
        }
        return cached;
    }

    /** O9: 10 градиентных сегментов ▰ на класс, позиция → цвет (lerp темы). */
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
}
