// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.ability.passive;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.classsystem.PlayerClass;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/**
 * 1.11.4 (P1): Разбойник — «Отравленные клинки» + «Садизм».
 * 1.12.6: миграция PotionEffect POISON → школьный DoT NATURE (poison_passive).
 * 1.14.0 (Б7): procBonus читается из Spec2Service (proc-узлы деревьев),
 *         TalentService больше не используется.
 */
public final class RoguePassives extends BaseClassPassive {

    public RoguePassives(RaskolClasses plugin) {
        super(plugin);
    }

    @Override
    public PlayerClass playerClass() {
        return PlayerClass.ROGUE;
    }

    private String cfgS(String passiveId, String key, String def) {
        String v = plugin.getConfig().getString(
                "classes." + playerClass().name() + ".passives." + passiveId + "." + key, def);
        return (v != null && !v.isEmpty()) ? v : def;
    }

    @Override
    public void onDamageOut(EntityDamageByEntityEvent event, Player attacker,
                            LivingEntity target, double damage) {
        // «Отравленные клинки»: 30% шанс → DoT NATURE poison_passive (2 с, 1 стек)
        if (enabled("poisoned_blades")) {
            double chance = cfgD("poisoned_blades", "chance", 0.30)
                    + plugin.getSpec2Service().procBonus(attacker.getUniqueId(), "poisoned_blades");
            int cd = cfgI("poisoned_blades", "cooldown-seconds", 3);
            String dotId = cfgS("poisoned_blades", "dot", "poison_passive");
            if (roll(chance) && procCdOk(attacker.getUniqueId(), "poisoned_blades", cd)) {
                plugin.getCombat().dots().applyById(attacker, target, dotId);
                plugin.getFx().procByKey(attacker, "☠ Яд!", "poisoned_blades");
            }
        }
        // «Садизм»: +3 урона при атаке со спины (КД 2 с)
        if (enabled("sadism") && isBehind(target, attacker)) {
            double bonus = cfgD("sadism", "bonus", 3.0)
                    + plugin.getSpec2Service().procBonus(attacker.getUniqueId(), "sadism");
            int cd = cfgI("sadism", "cooldown-seconds", 2);
            if (procCdOk(attacker.getUniqueId(), "sadism", cd)) {
                event.setDamage(damage + bonus);
                plugin.getFx().procByKey(attacker, "🗡 В спину!", "sadism");
            }
        }
    }
}
