// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.cc;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 1.13.0 (Б3): реестр активных канал-кастов (soul_rift и будущие каналы 1.14.0).
 * Кастер регистрирует canceller при старте канала и снимает его при завершении;
 * CCService.interruptible-CC вызывает interrupt(uuid) — канал гаснет сразу.
 * Static: канал живёт внутри тик-задачей кастера, сервис-инстанс не нужен.
 */
public final class CastChannels {

    private static final Map<UUID, Runnable> CHANNELS = new ConcurrentHashMap<>();

    private CastChannels() {
    }

    public static void register(UUID casterUuid, Runnable canceller) {
        if (casterUuid == null || canceller == null) {
            return;
        }
        CHANNELS.put(casterUuid, canceller);
    }

    public static void unregister(UUID casterUuid) {
        CHANNELS.remove(casterUuid);
    }

    public static boolean isChanneling(UUID casterUuid) {
        return casterUuid != null && CHANNELS.containsKey(casterUuid);
    }

    /** Прервать канал кастера: выполнить canceller и снять регистрацию. true если канал был. */
    public static boolean interrupt(UUID casterUuid) {
        if (casterUuid == null) {
            return false;
        }
        Runnable canceller = CHANNELS.remove(casterUuid);
        if (canceller == null) {
            return false;
        }
        try {
            canceller.run();
        } catch (RuntimeException ignored) {
            // canceller не должен ронять тик CC
        }
        return true;
    }
}
