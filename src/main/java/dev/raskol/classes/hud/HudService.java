// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.hud;

import dev.raskol.classes.RaskolClasses;
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

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * ActionBar-HUD в тёмной стилизации:
 * ❬ Энергия ☠ ▰▰▰▰▱▱▱▱▱▱ 30/100 ❭
 *
 * 1.5.6: список активных кулдаунов убран из actionbar — теперь он живёт
 * полоской перезарядки на свитках в хотбаре (ScrollCooldownTask).
 * Строка стала фиксированной длины: не прыгает и не съезжает при кастах.
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

    private final RaskolClasses plugin;
    private final Map<UUID, Boolean> overrides = new HashMap<>();

    /** O1: последний ключ кадра на игрока. */
    private final Map<UUID, String> lastFrameKey = new HashMap<>();
    /** Искра регена: позиция ▸ (0..9), null — нет. */
    private final Map<UUID, Integer> sparkPosition = new HashMap<>();
    private final Map<UUID, Integer> lastResourceInt = new HashMap<>();

    /** O4/O9: кэши статики и сегментов бара. */
    private final Map<PlayerClass, Component> headCache = new EnumMap<>(PlayerClass.class);
    private final Map<PlayerClass, Component[]> cellCache = new EnumMap<>(PlayerClass.class);

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

    /** API: инверсия персональной настройки (сохранено для /rc debug и будущего UI). */
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

        // 1.5.6: кулдауны больше не печатаются в actionbar — их показывает
        // ScrollCooldownTask на свитках в хотбаре. Ключ кадра теперь зависит
        // только от ресурса и искры — строка фиксированной длины.
        boolean animating = spark != null;
        StringBuilder keyBuilder = new StringBuilder(32)
                .append(pc.name()).append('|').append(filled)
                .append('|').append(valueInt);
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

        player.sendActionBar(render(pc, valueInt, filled, spark));

        // §4.5: аура полного ресурса — 1 партикл темы раз в 20 тиков (каждый 2-й кадр)
        if (plugin.getRaskolConfig().hudFullResourceAura()
                && valueInt >= ResourceState.MAX_VALUE && frame % 2 == 0) {
            player.getWorld().spawnParticle(plugin.getRaskolConfig().themeOf(pc).particle(),
                    player.getLocation().add(0, 1, 0), 1, 0.35, 0.5, 0.35, 0.01);
        }
    }

    /**
     * Фиксированный формат: ❬ Энергия ☠ ▰▰▰▰▱▱▱▱▱▱ 30/100 ❭
     * Длина строки не меняется от количества/названий кулдаунов.
     */
    private Component render(PlayerClass pc, int valueInt, int filled, Integer spark) {
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

        return Component.text()
                .append(FRAME_OPEN)
                .append(headOf(pc, theme))
                .append(bar)
                .append(Component.text(" " + valueInt + "/100", theme.primary()))
                .append(FRAME_CLOSE)
                .build();
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
