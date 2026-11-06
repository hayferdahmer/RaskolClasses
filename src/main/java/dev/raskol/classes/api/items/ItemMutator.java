// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.api.items;

import org.bukkit.inventory.ItemStack;

/** Extension seam for future socketing, crafting and enchanting patches. */
public interface ItemMutator {
    ItemStack mutate(ItemStack item);
}
