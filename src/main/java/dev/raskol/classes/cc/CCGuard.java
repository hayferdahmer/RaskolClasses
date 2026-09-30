// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.cc;

import dev.raskol.classes.RaskolClasses;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;

/**
 * 1.13.0 (Батч 2): блокировка действий под CC.
 * 
 * STUN (Оцепенение):
 *  - Запрет атак (EntityDamageByEntityEvent, damager=Player)
 *  - Запрет использования предметов из хотбара (PlayerInteractEvent с item)
 *  - Запрет взаимодействия с блоками (сундуки, двери, печи)
 *  - Запрет питья зелий/еды (PlayerItemConsumeEvent)
 *  - Запрет взаимодействия с сущностями (PlayerInteractEntityEvent)
 *
 * DISARM (Обезоруживание):
 *  - Запрет атак оружием (EntityDamageByEntityEvent, damager=Player, item in hand ≠ AIR)
 *  - Атака кулаком разрешена (с штрафом −50% урона через CombatService)
 *
 * FEAR (Ужас):
 *  - Запрет атак (EntityDamageByEntityEvent)
 *  - Запрет использования предметов
 *
 * SILENCE (Немота):
 *  - Не блокирует действия здесь — блокировка кастов в CastGuard.canCast()
 *
 * Все блокировки тихие (без сообщений игроку — фидбек через партиклы/звуки в Батче 3).
 * CCService.has() проверяет активные CC-экземпляры (не DR-стек).
 */
public final class CCGuard implements Listener {

    private final RaskolClasses plugin;

    public CCGuard(RaskolClasses plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /** STUN/FEAR/DISARM блокируют атаки; DISARM разрешает кулаки (AIR). */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) {
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity)) {
            return;
        }
        CCService cc = plugin.getCC();
        if (cc.has(attacker.getUniqueId(), CCType.STUN)
                || cc.has(attacker.getUniqueId(), CCType.FEAR)) {
            event.setCancelled(true);
            return;
        }
        if (cc.has(attacker.getUniqueId(), CCType.DISARM)) {
            // DISARM: оружие запрещено, кулаки разрешены
            if (attacker.getInventory().getItemInMainHand().getType().isAir()) {
                // Кулак — разрешено, но CombatService применит штраф −50% (Батч 3)
                return;
            }
            event.setCancelled(true);
        }
    }

    /** STUN/FEAR блокируют использование предметов (ПКМ по блокам/воздуху с предметом). */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        CCService cc = plugin.getCC();
        if (cc.has(player.getUniqueId(), CCType.STUN)
                || cc.has(player.getUniqueId(), CCType.FEAR)) {
            // Блокируем только если есть предмет в руке или взаимодействие с блоком
            if (event.getItem() != null || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                event.setCancelled(true);
            }
        }
    }

    /** STUN блокирует взаимодействие с сущностями (торговля, приручение, etc). */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        if (plugin.getCC().has(player.getUniqueId(), CCType.STUN)) {
            event.setCancelled(true);
        }
    }

    /** STUN блокирует питьё зелий/еды. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        if (plugin.getCC().has(player.getUniqueId(), CCType.STUN)) {
            event.setCancelled(true);
        }
    }
}
