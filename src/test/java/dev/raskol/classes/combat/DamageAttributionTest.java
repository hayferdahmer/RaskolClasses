// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class DamageAttributionTest {

    private static final double EPSILON = 1.0e-9;

    @Test
    void excludesNestedDamageFromOuterChannel() {
        Object target = new Object();
        DamageAttribution.Scope outer = DamageAttribution.begin(target, 20.0);
        DamageAttribution.Scope nested = DamageAttribution.begin(target, 20.0);

        assertEquals(2.0, nested.finish(18.0), EPSILON);
        assertEquals(0.0, outer.finish(18.0), EPSILON);
    }

    @Test
    void preservesOuterDamageInAdditionToNestedDamage() {
        Object target = new Object();
        DamageAttribution.Scope outer = DamageAttribution.begin(target, 20.0);
        DamageAttribution.Scope nested = DamageAttribution.begin(target, 20.0);

        assertEquals(2.0, nested.finish(18.0), EPSILON);
        assertEquals(3.0, outer.finish(15.0), EPSILON);
    }

    @Test
    void excludesDeeplyNestedDamageAtEveryLevel() {
        Object target = new Object();
        DamageAttribution.Scope outer = DamageAttribution.begin(target, 30.0);
        DamageAttribution.Scope middle = DamageAttribution.begin(target, 30.0);
        DamageAttribution.Scope inner = DamageAttribution.begin(target, 30.0);

        assertEquals(3.0, inner.finish(27.0), EPSILON);
        assertEquals(2.0, middle.finish(25.0), EPSILON);
        assertEquals(5.0, outer.finish(20.0), EPSILON);
    }

    @Test
    void doesNotAttributeDamageToAnotherTarget() {
        Object outerTarget = new Object();
        Object otherTarget = new Object();
        DamageAttribution.Scope outer = DamageAttribution.begin(outerTarget, 20.0);
        DamageAttribution.Scope nested = DamageAttribution.begin(otherTarget, 10.0);

        assertEquals(2.0, nested.finish(8.0), EPSILON);
        assertEquals(4.0, outer.finish(16.0), EPSILON);
    }

    @Test
    void propagatesThroughDifferentTargetScopesToMatchingAncestor() {
        Object outerTarget = new Object();
        Object middleTarget = new Object();
        DamageAttribution.Scope outer = DamageAttribution.begin(outerTarget, 20.0);
        DamageAttribution.Scope middle = DamageAttribution.begin(middleTarget, 10.0);
        DamageAttribution.Scope nestedSameTarget = DamageAttribution.begin(outerTarget, 20.0);

        assertEquals(3.0, nestedSameTarget.finish(17.0), EPSILON);
        assertEquals(0.0, middle.finish(10.0), EPSILON);
        assertEquals(0.0, outer.finish(17.0), EPSILON);
    }

    @Test
    void handlesSiblingNestedDamageCalls() {
        Object target = new Object();
        DamageAttribution.Scope outer = DamageAttribution.begin(target, 20.0);
        DamageAttribution.Scope first = DamageAttribution.begin(target, 20.0);
        assertEquals(1.0, first.finish(19.0), EPSILON);
        DamageAttribution.Scope second = DamageAttribution.begin(target, 19.0);
        assertEquals(2.0, second.finish(17.0), EPSILON);

        assertEquals(0.0, outer.finish(17.0), EPSILON);
    }

    @Test
    void cancelledNestedCallContributesNoDamage() {
        Object target = new Object();
        DamageAttribution.Scope outer = DamageAttribution.begin(target, 20.0);
        DamageAttribution.Scope cancelled = DamageAttribution.begin(target, 20.0);

        assertEquals(0.0, cancelled.finish(20.0), EPSILON);
        assertEquals(2.0, outer.finish(18.0), EPSILON);
    }

    @Test
    void attributionContextsAreIsolatedBetweenThreads() throws InterruptedException {
        Object target = new Object();
        DamageAttribution.Scope outer = DamageAttribution.begin(target, 20.0);
        double[] workerDamage = {-1.0};

        Thread worker = new Thread(() -> {
            DamageAttribution.Scope workerScope = DamageAttribution.begin(target, 10.0);
            workerDamage[0] = workerScope.finish(8.0);
        });
        worker.start();
        worker.join();

        assertEquals(2.0, workerDamage[0], EPSILON);
        assertEquals(3.0, outer.finish(17.0), EPSILON);
    }

    @Test
    void enforcesSingleFinishAndLifoOrder() {
        Object target = new Object();
        DamageAttribution.Scope outer = DamageAttribution.begin(target, 20.0);
        DamageAttribution.Scope nested = DamageAttribution.begin(target, 20.0);

        assertThrows(IllegalStateException.class, () -> outer.finish(19.0));
        assertEquals(1.0, nested.finish(19.0), EPSILON);
        assertEquals(0.0, outer.finish(19.0), EPSILON);
        assertThrows(IllegalStateException.class, () -> outer.finish(19.0));
    }
}
