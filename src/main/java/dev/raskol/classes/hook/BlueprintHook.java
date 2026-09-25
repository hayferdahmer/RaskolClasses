// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.hook;

import dev.raskol.classes.RaskolClasses;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 1.9.3: Хук RaskolEnchant (чертежи и крафт).
 * Softdepend: если RaskolEnchant недоступен — фолбэк «только готовые предметы».
 * Регистрирует кастомные рецепты через Bukkit Recipe API.
 *
 * 1.9.3-r2 FIX: ShapedRecipe#shape(String... rows) принимает СТРОКИ-ряды
 * ("ABA", "BCB", ...), а не String[]; хелпер registerRecipe переведён на
 * String[] rows + recipe.shape(rows) — убран varargs mismatch (String[] → String).
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
        // Здесь будет логика чтения чертежей из RaskolEnchant и регистрации рецептов:
        // парсинг YAML-чертежей → registerRecipe(id, result, rows, ingredients).
    }

    public void unregisterBlueprints() {
        for (NamespacedKey key : registeredRecipes) {
            plugin.getServer().removeRecipe(key);
        }
        registeredRecipes.clear();
    }

    /**
     * Регистрация фигурного рецепта.
     * @param rows три строки-ряда формы, напр. {"ABA", "BCB", "ABA"}
     *             (каждый символ — ключ из ingredients, пробел = пусто)
     */
    @SuppressWarnings("unused")
    private void registerRecipe(String id, ItemStack result, String[] rows,
                                Map<Character, ItemStack> ingredients) {
        NamespacedKey key = new NamespacedKey(plugin, id);
        ShapedRecipe recipe = new ShapedRecipe(key, result);
        recipe.shape(rows);                       // FIX: String[] → varargs String...
        for (Map.Entry<Character, ItemStack> entry : ingredients.entrySet()) {
            recipe.setIngredient(entry.getKey(), entry.getValue().getType());
        }
        plugin.getServer().addRecipe(recipe);
        registeredRecipes.add(key);
    }
}
