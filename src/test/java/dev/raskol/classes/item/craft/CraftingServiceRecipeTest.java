// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.item.craft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class CraftingServiceRecipeTest {
    @Test
    void fragmentsAreCopiedOnInputAndOutput() {
        Map<String, Integer> source = new HashMap<>();
        source.put("frag_weapon_rare", 3);
        CraftingService.Recipe recipe = new CraftingService.Recipe("test", "target", 10, source);

        source.put("frag_weapon_rare", 9);
        assertEquals(3, recipe.fragments().get("frag_weapon_rare"));
        assertThrows(
                UnsupportedOperationException.class, () -> recipe.fragments().put("other", 1));
    }
}
