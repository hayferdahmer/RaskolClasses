// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.hud.runtime;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Level;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

/** Paper-адаптер события завершения игровой сессии игрока. */
public final class PaperHudSessionEvents implements HudSessionEvents {

    private final Plugin plugin;

    public PaperHudSessionEvents(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    @Override
    public HudRegistration onQuit(Consumer<UUID> handler) {
        Objects.requireNonNull(handler, "handler");
        QuitListener listener = new QuitListener(handler, plugin.getLogger());
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        return new PaperHudRegistration(listener);
    }

    private static final class QuitListener implements Listener {

        private final Consumer<UUID> handler;
        private final java.util.logging.Logger logger;

        private QuitListener(Consumer<UUID> handler, java.util.logging.Logger logger) {
            this.handler = handler;
            this.logger = logger;
        }

        @EventHandler(priority = EventPriority.MONITOR)
        public void onPlayerQuit(PlayerQuitEvent event) {
            try {
                handler.accept(event.getPlayer().getUniqueId());
            } catch (RuntimeException failure) {
                logger.log(Level.WARNING, "Не удалось очистить HUD-сессию игрока", failure);
            }
        }
    }

    private static final class PaperHudRegistration implements HudRegistration {

        private final Listener listener;
        private final AtomicBoolean closed = new AtomicBoolean();

        private PaperHudRegistration(Listener listener) {
            this.listener = listener;
        }

        @Override
        public void close() {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            try {
                HandlerList.unregisterAll(listener);
            } catch (RuntimeException failure) {
                closed.set(false);
                throw failure;
            }
        }
    }
}
