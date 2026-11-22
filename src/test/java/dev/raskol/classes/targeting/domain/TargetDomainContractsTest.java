// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.targeting.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class TargetDomainContractsTest {

    @Test
    void targetIdentityRequiresEntityId() {
        assertThrows(NullPointerException.class, () -> new TargetIdentity(null));
    }

    @Test
    void selectionRequiresValidIdentityAndTimestamp() {
        UUID ownerId = UUID.randomUUID();
        TargetIdentity target = new TargetIdentity(UUID.randomUUID());

        TargetSelection selection =
                new TargetSelection(ownerId, target, 100L, TargetSelectionSource.ENTITY_RIGHT_CLICK);

        assertEquals(ownerId, selection.ownerId());
        assertEquals(target, selection.target());
        assertThrows(
                IllegalArgumentException.class,
                () -> new TargetSelection(ownerId, target, -1L, TargetSelectionSource.ENTITY_RIGHT_CLICK));
        assertThrows(NullPointerException.class, () -> new TargetSelection(ownerId, target, 100L, null));
    }

    @Test
    void targetViewCopiesMutableTagsAndCalculatesNormalizedHealth() {
        Set<String> tags = new HashSet<>(Set.of("elite"));
        TargetView view = view(OptionalDouble.of(150.0d), OptionalDouble.of(100.0d), tags);
        tags.add("mutated");

        assertEquals(Set.of("elite"), view.statusTags());
        assertEquals(1.0d, view.normalizedHealth().orElseThrow());
        assertThrows(
                UnsupportedOperationException.class, () -> view.statusTags().add("external"));
    }

    @Test
    void targetViewClampsHealthAtZero() {
        TargetView view = view(OptionalDouble.of(0.0d), OptionalDouble.of(100.0d), Set.of());

        assertEquals(0.0d, view.normalizedHealth().orElseThrow());
    }

    @Test
    void targetViewKeepsUnavailableHealthExplicit() {
        TargetView view = view(OptionalDouble.empty(), OptionalDouble.empty(), Set.of());

        assertTrue(view.normalizedHealth().isEmpty());
    }

    @Test
    void targetViewRejectsIncompleteOrInvalidHealth() {
        assertThrows(
                IllegalArgumentException.class, () -> view(OptionalDouble.of(10.0d), OptionalDouble.empty(), Set.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> view(OptionalDouble.of(10.0d), OptionalDouble.of(0.0d), Set.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> view(OptionalDouble.of(Double.NaN), OptionalDouble.of(100.0d), Set.of()));
    }

    @Test
    void targetViewRejectsInvalidOptionalLevelAndTimestamp() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new TargetView(
                        new TargetIdentity(UUID.randomUUID()),
                        TargetCategory.LIVING,
                        "Орк",
                        OptionalInt.of(-1),
                        OptionalDouble.empty(),
                        OptionalDouble.empty(),
                        Set.of(),
                        10L,
                        10L));
        assertThrows(
                IllegalArgumentException.class,
                () -> new TargetView(
                        new TargetIdentity(UUID.randomUUID()),
                        TargetCategory.LIVING,
                        "Орк",
                        OptionalInt.empty(),
                        OptionalDouble.empty(),
                        OptionalDouble.empty(),
                        Set.of(),
                        20L,
                        10L));
    }

    @Test
    void readResultDistinguishesNoneAndUnavailable() {
        TargetReadResult none = TargetReadResult.none();
        TargetReadResult unavailable = TargetReadResult.unavailable(TargetUnavailableReason.TARGET_RESOLUTION_FAILED);

        assertTrue(none instanceof TargetReadResult.None);
        assertTrue(unavailable instanceof TargetReadResult.Unavailable);
        assertFalse(none.equals(unavailable));
    }

    @Test
    void availableReadResultRequiresView() {
        TargetView view = view(OptionalDouble.empty(), OptionalDouble.empty(), Set.of());

        assertEquals(view, ((TargetReadResult.Available) TargetReadResult.available(view)).view());
        assertThrows(NullPointerException.class, () -> TargetReadResult.available(null));
    }

    private static TargetView view(OptionalDouble currentHealth, OptionalDouble maxHealth, Set<String> tags) {
        return new TargetView(
                new TargetIdentity(UUID.randomUUID()),
                TargetCategory.LIVING,
                "Орк",
                OptionalInt.of(12),
                currentHealth,
                maxHealth,
                tags,
                100L,
                120L);
    }
}
