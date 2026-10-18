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
 * 1.14.12 (Sprint 8, Волна 12C, P0-6-05): catch сужен до RuntimeException +
 * warning-лог (раньше Exception ignored — проглатывал все ошибки canceller'а,
 * включая NullPointerException из-за битой ссылки на задачу).
 * 1.14.13 (Sprint 9, Волна 13C, P0-10-01/02): static-mutable state переведён в
 * instance-поля singleton-экземпляра. Карты каналов больше не static-поля класса —
 * они instance-поля единственного экземпляра CastChannels, доступного через
 * CastChannels.instance(). Это решает проблему "static mutable state" (аудит 10-01):
 * состояние инкапсулировано в объекте, а не в классе; при необходимости можно
 * создать несколько экземпляров (например, для тестов) без конфликта.
 */
public final class CastChannels {

    private static final Logger LOGGER = Logger.getLogger("RaskolClasses");

    /** 1.14.13 (Волна 13C, 10-01): singleton-экземпляр с instance-полями. */
    private static final CastChannels INSTANCE = new CastChannels();

    /** 1.14.13 (Волна 13C): instance-поле вместо static — карта активных каналов. */
    private final Map<UUID, Runnable> channels = new ConcurrentHashMap<>();

    private CastChannels() {
    }

    /** 1.14.13 (Волна 13C): доступ к singleton-экземпляру. */
    public static CastChannels instance() {
        return INSTANCE;
    }

    /**
     * 1.14.13 (Волна 13C): static facade для обратной совместимости — делегирует в instance.
     * Потребители (WarlockAbilities, CCService) продолжают вызывать CastChannels.register(...)
     * без изменений, но состояние теперь instance-поле singleton-экземпляра.
     */
    public static void register(UUID casterUuid, Runnable canceller) {
        INSTANCE.registerInstance(casterUuid, canceller);
    }

    public static void unregister(UUID casterUuid) {
        INSTANCE.unregisterInstance(casterUuid);
    }

    public static boolean isChanneling(UUID casterUuid) {
        return INSTANCE.isChannelingInstance(casterUuid);
    }

    /**
     * Прервать канал кастера: выполнить canceller и снять регистрацию. true если канал был.
     * 1.14.12 (Волна 12C, 6-05): catch RuntimeException + warning-лог.
     */
    public static boolean interrupt(UUID casterUuid) {
        return INSTANCE.interruptInstance(casterUuid);
    }

    /* ------------------------------ instance-методы ------------------------------ */

    private void registerInstance(UUID casterUuid, Runnable canceller) {
        if (casterUuid == null || canceller == null) {
            return;
        }
        channels.put(casterUuid, canceller);
    }

    private void unregisterInstance(UUID casterUuid) {
        channels.remove(casterUuid);
    }

    private boolean isChannelingInstance(UUID casterUuid) {
        return casterUuid != null && channels.containsKey(casterUuid);
    }

    private boolean interruptInstance(UUID casterUuid) {
        if (casterUuid == null) {
            return false;
        }
        Runnable canceller = channels.remove(casterUuid);
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
