// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

class AbilitySourceContextTest {

    @Test
    void restoresOuterSourceAfterNestedScope() {
        UUID outerSource = UUID.randomUUID();
        UUID nestedSource = UUID.randomUUID();

        try (AbilitySourceContext.Scope outer = AbilitySourceContext.open(outerSource)) {
            assertEquals(outerSource, AbilitySourceContext.current());

            try (AbilitySourceContext.Scope nested = AbilitySourceContext.open(nestedSource)) {
                assertEquals(nestedSource, AbilitySourceContext.current());
            }

            assertEquals(outerSource, AbilitySourceContext.current());
        }

        assertNull(AbilitySourceContext.current());
    }

    @Test
    void nullNestedSourceMasksOuterSourceAndThenRestoresIt() {
        UUID outerSource = UUID.randomUUID();

        try (AbilitySourceContext.Scope outer = AbilitySourceContext.open(outerSource)) {
            assertEquals(outerSource, AbilitySourceContext.current());

            try (AbilitySourceContext.Scope nested = AbilitySourceContext.open(null)) {
                assertNull(AbilitySourceContext.current());
            }

            assertEquals(outerSource, AbilitySourceContext.current());
        }

        assertNull(AbilitySourceContext.current());
    }

    @Test
    void nullOuterSourceLeavesNoContextAfterClose() {
        try (AbilitySourceContext.Scope ignored = AbilitySourceContext.open(null)) {
            assertNull(AbilitySourceContext.current());
        }

        assertNull(AbilitySourceContext.current());
    }

    @Test
    void rejectsOutOfOrderCloseWithoutCorruptingOuterContext() {
        UUID outerSource = UUID.randomUUID();
        UUID nestedSource = UUID.randomUUID();
        AbilitySourceContext.Scope outer = AbilitySourceContext.open(outerSource);
        AbilitySourceContext.Scope nested = AbilitySourceContext.open(nestedSource);

        assertThrows(IllegalStateException.class, outer::close);
        assertEquals(nestedSource, AbilitySourceContext.current());

        nested.close();
        assertEquals(outerSource, AbilitySourceContext.current());
        outer.close();
        assertNull(AbilitySourceContext.current());
    }

    @Test
    void rejectsRepeatedCloseAfterScopeHasBeenClosed() {
        AbilitySourceContext.Scope scope = AbilitySourceContext.open(UUID.randomUUID());

        scope.close();

        assertNull(AbilitySourceContext.current());
        assertThrows(IllegalStateException.class, scope::close);
        assertNull(AbilitySourceContext.current());
    }

    @Test
    void rejectsCloseFromForeignThreadWithoutCorruptingOwnerContext() throws InterruptedException {
        UUID source = UUID.randomUUID();
        AbilitySourceContext.Scope scope = AbilitySourceContext.open(source);
        AtomicReference<Throwable> closeFailure = new AtomicReference<>();

        Thread worker = new Thread(() -> {
            try {
                scope.close();
            } catch (Throwable failure) {
                closeFailure.set(failure);
            }
        });

        worker.start();
        worker.join();

        assertEquals(IllegalStateException.class, closeFailure.get().getClass());
        assertEquals(source, AbilitySourceContext.current());

        scope.close();
        assertNull(AbilitySourceContext.current());
    }

    @Test
    void contextIsIsolatedBetweenThreads() throws InterruptedException {
        UUID outerSource = UUID.randomUUID();
        UUID workerSource = UUID.randomUUID();
        UUID[] observed = {outerSource};

        try (AbilitySourceContext.Scope outer = AbilitySourceContext.open(outerSource)) {
            Thread worker = new Thread(() -> {
                try (AbilitySourceContext.Scope ignored = AbilitySourceContext.open(workerSource)) {
                    observed[0] = AbilitySourceContext.current();
                }
            });
            worker.start();
            worker.join();

            assertEquals(workerSource, observed[0]);
            assertEquals(outerSource, AbilitySourceContext.current());
        }

        assertNull(AbilitySourceContext.current());
    }
}
