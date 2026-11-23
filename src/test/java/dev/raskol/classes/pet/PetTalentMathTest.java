// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.pet;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.raskol.classes.spec.model.Spec2Node;
import dev.raskol.classes.spec.registry.Spec2Registry;

@DisplayName("Pet talent combat math")
class PetTalentMathTest {

    @Test
    @DisplayName("chance is finite and clamped to the valid probability range")
    void chanceIsClamped() {
        assertEquals(0.0, PetTalentMath.chance(-5.0), 0.0001);
        assertEquals(25.0, PetTalentMath.chance(25.0), 0.0001);
        assertEquals(100.0, PetTalentMath.chance(150.0), 0.0001);
        assertEquals(0.0, PetTalentMath.chance(Double.NaN), 0.0001);
    }

    @Test
    @DisplayName("pet physical rank bonus and critical talent compose once")
    void damageMultipliersCompose() {
        assertEquals(1.20, PetTalentMath.outgoingDamageMultiplier(20.0, false, false), 0.0001);
        assertEquals(1.80, PetTalentMath.outgoingDamageMultiplier(20.0, true, false), 0.0001);
        assertEquals(2.40, PetTalentMath.outgoingDamageMultiplier(20.0, true, true), 0.0001);
        assertEquals(1.00, PetTalentMath.outgoingDamageMultiplier(Double.NaN, false, false), 0.0001);
    }

    @Test
    @DisplayName("implemented pet talents expose the expected rank-scaled effect contracts")
    void registeredPetTalentContractsMatchHandlers() {
        assertEffect("bm_wild_bond", "proc_pet_copy", "pet", 0.05);
        assertEffect("bm_cobra_reflexes", "pet_dodge_pct", "self", 5.0);
        assertEffect("bm_spirit_bond", "proc_pet_heal", "pet", 0.10);
        assertEffect("bm_go_for_the_throat", "proc_pet_crit_bonus", "pet", 0.20);
        assertEffect("bm_primal_fury", "pet_phys_dmg_pct", "self", 10.0);
        assertEffect("bm_pack_leader", "pet_crit_pct", "self", 5.0);
    }

    private static void assertEffect(String nodeId, String kind, String target, double value) {
        var tree = Spec2Registry.treeOf("beastmaster");
        org.junit.jupiter.api.Assertions.assertNotNull(tree, "дерево Повелителя зверей отсутствует");
        Spec2Node node = tree.find(nodeId);
        org.junit.jupiter.api.Assertions.assertNotNull(node, "узел отсутствует: " + nodeId);
        assertEquals(kind, node.effect().kind(), nodeId);
        assertEquals(target, node.effect().target(), nodeId);
        assertEquals(value, node.effect().value(), 0.0001, nodeId);
    }
}
