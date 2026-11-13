// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.targeting.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.raskol.classes.targeting.application.port.TargetSelectionPolicy;
import dev.raskol.classes.targeting.domain.TargetIdentity;
import dev.raskol.classes.targeting.domain.TargetInteractionHand;
import dev.raskol.classes.targeting.domain.TargetSelection;
import dev.raskol.classes.targeting.domain.TargetSelectionSource;
import dev.raskol.classes.targeting.infrastructure.memory.InMemoryTargetSelectionStore;

class TargetingApplicationServiceTest {

    private InMemoryTargetSelectionStore store;
    private AtomicLong clock;
    private TargetSelectionPolicy policy;
    private TargetingApplicationService service;
    private UUID actorId;

    @BeforeEach
    void setUp() {
        store = new InMemoryTargetSelectionStore();
        clock = new AtomicLong(1_000L);
        policy = input -> TargetSelectionPolicyResult.accepted();
        service = new TargetingApplicationService(store, policy, clock::get);
        actorId = UUID.randomUUID();
    }

    @Test
    void selectsCandidateAfterPolicyAccepts() {
        UUID targetId = UUID.randomUUID();

        TargetSelectionOutcome outcome = service.select(input(targetId));

        TargetSelection selection = assertInstanceOf(TargetSelectionOutcome.Selected.class, outcome).selection();
        assertEquals(actorId, selection.ownerId());
        assertEquals(new TargetIdentity(targetId), selection.target());
        assertEquals(1_000L, selection.selectedAtMillis());
        assertEquals(Optional.of(selection), service.selectedTargetOf(actorId));
    }

    @Test
    void repeatedSelectionIsIdempotentAndPreservesTimestamp() {
        UUID targetId = UUID.randomUUID();
        TargetSelection first = assertInstanceOf(
                        TargetSelectionOutcome.Selected.class, service.select(input(targetId)))
                .selection();
        clock.set(2_000L);

        TargetSelectionOutcome repeated = service.select(input(targetId));

        TargetSelection existing =
                assertInstanceOf(TargetSelectionOutcome.AlreadySelected.class, repeated).selection();
        assertSame(first, existing);
        assertEquals(1_000L, existing.selectedAtMillis());
        assertTrue(store.findByOwner(actorId).isPresent());
    }

    @Test
    void repeatedSelectionStillRunsPolicyValidation() {
        UUID targetId = UUID.randomUUID();
        service.select(input(targetId));
        AtomicInteger evaluations = new AtomicInteger();
        TargetingApplicationService validatingService = new TargetingApplicationService(
                store,
                input -> {
                    evaluations.incrementAndGet();
                    return TargetSelectionPolicyResult.rejected(TargetSelectionRejectionReason.POLICY_REJECTED);
                },
                clock::get);

        TargetSelectionOutcome outcome = validatingService.select(input(targetId));

        assertInstanceOf(TargetSelectionOutcome.Rejected.class, outcome);
        assertEquals(1, evaluations.get());
        assertTrue(service.selectedTargetOf(actorId).isPresent());
    }

    @Test
    void rejectedPolicyDoesNotReplaceExistingTarget() {
        UUID currentTarget = UUID.randomUUID();
        service.select(input(currentTarget));
        TargetingApplicationService rejectingService = new TargetingApplicationService(
                store,
                input -> TargetSelectionPolicyResult.rejected(TargetSelectionRejectionReason.POLICY_REJECTED),
                clock::get);

        TargetSelectionOutcome outcome = rejectingService.select(input(UUID.randomUUID()));

        assertInstanceOf(TargetSelectionOutcome.Rejected.class, outcome);
        assertEquals(new TargetIdentity(currentTarget), service.selectedTargetOf(actorId).orElseThrow().target());
    }

    @Test
    void unavailablePolicyDoesNotReplaceExistingTarget() {
        UUID currentTarget = UUID.randomUUID();
        service.select(input(currentTarget));
        TargetingApplicationService unavailableService = new TargetingApplicationService(
                store,
                input -> TargetSelectionPolicyResult.unavailable(
                        TargetSelectionUnavailableReason.POLICY_PROVIDER_UNAVAILABLE),
                clock::get);

        TargetSelectionOutcome outcome = unavailableService.select(input(UUID.randomUUID()));

        assertInstanceOf(TargetSelectionOutcome.Unavailable.class, outcome);
        assertEquals(new TargetIdentity(currentTarget), service.selectedTargetOf(actorId).orElseThrow().target());
    }

    @Test
    void rejectsSelfTargetWithoutCallingPolicy() {
        AtomicInteger evaluations = new AtomicInteger();
        TargetingApplicationService noPolicyService = new TargetingApplicationService(
                store,
                input -> {
                    evaluations.incrementAndGet();
                    return TargetSelectionPolicyResult.accepted();
                },
                clock::get);

        TargetSelectionOutcome outcome = noPolicyService.select(input(actorId));

        assertEquals(
                TargetSelectionRejectionReason.SELF_TARGET,
                assertInstanceOf(TargetSelectionOutcome.Rejected.class, outcome).reason());
        assertEquals(0, evaluations.get());
        assertTrue(service.selectedTargetOf(actorId).isEmpty());
    }

    @Test
    void rejectsOffHandAndUnsupportedSource() {
        TargetInteractionInput offHand = new TargetInteractionInput(
                actorId, UUID.randomUUID(), TargetSelectionSource.ENTITY_RIGHT_CLICK, TargetInteractionHand.OFF_HAND);
        TargetSelectionOutcome offHandResult = service.select(offHand);
        assertEquals(
                TargetSelectionRejectionReason.OFF_HAND_NOT_SUPPORTED,
                assertInstanceOf(TargetSelectionOutcome.Rejected.class, offHandResult).reason());

    }

    @Test
    void clearAndEndSessionOnlyAffectRequestedOwner() {
        UUID otherOwner = UUID.randomUUID();
        TargetSelection other = new TargetSelection(
                otherOwner,
                new TargetIdentity(UUID.randomUUID()),
                1_000L,
                TargetSelectionSource.ENTITY_RIGHT_CLICK);
        store.save(other);
        service.select(input(UUID.randomUUID()));

        assertTrue(service.clear(actorId).isPresent());
        assertFalse(service.selectedTargetOf(actorId).isPresent());
        assertEquals(Optional.of(other), service.selectedTargetOf(otherOwner));

        service.endSession(otherOwner);
        assertTrue(service.selectedTargetOf(otherOwner).isEmpty());
    }

    @Test
    void rejectsNegativeClockValueWithoutSavingSelection() {
        TargetingApplicationService invalidClockService =
                new TargetingApplicationService(store, policy, () -> -1L);

        assertThrows(IllegalStateException.class, () -> invalidClockService.select(input(UUID.randomUUID())));
        assertTrue(service.selectedTargetOf(actorId).isEmpty());
    }

    @Test
    void rejectsNullServiceDependenciesAndCommands() {
        assertThrows(
                NullPointerException.class,
                () -> new TargetingApplicationService(null, policy, clock::get));
        assertThrows(
                NullPointerException.class,
                () -> new TargetingApplicationService(store, null, clock::get));
        assertThrows(
                NullPointerException.class,
                () -> new TargetingApplicationService(store, policy, null));
        assertThrows(NullPointerException.class, () -> service.select(null));
        assertThrows(NullPointerException.class, () -> service.clear(null));
        assertThrows(NullPointerException.class, () -> service.selectedTargetOf(null));
        assertThrows(NullPointerException.class, () -> service.endSession(null));
    }

    private TargetInteractionInput input(UUID targetId) {
        return new TargetInteractionInput(
                actorId, targetId, TargetSelectionSource.ENTITY_RIGHT_CLICK, TargetInteractionHand.MAIN_HAND);
    }
}
