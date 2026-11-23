// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("StartupChecks: сохранность recovery snapshots")
class StartupChecksTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("старый YAML tmp сохраняется для восстановления SafeStorage")
    void preservesStaleYamlTmpFiles() throws IOException {
        File staleTmp = tempDir.resolve("health.yml.tmp").toFile();
        Files.writeString(staleTmp.toPath(), "players: preserved\n");
        assertTrue(staleTmp.setLastModified(1L));

        int staleCount = StartupChecks.reportStaleTmpFiles(tempDir.toFile(), Logger.getLogger("StartupChecksTest"));

        assertEquals(1, staleCount);
        assertTrue(staleTmp.isFile());
        assertEquals("players: preserved\n", Files.readString(staleTmp.toPath()));
    }
}
