// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.passive;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.classsystem.PlayerClass;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityRegainHealthEvent;

/** 1.11.4 (P1): Жрец — «Благодать» (grace). */
public final class PriestPassives extends BaseClassPassive {

    public PriestPassives(RaskolClasses plugin) {
        super(plugin);
    }

    @Override
    public PlayerClass playerClass() {
        return PlayerClass.PRIEST;
    }

    @Override
    public void onHealOut(EntityRegainHealthEvent event, Player healer) {
        if (!enabled("grace")) {
            return;
        }
        double mult = cfgD("grace", "multiplier", 1.15)
                + plugin.getTalentService().procBonus(healer.getUniqueId(), "grace");
        event.setAmount(event.getAmount() * mult);
        plugin.getFx().procByKey(healer, "✚ Благодать", "grace");
    }
}
