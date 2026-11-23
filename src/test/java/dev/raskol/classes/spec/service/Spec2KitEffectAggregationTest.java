// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.spec.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.raskol.classes.spec.model.Spec2Node;
import dev.raskol.classes.spec.registry.Spec2Registry;

@DisplayName("Spec2Service: ability-specific kit effect aggregation")
class Spec2KitEffectAggregationTest {

    @Test
    @DisplayName("extra hits, extra arrows and agility are aggregated by ability ID and rank")
    void abilitySpecificEffectsScaleByRank() {
        Spec2Service.Agg agg = new Spec2Service.Agg();

        Spec2Node extraHits = node("fury", "fury_unstoppable_force");
        Spec2Node extraArrows = node("marksmanship", "mm_rapid_fire");
        Spec2Node agility = node("subtlety", "su_master_of_subtlety");

        Spec2Service.accumulate(agg, extraHits, 2);
        Spec2Service.accumulate(agg, extraArrows, 2);
        Spec2Service.accumulate(agg, agility, 3);

        assertEquals(2.0, agg.kitExtraHits.get("rampage"), 0.0001);
        assertEquals(2.0, agg.kitExtraArrows.get("arrow_fan"), 0.0001);
        assertEquals(30.0, agg.kitAttrAgi.get("shadow_dance"), 0.0001);
    }

    @Test
    @DisplayName("physical penetration and magic ward remain scoped to their ability IDs")
    void abilitySpecificPenetrationAndWardDoNotLeakToOtherAbilities() {
        Spec2Service.Agg agg = new Spec2Service.Agg();

        Spec2Service.accumulate(agg, node("marksmanship", "mm_piercing_shots"), 2);
        Spec2Service.accumulate(agg, node("guard", "guard_spell_ward"), 2);

        assertEquals(20.0, agg.kitPenPhys.get("aimed_shot"), 0.0001);
        assertEquals(50.0, agg.kitResistMagic.get("balder_skin"), 0.0001);
        assertEquals(0.0, agg.kitPenPhys.getOrDefault("arrow_fan", 0.0), 0.0001);
        assertEquals(0.0, agg.kitResistMagic.getOrDefault("shield_wall", 0.0), 0.0001);
    }

    @Test
    @DisplayName("trap damage and cooldown passives aggregate by rank")
    void trapPassivesAggregateByRank() {
        Spec2Service.Agg agg = new Spec2Service.Agg();

        Spec2Service.accumulate(agg, node("survival", "sv_trap_mastery"), 3);
        Spec2Service.accumulate(agg, node("survival", "sv_resourcefulness"), 2);

        assertEquals(30.0, agg.proc.get("trap_dmg_pct"), 0.0001);
        assertEquals(20.0, agg.proc.get("cd_trap_pct"), 0.0001);
    }

    private static Spec2Node node(String treeId, String nodeId) {
        var tree = Spec2Registry.treeOf(treeId);
        assertNotNull(tree, "дерево отсутствует: " + treeId);
        Spec2Node node = tree.find(nodeId);
        assertNotNull(node, "узел отсутствует: " + treeId + "/" + nodeId);
        return node;
    }
}
