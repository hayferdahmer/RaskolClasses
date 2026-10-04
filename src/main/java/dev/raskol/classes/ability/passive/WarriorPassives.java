// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.ability.passive;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.classsystem.PlayerClass;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/**
 * 1.11.4 (P1): Воин — «Казнь» (execute_passive).
 * 1.14.0 (Б8): procBonus читается из Spec2Service (proc-узлы деревьев).
 */
public final class WarriorPassives extends BaseClassPassive {

    public WarriorPassives(RaskolClasses plugin) {
        super(plugin);
    }

    @Override
    public PlayerClass playerClass() {
        return PlayerClass.WARRIOR;
    }

    @Override
    public void onDamageOut(EntityDamageByEntityEvent event, Player attacker,
                            LivingEntity target, double damage) {
        if (!enabled("execute_passive")) {
            return;
        }
        double threshold = cfgD("execute_passive", "threshold", 0.20)
                + plugin.getSpec2Service().execThresholdBonus(attacker.getUniqueId()) / 100.0;
        if (hpFraction(target) > threshold) {
            return;
        }
        double chance = cfgD("execute_passive", "chance", 0.20)
                + plugin.getSpec2Service().procBonus(attacker.getUniqueId(), "execute_passive");
        int cd = cfgI("execute_passive", "cooldown-seconds", 6);
        if (roll(chance) && procCdOk(attacker.getUniqueId(), "execute_passive", cd)) {
            event.setDamage(damage * cfgD("execute_passive", "multiplier", 3.0));
            plugin.getFx().procByKey(attacker, "⚡ Казнь ×3!", "execute_passive");
        }
    }
}
