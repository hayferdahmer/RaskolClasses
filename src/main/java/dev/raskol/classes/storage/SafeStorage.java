// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.storage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.configuration.file.YamlConfiguration;

/**
 * 1.6.10: надёжное хранение YAML-файлов плагина.
 *
 * saveAtomic (1.15.5, аудит 2.6-79): НОВЫЙ порядок без окна «основной файл исчез»:
 *   1) пишем <file>.tmp (полная запись конфигурации);
 *   2) КОПИРУЕМ текущий <file> в <file>.bak (не перемещаем! основной файл остаётся
 *      на месте до успешного rename);
 *   3) атомарно переименовываем tmp → <file>.
 * Ранее шаг 2 перемещал основной файл в .bak, и между шагами 2 и 3 обесточка
 * оставляла каталог БЕЗ основного файла — загрузка уходила в пустую конфигурацию
 * (потеря всех данных хранилища). Копия устраняет это окно целиком.
 *
 * loadWithFallback (1.15.5, аудит 2.6-79/2.6-107): цепочка восстановления
 *   основной файл → .tmp (целостный, если краш случился до rename) → .bak → пустая.
 * Ранее отсутствующий основной файл сразу давал пустую конфигурацию, игнорируя
 * .bak/.tmp. Дополнительно: при успешной загрузке основного файла удаляется
 * висячий .tmp (след старого краша, аудит 2.6-107).
 */
public final class SafeStorage {

    private SafeStorage() {}

    /** Загрузка с цепочкой восстановления: main → tmp → bak → пустая конфигурация. */
    public static YamlConfiguration loadWithFallback(File file, Logger logger) {
        if (file == null) {
            return new YamlConfiguration();
        }
        File tmp = new File(file.getParentFile(), file.getName() + ".tmp");
        File bak = new File(file.getParentFile(), file.getName() + ".bak");

        if (file.exists()) {
            try {
                YamlConfiguration cfg = new YamlConfiguration();
                cfg.load(file);
                // 2.6-107: висячий .tmp от старого краша больше не нужен
                if (tmp.exists() && !tmp.delete()) {
                    logger.warning("SafeStorage: не удалось удалить временный файл " + tmp.getName());
                }
                return cfg;
            } catch (Exception e) {
                logger.warning("SafeStorage: файл " + file.getName() + " повреждён (" + e.getMessage()
                        + ") — пробую .tmp/.bak");
            }
        } else {
            logger.warning("SafeStorage: основной файл " + file.getName() + " отсутствует — пробую .tmp/.bak");
        }

        YamlConfiguration fromTmp = tryLoad(tmp);
        if (fromTmp != null) {
            logger.warning("SafeStorage: " + file.getName() + " восстановлен из .tmp (последняя завершённая запись)");
            return fromTmp;
        }
        YamlConfiguration fromBak = tryLoad(bak);
        if (fromBak != null) {
            logger.warning(
                    "SafeStorage: " + file.getName() + " восстановлен из .bak (потеряна только последняя запись)");
            return fromBak;
        }
        logger.severe(
                "SafeStorage: нет пригодных .tmp/.bak для " + file.getName() + " — стартую с пустой конфигурацией");
        return new YamlConfiguration();
    }

    /** Парсинг файла или null (отсутствует/бит). */
    private static YamlConfiguration tryLoad(File file) {
        if (file == null || !file.exists()) {
            return null;
        }
        try {
            YamlConfiguration cfg = new YamlConfiguration();
            cfg.load(file);
            return cfg;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Атомарный сейв: tmp → копия в .bak → rename tmp в цель.
     * Основной файл существует вплоть до успешного rename (аудит 2.6-79).
     * Если файловая система не поддерживает ATOMIC_MOVE — фолбэк на обычный replace.
     */
    public static boolean saveAtomic(YamlConfiguration cfg, File file, Logger logger) {
        if (cfg == null || file == null) {
            return false;
        }
        File tmp = new File(file.getParentFile(), file.getName() + ".tmp");
        try {
            cfg.save(tmp);
            if (file.exists()) {
                File bak = new File(file.getParentFile(), file.getName() + ".bak");
                Files.copy(file.toPath(), bak.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            try {
                Files.move(
                        tmp.toPath(),
                        file.toPath(),
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException atomicUnsupported) {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException | IllegalArgumentException e) {
            logger.log(Level.SEVERE, "SafeStorage: не удалось сохранить " + file.getName(), e);
            if (tmp.exists() && !tmp.delete()) {
                logger.warning("SafeStorage: не удалось удалить временный файл " + tmp.getName());
            }
            return false;
        }
    }
}
