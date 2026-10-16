// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.bootstrap;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.ability.HunterAbilities;
import dev.raskol.classes.ability.MageAbilities;
import dev.raskol.classes.ability.PriestAbilities;
import dev.raskol.classes.ability.RogueAbilities;
import org.bukkit.scheduler.BukkitTask;

/**
 * 1.14.10 (Sprint 7, Волна 7.2, P0-4-01): периодические задачи плагина,
 * вынесенные из RaskolClasses.onEnable (аудит 4-01). Задачи возвращаются
 * вызывающему — владелец (RaskolClasses) сам добавляет их в activeTasks
 * и отменяет в onDisable.
 *
 * Состав:
 *  - startPurgeTask: чистка протухших записей всех сервисов + китов + боевых логов
 *    (performance.purge-interval-ticks);
 *  - startAutosaveTask: пакетный персист cooldowns/resources/spec2-storage
 *    (storage.autosave-minutes).
 */
public final class PluginTasks {

    private PluginTasks() {
    }

    /**
     * Общая purge-задача. 1.14.8 (Волна 6.3, P0-6-02): сюда же подключены
     * purgeStale() четырёх китов и ProcService/CombatService-логи, которые
     * раньше не вызывались нигде.
     */
    public static BukkitTask startPurgeTask(RaskolClasses plugin) {
        int interval = plugin.getRaskolConfig().purgeIntervalTicks();
        return plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            plugin.getCooldowns().purgeExpired();
            plugin.getEffects().purgeExpired();
            plugin.getAbilities().purgeStaleAttempts();
            plugin.getFx().purgeStale();
            plugin.getInstallations().purgeStale();
            plugin.getResists().purgeExpired();
            plugin.getAttributes().purgeExpired();
            plugin.getCharacterLevels().purgeStale();
            // 1.14.8-fix (Волна 6.3, P0-6-02): метки китов и боевые логи
            HunterAbilities.purgeStale();
            RogueAbilities.purgeStale();
            MageAbilities.purgeStale();
            PriestAbilities.purgeStale();
            plugin.getCombat().procs().purgeStale();
            plugin.getCombat().purgeBurstLog();
            plugin.markPurgeNow();
        }, interval, interval);
    }

    /** Autosave-задача: единственный плановый путь персиста трёх хранилищ (P0-5-01). */
    public static BukkitTask startAutosaveTask(RaskolClasses plugin) {
        long ticks = Math.max(1, plugin.getConfig().getInt("storage.autosave-minutes", 5)) * 60L * 20L;
        return plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            plugin.getCooldowns().saveAll();
            plugin.getResources().saveAll();
            plugin.getSpec2Storage().save();
        }, ticks, ticks);
    }
}
