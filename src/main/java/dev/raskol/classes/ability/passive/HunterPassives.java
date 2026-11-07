// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.ability.passive;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.classsystem.PlayerClass;

/** 1.11.4 (P1): Охотник — «Хищник» (predator). */
public final class HunterPassives extends BaseClassPassive {

    public HunterPassives(RaskolClasses plugin) {
        super(plugin);
    }

    @Override
    public PlayerClass playerClass() {
        return PlayerClass.HUNTER;
    }

    @Override
    public void onDamageOut(EntityDamageByEntityEvent event, Player attacker, LivingEntity target, double damage) {
        if (!enabled("predator")) {
            return;
        }
        double threshold = cfgD("predator", "threshold", 0.80);
        if (hpFraction(attacker) >= threshold) {
            event.setDamage(damage * cfgD("predator", "multiplier", 1.20));
            plugin.getFx().procByKey(attacker, "🐺 Хищник!", "predator");
        }
    }
}
