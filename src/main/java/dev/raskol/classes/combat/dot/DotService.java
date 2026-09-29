// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.combat.dot;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.ability.WarlockAbilities;
import dev.raskol.classes.combat.CombatMath;
import dev.raskol.classes.combat.CombatService;
import dev.raskol.classes.combat.DamageType;
import dev.raskol.classes.combat.school.Penetration;
import dev.raskol.classes.combat.school.School;
import dev.raskol.classes.combat.school.SchoolConfig;
import dev.raskol.classes.combat.school.SchoolMitigation;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 1.12.4: DoT-ядро. Реестр живых Dot'ов по целям + тик-задача 1 с.
 * 1.12.5: реестр определений dots.*; снарядные Dot'ы; средовые триггеры; тик-VFX.
 * 1.12.5-fix: getHitEntity() → instanceof; puff-партикл = CLOUD.
 * 1.12.6: + публичный API для HUD: activeDotsOf() возвращает snapshot-список
 *         с доступом к def/stacks/expiresAt; remainingSeconds() для таймера.
 */
public final class DotService implements Listener {

    private static final long TICK_MILLIS = 1000L;

    private final RaskolClasses plugin;
    private final CombatService combat;
    private final NamespacedKey dotKey;
    private final Map<UUID, CopyOnWriteArrayList<DotInstance>> dots = new ConcurrentHashMap<>();

    public DotService(RaskolClasses plugin, CombatService combat) {
        this.plugin = plugin;
        this.combat = combat;
        this.dotKey = new NamespacedKey(plugin, "rc_dot");
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    /* ------------------------------ реестр определений ------------------------------ */

    private static School defaultSchool(String id) {
        return switch (id) {
            case "burning", "burning_passive" -> School.FIRE;
            case "poison", "poison_passive" -> School.NATURE;
            case "bleed", "bleed_passive" -> School.PHYSICAL;
            case "chilled" -> School.FROST;
            default -> School.ARCANE;
        };
    }

    private static double defaultDps(String id) {
        return switch (id) {
            case "burning" -> 4.0;
            case "burning_passive" -> 3.0;
            case "poison" -> 3.0;
            case "poison_passive" -> 2.5;
            case "bleed" -> 2.5;
            case "bleed_passive" -> 2.0;
            case "chilled" -> 2.0;
            default -> 1.0;
        };
    }

    private static int defaultDurationSeconds(String id) {
        return switch (id) {
            case "burning" -> 3;
            case "burning_passive" -> 2;
            case "poison" -> 5;
            case "poison_passive" -> 2;
            case "bleed" -> 4;
            case "bleed_passive" -> 3;
            case "chilled" -> 4;
            default -> 3;
        };
    }

    private static int defaultStacks(String id) {
        return switch (id) {
            case "burning", "poison" -> 3;
            case "bleed", "chilled" -> 2;
            case "burning_passive", "poison_passive", "bleed_passive" -> 1;
            default -> 1;
        };
    }

    public DotDef defById(String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        String base = "dots." + id + ".";
        School school = School.fromId(plugin.getConfig().getString(base + "school", ""));
        if (school == null) {
            school = defaultSchool(id);
        }
        double dps = plugin.getConfig().getDouble(base + "dps", defaultDps(id));
        if (!Double.isFinite(dps) || dps <= 0.0) {
            dps = defaultDps(id);
        }
        int dur = plugin.getConfig().getInt(base + "duration", defaultDurationSeconds(id));
        if (dur <= 0) {
            dur = defaultDurationSeconds(id);
        }
        int stacks = plugin.getConfig().getInt(base + "max-stacks", defaultStacks(id));
        if (stacks <= 0) {
            stacks = defaultStacks(id);
        }
        return DotDef.of(id, school, dps, dur * 1000L, stacks, id);
    }

    /* ------------------------------ публичный API ------------------------------ */

    public boolean applyById(Player owner, LivingEntity target, String dotId) {
        DotDef def = defById(dotId);
        if (def == null) {
            return false;
        }
        apply(owner, target, def);
        return true;
    }

    public void apply(Player owner, LivingEntity target, DotDef def) {
        if (owner == null || target == null || def == null || target.isDead()) {
            return;
        }
        if (!combat.canHit(owner, target)) {
            return;
        }
        UUID targetUuid = target.getUniqueId();
        long now = System.currentTimeMillis();
        CopyOnWriteArrayList<DotInstance> list =
                dots.computeIfAbsent(targetUuid, k -> new CopyOnWriteArrayList<>());
        for (DotInstance inst : list) {
            if (inst.ownerUuid().equals(owner.getUniqueId())
                    && inst.def().id().equals(def.id())) {
                inst.refresh(now);
                return;
            }
        }
        list.add(new DotInstance(def, owner.getUniqueId(), now));
    }

    public void removeAllOn(UUID targetUuid) {
        dots.remove(targetUuid);
    }

    public void removeSchoolOn(UUID targetUuid, School school) {
        CopyOnWriteArrayList<DotInstance> list = dots.get(targetUuid);
        if (list == null) {
            return;
        }
        list.removeIf(inst -> inst.def().school() == school);
        if (list.isEmpty()) {
            dots.remove(targetUuid);
        }
    }

    public int activeOn(UUID targetUuid) {
        CopyOnWriteArrayList<DotInstance> list = dots.get(targetUuid);
        return list == null ? 0 : list.size();
    }

    public int trackedTargets() {
        return dots.size();
    }

    /* ------------------------------ 1.12.6: HUD-API ------------------------------ */

    /**
     * Snapshot активных Dot'ов на цели. Пустой список, если цель не отслеживается.
     * HUD вызывает этот метод раз в тик для построения DoT-строки.
     */
    public List<DotInstance> activeDotsOf(UUID targetUuid) {
        CopyOnWriteArrayList<DotInstance> list = dots.get(targetUuid);
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }
        return new ArrayList<>(list);
    }

    /** Оставшиеся секунды до истечения Dot'а (ceil — всегда ≥1 на живом Dot). */
    public static long remainingSeconds(DotInstance inst, long nowMillis) {
        long left = inst.expiresAt() - nowMillis;
        if (left <= 0L) {
            return 0L;
        }
        return (left + 999L) / 1000L;
    }

    /* ------------------------------ средовые триггеры ------------------------------ */

    public static boolean shouldExtinguish(School school, Material block) {
        if (school == null || block == null) {
            return false;
        }
        if (school == School.FIRE) {
            return block == Material.WATER || block == Material.BUBBLE_COLUMN
                    || block == Material.POWDER_SNOW;
        }
        if (school == School.FROST) {
            return block == Material.FIRE || block == Material.SOUL_FIRE
                    || block == Material.LAVA;
        }
        return false;
    }

    /* ------------------------------ снарядные Dot'ы ------------------------------ */

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof Projectile proj)) {
            return;
        }
        if (!proj.getPersistentDataContainer().has(dotKey, PersistentDataType.STRING)) {
            return;
        }
        String dotId = proj.getPersistentDataContainer().get(dotKey, PersistentDataType.STRING);
        if (dotId == null || dotId.isEmpty()) {
            return;
        }
        if (!(proj.getShooter() instanceof Player owner)) {
            return;
        }
        Entity hitEntity = event.getHitEntity();
        if (!(hitEntity instanceof LivingEntity hit)) {
            return;
        }
        applyById(owner, hit, dotId);
    }

    public NamespacedKey dotTagKey() {
        return dotKey;
    }

    /* ------------------------------ тик ------------------------------ */

    private void tick() {
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, CopyOnWriteArrayList<DotInstance>> entry : dots.entrySet()) {
            UUID targetUuid = entry.getKey();
            CopyOnWriteArrayList<DotInstance> list = entry.getValue();

            Entity entity = plugin.getServer().getEntity(targetUuid);
            if (!(entity instanceof LivingEntity target) || target.isDead()) {
                dots.remove(targetUuid);
                continue;
            }

            int n = list.size();
            double[] pending = new double[n];
            int idx = 0;
            for (DotInstance inst : list) {
                if (inst.expired(now)) {
                    list.remove(inst);
                    pending[idx++] = 0.0;
                    continue;
                }
                if (shouldExtinguish(inst.def().school(),
                        target.getLocation().getBlock().getType())) {
                    list.remove(inst);
                    puff(target, inst.def().school());
                    pending[idx++] = 0.0;
                    continue;
                }
                Player owner = plugin.getServer().getPlayer(inst.ownerUuid());
                if (owner == null || !combat.canHit(owner, target)) {
                    list.remove(inst);
                    pending[idx++] = 0.0;
                    continue;
                }
                School school = inst.def().school();
                double immunity = combat.schoolImmunity().multiplierFor(target.getType(), school);
                if (immunity <= 0.0) {
                    list.remove(inst);
                    pending[idx++] = 0.0;
                    continue;
                }
                double baseDps = inst.def().dps() * inst.stacks();
                double seal = WarlockAbilities.sealAmplifyOf(targetUuid);
                double dps = DotMath.withMults(baseDps,
                        combat.schoolConfig().multiplier(school), immunity, seal);
                dps *= 1.0 - mitigationFor(target, owner, school);
                pending[idx] = dps;
                idx++;
            }
            if (list.isEmpty()) {
                dots.remove(targetUuid);
                continue;
            }

            double maxHp = combat.caps().formulaMaxOf(target);
            double capPct = plugin.getConfig().getDouble("combat.dot-dps-cap-pct", 6.0);
            double limit = DotMath.dpsLimit(maxHp, capPct);
            double factor = DotMath.capFactor(pending, limit);

            double scale = combat.scaleOf(target);
            idx = 0;
            for (DotInstance inst : list) {
                double dps = pending[idx++];
                if (dps <= 0.0) {
                    continue;
                }
                Player owner = plugin.getServer().getPlayer(inst.ownerUuid());
                if (owner == null) {
                    continue;
                }
                double amount = dps * factor * (TICK_MILLIS / 1000.0) * scale;
                if (amount <= 0.0) {
                    continue;
                }
                CombatService.setSuppress(true);
                CombatService.beginReflect();
                try {
                    target.damage(amount, owner);
                } finally {
                    CombatService.endReflect();
                    CombatService.setSuppress(false);
                }
                tickVfx(target, inst.def().school());
            }
            if (list.isEmpty()) {
                dots.remove(targetUuid);
            }
        }
    }

    private void tickVfx(LivingEntity target, School school) {
        Particle p = resolveParticle(plugin.getConfig().getString(
                "vfx.dot." + school.id() + ".particle", null), defaultDotParticle(school));
        if (p != null) {
            target.getWorld().spawnParticle(p, target.getLocation().add(0.0, 1.0, 0.0),
                    2, 0.2, 0.3, 0.2, 0.01);
        }
    }

    private void puff(LivingEntity target, School school) {
        Particle p = school == School.FIRE ? Particle.SMOKE : Particle.CLOUD;
        target.getWorld().spawnParticle(p, target.getLocation().add(0.0, 1.0, 0.0),
                6, 0.3, 0.4, 0.3, 0.01);
    }

    private Particle defaultDotParticle(School school) {
        return switch (school) {
            case FIRE -> Particle.FLAME;
            case FROST -> Particle.SNOWFLAKE;
            case NATURE -> Particle.COMPOSTER;
            case PHYSICAL -> Particle.DAMAGE_INDICATOR;
            default -> null;
        };
    }

    private Particle resolveParticle(String name, Particle fallback) {
        if (name == null || name.isEmpty()) {
            return fallback;
        }
        try {
            return Particle.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }

    private double mitigationFor(LivingEntity target, Player owner, School school) {
        SchoolConfig sc = combat.schoolConfig();
        if (!(target instanceof Player tp) || combat.resists().disabledIn(tp.getWorld())) {
            return 0.0;
        }
        UUID targetUuid = tp.getUniqueId();
        DamageType channel = school.channel();
        if (channel == DamageType.TRUE) {
            return 0.0;
        }
        double cap = combat.resists().pvpCap();
        double factor = channel == DamageType.PHYSICAL
                ? combat.resists().physicalFactor(targetUuid, cap)
                : combat.resists().magicFactor(targetUuid, cap);
        if (!Double.isFinite(factor) || factor >= 1.0 || factor < 0.0) {
            factor = 1.0;
        }
        double channelResistPct = (1.0 - factor) * 100.0;
        Penetration pen = combat.penTraits().channelPen(
                owner.getUniqueId(), channel == DamageType.PHYSICAL,
                plugin.getGearHook(), sc.penPctCap());
        double effChannel = pen.effectiveResist(channelResistPct, sc.penPctCap());
        double elResist = combat.elemental().resistOf(targetUuid, school);
        double schoolPen = combat.penTraits().schoolPenFraction(
                owner.getUniqueId(), school, plugin.getGearHook(), sc.penPctCap());
        double effEl = CombatMath.effectiveResist(elResist, 0.0, schoolPen, sc.penPctCap());
        return SchoolMitigation.mitigationFor(effChannel, Penetration.NONE,
                sc.penPctCap(), effEl, sc.elementalEnabled(), sc.mitigationCap());
    }
}
