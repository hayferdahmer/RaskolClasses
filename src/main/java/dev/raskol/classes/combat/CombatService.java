// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.combat;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.ability.WarlockAbilities;
import dev.raskol.classes.attribute.PowerService;
import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.combat.school.ElementalResistService;
import dev.raskol.classes.combat.school.PenTraitsService;
import dev.raskol.classes.combat.school.Penetration;
import dev.raskol.classes.combat.school.School;
import dev.raskol.classes.combat.school.SchoolConfig;
import dev.raskol.classes.combat.school.SchoolMitigation;
import dev.raskol.classes.config.RaskolConfig;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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

import java.util.Locale;
import java.util.UUID;

/**
 * 1.11.4 (P3): ТОНКИЙ ФАСАД боевого ядра. Публичный API сохранён полностью:
 *   dealDamage / canHit / simulateTaken / powers / resists / avoidance / caps,
 *   статик-маркеры (SUPPRESS / ABILITY_SOURCE / REFLECT_SUPPRESS), cappedDamage.
 * Путь A вынесен в VanillaDamageListener (регистрируется здесь же, в конструкторе);
 * капы и burst-окно — в DamageCaps; pure-математика — в CombatMath.
 * 1.12.2 (Блок 1): ElementalResistService (стихийный слой резистов).
 * 1.12.2 (Блок 4): живая проводка pen/elemental в путь B: phys→School.PHYSICAL,
 *         magic→School.ARCANE (соглашение legacy-адаптера); pen=0 и elemental=0 →
 *         множители равны старым резист-факторам (нейтрально к 1.12.1).
 */
public final class CombatService implements Listener {

    private static final ThreadLocal<Boolean> SUPPRESS = ThreadLocal.withInitial(() -> Boolean.FALSE);
    private static final ThreadLocal<UUID> ABILITY_SOURCE = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> REFLECT_SUPPRESS = ThreadLocal.withInitial(() -> Boolean.FALSE);
    private static volatile org.bukkit.damage.DamageType magicTypeCache;

    private final RaskolClasses plugin;
    private final ResistService resists;
    private final AvoidanceService avoidance;
    private final PowerService powers;
    private final DamageCaps caps;
    private final VanillaDamageListener vanillaListener;
    private final ElementalResistService elemental;
    private final SchoolConfig schoolConfig;
    private final PenTraitsService penTraits;

    public CombatService(RaskolClasses plugin, ResistService resists) {
        this.plugin = plugin;
        this.resists = resists;
        this.avoidance = new AvoidanceService(plugin);
        this.powers = new PowerService(plugin);
        this.caps = new DamageCaps(plugin);
        this.vanillaListener = new VanillaDamageListener(
                plugin, this, resists, avoidance, powers, caps);
        plugin.getServer().getPluginManager().registerEvents(vanillaListener, plugin);
        this.schoolConfig = new SchoolConfig(plugin);
        this.elemental = new ElementalResistService(plugin, schoolConfig);
        this.penTraits = new PenTraitsService(plugin);
    }

    /* ------------------------------ статик-маркеры ------------------------------ */

    /** Читает и сбрасывает флаг «урон применён нами» (путь B → путь A). */
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

    /** UUID игрока-источника внутри dealDamage (синхронно виден в событии). */
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

    /** 1.12.2 (Блок 1): стихийный слой резистов школ. */
    public ElementalResistService elemental() {
        return elemental;
    }

    /** 1.12.2 (Блок 3): агрегатор pen-трейтов (gear+таланты+спеки). */
    public PenTraitsService penTraits() {
        return penTraits;
    }

    /** Делегат для BalanceSimulator/selftest (внешний API не меняем). */
    public static double cappedDamage(double damage, double maxHp, double pct) {
        return CombatMath.cappedDamage(damage, maxHp, pct);
    }

    public void purgeBurstLog() {
        caps.purge();
    }

    /* ------------------------------ хелперы единиц ------------------------------ */

    private double cfgD(String path, double def) {
        double v = plugin.getConfig().getDouble(path, def);
        return Double.isFinite(v) ? v : def;
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

    /** Доля HP (formula для игроков) для порога игнора маг-резиста. */
    private double hpFractionOf(LivingEntity e) {
        if (e instanceof Player p) {
            double max = plugin.getHpBarService().formulaMaxHp(p.getUniqueId());
            return max > 0.0 ? plugin.getHpBarService().currentFormulaHp(p) / max : 1.0;
        }
        double max = e.getMaxHealth();
        return max > 0.0 ? e.getHealth() / max : 1.0;
    }

    /* ------------------------- фракционный гейт ------------------------- */

    public boolean canHit(Entity source, LivingEntity target) {
        if (target == null) {
            return false;
        }
        if (!(target instanceof Player tp)) {
            return true;
        }
        if (source == null) {
            return true;
        }
        if (!(source instanceof Player sp)) {
            return true;
        }
        if (sp.getUniqueId().equals(tp.getUniqueId())) {
            return true;
        }
        if (plugin.getConfig().getBoolean("combat.friendly-fire", false)) {
            return true;
        }
        return !Targeting.isAlly(plugin, sp.getUniqueId(), tp.getUniqueId());
    }

    /* ------------------------- симулятор (effective) ------------------------- */

    public double simulateTaken(LivingEntity target, DamageProfile profile) {
        if (profile == null || target == null) {
            return 0.0;
        }
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
        return dealDamage(target, source, profile, false);
    }

    public double dealDamage(LivingEntity target, Entity source, DamageProfile profile,
                             boolean allowOverCap) {
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
        double truePart = Math.min(safe.trueDamage(), trueCap());
        double physBase = safe.physical();
        double magicBase = safe.magic();
        if (source instanceof Player sp) {
            if (physBase > 0.0 && rollMeleeCrit(sp)) {
                physBase *= meleeMult();
                critFeedback(sp, true);
            }
            if (magicBase > 0.0 && rollSpellCrit(sp)) {
                magicBase *= spellMult();
                critFeedback(sp, false);
            }
        }

        // 1.11.3: игнор маг-резиста чернокнижником при HP ≤ порога (любая сторона)
        boolean warlockIgnoreMagic = false;
        if (source instanceof Player spSrc && magicBase > 0.0
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
                // legacy-ветка: рубильник школ выключен
                physPart = Double.isFinite(physFactor) ? physBase * physFactor : physBase;
                double magicScaled = Double.isFinite(magicFactor) ? magicBase * magicFactor : magicBase;
                if (warlockIgnoreMagic) {
                    magicScaled = magicBase;
                }
                magicTruePart = magicScaled + truePart;
            } else {
                // 1.12.2 (Блок 4): pen атакующего + стихийный слой цели
                double physResistPct = Double.isFinite(physFactor) ? (1.0 - physFactor) * 100.0 : 0.0;
                double magicResistPct = Double.isFinite(magicFactor) ? (1.0 - magicFactor) * 100.0 : 0.0;
                if (warlockIgnoreMagic) {
                    magicResistPct = 0.0;
                }
                double effElPhys = 0.0;
                double effElMagic = 0.0;
                if (source instanceof Player srcP) {
                    UUID sUuid = srcP.getUniqueId();
                    Penetration penPhys = penTraits.channelPen(
                            sUuid, true, plugin.getGearHook(), schoolConfig.penPctCap());
                    Penetration penMagic = penTraits.channelPen(
                            sUuid, false, plugin.getGearHook(), schoolConfig.penPctCap());
                    physResistPct = penPhys.effectiveResist(physResistPct, schoolConfig.penPctCap());
                    magicResistPct = penMagic.effectiveResist(magicResistPct, schoolConfig.penPctCap());
                    double elPhys = elemental.resistOf(uuid, School.PHYSICAL);
                    double elMagic = elemental.resistOf(uuid, School.ARCANE);
                    double spPhys = penTraits.schoolPenFraction(
                            sUuid, School.PHYSICAL, plugin.getGearHook(), schoolConfig.penPctCap());
                    double spMagic = penTraits.schoolPenFraction(
                            sUuid, School.ARCANE, plugin.getGearHook(), schoolConfig.penPctCap());
                    effElPhys = CombatMath.effectiveResist(elPhys, 0.0, spPhys, schoolConfig.penPctCap());
                    effElMagic = CombatMath.effectiveResist(elMagic, 0.0, spMagic, schoolConfig.penPctCap());
                }
                double mitPhys = SchoolMitigation.mitigationFor(physResistPct, Penetration.NONE,
                        schoolConfig.penPctCap(), effElPhys,
                        schoolConfig.elementalEnabled(), schoolConfig.mitigationCap());
                double mitMagic = SchoolMitigation.mitigationFor(magicResistPct, Penetration.NONE,
                        schoolConfig.penPctCap(), effElMagic,
                        schoolConfig.elementalEnabled(), schoolConfig.mitigationCap());
                physPart = physBase * (1.0 - mitPhys);
                double magicScaled = magicBase * (1.0 - mitMagic);
                magicTruePart = magicScaled + truePart;
            }
        } else {
            physPart = physBase;
            magicTruePart = magicBase + truePart;
        }

        // «Печать Погибели»: амплификация входящего урона (путь B)
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
        debugLog(target, source, safe, taken);
        double scale = caps.scaleOf(target);
        double applyPhys = physPart * scale;
        double applyMagic = magicTruePart * scale;

        ABILITY_SOURCE.set(source instanceof Player s2 ? s2.getUniqueId() : null);
        try {
            if (applyPhys > 0.0) {
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
            }
            if (applyMagic > 0.0) {
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
            }
        } finally {
            ABILITY_SOURCE.remove();
        }

        // откат чернокнижника 6.66% (глушится для рефлект-урона)
        if (source instanceof Player attacker && taken > 0.0
                && !reflectSuppressed()
                && plugin.getClassProvider().getClassOf(attacker) == PlayerClass.WARLOCK) {
            RaskolConfig cfg = plugin.getRaskolConfig();
            double recoil = taken * cfg.warlockRecoilPercent() / 100.0;
            double maxRecoil = caps.formulaMaxOf(attacker) * cfg.warlockRecoilCapPct() / 100.0;
            recoil = Math.min(recoil, maxRecoil);
            double scaleA = caps.scaleOf(attacker);
            double carrierNow = attacker.getHealth();
            double newCarrier = carrierNow - recoil * scaleA;
            attacker.setHealth(Math.max(cfg.warlockRecoilMinHp(), newCarrier));
        }

        return taken;
    }

    private boolean rollMeleeCrit(Player player) {
        return java.util.concurrent.ThreadLocalRandom.current().nextDouble() * 100.0
                < plugin.getAttributes().critMeleeChance(player.getUniqueId());
    }

    private boolean rollSpellCrit(Player player) {
        return java.util.concurrent.ThreadLocalRandom.current().nextDouble() * 100.0
                < plugin.getAttributes().critSpellChance(player.getUniqueId());
    }

    private double meleeMult() {
        return cfgD("attributes.crit.melee-mult", 1.5);
    }

    private double spellMult() {
        return cfgD("attributes.crit.spell-mult", 1.5);
    }

    private void critFeedback(Player attacker, boolean melee) {
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

    /* ------------------------------ анти-хил (S5) ------------------------------ */

    /** 1.10.0 / 1.11.2 (S5): анти-хил блокирует ВСЕ причины лечения. */
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
            String reason = event.getRegainReason() != null ? event.getRegainReason().name() : "UNKNOWN";
            Component msg = Component.text(
                    "[antiheal] " + le.getName() + " блокировано лечение ("
                            + reason + ", amount=" + event.getAmount() + ")",
                    NamedTextColor.DARK_PURPLE);
            for (Player online : plugin.getServer().getOnlinePlayers()) {
                if (online.hasPermission("raskolclasses.debug")) {
                    online.sendMessage(msg);
                }
            }
        }
    }

    /* ------------------------------ debug-лог ------------------------------ */

    private void debugLog(LivingEntity target, Entity source, DamageProfile profile, double taken) {
        if (!plugin.getConfig().getBoolean("combat.debug-damage", false)) {
            return;
        }
        String resistInfo;
        if (target instanceof Player tp) {
            UUID uuid = tp.getUniqueId();
            resistInfo = String.format(Locale.ROOT, "резисты: физ %.0f%% / маг %.0f%%",
                    resists.physicalResist(uuid), resists.magicResist(uuid));
        } else {
            resistInfo = "резисты: физ 0% / маг 0%";
        }
        String line = String.format(Locale.ROOT,
                "[dmg] %s → %s: профиль %.1f физ / %.1f маг / %.1f чист → дошло %.1f (%s)",
                source != null ? source.getName() : "env",
                target.getName(),
                profile.physical(), profile.magic(), profile.trueDamage(),
                taken, resistInfo);
        Component message = Component.text(line, NamedTextColor.DARK_GRAY);
        for (Player online : plugin.getServer().getOnlinePlayers()) {
            if (online.hasPermission("raskolclasses.debug")) {
                online.sendMessage(message);
            }
        }
    }
}
