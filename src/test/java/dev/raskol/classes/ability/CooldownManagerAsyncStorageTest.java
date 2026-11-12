// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.ability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import java.util.logging.Logger;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.raskol.classes.storage.AsyncStorageWriter;

class CooldownManagerAsyncStorageTest {

    private static final Logger LOGGER = Logger.getLogger("CooldownManagerAsyncStorageTest");

    @TempDir
    Path tempDir;

    @Test
    void saveAllPersistsImmutableSnapshotThroughSharedWriter() {
        File file = tempDir.resolve("cooldowns.yml").toFile();
        AsyncStorageWriter writer = new AsyncStorageWriter(LOGGER);
        CooldownManager manager = new CooldownManager(file, writer);
        UUID playerId = UUID.randomUUID();

        manager.start(playerId, "fireball", 60_000L);
        manager.saveAll();
        manager.cancel(playerId, "fireball");

        assertTrue(writer.closeAndAwait(Duration.ofSeconds(5)));

        YamlConfiguration persisted = YamlConfiguration.loadConfiguration(file);
        assertTrue(persisted.getLong(playerId + ".fireball", 0L) > System.currentTimeMillis());
        assertEquals(0, manager.totalEntries());
    }

    @Test
    void savePlayerPersistsSnapshotThroughSharedWriter() {
        File file = tempDir.resolve("single-player-cooldowns.yml").toFile();
        AsyncStorageWriter writer = new AsyncStorageWriter(LOGGER);
        CooldownManager manager = new CooldownManager(file, writer);
        UUID playerId = UUID.randomUUID();

        manager.start(playerId, "frostbolt", 60_000L);
        manager.savePlayer(playerId);
        manager.cancel(playerId, "frostbolt");

        assertTrue(writer.closeAndAwait(Duration.ofSeconds(5)));

        YamlConfiguration persisted = YamlConfiguration.loadConfiguration(file);
        assertTrue(persisted.getLong(playerId + ".frostbolt", 0L) > System.currentTimeMillis());
    }
}
