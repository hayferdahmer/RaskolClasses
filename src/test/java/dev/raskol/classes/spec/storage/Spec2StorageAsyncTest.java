// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.spec.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.raskol.classes.character.profile.CharacterProfileStorage;
import dev.raskol.classes.storage.AsyncStorageWriter;

/** Проверки фасада Spec2Storage над единым characters.yml. */
@DisplayName("Spec2Storage: единое хранилище профиля")
class Spec2StorageAsyncTest {

    private static final Logger LOGGER = Logger.getLogger("Spec2StorageAsyncTest");

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("специализация сохраняется в том же файле, что и профиль персонажа")
    void persistsSpecializationInsideCharacterProfile() {
        File file = tempDir.resolve("characters.yml").toFile();
        AsyncStorageWriter writer = new AsyncStorageWriter(LOGGER);
        CharacterProfileStorage profiles = new CharacterProfileStorage(file, writer, LOGGER);
        Spec2Storage storage = new Spec2Storage(profiles);
        UUID playerId = UUID.randomUUID();

        storage.setMain(playerId, "restoration");
        storage.setRanks(playerId, "restoration", Map.of("healing_touch", 2));
        Map<String, Integer> ranks = storage.getRanks(playerId, "restoration");
        ranks.merge("renewal", 1, Integer::sum);
        storage.setRanks(playerId, "restoration", ranks);
        assertTrue(storage.isDirty());
        assertTrue(storage.save());
        assertTrue(writer.closeAndAwait(Duration.ofSeconds(5)));

        YamlConfiguration saved = YamlConfiguration.loadConfiguration(file);
        String profile = "profiles." + playerId;
        assertEquals("restoration", saved.getString(profile + ".specialization.main"));
        assertEquals(2, saved.getInt(profile + ".specialization.ranks.restoration.healing_touch"));
        assertEquals(1, saved.getInt(profile + ".specialization.ranks.restoration.renewal"));
        assertEquals(3, saved.getInt("schema-version"));
        assertFalse(storage.isDirty());
    }

    @Test
    @DisplayName("неудачная запись общего файла не очищает dirty-состояние")
    void failedWriteKeepsDirtyState() throws Exception {
        File blockedParent = tempDir.resolve("not-a-directory").toFile();
        Files.writeString(blockedParent.toPath(), "blocker");
        File target = new File(blockedParent, "characters.yml");
        AsyncStorageWriter writer = new AsyncStorageWriter(LOGGER);
        CharacterProfileStorage profiles = new CharacterProfileStorage(target, writer, LOGGER);
        Spec2Storage storage = new Spec2Storage(profiles);

        storage.setRanks(UUID.randomUUID(), "restoration", Map.of("healing_touch", 1));

        assertTrue(writer.closeAndAwait(Duration.ofSeconds(5)));
        assertTrue(storage.isDirty());
    }

    @Test
    @DisplayName("последняя мутация попадает в единый снимок после предыдущей записи")
    void newerMutationRemainsDirtyAfterOlderSnapshotCompletes() throws Exception {
        File file = tempDir.resolve("characters.yml").toFile();
        AsyncStorageWriter writer = new AsyncStorageWriter(LOGGER);
        CharacterProfileStorage profiles = new CharacterProfileStorage(file, writer, LOGGER);
        Spec2Storage storage = new Spec2Storage(profiles);
        UUID playerId = UUID.randomUUID();

        storage.setMain(playerId, "first");
        assertTrue(storage.save());
        storage.setMain(playerId, "second");
        assertTrue(storage.isDirty());
        assertTrue(storage.save());
        assertTrue(writer.closeAndAwait(Duration.ofSeconds(5)));

        YamlConfiguration saved = YamlConfiguration.loadConfiguration(file);
        assertEquals("second", saved.getString("profiles." + playerId + ".specialization.main"));
        assertFalse(storage.isDirty());
    }
}
