// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.spec.storage;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import dev.raskol.classes.character.profile.CharacterProfileStorage;

/** Миграция раздельных файлов Spec 2.0 в единый characters.yml. */
public final class Spec2StorageMigration {

    private static final String LEGACY_FILE_NAME = "spec2.yml";
    private static final String CURRENT_FILE_NAME = "spec2-storage.yml";
    private static final Duration SAVE_TIMEOUT = Duration.ofSeconds(30);

    private Spec2StorageMigration() {}

    /**
     * Переносит все читаемые данные Spec 2.0 в characters.yml.
     * Исходные файлы архивируются только после подтверждённой атомарной записи.
     */
    public static void migrate(File directory, CharacterProfileStorage profiles, Logger logger) {
        if (directory == null || profiles == null || logger == null) {
            throw new IllegalArgumentException("directory, profiles и logger обязательны");
        }

        Source current = loadSource(new File(directory, CURRENT_FILE_NAME), logger);
        Source legacy = loadSource(new File(directory, LEGACY_FILE_NAME), logger);
        Map<UUID, Spec2Data> currentData = parse(current.yaml());
        Map<UUID, Spec2Data> legacyData = parse(legacy.yaml());
        if (current.present() && !current.readable() && legacyData.isEmpty()) {
            throw new IllegalStateException("spec2-storage.yml повреждён, а пригодной legacy-копии нет");
        }
        if (legacy.present() && !legacy.readable() && currentData.isEmpty()) {
            throw new IllegalStateException("spec2.yml повреждён, а пригодной текущей копии нет");
        }
        Map<UUID, Spec2Data> combined = merge(currentData, legacyData, logger);

        for (Map.Entry<UUID, Spec2Data> entry : combined.entrySet()) {
            UUID playerId = entry.getKey();
            Spec2Data source = entry.getValue();
            if (source.main() == null && source.ranks().isEmpty()) {
                continue;
            }
            profiles.ensureProfile(playerId);
            mergeIntoProfile(profiles, playerId, source, logger);
        }

        boolean hasLegacySources = current.present() || legacy.present();
        boolean needsSave = profiles.isDirty() || hasLegacySources;
        if (needsSave && !profiles.saveAndAwait(SAVE_TIMEOUT)) {
            throw new IllegalStateException(
                    "Не удалось подтвердить миграцию Spec 2.0 в characters.yml; исходные файлы сохранены");
        }

        File unifiedFile = new File(directory, CharacterProfileStorage.FILE_NAME);
        if (unifiedFile.isFile() && (needsSave || current.present() || legacy.present())) {
            refreshUnifiedBackup(directory, unifiedFile, logger);
        }

        archiveSource(directory, CURRENT_FILE_NAME, current, logger);
        archiveSource(directory, LEGACY_FILE_NAME, legacy, logger);
        if (current.present() || legacy.present()) {
            logger.info("Spec2StorageMigration: данные специализаций перенесены в characters.yml");
        }
    }

    private static Source loadSource(File file, Logger logger) {
        File[] candidates = {
            file,
            new File(file.getParentFile(), file.getName() + ".tmp"),
            new File(file.getParentFile(), file.getName() + ".bak")
        };
        boolean present = false;
        for (File candidate : candidates) {
            if (!candidate.exists()) {
                continue;
            }
            present = true;
            try {
                YamlConfiguration yaml = new YamlConfiguration();
                yaml.load(candidate);
                if (!candidate.equals(file)) {
                    logger.warning("spec2: восстановлен источник " + file.getName() + " из " + candidate.getName());
                }
                return new Source(true, true, yaml);
            } catch (Exception exception) {
                logger.warning("spec2: не удалось прочитать " + candidate.getName() + ": " + exception.getMessage());
            }
        }
        return new Source(present, !present, new YamlConfiguration());
    }

    private static Map<UUID, Spec2Data> parse(YamlConfiguration yaml) {
        Map<UUID, Spec2Data> result = new LinkedHashMap<>();
        if (!yaml.contains("players")) {
            return result;
        }
        ConfigurationSection players = yaml.getConfigurationSection("players");
        if (players == null) {
            throw new IllegalStateException("Раздел players в legacy Spec 2.0 должен быть YAML-разделом");
        }

        for (String rawId : players.getKeys(false)) {
            UUID playerId;
            try {
                playerId = UUID.fromString(rawId);
            } catch (IllegalArgumentException exception) {
                throw new IllegalStateException("Некорректный UUID в Spec 2.0: " + rawId, exception);
            }
            ConfigurationSection player = players.getConfigurationSection(rawId);
            if (player == null) {
                throw new IllegalStateException("Запись Spec 2.0 игрока " + rawId + " должна быть YAML-разделом");
            }

            String main = null;
            if (player.contains("main")) {
                Object rawMain = player.get("main");
                if (!(rawMain instanceof String value) || !isValidKey(value)) {
                    throw new IllegalStateException("Некорректная основная специализация игрока " + rawId);
                }
                main = value;
            }

            Map<String, Map<String, Integer>> ranks = new LinkedHashMap<>();
            if (player.contains("ranks")) {
                ConfigurationSection trees = player.getConfigurationSection("ranks");
                if (trees == null) {
                    throw new IllegalStateException("Ранги Spec 2.0 игрока " + rawId + " должны быть YAML-разделом");
                }
                for (String treeId : trees.getKeys(false)) {
                    if (!isValidKey(treeId)) {
                        throw new IllegalStateException("Некорректный идентификатор дерева Spec 2.0: " + treeId);
                    }
                    ConfigurationSection tree = trees.getConfigurationSection(treeId);
                    if (tree == null) {
                        throw new IllegalStateException("Дерево Spec 2.0 " + treeId + " должно быть YAML-разделом");
                    }
                    Map<String, Integer> treeRanks = new LinkedHashMap<>();
                    for (String nodeId : tree.getKeys(false)) {
                        Object rawRank = tree.get(nodeId);
                        if (!isValidKey(nodeId)
                                || !(rawRank instanceof Number number)
                                || number.doubleValue() != (double) number.intValue()
                                || number.intValue() <= 0) {
                            throw new IllegalStateException(
                                    "Некорректный ранг Spec 2.0: " + rawId + "/" + treeId + "/" + nodeId);
                        }
                        treeRanks.put(nodeId, number.intValue());
                    }
                    if (!treeRanks.isEmpty()) {
                        ranks.put(treeId, treeRanks);
                    }
                }
            }
            result.put(playerId, new Spec2Data(main, ranks));
        }
        return result;
    }

    private static Map<UUID, Spec2Data> merge(
            Map<UUID, Spec2Data> current, Map<UUID, Spec2Data> legacy, Logger logger) {
        Map<UUID, Spec2Data> merged = new LinkedHashMap<>(current);
        for (Map.Entry<UUID, Spec2Data> entry : legacy.entrySet()) {
            UUID playerId = entry.getKey();
            Spec2Data old = entry.getValue();
            Spec2Data fresh = merged.get(playerId);
            if (fresh == null) {
                merged.put(playerId, old);
                continue;
            }

            String main = fresh.main() == null ? old.main() : fresh.main();
            if (fresh.main() != null && old.main() != null && !fresh.main().equals(old.main())) {
                logger.warning("spec2: конфликт основной специализации " + playerId + "; приоритет у "
                        + CURRENT_FILE_NAME + ", legacy-копия будет архивирована");
            }

            Map<String, Map<String, Integer>> ranks = deepCopy(fresh.ranks());
            for (Map.Entry<String, Map<String, Integer>> tree : old.ranks().entrySet()) {
                Map<String, Integer> targetRanks =
                        ranks.computeIfAbsent(tree.getKey(), ignored -> new LinkedHashMap<>());
                for (Map.Entry<String, Integer> rank : tree.getValue().entrySet()) {
                    Integer existing = targetRanks.putIfAbsent(rank.getKey(), rank.getValue());
                    if (existing != null && !existing.equals(rank.getValue())) {
                        logger.warning("spec2: конфликт ранга " + playerId + "/" + tree.getKey() + "/" + rank.getKey()
                                + "; приоритет у " + CURRENT_FILE_NAME);
                    }
                }
            }
            merged.put(playerId, new Spec2Data(main, ranks));
        }
        return merged;
    }

    private static Map<String, Map<String, Integer>> deepCopy(Map<String, Map<String, Integer>> source) {
        Map<String, Map<String, Integer>> copy = new LinkedHashMap<>();
        source.forEach((treeId, ranks) -> copy.put(treeId, new LinkedHashMap<>(ranks)));
        return copy;
    }

    private static void mergeIntoProfile(
            CharacterProfileStorage profiles, UUID playerId, Spec2Data source, Logger logger) {
        String existingMain = profiles.getSpecializationMain(playerId);
        if (existingMain == null && source.main() != null) {
            profiles.setSpecializationMain(playerId, source.main());
        } else if (existingMain != null && source.main() != null && !existingMain.equals(source.main())) {
            logger.warning("spec2: конфликт основной специализации " + playerId
                    + "; приоритет у characters.yml, исходное значение сохранено в архиве");
        }

        for (Map.Entry<String, Map<String, Integer>> tree : source.ranks().entrySet()) {
            Map<String, Integer> existing =
                    new LinkedHashMap<>(profiles.getSpecializationRanks(playerId, tree.getKey()));
            boolean changed = false;
            for (Map.Entry<String, Integer> rank : tree.getValue().entrySet()) {
                Integer current = existing.putIfAbsent(rank.getKey(), rank.getValue());
                if (current == null) {
                    changed = true;
                } else if (!current.equals(rank.getValue())) {
                    logger.warning("spec2: конфликт ранга " + playerId + "/" + tree.getKey() + "/" + rank.getKey()
                            + "; приоритет у characters.yml, исходное значение сохранено в архиве");
                }
            }
            if (changed) {
                profiles.setSpecializationRanks(playerId, tree.getKey(), existing);
            }
        }
    }

    private static void archiveSource(File directory, String name, Source source, Logger logger) {
        if (!source.present()) {
            return;
        }
        File[] files = {
            new File(directory, name), new File(directory, name + ".tmp"), new File(directory, name + ".bak")
        };
        for (File file : files) {
            if (!file.exists()) {
                continue;
            }
            File target = nextArchiveFile(file);
            try {
                try {
                    Files.move(file.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException exception) {
                    Files.move(file.toPath(), target.toPath());
                }
                logger.info("spec2: архивирован " + file.getName() + " → " + target.getName());
            } catch (IOException exception) {
                logger.warning("spec2: не удалось архивировать " + file.getName() + "; файл оставлен на месте: "
                        + exception.getMessage());
            }
        }
    }

    /**
     * После миграции backup тоже должен содержать единый снимок.
     * Иначе восстановление старого characters.yml.bak могло бы вернуть профиль
     * без specialization после того, как отдельный spec2-файл уже архивирован.
     */
    private static void refreshUnifiedBackup(File directory, File unifiedFile, Logger logger) {
        File temporary = new File(directory, CharacterProfileStorage.FILE_NAME + ".bak.tmp");
        File backup = new File(directory, CharacterProfileStorage.FILE_NAME + ".bak");
        File archivedBackup = null;
        try {
            Files.copy(unifiedFile.toPath(), temporary.toPath(), StandardCopyOption.REPLACE_EXISTING);
            if (backup.exists()) {
                archivedBackup = nextArchiveFile(backup);
                Files.move(backup.toPath(), archivedBackup.toPath());
            }
            try {
                Files.move(
                        temporary.toPath(),
                        backup.toPath(),
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            if (archivedBackup != null && archivedBackup.exists() && !backup.exists()) {
                try {
                    Files.move(archivedBackup.toPath(), backup.toPath());
                } catch (IOException restoreException) {
                    logger.severe("spec2: не удалось восстановить прежний backup characters.yml: "
                            + restoreException.getMessage());
                }
            }
            if (temporary.exists() && !temporary.delete()) {
                logger.warning("spec2: не удалось удалить временный backup " + temporary.getName());
            }
            throw new IllegalStateException(
                    "Не удалось обновить backup единого characters.yml; исходные Spec 2.0 файлы сохранены", exception);
        }
        logger.info("spec2: backup characters.yml обновлён единым снимком профиля и специализаций");
    }

    private static File nextArchiveFile(File source) {
        File parent = source.getParentFile();
        File target = new File(parent, source.getName() + ".migrated");
        int suffix = 1;
        while (target.exists()) {
            target = new File(parent, source.getName() + ".migrated-" + suffix++);
        }
        return target;
    }

    private static boolean isValidKey(String value) {
        return value != null && value.matches("[A-Za-z0-9_-]{1,64}");
    }

    private record Source(boolean present, boolean readable, YamlConfiguration yaml) {}

    private record Spec2Data(String main, Map<String, Map<String, Integer>> ranks) {
        private Spec2Data {
            ranks = Map.copyOf(ranks);
        }
    }
}
