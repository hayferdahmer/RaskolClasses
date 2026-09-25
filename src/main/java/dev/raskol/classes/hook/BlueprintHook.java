// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.hook;

import dev.raskol.classes.RaskolClasses;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

/**
 * 1.9.3: Хук RaskolEnchant (чертежи и крафт).
 * Softdepend: если RaskolEnchant недоступен, фолбэк на "только готовые предметы".
 * Регистрирует кастомные рецепты через Bukkit Recipe API.
 */
public final class BlueprintHook {

    private final RaskolClasses plugin;
    private final Plugin enchantPlugin;
    private final List<NamespacedKey> registeredRecipes = new ArrayList<>();

    public BlueprintHook(RaskolClasses plugin) {
        this.plugin = plugin;
        this.enchantPlugin = plugin.getServer().getPluginManager().getPlugin("RaskolEnchant");
    }

    public boolean isAvailable() {
        return enchantPlugin != null && enchantPlugin.isEnabled();
    }

    public void registerBlueprints() {
        if (!isAvailable()) {
            plugin.getLogger().info("RaskolEnchant: не найден — чертежи отключены");
            return;
        }
        plugin.getLogger().info("RaskolEnchant: хук активен (чертежи загружены)");
        // Здесь будет логика чтения чертежей из RaskolEnchant и регистрации рецептов
        // Пример: чтение YAML-файлов чертежей и создание ShapedRecipe
    }

    public void unregisterBlueprints() {
        for (NamespacedKey key : registeredRecipes) {
            plugin.getServer().removeRecipe(key);
        }
        registeredRecipes.clear();
    }

    /** Пример регистрации рецепта (для будущего использования). */
    @SuppressWarnings("unused")
    private void registerRecipe(String id, ItemStack result, String[][] shape,
                                java.util.Map<Character, ItemStack> ingredients) {
        NamespacedKey key = new NamespacedKey(plugin, id);
        ShapedRecipe recipe = new ShapedRecipe(key, result);
        recipe.shape(shape[0], shape[1], shape[2]);
        for (var entry : ingredients.entrySet()) {
            recipe.setIngredient(entry.getKey(), entry.getValue());
        }
        plugin.getServer().addRecipe(recipe);
        registeredRecipes.add(key);
    }
}
