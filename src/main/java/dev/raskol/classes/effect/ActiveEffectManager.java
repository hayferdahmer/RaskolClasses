// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.effect;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Флаги активных способностей и слушатели, которые их потребляют.
 * Длительности приходят из config (D2); просрочка проверяется лениво,
 * записи чистятся на выходе и purge-тиком (O7).
 */
public final class ActiveEffectManager implements Listener {

    private final Map<UUID, Map<EffectType, Long>> expiresAt = new ConcurrentHashMap<>();

    public void addTimed(UUID playerId, EffectType type, long durationMillis) {
        byPlayer(playerId).put(type, System.currentTimeMillis() + durationMillis);
    }

    /** Разовый флаг: живёт до срабатывания (D3). */
    public void addOneShot(UUID playerId, EffectType type) {
        byPlayer(playerId).put(type, Long.MAX_VALUE);
    }

    public boolean has(UUID playerId, EffectType type) {
        Map<EffectType, Long> map = expiresAt.get(playerId);
        if (map == null) {
            return false;
        }
        Long expires = map.get(type);
        if (expires == null) {
            return false;
        }
        if (expires < System.currentTimeMillis()) {
            map.remove(type);
            return false;
        }
        return true;
    }

    public void remove(UUID playerId, EffectType type) {
        Map<EffectType, Long> map = expiresAt.get(playerId);
        if (map != null) {
            map.remove(type);
        }
    }

    public void clearPlayer(UUID playerId) {
        expiresAt.remove(playerId);
    }

    public void clear() {
        expiresAt.clear();
    }

    /** O7: удалить истёкшие флаги и опустевшие записи игроков. */
    public void purgeExpired() {
        long now = System.currentTimeMillis();
        expiresAt.forEach((playerId, map) -> {
            map.values().removeIf(expires -> expires != Long.MAX_VALUE && expires < now);
            if (map.isEmpty()) {
                expiresAt.remove(playerId);
            }
        });
    }

    /** Возвращает неистёкшие флаги игрока (для /rc debug). Без мутации. */
    public Map<EffectType, Long> getActiveEffects(UUID playerId) {
        Map<EffectType, Long> map = expiresAt.get(playerId);
        if (map == null) {
            return Map.of();
        }
        long now = System.currentTimeMillis();
        Map<EffectType, Long> result = new EnumMap<>(EffectType.class);
        map.forEach((type, expires) -> {
            if (expires == Long.MAX_VALUE || expires >= now) {
                result.put(type, expires);
            }
        });
        return result;
    }

    private Map<EffectType, Long> byPlayer(UUID playerId) {
        return expiresAt.computeIfAbsent(playerId, id -> new EnumMap<>(EffectType.class));
    }

    /* ------------------------- слушатели ------------------------- */

    /** Входящий урон: стальная кожа, уклонение, аспект гепарда, срыв скрытности. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        UUID id = player.getUniqueId();

        if (event.getCause() == EntityDamageEvent.DamageCause.FALL
                && has(id, EffectType.NO_FALL_DAMAGE)) {
            event.setCancelled(true);
            return;
        }

        // Урон пустотой не отменяем — не ломаем ванильную механику
        if (has(id, EffectType.EVASION)
                && event.getCause() != EntityDamageEvent.DamageCause.VOID) {
            event.setCancelled(true);
            return;
        }

        if (has(id, EffectType.SHIELD_WALL)) {
            event.setDamage(event.getDamage() * 0.2); // −80% (Стальная кожа)
        }

        if (has(id, EffectType.STEALTH)) {
            breakStealth(player); // полученный урон срывает невидимость
        }
    }

    /** Исходящий урон: бонус скрытности, прицельный выстрел; входящий с носителем — кровавое безумие. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        Player attacker = resolveAttacker(event.getDamager());
        if (attacker != null) {
            UUID id = attacker.getUniqueId();

            if (has(id, EffectType.STEALTH)) {
                event.setDamage(event.getDamage() * 1.5); // бонус первого удара
                breakStealth(attacker);
            }

            if (has(id, EffectType.AIMED_SHOT) && event.getDamager() instanceof AbstractArrow) {
                remove(id, EffectType.AIMED_SHOT);
                event.setDamage(event.getDamage() * 2.0);
                if (event.getEntity() instanceof LivingEntity target) {
                    // Замедление от прицельного выстрела — фиксированные 3 с (механика 1.0)
                    target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1));
                }
            }
        }

        // Кровавое безумие: 20% входящего урона возвращается агрессору
        if (event.getEntity() instanceof Player victim
                && has(victim.getUniqueId(), EffectType.BLOOD_FURY)
                && event.getDamager() instanceof LivingEntity aggressor) {
            aggressor.damage(event.getDamage() * 0.2, victim);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        clearPlayer(event.getPlayer().getUniqueId());
    }

    /* ------------------------- служебное ------------------------- */

    private Player resolveAttacker(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile
                && projectile.getShooter() instanceof Player shooter) {
            return shooter;
        }
        return null;
    }

    private void breakStealth(Player player) {
        remove(player.getUniqueId(), EffectType.STEALTH);
        player.removePotionEffect(PotionEffectType.INVISIBILITY);
    }

    private double maxHealth(LivingEntity entity) {
        AttributeInstance attribute = entity.getAttribute(Attribute.MAX_HEALTH);
        return attribute != null ? attribute.getValue() : 20.0;
    }
}
