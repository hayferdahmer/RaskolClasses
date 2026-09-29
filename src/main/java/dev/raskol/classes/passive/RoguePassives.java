// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.passive;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.classsystem.PlayerClass;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/**
 * 1.11.4 (P1): Разбойник — «Отравленные клинки» + «Садизм».
 * 1.12.6 (UX/VFX школ): миграция PotionEffect POISON → школьный DoT NATURE
 *         (poison_passive из dots.* реестра). Атрибуция, стеки и кап DoT-DPS
 *         идут через DotService; очищение жреца (School.NATURE) снимает яд.
 *         Длительность/стеки берутся из dots.poison_passive.*, конфиг пассивки
 *         читает только шанс и КД (plus ключ dot: для выбора DoT-определения).
 */
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
        // «Отравленные клинки»: 30% шанс → DoT NATURE poison_passive (2 с, 1 стек)
        if (enabled("poisoned_blades")) {
            double chance = cfgD("poisoned_blades", "chance", 0.30)
                    + plugin.getTalentService().procBonus(attacker.getUniqueId(), "poisoned_blades");
            int cd = cfgI("poisoned_blades", "cooldown-seconds", 3);
            String dotId = cfgS("poisoned_blades", "dot", "poison_passive");
            if (roll(chance) && procCdOk(attacker.getUniqueId(), "poisoned_blades", cd)) {
                // 1.12.6: школьный DoT вместо ванильного PotionEffect POISON.
                // applyById сам проверяет canHit, школу, иммунитеты, cap-DPS.
                plugin.getCombat().dots().applyById(attacker, target, dotId);
                plugin.getFx().procByKey(attacker, "☠ Яд!", "poisoned_blades");
            }
        }
        // «Садизм»: +3 урона при атаке со спины (КД 2 с)
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
