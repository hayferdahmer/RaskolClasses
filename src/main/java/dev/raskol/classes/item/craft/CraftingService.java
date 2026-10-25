// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.item.craft;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.hook.EconomyHook;
import dev.raskol.classes.item.model.ItemDefinition;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 1.15.4: крафт предметов из фрагментов + монет (crafting.yml).
 *
 * Формат crafting.yml:
 *   schema-version: 1
 *   recipes:
 *     warrior_sword_40:
 *       target: warrior_main_hand_40_rare     # id из реестра items.yml
 *       coins: 500                             # цена в монетах Vault (0 = бесплатно)
 *       fragments:
 *         frag_weapon_rare: 5
 *         frag_essence_warrior: 2
 *
 * Гарантии: проверка наличия фрагментов ДО списания монет; списание монет ДО
 * создания предмета; ошибка создания → возврат монет.
 * Рецепт с неизвестной целью/фрагментом отключается точечно с warning.
 *
 * 1.15.4-A-fix2: Vault НЕ является compile-зависимостью проекта (pom.xml чистый,
 * EconomyHook работает без импорта milkbowl). Compensation-путь refundCoins()
 * идёт через reflection: ServicesManager → интерфейс Economy по имени класса →
 * depositPlayer(OfflinePlayer, double) → поле EconomyResponse.transactionSuccess.
 * При отсутствии Vault/провайдера возврат не выполняется, но инцидент логируется
 * warning'ом с ником и суммой для ручной компенсации админом.
 */
public final class CraftingService {

    public enum CraftResult { OK, DISABLED, UNKNOWN, NO_TARGET, NO_FRAGS, NO_COINS, NO_ECON }

    public record Recipe(String id, String target, int coins, Map<String, Integer> fragments) {
    }

    private final RaskolClasses plugin;
    private final FragmentRegistry fragments;
    private final EconomyHook economy;
    private final File file;
    private volatile Map<String, Recipe> recipes = Map.of();

    public CraftingService(RaskolClasses plugin, FragmentRegistry fragments) {
        this.plugin = plugin;
        this.fragments = fragments;
        this.economy = new EconomyHook(plugin);
        this.file = new File(plugin.getDataFolder(), "crafting.yml");
    }

    /** Извлечь crafting.yml из jar при отсутствии и загрузить рецепты. */
    public synchronized void reload() {
        if (!file.exists()) {
            plugin.saveResource("crafting.yml", false);
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        Map<String, Recipe> loaded = new LinkedHashMap<>();
        ConfigurationSection section = yaml.getConfigurationSection("recipes");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                ConfigurationSection s = section.getConfigurationSection(id);
                if (s == null) {
                    continue;
                }
                String target = s.getString("target", "");
                int coins = Math.max(0, s.getInt("coins", 0));
                Map<String, Integer> frags = new LinkedHashMap<>();
                ConfigurationSection fragSec = s.getConfigurationSection("fragments");
                if (fragSec != null) {
                    for (String fragId : fragSec.getKeys(false)) {
                        int count = fragSec.getInt(fragId, 0);
                        if (count > 0 && fragments.byId(fragId) != null) {
                            frags.put(fragId, count);
                        } else if (count > 0) {
                            plugin.getLogger().warning("crafting: рецепт " + id
                                    + " отключён: неизвестный фрагмент " + fragId);
                            frags = null;
                            break;
                        }
                    }
                }
                if (frags == null) {
                    continue;
                }
                if (plugin.getItems() != null && plugin.getItems().registry().get(target) == null) {
                    plugin.getLogger().warning("crafting: рецепт " + id
                            + " отключён: неизвестная цель " + target);
                    continue;
                }
                loaded.put(id, new Recipe(id, target, coins, Map.copyOf(frags)));
            }
        }
        this.recipes = Map.copyOf(loaded);
        plugin.getLogger().info("crafting: загружено рецептов " + loaded.size());
    }

    public List<Recipe> recipes() {
        return new ArrayList<>(recipes.values());
    }

    public Recipe recipe(String id) {
        return recipes.get(id);
    }

    /** Выполнить крафт: фрагменты → монеты → предмет с меткой crafted_by. */
    public CraftResult craft(Player player, String recipeId) {
        if (!plugin.getConfig().getBoolean("item.craft.enabled", true)) {
            return CraftResult.DISABLED;
        }
        Recipe recipe = recipes.get(recipeId);
        if (recipe == null) {
            return CraftResult.UNKNOWN;
        }
        ItemDefinition def = plugin.getItems().registry().get(recipe.target());
        if (def == null) {
            return CraftResult.NO_TARGET;
        }
        for (Map.Entry<String, Integer> e : recipe.fragments().entrySet()) {
            Fragment fragment = fragments.byId(e.getKey());
            if (fragment == null || fragments.countInInventory(player, fragment) < e.getValue()) {
                return CraftResult.NO_FRAGS;
            }
        }
        boolean coinsPaid = false;
        if (recipe.coins() > 0) {
            if (!economy.available()) {
                return CraftResult.NO_ECON;
            }
            if (economy.balance(player.getUniqueId()) < recipe.coins()) {
                return CraftResult.NO_COINS;
            }
        }
        // списание: фрагменты → монеты (компенсация при ошибке создания)
        for (Map.Entry<String, Integer> e : recipe.fragments().entrySet()) {
            fragments.consume(player, fragments.byId(e.getKey()), e.getValue());
        }
        if (recipe.coins() > 0) {
            economy.withdraw(player.getUniqueId(), recipe.coins());
            coinsPaid = true;
        }
        try {
            ItemStack created = plugin.getItems().create(recipe.target(), def.itemLevel(),
                    ThreadLocalRandom.current().nextLong());
            plugin.getItems().pdc().markCraftedBy(created, player.getUniqueId());
            player.getInventory().addItem(created).values()
                    .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
            player.sendMessage(Component.text(plugin.getRaskolConfig()
                    .message("item.craft-ok", "⚒ Создано: {item}")
                    .replace("{item}", def.displayName()), NamedTextColor.GREEN));
            return CraftResult.OK;
        } catch (RuntimeException ex) {
            if (coinsPaid) {
                boolean refunded = refundCoins(player, recipe.coins());
                plugin.getLogger().warning("crafting: создание " + recipe.target()
                        + " не удалось; монеты " + (refunded ? "возвращены через Vault"
                        : "НЕ возвращены (нет провайдера/метода) — компенсируй вручную")
                        + ": игрок " + player.getName() + ", сумма " + recipe.coins()
                        + "; причина: " + ex.getMessage());
            } else {
                plugin.getLogger().warning("crafting: создание " + recipe.target()
                        + " не удалось: " + ex.getMessage());
            }
            return CraftResult.NO_TARGET;
        }
    }

    /**
     * 1.15.4-A-fix2: возврат монет через reflection над Vault Economy.
     * Путь: Class.forName("net.milkbowl.vault.economy.Economy") →
     * ServicesManager.getRegistration(iface).getProvider() →
     * depositPlayer(OfflinePlayer, double) → EconomyResponse.transactionSuccess.
     * Любая неудача (нет Vault, нет провайдера, нет метода) = false + warning у вызывающего.
     */
    private boolean refundCoins(Player player, int coins) {
        try {
            Class<?> economyIface = Class.forName("net.milkbowl.vault.economy.Economy");
            var registration = plugin.getServer().getServicesManager().getRegistration(economyIface);
            if (registration == null) {
                return false;
            }
            Object provider = registration.getProvider();
            if (provider == null) {
                return false;
            }
            Method deposit = null;
            for (Method m : economyIface.getMethods()) {
                Class<?>[] p = m.getParameterTypes();
                if ("depositPlayer".equals(m.getName()) && p.length == 2
                        && p[0] == OfflinePlayer.class && p[1] == double.class) {
                    deposit = m;
                    break;
                }
            }
            if (deposit == null) {
                return false;
            }
            Object response = deposit.invoke(provider,
                    Bukkit.getOfflinePlayer(player.getUniqueId()), (double) coins);
            if (response == null) {
                return true; // void-подобная имплементация: считаем возврат выполненным
            }
            Field success = response.getClass().getField("transactionSuccess");
            return success.getBoolean(response);
        } catch (Throwable t) {
            plugin.getLogger().warning("crafting: vault-refund exception: " + t);
            return false;
        }
    }

    /** Сообщение об отказе для UI/команды. */
    public void notifyFailure(Player player, CraftResult result, Recipe recipe) {
        String key;
        String fallback;
        switch (result) {
            case DISABLED -> { key = "item.craft-disabled"; fallback = "⚒ Крафт отключён"; }
            case UNKNOWN -> { key = "item.craft-unknown"; fallback = "⚒ Рецепт не найден"; }
            case NO_TARGET -> { key = "item.craft-target"; fallback = "⚒ Цель рецепта недоступна"; }
            case NO_ECON -> { key = "item.craft-no-econ"; fallback = "⚒ Экономика недоступна — крафт с монетами отключён"; }
            case NO_COINS -> { key = "item.craft-coins"; fallback = "⚒ Не хватает монет: нужно {coins}"; }
            case NO_FRAGS -> { key = "item.craft-frags"; fallback = "⚒ Не хватает фрагментов: {missing}"; }
            default -> { return; }
        }
        String missing = "";
        if (result == CraftResult.NO_FRAGS && recipe != null) {
            StringBuilder sb = new StringBuilder();
            recipe.fragments().forEach((fragId, count) -> {
                Fragment fragment = fragments.byId(fragId);
                int have = fragment == null ? 0 : fragments.countInInventory(player, fragment);
                if (have < count) {
                    sb.append(fragment == null ? fragId : fragment.displayName())
                            .append(" ").append(have).append("/").append(count).append(", ");
                }
            });
            missing = sb.length() > 0 ? sb.substring(0, sb.length() - 2) : "";
        }
        player.sendMessage(Component.text(plugin.getRaskolConfig()
                .message(key, fallback)
                .replace("{coins}", String.valueOf(recipe == null ? 0 : recipe.coins()))
                .replace("{missing}", missing), NamedTextColor.RED));
    }
}
