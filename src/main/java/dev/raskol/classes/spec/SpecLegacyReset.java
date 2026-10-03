// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.spec;

import dev.raskol.classes.RaskolClasses;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 1.14.0 (Б2): обнуление legacy-билдов талантов + возврат очков.
 * 1.14.0 (Б4-fix): переведён на Map-API TalentsStorage ({nodeId: rank}).
 * Идемпотентно: повторный вызов без legacy-покупок = 0 изменений.
 */
public final class SpecLegacyReset implements Listener {

    private static final List<String> LEGACY_TREE_KEYS = List.of(
            "tracker", "lightbearer", "liquidator", "trickster",
            "black_mage", "hell_channel");

    private final RaskolClasses plugin;

    public SpecLegacyReset(RaskolClasses plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public static List<String> legacyTreeKeys() {
        return LEGACY_TREE_KEYS;
    }

    public void resetAllOnline() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            resetPlayer(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJoin(PlayerJoinEvent event) {
        resetPlayer(event.getPlayer());
    }

    /** Обнулить legacy-покупки; возвращает число стёртых узлов (0 = ничего не тронуто). */
    public int resetPlayer(Player player) {
        if (player == null) {
            return 0;
        }
        UUID uuid = player.getUniqueId();
        int freed = 0;
        for (String key : LEGACY_TREE_KEYS) {
            Map<String, Integer> purchased = plugin.getTalentsStorage().getPurchased(uuid, key);
            if (purchased == null || purchased.isEmpty()) {
                continue;
            }
            freed += purchased.size();
            plugin.getTalentsStorage().setPurchased(uuid, key, new HashMap<>());
        }
        if (freed <= 0) {
            return 0;
        }
        plugin.getTalentsStorage().save();
        plugin.getTalentService().reconcile(uuid);
        player.sendMessage(Component.text(
                "§6Раскол · Спек 2.0: §7старые ветки путей обнулены (узлов: " + freed
                        + "). Очки талантов возвращены — распредели их заново: §6/rc menu §7→ Таланты.",
                NamedTextColor.GOLD));
        plugin.getLogger().info("1.14.0 legacy-reset: " + player.getName()
                + " обнулено узлов=" + freed + " (очки возвращены)");
        return freed;
    }
}
