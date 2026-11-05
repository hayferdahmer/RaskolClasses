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
 *
 * 1.15.6 (Волна 4, аудит 2.6-3): РАЗДЕЛЕНИЕ ПРИОРИТЕТОВ.
 * Раньше все боевые обработчики сидели на HIGH — ПОСЛЕ VanillaDamageListener
 * (NORMAL), который применяет резисты и капы: множители STEALTH ×1.5,
 * AIMED_SHOT ×2 и SHIELD_WALL ×0.2 обходили combat.max-single-hit-pct и
 * попадали в burst-окно уже уменьшенными/увеличенными (связка с 2.6-40).
 * Новая схема:
 *  - LOW  (onIncomingEarly / onOutgoingEarly): отмены и МНОЖИТЕЛИ — до капов пути A;
 *  - HIGH (onIncomingLate / onOutgoingLate): потребления флагов и сайд-эффекты
 *    (срыв скрытности, расход AIMED_SHOT, замедление, BLOOD_FURY) — только после
 *    подтверждения попадания (ignoreCancelled=true: отменённый удар не тратит флаги).
 * 2.6-4 (BLOOD_FURY в обход canHit/капов) осознанно отложен в Волну 5: требует
 * инъекцию CombatService и рекурсион-гард, делается вместе с MitigationEngine.
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

    /**
     * 1.15.6 (2.6-3): ВХОДЯЩИЕ отмены/снижения ДО капов пути A (LOW).
     * Стальная кожа уменьшает урон до того, как VanillaDamageListener посчитает
     * single-hit/burst-капы — burst-окно видит финальную цифру.
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onIncomingEarly(EntityDamageEvent event) {
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
    }

    /**
     * 1.15.6 (2.6-3): срыв скрытности полученным уроном — ПОСЛЕ подтверждения
     * попадания (HIGH + ignoreCancelled): уклонённый/отменённый удар не срывает стелс.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onIncomingLate(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (has(player.getUniqueId(), EffectType.STEALTH)) {
            breakStealth(player);
        }
    }

    /**
     * 1.15.6 (2.6-3): ИСХОДЯЩИЕ множители ДО капов пути A (LOW).
     * Бонус первого удара из скрытности и прицельный выстрел умножают базу,
     * которую затем ограничивают combat.max-single-hit-pct и burst-окно.
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onOutgoingEarly(EntityDamageByEntityEvent event) {
        Player attacker = resolveAttacker(event.getDamager());
        if (attacker == null) {
            return;
        }
        UUID id = attacker.getUniqueId();
        if (has(id, EffectType.STEALTH)) {
            event.setDamage(event.getDamage() * 1.5); // бонус первого удара
        }
        if (has(id, EffectType.AIMED_SHOT) && event.getDamager() instanceof AbstractArrow) {
            event.setDamage(event.getDamage() * 2.0);
        }
    }

    /**
     * 1.15.6 (2.6-3): потребления флагов и сайд-эффекты ПОСЛЕ подтверждения
     * попадания (HIGH + ignoreCancelled): отменённый удар (альянс-гейт, регион,
     * уклонение) НЕ тратит скрытность и AIMED_SHOT.
     * 2.6-4: BLOOD_FURY пока прямой damage() — маршрутизация через dealDamage
     * отложена в Волну 5 (нужны canHit/капы/рекурсион-гард).
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onOutgoingLate(EntityDamageByEntityEvent event) {
        Player attacker = resolveAttacker(event.getDamager());
        if (attacker != null) {
            UUID id = attacker.getUniqueId();

            if (has(id, EffectType.STEALTH)) {
                breakStealth(attacker);
            }

            if (has(id, EffectType.AIMED_SHOT) && event.getDamager() instanceof AbstractArrow) {
                remove(id, EffectType.AIMED_SHOT);
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
