// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.hud;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.config.RaskolConfig;
import dev.raskol.classes.effect.EffectType;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Пакет 5: босс-бар V2 для активных эффектов.
 * Тикает каждые N тиков (по умолчанию 5), опрашивает ActiveEffectManager,
 * фильтрует эффекты по порогу минимальной длительности (8 с по умолчанию),
 * сортирует по оставшемуся времени и показывает top-N (по умолчанию 2).
 * Цвет бара = цвет класса. Отсчёт — целые секунды. One-shot эффекты
 * (AIMED_SHOT) показываются как «{name}» с полной шкалой.
 */
public final class BossBarService {

    private final RaskolClasses plugin;
    private final Map<UUID, Map<EffectType, BossBar>> bars = new ConcurrentHashMap<>();
    private BukkitTask task;

    public BossBarService(RaskolClasses plugin) {
        this.plugin = plugin;
    }

    public BukkitTask start() {
        int period = plugin.getRaskolConfig().bossBarTickPeriod();
        this.task = new BukkitRunnable() {
            @Override
            public void run() {
                tick();
            }
        }.runTaskTimer(plugin, period, period);
        return this.task;
    }

    /** /rc reload: чистим всё и перезапускаем с новыми параметрами. */
    public void applyConfig() {
        shutdown();
        start();
    }

    /** onDisable: убираем все бары со всех игроков, отменяем тик. */
    public void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (Map<EffectType, BossBar> playerBars : bars.values()) {
            for (BossBar bar : playerBars.values()) {
                bar.removeAll();
            }
        }
        bars.clear();
    }

    private void tick() {
        RaskolConfig cfg = plugin.getRaskolConfig();
        if (!cfg.isBossBarEnabled()) {
            cleanupAll();
            return;
        }

        long now = System.currentTimeMillis();
        long minMillis = (long) cfg.bossBarMinDurationSeconds() * 1000L;
        int maxVisible = cfg.bossBarMaxVisible();
        String fmt = cfg.bossBarFormat();
        String fmtInf = cfg.bossBarFormatInfinite();

        for (Player player : Bukkit.getOnlinePlayers()) {
            // Уважаем флаг /rc hud
            if (plugin.getHud() != null && !plugin.getHud().isVisible(player)) {
                cleanupPlayer(player.getUniqueId());
                continue;
            }
            PlayerClass pc = plugin.getClassProvider().getClassOf(player);
            if (pc == null) {
                cleanupPlayer(player.getUniqueId());
                continue;
            }

            Map<EffectType, Long> active = plugin.getEffects().getActiveEffects(player.getUniqueId());

            // Фильтр: displayName != null (legacy пропускаем) и remaining >= minMillis или one-shot
            List<Map.Entry<EffectType, Long>> candidates = new ArrayList<>();
            for (Map.Entry<EffectType, Long> e : active.entrySet()) {
                EffectType type = e.getKey();
                if (type.displayName() == null) {
                    continue;
                }
                long expires = e.getValue();
                long remaining = expires == Long.MAX_VALUE ? Long.MAX_VALUE : (expires - now);
                if (remaining == Long.MAX_VALUE || remaining >= minMillis) {
                    candidates.add(e);
                }
            }

            // top-N по remaining (убывание)
            candidates.sort(Comparator.<Map.Entry<EffectType, Long>, Long>comparing(
                    e -> e.getValue() == Long.MAX_VALUE ? Long.MAX_VALUE : e.getValue() - now
            ).reversed());

            Map<EffectType, BossBar> playerBars = bars.computeIfAbsent(
                    player.getUniqueId(), id -> new EnumMap<>(EffectType.class));
            BarColor color = barColor(pc);

            int kept = 0;
            List<EffectType> visible = new ArrayList<>();
            for (Map.Entry<EffectType, Long> e : candidates) {
                if (kept >= maxVisible) {
                    break;
                }
                EffectType type = e.getKey();
                long expires = e.getValue();
                BossBar bar = playerBars.get(type);
                if (bar == null) {
                    bar = Bukkit.createBossBar("", color, BarStyle.SOLID);
                    bar.addPlayer(player);
                    playerBars.put(type, bar);
                } else {
                    bar.setColor(color);
                }

                if (expires == Long.MAX_VALUE) {
                    bar.setTitle(fmtInf.replace("{name}", type.displayName()));
                    bar.setProgress(1.0);
                } else {
                    long remaining = expires - now;
                    int sec = (int) Math.max(1L, (remaining + 999L) / 1000L);
                    bar.setTitle(fmt.replace("{name}", type.displayName())
                            .replace("{sec}", String.valueOf(sec)));
                    double total = totalDurationMillis(pc, type);
                    double progress = total > 0
                            ? Math.max(0.0, Math.min(1.0, remaining / total))
                            : 1.0;
                    bar.setProgress(progress);
                }
                visible.add(type);
                kept++;
            }

            // Убираем бары, не вошедшие в top-N
            List<EffectType> toRemove = new ArrayList<>();
            for (Map.Entry<EffectType, BossBar> entry : playerBars.entrySet()) {
                if (!visible.contains(entry.getKey())) {
                    entry.getValue().removePlayer(player);
                    toRemove.add(entry.getKey());
                }
            }
            for (EffectType t : toRemove) {
                playerBars.remove(t);
            }
            if (playerBars.isEmpty()) {
                bars.remove(player.getUniqueId());
            }
        }

        // Убираем бары игроков, которые вышли с сервера
        List<UUID> offline = new ArrayList<>();
        for (UUID id : bars.keySet()) {
            if (Bukkit.getPlayer(id) == null) {
                offline.add(id);
            }
        }
        for (UUID id : offline) {
            cleanupPlayer(id);
        }
    }

    /**
     * Начальная длительность эффекта для расчёта прогресса бара.
     * Берём из конфига длительности связанной активки; если эффекта
     * в маппинге нет (legacy) — возвращаем 0 и бар показываем полным.
     */
    private double totalDurationMillis(PlayerClass pc, EffectType type) {
        return switch (type) {
            case SHIELD_WALL -> plugin.getRaskolConfig()
                    .durationSeconds(PlayerClass.WARRIOR, "steel_skin", 5) * 1000.0;
            case BLOOD_FURY -> plugin.getRaskolConfig()
                    .durationSeconds(PlayerClass.WARRIOR, "blood_fury", 4) * 1000.0;
            case EVASION -> plugin.getRaskolConfig()
                    .durationSeconds(PlayerClass.ROGUE, "evasion", 4) * 1000.0;
            case STEALTH -> plugin.getRaskolConfig()
                    .durationSeconds(PlayerClass.ROGUE, "stealth", 15) * 1000.0;
            case NO_FALL_DAMAGE -> plugin.getRaskolConfig()
                    .durationSeconds(PlayerClass.HUNTER, "cheetah_aspect", "no-fall", 10) * 1000.0;
            case AIMED_SHOT, EXECUTE -> 0.0;
        };
    }

    /** Цвет бара по классу. Подбираем ближайший BarColor к secondary теме. */
    private static BarColor barColor(PlayerClass pc) {
        return switch (pc) {
            case WARRIOR -> BarColor.RED;
            case HUNTER -> BarColor.GREEN;
            case PRIEST -> BarColor.WHITE;
            case MAGE -> BarColor.BLUE;
            case ROGUE -> BarColor.PURPLE;
        };
    }

    private void cleanupPlayer(UUID id) {
        Map<EffectType, BossBar> playerBars = bars.remove(id);
        if (playerBars == null) {
            return;
        }
        Player p = Bukkit.getPlayer(id);
        for (BossBar bar : playerBars.values()) {
            if (p != null) {
                bar.removePlayer(p);
            } else {
                bar.removeAll();
            }
        }
    }

    private void cleanupAll() {
        for (Map<EffectType, BossBar> playerBars : bars.values()) {
            for (BossBar bar : playerBars.values()) {
                bar.removeAll();
            }
        }
        bars.clear();
    }
}
