// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.cc;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * 1.13.0 (Б3): реестр активных канал-кастов (soul_rift и будущие каналы 1.14.0).
 * Кастер регистрирует canceller при старте канала и снимает его при завершении;
 * CCService.interruptible-CC вызывает interrupt(uuid) — канал гаснет сразу.
 * Static: канал живёт внутри тик-задачей кастера, сервис-инстанс не нужен.
 * 1.14.12 (Sprint 8, Волна 12C, P0-6-05): catch сужен до RuntimeException +
 * warning-лог (раньше Exception ignored — проглатывал все ошибки canceller'а,
 * включая NullPointerException из-за битой ссылки на задачу).
 */
public final class CastChannels {

    private static final Logger LOGGER = Logger.getLogger("RaskolClasses");
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

    /**
     * Прервать канал кастера: выполнить canceller и снять регистрацию. true если канал был.
     * 1.14.12 (Волна 12C, 6-05): catch RuntimeException + warning-лог — canceller
     * не должен ронять тик CC, но ошибка логируется для диагностики.
     */
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
        } catch (RuntimeException e) {
            LOGGER.warning("CastChannels: canceller для " + casterUuid
                    + " упал с " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        return true;
    }
}
