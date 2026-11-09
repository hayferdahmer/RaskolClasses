// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.combat;

import java.util.Locale;
import java.util.UUID;

import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityRegainHealthEvent;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.ability.WarlockAbilities;
import dev.raskol.classes.api.combat.CombatProfile;
import dev.raskol.classes.attribute.Hp;
import dev.raskol.classes.attribute.PowerService;
import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.combat.dot.DotService;
import dev.raskol.classes.combat.school.ElementalResistService;
import dev.raskol.classes.combat.school.PenTraitsService;
import dev.raskol.classes.combat.school.Penetration;
import dev.raskol.classes.combat.school.School;
import dev.raskol.classes.combat.school.SchoolConfig;
import dev.raskol.classes.combat.school.SchoolImmunity;
import dev.raskol.classes.combat.school.SchoolMitigation;
import dev.raskol.classes.config.ConfigReads;
import dev.raskol.classes.config.RaskolConfig;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * 1.11.4 (P3): ТОНКИЙ ФАСАД боевого ядра. Публичный API сохранён полностью.
 * 1.12.2: ElementalResistService, PenTraitsService, живая проводка pen+elemental.
 * 1.12.3: ThreadLocal-контекст школы (currentCastSchool) — иммунитеты/множители в пути B.
 * 1.12.4: DotService (реестр DoT + тик-задача) создан здесь, геттер dots().
 * 1.13.0 (Б2): после применения урона пути B — CCService.breakOnDamage.
 * 1.14.3 (Волна 3, 3B/3C1/3C2): хот-путь урона, ProcService, каст-проки.
 * 1.14.7 (Sprint 3, P0-6A): enrageMult и onMeleeHitLand.
 * 1.14.10 (Sprint 7): critFeedback public; cfgD → ConfigReads.
 * 1.14.12 (Sprint 8): hpFractionOf через Hp.max.
 * 1.15.5 (Волна 3, аудит 2.6-31): before/after health guard — фактический урон.
 * 1.15.6 (Волна 4, аудит 2.6-2): setNoDamageTicks(0) перед каждым ударом пути B.
 * 1.15.6 (Волна 7, аудит 2.6-5): отдача Чернокнижика ТОЛЬКО уменьшает: не лечит при
 *         HP ниже minHp (верхний зажим carrierNow), не опускает ниже minHp,
 *         не выше carrierMax, пропускает мёртвых.
 * 1.15.6 (Волна 7, аудит 2.6-33): CombatProfile цели (RaskolMobs) учитывается в пути B:
 *         channel/school-резисты, иммунитеты и уязвимости профиля применяются к
 *         физ- и маг-каналам (зеркально моб-ветви пути A).
 * 1.15.6 (Волна 7, аудит 2.6-39): double_strike повторяет ПЕРВЫЙ удар как true-урон
 *         (фактически дошедшая физ-часть): резисты и множители не применяются дважды.
 */
public final class CombatService implements Listener {

    private static final ThreadLocal<Boolean> SUPPRESS = ThreadLocal.withInitial(() -> Boolean.FALSE);
    private static final ThreadLocal<UUID> ABILITY_SOURCE = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> REFLECT_SUPPRESS = ThreadLocal.withInitial(() -> Boolean.FALSE);
    /** 1.12.3: школа текущего каста (set перед вызовом кастера, clear после). */
    private static final ThreadLocal<School> CURRENT_CAST_SCHOOL = new ThreadLocal<>();

    private static volatile org.bukkit.damage.DamageType magicTypeCache;

    private final RaskolClasses plugin;
    private final ResistService resists;
    private final AvoidanceService avoidance;
    private final PowerService powers;
    private final DamageCaps caps;
    private final VanillaDamageListener vanillaListener;
    private final ElementalResistService elemental;
    private final SchoolConfig schoolConfig;
    private final SchoolImmunity schoolImmunity;
    private final PenTraitsService penTraits;
    private final DotService dots;
    private final ProcService procs;

    public CombatService(RaskolClasses plugin, ResistService resists) {
        this.plugin = plugin;
        this.resists = resists;
        this.avoidance = new AvoidanceService(plugin);
        this.powers = new PowerService(plugin);
        this.caps = new DamageCaps(plugin);
        this.procs = new ProcService(plugin);
        this.vanillaListener = new VanillaDamageListener(plugin, this, resists, avoidance, powers, caps);
        this.schoolConfig = new SchoolConfig(plugin);
        this.schoolImmunity = new SchoolImmunity(plugin);
        this.elemental = new ElementalResistService(plugin, schoolConfig);
        this.penTraits = new PenTraitsService(plugin);
        this.dots = new DotService(plugin, this);
    }

    /* ------------------------------ статик-маркеры ------------------------------ */

    public static boolean consumeSuppress() {
        boolean v = Boolean.TRUE.equals(SUPPRESS.get());
        if (v) {
            SUPPRESS.set(Boolean.FALSE);
        }
        return v;
    }

    public static void setSuppress(boolean value) {
        SUPPRESS.set(value);
    }

    public static UUID abilitySourceMark() {
        return ABILITY_SOURCE.get();
    }

    public static boolean reflectSuppressed() {
        return Boolean.TRUE.equals(REFLECT_SUPPRESS.get());
    }

    public static void beginReflect() {
        REFLECT_SUPPRESS.set(Boolean.TRUE);
    }

    public static void endReflect() {
        REFLECT_SUPPRESS.set(Boolean.FALSE);
    }

    public static void setCurrentCastSchool(School school) {
        if (school == null) {
            CURRENT_CAST_SCHOOL.remove();
        } else {
            CURRENT_CAST_SCHOOL.set(school);
        }
    }

    public static School currentCastSchool() {
        return CURRENT_CAST_SCHOOL.get();
    }

    public static void clearCastSchool() {
        CURRENT_CAST_SCHOOL.remove();
    }

    /* ------------------------------ геттеры слоёв ------------------------------ */

    public ResistService resists() {
        return resists;
    }

    public AvoidanceService avoidance() {
        return avoidance;
    }

    public PowerService powers() {
        return powers;
    }

    public DamageCaps caps() {
        return caps;
    }

    public VanillaDamageListener vanillaListener() {
        return vanillaListener;
    }

    public ElementalResistService elemental() {
        return elemental;
    }

    public PenTraitsService penTraits() {
        return penTraits;
    }

    public SchoolConfig schoolConfig() {
        return schoolConfig;
    }

    public SchoolImmunity schoolImmunity() {
        return schoolImmunity;
    }

    public DotService dots() {
        return dots;
    }

    public ProcService procs() {
        return procs;
    }

    public static double cappedDamage(double damage, double maxHp, double pct) {
        return CombatMath.cappedDamage(damage, maxHp, pct);
    }

    public void purgeBurstLog() {
        caps.purge();
    }

    /* ------------------------------ хелперы единиц ------------------------------ */

    private double cfgD(String path, double def) {
        return ConfigReads.finiteDouble(plugin.getConfig(), path, def);
    }

    private static org.bukkit.damage.DamageType magicType() {
        org.bukkit.damage.DamageType local = magicTypeCache;
        if (local == null) {
            local = RegistryAccess.registryAccess()
                    .getRegistry(RegistryKey.DAMAGE_TYPE)
                    .get(NamespacedKey.minecraft("magic"));
            magicTypeCache = local;
        }
        return local;
    }

    private double trueCap() {
        double v = cfgD("combat.true-damage-cap-per-hit", 1000.0);
        return v >= 0.0 ? v : 1000.0;
    }

    public double carrierMaxOf(LivingEntity target) {
        return caps.carrierMaxOf(target);
    }

    public double formulaMaxOf(LivingEntity target) {
        return caps.formulaMaxOf(target);
    }

    public double scaleOf(Entity target) {
        return caps.scaleOf(target);
    }

    private double hpFractionOf(LivingEntity e) {
        if (e instanceof Player p) {
            double max = plugin.getHpBarService().formulaMaxHp(p.getUniqueId());
            return max > 0.0 ? plugin.getHpBarService().currentFormulaHp(p) / max : 1.0;
        }
        double max = Hp.max(e);
        return max > 0.0 ? e.getHealth() / max : 1.0;
    }

    /* ------------------------- фракционный гейт ------------------------- */

    public boolean canHit(Entity source, LivingEntity target) {
        if (target == null) return false;
        if (!(target instanceof Player tp)) return true;
        if (source == null) return true;
        if (!(source instanceof Player sp)) return true;
        if (sp.getUniqueId().equals(tp.getUniqueId())) return true;
        if (plugin.getConfig().getBoolean("combat.friendly-fire", false)) return true;
        return !Targeting.isAlly(plugin, sp.getUniqueId(), tp.getUniqueId());
    }

    /* ------------------------- симулятор (effective) ------------------------- */

    public double simulateTaken(LivingEntity target, DamageProfile profile) {
        if (profile == null || target == null) return 0.0;
        DamageProfile safe = CombatMath.sanitize(profile);
        double truePart = Math.min(safe.trueDamage(), trueCap());
        if (resists.disabledIn(target.getWorld())) {
            return safe.physical() + safe.magic() + truePart;
        }
        if (target instanceof Player p) {
            UUID uuid = p.getUniqueId();
            double physFactor = resists.physicalFactor(uuid);
            double magicFactor = resists.magicFactor(uuid);
            double phys = Double.isFinite(physFactor) ? safe.physical() * physFactor : safe.physical();
            double magic = Double.isFinite(magicFactor) ? safe.magic() * magicFactor : safe.magic();
            return phys + magic + truePart;
        }
        return safe.physical() + safe.magic() + truePart;
    }

    /* ------------------------- путь B (наши способности) ------------------------- */

    public double dealDamage(LivingEntity target, Entity source, DamageProfile profile) {
        return dealDamage(target, source, profile, false, false);
    }

    public double dealDamage(LivingEntity target, Entity source, DamageProfile profile, boolean allowOverCap) {
        return dealDamage(target, source, profile, allowOverCap, false);
    }

    public double dealDamage(
            LivingEntity target, Entity source, DamageProfile profile, boolean allowOverCap, boolean fromProc) {
        if (profile == null || target == null || target.isDead()) {
            return 0.0;
        }
        if (!canHit(source, target)) {
            return 0.0;
        }
        DamageProfile safe = CombatMath.sanitize(profile);
        if (safe.isEmpty()) {
            return 0.0;
        }
        if (target instanceof Player tp) {
            GameMode gm = tp.getGameMode();
            if (gm == GameMode.SPECTATOR || gm == GameMode.CREATIVE) {
                return 0.0;
            }
        }

        School castSchool = currentCastSchool();
        double schoolMult = 1.0;
        if (castSchool != null && schoolConfig.enabled()) {
            schoolMult *= schoolConfig.multiplier(castSchool);
            double immMult = schoolImmunity.multiplierFor(target.getType(), castSchool);
            if (immMult <= 0.0) {
                return 0.0;
            }
            schoolMult *= immMult;
        }

        double truePart = Math.min(safe.trueDamage(), trueCap());
        double physBase = safe.physical();
        double magicBase = safe.magic();

        if (source instanceof Player srcP) {
            UUID sUuid = srcP.getUniqueId();
            double physPct = safePct(plugin.getSpec2Service().physDmgPercent(sUuid));
            double magicPct = safePct(plugin.getSpec2Service().magicDmgPercent(sUuid));
            if (physPct > 0.0) physBase *= (1.0 + physPct);
            if (magicPct > 0.0) magicBase *= (1.0 + magicPct);
        }

        if (source instanceof Player srcP) {
            double amp = procs.getAmplifier(srcP);
            if (amp > 1.0) {
                physBase *= amp;
                magicBase *= amp;
                procs.consumeAmplifier(srcP);
            }
        }

        double expose = procs.readExposeMult(target);
        if (expose > 1.0) {
            physBase *= expose;
            magicBase *= expose;
        }

        if (source instanceof Player srcP) {
            double enrage = procs.enrageMult(srcP);
            if (enrage > 1.0) {
                physBase *= enrage;
            }
        }

        if (source instanceof Player srcP) {
            double stealth = procs.rollStealthBonus(srcP);
            if (stealth > 1.0) {
                physBase *= stealth;
                magicBase *= stealth;
            }
        }

        double armorPen = 0.0;
        if (source instanceof Player srcP) {
            armorPen = procs.rollArmorPen(srcP, target);
            if (!Double.isFinite(armorPen) || armorPen < 0.0) {
                armorPen = 0.0;
            }
            if (armorPen > 1.0) {
                armorPen = 1.0;
            }
        }

        if (physBase > 0.0 && target instanceof Player tgtP && hasShield(tgtP)) {
            if (rollBlock(tgtP)) {
                blockFeedback(tgtP);
                physBase = 0.0;
            }
        }

        boolean physCrit = false;
        boolean magicCrit = false;
        if (source instanceof Player sp) {
            if (physBase > 0.0 && rollMeleeCrit(sp)) {
                physBase *= meleeMult(sp);
                physCrit = true;
                critFeedback(sp, true);
            }
            if (magicBase > 0.0 && rollSpellCrit(sp)) {
                magicBase *= spellMult(sp);
                magicCrit = true;
                critFeedback(sp, false);
            }
        }

        boolean warlockIgnoreMagic = false;
        if (source instanceof Player spSrc
                && magicBase > 0.0
                && plugin.getClassProvider().getClassOf(spSrc) == PlayerClass.WARLOCK) {
            double thr = cfgD("classes.WARLOCK.ignore-magic-resist-below-hp", 0.25);
            warlockIgnoreMagic = hpFractionOf(target) <= thr || hpFractionOf(spSrc) <= thr;
        }

        double physPart;
        double magicTruePart;
        if (resists.disabledIn(target.getWorld())) {
            physPart = physBase;
            magicTruePart = magicBase + truePart;
        } else if (target instanceof Player p) {
            UUID uuid = p.getUniqueId();
            double cap = source instanceof Player ? resists.pvpCap() : resists.cap();
            double physFactor = resists.physicalFactor(uuid, cap);
            double magicFactor = resists.magicFactor(uuid, cap);
            if (!schoolConfig.enabled()) {
                physFactor = penFactor(physFactor, armorPen);
                magicFactor = penFactor(magicFactor, armorPen);
                physPart = Double.isFinite(physFactor) ? physBase * physFactor : physBase;
                double magicScaled = Double.isFinite(magicFactor) ? magicBase * magicFactor : magicBase;
                if (warlockIgnoreMagic) {
                    magicScaled = magicBase;
                }
                magicTruePart = magicScaled + truePart;
            } else {
                double physResistPct = Double.isFinite(physFactor) ? (1.0 - physFactor) * 100.0 : 0.0;
                double magicResistPct = Double.isFinite(magicFactor) ? (1.0 - magicFactor) * 100.0 : 0.0;
                if (warlockIgnoreMagic) {
                    magicResistPct = 0.0;
                }
                double effElPhys = 0.0;
                double effElMagic = 0.0;
                if (source instanceof Player srcP) {
                    UUID sUuid = srcP.getUniqueId();
                    Penetration penPhys = penTraits.channelPen(sUuid, true, schoolConfig.penPctCap());
                    Penetration penMagic = penTraits.channelPen(sUuid, false, schoolConfig.penPctCap());
                    physResistPct = penPhys.effectiveResist(physResistPct, schoolConfig.penPctCap());
                    magicResistPct = penMagic.effectiveResist(magicResistPct, schoolConfig.penPctCap());
                    double elPhys = elemental.resistOf(uuid, School.PHYSICAL);
                    double elMagic = elemental.resistOf(uuid, School.ARCANE);
                    double spPhys = penTraits.schoolPenFraction(sUuid, School.PHYSICAL, schoolConfig.penPctCap());
                    double spMagic = penTraits.schoolPenFraction(sUuid, School.ARCANE, schoolConfig.penPctCap());
                    effElPhys = CombatMath.effectiveResist(elPhys, 0.0, spPhys, schoolConfig.penPctCap());
                    effElMagic = CombatMath.effectiveResist(elMagic, 0.0, spMagic, schoolConfig.penPctCap());
                }
                physResistPct = penResistPct(physResistPct, armorPen);
                magicResistPct = penResistPct(magicResistPct, armorPen);
                double mitPhys = SchoolMitigation.mitigationFor(
                        physResistPct,
                        Penetration.NONE,
                        schoolConfig.penPctCap(),
                        effElPhys,
                        schoolConfig.elementalEnabled(),
                        schoolConfig.mitigationCap());
                double mitMagic = SchoolMitigation.mitigationFor(
                        magicResistPct,
                        Penetration.NONE,
                        schoolConfig.penPctCap(),
                        effElMagic,
                        schoolConfig.elementalEnabled(),
                        schoolConfig.mitigationCap());
                physPart = physBase * (1.0 - mitPhys);
                double magicScaled = magicBase * (1.0 - mitMagic);
                magicTruePart = magicScaled + truePart;
            }
        } else {
            physPart = physBase;
            double magicPart = magicBase;
            // 1.15.6 (Волна 7, аудит 2.6-33): профиль RaskolMobs цели работает и в пути B
            CombatProfile prof = plugin.getCombatProfiles().profileOf(target.getUniqueId());
            if (prof != null) {
                School magicSchool = castSchool != null ? castSchool : School.ARCANE;
                if (prof.immunities().contains(School.PHYSICAL)) {
                    physPart = 0.0;
                } else {
                    physPart *= 1.0 - profileChannelResist(prof, true, source, School.PHYSICAL) / 100.0;
                    physPart *=
                            1.0 + Math.max(-100.0, prof.vulnerabilities().getOrDefault(School.PHYSICAL, 0.0)) / 100.0;
                }
                if (prof.immunities().contains(magicSchool)) {
                    magicPart = 0.0;
                } else {
                    magicPart *= 1.0 - profileChannelResist(prof, false, source, magicSchool) / 100.0;
                    magicPart *= 1.0 + Math.max(-100.0, prof.vulnerabilities().getOrDefault(magicSchool, 0.0)) / 100.0;
                }
            }
            magicTruePart = magicPart + truePart;
        }

        if (schoolMult != 1.0) {
            physPart *= schoolMult;
            magicTruePart *= schoolMult;
        }

        double amp = WarlockAbilities.sealAmplifyOf(target.getUniqueId());
        if (amp > 0.0) {
            physPart *= (1.0 + amp);
            magicTruePart *= (1.0 + amp);
        }

        double taken = physPart + magicTruePart;
        if (!allowOverCap && target instanceof Player tp2) {
            double pct = cfgD("combat.max-single-hit-pct", 35.0);
            if (pct > 0.0 && taken > 0.0) {
                double limit = caps.formulaMaxOf(tp2) * pct / 100.0;
                if (taken > limit) {
                    double f = limit / taken;
                    physPart *= f;
                    magicTruePart *= f;
                    taken = limit;
                }
            }
            double burstAllowed = caps.applyBurstCap(tp2, taken, caps.formulaMaxOf(tp2));
            if (burstAllowed < taken && taken > 0.0) {
                double f = burstAllowed / taken;
                physPart *= f;
                magicTruePart *= f;
                taken = burstAllowed;
            }
        }
        debugLog(target, source, safe, taken, castSchool);
        double scale = caps.scaleOf(target);
        double applyPhys = physPart * scale;
        double applyMagic = magicTruePart * scale;

        double actualPhys = 0.0;
        double actualMagic = 0.0;

        ABILITY_SOURCE.set(source instanceof Player s2 ? s2.getUniqueId() : null);
        try {
            if (applyPhys > 0.0) {
                double before = target.getHealth();
                target.setNoDamageTicks(0);
                setSuppress(true);
                try {
                    if (source != null) {
                        target.damage(applyPhys, source);
                    } else {
                        target.damage(applyPhys);
                    }
                } finally {
                    setSuppress(false);
                }
                double after = target.getHealth();
                if (after < before) {
                    actualPhys = (before - after) / scale;
                }
            }
            if (applyMagic > 0.0) {
                double before = target.getHealth();
                target.setNoDamageTicks(0);
                setSuppress(true);
                try {
                    org.bukkit.damage.DamageType magic = magicType();
                    if (magic != null) {
                        DamageSource.Builder builder = DamageSource.builder(magic);
                        if (source != null) {
                            builder = builder.withDirectEntity(source).withCausingEntity(source);
                        }
                        target.damage(applyMagic, builder.build());
                    } else {
                        target.damage(applyMagic);
                    }
                } finally {
                    setSuppress(false);
                }
                double after = target.getHealth();
                if (after < before) {
                    actualMagic = (before - after) / scale;
                }
            }
        } finally {
            ABILITY_SOURCE.remove();
        }

        double actualTaken = (actualPhys + actualMagic) * scale;
        if (actualTaken <= 0.0) {
            return 0.0;
        }

        plugin.getCC().breakOnDamage(target, actualTaken, caps.formulaMaxOf(target));
        procs.onDefenderDamaged(target, actualTaken);

        if (actualMagic > 0.0 && source instanceof Player srcP) {
            procs.onDefenderDamagedByMagic(target, srcP, actualMagic * scale);
        }

        if (!fromProc && source instanceof Player srcP) {
            if (actualPhys > 0.0) {
                procs.onMeleeHitLand(srcP, target);
            }
            procs.rollCritProcs(srcP, target, physCrit || magicCrit, physCrit);
            if (physCrit) {
                procs.setUndodgeable(srcP);
            }
            procs.applyExpose(srcP, target);
            if (physPart > 0.0) {
                final double recurPhys = physPart * scale;
                // 1.15.6 (Волна 7, аудит 2.6-39): второй удар double_strike = повтор
                // ПЕРВОГО (уже смягчённого) физ-удара, поданный как true-урон:
                // резисты/множители/криты не применяются повторно.
                procs.rollDoubleStrike(
                        srcP, target, () -> dealDamage(target, srcP, DamageProfile.trueDmg(recurPhys), true, true));
            }
        }

        // откат чернокнижника 6.66% (глушится для рефлект-урона)
        if (source instanceof Player attacker
                && actualTaken > 0.0
                && !reflectSuppressed()
                && plugin.getClassProvider().getClassOf(attacker) == PlayerClass.WARLOCK) {
            RaskolConfig cfg = plugin.getRaskolConfig();
            double recoil = actualTaken * cfg.warlockRecoilPercent() / 100.0;
            double maxRecoil = caps.formulaMaxOf(attacker) * cfg.warlockRecoilCapPct() / 100.0;
            recoil = Math.min(recoil, maxRecoil);
            double scaleA = caps.scaleOf(attacker);
            double carrierNow = attacker.getHealth();
            double minHp = cfg.warlockRecoilMinHp();
            // 1.15.6 (Волна 7, аудит 2.6-5): отдача ТОЛЬКО уменьшает.
            // Раньше setHealth(max(minHp, now - recoil)) ЛЕЧИЛА при HP < minHp
            // и не имела верхнего зажима. Теперь: пропуск мёртвых, пропуск при
            // HP ≤ minHp, верхний зажим = текущее HP и carrierMax, нижний = minHp.
            if (!attacker.isDead() && carrierNow > minHp && recoil > 0.0) {
                double newCarrier = carrierNow - recoil * scaleA;
                newCarrier = Math.max(minHp, newCarrier);
                newCarrier = Math.min(newCarrier, carrierNow);
                newCarrier = Math.min(newCarrier, caps.carrierMaxOf(attacker));
                attacker.setHealth(newCarrier);
            }
        }

        return actualTaken;
    }

    /**
     * 1.15.6 (Волна 7, аудит 2.6-33): channel+school резист профиля моба с pen/
     * schoolPen атакующего — зеркало моб-ветви пути A (VanillaDamageListener).
     */
    private double profileChannelResist(CombatProfile prof, boolean physical, Entity source, School school) {
        double channel = physical ? prof.physResist() : prof.magicResist();
        double schoolR = prof.schoolResists().getOrDefault(school, 0.0);
        double pen = 0.0;
        double schoolPen = 0.0;
        if (source instanceof Player sp) {
            pen = penTraits
                            .channelPen(sp.getUniqueId(), physical, schoolConfig.penPctCap())
                            .pct()
                    * 100.0;
            schoolPen = penTraits.schoolPenFraction(sp.getUniqueId(), school, schoolConfig.penPctCap()) * 100.0;
        }
        return Math.max(0.0, Math.min(90.0, channel - pen + schoolR - schoolPen));
    }

    private boolean rollMeleeCrit(Player player) {
        double baseChance = plugin.getAttributes().critMeleeChance(player.getUniqueId());
        double bonus = safePct(plugin.getSpec2Service().critMeleeBonus(player.getUniqueId()));
        return java.util.concurrent.ThreadLocalRandom.current().nextDouble() * 100.0 < (baseChance + bonus * 100.0);
    }

    private boolean rollSpellCrit(Player player) {
        return java.util.concurrent.ThreadLocalRandom.current().nextDouble() * 100.0
                < plugin.getAttributes().critSpellChance(player.getUniqueId());
    }

    private boolean rollBlock(Player target) {
        double pct = safePct(plugin.getSpec2Service().blockPercent(target.getUniqueId()));
        if (pct <= 0.0) {
            return false;
        }
        return java.util.concurrent.ThreadLocalRandom.current().nextDouble() < pct;
    }

    private boolean hasShield(Player player) {
        org.bukkit.inventory.ItemStack off = player.getInventory().getItemInOffHand();
        if (off != null && off.getType().name().endsWith("SHIELD")) {
            return true;
        }
        org.bukkit.inventory.ItemStack main = player.getInventory().getItemInMainHand();
        return main != null && main.getType().name().endsWith("SHIELD");
    }

    private double meleeMult(Player attacker) {
        return cfgD("attributes.crit.melee-mult", 1.5) * procs.critMultBonus(attacker);
    }

    private double spellMult(Player attacker) {
        return cfgD("attributes.crit.spell-mult", 1.5) * procs.critMultBonus(attacker);
    }

    private static double penFactor(double factor, double armorPen) {
        if (armorPen <= 0.0 || !Double.isFinite(factor)) {
            return factor;
        }
        return 1.0 - (1.0 - factor) * (1.0 - armorPen);
    }

    private static double penResistPct(double resistPct, double armorPen) {
        if (armorPen <= 0.0) {
            return resistPct;
        }
        return Math.max(0.0, resistPct * (1.0 - armorPen));
    }

    public void critFeedback(Player attacker, boolean melee) {
        if (!plugin.getConfig().getBoolean("attributes.crit.visuals", true)) {
            return;
        }
        String tag = melee
                ? plugin.getConfig().getString("attributes.crit.melee-tag", "⚡ Крит!")
                : plugin.getConfig().getString("attributes.crit.spell-tag", "✦ Магический крит!");
        attacker.showTitle(net.kyori.adventure.title.Title.title(
                Component.empty(),
                Component.text(tag, melee ? NamedTextColor.RED : NamedTextColor.LIGHT_PURPLE),
                net.kyori.adventure.title.Title.Times.times(
                        java.time.Duration.ofMillis(50),
                        java.time.Duration.ofMillis(550),
                        java.time.Duration.ofMillis(150))));
        dev.raskol.classes.fx.FxService fx = plugin.getFx();
        String soundKey = melee
                ? plugin.getConfig().getString("attributes.crit.melee-sound", "ENTITY_PLAYER_ATTACK_CRIT")
                : plugin.getConfig().getString("attributes.crit.spell-sound", "ENTITY_EVOKER_CAST_SPELL");
        org.bukkit.Sound s = fx.resolveSound(soundKey);
        if (s != null) {
            fx.playSound(attacker.getLocation(), s, 0.5f, melee ? 0.9f : 1.2f);
        }
    }

    private void blockFeedback(Player target) {
        if (!plugin.getConfig().getBoolean("combat.block.visuals", true)) {
            return;
        }
        dev.raskol.classes.fx.FxService fx = plugin.getFx();
        org.bukkit.Sound s = fx.resolveSound(plugin.getConfig().getString("combat.block.sound", "ITEM_SHIELD_BLOCK"));
        if (s != null) {
            fx.playSound(target.getLocation(), s, 0.6f, 1.0f);
        }
    }

    private static double safePct(double v) {
        return Double.isFinite(v) && v >= 0 ? v / 100.0 : 0.0;
    }

    /* ------------------------------ анти-хил (S5) ------------------------------ */

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onRegainHealth(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof LivingEntity le)) {
            return;
        }
        if (!WarlockAbilities.isAntihealed(le.getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        if (plugin.getConfig().getBoolean("combat.debug-damage", false)) {
            String reason = event.getRegainReason().name();
            Component msg = Component.text(
                    "[antiheal] " + le.getName() + " блокировано лечение (" + reason + ", amount=" + event.getAmount()
                            + ")",
                    NamedTextColor.DARK_PURPLE);
            for (Player online : plugin.getServer().getOnlinePlayers()) {
                if (online.hasPermission("raskolclasses.debug")) {
                    online.sendMessage(msg);
                }
            }
        }
    }

    /* ------------------------------ debug-лог ------------------------------ */

    private void debugLog(LivingEntity target, Entity source, DamageProfile profile, double taken, School school) {
        if (!plugin.getConfig().getBoolean("combat.debug-damage", false)) {
            return;
        }
        String resistInfo;
        if (target instanceof Player tp) {
            UUID uuid = tp.getUniqueId();
            resistInfo = String.format(
                    Locale.ROOT,
                    "резисты: физ %.0f%% / маг %.0f%%",
                    resists.physicalResist(uuid),
                    resists.magicResist(uuid));
        } else {
            resistInfo = "резисты: физ 0% / маг 0%";
        }
        String schoolTag = school != null ? " · school=" + school.id() : "";
        String line = String.format(
                Locale.ROOT,
                "[dmg] %s → %s: профиль %.1f физ / %.1f маг / %.1f чист → дошло %.1f (%s)%s",
                source != null ? source.getName() : "env",
                target.getName(),
                profile.physical(),
                profile.magic(),
                profile.trueDamage(),
                taken,
                resistInfo,
                schoolTag);
        Component message = Component.text(line, NamedTextColor.DARK_GRAY);
        for (Player online : plugin.getServer().getOnlinePlayers()) {
            if (online.hasPermission("raskolclasses.debug")) {
                online.sendMessage(message);
            }
        }
    }
}
