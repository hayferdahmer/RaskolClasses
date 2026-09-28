// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.gui.book;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.gui.ClassBook;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

/**
 * 1.11.4 (P4b): контекст рендера/клика таба: плагин, игрок, класс,
 * инвентарь книги и ссылка на фасад для refresh.
 */
public record RenderCtx(RaskolClasses plugin, Player player, PlayerClass pc,
                        Inventory inv, ClassBook book) {

    public void refresh() {
        book.refresh(plugin, player);
    }
}
