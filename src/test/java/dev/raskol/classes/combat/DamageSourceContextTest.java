// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("DamageSourceContext: атрибуция боевого события")
class DamageSourceContextTest {

    @Test
    @DisplayName("окружение не получает фиктивного атакующего и руки оружия")
    void environmentHasNoAttacker() {
        UUID target = UUID.randomUUID();
        DamageSourceContext context =
                new DamageSourceContext(null, UUID.randomUUID(), UUID.randomUUID(), target,
                        DamageSourceContext.SourceType.ENVIRONMENT, "FALL_DAMAGE",
                        DamageSourceContext.WeaponHand.OFF_HAND, false, false);

        assertNull(context.sourceEntityId());
        assertNull(context.attackerId());
        assertNull(context.ownerId());
        assertEquals(target, context.targetId());
        assertEquals(DamageSourceContext.WeaponHand.UNKNOWN, context.weaponHand());
    }

    @Test
    @DisplayName("контекст игрока по умолчанию связывает владельца с атакующим")
    void playerOwnerDefaultsToAttacker() {
        UUID player = UUID.randomUUID();
        DamageSourceContext context =
                new DamageSourceContext(player, player, null, UUID.randomUUID(),
                        DamageSourceContext.SourceType.PLAYER, " Tyr_Strike ",
                        null, false, false);

        assertEquals(player, context.ownerId());
        assertEquals("tyr_strike", context.abilityId());
        assertEquals(DamageSourceContext.WeaponHand.UNKNOWN, context.weaponHand());
    }

    @Test
    @DisplayName("контекст питомца хранит сущность питомца и владельца отдельно")
    void petKeepsEntityAndOwnerSeparate() {
        UUID pet = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        UUID target = UUID.randomUUID();

        DamageSourceContext context = DamageSourceContext.pet(pet, owner, target, "wolf_mark", true);

        assertEquals(DamageSourceContext.SourceType.PET, context.sourceType());
        assertEquals(pet, context.sourceEntityId());
        assertEquals(owner, context.attackerId());
        assertEquals(owner, context.ownerId());
        assertEquals(target, context.targetId());
        assertTrue(context.fromProc());
        assertFalse(context.reflected());
    }

    @Test
    @DisplayName("невалидный ID способности не попадает в боевой контекст")
    void invalidAbilityIdIsDiscarded() {
        DamageSourceContext context =
                new DamageSourceContext(null, null, null, null,
                        DamageSourceContext.SourceType.ABILITY, "../admin",
                        DamageSourceContext.WeaponHand.UNKNOWN, false, false);

        assertNull(context.abilityId());
    }

    @Test
    @DisplayName("reflection всегда помечен как отражённый источник")
    void reflectionIsExplicit() {
        DamageSourceContext context =
                new DamageSourceContext(null, null, null, null,
                        DamageSourceContext.SourceType.REFLECTION, null, null, true, false);

        assertTrue(context.reflected());
        assertTrue(context.fromProc());
    }

    @Test
    @DisplayName("withWeaponHand создаёт новый контекст и не меняет исходный")
    void handCopyIsImmutable() {
        DamageSourceContext original =
                new DamageSourceContext(null, null, null, null,
                        DamageSourceContext.SourceType.ABILITY, "aimed_shot",
                        null, false, false);
        DamageSourceContext updated = original.withWeaponHand(DamageSourceContext.WeaponHand.OFF_HAND);

        assertSame(DamageSourceContext.WeaponHand.UNKNOWN, original.weaponHand());
        assertEquals(DamageSourceContext.WeaponHand.OFF_HAND, updated.weaponHand());
        assertEquals(original.abilityId(), updated.abilityId());
    }
}
