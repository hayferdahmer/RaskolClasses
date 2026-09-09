// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.hotbar;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.install.InstallationType;
import dev.raskol.classes.spec.Spec;
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
 * Три категории «битых» свитков:
 * 1. Ability-свитки (PDC-ключ ability): id есть, но AbilityRegistry.exists(id)
 *    возвращает false — абилка переименована/удалена между сезонами.
 * 2. Spec-свитки (PDC-ключ spec_ability): ключ есть, но readSpec() = null —
 *    id не резолвится в Spec (удалённая спека, опечатка).
 * 3. Install-свитки (PDC-ключ install): ключ есть, но readType() = null —
 *    id не резолвится в InstallationType.
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
            // 2. Spec-свиток: PDC-ключ есть, но id не резолвится в Spec
            if (plugin.getSpecToken().isSpecScroll(item)
                    && plugin.getSpecToken().readSpec(item) == null) {
                item.setAmount(0);
                removed++;
                continue;
            }
            // 3. Install-свиток: PDC-ключ есть, но id не резолвится в тип
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
