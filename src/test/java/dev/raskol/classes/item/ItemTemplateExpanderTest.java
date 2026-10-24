// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.item;

import dev.raskol.classes.item.config.ItemTemplateExpander;
import dev.raskol.classes.item.model.ItemDefinition;
import dev.raskol.classes.item.model.ItemRarity;
import dev.raskol.classes.item.model.ItemStat;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemTemplateExpanderTest {
    @Test void starterStockHas270DefinitionsAndIncludesWarlock(){
        var items=ItemTemplateExpander.expand(ItemTemplateExpander.starterTemplates(),Map.of(),List.of(20,40,60),List.of(ItemRarity.COMMON,ItemRarity.RARE,ItemRarity.EPIC));
        assertEquals(270,items.size());assertTrue(items.stream().anyMatch(i->i.id().startsWith("warlock_")));
        assertEquals(270,items.stream().map(ItemDefinition::id).distinct().count());
    }
    @Test void budgetScalesByLevelAndRarity(){
        var t=ItemTemplateExpander.starterTemplates().get(0);
        var items=ItemTemplateExpander.expand(List.of(t),Map.of(),List.of(20,40),List.of(ItemRarity.COMMON,ItemRarity.EPIC));
        double c20=items.get(0).baseStats().get(ItemStat.ATTACK_DAMAGE);
        double e20=items.get(1).baseStats().get(ItemStat.ATTACK_DAMAGE);
        double c40=items.get(2).baseStats().get(ItemStat.ATTACK_DAMAGE);
        assertTrue(e20>c20);assertTrue(c40>c20);
    }
}
