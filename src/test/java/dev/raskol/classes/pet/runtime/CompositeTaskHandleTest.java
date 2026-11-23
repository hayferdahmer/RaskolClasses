// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.pet.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.raskol.classes.bootstrap.lifecycle.TaskHandle;

@DisplayName("Composite pet task handle")
class CompositeTaskHandleTest {

    @Test
    @DisplayName("cancels every owned task and reports completion only when all are cancelled")
    void cancelsAllTasks() {
        List<String> calls = new ArrayList<>();
        FakeTask first = new FakeTask("first", calls);
        FakeTask second = new FakeTask("second", calls);
        CompositeTaskHandle handle = new CompositeTaskHandle(first, second);

        assertFalse(handle.isCancelled());
        handle.cancel();

        assertEquals(List.of("first", "second"), calls);
        assertTrue(handle.isCancelled());
    }

    @Test
    @DisplayName("continues cancellation after one task throws")
    void cancellationFailureDoesNotSkipRemainingTasks() {
        List<String> calls = new ArrayList<>();
        FakeTask first = new FakeTask("first", calls);
        FakeTask second = new FakeTask("second", calls);
        first.fail = true;
        CompositeTaskHandle handle = new CompositeTaskHandle(first, second);

        assertThrows(IllegalStateException.class, handle::cancel);

        assertEquals(List.of("first", "second"), calls);
        assertFalse(handle.isCancelled());
        assertTrue(second.cancelled);
    }

    private static final class FakeTask implements TaskHandle {

        private final String name;
        private final List<String> calls;
        private boolean cancelled;
        private boolean fail;

        private FakeTask(String name, List<String> calls) {
            this.name = name;
            this.calls = calls;
        }

        @Override
        public void cancel() {
            calls.add(name);
            if (fail) {
                fail = false;
                throw new IllegalStateException(name);
            }
            cancelled = true;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }
    }
}
