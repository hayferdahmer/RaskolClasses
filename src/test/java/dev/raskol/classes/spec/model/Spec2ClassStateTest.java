// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.spec.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import dev.raskol.classes.classsystem.PlayerClass;

class Spec2ClassStateTest {

    @Test
    void unresolvedClassPreservesMainSpecAndEveryStoredTree() {
        Spec2ClassState.ReconcilePlan plan =
                Spec2ClassState.plan(null, "fire", List.of("arcane", "fire", "frost"));

        assertFalse(plan.classResolved());
        assertFalse(plan.clearMainSpec());
        assertTrue(plan.foreignTreeIds().isEmpty());
    }

    @Test
    void matchingClassPreservesMainSpecAndItsTrees() {
        Spec2ClassState.ReconcilePlan plan =
                Spec2ClassState.plan(PlayerClass.MAGE, "fire", List.of("arcane", "fire", "frost"));

        assertTrue(plan.classResolved());
        assertFalse(plan.clearMainSpec());
        assertTrue(plan.foreignTreeIds().isEmpty());
    }

    @Test
    void confirmedClassChangeClearsMainSpecAndForeignTrees() {
        Spec2ClassState.ReconcilePlan plan =
                Spec2ClassState.plan(PlayerClass.WARLOCK, "fire", List.of("arcane", "fire", "affliction"));

        assertTrue(plan.classResolved());
        assertTrue(plan.clearMainSpec());
        assertEquals(List.of("arcane", "fire"), plan.foreignTreeIds());
    }

    @Test
    void missingMainSpecDoesNotTriggerReset() {
        Spec2ClassState.ReconcilePlan plan =
                Spec2ClassState.plan(PlayerClass.WARLOCK, null, List.of("affliction"));

        assertTrue(plan.classResolved());
        assertFalse(plan.clearMainSpec());
        assertTrue(plan.foreignTreeIds().isEmpty());
    }

    @Test
    void roleCompatibilityRequiresKnownSpecAndMatchingClass() {
        assertTrue(Spec2ClassState.isMainSpecCompatible(PlayerClass.MAGE, "frost"));
        assertFalse(Spec2ClassState.isMainSpecCompatible(PlayerClass.WARLOCK, "frost"));
        assertFalse(Spec2ClassState.isMainSpecCompatible(PlayerClass.MAGE, "unknown"));
        assertFalse(Spec2ClassState.isMainSpecCompatible(null, "frost"));
    }
}
