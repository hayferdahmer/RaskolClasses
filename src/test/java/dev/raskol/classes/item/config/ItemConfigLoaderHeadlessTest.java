// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.item.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.item.model.ItemRarity;
import dev.raskol.classes.item.service.ItemRegistry;

@DisplayName("ItemConfigLoader: headless production loading")
class ItemConfigLoaderHeadlessTest {

    @Test
    @DisplayName("production items.yml загружается без Bukkit Registry и сохраняет предметы всех классов")
    void loadsProductionRegistryWithoutPaperRegistry() throws Exception {
        List<String> warnings = new ArrayList<>();
        ItemRegistry registry = ItemConfigLoader.loadHeadless(new File("src/main/resources/items.yml"), warnings::add);

        assertTrue(registry.size() > 0);
        assertTrue(registry.all().stream().anyMatch(item -> item.rarity() == ItemRarity.EPIC));
        for (PlayerClass playerClass : PlayerClass.values()) {
            assertFalse(
                    registry.all().stream()
                            .filter(item -> item.playerClass() == playerClass)
                            .toList()
                            .isEmpty(),
                    "В реестре должны быть предметы класса " + playerClass);
        }
    }

    @Test
    @DisplayName("headless material validator отклоняет некорректный идентификатор")
    void rejectsInvalidMaterialIdentifier() {
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class, () -> ItemValidator.validateMaterialSyntax("minecraft:stone;rm -rf"));
    }
}
