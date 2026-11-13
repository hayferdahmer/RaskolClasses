// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.bootstrap.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class TaskScopeTest {

    @Test
    void cancelAllCancelsEveryRegisteredTaskAndKeepsScopeOpen() {
        TaskScope scope = new TaskScope();
        AtomicInteger cancellations = new AtomicInteger();

        scope.register(handle(cancellations));
        scope.register(handle(cancellations));

        scope.cancelAll();

        assertEquals(2, cancellations.get());
        assertEquals(0, scope.size());
        assertFalse(scope.isClosed());
    }

    @Test
    void cancellationFailureDoesNotPreventOtherTasksFromBeingCancelled() {
        TaskScope scope = new TaskScope();
        AtomicInteger cancellations = new AtomicInteger();

        scope.register(new TaskHandle() {
            @Override
            public void cancel() {
                throw new IllegalStateException("ошибка отмены");
            }

            @Override
            public boolean isCancelled() {
                return false;
            }
        });
        scope.register(handle(cancellations));

        IllegalStateException failure = assertThrows(IllegalStateException.class, scope::cancelAll);

        assertEquals(1, failure.getSuppressed().length);
        assertEquals(1, cancellations.get());
        assertEquals(0, scope.size());
    }

    @Test
    void closeIsIdempotentAndRejectsNewTasks() {
        TaskScope scope = new TaskScope();
        AtomicInteger cancellations = new AtomicInteger();
        TaskHandle task = handle(cancellations);

        scope.register(task);
        scope.close();
        scope.close();

        assertTrue(scope.isClosed());
        assertEquals(1, cancellations.get());
        assertThrows(IllegalStateException.class, () -> scope.register(task));
    }

    private static TaskHandle handle(AtomicInteger cancellations) {
        return new TaskHandle() {
            private boolean cancelled;

            @Override
            public void cancel() {
                if (!cancelled) {
                    cancelled = true;
                    cancellations.incrementAndGet();
                }
            }

            @Override
            public boolean isCancelled() {
                return cancelled;
            }
        };
    }
}
