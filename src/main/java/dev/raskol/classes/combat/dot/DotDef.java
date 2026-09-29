// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.combat.dot;

import dev.raskol.classes.combat.school.School;

/**
 * 1.12.4: определение периодического урона (DoT).
 *  - dps — урон в СЕКУНДУ в formula-единицах (до митигации/капов);
 *  - periodMillis — период тика (дефолт 1000);
 *  - durationMillis — полное время жизни;
 *  - maxStacks — потолок стеков при повторном наложении тем же владельцем;
 *  - school — школа урона (иммунитеты/множители/стихийный резист);
 *  - sourceAbility — id способности-источника (атрибуция, vfx, логи).
 * Immutable record.
 */
public record DotDef(
        String id,
        School school,
        double dps,
        long periodMillis,
        long durationMillis,
        int maxStacks,
        String sourceAbility
) {
    public static DotDef of(String id, School school, double dps,
                            long durationMillis, int maxStacks, String sourceAbility) {
        return new DotDef(id, school, dps, 1000L, durationMillis, maxStacks, sourceAbility);
    }

    public DotDef withDps(double newDps) {
        return new DotDef(id, school, newDps, periodMillis, durationMillis, maxStacks, sourceAbility);
    }
}
