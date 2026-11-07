// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.item.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemDamageEvent;

import dev.raskol.classes.item.factory.ItemPdc;

/**
 * 1.15.1-C: страховка прочности для предметов RaskolClasses.
 *
 * Предметы, созданные ПОСЛЕ 1.15.1-C, несут флаг unbreakable из ItemFactory и
 * до этого слушателя не доходят (событие не генерируется). Guard покрывает
 * legacy-предметы 1.15.0: PDC v1 есть, флага нет — отменяем урон прочности.
 *
 * Скоуп строго наш: проверяется PDC-маркер raskolclasses:item_v; ванильные
 * инструменты/броня игроков событию не подвергаются (решение «ломаются только
 * не-наши» принято в дорожной карте 1.15.1).
 */
public final class UnbreakableGuard implements Listener {

    private final ItemPdc pdc;

    public UnbreakableGuard(ItemPdc pdc) {
        this.pdc = pdc;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onItemDamage(PlayerItemDamageEvent event) {
        if (pdc.isItem(event.getItem())) {
            event.setCancelled(true);
        }
    }
}
