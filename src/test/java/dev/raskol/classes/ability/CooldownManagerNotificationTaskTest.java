// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.ability;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.raskol.classes.storage.AsyncStorageWriter;

class CooldownManagerNotificationTaskTest {

    @TempDir
    Path tempDir;

    @Test
    void completionReleasesOwnHandleAndEmptyPlayerMap() {
        UUID playerId = UUID.randomUUID();
        BukkitTask task = taskHandle();
        Map<UUID, Map<String, BukkitTask>> registry = registry(playerId, "fireball", task);

        CooldownManager.releaseCompletedTask(registry, playerId, "fireball", task);

        assertFalse(registry.containsKey(playerId));
    }

    @Test
    void callbackFinallyReleasesHandleForEarlyReturnPaths() {
        UUID playerId = UUID.randomUUID();
        BukkitTask task = taskHandle();
        CooldownManager manager = manager();
        register(manager, playerId, "frostbolt", task);

        manager.executeNotificationCallback(playerId, "frostbolt", task, () -> {
            // Моделирует offline и продлённый cooldown: callback завершился без уведомления.
        });

        assertFalse(hasPlayerTasks(manager, playerId));
    }

    @Test
    void callbackFinallyReleasesHandleWhenNotificationThrows() {
        UUID playerId = UUID.randomUUID();
        BukkitTask task = taskHandle();
        CooldownManager manager = manager();
        register(manager, playerId, "arcane", task);

        assertThrows(
                IllegalStateException.class,
                () -> manager.executeNotificationCallback(playerId, "arcane", task, () -> {
                    throw new IllegalStateException("notification failed");
                }));

        assertFalse(hasPlayerTasks(manager, playerId));
    }

    @Test
    void staleCallbackCannotRemoveReplacementHandle() {
        UUID playerId = UUID.randomUUID();
        BukkitTask oldTask = taskHandle();
        BukkitTask replacement = taskHandle();
        CooldownManager manager = manager();
        register(manager, playerId, "fireball", replacement);

        manager.executeNotificationCallback(playerId, "fireball", oldTask, () -> {});

        assertSame(replacement, taskMap(manager, playerId).get("fireball"));
    }

    @Test
    void staleCallbackCannotRemoveReplacementPlayerMap() {
        UUID playerId = UUID.randomUUID();
        BukkitTask oldTask = taskHandle();
        BukkitTask replacement = taskHandle();
        Map<UUID, Map<String, BukkitTask>> registry = registry(playerId, "fireball", oldTask);
        Map<String, BukkitTask> oldMap = registry.get(playerId);
        Map<String, BukkitTask> replacementMap = new ConcurrentHashMap<>();
        replacementMap.put("frostbolt", replacement);
        registry.put(playerId, replacementMap);

        CooldownManager.releaseCompletedTask(registry, playerId, "fireball", oldTask);

        assertSame(replacementMap, registry.get(playerId));
        assertSame(replacement, registry.get(playerId).get("frostbolt"));
        assertSame(oldTask, oldMap.get("fireball"));
    }

    private CooldownManager manager() {
        return new CooldownManager(
                tempDir.resolve(UUID.randomUUID() + ".yml").toFile(),
                new AsyncStorageWriter(Logger.getLogger("CooldownManagerNotificationTaskTest")));
    }

    private static void register(CooldownManager manager, UUID playerId, String abilityId, BukkitTask task) {
        try {
            java.lang.reflect.Field field = CooldownManager.class.getDeclaredField("tasks");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<UUID, Map<String, BukkitTask>> registry = (Map<UUID, Map<String, BukkitTask>>) field.get(manager);
            registry.computeIfAbsent(playerId, ignored -> new ConcurrentHashMap<>())
                    .put(abilityId, task);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static Map<String, BukkitTask> taskMap(CooldownManager manager, UUID playerId) {
        try {
            java.lang.reflect.Field field = CooldownManager.class.getDeclaredField("tasks");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<UUID, Map<String, BukkitTask>> registry = (Map<UUID, Map<String, BukkitTask>>) field.get(manager);
            return registry.get(playerId);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static boolean hasPlayerTasks(CooldownManager manager, UUID playerId) {
        try {
            java.lang.reflect.Field field = CooldownManager.class.getDeclaredField("tasks");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<UUID, Map<String, BukkitTask>> registry = (Map<UUID, Map<String, BukkitTask>>) field.get(manager);
            return registry.containsKey(playerId);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static Map<UUID, Map<String, BukkitTask>> registry(UUID playerId, String abilityId, BukkitTask task) {
        Map<UUID, Map<String, BukkitTask>> registry = new ConcurrentHashMap<>();
        registry.put(playerId, new ConcurrentHashMap<>(Map.of(abilityId, task)));
        return registry;
    }

    private static BukkitTask taskHandle() {
        AtomicBoolean cancelled = new AtomicBoolean();
        return (BukkitTask) Proxy.newProxyInstance(
                BukkitTask.class.getClassLoader(),
                new Class<?>[] {BukkitTask.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "cancel" -> {
                        cancelled.set(true);
                        yield null;
                    }
                    case "isCancelled" -> cancelled.get();
                    case "getTaskId" -> 1;
                    case "isSync" -> true;
                    case "getPeriod", "getNextRun", "getScheduledTick" -> 0L;
                    case "getOwner" -> null;
                    case "toString" -> "TestBukkitTask";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == arguments[0];
                    default -> null;
                });
    }
}
