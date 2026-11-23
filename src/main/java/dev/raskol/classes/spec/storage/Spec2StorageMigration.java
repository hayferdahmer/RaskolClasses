// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.spec.storage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.attribute.BasicFileAttributes;
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
        boolean legacyExists = attributes(oldFile) != null;
        if (!legacyExists) {
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

        BasicFileAttributes newAttributes = attributes(newFile);
        boolean currentExists = newAttributes != null;
        if (currentExists && !newAttributes.isRegularFile()) {
            if (!legacyReadable) {
                throw new IllegalStateException(
                        "spec2: целевой путь не является файлом, а legacy-хранилище недоступно");
            }
            logger.severe("spec2: целевой путь " + NEW_FILE_NAME
                    + " занят каталогом или специальным файлом; legacy оставлен без изменений");
            return legacy;
        }

        SafeStorage.LoadResult currentLoad;
        YamlConfiguration current;
        try {
            currentLoad = SafeStorage.load(newFile, logger);
            current = currentLoad.configuration();
        } catch (SafeStorage.StorageLoadException exception) {
            if (legacyReadable && exception.status() == SafeStorage.LoadStatus.CORRUPT) {
                logger.severe("spec2: новое хранилище повреждено; используем читаемый legacy-файл, "
                        + "повреждённые копии нового хранилища сохранены для восстановления");
                return legacy;
            }
            throw exception;
        }
        if (!legacyReadable) {
            if (currentLoad.status() != SafeStorage.LoadStatus.NEW && !current.getKeys(true).isEmpty()) {
                logger.severe("spec2: legacy-файл не читается; используем существующее новое хранилище, "
                        + "legacy оставлен без изменений");
                return current;
            }
            throw new IllegalStateException(
                    "spec2: legacy-файл существует, но не читается; безопасная миграция невозможна");
        }
        if (legacyReadable
                && current.getKeys(true).isEmpty()
                && !legacy.getKeys(true).isEmpty()) {
            if (SafeStorage.saveAtomic(legacy, newFile, logger)) {
                logger.info("spec2: новая копия проверена и записана; legacy сохранён для ручной проверки: "
                        + OLD_FILE_NAME + " → " + NEW_FILE_NAME);
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
            logger.info("spec2: пустой legacy-файл сохранён без изменений: " + OLD_FILE_NAME);
        }
        return current;
    }

    private static BasicFileAttributes attributes(File file) {
        try {
            return Files.readAttributes(file.toPath(), BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        } catch (NoSuchFileException exception) {
            return null;
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "spec2: невозможно проверить путь хранилища " + file.getName(), exception);
        }
    }
}

