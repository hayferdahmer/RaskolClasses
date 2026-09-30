// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.cc;

import dev.raskol.classes.RaskolClasses;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.potion.PotionEffectType;

/**
 * 1.13.0 (Б3): обёртка ванильных эффектов контроля в нашу CC-систему (ТЗ п.8).
 *
 * Whitelist (pure-маппинг wrapTarget, selftest-чек 85):
 *   SLOWNESS  → SLOW
 *   BLINDNESS → BLIND
 *   WEAKNESS  → SILENCE
 * Всё остальное (JUMP_BOOST, SPEED, REGENERATION…) — не трогаем.
 *
 * Правила синхронизации:
 *  - ADDED whitelisted-эффекта отменяется и превращается в tryApply(null, …) с
 *    ванильной длительностью → проходит DR, иммунитеты и ccResist как наш CC;
 *  - наш собственный туман BLINDNESS (CCService.applyBlindFog) пропускается:
 *    пока активен CCType.BLIND, события BLINDNESS игнорируются;
 *  - CLEARED (/effect clear) и молоко (MILK_BUCKET) снимают наши CC через
 *    CCService.removeAll, но DR-стеки НЕ сбрасывают (ТЗ п.5.5);
 *  - рекурсии нет: мы не накладываем ванильные эффекты кроме guard-тумана BLIND.
 *
 * 1.13.0-fix: event.getType() → event.getModifiedType() (Bukkit API:
 *         EntityPotionEffectEvent использует getModifiedType, а не getType).
 */
public final class VanillaCCWrapper implements Listener {

    private final RaskolClasses plugin;

    public VanillaCCWrapper(RaskolClasses plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /** Pure-маппинг ванильного эффекта в CC-тип; null = не оборачиваем. */
    public static CCType wrapTarget(PotionEffectType type) {
        if (type == null) {
            return null;
        }
        if (type.equals(PotionEffectType.SLOWNESS)) {
            return CCType.SLOW;
        }
        if (type.equals(PotionEffectType.BLINDNESS)) {
            return CCType.BLIND;
        }
        if (type.equals(PotionEffectType.WEAKNESS)) {
            return CCType.SILENCE;
        }
        return null;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEffectAdd(EntityPotionEffectEvent event) {
        if (!(event.getEntity() instanceof LivingEntity le)) {
            return;
        }
        // 1.13.0-fix: event.getModifiedType() вместо event.getType()
        PotionEffectType modifiedType = event.getModifiedType();
        // наш туман слепоты не оборачиваем и не даём ему уйти в DR повторно
        if (PotionEffectType.BLINDNESS.equals(modifiedType)
                && plugin.getCC().has(le.getUniqueId(), CCType.BLIND)) {
            return;
        }
        if (event.getAction() == EntityPotionEffectEvent.Action.CLEARED) {
            // /effect clear: снять наши CC, DR оставить
            plugin.getCC().removeAll(le.getUniqueId());
            return;
        }
        if (event.getAction() != EntityPotionEffectEvent.Action.ADDED) {
            return;
        }
        CCType cc = wrapTarget(modifiedType);
        if (cc == null) {
            return;
        }
        int durationTicks = event.getNewEffect() != null
                ? event.getNewEffect().getDuration()
                : plugin.getCC().durationTicks(cc);
        if (durationTicks <= 0) {
            durationTicks = plugin.getCC().durationTicks(cc);
        }
        event.setCancelled(true);
        plugin.getCC().tryApply(null, le, cc, durationTicks);
    }

    /** Молоко снимает наши CC синхронно с ванильными, но DR НЕ сбрасывает. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMilk(PlayerItemConsumeEvent event) {
        if (event.getItem().getType() != Material.MILK_BUCKET) {
            return;
        }
        Player player = event.getPlayer();
        plugin.getCC().removeAll(player.getUniqueId());
    }
}
