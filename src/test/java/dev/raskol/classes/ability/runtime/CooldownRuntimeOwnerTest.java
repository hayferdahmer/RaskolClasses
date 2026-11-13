// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.ability.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class CooldownRuntimeOwnerTest {

    @Test
    void startRegistersListenerOnce() {
        List<String> calls = new ArrayList<>();
        CooldownRuntimeOwner owner = owner(calls);

        owner.start();
        owner.start();

        assertEquals(List.of("register"), calls);
        assertTrue(owner.isRunning());
    }

    @Test
    void closeCancelsNotificationsBeforeUnregisteringListener() {
        List<String> calls = new ArrayList<>();
        CooldownRuntimeOwner owner = owner(calls);

        owner.start();
        owner.close();

        assertEquals(List.of("register", "cancel-tasks", "unregister"), calls);
        assertTrue(owner.isClosed());
        assertFalse(owner.isRunning());
    }

    @Test
    void repeatedCloseDoesNotRepeatSuccessfulCleanup() {
        List<String> calls = new ArrayList<>();
        CooldownRuntimeOwner owner = owner(calls);

        owner.start();
        owner.close();
        owner.close();

        assertEquals(1, count(calls, "cancel-tasks"));
        assertEquals(1, count(calls, "unregister"));
    }

    @Test
    void cancellationFailureDoesNotSkipUnregistrationAndCanBeRetried() {
        List<String> calls = new ArrayList<>();
        boolean[] failOnce = {true};
        CooldownRuntimeOwner owner = new CooldownRuntimeOwner(registration(calls), () -> {
            calls.add("cancel-tasks");
            if (failOnce[0]) {
                failOnce[0] = false;
                throw new IllegalStateException("cancellation failed");
            }
        });

        owner.start();
        assertThrows(IllegalStateException.class, owner::close);

        assertEquals(List.of("register", "cancel-tasks", "unregister"), calls);
        assertFalse(owner.isClosed());

        owner.close();

        assertEquals(2, count(calls, "cancel-tasks"));
        assertEquals(1, count(calls, "unregister"));
        assertTrue(owner.isClosed());
    }

    @Test
    void unregisterFailureCanBeRetriedWithoutRepeatingTaskCancellation() {
        List<String> calls = new ArrayList<>();
        boolean[] failOnce = {true};
        CooldownRuntimeRegistration registration = new CooldownRuntimeRegistration() {
            @Override
            public void register() {
                calls.add("register");
            }

            @Override
            public void unregister() {
                calls.add("unregister");
                if (failOnce[0]) {
                    failOnce[0] = false;
                    throw new IllegalStateException("unregister failed");
                }
            }
        };
        CooldownRuntimeOwner owner = new CooldownRuntimeOwner(registration, () -> calls.add("cancel-tasks"));

        owner.start();
        assertThrows(IllegalStateException.class, owner::close);
        owner.close();

        assertEquals(1, count(calls, "cancel-tasks"));
        assertEquals(2, count(calls, "unregister"));
        assertTrue(owner.isClosed());
    }

    @Test
    void registrationFailureAttemptsRollback() {
        List<String> calls = new ArrayList<>();
        CooldownRuntimeRegistration registration = new CooldownRuntimeRegistration() {
            @Override
            public void register() {
                calls.add("register");
                throw new IllegalStateException("registration failed");
            }

            @Override
            public void unregister() {
                calls.add("unregister");
            }
        };
        CooldownRuntimeOwner owner = new CooldownRuntimeOwner(registration, () -> calls.add("cancel-tasks"));

        assertThrows(IllegalStateException.class, owner::start);

        assertEquals(List.of("register", "unregister"), calls);
        assertFalse(owner.isRunning());
    }

    @Test
    void closeBeforeStartIsSafeAndPreventsLaterStart() {
        List<String> calls = new ArrayList<>();
        CooldownRuntimeOwner owner = owner(calls);

        owner.close();

        assertTrue(owner.isClosed());
        assertThrows(IllegalStateException.class, owner::start);
        assertEquals(List.of(), calls);
    }

    private static CooldownRuntimeOwner owner(List<String> calls) {
        return new CooldownRuntimeOwner(registration(calls), () -> calls.add("cancel-tasks"));
    }

    private static CooldownRuntimeRegistration registration(List<String> calls) {
        return new CooldownRuntimeRegistration() {
            @Override
            public void register() {
                calls.add("register");
            }

            @Override
            public void unregister() {
                calls.add("unregister");
            }
        };
    }

    private static int count(List<String> values, String expected) {
        return (int) values.stream().filter(expected::equals).count();
    }
}
