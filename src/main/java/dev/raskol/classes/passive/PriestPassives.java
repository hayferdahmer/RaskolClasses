// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.passive;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.classsystem.PlayerClass;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityRegainHealthEvent;

/**
 * 1.11.4 (P1): Жрец — «Благодать» (grace).
 * 1.14.0 (Б8): procBonus из Spec2Service; роль HEALER добавляет множитель
 *         в PriestAbilities.applyHeal (не здесь).
 */
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
                + plugin.getSpec2Service().procBonus(healer.getUniqueId(), "grace")
                + plugin.getSpec2Service().healOutPercent(healer.getUniqueId()) / 100.0;
        event.setAmount(event.getAmount() * mult);
        plugin.getFx().procByKey(healer, "✚ Благодать", "grace");
    }
}
