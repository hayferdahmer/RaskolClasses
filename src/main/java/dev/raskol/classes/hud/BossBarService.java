// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.hud;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.config.RaskolConfig;
import dev.raskol.classes.effect.EffectType;
import dev.raskol.classes.hud.channel.HudChannel;
import dev.raskol.classes.hud.channel.HudChannelCoordinator;
import dev.raskol.classes.hud.snapshot.BossBarPresentation;
import dev.raskol.classes.hud.snapshot.WidgetFrame;
import dev.raskol.classes.util.TextFx;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/**
 * Пакет 5: босс-бар V2 для активных эффектов.
 * Создание, обновление и удаление Paper BossBar делегируется единому HUD channel owner.
 */
public final class BossBarService {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static final int BOSS_BAR_PRIORITY = 10;

    private final RaskolClasses plugin;
    private final HudChannelCoordinator channelCoordinator;
    private final Map<UUID, Set<String>> publishedFrames = new ConcurrentHashMap<>();

    public BossBarService(RaskolClasses plugin) {
        this(plugin, null);
    }

    public BossBarService(RaskolClasses plugin, HudChannelCoordinator channelCoordinator) {
        this.plugin = plugin;
        this.channelCoordinator = channelCoordinator;
    }

    /** Удаляет все опубликованные полосы; периодический вызов принадлежит BossBarRuntimeOwner. */
    public void clearBars() {
        for (UUID playerId : List.copyOf(publishedFrames.keySet())) {
            cleanupPlayer(playerId);
        }
    }

    /** Выполняет один цикл обновления полос; вызывается синхронным runtime scheduler. */
    public void tick() {
        RaskolConfig cfg = plugin.getRaskolConfig();
        if (!cfg.isBossBarEnabled()) {
            clearBars();
            return;
        }

        long now = System.currentTimeMillis();
        long minMillis = (long) cfg.bossBarMinDurationSeconds() * 1000L;
        int maxVisible = cfg.bossBarMaxVisible();
        String fmt = cfg.bossBarFormat();
        String fmtInf = cfg.bossBarFormatInfinite();

        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID playerId = player.getUniqueId();

            if (plugin.getHud() != null && !plugin.getHud().isVisible(player)) {
                cleanupPlayer(playerId);
                continue;
            }
            PlayerClass playerClass = plugin.getClassProvider().getClassOf(player);
            if (playerClass == null) {
                cleanupPlayer(playerId);
                continue;
            }

            RaskolConfig.ClassTheme theme = cfg.themeOf(playerClass);
            Map<EffectType, Long> active = plugin.getEffects().getActiveEffects(playerId);
            List<Map.Entry<EffectType, Long>> candidates = new ArrayList<>();
            for (Map.Entry<EffectType, Long> entry : active.entrySet()) {
                EffectType type = entry.getKey();
                if (type.displayName() == null) {
                    continue;
                }
                boolean oneShot = entry.getValue() == Long.MAX_VALUE;
                if (oneShot || totalDurationMillis(playerClass, type) >= minMillis) {
                    candidates.add(entry);
                }
            }

            candidates.sort(Comparator.<Map.Entry<EffectType, Long>, Long>comparing(
                            entry -> entry.getValue() == Long.MAX_VALUE ? Long.MAX_VALUE : entry.getValue() - now)
                    .reversed());

            BossBarPresentation.Color color = barColor(playerClass);
            Set<String> visibleFrames = new HashSet<>();
            int kept = 0;
            for (Map.Entry<EffectType, Long> entry : candidates) {
                if (kept >= maxVisible) {
                    break;
                }
                EffectType type = entry.getKey();
                long expiresAt = entry.getValue();
                String widgetId = widgetId(type);
                visibleFrames.add(widgetId);

                String secondsText;
                double progress;
                if (expiresAt == Long.MAX_VALUE) {
                    secondsText = "";
                    progress = 1.0;
                } else {
                    long remaining = Math.max(0L, expiresAt - now);
                    int seconds = (int) Math.max(1L, (remaining + 999L) / 1000L);
                    secondsText = String.valueOf(seconds);
                    double total = totalDurationMillis(playerClass, type);
                    progress = total > 0.0 ? Math.max(0.0, Math.min(1.0, remaining / total)) : 1.0;
                }

                Component title = buildTitle(theme, type, expiresAt == Long.MAX_VALUE ? fmtInf : fmt, secondsText);
                WidgetFrame frame = new WidgetFrame(
                        widgetId,
                        HudChannel.BOSS_BAR.name(),
                        BOSS_BAR_PRIORITY,
                        LEGACY.serialize(title),
                        OptionalLong.empty(),
                        java.util.Optional.of(new BossBarPresentation(
                                progress, color, BossBarPresentation.Style.SEGMENTED_10, Set.of())));
                if (channelCoordinator != null) {
                    try {
                        channelCoordinator.upsertPersistentFrame(playerId, frame);
                    } catch (RuntimeException ignored) {
                        // Coordinator журналирует сбой; следующий BossBar tick повторит публикацию.
                    }
                }
                kept++;
            }

            replacePublishedFrames(playerId, visibleFrames);
        }

        for (UUID playerId : List.copyOf(publishedFrames.keySet())) {
            if (Bukkit.getPlayer(playerId) == null) {
                cleanupPlayer(playerId);
            }
        }
    }

    private void replacePublishedFrames(UUID playerId, Set<String> visibleFrames) {
        Set<String> previous = publishedFrames.getOrDefault(playerId, Set.of());
        for (String widgetId : previous) {
            if (!visibleFrames.contains(widgetId)) {
                removeFrame(playerId, widgetId);
            }
        }
        if (visibleFrames.isEmpty()) {
            publishedFrames.remove(playerId);
        } else {
            publishedFrames.put(playerId, Set.copyOf(visibleFrames));
        }
    }

    private Component buildTitle(RaskolConfig.ClassTheme theme, EffectType type, String template, String secondsText) {
        String suffix = template.replace("{name}", "").replace("{sec}", secondsText);
        return Component.text(theme.symbol() + " ", theme.primary())
                .append(TextFx.gradient(type.displayName(), theme.primary(), theme.secondary()))
                .append(Component.text(suffix, NamedTextColor.DARK_GRAY));
    }

    /**
     * Длительность эффекта для прогресс-бара.
     * WARLOCK-эффекты пока не входят в EffectType — возвращаем 0 (бар полный).
     */
    private double totalDurationMillis(PlayerClass playerClass, EffectType type) {
        return switch (type) {
            case SHIELD_WALL -> plugin.getRaskolConfig().durationSeconds(PlayerClass.WARRIOR, "steel_skin", 5) * 1000.0;
            case BLOOD_FURY -> plugin.getRaskolConfig().durationSeconds(PlayerClass.WARRIOR, "blood_fury", 4) * 1000.0;
            case EVASION -> plugin.getRaskolConfig().durationSeconds(PlayerClass.ROGUE, "evasion", 4) * 1000.0;
            case STEALTH -> plugin.getRaskolConfig().durationSeconds(PlayerClass.ROGUE, "stealth", 15) * 1000.0;
            case NO_FALL_DAMAGE ->
                plugin.getRaskolConfig().durationSeconds(PlayerClass.HUNTER, "cheetah_aspect", "no-fall", 10) * 1000.0;
            case AIMED_SHOT, EXECUTE -> 0.0;
        };
    }

    private static BossBarPresentation.Color barColor(PlayerClass playerClass) {
        return switch (playerClass) {
            case WARRIOR -> BossBarPresentation.Color.RED;
            case HUNTER -> BossBarPresentation.Color.GREEN;
            case PRIEST -> BossBarPresentation.Color.WHITE;
            case MAGE -> BossBarPresentation.Color.BLUE;
            case ROGUE, WARLOCK -> BossBarPresentation.Color.PURPLE;
        };
    }

    private static String widgetId(EffectType type) {
        return "legacy-effect:" + type.name().toLowerCase(Locale.ROOT);
    }

    private void cleanupPlayer(UUID playerId) {
        Set<String> widgetIds = publishedFrames.remove(playerId);
        if (widgetIds == null) {
            return;
        }
        for (String widgetId : widgetIds) {
            removeFrame(playerId, widgetId);
        }
    }

    private void removeFrame(UUID playerId, String widgetId) {
        if (channelCoordinator == null) {
            return;
        }
        try {
            channelCoordinator.removePersistentFrame(playerId, widgetId);
        } catch (RuntimeException ignored) {
            // Coordinator журналирует сбой и повторит reconciliation на следующем channel tick.
        }
    }
}
