// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.item.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * 1.15.5: hover-превью предметов в чате.
 *
 * Почему showText, а не showItem: Adventure-событие showItem(Key, amount) рендерит
 * ванильный тултип клиента БЕЗ наших строк lore/аффиксов/сетов. Единственный способ
 * показать кастомное содержимое предмета в hover — showText с форматированным блоком
 * displayName + lore. Визуально для игрока это «карточка предмета» в чате.
 */
public final class ItemHover {

    private ItemHover() {
    }

    /** Текстовое превью предмета: имя + все строки lore (для HoverEvent.showText). */
    public static Component lorePreview(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) {
            return Component.text("(пусто)", NamedTextColor.GRAY);
        }
        ItemMeta meta = stack.getItemMeta();
        List<Component> lines = new ArrayList<>();
        Component name = meta.displayName();
        lines.add(name != null ? name : Component.text(stack.getType().name(), NamedTextColor.WHITE));
        if (meta.lore() != null) {
            lines.addAll(meta.lore());
        }
        return Component.join(JoinConfiguration.newlines(), lines);
    }

    /** Обернуть label так, чтобы при наведении показывалось превью предмета. */
    public static Component withPreview(Component label, ItemStack stack) {
        return label.hoverEvent(HoverEvent.showText(lorePreview(stack)));
    }
}
