// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.passive;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.classsystem.PlayerClass;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/** 1.11.4 (P1): Разбойник — «Отравленные клинки» + «Садизм». */
public final class RoguePassives extends BaseClassPassive {

    public RoguePassives(RaskolClasses plugin) {
        super(plugin);
    }

    @Override
    public PlayerClass playerClass() {
        return PlayerClass.ROGUE;
    }

    @Override
    public void onDamageOut(EntityDamageByEntityEvent event, Player attacker,
                            LivingEntity target, double damage) {
        if (enabled("poisoned_blades")) {
            double chance = cfgD("poisoned_blades", "chance", 0.30)
                    + plugin.getTalentService().procBonus(attacker.getUniqueId(), "poisoned_blades");
            int cd = cfgI("poisoned_blades", "cooldown-seconds", 3);
            int dur = cfgI("poisoned_blades", "duration-seconds", 2);
            if (roll(chance) && procCdOk(attacker.getUniqueId(), "poisoned_blades", cd)) {
                target.addPotionEffect(new PotionEffect(PotionEffectType.POISON, dur * 20, 0));
                plugin.getFx().procByKey(attacker, "☠ Яд!", "poisoned_blades");
            }
        }
        if (enabled("sadism") && isBehind(target, attacker)) {
            double bonus = cfgD("sadism", "bonus", 3.0)
                    + plugin.getTalentService().procBonus(attacker.getUniqueId(), "sadism");
            int cd = cfgI("sadism", "cooldown-seconds", 2);
            if (procCdOk(attacker.getUniqueId(), "sadism", cd)) {
                event.setDamage(damage + bonus);
                plugin.getFx().procByKey(attacker, "🗡 В спину!", "sadism");
            }
        }
    }
}
