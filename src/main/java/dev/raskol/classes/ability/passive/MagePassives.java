// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.ability.passive;

import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.classsystem.PlayerClass;

/**
 * 1.14.0 (Б8): Маг — «Пропитан маной» (mana_soaked). Файл отсутствовал в 1.11.4 —
 * пассивка была мертва; теперь живая: при мане ≥ threshold входящий урон
 * снижается на reduction% (+ proc-бонус spec2-узлов). Внутренний КД тега 2 с.
 * Конфиг: classes.MAGE.passives.mana_soaked.{enabled,threshold,reduction}.
 *
 * 1.15.7 (Волна 7c): без изменений. Файл корректен.
 */
public final class MagePassives extends BaseClassPassive {

    public MagePassives(RaskolClasses plugin) {
        super(plugin);
    }

    @Override
    public PlayerClass playerClass() {
        return PlayerClass.MAGE;
    }

    @Override
    public void onDamageIn(EntityDamageByEntityEvent event, Player victim, double damage) {
        if (!enabled("mana_soaked")) {
            return;
        }
        double threshold = cfgD("mana_soaked", "threshold", 50.0);
        if (plugin.getResources().getValue(victim.getUniqueId()) < threshold) {
            return;
        }
        double reduction = cfgD("mana_soaked", "reduction", 0.15)
                + plugin.getSpec2Service().procBonus(victim.getUniqueId(), "mana_soaked");
        if (reduction <= 0.0) {
            return;
        }
        if (!procCdOk(victim.getUniqueId(), "mana_soaked", 2)) {
            return;
        }
        event.setDamage(damage * (1.0 - Math.min(0.75, reduction)));
        plugin.getFx().procByKey(victim, "💠 Пропитан маной", "mana_soaked");
    }
}
