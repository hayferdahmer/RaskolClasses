// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.bootstrap;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.gui.book.TalentsTab;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

/**
 * 1.14.10 (Sprint 7, Волна 7.2, P0-4-01): сессионные слушатели входа/выхода,
 * вынесенные из анонимных new Listener() в RaskolClasses.onEnable (аудит 4-01).
 * Поведение идентично прежним анонимным слушателям:
 *  - onQuit: чистка резистов/атрибутов/уровней/spec2-кэша + GUI-сессии книги (P1-3);
 *  - onJoin: reconcile spec2 и синхронизация HP-атрибута;
 *  - resyncOnline(): разовый прогон по онлайну при старте (вызывает владелец).
 */
public final class SessionListener implements Listener {

    private final RaskolClasses plugin;

    public SessionListener(RaskolClasses plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID quitUuid = event.getPlayer().getUniqueId();
        plugin.getResists().clear(quitUuid);
        plugin.getAttributes().clear(quitUuid);
        plugin.getCharacterLevels().invalidate(quitUuid);
        plugin.getSpec2Service().clear(quitUuid);
        // 1.14.7 (Sprint 4, P1-3): GUI-сессия книги не переживает выход
        TalentsTab.clearSession(quitUuid);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getSpec2Service().reconcile(player.getUniqueId());
        plugin.getHpSync().sync(player);
    }

    /** Разовая синхронизация онлайна при старте плагина (игроки, зашедшие до enable). */
    public void resyncOnline() {
        for (Player online : plugin.getServer().getOnlinePlayers()) {
            plugin.getSpec2Service().reconcile(online.getUniqueId());
            plugin.getHpSync().sync(online);
        }
    }
}
