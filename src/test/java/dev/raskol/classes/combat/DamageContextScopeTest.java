// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Combat source context scopes")
class DamageContextScopeTest {

    @Test
    @DisplayName("вложенный ability ID восстанавливает предыдущий контекст")
    void nestedAbilityContextRestoresPrevious() {
        assertNull(CombatService.currentAbilityId());

        CombatService.withAbilityContext("aimed_shot", () -> {
            assertEquals("aimed_shot", CombatService.currentAbilityId());
            CombatService.withAbilityContext("arrow_fan", () -> {
                assertEquals("arrow_fan", CombatService.currentAbilityId());
                return null;
            });
            assertEquals("aimed_shot", CombatService.currentAbilityId());
            return null;
        });

        assertNull(CombatService.currentAbilityId());
    }

    @Test
    @DisplayName("ability ID очищается после исключения")
    void abilityContextRestoresAfterException() {
        assertThrows(
                IllegalStateException.class,
                () -> CombatService.withAbilityContext("aimed_shot", () -> {
                    assertEquals("aimed_shot", CombatService.currentAbilityId());
                    throw new IllegalStateException("expected");
                }));
        assertNull(CombatService.currentAbilityId());
    }

    @Test
    @DisplayName("вложенный source context восстанавливает внешний источник")
    void nestedSourceContextRestoresPrevious() {
        UUID caster = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        DamageSourceContext ability = DamageSourceContext.ability(caster, target, "aimed_shot", false);
        DamageSourceContext trap = DamageSourceContext.trap(UUID.randomUUID(), caster, target, "explosive_trap", false);

        assertNull(CombatService.currentSourceContext());
        CombatService.withSourceContext(ability, () -> {
            assertSame(ability, CombatService.currentSourceContext());
            CombatService.withSourceContext(trap, () -> {
                assertSame(trap, CombatService.currentSourceContext());
                return null;
            });
            assertSame(ability, CombatService.currentSourceContext());
            return null;
        });
        assertNull(CombatService.currentSourceContext());
    }
}
