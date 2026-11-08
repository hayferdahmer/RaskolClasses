// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.spec.storage;

import java.io.File;
import java.util.logging.Logger;

import org.bukkit.configuration.file.YamlConfiguration;

import dev.raskol.classes.storage.SafeStorage;

/** Безопасная миграция legacy-хранилища специализаций. */
final class Spec2StorageMigration {

    private static final String OLD_FILE_NAME = "spec2.yml";
    private static final String NEW_FILE_NAME = "spec2-storage.yml";

    private Spec2StorageMigration() {}

    static YamlConfiguration load(File directory, Logger logger) {
        File oldFile = new File(directory, OLD_FILE_NAME);
        File newFile = new File(directory, NEW_FILE_NAME);
        if (!oldFile.exists()) {
            return SafeStorage.loadWithFallback(newFile, logger);
        }

        YamlConfiguration legacy = new YamlConfiguration();
        boolean legacyReadable;
        try {
            legacy.load(oldFile);
            legacyReadable = true;
        } catch (Exception ex) {
            legacyReadable = false;
            logger.severe("spec2: legacy-файл " + OLD_FILE_NAME
                    + " не удалось прочитать; оригинал оставлен без изменений: " + ex.getMessage());
        }

        YamlConfiguration current =
                newFile.exists() ? SafeStorage.loadWithFallback(newFile, logger) : new YamlConfiguration();
        if (legacyReadable
                && current.getKeys(true).isEmpty()
                && !legacy.getKeys(true).isEmpty()) {
            if (SafeStorage.saveAtomic(legacy, newFile, logger)) {
                if (oldFile.delete()) {
                    logger.info("spec2: восстановлено и мигрировано " + OLD_FILE_NAME + " → " + NEW_FILE_NAME);
                } else {
                    logger.warning("spec2: новая копия записана, но legacy-файл оставлен: " + OLD_FILE_NAME);
                }
            } else {
                logger.warning(
                        "spec2: не удалось записать новую копию; legacy-файл сохранён, данные остаются в памяти");
            }
            return legacy;
        }

        if (legacyReadable
                && !legacy.getKeys(true).isEmpty()
                && !current.getKeys(true).isEmpty()) {
            logger.warning("spec2: обнаружены оба файла с данными; используем " + NEW_FILE_NAME + ", legacy "
                    + OLD_FILE_NAME + " не удаляем автоматически");
        } else if (legacyReadable
                && current.getKeys(true).isEmpty()
                && legacy.getKeys(true).isEmpty()) {
            if (!oldFile.delete()) {
                logger.warning("spec2: не удалось удалить пустой legacy-файл " + OLD_FILE_NAME);
            }
        }
        return current;
    }
}
