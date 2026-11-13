// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.bootstrap.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class ModuleLifecycleTest {

    @Test
    void followsSuccessfulLifecycleInOrder() {
        ModuleLifecycle lifecycle = new ModuleLifecycle();

        lifecycle.initialized();
        lifecycle.registered();
        lifecycle.started();
        lifecycle.beginStopping();
        lifecycle.stopped();
        lifecycle.closed();

        assertEquals(ModuleState.CLOSED, lifecycle.state());
    }

    @Test
    void rejectsOutOfOrderTransitions() {
        ModuleLifecycle lifecycle = new ModuleLifecycle();

        assertThrows(IllegalStateException.class, lifecycle::started);
        assertEquals(ModuleState.NEW, lifecycle.state());
    }

    @Test
    void failureIsStoredAsImmutableDiagnosticSnapshot() {
        ModuleLifecycle lifecycle = new ModuleLifecycle();
        IllegalStateException cause = new IllegalStateException("ошибка запуска");

        lifecycle.initialized();
        lifecycle.failed(cause);
        ModuleLifecycle.FailureSnapshot snapshot = lifecycle.failure();
        lifecycle.closed();

        assertEquals(ModuleState.CLOSED, lifecycle.state());
        assertEquals(IllegalStateException.class.getName(), snapshot.type());
        assertEquals("ошибка запуска", snapshot.message());
        assertNotSame(cause, snapshot);
        assertThrows(UnsupportedOperationException.class, () -> snapshot.stackTrace().add("изменение"));
    }

    @Test
    void preservesTheFirstFailureSnapshot() {
        ModuleLifecycle lifecycle = new ModuleLifecycle();
        lifecycle.failed(new IllegalStateException("первая"));
        ModuleLifecycle.FailureSnapshot first = lifecycle.failure();

        lifecycle.failed(new IllegalArgumentException("вторая"));

        assertEquals(first, lifecycle.failure());
    }

    @Test
    void failureSnapshotCopiesStackTraceInput() {
        List<String> frames = new java.util.ArrayList<>(List.of("frame"));
        ModuleLifecycle.FailureSnapshot snapshot =
                new ModuleLifecycle.FailureSnapshot("Example", "message", frames);
        frames.add("mutated");

        assertEquals(List.of("frame"), snapshot.stackTrace());
    }

    @Test
    void stoppingAndClosingAreIdempotentOnlyWhereDocumented() {
        ModuleLifecycle lifecycle = new ModuleLifecycle();

        lifecycle.beginStopping();
        lifecycle.beginStopping();
        lifecycle.stopped();

        assertEquals(ModuleState.STOPPED, lifecycle.state());
        assertThrows(IllegalStateException.class, lifecycle::stopped);
    }
}
