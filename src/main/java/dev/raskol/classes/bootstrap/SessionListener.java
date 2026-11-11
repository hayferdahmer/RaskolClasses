// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.bootstrap;

import java.util.UUID;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.gui.book.TalentsTab;

/**
 * 1.14.10 (Sprint 7, Волна 7.2, P0-4-01): сессионные слушатели входа/выхода,
 * вынесенные из анонимных new Listener() в RaskolClasses.onEnable (аудит 4-01).
 *  - onQuit: чистка резистов/атрибутов/уровней/spec2-кэша + GUI-сессии книги (P1-3);
 *  - onJoin: reconcile spec2 и синхронизация HP-атрибута;
 *  - resyncOnline(): разовый прогон по онлайну при старте.
 *
 * Изменения Spec2 при выходе ставятся в очередь асинхронной записи.
 * YAML-снимок формируется на основном потоке, дисковая операция выполняется
 * выделенным последовательным writer без блокировки PlayerQuitEvent.
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
        // 1.15.5 (аудит 2.6-91): фиксация spec2-изменений выходящего игрока
        plugin.getSpec2Storage().saveIfDirty();
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
