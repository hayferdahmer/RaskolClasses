// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.combat;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.spec.service.Spec2Service;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 1.14.3 (Волна 3, 3C1): ЕДИНЫЙ фасад proc-узлов деревьев путей.
 *
 * Триггеры (вызываются из CombatService/путь B и VanillaDamageListener/путь A):
 *   onDefenderAvoid(defender, attacker, result) → proc_riposte / proc_counterattack
 *   onDefenderBlock(defender, attacker)         → proc_revenge / proc_shield_slam
 *   onDefenderDamaged(victim, formulaDamage)    → proc_second_wind (HP<35%, КД 30 с)
 *   rollCritProcs(attacker, target, wasCrit, isMelee) → proc_bleed_on_crit /
 *                                                       proc_apply_poison /
 *                                                       proc_crit_bonus /
 *                                                       proc_savage /
 *                                                       proc_headshot
 *   rollDoubleStrike(attacker, target, strike)  → proc_double_strike (recurse strike)
 *   applyExpose(attacker, target)               → proc_expose (+X% incoming damage, 6 с)
 *
 * Состояния (ConcurrentHashMap, lock-free чтения):
 *   nextHitMult/Expiry  — множитель следующего удара (riposte/revenge/shield_slam/counterattack)
 *   exposeExpiry/Mult   — дебафф цели (incoming damage %)
 *   secondWindCd        — per-player cooldown 30 с
 *
 * Все чтения безопасны к пустому агрегату spec2: procBonus=0 → множитель 1.0,
 * next-hit не ставится, second_wind не срабатывает → чеки selftest 1–99 стабильны.
 */
public final class ProcService {

    /** Результат ролла уклонения/парирования (AvoidanceService.tryAvoid). */
    public enum AvoidResult { NONE, DODGE, PARRY }

    private final RaskolClasses plugin;

    /** Множитель следующего удара атакующего (proc_riposte и т.п.). */
    private final Map<UUID, Double> nextHitMult = new ConcurrentHashMap<>();
    private final Map<UUID, Long> nextHitExpiry = new ConcurrentHashMap<>();

    /** Дебафф incoming damage на цели (proc_expose). */
    private final Map<UUID, Long> exposeExpiry = new ConcurrentHashMap<>();
    private final Map<UUID, Double> exposeMult = new ConcurrentHashMap<>();

    /** Cooldown proc_second_wind per player. */
    private final Map<UUID, Long> secondWindCd = new ConcurrentHashMap<>();

    public ProcService(RaskolClasses plugin) {
        this.plugin = plugin;
    }

    /* ------------------------------ amplifier (next-hit bonus) ------------------------------ */

    /**
     * Текущий множитель следующего удара атакующего (1.0 если нет активного прока).
     * Автоматически сбрасывается, если срок истёк.
     */
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

    /** Сброс amplifier после применения (вызывает CombatService после первого удара). */
    public void consumeAmplifier(Player attacker) {
        if (attacker == null) {
            return;
        }
        UUID uuid = attacker.getUniqueId();
        nextHitExpiry.remove(uuid);
        nextHitMult.remove(uuid);
    }

    private void setAmplifier(Player attacker, double mult, long durationMs) {
        if (attacker == null || mult <= 1.0) {
            return;
        }
        UUID uuid = attacker.getUniqueId();
        long now = System.currentTimeMillis();
        Long prev = nextHitExpiry.get(uuid);
        // Стакаем множители мультипликативно (riposte × revenge → оба применяются)
        Double cur = nextHitMult.get(uuid);
        double newMult = (cur != null && cur > 1.0 && prev != null && now <= prev)
                ? cur * mult
                : mult;
        nextHitMult.put(uuid, newMult);
        nextHitExpiry.put(uuid, now + durationMs);
    }

    /* ------------------------------ expose (incoming damage bonus) ------------------------------ */

    /** Множитель входящего урона для цели (1.0 если нет дебаффа). */
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

    /**
     * proc_expose: цель получает +5% входящего урона на 6 с за ранг узла.
     * Повторное применение обновляет срок, множитель стакается аддитивно (не мультипликативно).
     */
    public void applyExpose(Player attacker, LivingEntity target) {
        if (attacker == null || target == null) {
            return;
        }
        Spec2Service svc = plugin.getSpec2Service();
        if (svc == null) {
            return;
        }
        double proc = svc.procBonus(attacker.getUniqueId(), "expose");
        if (!Double.isFinite(proc) || proc <= 0.0) {
            return;
        }
        UUID uuid = target.getUniqueId();
        long now = System.currentTimeMillis();
        long durationMs = 6_000L; // 6 с по дизайну
        Double cur = exposeMult.get(uuid);
        Long exp = exposeExpiry.get(uuid);
        double newMult;
        if (cur != null && cur > 1.0 && exp != null && now <= exp) {
            newMult = cur + proc / 100.0;
        } else {
            newMult = 1.0 + proc / 100.0;
        }
        exposeMult.put(uuid, newMult);
        exposeExpiry.put(uuid, now + durationMs);
    }

    /* ------------------------------ on-avoid / on-block triggers ------------------------------ */

    /**
     * Вызывается из AvoidanceService/пути A при успешном уклонении или парировании.
     * - PARRY → proc_riposte (+50% к следующему удару за ранг, 8 с)
     * - DODGE → proc_counterattack (+50% к следующему удару за ранг, 8 с)
     */
    public void onDefenderAvoid(Player defender, Player attacker, AvoidResult result) {
        if (defender == null || attacker == null || result == AvoidResult.NONE) {
            return;
        }
        Spec2Service svc = plugin.getSpec2Service();
        if (svc == null) {
            return;
        }
        UUID duuid = defender.getUniqueId();
        long durationMs = 8_000L;
        if (result == AvoidResult.PARRY) {
            double proc = svc.procBonus(duuid, "riposte");
            if (Double.isFinite(proc) && proc > 0.0) {
                setAmplifier(attacker, 1.0 + proc / 100.0, durationMs);
            }
        } else if (result == AvoidResult.DODGE) {
            double proc = svc.procBonus(duuid, "counterattack");
            if (Double.isFinite(proc) && proc > 0.0) {
                setAmplifier(attacker, 1.0 + proc / 100.0, durationMs);
            }
        }
    }

    /**
     * Вызывается из пути A при успешном блоке щитом.
     * - proc_revenge (guard): +10% к следующему удару за ранг, 8 с (гарантированно)
     * - proc_shield_slam (guard): +50% к следующему удару за ранг, 8 с (шанс 20% за ранг)
     */
    public void onDefenderBlock(Player defender, Player attacker) {
        if (defender == null || attacker == null) {
            return;
        }
        Spec2Service svc = plugin.getSpec2Service();
        if (svc == null) {
            return;
        }
        UUID duuid = defender.getUniqueId();
        long durationMs = 8_000L;

        double revenge = svc.procBonus(duuid, "revenge");
        if (Double.isFinite(revenge) && revenge > 0.0) {
            setAmplifier(attacker, 1.0 + revenge / 100.0, durationMs);
        }

        double shieldSlam = svc.procBonus(duuid, "shield_slam");
        if (Double.isFinite(shieldSlam) && shieldSlam > 0.0) {
            if (ThreadLocalRandom.current().nextDouble() < shieldSlam / 100.0) {
                setAmplifier(attacker, 1.5, durationMs);
            }
        }
    }

    /* ------------------------------ on-damaged triggers ------------------------------ */

    /**
     * proc_second_wind (arms T4): при HP<35% после получения урона → мгновенный хил 8%
     * формульного maxHP. КД 30 с. Вызывается из пути B (CombatService.dealDamage)
     * и пути A (VanillaDamageListener) после применения урона.
     */
    public void onDefenderDamaged(LivingEntity victim, double formulaDamage) {
        if (!(victim instanceof Player p) || formulaDamage <= 0.0) {
            return;
        }
        Spec2Service svc = plugin.getSpec2Service();
        if (svc == null) {
            return;
        }
        UUID uuid = p.getUniqueId();
        double proc = svc.procBonus(uuid, "second_wind");
        if (!Double.isFinite(proc) || proc <= 0.0) {
            return;
        }

        // Cooldown check
        Long cd = secondWindCd.get(uuid);
        long now = System.currentTimeMillis();
        if (cd != null && now < cd) {
            return;
        }

        // HP threshold: < 35% formula max
        double maxHp = plugin.getHpBarService().formulaMaxHp(uuid);
        double curHp = plugin.getHpBarService().currentFormulaHp(p);
        if (maxHp <= 0.0 || curHp / maxHp >= 0.35) {
            return;
        }

        // proc = percent chance per rank (10% за ранг в узле)
        if (ThreadLocalRandom.current().nextDouble() >= proc / 100.0) {
            return;
        }

        double heal = maxHp * 0.08; // 8% формульного maxHP
        plugin.getHpBarService().heal(p, heal, null);
        secondWindCd.put(uuid, now + 30_000L);
    }

    /* ------------------------------ on-crit / on-hit triggers ------------------------------ */

    /**
     * Вызывается из CombatService.dealDamage после крит-ролла.
     * - proc_bleed_on_crit (arms): crit мили → bleed DoT
     * - proc_apply_poison: crit мили → poison DoT
     * - proc_crit_bonus / proc_savage / proc_headshot → уже учтены в meleeMult/spellMult,
     *   здесь только визуал/VFX можно добавить (не делаем, чтобы не дублировать)
     */
    public void rollCritProcs(Player attacker, LivingEntity target, boolean wasCrit, boolean isMelee) {
        if (attacker == null || target == null || !wasCrit || !isMelee) {
            return;
        }
        Spec2Service svc = plugin.getSpec2Service();
        if (svc == null) {
            return;
        }
        UUID uuid = attacker.getUniqueId();

        double bleedOnCrit = svc.procBonus(uuid, "bleed_on_crit");
        if (Double.isFinite(bleedOnCrit) && bleedOnCrit > 0.0) {
            plugin.getCombat().dots().applyById(attacker, target, "bleed");
        }

        double applyPoison = svc.procBonus(uuid, "apply_poison");
        if (Double.isFinite(applyPoison) && applyPoison > 0.0) {
            if (ThreadLocalRandom.current().nextDouble() < applyPoison / 100.0) {
                plugin.getCombat().dots().applyById(attacker, target, "poison");
            }
        }
    }

    /**
     * proc_double_strike (fury T3): 10% шанс за ранг повторить удар.
     * Вызывается из CombatService.dealDamage ПОСЛЕ основного удара; если ролл
     * прошёл — вызывает strike.run() один раз. Защита от рекурсии — флаг fromProc
     * в CombatService.dealDamage (не вызывает rollDoubleStrike рекурсивно).
     */
    public boolean rollDoubleStrike(Player attacker, LivingEntity target, Runnable strike) {
        if (attacker == null || target == null || strike == null) {
            return false;
        }
        Spec2Service svc = plugin.getSpec2Service();
        if (svc == null) {
            return false;
        }
        double proc = svc.procBonus(attacker.getUniqueId(), "double_strike");
        if (!Double.isFinite(proc) || proc <= 0.0) {
            return false;
        }
        if (ThreadLocalRandom.current().nextDouble() >= proc / 100.0) {
            return false;
        }
        strike.run();
        return true;
    }

    /* ------------------------------ crit-mult bonus (читается из CombatService.meleeMult/spellMult) ------------------------------ */

    /**
     * proc_crit_bonus / proc_savage / proc_headshot: добавка к crit-множителю.
     * Возвращает мультипликативную добавку (1.0 = без изменений; 1.5 = +50% к crit-урону).
     * Вызывается из CombatService.meleeMult/spellMult.
     */
    public double critMultBonus(Player attacker) {
        if (attacker == null) {
            return 1.0;
        }
        Spec2Service svc = plugin.getSpec2Service();
        if (svc == null) {
            return 1.0;
        }
        UUID uuid = attacker.getUniqueId();
        double mult = 1.0;

        double critBonus = svc.procBonus(uuid, "crit_bonus");
        if (Double.isFinite(critBonus) && critBonus > 0.0) {
            mult *= (1.0 + critBonus / 100.0);
        }

        double savage = svc.procBonus(uuid, "savage");
        if (Double.isFinite(savage) && savage > 0.0) {
            mult *= (1.0 + savage / 100.0);
        }

        double headshot = svc.procBonus(uuid, "headshot");
        if (Double.isFinite(headshot) && headshot > 0.0) {
            mult *= (1.0 + headshot / 100.0);
        }

        return mult;
    }

    /** Плановая чистка протухших состояний (вызывать из purge-задачи). */
    public void purgeStale() {
        long now = System.currentTimeMillis();
        nextHitExpiry.entrySet().removeIf(e -> e.getValue() < now);
        nextHitMult.keySet().removeIf(id -> !nextHitExpiry.containsKey(id));
        exposeExpiry.entrySet().removeIf(e -> e.getValue() < now);
        exposeMult.keySet().removeIf(id -> !exposeExpiry.containsKey(id));
        secondWindCd.entrySet().removeIf(e -> e.getValue() < now);
    }
}
