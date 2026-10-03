// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.hotbar;

import dev.raskol.classes.RaskolClasses;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;

/**
 * 1.6.11: санитизация свитков с мёртвыми/неизвестными id на входе игрока.
 *
 * Две категории «битых» свитков (1.14.0 Б8.2-fix — spec-свитки удалены вместе
 * со SpecToken):
 * 1. Ability-свитки (PDC-ключ ability): id есть, но AbilityRegistry.exists(id)
 *    возвращает false — абилка переименована/удалена между сезонами.
 * 2. Install-свитки (PDC-ключ install): ключ есть, но readType() = null —
 *    id не резолвится в InstallationType.
 *
 * Исторически (1.7.5–1.13.x) сюда входила категория spec-свитков (PDC-ключ
 * spec_ability) — теперь spec_ability-свитки физически не создаются, а старые
 * экземпляры из инвентарей игроков уже выгорели за годы апдейтов. Если в
 * будущем обнаружится живой spec_ability-свиток — он останется, но не нанесёт
 * вреда: ability-реестр его не знает, install-токен тоже.
 *
 * Работает только на join (достаточно редко). Игрок получает одно уведомление
 * с общим числом удалённых свитков.
 */
public final class ScrollSanitizer implements Listener {

    private final RaskolClasses plugin;

    public ScrollSanitizer(RaskolClasses plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        int removed = sanitize(player);
        if (removed > 0) {
            player.sendMessage(Component.text("Удалены устаревшие классовые свитки: " + removed,
                    NamedTextColor.GRAY));
        }
    }

    private int sanitize(Player player) {
        int removed = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null) {
                continue;
            }
            // 1. Ability-свиток: id есть, но в реестре больше не существует
            String abilityId = plugin.getTokens().readId(item);
            if (abilityId != null && !plugin.getAbilities().exists(abilityId)) {
                item.setAmount(0);
                removed++;
                continue;
            }
            // 2. Install-свиток: PDC-ключ есть, но id не резолвится в тип
            if (plugin.getInstallToken().isInstallScroll(item)
                    && plugin.getInstallToken().readType(item) == null) {
                item.setAmount(0);
                removed++;
            }
        }
        if (removed > 0) {
            player.updateInventory();
        }
        return removed;
    }
}
