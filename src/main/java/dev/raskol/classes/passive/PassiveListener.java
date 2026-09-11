// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.passive;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.attribute.AttributeMath;
import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.config.RaskolConfig;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
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
 *  - execute_passive (воин): шанс ×3 урона по цели ≤20% HP, КД 6 с;
 *  - predator (охотник): ×1.2 урона, пока HP атакующего ≥80%;
 *  - poisoned_blades (разбойник): шанс Яд I 2 с, КД 3 с;
 *  - sadism (разбойник): +N урона при атаке со спины, КД 2 с;
 *  - grace (жрец): ×M к исходящему лечению (через маркер хилера).
 * Шансы/бонусы читаются из конфига ПЛЮС TalentService.procBonus (узлы «proc»).
 * Визуал проков — FxService.procByKey (теги vfx.proc.*).
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
                // Казнь: шанс ×3 по цели ≤ порога HP
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
                // Хищник: ×1.2, пока HP атакующего ≥ порога
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
                // Отравленные клинки: шанс Яд I
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
                // Садизм: +N урона при атаке со спины
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
            default -> {
                // PRIEST, MAGE: исходящих проков урона нет
            }
        }
    }

    /* --------------------------- исходящее лечение --------------------------- */

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onRegainHealth(EntityRegainHealthEvent event) {
        // Благодать: ×M к лечению, исходящему от жреца (маркер ставит PriestAbilities)
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

    /** Атакующий за спиной цели: угол между направлением цели и вектором на атакующего ≥ back-angle. */
    private boolean isBehind(LivingEntity target, Player attacker) {
        Vector facing = target.getLocation().getDirection();
        Vector toAtt = attacker.getLocation().toVector().subtract(target.getLocation().toVector());
        double angle = AttributeMath.angleToAttacker(
                facing.getX(), facing.getZ(), toAtt.getX(), toAtt.getZ());
        return AttributeMath.isBack(angle,
                plugin.getConfig().getDouble("avoidance.back-angle", 135.0));
    }

    private Player resolvePlayer(org.bukkit.entity.Entity damager) {
        if (damager instanceof Player p) {
            return p;
        }
        if (damager instanceof Projectile proj && proj.getShooter() instanceof Player p) {
            return p;
        }
        return null;
    }
}
