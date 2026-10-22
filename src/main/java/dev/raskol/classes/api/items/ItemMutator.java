package dev.raskol.classes.api.items;

import org.bukkit.inventory.ItemStack;

/** Extension seam for future socketing, crafting and enchanting patches. */
public interface ItemMutator {
    ItemStack mutate(ItemStack item);
}
