// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PluginReloadResultTest {

    @Test
    void successfulReloadHasNoFailureMessage() {
        PluginReloadResult result = PluginReloadResult.success();

        assertTrue(result.successful());
        assertEquals("", result.message());
    }

    @Test
    void failedReloadIncludesStageAndPartialApplicationWarning() {
        PluginReloadResult result = PluginReloadResult.failure("предметы и крафт", "crafting.yml не загружен");

        assertFalse(result.successful());
        assertTrue(result.message().contains("предметы и крафт"));
        assertTrue(result.message().contains("crafting.yml не загружен"));
        assertTrue(result.message().contains("автоматического отката нет"));
    }

    @Test
    void failedReloadUsesFallbackWhenDetailIsMissing() {
        PluginReloadResult result = PluginReloadResult.failure("config.yml", null);

        assertFalse(result.successful());
        assertTrue(result.message().contains("причина не указана"));
    }
}
