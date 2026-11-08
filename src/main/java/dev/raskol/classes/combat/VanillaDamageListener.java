// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.combat;

import java.util.Locale;
import java.util.UUID;

import org.bukkit.GameMode;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.ability.WarlockAbilities;
import dev.raskol.classes.api.combat.CombatProfile;
import dev.raskol.classes.attribute.PowerService;
import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.combat.school.Penetration;
import dev.raskol.classes.combat.school.School;
import dev.raskol.classes.combat.school.SchoolConfig;
import dev.raskol.classes.combat.school.SchoolImmunity;
import dev.raskol.classes.combat.school.SchoolMitigation;
import dev.raskol.classes.config.ConfigReads;
import dev.raskol.classes.item.model.ItemDefinition;
import dev.raskol.classes.item.model.WeaponType;

/**
 * 1.11.4 (P3): ПУТЬ A — ванильные события урона.
 * 1.12.1–1.13.0: школы, иммунитеты, pen+elemental, breaksOnDamage.
 * 1.14.3 (3C1/3C2): ProcService-интеграция, stealth/crit-множители, reflect.
 * 1.14.10 (7.2B/7.3): critFeedback единая точка; cfgD → ConfigReads.
 * 1.14.14 (фаза 2): epsilon-сравнения.
 * 1.15.2: WeaponMastery для PHYSICAL.
 * 1.15.5 (Волна 3, 2.6-32/34): школа из currentCastSchool / schoolOf(cause).
 * 1.15.6 (Волна 4, 2.6-40): burst allowed==0 → отмена; MONITOR-откат burst-лога.
 * 1.15.6 (Волна 7, 2.6-38): suppressed-события (DoT-тики и удары пути B) выходят
 *         ДО блока уклонения: магические DoT больше не уклоняются/парируются,
 *         парирование не накладывает stagger на владельца DoT.
 */
public final class VanillaDamageListener implements Listener {

    private final RaskolClasses plugin;
    private final CombatService combat;
    private final ResistService resists;
    private final AvoidanceService avoidance;
    private final PowerService powers;
    private final DamageCaps caps;
    private final SchoolConfig schoolConfig;
    private final SchoolImmunity immunity;

    /** Состояние отката burst-лога хранится отдельно для каждого события урона. */
    private final BurstRollbackTracker burstRollbacks = new BurstRollbackTracker();

    public VanillaDamageListener(
            RaskolClasses plugin,
            CombatService combat,
            ResistService resists,
            AvoidanceService avoidance,
            PowerService powers,
            DamageCaps caps) {
        this.plugin = plugin;
        this.combat = combat;
        this.resists = resists;
        this.avoidance = avoidance;
        this.powers = powers;
        this.caps = caps;
        this.schoolConfig = new SchoolConfig(plugin);
        this.immunity = new SchoolImmunity(plugin);
    }

    private double cfgD(String path, double def) {
        return ConfigReads.finiteDouble(plugin.getConfig(), path, def);
    }

    /* ------------------------------ путь A ------------------------------ */

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        boolean suppressed = CombatService.consumeSuppress();
        CombatProfile targetProfile = event.getEntity() instanceof LivingEntity living
                ? plugin.getCombatProfiles().profileOf(living.getUniqueId())
                : null;
        CombatProfile sourceProfile = event instanceof EntityDamageByEntityEvent by ? profileOfSource(by) : null;

        Player attacker = null;
        if (event instanceof EntityDamageByEntityEvent by) {
            attacker = resolveAttacker(by);
        }

        if (!suppressed
                && attacker != null
                && event.getEntity() instanceof Player victimTarget
                && !attacker.getUniqueId().equals(victimTarget.getUniqueId())
                && !plugin.getConfig().getBoolean("combat.friendly-fire", false)
                && Targeting.isAlly(plugin, attacker.getUniqueId(), victimTarget.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        if (!suppressed) {
            applyOutgoingOffense(event);
            double amp = WarlockAbilities.sealAmplifyOf(event.getEntity().getUniqueId());
            if (amp > 0.0 && event.getDamage() > 0.0) {
                event.setDamage(event.getDamage() * (1.0 + amp));
            }
        }

        DamageType incomingType = effectiveType(event);
        School castSchool = CombatService.currentCastSchool();
        School school;
        if (castSchool != null) {
            school = castSchool;
        } else if (incomingType == DamageType.PHYSICAL) {
            school = School.PHYSICAL;
        } else if (incomingType == DamageType.TRUE) {
            school = School.TRUE;
        } else {
            school = schoolConfig.schoolOf(event.getCause());
        }

        if (!suppressed && event.getEntity() instanceof LivingEntity ent) {
            double mult = immunity.multiplierFor(ent.getType(), school) * schoolConfig.multiplier(school);
            if (targetProfile != null) {
                if (targetProfile.immunities().contains(school)) {
                    event.setCancelled(true);
                    return;
                }
                mult *= 1.0 + Math.max(-100.0, targetProfile.vulnerabilities().getOrDefault(school, 0.0)) / 100.0;
            }
            if (mult <= 0.0) {
                event.setCancelled(true);
                return;
            }
            if (mult != 1.0 && event.getDamage() > 0.0) {
                event.setDamage(event.getDamage() * mult);
            }
        }

        if (!(event.getEntity() instanceof Player target)) {
            if (!suppressed && targetProfile != null && event.getDamage() > 0.0 && incomingType != DamageType.TRUE) {
                double channel =
                        incomingType == DamageType.PHYSICAL ? targetProfile.physResist() : targetProfile.magicResist();
                double schoolResist = targetProfile.schoolResists().getOrDefault(school, 0.0);
                double pen = sourceProfile != null
                        ? (incomingType == DamageType.PHYSICAL
                                ? sourceProfile.penPhysPct()
                                : sourceProfile.penMagicPct())
                        : attacker != null
                                ? combat.penTraits()
                                                .channelPen(
                                                        attacker.getUniqueId(),
                                                        incomingType == DamageType.PHYSICAL,
                                                        0.40)
                                                .pct()
                                        * 100.0
                                : 0.0;
                double schoolPen = sourceProfile != null
                        ? sourceProfile.penSchoolPct().getOrDefault(school, 0.0)
                        : attacker != null
                                ? combat.penTraits().schoolPenFraction(attacker.getUniqueId(), school, 0.40) * 100.0
                                : 0.0;
                double resist = Math.max(0, Math.min(90, channel - pen + schoolResist - schoolPen));
                event.setDamage(event.getDamage() * (1.0 - resist / 100.0));
            }
            if (!suppressed
                    && targetProfile != null
                    && event.getDamage() > 0.0
                    && event.getCause() != EntityDamageEvent.DamageCause.CUSTOM
                    && event instanceof EntityDamageByEntityEvent by)
                reflect(by, event.getDamage(), targetProfile.reflect());
            if (!suppressed && event.getEntity() instanceof LivingEntity mob && event.getDamage() > 0.0) {
                hookBreaksCarrier(mob, event.getDamage());
            }
            return;
        }
        if (target.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        if (resists.disabledIn(target.getWorld())) {
            if (!suppressed && event.getDamage() > 0.0) {
                hookBreaksCarrier(target, event.getDamage());
            }
            return;
        }
        DamageType type = incomingType;
        if (type == DamageType.TRUE) {
            if (!suppressed) {
                caps.applyEnvLethalScale(event, target);
                hookBreaksCarrier(target, event.getDamage());
            }
            return;
        }

        // 1.15.6 (Волна 7, аудит 2.6-38): suppressed-события (DoT/путь B) НЕ проходят
        // уклонение/парирование: маг-DoT не dodge'ится, парирование не вешает stagger
        // на владельца DoT. Выход ДО блока avoidance.
        if (suppressed) {
            return;
        }

        // 1.14.3 (3C1 + фикс #1301): avoidance с различением dodge/parry
        if (type == DamageType.PHYSICAL) {
            ProcService.AvoidResult avoidResult = avoidance.tryAvoid(target, event);
            if (avoidResult != ProcService.AvoidResult.NONE) {
                event.setCancelled(true);
                if (attacker != null) {
                    if (avoidResult == ProcService.AvoidResult.PARRY && holdsShield(target)) {
                        combat.procs().onDefenderBlock(target, attacker);
                    } else {
                        combat.procs().onDefenderAvoid(target, attacker, avoidResult);
                    }
                }
                return;
            }
        }

        double cap = isPvp(event) ? resists.pvpCap() : resists.cap();
        UUID uuid = target.getUniqueId();
        double factor =
                type == DamageType.PHYSICAL ? resists.physicalFactor(uuid, cap) : resists.magicFactor(uuid, cap);

        double defenseMult = 1.0;
        if (Double.isFinite(factor) && factor >= 0.0 && factor < 1.0) {
            double channelResistPct = (1.0 - factor) * 100.0;
            double mitigation;
            if ((attacker != null || sourceProfile != null) && schoolConfig.enabled()) {
                UUID aUuid = attacker == null ? null : attacker.getUniqueId();
                Penetration pen = sourceProfile != null
                        ? Penetration.of(
                                0.0,
                                dev.raskol.classes.combat.school.PenTraitsService.clampSumPercent(
                                        type == DamageType.PHYSICAL
                                                ? sourceProfile.penPhysPct()
                                                : sourceProfile.penMagicPct(),
                                        schoolConfig.penPctCap()))
                        : combat.penTraits().channelPen(aUuid, type == DamageType.PHYSICAL, schoolConfig.penPctCap());
                double effChannel = pen.effectiveResist(channelResistPct, schoolConfig.penPctCap());
                double elResist = combat.elemental().resistOf(uuid, school);
                double schoolPen = sourceProfile != null
                        ? dev.raskol.classes.combat.school.PenTraitsService.clampSumPercent(
                                sourceProfile.penSchoolPct().getOrDefault(school, 0.0), schoolConfig.penPctCap())
                        : combat.penTraits().schoolPenFraction(aUuid, school, schoolConfig.penPctCap());
                double effEl = CombatMath.effectiveResist(elResist, 0.0, schoolPen, schoolConfig.penPctCap());
                mitigation = SchoolMitigation.mitigationFor(
                        effChannel,
                        Penetration.NONE,
                        schoolConfig.penPctCap(),
                        effEl,
                        schoolConfig.elementalEnabled(),
                        schoolConfig.mitigationCap());
            } else {
                mitigation = channelResistPct / 100.0;
            }
            defenseMult = 1.0 - mitigation;
            if (defenseMult <= 0.0) {
                event.setCancelled(true);
                return;
            }
        }
        if (defenseMult < 1.0) {
            event.setDamage(event.getDamage() * defenseMult);
        }

        if (attacker != null) {
            double expose = combat.procs().readExposeMult(target);
            if (expose > 1.0) {
                event.setDamage(event.getDamage() * expose);
            }
        }

        caps.applySingleHitCapCarrier(event, target);
        double scale = caps.scaleOf(target);
        double effective = scale > 0.0 ? event.getDamage() / scale : event.getDamage();
        effective = caps.applyBurstCap(target, effective, caps.formulaMaxOf(target));
        if (effective <= 0.0) {
            event.setCancelled(true);
            return;
        }
        burstRollbacks.arm(event, uuid, effective);
        event.setDamage(effective * scale);

        if (event.getCause() != EntityDamageEvent.DamageCause.CUSTOM
                && event.getDamage() > 0.0
                && event instanceof EntityDamageByEntityEvent by) {
            double reflect = targetProfile != null
                    ? targetProfile.reflect()
                    : plugin.getItems().value(target.getUniqueId(), dev.raskol.classes.item.model.ItemStat.REFLECT);
            reflect(by, event.getDamage(), reflect);
        }

        hookBreaksCarrier(target, event.getDamage());

        if (event.getDamage() > 0.0) {
            double formulaDamage = scale > 0.0 ? event.getDamage() / scale : event.getDamage();
            combat.procs().onDefenderDamaged(target, formulaDamage);
            if (attacker != null) {
                combat.procs().applyExpose(attacker, target);
                if (type == DamageType.MAGIC) {
                    combat.procs().onDefenderDamagedByMagic(target, attacker, formulaDamage);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDamageMonitor(EntityDamageEvent event) {
        BurstRollbackTracker.Entry rollback = burstRollbacks.take(event);
        if (rollback == null || !event.isCancelled()) {
            return;
        }
        if (event.getEntity() instanceof Player target
                && target.getUniqueId().equals(rollback.playerId())) {
            caps.undoLastBurst(target, rollback.amount());
        }
    }

    private void reflect(EntityDamageByEntityEvent event, double damage, double percent) {
        Entity target = event.getEntity(), source = sourceEntity(event);
        if (source instanceof LivingEntity livingSource && livingSource != target && percent > 0 && damage > 0) {
            CombatService.beginReflect();
            try {
                combat.dealDamage(
                        livingSource,
                        (LivingEntity) target,
                        dev.raskol.classes.combat.DamageProfile.trueDmg(damage * percent / 100.0),
                        true,
                        true);
            } finally {
                CombatService.endReflect();
            }
        }
    }

    private static boolean holdsShield(Player player) {
        org.bukkit.inventory.ItemStack off = player.getInventory().getItemInOffHand();
        if (off != null && off.getType().name().endsWith("SHIELD")) {
            return true;
        }
        org.bukkit.inventory.ItemStack main = player.getInventory().getItemInMainHand();
        return main != null && main.getType().name().endsWith("SHIELD");
    }

    private void hookBreaksCarrier(LivingEntity victim, double carrierDamage) {
        if (carrierDamage <= 0.0) {
            return;
        }
        double scale = caps.scaleOf(victim);
        double formulaDamage = scale > 0.0 ? carrierDamage / scale : carrierDamage;
        if (formulaDamage > 0.0) {
            plugin.getCC().breakOnDamage(victim, formulaDamage, caps.formulaMaxOf(victim));
        }
    }

    /* --------------------- исходящий офенс --------------------- */

    private void applyOutgoingOffense(EntityDamageEvent event) {
        if (!(event instanceof EntityDamageByEntityEvent by)) {
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity)) {
            return;
        }
        Player attacker = resolveAttacker(by);
        if (attacker == null) {
            CombatProfile profile = profileOfSource(by);
            if (profile != null) {
                double base = profile.attackDamage() * caps.scaleOf(by.getEntity());
                double total = base;
                if (profile.critChance() > 0
                        && java.util.concurrent.ThreadLocalRandom.current().nextDouble() * 100 < profile.critChance())
                    total *= profile.critMult();
                if (Double.isFinite(total) && total >= 0) event.setDamage(total);
            }
            return;
        }
        DamageType type = effectiveType(event);
        if (type == DamageType.TRUE) {
            return;
        }
        UUID uuid = attacker.getUniqueId();
        double add = 0.0;
        boolean crit = false;
        if (type == DamageType.PHYSICAL) {
            add += powers.weaponPower(uuid) * cfgD("attributes.offense.basic-coeff", 0.35);
            if (plugin.getConfig().getBoolean("attributes.offense.str-to-physical", false)) {
                add += plugin.getAttributes().value(uuid, dev.raskol.classes.attribute.AttributeType.STR)
                        + plugin.getAttributes()
                                .levelOf(uuid, plugin.getClassProvider().getClassOf(attacker));
            }
            crit = rollMeleeCrit(attacker);
        } else {
            add += powers.spellPower(uuid) * cfgD("attributes.offense.basic-coeff-magic", 0.35);
            if (plugin.getConfig().getBoolean("attributes.offense.int-to-magic", false)) {
                add += plugin.getAttributes().value(uuid, dev.raskol.classes.attribute.AttributeType.INT)
                        + plugin.getAttributes()
                                .levelOf(uuid, plugin.getClassProvider().getClassOf(attacker));
            }
            crit = rollSpellCrit(attacker);
        }
        add *= caps.scaleOf(by.getEntity());
        double base = event.getDamage();
        double itemDamage = plugin.getItems().value(uuid, dev.raskol.classes.item.model.ItemStat.ATTACK_DAMAGE);
        if (by.getDamager() instanceof Projectile projectile) {
            Double shot = plugin.getItems().pdc().shotDamage(projectile);
            if (shot != null && Double.isFinite(shot) && shot >= 0) itemDamage = shot;
        }
        if (itemDamage > 0.0) itemDamage *= caps.scaleOf(by.getEntity());
        if (itemDamage > 0.0) base = itemDamage;
        double total = base + add;

        if (type == DamageType.PHYSICAL) {
            PlayerClass pc = plugin.getClassProvider().getClassOf(attacker);
            org.bukkit.inventory.ItemStack mainHand = attacker.getInventory().getItemInMainHand();
            ItemDefinition weaponDef = plugin.getItems().definitionOf(mainHand);
            WeaponType weaponType = weaponDef != null ? weaponDef.weaponType() : null;
            double mastery = WeaponMastery.masteryMultiplier(pc, weaponType);
            total *= mastery;
        }

        double stealth = combat.procs().rollStealthBonus(attacker);
        if (stealth > 1.0) {
            total *= stealth;
        }

        double amp = combat.procs().getAmplifier(attacker);
        if (amp > 1.0) {
            total *= amp;
            combat.procs().consumeAmplifier(attacker);
        }

        if (crit) {
            double baseMult = type == DamageType.PHYSICAL ? meleeMult(attacker) : spellMult(attacker);
            total *= baseMult;
            combat.critFeedback(attacker, type == DamageType.PHYSICAL);
            combat.procs().rollCritProcs(attacker, (LivingEntity) by.getEntity(), true, type == DamageType.PHYSICAL);
            if (type == DamageType.PHYSICAL) {
                combat.procs().setUndodgeable(attacker);
            }
        }

        if (Math.abs(total - base) > 1e-9 && Double.isFinite(total) && total >= 0.0) {
            event.setDamage(total);
        }
    }

    private Player resolveAttacker(EntityDamageByEntityEvent by) {
        Entity damager = by.getDamager();
        if (damager instanceof Player p) {
            return p;
        }
        if (damager instanceof Projectile proj && proj.getShooter() instanceof Player ps) {
            return ps;
        }
        return null;
    }

    private Entity sourceEntity(EntityDamageByEntityEvent by) {
        Entity damager = by.getDamager();
        return damager instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter
                ? shooter
                : damager;
    }

    private CombatProfile profileOfSource(EntityDamageByEntityEvent by) {
        Entity source = sourceEntity(by);
        return source instanceof LivingEntity living
                ? plugin.getCombatProfiles().profileOf(living.getUniqueId())
                : null;
    }

    private DamageType effectiveType(EntityDamageEvent event) {
        if (!(event instanceof EntityDamageByEntityEvent by)) return typeOf(event.getCause());
        CombatProfile profile = profileOfSource(by);
        if (profile != null) {
            double phys = profile.damageWeights().physical(),
                    magic = profile.damageWeights().magic(),
                    truth = profile.damageWeights().trueDamage();
            if (truth >= phys && truth >= magic && truth > 0) return DamageType.TRUE;
            return magic > phys ? DamageType.MAGIC : DamageType.PHYSICAL;
        }
        if (by.getDamager() instanceof Projectile projectile) {
            String shotType = plugin.getItems().pdc().shotType(projectile);
            if (shotType != null) return "MAGIC".equals(shotType) ? DamageType.MAGIC : DamageType.PHYSICAL;
            if (projectile.getShooter() instanceof Player shooter
                    && "MAGIC".equals(plugin.getItems().autoAttackType(shooter.getUniqueId()))) return DamageType.MAGIC;
        } else if (by.getDamager() instanceof Player player
                && "MAGIC".equals(plugin.getItems().autoAttackType(player.getUniqueId()))) return DamageType.MAGIC;
        return typeOf(event.getCause());
    }

    private boolean rollMeleeCrit(Player player) {
        return java.util.concurrent.ThreadLocalRandom.current().nextDouble() * 100.0
                < plugin.getAttributes().critMeleeChance(player.getUniqueId());
    }

    private boolean rollSpellCrit(Player player) {
        return java.util.concurrent.ThreadLocalRandom.current().nextDouble() * 100.0
                < plugin.getAttributes().critSpellChance(player.getUniqueId());
    }

    private double meleeMult(Player attacker) {
        return cfgD("attributes.crit.melee-mult", 1.5)
                * combat.procs().critMultBonus(attacker)
                * (1.0
                        + plugin.getItems()
                                        .value(
                                                attacker.getUniqueId(),
                                                dev.raskol.classes.item.model.ItemStat.CRIT_DAMAGE)
                                / 100.0);
    }

    private double spellMult(Player attacker) {
        return cfgD("attributes.crit.spell-mult", 1.5)
                * combat.procs().critMultBonus(attacker)
                * (1.0
                        + plugin.getItems()
                                        .value(
                                                attacker.getUniqueId(),
                                                dev.raskol.classes.item.model.ItemStat.CRIT_DAMAGE)
                                / 100.0);
    }

    private static boolean isPvp(EntityDamageEvent event) {
        if (!(event instanceof EntityDamageByEntityEvent byEntity)) {
            return false;
        }
        Entity damager = byEntity.getDamager();
        if (damager instanceof Player) {
            return true;
        }
        return damager instanceof Projectile proj && proj.getShooter() instanceof Player;
    }

    private DamageType typeOf(EntityDamageEvent.DamageCause cause) {
        String override = plugin.getConfig().getString("damage-types.vanilla-map." + cause.name());
        if (override != null && !override.isEmpty()) {
            try {
                return DamageType.valueOf(override.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return DamageType.defaultFor(cause);
    }
}
