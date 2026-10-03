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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 1.14.0 (Б2): обнуление legacy-билдов талантов по решению гейм-дизайна
 * («миграция не нужна — обнулить старые и дать очки»).
 *
 * Механика: покупки талантов, лежащие под legacy-ключами деревьев
 * (tracker, lightbearer, liquidator, trickster, black_mage, hell_channel),
 * стираются из TalentsStorage; reconcile пересчитывает бюджет —
 * потраченные очки возвращаются игроку полностью (spentGlobal суммирует
 * только живые покупки). Выбор спеки не трогаем: legacy-выбор нормализуется
 * Spec.fromId в современную спеку, либо остаётся legacy-константой до респека
 * (оба пути безопасны, см. Spec.java 1.14.0).
 *
 * Идемпотентность: повторный вызов по игроку без legacy-покупок = 0 изменений,
 * без сообщений и без save. Лог — одна строка на факт обнуления (не спамит).
 */
public final class SpecLegacyReset implements Listener {

    /** Legacy-ключи деревьев, покупки под которыми обнуляются в 1.14.0. */
    private static final List<String> LEGACY_TREE_KEYS = List.of(
            "tracker", "lightbearer", "liquidator", "trickster",
            "black_mage", "hell_channel");

    private final RaskolClasses plugin;

    public SpecLegacyReset(RaskolClasses plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /** Ключи для selftest-чека 93 и диагностики. */
    public static List<String> legacyTreeKeys() {
        return LEGACY_TREE_KEYS;
    }

    /** Разовый прогон по всем онлайн-игрокам (вызывается из onEnable после талент-сервиса). */
    public void resetAllOnline() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            resetPlayer(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJoin(PlayerJoinEvent event) {
        resetPlayer(event.getPlayer());
    }

    /**
     * Обнулить legacy-покупки игрока. Возвращает число стёртых узлов
     * (0 = ничего не тронуто). Публичный для selftest-чека 94.
     */
    public int resetPlayer(Player player) {
        if (player == null) {
            return 0;
        }
        UUID uuid = player.getUniqueId();
        int freed = 0;
        for (String key : LEGACY_TREE_KEYS) {
            List<String> purchased = plugin.getTalentsStorage().getPurchased(uuid, key);
            if (purchased == null || purchased.isEmpty()) {
                continue;
            }
            freed += purchased.size();
            plugin.getTalentsStorage().setPurchased(uuid, key, new ArrayList<>());
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
