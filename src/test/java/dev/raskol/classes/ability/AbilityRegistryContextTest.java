// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.ability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.raskol.classes.combat.CombatService;

@DisplayName("AbilityRegistry source context")
class AbilityRegistryContextTest {

    @Test
    @DisplayName("cast boundary exposes the active ability ID and restores the outer scope")
    void castBoundaryScopesAbilityId() {
        assertNull(CombatService.currentAbilityId());

        AbilityRegistry.withAbilityScope("aimed_shot", () -> {
            assertEquals("aimed_shot", CombatService.currentAbilityId());
            AbilityRegistry.withAbilityScope("arrow_fan", () -> {
                assertEquals("arrow_fan", CombatService.currentAbilityId());
                return true;
            });
            assertEquals("aimed_shot", CombatService.currentAbilityId());
            return true;
        });

        assertNull(CombatService.currentAbilityId());
    }
}
