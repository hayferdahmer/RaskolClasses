// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.passive;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.attribute.AttributeMath;
import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.combat.CombatService;
import dev.raskol.classes.combat.DamageProfile;
import dev.raskol.classes.config.RaskolConfig;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Пассивки классов (1.6.x) + бонусы талантов (1.9.0).
 *  - execute_passive (воин), predator (охотник), poisoned_blades/sadism (разбойник),
 *    grace (жрец) — как ранее.
 *  - 1.11.2: black_mass (чернокнижник):
 *      • лифстил 6.66% ТОЛЬКО с урона способностей (маркер CombatService.abilitySourceMark);
 *      • рефлект 6.66% полученного урона чистым уроном по всем в радиусе 8
 *        (союзники-игроки защищены фракционным гейтом), внутренний КД 1 с;
 *      • S4: recoil/overflow/плата «Чёрного Слова» идут через прямой setHealth
 *        без EntityDamageByEntityEvent → рефлект на них не срабатывает;
 *        рефлект-урон помечен CombatService.beginReflect() и не цепляет
 *        лифстил/рефлект/откат по цепочке.
 *  - 1.11.2-fix: рефлект-урон использует new DamageProfile(0,0,refl) вместо
 *    несуществующего DamageProfile.trueDamage(double).
 */
public final class PassiveListener implements Listener {

    /** Маркер хилера: PriestAbilities ставит перед heal(), ResourceService читает. */
    private static volatile UUID healerMark = null;

    public static void markHealer(UUID priestUuid) {
        healerMark = priestUuid;
    }

    /** Одноразовое чтение маркера (null если не стоял). */
    public static UUID pollHealerMark() {
        UUID v = healerMark;
        healerMark = null;
        return v;
    }

    private final RaskolClasses plugin;
    private final Map<UUID, Map<String, Long>> lastProc = new ConcurrentHashMap<>();

    public PassiveListener(RaskolClasses plugin) {
        this.plugin = plugin;
    }

    private double cfgD(PlayerClass pc, String id, String key, double def) {
        return plugin.getRaskolConfig().passiveDouble(pc, id, key, def);
    }

    private int cfgI(PlayerClass pc, String id, String key, int def) {
        return plugin.getRaskolConfig().passiveInt(pc, id, key, def);
    }

    private boolean enabled(PlayerClass pc, String id) {
        return plugin.getRaskolConfig().passiveEnabled(pc, id);
    }

    private boolean procCdOk(UUID uuid, String id, int cdSeconds) {
        long now = System.currentTimeMillis();
        Map<String, Long> map = lastProc.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>());
        Long prev = map.get(id);
        if (prev != null && now - prev < cdSeconds * 1000L) {
            return false;
        }
        map.put(id, now);
        return true;
    }

    public void clear(UUID uuid) {
        lastProc.remove(uuid);
    }

    /* --------------------------- исходящий урон --------------------------- */

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        Player attacker = resolvePlayer(event.getDamager());
        if (attacker == null || !(event.getEntity() instanceof LivingEntity target)) {
            return;
        }
        PlayerClass pc = plugin.getClassProvider().getClassOf(attacker);
        if (pc == null) {
            return;
        }
        UUID uuid = attacker.getUniqueId();
        double damage = event.getDamage();

        switch (pc) {
            case WARRIOR -> {
                if (enabled(pc, "execute_passive")) {
                    double threshold = cfgD(pc, "execute_passive", "threshold", 0.20);
                    double frac = hpFraction(target);
                    if (frac <= threshold) {
                        double chance = cfgD(pc, "execute_passive", "chance", 0.20)
                                + plugin.getTalentService().procBonus(uuid, "execute_passive");
                        int cd = cfgI(pc, "execute_passive", "cooldown-seconds", 6);
                        if (roll(chance) && procCdOk(uuid, "execute_passive", cd)) {
                            double mult = cfgD(pc, "execute_passive", "multiplier", 3.0);
                            event.setDamage(damage * mult);
                            plugin.getFx().procByKey(attacker, "⚡ Казнь ×3!", "execute_passive");
                        }
                    }
                }
            }
            case HUNTER -> {
                if (enabled(pc, "predator")) {
                    double threshold = cfgD(pc, "predator", "threshold", 0.80);
                    if (hpFraction(attacker) >= threshold) {
                        double mult = cfgD(pc, "predator", "multiplier", 1.20);
                        event.setDamage(damage * mult);
                        plugin.getFx().procByKey(attacker, "🐺 Хищник!", "predator");
                    }
                }
            }
            case ROGUE -> {
                if (enabled(pc, "poisoned_blades")) {
                    double chance = cfgD(pc, "poisoned_blades", "chance", 0.30)
                            + plugin.getTalentService().procBonus(uuid, "poisoned_blades");
                    int cd = cfgI(pc, "poisoned_blades", "cooldown-seconds", 3);
                    int dur = cfgI(pc, "poisoned_blades", "duration-seconds", 2);
                    if (roll(chance) && procCdOk(uuid, "poisoned_blades", cd)) {
                        target.addPotionEffect(new PotionEffect(PotionEffectType.POISON, dur * 20, 0));
                        plugin.getFx().procByKey(attacker, "☠ Яд!", "poisoned_blades");
                    }
                }
                if (enabled(pc, "sadism") && isBehind(target, attacker)) {
                    double bonus = cfgD(pc, "sadism", "bonus", 3.0)
                            + plugin.getTalentService().procBonus(uuid, "sadism");
                    int cd = cfgI(pc, "sadism", "cooldown-seconds", 2);
                    if (procCdOk(uuid, "sadism", cd)) {
                        event.setDamage(damage + bonus);
                        plugin.getFx().procByKey(attacker, "🗡 В спину!", "sadism");
                    }
                }
            }
            case WARLOCK -> {
                // 1.11.2: лифстил 6.66% только с урона способностей, не с авто-атак
                if (enabled(pc, "black_mass")
                        && !CombatService.reflectSuppressed()
                        && uuid.equals(CombatService.abilitySourceMark())) {
                    double ls = damage * cfgD(pc, "black_mass", "lifesteal", 0.0666);
                    double scale = plugin.getAttributes().scale(attacker);
                    if (scale > 0.0 && ls > 0.0) {
                        plugin.getHpBarService().heal(attacker, ls / scale);
                        plugin.getFx().procByKey(attacker, "☾ Чёрная Месса", "black_mass");
                    }
                }
            }
            default -> {
                // PRIEST, MAGE: исходящих проков урона нет
            }
        }

        // 1.11.2: рефлект Чёрной Мессы — 6.66% полученного урона чистым по всем в радиусе
        if (event.getEntity() instanceof Player victim
                && plugin.getClassProvider().getClassOf(victim) == PlayerClass.WARLOCK
                && enabled(PlayerClass.WARLOCK, "black_mass")
                && !CombatService.reflectSuppressed()
                && event.getDamager() instanceof LivingEntity) {
            UUID vid = victim.getUniqueId();
            int cd = cfgI(PlayerClass.WARLOCK, "black_mass", "cooldown-seconds", 1);
            if (procCdOk(vid, "black_mass_reflect", cd)) {
                double reflCarrier = event.getDamage()
                        * cfgD(PlayerClass.WARLOCK, "black_mass", "reflect", 0.0666);
                double radius = cfgD(PlayerClass.WARLOCK, "black_mass", "reflect-radius", 8.0);
                double scaleV = plugin.getAttributes().scale(victim);
                double reflFormula = scaleV > 0.0 ? reflCarrier / scaleV : reflCarrier;
                if (reflFormula > 0.0) {
                    CombatService.beginReflect();
                    try {
                        for (Entity e : victim.getWorld().getNearbyEntities(
                                victim.getLocation(), radius, radius, radius)) {
                            if (!(e instanceof LivingEntity t) || t.equals(victim) || t.isDead()) {
                                continue;
                            }
                            if (!plugin.getCombat().canHit(victim, t)) {
                                continue; // союзники-игроки не страдают
                            }
                            plugin.getCombat().dealDamage(t, victim,
                                    new DamageProfile(0.0, 0.0, reflFormula));
                        }
                    } finally {
                        CombatService.endReflect();
                    }
                    plugin.getFx().procByKey(victim, "☾ Чёрная Месса", "black_mass");
                }
            }
        }
    }

    /* --------------------------- исходящее лечение --------------------------- */

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onRegainHealth(EntityRegainHealthEvent event) {
        UUID healer = healerMark; // читаем без poll: poll делает ResourceService
        if (healer == null) {
            return;
        }
        Player priest = plugin.getServer().getPlayer(healer);
        if (priest == null) {
            return;
        }
        PlayerClass pc = plugin.getClassProvider().getClassOf(priest);
        if (pc != PlayerClass.PRIEST || !enabled(pc, "grace")) {
            return;
        }
        double mult = cfgD(pc, "grace", "multiplier", 1.15)
                + plugin.getTalentService().procBonus(healer, "grace");
        event.setAmount(event.getAmount() * mult);
        plugin.getFx().procByKey(priest, "✚ Благодать", "grace");
    }

    /* ------------------------------- утилиты ------------------------------- */

    private boolean roll(double chance) {
        return Math.random() < chance;
    }

    private double hpFraction(LivingEntity e) {
        AttributeInstance attr = e.getAttribute(Attribute.MAX_HEALTH);
        double max = attr != null ? attr.getValue() : 20.0;
        return max > 0 ? e.getHealth() / max : 1.0;
    }

    private boolean isBehind(LivingEntity target, Player attacker) {
        Vector facing = target.getLocation().getDirection();
        Vector toAtt = attacker.getLocation().toVector().subtract(target.getLocation().toVector());
        double angle = AttributeMath.angleToAttacker(
                facing.getX(), facing.getZ(), toAtt.getX(), toAtt.getZ());
        return AttributeMath.isBack(angle,
                plugin.getConfig().getDouble("avoidance.back-angle", 135.0));
    }

    private Player resolvePlayer(Entity damager) {
        if (damager instanceof Player p) {
            return p;
        }
        if (damager instanceof Projectile proj && proj.getShooter() instanceof Player p) {
            return p;
        }
        return null;
    }
}
