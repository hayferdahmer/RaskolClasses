// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.combat.dot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

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
import org.bukkit.scheduler.BukkitTask;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.ability.WarlockAbilities;
import dev.raskol.classes.api.combat.CombatProfile;
import dev.raskol.classes.combat.CombatMath;
import dev.raskol.classes.combat.CombatService;
import dev.raskol.classes.combat.DamageType;
import dev.raskol.classes.combat.school.Penetration;
import dev.raskol.classes.combat.school.School;
import dev.raskol.classes.combat.school.SchoolConfig;
import dev.raskol.classes.combat.school.SchoolMitigation;
import dev.raskol.classes.spec.service.Spec2Service;

/**
 * 1.12.4–1.12.6: DoT-ядро (реестр, тик 1 с, атрибуция, стеки, кап DPS, триггеры среды).
 * 1.14.0 (Б2/Б7): spec2-тюнинг источника; wither + партиклы всех школ.
 * 1.14.7 (P0-6A): applyByIdWithDpsBonus.
 * 1.14.13 (Волна 13B-b): конструктор без побочных эффектов.
 * 1.14.14 (фаза 1): vacuous instanceof убран.
 * 1.15.5 (Волна 3, аудит 2.6-30): tick() через чистую функцию computeTicks.
 * 1.15.6 (Волна 4, аудит 2.6-2): setNoDamageTicks(0) перед damage.
 * 1.15.6 (Волна 7, аудит 2.6-38): DamageSource по каналу школы. Раньше тик всегда
 *         бил target.damage(amount, owner) → причина ENTITY_ATTACK → PHYSICAL:
 *         магические DoT (burning/poison/wither/chilled) могли уклоняться/парироваться,
 *         парирование накладывало stagger на владельца DoT. Теперь MAGIC/TRUE-школы
 *         идут через magic-DamageSource (cause MAGIC), PHYSICAL — по-прежнему melee.
 * 1.15.8 (A-06-003): DoT учитывает CombatProfile внешних мобов: иммунитеты,
 *         сопротивления канала/школы, пробитие владельца и уязвимости профиля.
 */
public final class DotService implements Listener {

    private static final long TICK_MILLIS = 1000L;

    private static volatile org.bukkit.damage.DamageType magicTypeCache;

    private final RaskolClasses plugin;
    private final CombatService combat;
    private final NamespacedKey dotKey;
    private final Map<UUID, CopyOnWriteArrayList<DotInstance>> dots = new ConcurrentHashMap<>();

    public DotService(RaskolClasses plugin, CombatService combat) {
        this.plugin = plugin;
        this.combat = combat;
        this.dotKey = new NamespacedKey(plugin, "rc_dot");
    }

    /** 1.15.6 (2.6-38): magic-DamageType из реестра (кэш, лениво). */
    private static org.bukkit.damage.DamageType magicType() {
        org.bukkit.damage.DamageType local = magicTypeCache;
        if (local == null) {
            local = io.papermc.paper.registry.RegistryAccess.registryAccess()
                    .getRegistry(io.papermc.paper.registry.RegistryKey.DAMAGE_TYPE)
                    .get(NamespacedKey.minecraft("magic"));
            magicTypeCache = local;
        }
        return local;
    }

    public void registerListeners() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public BukkitTask startTickTask() {
        return plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    /* ------------------------------ реестр определений ------------------------------ */

    private static School defaultSchool(String id) {
        return switch (id) {
            case "burning", "burning_passive" -> School.FIRE;
            case "poison", "poison_passive" -> School.NATURE;
            case "bleed", "bleed_passive" -> School.PHYSICAL;
            case "chilled" -> School.FROST;
            case "wither" -> School.SHADOW;
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
            case "wither" -> 2.5;
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
            case "wither" -> 6;
            default -> 3;
        };
    }

    private static int defaultStacks(String id) {
        return switch (id) {
            case "burning", "poison" -> 3;
            case "bleed", "chilled" -> 2;
            case "wither" -> 3;
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

    public boolean applyByIdWithDpsBonus(Player owner, LivingEntity target, String dotId, double dpsBonus) {
        DotDef def = defById(dotId);
        if (def == null) {
            return false;
        }
        if (Double.isFinite(dpsBonus) && dpsBonus > 0.0) {
            def = DotDef.of(
                    def.id(),
                    def.school(),
                    def.dps() + dpsBonus,
                    def.durationMillis(),
                    def.maxStacks(),
                    def.sourceAbility());
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
        def = specTuned(owner, def);
        UUID targetUuid = target.getUniqueId();
        long now = System.currentTimeMillis();
        CopyOnWriteArrayList<DotInstance> list = dots.computeIfAbsent(targetUuid, k -> new CopyOnWriteArrayList<>());
        for (DotInstance inst : list) {
            if (inst.ownerUuid().equals(owner.getUniqueId()) && inst.def().id().equals(def.id())) {
                inst.refresh(now);
                return;
            }
        }
        list.add(new DotInstance(def, owner.getUniqueId(), now));
    }

    private DotDef specTuned(Player owner, DotDef def) {
        Spec2Service spec2 = plugin.getSpec2Service();
        if (spec2 == null) {
            return def;
        }
        UUID uuid = owner.getUniqueId();
        double durSec = spec2.dotDurBonus(uuid, def.id());
        double stacks = spec2.dotStacksBonus(uuid, def.id());
        double multPct = spec2.dotMultBonus(uuid, def.id());
        if (durSec == 0.0 && stacks == 0.0 && multPct == 0.0) {
            return def;
        }
        long durationMillis = def.durationMillis() + (long) (durSec * 1000.0);
        int maxStacks = (int) Math.max(1, def.maxStacks() + stacks);
        double dps = def.dps() * (1.0 + multPct / 100.0);
        return DotDef.of(def.id(), def.school(), dps, durationMillis, maxStacks, def.sourceAbility());
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

    public List<DotInstance> activeDotsOf(UUID targetUuid) {
        CopyOnWriteArrayList<DotInstance> list = dots.get(targetUuid);
        return list == null ? Collections.emptyList() : List.copyOf(list);
    }

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
            return block == Material.WATER || block == Material.BUBBLE_COLUMN || block == Material.POWDER_SNOW;
        }
        if (school == School.FROST) {
            return block == Material.FIRE || block == Material.SOUL_FIRE || block == Material.LAVA;
        }
        return false;
    }

    /* ------------------------------ снарядные Dot'ы ------------------------------ */

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        Projectile proj = event.getEntity();
        if (proj == null) {
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

    private record TickOutcome(DotInstance inst, double damage) {}

    private List<TickOutcome> computeTicks(LivingEntity target, List<DotInstance> snapshot, long now) {
        List<TickOutcome> outcomes = new ArrayList<>(snapshot.size());
        for (DotInstance inst : snapshot) {
            if (inst.expired(now)) {
                outcomes.add(new TickOutcome(inst, 0.0));
                continue;
            }
            if (shouldExtinguish(
                    inst.def().school(), target.getLocation().getBlock().getType())) {
                puff(target, inst.def().school());
                outcomes.add(new TickOutcome(inst, 0.0));
                continue;
            }
            Player owner = plugin.getServer().getPlayer(inst.ownerUuid());
            if (owner == null || !combat.canHit(owner, target)) {
                outcomes.add(new TickOutcome(inst, 0.0));
                continue;
            }
            School school = inst.def().school();
            double immunity = combat.schoolImmunity().multiplierFor(target.getType(), school);
            if (immunity <= 0.0) {
                outcomes.add(new TickOutcome(inst, 0.0));
                continue;
            }

            double profileMultiplier = profileMultiplierFor(target, owner, school);
            if (profileMultiplier <= 0.0) {
                outcomes.add(new TickOutcome(inst, 0.0));
                continue;
            }

            double baseDps = inst.def().dps() * inst.stacks();
            double seal = WarlockAbilities.sealAmplifyOf(target.getUniqueId());
            double dps = DotMath.withMults(baseDps, combat.schoolConfig().multiplier(school), immunity, seal);
            dps *= profileMultiplier;
            dps *= 1.0 - mitigationFor(target, owner, school);
            outcomes.add(new TickOutcome(inst, dps));
        }
        return outcomes;
    }

    /**
     * Рассчитывает защиту внешнего моба по его CombatProfile.
     * Игроки продолжают использовать существующий расчёт mitigationFor().
     */
    private double profileMultiplierFor(LivingEntity target, Player owner, School school) {
        if (target instanceof Player) {
            return 1.0;
        }

        CombatProfile profile = plugin.getCombatProfiles().profileOf(target.getUniqueId());
        if (profile == null) {
            return 1.0;
        }

        double channelPenPct = 0.0;
        double schoolPenPct = 0.0;

        if (school.channel() != DamageType.TRUE) {
            boolean physical = school.channel() == DamageType.PHYSICAL;
            channelPenPct = combat.penTraits()
                            .channelPen(owner.getUniqueId(), physical, combat.schoolConfig().penPctCap())
                            .pct()
                    * 100.0;
            schoolPenPct = combat.penTraits()
                            .schoolPenFraction(owner.getUniqueId(), school, combat.schoolConfig().penPctCap())
                    * 100.0;
        }

        return DotCombatProfileMath.multiplier(profile, school, channelPenPct, schoolPenPct);
    }

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

            List<DotInstance> snapshot = List.copyOf(list);
            List<TickOutcome> outcomes = computeTicks(target, snapshot, now);

            for (TickOutcome outcome : outcomes) {
                if (outcome.damage() <= 0.0) {
                    list.remove(outcome.inst());
                }
            }
            if (list.isEmpty()) {
                dots.remove(targetUuid);
                continue;
            }

            double[] damages = new double[outcomes.size()];
            int validCount = 0;
            for (TickOutcome outcome : outcomes) {
                if (outcome.damage() > 0.0) {
                    damages[validCount++] = outcome.damage();
                }
            }
            if (validCount == 0) {
                continue;
            }

            double maxHp = combat.caps().formulaMaxOf(target);
            double capPct = plugin.getConfig().getDouble("combat.dot-dps-cap-pct", 6.0);
            double limit = DotMath.dpsLimit(maxHp, capPct);
            double[] pending = new double[validCount];
            System.arraycopy(damages, 0, pending, 0, validCount);
            double factor = DotMath.capFactor(pending, limit);
            double scale = combat.scaleOf(target);

            for (TickOutcome outcome : outcomes) {
                if (outcome.damage() <= 0.0) {
                    continue;
                }
                Player owner = plugin.getServer().getPlayer(outcome.inst().ownerUuid());
                if (owner == null) {
                    continue;
                }
                double amount = outcome.damage() * factor * (TICK_MILLIS / 1000.0) * scale;
                if (amount <= 0.0) {
                    continue;
                }
                CombatService.setSuppress(true);
                CombatService.beginReflect();
                try {
                    target.setNoDamageTicks(0);
                    // 1.15.6 (Волна 7, аудит 2.6-38): канал школы → тип события урона.
                    // MAGIC/TRUE идут через magic-DamageSource: путь A не считает их
                    // PHYSICAL (уклонение/парирование/stagger на маг-DoT исключены).
                    School channelSchool = outcome.inst().def().school();
                    if (channelSchool.channel() == DamageType.PHYSICAL) {
                        target.damage(amount, owner);
                    } else {
                        org.bukkit.damage.DamageType magic = magicType();
                        if (magic != null) {
                            target.damage(
                                    amount,
                                    org.bukkit.damage.DamageSource.builder(magic)
                                            .withDirectEntity(owner)
                                            .withCausingEntity(owner)
                                            .build());
                        } else {
                            target.damage(amount, owner);
                        }
                    }
                } finally {
                    CombatService.endReflect();
                    CombatService.setSuppress(false);
                }
                tickVfx(target, outcome.inst().def().school());
            }
            if (list.isEmpty()) {
                dots.remove(targetUuid);
            }
        }
    }

    private void tickVfx(LivingEntity target, School school) {
        Particle p = resolveParticle(
                plugin.getConfig().getString("vfx.dot." + school.id() + ".particle", null), defaultDotParticle(school));
        if (p != null) {
            target.getWorld().spawnParticle(p, target.getLocation().add(0.0, 1.0, 0.0), 2, 0.2, 0.3, 0.2, 0.01);
        }
    }

    private void puff(LivingEntity target, School school) {
        Particle p = school == School.FIRE ? Particle.SMOKE : Particle.CLOUD;
        target.getWorld().spawnParticle(p, target.getLocation().add(0.0, 1.0, 0.0), 6, 0.3, 0.4, 0.3, 0.01);
    }

    private Particle defaultDotParticle(School school) {
        return switch (school) {
            case FIRE -> Particle.FLAME;
            case FROST -> Particle.SNOWFLAKE;
            case NATURE -> Particle.COMPOSTER;
            case PHYSICAL -> Particle.DAMAGE_INDICATOR;
            case SHADOW -> Particle.SCULK_SOUL;
            case HOLY -> Particle.ENCHANT;
            case ARCANE -> Particle.REVERSE_PORTAL;
            case TRUE -> Particle.POOF;
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
        Penetration pen =
                combat.penTraits().channelPen(owner.getUniqueId(), channel == DamageType.PHYSICAL, sc.penPctCap());
        double effChannel = pen.effectiveResist(channelResistPct, sc.penPctCap());
        double elResist = combat.elemental().resistOf(targetUuid, school);
        double schoolPen = combat.penTraits().schoolPenFraction(owner.getUniqueId(), school, sc.penPctCap());
        double effEl = CombatMath.effectiveResist(elResist, 0.0, schoolPen, sc.penPctCap());
        return SchoolMitigation.mitigationFor(
                effChannel, Penetration.NONE, sc.penPctCap(), effEl, sc.elementalEnabled(), sc.mitigationCap());
    }
}
