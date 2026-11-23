// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.combat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Ranged ability critical routing")
class RangedAbilityCritRoutingTest {

    @Test
    @DisplayName("hunter arrow abilities use ranged critical chance")
    void hunterArrowAbilitiesUseRangedCritChance() {
        UUID attacker = UUID.randomUUID();
        UUID target = UUID.randomUUID();

        assertTrue(isRanged(attacker, target, "aimed_shot"));
        assertTrue(isRanged(attacker, target, "arrow_fan"));
        assertTrue(isRanged(attacker, target, "arrow_rain"));
        assertTrue(isRanged(attacker, target, "chimera_shot"));
    }

    @Test
    @DisplayName("melee and trap abilities do not use ranged critical chance")
    void meleeAndTrapAbilitiesUseOtherCriticalRules() {
        UUID attacker = UUID.randomUUID();
        UUID target = UUID.randomUUID();

        assertFalse(isRanged(attacker, target, "rampage"));
        assertFalse(isRanged(attacker, target, "explosive_trap"));
        assertFalse(CombatService.isRangedAbility(DamageSourceContext.infer(null, null, false, false)));
    }

    private static boolean isRanged(UUID attacker, UUID target, String abilityId) {
        return CombatService.isRangedAbility(DamageSourceContext.ability(attacker, target, abilityId, false));
    }
}
