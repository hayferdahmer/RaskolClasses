package dev.raskol.classes.api.items;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import dev.raskol.classes.item.config.ItemTemplateExpander;
import dev.raskol.classes.item.model.ItemDefinition;
import dev.raskol.classes.item.model.ItemRarity;
import dev.raskol.classes.item.service.ItemRegistry;

class ItemsApiTest {
    @Test
    void resolvesCommonRarityDeclaredByItemsConfiguration() {
        ItemRegistry registry = registry(List.of(ItemRarity.COMMON, ItemRarity.RARE, ItemRarity.EPIC));

        ItemDefinition definition =
                ItemsApi.resolveTemplate(registry, "warrior_main_hand", 20, ItemRarity.COMMON);

        assertEquals("warrior_main_hand_20_common", definition.id());
        assertEquals(ItemRarity.COMMON, definition.rarity());
    }

    @Test
    void rejectsRarityThatIsNotConfiguredInsteadOfSubstitutingLegendary() {
        ItemRegistry registry = registry(List.of(ItemRarity.COMMON, ItemRarity.RARE, ItemRarity.EPIC));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> ItemsApi.resolveTemplate(registry, "warrior_main_hand", 20, ItemRarity.LEGENDARY));

        assertTrue(exception.getMessage().contains("Available rarities: COMMON, RARE, EPIC"));
    }

    @Test
    void supportsRaritiesConfiguredWithoutApiCodeChanges() {
        ItemRegistry registry = registry(List.of(ItemRarity.UNCOMMON, ItemRarity.MYTHIC));

        ItemDefinition definition =
                ItemsApi.resolveTemplate(registry, "warrior_main_hand", 20, ItemRarity.MYTHIC);

        assertEquals(ItemRarity.MYTHIC, definition.rarity());
        assertEquals("warrior_main_hand_20_mythic", definition.id());
    }

    @Test
    void selectsNearestLevelFromLoadedRegistry() {
        ItemRegistry registry = registry(List.of(ItemRarity.COMMON, ItemRarity.RARE, ItemRarity.EPIC));

        ItemDefinition definition =
                ItemsApi.resolveTemplate(registry, "warrior_main_hand", 35, ItemRarity.RARE);

        assertEquals(40, definition.itemLevel());
    }

    private static ItemRegistry registry(List<ItemRarity> rarities) {
        ItemTemplateExpander.Template template = ItemTemplateExpander.starterTemplates().stream()
                .filter(candidate -> candidate.id().equals("warrior_main_hand"))
                .findFirst()
                .orElseThrow();
        List<ItemDefinition> definitions = ItemTemplateExpander.expand(
                List.of(template), Map.of(), List.of(20, 40, 60), rarities);
        return new ItemRegistry(definitions);
    }
}
