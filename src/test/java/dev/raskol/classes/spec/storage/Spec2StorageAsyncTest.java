// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.spec.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Path;
import java.util.Map;
import java.nio.file.Files;
import java.util.UUID;
import java.util.logging.Logger;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.raskol.classes.storage.AsyncStorageWriter;

/** Проверки асинхронного сохранения и поколений данных Spec2Storage. */
@DisplayName("Spec2Storage: асинхронная запись снимков")
class Spec2StorageAsyncTest {

    private static final Logger LOGGER = Logger.getLogger("Spec2StorageAsyncTest");

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("снимок записывается, а dirty снимается после подтверждения")
    void persistsSnapshotAndClearsDirtyAfterSuccess() {
        File file = tempDir.resolve("spec2-storage.yml").toFile();
        AsyncStorageWriter writer = new AsyncStorageWriter(LOGGER);
        Spec2Storage storage = new Spec2Storage(file, new YamlConfiguration(), writer, LOGGER);
        UUID playerId = UUID.randomUUID();

        storage.setMain(playerId, "restoration");
        assertTrue(storage.isDirty());
        assertTrue(storage.save());
        assertTrue(storage.close());

        YamlConfiguration saved = YamlConfiguration.loadConfiguration(file);
        assertEquals("restoration", saved.getString("players." + playerId + ".main"));
        assertFalse(storage.isDirty());
    }

    @Test
    @DisplayName("неудачная запись не очищает dirty-состояние")
    void failedWriteKeepsDirtyState() {
        File blockedParent = tempDir.resolve("not-a-directory").toFile();
        try {
            Files.writeString(blockedParent.toPath(), "blocker");
        } catch (java.io.IOException exception) {
            throw new AssertionError(exception);
        }
        File target = new File(blockedParent, "spec2-storage.yml");
        AsyncStorageWriter writer = new AsyncStorageWriter(LOGGER);
        Spec2Storage storage = new Spec2Storage(target, new YamlConfiguration(), writer, LOGGER);

        storage.setRanks(UUID.randomUUID(), "restoration", Map.of("healing_touch", 1));

        assertFalse(storage.close());
        assertTrue(storage.isDirty());
    }

    @Test
    @DisplayName("изменения после постановки снимка остаются dirty до следующего сохранения")
    void newerMutationRemainsDirtyAfterOlderSnapshotCompletes() throws Exception {
        File file = tempDir.resolve("generations.yml").toFile();
        AsyncStorageWriter writer = new AsyncStorageWriter(LOGGER);
        Spec2Storage storage = new Spec2Storage(file, new YamlConfiguration(), writer, LOGGER);
        UUID playerId = UUID.randomUUID();

        storage.setMain(playerId, "first");
        assertTrue(storage.save());
        storage.setMain(playerId, "second");
        assertTrue(storage.isDirty());
        assertTrue(storage.save());
        assertTrue(storage.close());

        YamlConfiguration saved = YamlConfiguration.loadConfiguration(file);
        assertEquals("second", saved.getString("players." + playerId + ".main"));
        assertFalse(storage.isDirty());
    }
}
