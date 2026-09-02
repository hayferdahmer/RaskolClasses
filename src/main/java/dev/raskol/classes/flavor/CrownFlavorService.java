// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.flavor;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.hook.FactionHook;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.Locale;
import java.util.UUID;

/**
 * Королевские вкусы (1.4.0, Пакет 4): титул + партикл-аура по фракции.
 * Один класс выглядит по-разному в Рассвете и Вальрадисе.
 * Всё числовое/текстовое — в конфиге (секция flavors).
 */
public final class CrownFlavorService implements Listener {

    private final RaskolClasses plugin;
    private final FactionHook factionHook;

    public CrownFlavorService(RaskolClasses plugin, FactionHook factionHook) {
        this.plugin = plugin;
        this.factionHook = factionHook;
    }

    public String factionOf(UUID uuid) {
        return factionHook.factionOf(uuid);
    }

    /** «Рассвет» / «Вальрадис» / «Вне короны». */
    public String crownDisplayName(UUID uuid) {
        String faction = factionHook.factionOf(uuid);
        if (faction.isEmpty()) {
            return "Вне короны";
        }
        return plugin.getConfig().getString("flavors." + faction + ".crown", faction);
    }

    /** Титул класса под короной; "" если вне короны или не задан. */
    public String titleOf(UUID uuid, PlayerClass pc) {
        if (pc == null) {
            return "";
        }
        String faction = factionHook.factionOf(uuid);
        if (faction.isEmpty()) {
            return "";
        }
        return plugin.getConfig().getString("flavors." + faction + ".titles." + pc.name(), "");
    }

    /** Аура: раз в 40 тиков партикл короны вокруг игрока. */
    public BukkitTask startAuraTask() {
        return plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Player online : plugin.getServer().getOnlinePlayers()) {
                String faction = factionHook.factionOf(online.getUniqueId());
                if (faction.isEmpty()) {
                    continue;
                }
                String fallback = "rassvet".equals(faction) ? "END_ROD" : "SOUL_FIRE_FLAME";
                Particle particle = parseParticle(
                        plugin.getConfig().getString("flavors." + faction + ".particle", fallback));
                if (particle == null) {
                    continue;
                }
                online.spawnParticle(particle,
                        online.getLocation().add(0.0, 0.3, 0.0),
                        2, 0.25, 0.2, 0.25, 0.0);
            }
        }, 40L, 40L);
    }

    /** При входе — напомнить титул (если есть). */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PlayerClass pc = plugin.getClassProvider().getClassOf(player);
        String title = titleOf(player.getUniqueId(), pc);
        if (!title.isEmpty()) {
            player.sendMessage(Component.text("Твой титул: ", NamedTextColor.GRAY)
                    .append(Component.text(title,
                            pc != null ? pc.getColor() : NamedTextColor.GOLD)));
        }
    }

    private Particle parseParticle(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        try {
            return Particle.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
