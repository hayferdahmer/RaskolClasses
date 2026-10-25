// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.item.craft;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.item.model.ItemRarity;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 1.15.4: реестр 13 фрагментов (6 осколков × тир + 6 эссенций + реликвия)
 * и операции с ними как с PDC-предметами (отдельный формат frag_v/frag_id,
 * не пересекается с item_v предметного слоя).
 */
public final class FragmentRegistry {

    private static final Map<String, Fragment> ALL = build();

    private final NamespacedKey fragVersion;
    private final NamespacedKey fragId;

    public FragmentRegistry(RaskolClasses plugin) {
        fragVersion = new NamespacedKey(plugin, "frag_v");
        fragId = new NamespacedKey(plugin, "frag_id");
    }

    private static Map<String, Fragment> build() {
        Map<String, Fragment> map = new LinkedHashMap<>();
        for (ItemRarity tier : new ItemRarity[]{ItemRarity.COMMON, ItemRarity.RARE, ItemRarity.EPIC}) {
            String t = tier.name().toLowerCase(Locale.ROOT);
            map.put("frag_weapon_" + t, new Fragment("frag_weapon_" + t, Fragment.Kind.WEAPON, tier, null,
                    Material.IRON_NUGGET, "Осколок оружия (" + rarityRu(tier) + ")"));
            map.put("frag_armor_" + t, new Fragment("frag_armor_" + t, Fragment.Kind.ARMOR, tier, null,
                    Material.IRON_INGOT, "Осколок брони (" + rarityRu(tier) + ")"));
        }
        for (PlayerClass pc : PlayerClass.values()) {
            String c = pc.name().toLowerCase(Locale.ROOT);
            map.put("frag_essence_" + c, new Fragment("frag_essence_" + c, Fragment.Kind.ESSENCE, null, pc,
                    Material.GHAST_TEAR, "Эссенция пути: " + pc.getDisplayName()));
        }
        map.put("frag_relic", new Fragment("frag_relic", Fragment.Kind.RELIC, null, null,
                Material.NETHERITE_SCRAP, "Реликвия Раскола"));
        return Map.copyOf(map);
    }

    private static String rarityRu(ItemRarity rarity) {
        return switch (rarity) {
            case COMMON -> "обычный";
            case RARE -> "редкий";
            case EPIC -> "эпический";
            default -> rarity.name();
        };
    }

    public Fragment byId(String id) {
        return id == null ? null : ALL.get(id);
    }

    public Collection<Fragment> all() {
        return ALL.values();
    }

    /** Создать стак фрагмента с PDC-меткой и цветным именем. */
    public ItemStack createStack(Fragment fragment, int amount) {
        ItemStack stack = new ItemStack(fragment.material(), Math.max(1, amount));
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(fragment.displayName(), colorOf(fragment)));
            meta.lore(java.util.List.of(
                    Component.text("Фрагмент крафта RaskolClasses", NamedTextColor.GRAY),
                    Component.text("Не продаётся · только разбор/крафт", NamedTextColor.DARK_GRAY)));
            meta.getPersistentDataContainer().set(fragVersion, PersistentDataType.INTEGER, 1);
            meta.getPersistentDataContainer().set(fragId, PersistentDataType.STRING, fragment.id());
            stack.setItemMeta(meta);
        }
        return stack;
    }

    /** Фрагмент из стака или null (чужие/ванильные предметы не матчатся). */
    public Fragment fragmentOf(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) {
            return null;
        }
        var p = stack.getItemMeta().getPersistentDataContainer();
        if (!Integer.valueOf(1).equals(p.get(fragVersion, PersistentDataType.INTEGER))) {
            return null;
        }
        return byId(p.get(fragId, PersistentDataType.STRING));
    }

    /** Выдать фрагменты игроку с дропом переполнения. */
    public void addFragments(Player player, Map<Fragment, Integer> amounts) {
        amounts.forEach((fragment, count) -> {
            if (count <= 0) {
                return;
            }
            player.getInventory().addItem(createStack(fragment, count)).values()
                    .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        });
    }

    /** Сколько фрагментов этого типа в инвентаре. */
    public int countInInventory(Player player, Fragment fragment) {
        int total = 0;
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            Fragment f = fragmentOf(stack);
            if (f == fragment) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    /** Списать фрагменты; false если не хватает (инвентарь не трогается). */
    public boolean consume(Player player, Fragment fragment, int count) {
        if (countInInventory(player, fragment) < count) {
            return false;
        }
        int left = count;
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int i = 0; i < contents.length && left > 0; i++) {
            ItemStack stack = contents[i];
            if (fragmentOf(stack) != fragment) {
                continue;
            }
            int take = Math.min(left, stack.getAmount());
            if (take == stack.getAmount()) {
                player.getInventory().setItem(i, null);
            } else {
                stack.setAmount(stack.getAmount() - take);
            }
            left -= take;
        }
        return true;
    }

    private static NamedTextColor colorOf(Fragment fragment) {
        return switch (fragment.kind()) {
            case RELIC -> NamedTextColor.LIGHT_PURPLE;
            case ESSENCE -> NamedTextColor.AQUA;
            case WEAPON, ARMOR -> switch (fragment.tier()) {
                case EPIC -> NamedTextColor.DARK_PURPLE;
                case RARE -> NamedTextColor.BLUE;
                default -> NamedTextColor.WHITE;
            };
        };
    }
}
