// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.spec.model;

/**
 * 1.14.0 «Спек 2.0»: эффект узла за ОДИН ранг; суммарный = value × rank.
 * kind — ключ маппинга в Spec2Service (см. javadoc сервиса):
 *   attr | resist | kit_base | kit_mult | kit_cd | cd | regen | avoid | proc |
 *   pen_phys_pct | pen_magic_pct | phys_dmg_pct | magic_dmg_pct |
 *   crit_melee_pct | heal_out_pct | exec_threshold_pct |
 *   dot_dur | dot_stacks | dot_mult | cc_power | cc_resist | cc_dur |
 *   unlock_ability (target = abilityId).
 */
public record Spec2Effect(String kind, String target, double value, double value2) {

    public static Spec2Effect of(String kind, String target, double value) {
        return new Spec2Effect(kind, target, value, 0.0);
    }

    public Spec2Effect scaledBy(int rank) {
        if (rank <= 0) {
            return new Spec2Effect(kind, target, 0.0, 0.0);
        }
        return new Spec2Effect(kind, target, value * rank, value2 * rank);
    }
}
