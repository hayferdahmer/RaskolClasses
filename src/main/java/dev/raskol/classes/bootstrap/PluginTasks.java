// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.bootstrap;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.ability.HunterAbilities;
import dev.raskol.classes.ability.MageAbilities;
import dev.raskol.classes.ability.PriestAbilities;
import dev.raskol.classes.ability.RogueAbilities;

/**
 * Cross-domain maintenance callbacks. Планирование и владение repeating tasks
 * вынесены в bootstrap.runtime; этот класс сохраняет только состав операций.
 */
public final class PluginTasks {

    private PluginTasks() {}

    /**
     * Общая purge-задача. 1.14.8 (Волна 6.3, P0-6-02): сюда же подключены
     * purgeStale() четырёх китов и ProcService/CombatService-логи, которые
     * раньше не вызывались нигде.
     */
    public static void purge(RaskolClasses plugin) {
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
    }

    /** Один пакетный autosave трёх хранилищ (P0-5-01). */
    public static void autosave(RaskolClasses plugin) {
                            plugin.getCooldowns().saveAll();
                            plugin.getResources().saveAll();
                            plugin.getSpec2Storage().save();
    }
}
