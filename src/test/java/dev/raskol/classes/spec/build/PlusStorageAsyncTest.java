// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.spec.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.raskol.classes.storage.AsyncStorageWriter;

class PlusStorageAsyncTest {
    private static final Logger LOGGER = Logger.getLogger("PlusStorageAsyncTest");

    @TempDir
    Path tempDir;

    @Test
    void commitsSubscriptionOnlyAfterAsyncSnapshotIsWritten() throws Exception {
        AsyncStorageWriter writer = new AsyncStorageWriter(LOGGER);
        try {
            PlusStorage storage = new PlusStorage(tempDir.toFile(), writer);
            UUID player = UUID.randomUUID();

            assertTrue(storage.extendAsync(player, TimeUnit.DAYS.toMillis(30), "Подарок").get(5, TimeUnit.SECONDS));
            assertTrue(storage.expiresAt(player) > System.currentTimeMillis());
            assertEquals("Подарок", YamlConfiguration.loadConfiguration(
                    tempDir.resolve("plus-subscription.yml").toFile())
                    .getString("players." + player + ".gift-note"));
        } finally {
            assertTrue(writer.closeAndAwait(Duration.ofSeconds(5)));
        }
    }

    @Test
    void failedWriteDoesNotGrantSubscriptionOrConsumeGiftNote() throws Exception {
        Path blocker = tempDir.resolve("not-a-directory");
        Files.writeString(blocker, "file");
        AsyncStorageWriter writer = new AsyncStorageWriter(LOGGER);
        try {
            PlusStorage storage = new PlusStorage(blocker.toFile(), writer);
            UUID player = UUID.randomUUID();

            assertFalse(storage.extendAsync(player, TimeUnit.DAYS.toMillis(30), "Подарок").get(5, TimeUnit.SECONDS));
            assertEquals(0L, storage.expiresAt(player));
            assertEquals(0, storage.activeCount(System.currentTimeMillis()));
        } finally {
            assertTrue(writer.closeAndAwait(Duration.ofSeconds(5)));
        }
    }

}
