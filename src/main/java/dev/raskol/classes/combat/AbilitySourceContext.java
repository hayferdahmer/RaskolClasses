// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.combat;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;

/** Стековый контекст источника способности для реентерабельных боевых вызовов. */
final class AbilitySourceContext {

    private static final ThreadLocal<Deque<Scope>> SCOPES = ThreadLocal.withInitial(ArrayDeque::new);

    private AbilitySourceContext() {}

    static Scope open(UUID source) {
        Scope scope = new Scope(source, Thread.currentThread());
        SCOPES.get().push(scope);
        return scope;
    }

    static UUID current() {
        Deque<Scope> scopes = SCOPES.get();
        if (scopes.isEmpty()) {
            SCOPES.remove();
            return null;
        }
        return scopes.peek().source;
    }

    static final class Scope implements AutoCloseable {

        private final UUID source;
        private final Thread owner;
        private boolean closed;

        private Scope(UUID source, Thread owner) {
            this.source = source;
            this.owner = owner;
        }

        @Override
        public void close() {
            if (closed) {
                throw new IllegalStateException("Ability source scope is already closed");
            }
            if (Thread.currentThread() != owner) {
                throw new IllegalStateException("Ability source scope must be closed on its owning thread");
            }

            Deque<Scope> scopes = SCOPES.get();
            if (scopes.peek() != this) {
                throw new IllegalStateException("Ability source scopes must be closed in LIFO order");
            }

            scopes.pop();
            closed = true;
            if (scopes.isEmpty()) {
                SCOPES.remove();
            }
        }
    }
}
