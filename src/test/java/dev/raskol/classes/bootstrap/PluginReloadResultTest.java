// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class PluginReloadResultTest {

    @Test
    void successfulReloadHasNoFailureMessage() {
        PluginReloadResult result = PluginReloadResult.success();

        assertTrue(result.successful());
        assertEquals("", result.message());
    }

    @Test
    void successfulItemReloadExecutesOperationAndReportsSuccess() {
        AtomicInteger calls = new AtomicInteger();

        PluginReloadResult result = PluginReloadResult.itemsReload(() -> {
            calls.incrementAndGet();
            return true;
        });

        assertTrue(result.successful());
        assertEquals(1, calls.get());
    }

    @Test
    void failedItemReloadExecutesOperationAndReportsFailure() {
        AtomicInteger calls = new AtomicInteger();

        PluginReloadResult result = PluginReloadResult.itemsReload(() -> {
            calls.incrementAndGet();
            return false;
        });

        assertFalse(result.successful());
        assertEquals(1, calls.get());
        assertTrue(result.message().contains("предметы и крафт"));
        assertTrue(result.message().contains("crafting.yml"));
        assertTrue(result.message().contains("автоматического отката нет"));
    }

    @Test
    void failedReloadUsesFallbackWhenDetailIsMissing() {
        PluginReloadResult result = PluginReloadResult.failure("config.yml", null);

        assertFalse(result.successful());
        assertTrue(result.message().contains("причина не указана"));
    }
}
