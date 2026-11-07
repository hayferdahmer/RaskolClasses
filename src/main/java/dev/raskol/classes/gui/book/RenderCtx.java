// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.gui.book;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.gui.ClassBook;

/**
 * 1.11.4 (P4b): контекст рендера/клика таба: плагин, игрок, класс,
 * инвентарь книги и ссылка на фасад для refresh.
 */
public record RenderCtx(RaskolClasses plugin, Player player, PlayerClass pc, Inventory inv, ClassBook book) {

    public void refresh() {
        book.refresh(plugin, player);
    }
}
