// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.storage;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Надёжное YAML-хранилище с восстановлением и fail-closed поведением.
 *
 * Повреждённые копии сохраняются в карантине вместе с SHA-256 и метаданными.
 * Оригиналы не удаляются. Если ни одна копия не читается либо возникла
 * ошибка доступа к файлам, вызывающий код получает исключение и не может
 * продолжить работу с пустым состоянием поверх существующих данных.
 */
public final class SafeStorage {

    private SafeStorage() {}

    public enum LoadStatus {
        NEW,
        LOADED_MAIN,
        RECOVERED_TMP,
        RECOVERED_BACKUP,
        CORRUPT,
        IO_ERROR
    }

    public record LoadResult(LoadStatus status, String yamlSnapshot, File source) {
        public LoadResult {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(yamlSnapshot, "yamlSnapshot");
            if (status == LoadStatus.NEW && source != null) {
                throw new IllegalArgumentException("NEW не должен иметь source");
            }
        }

        public YamlConfiguration configuration() {
            try {
                YamlConfiguration copy = new YamlConfiguration();
                copy.loadFromString(yamlSnapshot);
                return copy;
            } catch (InvalidConfigurationException exception) {
                throw new IllegalStateException("SafeStorage: внутренний снимок загрузки повреждён", exception);
            }
        }
    }

    public static final class StorageLoadException extends IllegalStateException {
        private final LoadStatus status;

        public StorageLoadException(LoadStatus status, String message, Throwable cause) {
            super(message, cause);
            this.status = Objects.requireNonNull(status, "status");
        }

        public LoadStatus status() {
            return status;
        }
    }

    /** Загружает данные и возвращает явный результат, не смешивая NEW и CORRUPT. */
    public static LoadResult load(File file, Logger logger) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(logger, "logger");

        File tmp = sibling(file, ".tmp");
        File backup = sibling(file, ".bak");
        final boolean anyCopyExists;
        try {
            anyCopyExists = exists(file.toPath()) || exists(tmp.toPath()) || exists(backup.toPath());
        } catch (IOException exception) {
            throw new StorageLoadException(
                    LoadStatus.IO_ERROR,
                    "SafeStorage: невозможно проверить наличие копий " + file.getName(),
                    exception);
        }
        if (!anyCopyExists) {
            logger.info("SafeStorage: хранилище " + file.getName() + " ещё не создано");
            return new LoadResult(LoadStatus.NEW, new YamlConfiguration().saveToString(), null);
        }

        File[] candidates = {file, tmp, backup};
        LoadStatus[] statuses = {LoadStatus.LOADED_MAIN, LoadStatus.RECOVERED_TMP, LoadStatus.RECOVERED_BACKUP};
        Throwable lastCorruption = null;
        boolean sawIoError = false;
        Set<File> quarantined = new HashSet<>();

        for (int i = 0; i < candidates.length; i++) {
            File candidate = candidates[i];
            try {
                if (!exists(candidate.toPath())) {
                    continue;
                }
                YamlConfiguration configuration = parse(candidate.toPath());
                if (i > 0) {
                    logger.warning("SafeStorage: " + file.getName() + " восстановлен из " + (i == 1 ? ".tmp" : ".bak"));
                }
                quarantineInvalidCopies(candidates, logger, candidate, quarantined);
                if (i > 0) {
                    restorePrimary(file, candidate, tmp);
                }
                return new LoadResult(statuses[i], configuration.saveToString(), candidate);
            } catch (InvalidConfigurationException exception) {
                lastCorruption = exception;
                quarantine(candidate, exception, logger);
                quarantined.add(candidate);
            } catch (IOException exception) {
                sawIoError = true;
                logger.log(Level.SEVERE, "SafeStorage: ошибка чтения " + candidate.getName(), exception);
            }
        }

        if (sawIoError) {
            throw new StorageLoadException(
                    LoadStatus.IO_ERROR,
                    "SafeStorage: невозможно безопасно прочитать копии " + file.getName(),
                    lastCorruption);
        }
        throw new StorageLoadException(
                LoadStatus.CORRUPT,
                "SafeStorage: все существующие копии " + file.getName()
                        + " повреждены; запись заблокирована, исходники сохранены",
                lastCorruption);
    }

    /**
     * Совместимый API для существующих хранилищ. При CORRUPT/IO_ERROR
     * выбрасывает исключение вместо возврата пустой конфигурации.
     */
    public static YamlConfiguration loadWithFallback(File file, Logger logger) {
        return load(file, logger).configuration();
    }

    private static YamlConfiguration parse(Path path) throws IOException, InvalidConfigurationException {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.load(path.toFile());
        return configuration;
    }

    private static void quarantineInvalidCopies(
            File[] candidates, Logger logger, File selected, Set<File> quarantined) {
        for (File candidate : candidates) {
            if (candidate.equals(selected) || quarantined.contains(candidate)) {
                continue;
            }
            try {
                if (!exists(candidate.toPath())) {
                    continue;
                }
                parse(candidate.toPath());
            } catch (InvalidConfigurationException exception) {
                quarantine(candidate, exception, logger);
                quarantined.add(candidate);
            } catch (IOException exception) {
                logger.log(Level.SEVERE, "SafeStorage: не удалось проверить копию " + candidate.getName(), exception);
                throw new StorageLoadException(
                        LoadStatus.IO_ERROR, "SafeStorage: ошибка проверки " + candidate.getName(), exception);
            }
        }
    }

    private static void restorePrimary(File primary, File recovered, File tmp) {
        try {
            if (!recovered.equals(tmp)) {
                Files.copy(recovered.toPath(), tmp.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            try {
                Files.move(
                        tmp.toPath(),
                        primary.toPath(),
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(tmp.toPath(), primary.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new StorageLoadException(
                    LoadStatus.IO_ERROR,
                    "SafeStorage: резервная копия прочитана, но основной файл не удалось восстановить",
                    exception);
        }
    }

    private static void quarantine(File damaged, Exception reason, Logger logger) {
        Path original = damaged.toPath();
        String token = Instant.now().toEpochMilli() + "-" + UUID.randomUUID();
        Path quarantine = sibling(damaged, ".corrupt-" + token + ".quarantine").toPath();
        Path metadata = Path.of(quarantine.toString() + ".json");
        try {
            byte[] originalHash = sha256(original);
            Files.copy(original, quarantine);
            byte[] quarantinedHash = sha256(quarantine);
            if (!MessageDigest.isEqual(originalHash, quarantinedHash)) {
                throw new IOException("SHA-256 копии карантина не совпадает с оригиналом");
            }
            String json = "{\n"
                    + "  \"originalPath\": \""
                    + escapeJson(original.toAbsolutePath().toString()) + "\",\n"
                    + "  \"quarantinePath\": \""
                    + escapeJson(quarantine.toAbsolutePath().toString()) + "\",\n"
                    + "  \"capturedAt\": \"" + Instant.now() + "\",\n"
                    + "  \"sizeBytes\": " + Files.size(original) + ",\n"
                    + "  \"sha256\": \"" + HexFormat.of().formatHex(originalHash) + "\",\n"
                    + "  \"reason\": \"" + escapeJson(reason.getMessage()) + "\"\n"
                    + "}\n";
            Files.writeString(metadata, json);
            logger.severe("SafeStorage: повреждённая копия сохранена в карантин: " + quarantine.getFileName());
        } catch (IOException | NoSuchAlgorithmException exception) {
            logger.log(Level.SEVERE,
                    "SafeStorage: не удалось безопасно поместить " + damaged.getName()
                            + " в карантин; исходник оставлен, дальнейшая работа запрещена",
                    exception);
            throw new StorageLoadException(LoadStatus.IO_ERROR,
                    "SafeStorage: карантин повреждённой копии не подтверждён", exception);
        }
    }

    private static boolean exists(Path path) throws IOException {
        try {
            Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            return true;
        } catch (NoSuchFileException exception) {
            return false;
        }
    }

    private static byte[] sha256(Path path) throws IOException, NoSuchAlgorithmException {
        try {
            return MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path));
        } catch (NoSuchAlgorithmException exception) {
            throw exception;
        }
    }

    private static String escapeJson(String value) {
        return value == null ? "" : value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    private static File sibling(File file, String suffix) {
        return new File(file.getAbsoluteFile().getParentFile(), file.getName() + suffix);
    }

    public static boolean saveAtomic(YamlConfiguration configuration, File file, Logger logger) {
        if (configuration == null) {
            return false;
        }
        return saveAtomic(configuration.saveToString(), file, logger);
    }

    /** Записывает только валидный YAML-снимок; повреждённый текущий файл не перезаписывает. */
    public static boolean saveAtomic(String snapshot, File file, Logger logger) {
        if (snapshot == null || file == null || logger == null) {
            return false;
        }
        File target = file.getAbsoluteFile();
        File parent = target.getParentFile();
        File tmp = sibling(target, ".tmp");
        File backup = sibling(target, ".bak");
        try {
            if (parent != null && !parent.exists() && !parent.mkdirs() && !parent.isDirectory()) {
                throw new IOException("Не удалось создать каталог " + parent);
            }

            YamlConfiguration validation = new YamlConfiguration();
            validation.loadFromString(snapshot);

            boolean targetExists = exists(target.toPath());
            boolean tmpExists = exists(tmp.toPath());
            boolean backupExists = exists(backup.toPath());
            if (targetExists) {
                parse(target.toPath());
            } else if (tmpExists || backupExists) {
                throw new IOException("Основной файл отсутствует, но существуют sidecar-копии; сначала требуется recovery");
            }

            Files.writeString(tmp.toPath(), snapshot);
            parse(tmp.toPath());

            if (targetExists) {
                Files.copy(target.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
                parse(backup.toPath());
            }

            try {
                Files.move(tmp.toPath(), target.toPath(),
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException | InvalidConfigurationException | IllegalArgumentException exception) {
            logger.log(Level.SEVERE, "SafeStorage: запись " + target.getName()
                    + " отменена; существующие данные сохранены", exception);
            return false;
        }
    }
}
