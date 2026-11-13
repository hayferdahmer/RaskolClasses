// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.targeting.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import dev.raskol.classes.targeting.application.port.TargetSelectionCheck;
import dev.raskol.classes.targeting.domain.TargetInteractionHand;
import dev.raskol.classes.targeting.domain.TargetSelectionSource;

class CompositeTargetSelectionPolicyTest {

    private final TargetInteractionInput input = new TargetInteractionInput(
            UUID.randomUUID(),
            UUID.randomUUID(),
            TargetSelectionSource.ENTITY_RIGHT_CLICK,
            TargetInteractionHand.MAIN_HAND);

    @Test
    void acceptsOnlyWhenEveryCheckAccepts() {
        AtomicInteger evaluations = new AtomicInteger();
        CompositeTargetSelectionPolicy policy = new CompositeTargetSelectionPolicy(List.of(
                candidate -> {
                    evaluations.incrementAndGet();
                    return TargetSelectionPolicyResult.accepted();
                },
                candidate -> {
                    evaluations.incrementAndGet();
                    return TargetSelectionPolicyResult.accepted();
                }));

        assertInstanceOf(TargetSelectionPolicyResult.Accepted.class, policy.evaluate(input));
        assertEquals(2, evaluations.get());
    }

    @Test
    void stopsAtFirstRejection() {
        AtomicInteger evaluations = new AtomicInteger();
        CompositeTargetSelectionPolicy policy = new CompositeTargetSelectionPolicy(List.of(
                candidate -> TargetSelectionPolicyResult.rejected(TargetSelectionRejectionReason.POLICY_REJECTED),
                candidate -> {
                    evaluations.incrementAndGet();
                    return TargetSelectionPolicyResult.accepted();
                }));

        TargetSelectionPolicyResult result = policy.evaluate(input);

        assertEquals(
                TargetSelectionRejectionReason.POLICY_REJECTED,
                assertInstanceOf(TargetSelectionPolicyResult.Rejected.class, result)
                        .reason());
        assertEquals(0, evaluations.get());
    }

    @Test
    void stopsAtFirstUnavailableResult() {
        AtomicInteger evaluations = new AtomicInteger();
        CompositeTargetSelectionPolicy policy = new CompositeTargetSelectionPolicy(List.of(
                candidate -> TargetSelectionPolicyResult.unavailable(
                        TargetSelectionUnavailableReason.POLICY_PROVIDER_UNAVAILABLE),
                candidate -> {
                    evaluations.incrementAndGet();
                    return TargetSelectionPolicyResult.accepted();
                }));

        assertInstanceOf(TargetSelectionPolicyResult.Unavailable.class, policy.evaluate(input));
        assertEquals(0, evaluations.get());
    }

    @Test
    void convertsProviderRuntimeFailureToUnavailable() {
        CompositeTargetSelectionPolicy policy = new CompositeTargetSelectionPolicy(List.of(candidate -> {
            throw new IllegalStateException("internal provider details");
        }));

        TargetSelectionPolicyResult result = policy.evaluate(input);

        assertEquals(
                TargetSelectionUnavailableReason.POLICY_PROVIDER_UNAVAILABLE,
                assertInstanceOf(TargetSelectionPolicyResult.Unavailable.class, result)
                        .reason());
    }

    @Test
    void preservesCheckOrderAndDefensivelyCopiesConfiguration() {
        List<Integer> order = new ArrayList<>();
        List<TargetSelectionCheck> configured = new ArrayList<>();
        configured.add(candidate -> {
            order.add(1);
            return TargetSelectionPolicyResult.accepted();
        });
        configured.add(candidate -> {
            order.add(2);
            return TargetSelectionPolicyResult.accepted();
        });
        CompositeTargetSelectionPolicy policy = new CompositeTargetSelectionPolicy(configured);
        configured.clear();

        policy.evaluate(input);

        assertEquals(List.of(1, 2), order);
    }

    @Test
    void rejectsNullConfigurationAndInput() {
        assertThrows(NullPointerException.class, () -> new CompositeTargetSelectionPolicy(null));
        assertThrows(IllegalArgumentException.class, () -> new CompositeTargetSelectionPolicy(List.of()));
        assertThrows(
                NullPointerException.class,
                () -> new CompositeTargetSelectionPolicy(java.util.Arrays.asList((TargetSelectionCheck) null)));
        assertThrows(
                NullPointerException.class,
                () -> new CompositeTargetSelectionPolicy(List.of(candidate -> TargetSelectionPolicyResult.accepted()))
                        .evaluate(null));
    }
}
