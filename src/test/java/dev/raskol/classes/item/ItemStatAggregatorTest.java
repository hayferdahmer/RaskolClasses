// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.item;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.item.model.ItemDefinition;
import dev.raskol.classes.item.model.ItemRarity;
import dev.raskol.classes.item.model.ItemSlot;
import dev.raskol.classes.item.model.ItemStat;
import dev.raskol.classes.item.service.ItemStatAggregator;

class ItemStatAggregatorTest {
    private ItemDefinition item(PlayerClass pc, int requirement, double str) {
        return new ItemDefinition(
                "test_item",
                "Test",
                "IRON_CHESTPLATE",
                ItemSlot.CHEST,
                null,
                ItemRarity.COMMON,
                20,
                pc,
                null,
                requirement,
                Map.of(ItemStat.STR, str),
                List.of(),
                0,
                null,
                List.of(),
                false,
                null);
    }

    @Test
    void rangedCriticalItemStatIsRegisteredAndClamped() {
        assertEquals(ItemStat.CRIT_RANGED, ItemStat.fromId("crit-ranged"));
        assertEquals(40.0, ItemStat.CRIT_RANGED.clamp(40.0), 0.0001);
        assertEquals(50.0, ItemStat.CRIT_RANGED.clamp(80.0), 0.0001);
    }

    @Test
    void classAndLevelRequirementsFilterItemStats() {
        var eligible = item(PlayerClass.WARRIOR, 10, 4);
        var wrongClass = item(PlayerClass.MAGE, 1, 20);
        var highLevel = item(PlayerClass.WARRIOR, 50, 30);
        var result = ItemStatAggregator.aggregate(
                List.of(eligible, wrongClass, highLevel), List.of(), PlayerClass.WARRIOR, null, 20);
        assertEquals(4, result.get(ItemStat.STR));
    }
}
