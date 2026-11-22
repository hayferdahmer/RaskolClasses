// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.hud.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.bukkit.Server;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;

import dev.raskol.classes.bootstrap.lifecycle.TaskHandle;

class PaperHudAdaptersTest {

    @Test
    void paperSchedulerRunsActionAndReturnsCancellableHandle() {
        AtomicReference<Runnable> scheduledAction = new AtomicReference<>();
        AtomicReference<Plugin> scheduledPlugin = new AtomicReference<>();
        AtomicInteger initialDelay = new AtomicInteger(-1);
        AtomicInteger period = new AtomicInteger(-1);
        AtomicBoolean cancelled = new AtomicBoolean();
        AtomicInteger cancelCalls = new AtomicInteger();

        BukkitTask task = proxy(BukkitTask.class, (instance, method, args) -> switch (method.getName()) {
            case "cancel" -> {
                cancelCalls.incrementAndGet();
                cancelled.set(true);
                yield null;
            }
            case "isCancelled" -> cancelled.get();
            default -> defaultValue(method.getReturnType());
        });

        BukkitScheduler scheduler = proxy(BukkitScheduler.class, (instance, method, args) -> {
            if (method.getName().equals("runTaskTimer")) {
                scheduledPlugin.set((Plugin) args[0]);
                scheduledAction.set((Runnable) args[1]);
                initialDelay.set(Math.toIntExact((long) args[2]));
                period.set(Math.toIntExact((long) args[3]));
                return task;
            }
            return defaultValue(method.getReturnType());
        });

        Plugin plugin = pluginWith(scheduler, null);
        AtomicInteger executions = new AtomicInteger();
        TaskHandle handle = new PaperHudTaskScheduler(plugin).scheduleRepeating(executions::incrementAndGet, 4, 20);

        assertSame(plugin, scheduledPlugin.get());
        assertEquals(4, initialDelay.get());
        assertEquals(20, period.get());
        assertNotNull(scheduledAction.get());
        scheduledAction.get().run();
        assertEquals(1, executions.get());

        handle.cancel();
        handle.cancel();
        assertTrue(handle.isCancelled());
        assertEquals(2, cancelCalls.get());
    }

    @Test
    void paperSchedulerRejectsInvalidIntervalsBeforeScheduling() {
        AtomicInteger scheduleCalls = new AtomicInteger();
        BukkitScheduler scheduler = proxy(BukkitScheduler.class, (instance, method, args) -> {
            if (method.getName().equals("runTaskTimer")) {
                scheduleCalls.incrementAndGet();
            }
            return defaultValue(method.getReturnType());
        });
        PaperHudTaskScheduler adapter = new PaperHudTaskScheduler(pluginWith(scheduler, null));

        assertThrows(IllegalArgumentException.class, () -> adapter.scheduleRepeating(() -> {}, -1, 20));
        assertThrows(IllegalArgumentException.class, () -> adapter.scheduleRepeating(() -> {}, 0, 0));
        assertEquals(0, scheduleCalls.get());
    }

    @Test
    void paperSessionRegistrationCanBeClosedRepeatedly() {
        AtomicReference<Listener> registeredListener = new AtomicReference<>();
        AtomicInteger registrations = new AtomicInteger();
        PluginManager manager = proxy(PluginManager.class, (instance, method, args) -> {
            if (method.getName().equals("registerEvents")) {
                registeredListener.set((Listener) args[0]);
                registrations.incrementAndGet();
            }
            return defaultValue(method.getReturnType());
        });
        PaperHudSessionEvents adapter = new PaperHudSessionEvents(pluginWith(null, manager));

        HudRegistration registration = adapter.onQuit(playerId -> {});
        assertNotNull(registeredListener.get());
        assertEquals(1, registrations.get());

        registration.close();
        registration.close();
    }

    private static Plugin pluginWith(BukkitScheduler scheduler, PluginManager manager) {
        Server server = proxy(Server.class, (instance, method, args) -> switch (method.getName()) {
            case "getScheduler" -> scheduler;
            case "getPluginManager" -> manager;
            default -> defaultValue(method.getReturnType());
        });
        return proxy(Plugin.class, (instance, method, args) -> switch (method.getName()) {
            case "getServer" -> server;
            case "getLogger" -> java.util.logging.Logger.getLogger("PaperHudAdaptersTest");
            default -> defaultValue(method.getReturnType());
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler);
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive() || type == void.class) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return '\0';
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == float.class) {
            return 0F;
        }
        if (type == double.class) {
            return 0D;
        }
        return null;
    }
}
