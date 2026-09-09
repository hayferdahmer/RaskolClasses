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
 * Чистит инвентарь от свитков способностей с несуществующими id (после
 * переименований в конфиге между сезонами), свитков спеков и инсталляций
 * с неизвестными значениями. Игрок получает уведомление один раз за чистку.
 *
 * Не трогает валидные свитки; не чистит при каждом клике — только на join
 * (достаточно редко и безопасно для производительности).
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
            String abilityId = plugin.getTokens().readId(item);
            if (abilityId != null && !plugin.getAbilities().exists(abilityId)) {
                item.setAmount(0);
                removed++;
                continue;
            }
            Spec spec = plugin.getSpecToken().readSpec(item);
            if (spec == null && plugin.getSpecToken().isSpecScroll(item)) {
                item.setAmount(0);
                removed++;
                continue;
            }
            InstallationType type = plugin.getInstallToken().readType(item);
            if (type == null && plugin.getInstallToken().isInstallScroll(item)) {
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
