// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.ability.passive;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.event.CustomHealEvent;
import org.bukkit.entity.Player;

/**
 * 1.11.4 (P1): Жрец — «Благодать» (grace).
 * 1.14.0 (Б8): procBonus из Spec2Service; роль HEALER добавляет множитель
 *         в PriestAbilities.applyHeal (не здесь).
 * 1.14.1 (Волна 1): слагаемое healOutPercent УБРАНО из «Благодати» — универсальный
 *         множитель heal_out_pct теперь применяется в Spec2RoleListener.onCustomHeal.
 *         Формула «Благодати»: multiplier + proc-узлы (аддитивно, как в 1.14.0).
 *
 * 1.14.7 (Sprint 4, P1-1 миграция): перенос с onHealOut(EntityRegainHealthEvent)
 *         на onCustomHealOut(CustomHealEvent). Ванильный regain (еда/зелья) больше
 *         не получает множителя «Благодати» — это был misattribution-баг P1-2,
 *         когда пассивка целителя влияла на лечение без атрибуции. Теперь «Благодать»
 *         применяется только к лечению китов (CustomHealEvent через HpBarService.heal),
 *         где целитель атрибутирован; в порядке множителей идёт ПЕРВОЙ (приоритет
 *         HIGHEST в PassiveListener), ДО spec2-множителей Spec2RoleListener (HIGH).
 */
public final class PriestPassives extends BaseClassPassive {

    public PriestPassives(RaskolClasses plugin) {
        super(plugin);
    }

    @Override
    public PlayerClass playerClass() {
        return PlayerClass.PRIEST;
    }

    /**
     * 1.14.7 (Sprint 4, P1-1): «Благодать» на пути лечения китов (CustomHealEvent).
     * Множитель = baseMultiplier + Σ procBonus из spec2-узлов; применяется к
     * базовому heal-amount ДО spec2-множителей (роль HEALER/heal_out_pct).
     */
    @Override
    public void onCustomHealOut(CustomHealEvent event, Player healer) {
        if (!enabled("grace")) {
            return;
        }
        double mult = cfgD("grace", "multiplier", 1.15)
                + plugin.getSpec2Service().procBonus(healer.getUniqueId(), "grace");
        if (mult <= 1.0) {
            return;
        }
        event.setAmount(event.getAmount() * mult);
        plugin.getFx().procByKey(healer, "✚ Благодать", "grace");
    }
}
