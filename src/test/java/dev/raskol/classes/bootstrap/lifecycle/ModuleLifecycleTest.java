// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.bootstrap.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
    void failureCanBeClosedAfterCleanup() {
        ModuleLifecycle lifecycle = new ModuleLifecycle();
        IllegalStateException cause = new IllegalStateException("ошибка запуска");

        lifecycle.initialized();
        lifecycle.failed(cause);
        lifecycle.closed();

        assertEquals(ModuleState.CLOSED, lifecycle.state());
        assertSame(cause, lifecycle.failure());
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
