// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.storage;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 1.6.10: надёжное хранение YAML-файлов плагина.
 *
 * saveAtomic: пишем в <file>.tmp → предыдущую версию уносим в <file>.bak →
 * атомарно переименовываем tmp в целевой файл. После обесточки/kill -9 на диске
 * не может остаться полубитого yml: есть либо целостный текущий, либо .bak.
 *
 * loadWithFallback: парсим файл; при коррупте (битый YAML) — WARNING одной
 * строкой и попытка чтения <file>.bak; если и .bak бит/отсутствует — пустая
 * конфигурация (плагин поднимается, данные с нуля, инцидент залогирован).
 */
public final class SafeStorage {

    private SafeStorage() {
    }

    /** Загрузка с фолбэком: corrupt → .bak → пустая конфигурация. */
    public static YamlConfiguration loadWithFallback(File file, Logger logger) {
        if (file == null || !file.exists()) {
            return new YamlConfiguration();
        }
        try {
            YamlConfiguration cfg = new YamlConfiguration();
            cfg.load(file);
            return cfg;
        } catch (Exception e) {
            logger.warning("SafeStorage: файл " + file.getName()
                    + " повреждён (" + e.getMessage() + ") — пробую .bak");
            File bak = new File(file.getParentFile(), file.getName() + ".bak");
            if (bak.exists()) {
                try {
                    YamlConfiguration cfg = new YamlConfiguration();
                    cfg.load(bak);
                    logger.warning("SafeStorage: " + file.getName()
                            + " восстановлен из .bak (потеряна только последняя запись)");
                    return cfg;
                } catch (Exception e2) {
                    logger.log(Level.SEVERE, "SafeStorage: .bak для " + file.getName()
                            + " тоже повреждён — стартую с пустой конфигурацией", e2);
                }
            } else {
                logger.severe("SafeStorage: нет .bak для " + file.getName()
                        + " — стартую с пустой конфигурацией");
            }
            return new YamlConfiguration();
        }
    }

    /**
     * Атомарный сейв: tmp → .bak (предыдущая версия) → rename tmp в цель.
     * Если файловая система не поддерживает ATOMIC_MOVE — фолбэк на обычный
     * replace (всё ещё безопаснее прямой записи в целевой файл).
     */
    public static void saveAtomic(YamlConfiguration cfg, File file, Logger logger) {
        if (cfg == null || file == null) {
            return;
        }
        File tmp = new File(file.getParentFile(), file.getName() + ".tmp");
        try {
            cfg.save(tmp);
            if (file.exists()) {
                File bak = new File(file.getParentFile(), file.getName() + ".bak");
                Files.move(file.toPath(), bak.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            try {
                Files.move(tmp.toPath(), file.toPath(),
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException atomicUnsupported) {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | IllegalArgumentException e) {
            logger.log(Level.SEVERE, "SafeStorage: не удалось сохранить " + file.getName(), e);
            if (tmp.exists() && !tmp.delete()) {
                logger.warning("SafeStorage: не удалось удалить временный файл " + tmp.getName());
            }
        }
    }
}
