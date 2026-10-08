// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.combat;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.ability.RogueAbilities;
import dev.raskol.classes.spec.service.Spec2Service;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 1.14.3 (3C1/3C2): фасад proc-узлов деревьев путей.
 *
 * 1.14.6-fix (Спринт 1, P0-3): КОНТРАКТ ЕДИНИЦ.
 *  - Значения узлов proc_* — ДОЛИ за ранг: 0.15 = «15% шанс за ранг».
 *    Шанс срабатывания = clamp(Σ рангов, 0..1), бросок — rollChance(fraction).
 *  - СИЛА эффекта (амплификатор) берётся из конфига spec2.procs.<id>.amp
 *    с дефолтами из описаний узлов (riposte 0.50, revenge 0.10, headshot 1.00 …).
 *    Ранг влияет только на шанс, не на силу — как в описаниях («+50%» фиксировано).
 *  - proc_expose — детерминированный: величина = Σ рангов (доля), кап из конфига.
 *  - Процентные виды (hp_pct, resource_max, …) здесь НЕ читаются: они в геттерах
 *    Spec2Service и трактуются как проценты (делятся на 100 потребителем).
 *
 * 1.14.6-fix (Спринт 1, P0-2): ключи agg.proc приходят БЕЗ префикса proc_
 * (срезается в Spec2Service.accumulate), поэтому procBonus(uuid, "riposte") работает.
 *
 * Amplifier следующего удара ставится на ЗАЩИЩАВШЕГОСЯ (riposte/counterattack/
 * revenge/shield_slam) и снимается, когда он сам наносит урон.
 */
public final class ProcService {

    /** Результат ролла уклонения/парирования (AvoidanceService.tryAvoid). */
    public enum AvoidResult { NONE, DODGE, PARRY }

    private final RaskolClasses plugin;

    private final Map<UUID, Double> nextHitMult = new ConcurrentHashMap<>();
    private final Map<UUID, Long> nextHitExpiry = new ConcurrentHashMap<>();
    private final Map<UUID, Long> exposeExpiry = new ConcurrentHashMap<>();
    private final Map<UUID, Double> exposeMult = new ConcurrentHashMap<>();
    private final Map<UUID, Long> secondWindCd = new ConcurrentHashMap<>();
    private final Map<UUID, Long> undodgeableExpiry = new ConcurrentHashMap<>();

    public ProcService(RaskolClasses plugin) {
        this.plugin = plugin;
    }

    /* ------------------------------ pure-хелперы (selftest 103/112) ------------------------------ */

    /** Кламп доли шанса в [0,1]; NaN/отрицательные → 0. */
    public static double clampFraction(double v) {
        return Double.isFinite(v) ? Math.max(0.0, Math.min(1.0, v)) : 0.0;
    }

    /** Бросок шанса-доли. */
    public static boolean rollChance(double fraction) {
        return ThreadLocalRandom.current().nextDouble() < clampFraction(fraction);
    }

    /* ------------------------------ чтение агрегата и конфига ------------------------------ */

    private double proc(UUID uuid, String id) {
        Spec2Service svc = plugin.getSpec2Service();
        return svc == null ? 0.0 : svc.procBonus(uuid, id);
    }

    /** Сила эффекта прока: spec2.procs.<id>.amp, дефолт из описания узла. */
    private double amp(String id, double def) {
        double v = plugin.getConfig().getDouble("spec2.procs." + id + ".amp", def);
        return Double.isFinite(v) && v >= 0.0 ? v : def;
    }

    /* ------------------------------ amplifier (next-hit bonus) ------------------------------ */

    public double getAmplifier(Player attacker) {
        if (attacker == null) {
            return 1.0;
        }
        UUID uuid = attacker.getUniqueId();
        Long exp = nextHitExpiry.get(uuid);
        if (exp == null || System.currentTimeMillis() > exp) {
            nextHitExpiry.remove(uuid);
            nextHitMult.remove(uuid);
            return 1.0;
        }
        Double m = nextHitMult.get(uuid);
        return m != null && m > 1.0 ? m : 1.0;
    }

    public void consumeAmplifier(Player attacker) {
        if (attacker == null) {
            return;
        }
        UUID uuid = attacker.getUniqueId();
        nextHitExpiry.remove(uuid);
        nextHitMult.remove(uuid);
    }

    private void setAmplifier(Player owner, double mult, long durationMs) {
        if (owner == null || mult <= 1.0) {
            return;
        }
        UUID uuid = owner.getUniqueId();
        long now = System.currentTimeMillis();
        Long prev = nextHitExpiry.get(uuid);
        Double cur = nextHitMult.get(uuid);
        double newMult = (cur != null && cur > 1.0 && prev != null && now <= prev)
                ? cur * mult
                : mult;
        nextHitMult.put(uuid, newMult);
        nextHitExpiry.put(uuid, now + durationMs);
    }

    /* ------------------------------ expose (incoming damage bonus, детерминированный) ------------------------------ */

    public double readExposeMult(LivingEntity target) {
        if (target == null) {
            return 1.0;
        }
        UUID uuid = target.getUniqueId();
        Long exp = exposeExpiry.get(uuid);
        if (exp == null || System.currentTimeMillis() > exp) {
            exposeExpiry.remove(uuid);
            exposeMult.remove(uuid);
            return 1.0;
        }
        Double m = exposeMult.get(uuid);
        return m != null && m > 1.0 ? m : 1.0;
    }

    public void applyExpose(Player attacker, LivingEntity target) {
        if (attacker == null || target == null) {
            return;
        }
        double add = proc(attacker.getUniqueId(), "expose"); // доля за ранг, напр. 0.05
        if (add <= 0.0) {
            return;
        }
        double cap = plugin.getConfig().getDouble("spec2.procs.expose.cap", 0.30);
        add = Math.min(add, Double.isFinite(cap) && cap > 0.0 ? cap : 0.30);
        UUID uuid = target.getUniqueId();
        long now = System.currentTimeMillis();
        long durationMs = 6_000L;
        Double cur = exposeMult.get(uuid);
        Long exp = exposeExpiry.get(uuid);
        double newMult = (cur != null && cur > 1.0 && exp != null && now <= exp)
                ? Math.min(cur + add, 1.0 + (Double.isFinite(cap) ? cap : 0.30))
                : 1.0 + add;
        exposeMult.put(uuid, newMult);
        exposeExpiry.put(uuid, now + durationMs);
    }

    /* ------------------------------ on-avoid / on-block ------------------------------ */

    /**
     * PARRY → proc_riposte: шанс = доля за ранг, сила = amp (дефолт +50%).
     * DODGE → proc_counterattack: аналогично.
     * Amplifier получает ЗАЩИЩАВШИЙСЯ.
     */
    public void onDefenderAvoid(Player defender, Player attacker, AvoidResult result) {
        if (defender == null || attacker == null || result == AvoidResult.NONE) {
            return;
        }
        UUID duuid = defender.getUniqueId();
        long durationMs = 8_000L;
        if (result == AvoidResult.PARRY) {
            if (rollChance(proc(duuid, "riposte"))) {
                setAmplifier(defender, 1.0 + amp("riposte", 0.50), durationMs);
            }
        } else if (result == AvoidResult.DODGE) {
            if (rollChance(proc(duuid, "counterattack"))) {
                setAmplifier(defender, 1.0 + amp("counterattack", 0.50), durationMs);
            }
        }
    }

    /** Блок щитом: revenge (гарант-шанс, +10%) и shield_slam (шанс, +50%). */
    public void onDefenderBlock(Player defender, Player attacker) {
        if (defender == null || attacker == null) {
            return;
        }
        UUID duuid = defender.getUniqueId();
        long durationMs = 8_000L;
        if (rollChance(proc(duuid, "revenge"))) {
            setAmplifier(defender, 1.0 + amp("revenge", 0.10), durationMs);
        }
        if (rollChance(proc(duuid, "shield_slam"))) {
            setAmplifier(defender, 1.0 + amp("shield_slam", 0.50), durationMs);
        }
    }

    /* ------------------------------ on-damaged ------------------------------ */

    /** second_wind: шанс = доля за ранг; хил = amp (дефолт 8% formula-maxHP); КД 30 с. */
    public void onDefenderDamaged(LivingEntity victim, double formulaDamage) {
        if (!(victim instanceof Player p) || formulaDamage <= 0.0) {
            return;
        }
        UUID uuid = p.getUniqueId();
        double chance = proc(uuid, "second_wind");
        if (chance <= 0.0) {
            return;
        }
        Long cd = secondWindCd.get(uuid);
        long now = System.currentTimeMillis();
        if (cd != null && now < cd) {
            return;
        }
        double maxHp = plugin.getHpBarService().formulaMaxHp(uuid);
        double curHp = plugin.getHpBarService().currentFormulaHp(p);
        if (maxHp <= 0.0 || curHp / maxHp >= 0.35) {
            return;
        }
        if (!rollChance(chance)) {
            return;
        }
        double heal = maxHp * amp("second_wind", 0.08);
        plugin.getHpBarService().heal(p, heal, null);
        secondWindCd.put(uuid, now + 30_000L);
    }

    /** reflect_magic: шанс = доля за ранг; отражается amp (дефолт 20%) маг-урона. */
    public void onDefenderDamagedByMagic(LivingEntity victim, Player attacker, double magicDamage) {
        if (!(victim instanceof Player defender) || attacker == null || magicDamage <= 0.0) {
            return;
        }
        double chance = proc(defender.getUniqueId(), "reflect_magic");
        if (chance <= 0.0 || !rollChance(chance)) {
            return;
        }
        double reflect = magicDamage * amp("reflect_magic", 0.20);
        if (reflect <= 0.0) {
            return;
        }
        double scale = plugin.getHpBarService().scale(attacker);
        double carrierDmg = reflect * scale;
        CombatService.setSuppress(true);
        CombatService.beginReflect();
        try {
            attacker.damage(carrierDmg, defender);
        } finally {
            CombatService.endReflect();
            CombatService.setSuppress(false);
        }
    }

    /* ------------------------------ on-crit / on-hit ------------------------------ */

    /**
     * Крит-проки: bleed_on_crit / apply_poison (шанс = доля за ранг),
     * poison_extend / burning_extend (шанс → освежить DoT),
     * vendetta_refresh (шанс → продлить вендетту).
     */
    public void rollCritProcs(Player attacker, LivingEntity target, boolean wasCrit, boolean isMelee) {
        if (attacker == null || target == null || !wasCrit) {
            return;
        }
        UUID uuid = attacker.getUniqueId();

        if (isMelee && rollChance(proc(uuid, "bleed_on_crit"))) {
            plugin.getCombat().dots().applyById(attacker, target, "bleed");
        }
        if (isMelee && rollChance(proc(uuid, "apply_poison"))) {
            plugin.getCombat().dots().applyById(attacker, target, "poison");
        }
        if (isMelee && rollChance(proc(uuid, "poison_extend"))) {
            plugin.getCombat().dots().applyById(attacker, target, "poison"); // refresh
        }
        if (!isMelee && rollChance(proc(uuid, "burning_extend"))) {
            plugin.getCombat().dots().applyById(attacker, target, "burning"); // refresh
        }
        if (rollChance(proc(uuid, "vendetta_refresh"))) {
            RogueAbilities.refreshVendetta(target.getUniqueId());
        }
    }

    /** double_strike: шанс = доля за ранг; повторный удар без рекурсии (fromProc в CombatService). */
    public boolean rollDoubleStrike(Player attacker, LivingEntity target, Runnable strike) {
        if (attacker == null || target == null || strike == null) {
            return false;
        }
        if (!rollChance(proc(attacker.getUniqueId(), "double_strike"))) {
            return false;
        }
        strike.run();
        return true;
    }

    /**
     * crit_bonus / savage / headshot: для каждого — бросок шанса (доля за ранг);
     * при успехе crit-множитель умножается на (1 + amp).
     * Дефолты amp из описаний: crit_bonus +30%, savage +30%, headshot +100% (×2).
     * Вызывается ОДИН раз на крит (CombatService.meleeMult/spellMult, путь A и B).
     */
    public double critMultBonus(Player attacker) {
        if (attacker == null) {
            return 1.0;
        }
        UUID uuid = attacker.getUniqueId();
        double mult = 1.0;
        if (rollChance(proc(uuid, "crit_bonus"))) {
            mult *= 1.0 + amp("crit_bonus", 0.30);
        }
        if (rollChance(proc(uuid, "savage"))) {
            mult *= 1.0 + amp("savage", 0.30);
        }
        if (rollChance(proc(uuid, "headshot"))) {
            mult *= 1.0 + amp("headshot", 1.00);
        }
        return mult;
    }

    /* ------------------------------ stealth ------------------------------ */

    /** stealth_bonus: шанс = доля за ранг; при успехе ×(1+amp) урона из невидимости (дефолт +50%). */
    public double rollStealthBonus(Player attacker) {
        if (attacker == null) {
            return 1.0;
        }
        PotionEffect invis = attacker.getPotionEffect(PotionEffectType.INVISIBILITY);
        if (invis == null || invis.getDuration() <= 0) {
            return 1.0;
        }
        if (!rollChance(proc(attacker.getUniqueId(), "stealth_bonus"))) {
            return 1.0;
        }
        return 1.0 + amp("stealth_bonus", 0.50);
    }

    /** stealth_extend: шанс = доля за ранг; при успехе +amp секунд к shadow_cloak (дефолт +1 с). */
    public double getStealthExtendBonus(Player player) {
        if (player == null) {
            return 0.0;
        }
        if (!rollChance(proc(player.getUniqueId(), "stealth_extend"))) {
            return 0.0;
        }
        return amp("stealth_extend", 1.0);
    }

    /** armor_pen: шанс = доля за ранг; при успехе игнор amp резиста цели (дефолт 20%). */
    public double rollArmorPen(Player attacker, LivingEntity target) {
        if (attacker == null || target == null) {
            return 0.0;
        }
        PotionEffect invis = attacker.getPotionEffect(PotionEffectType.INVISIBILITY);
        if (invis == null || invis.getDuration() <= 0) {
            return 0.0;
        }
        if (!rollChance(proc(attacker.getUniqueId(), "armor_pen"))) {
            return 0.0;
        }
        return amp("armor_pen", 0.20);
    }

    /* ------------------------------ undodgeable ------------------------------ */

    /** undodgeable: шанс = доля за ранг; флаг на 5 с (следующий удар не уклоняется). */
    public void setUndodgeable(Player attacker) {
        if (attacker == null) {
            return;
        }
        if (!rollChance(proc(attacker.getUniqueId(), "undodgeable"))) {
            return;
        }
        undodgeableExpiry.put(attacker.getUniqueId(), System.currentTimeMillis() + 5_000L);
    }

    public boolean hasUndodgeable(Player attacker) {
        if (attacker == null) {
            return false;
        }
        Long exp = undodgeableExpiry.get(attacker.getUniqueId());
        if (exp == null || System.currentTimeMillis() > exp) {
            undodgeableExpiry.remove(attacker.getUniqueId());
            return false;
        }
        return true;
    }

    public void consumeUndodgeable(Player attacker) {
        if (attacker == null) {
            return;
        }
        undodgeableExpiry.remove(attacker.getUniqueId());
    }

    /* ------------------------------ cleanup ------------------------------ */

    public void purgeStale() {
        long now = System.currentTimeMillis();
        nextHitExpiry.entrySet().removeIf(e -> e.getValue() < now);
        nextHitMult.keySet().removeIf(id -> !nextHitExpiry.containsKey(id));
        exposeExpiry.entrySet().removeIf(e -> e.getValue() < now);
        exposeMult.keySet().removeIf(id -> !exposeExpiry.containsKey(id));
        secondWindCd.entrySet().removeIf(e -> e.getValue() < now);
        undodgeableExpiry.entrySet().removeIf(e -> e.getValue() < now);
    }
}
