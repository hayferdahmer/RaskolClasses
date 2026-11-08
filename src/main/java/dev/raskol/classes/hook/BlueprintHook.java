// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.hook;

import org.bukkit.plugin.Plugin;

import dev.raskol.classes.RaskolClasses;

/**
 * Проверяет наличие RaskolEnchant.
 *
 * Загрузка чертежей и регистрация рецептов пока не реализованы.
 * Наличие внешнего плагина не означает, что интеграция активна.
 */
public final class BlueprintHook {

    private final RaskolClasses plugin;
    private final Plugin enchantPlugin;

    public BlueprintHook(RaskolClasses plugin) {
        this.plugin = plugin;
        this.enchantPlugin = plugin.getServer().getPluginManager().getPlugin("RaskolEnchant");
    }

    public boolean isAvailable() {
        return enchantPlugin != null && enchantPlugin.isEnabled();
    }

    /**
     * Сейчас рецепты не регистрируются, поэтому при выключении удалять нечего.
     * Метод сохраняет контракт жизненного цикла, используемый главным плагином.
     */
    public void unregisterBlueprints() {
        // Регистрация рецептов будет добавлена вместе с загрузчиком чертежей.
    }

    public void registerBlueprints() {
        if (!isAvailable()) {
            plugin.getLogger().info("RaskolEnchant: не найден — интеграция с чертежами отключена");
            return;
        }

        plugin.getLogger().warning(
                "RaskolEnchant обнаружен, но загрузка чертежей и регистрация рецептов ещё не реализованы; интеграция не активна");
    }
}
