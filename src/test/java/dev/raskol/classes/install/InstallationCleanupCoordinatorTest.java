// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.install;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class InstallationCleanupCoordinatorTest {

    private static final InstallationType TYPE = InstallationType.values()[0];

    @Test
    void cleanupContinuesAfterOneHandlerFails() {
        Map<UUID, ActiveInstallation> installations = new ConcurrentHashMap<>();
        ActiveInstallation failed = installation();
        ActiveInstallation successful = installation();
        installations.put(failed.id(), failed);
        installations.put(successful.id(), successful);
        List<UUID> expired = new ArrayList<>();
        List<UUID> reported = new ArrayList<>();
        InstallationHandler handler = handler(installation -> {
            expired.add(installation.id());
            if (installation.id().equals(failed.id())) {
                throw new IllegalStateException("transient cleanup failure");
            }
        });
        InstallationCleanupCoordinator coordinator = coordinator(installations, handler, reported);

        int failures = coordinator.cleanup(installation -> true, "test");

        assertEquals(1, failures);
        assertEquals(Set.of(failed.id(), successful.id()), Set.copyOf(expired));
        assertEquals(List.of(failed.id()), reported);
        assertTrue(installations.containsKey(failed.id()));
        assertFalse(installations.containsKey(successful.id()));
    }

    @Test
    void failedInstallationCanBeRetriedAndRemovedAfterSuccess() {
        Map<UUID, ActiveInstallation> installations = new ConcurrentHashMap<>();
        ActiveInstallation installation = installation();
        installations.put(installation.id(), installation);
        AtomicInteger attempts = new AtomicInteger();
        InstallationHandler handler = handler(candidate -> {
            if (attempts.incrementAndGet() == 1) {
                throw new IllegalStateException("first attempt fails");
            }
        });
        InstallationCleanupCoordinator coordinator = coordinator(installations, handler, new ArrayList<>());

        assertEquals(1, coordinator.cleanup(candidate -> true, "first"));
        assertTrue(installations.containsKey(installation.id()));

        assertEquals(0, coordinator.cleanup(candidate -> true, "retry"));
        assertFalse(installations.containsKey(installation.id()));
        assertEquals(2, attempts.get());
    }

    @Test
    void cleanupOnlyRemovesTheExactSnapshotEntry() {
        Map<UUID, ActiveInstallation> installations = new ConcurrentHashMap<>();
        ActiveInstallation original = installation();
        ActiveInstallation replacement =
                new ActiveInstallation(original.id(), original.type(), original.owner(), null, original.expiresAt());
        installations.put(original.id(), original);
        InstallationHandler handler = handler(candidate -> installations.put(candidate.id(), replacement));
        InstallationCleanupCoordinator coordinator = coordinator(installations, handler, new ArrayList<>());

        assertEquals(0, coordinator.cleanup(candidate -> true, "identity"));
        assertTrue(installations.get(original.id()) == replacement);
    }

    @Test
    void selectorLeavesNonMatchingInstallationsUntouched() {
        Map<UUID, ActiveInstallation> installations = new ConcurrentHashMap<>();
        UUID selectedOwner = UUID.randomUUID();
        ActiveInstallation selected = installation(selectedOwner);
        ActiveInstallation other = installation(UUID.randomUUID());
        installations.put(selected.id(), selected);
        installations.put(other.id(), other);
        List<UUID> expired = new ArrayList<>();
        InstallationCleanupCoordinator coordinator =
                coordinator(installations, handler(candidate -> expired.add(candidate.id())), new ArrayList<>());

        assertEquals(0, coordinator.cleanup(candidate -> candidate.owner().equals(selectedOwner), "selected"));

        assertEquals(List.of(selected.id()), expired);
        assertFalse(installations.containsKey(selected.id()));
        assertTrue(installations.containsKey(other.id()));
    }

    private static InstallationCleanupCoordinator coordinator(
            Map<UUID, ActiveInstallation> installations, InstallationHandler handler, List<UUID> reported) {
        return new InstallationCleanupCoordinator(
                installations,
                type -> type == TYPE ? handler : null,
                (operation, installation, exception) -> reported.add(installation.id()));
    }

    private static InstallationHandler handler(java.util.function.Consumer<ActiveInstallation> expire) {
        return new InstallationHandler() {
            @Override
            public InstallationType type() {
                return TYPE;
            }

            @Override
            public boolean place(Player player, Location location, UUID id) {
                return true;
            }

            @Override
            public boolean tick(ActiveInstallation installation) {
                return true;
            }

            @Override
            public void expire(ActiveInstallation installation) {
                expire.accept(installation);
            }

            @Override
            public double radius() {
                return 1.0;
            }

            @Override
            public int cooldownSeconds() {
                return 1;
            }

            @Override
            public int durationSeconds() {
                return 1;
            }
        };
    }

    private static ActiveInstallation installation() {
        return installation(UUID.randomUUID());
    }

    private static ActiveInstallation installation(UUID owner) {
        return new ActiveInstallation(UUID.randomUUID(), TYPE, owner, null, 0L);
    }
}
