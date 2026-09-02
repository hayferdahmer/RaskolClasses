// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.spec;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.classsystem.PlayerClass;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Слушатели для применения пассивок спеков (1.4.0, Пакет 1).
 * TODO: Пакет 2 — перенос талантов 25/50/75 из Skript в плагин.
 */
public final class SpecListener implements Listener {

    private final RaskolClasses plugin;
    private final Map<UUID, Long> lastProc = new ConcurrentHashMap<>();

    public SpecListener(RaskolClasses plugin) {
        this.plugin = plugin;
    }

    /**
     * Пример: Берсерк — +15% урона при ярости ≥ 50.
     * TODO: Реализовать полную логику всех спеков в Пакете 2.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) {
            return;
        }
        Spec spec = plugin.getSpecService().getSpec(attacker.getUniqueId());
        if (spec == null) {
            return;
        }

        // Пример: Berserker +15% damage when rage >= 50
        if (spec == Spec.BERSERKER) {
            double rage = plugin.getResources().getValue(attacker.getUniqueId());
            if (rage >= 50.0) {
                event.setDamage(event.getDamage() * 1.15);
            }
        }

        // Пример: Liquidator +10% crit chance
        if (spec == Spec.LIQUIDATOR) {
            if (Math.random() < 0.10) {
                event.setDamage(event.getDamage() * 1.5);
            }
        }
    }

    /**
     * Пример: Светоносец — +20% к лечению.
     * TODO: Реализовать полную логику всех спеков в Пакете 2.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHeal(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        Spec spec = plugin.getSpecService().getSpec(player.getUniqueId());
        if (spec == Spec.LIGHTBEARER) {
            event.setAmount(event.getAmount() * 1.20);
        }
    }
}
