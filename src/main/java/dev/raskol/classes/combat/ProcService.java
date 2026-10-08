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
 * 1.14.3 (Волна 3, 3C1+3C2): ЕДИНЫЙ фасад proc-узлов деревьев путей.
 *
 * 3C1 (мили-проки): riposte, counterattack, revenge, shield_slam, second_wind,
 *                    bleed_on_crit, apply_poison, double_strike, expose,
 *                    crit_bonus/savage/headshot (crit-mult bonus).
 * 3C2 (каст-проки и утилитарные): poison_extend, burning_extend, vendetta_refresh,
 *                    stealth_extend, stealth_bonus, reflect_magic, undodgeable,
 *                    armor_pen.
 *
 * Триггеры (вызываются из CombatService/путь B и VanillaDamageListener/путь A):
 *   onDefenderAvoid(defender, attacker, result) → proc_riposte / proc_counterattack
 *   onDefenderBlock(defender, attacker)         → proc_revenge / proc_shield_slam
 *   onDefenderDamaged(victim, formulaDamage)    → proc_second_wind (HP<35%, КД 30 с)
 *   onDefenderDamagedByMagic(victim, attacker, magicDamage) → proc_reflect_magic
 *   rollCritProcs(attacker, target, wasCrit, isMelee) → proc_bleed_on_crit /
 *                                                       proc_apply_poison /
 *                                                       proc_poison_extend /
 *                                                       proc_burning_extend /
 *                                                       proc_vendetta_refresh
 *   rollDoubleStrike(attacker, target, strike)  → proc_double_strike (recurse strike)
 *   applyExpose(attacker, target)               → proc_expose (+X% incoming damage, 6 с)
 *   rollStealthBonus(attacker)                  → proc_stealth_bonus (+50% урона из невидимости)
 *   onStealthBreak(player)                      → proc_stealth_extend (продление следующей невидимости)
 *   hasUndodgeable(attacker)                    → proc_undodgeable (следующий удар нельзя уклонить)
 *   rollArmorPen(attacker, target)              → proc_armor_pen (игнор брони из невидимости)
 *
 * Состояния (ConcurrentHashMap, lock-free чтения):
 *   nextHitMult/Expiry  — множитель следующего удара (riposte/revenge/shield_slam/counterattack)
 *   exposeExpiry/Mult   — дебафф цели (incoming damage %)
 *   secondWindCd        — per-player cooldown 30 с
 *   undodgeableExpiry   — флаг "следующий удар нельзя уклонить"
 *   stealthExtendBonus  — бонус к длительности следующей невидимости
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

    /** Флаг "следующий удар нельзя уклонить" (proc_undodgeable). */
    private final Map<UUID, Long> undodgeableExpiry = new ConcurrentHashMap<>();

    /** Бонус к длительности следующей невидимости (proc_stealth_extend, в секундах). */
    private final Map<UUID, Double> stealthExtendBonus = new ConcurrentHashMap<>();

    public ProcService(RaskolClasses plugin) {
        this.plugin = plugin;
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

    private void setAmplifier(Player attacker, double mult, long durationMs) {
        if (attacker == null || mult <= 1.0) {
            return;
        }
        UUID uuid = attacker.getUniqueId();
        long now = System.currentTimeMillis();
        Long prev = nextHitExpiry.get(uuid);
        Double cur = nextHitMult.get(uuid);
        double newMult = (cur != null && cur > 1.0 && prev != null && now <= prev)
                ? cur * mult
                : mult;
        nextHitMult.put(uuid, newMult);
        nextHitExpiry.put(uuid, now + durationMs);
    }

    /* ------------------------------ expose (incoming damage bonus) ------------------------------ */

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
        long durationMs = 6_000L;
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

        if (ThreadLocalRandom.current().nextDouble() >= proc / 100.0) {
            return;
        }

        double heal = maxHp * 0.08;
        plugin.getHpBarService().heal(p, heal, null);
        secondWindCd.put(uuid, now + 30_000L);
    }

    /**
     * 1.14.3 (3C2): proc_reflect_magic — отражение % магического урона обратно атакующему.
     * Вызывается из CombatService.dealDamage и VanillaDamageListener после применения маг-урона.
     * Процент отражения = procBonus("reflect_magic") (10% за ранг).
     */
    public void onDefenderDamagedByMagic(LivingEntity victim, Player attacker, double magicDamage) {
        if (!(victim instanceof Player defender) || attacker == null || magicDamage <= 0.0) {
            return;
        }
        Spec2Service svc = plugin.getSpec2Service();
        if (svc == null) {
            return;
        }
        double proc = svc.procBonus(defender.getUniqueId(), "reflect_magic");
        if (!Double.isFinite(proc) || proc <= 0.0) {
            return;
        }
        double reflect = magicDamage * (proc / 100.0);
        if (reflect <= 0.0) {
            return;
        }
        // Отражаем урон обратно атакующему (без рекурсии proc_reflect)
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

    /* ------------------------------ on-crit / on-hit triggers ------------------------------ */

    /**
     * 1.14.3 (3C2): расширен — добавлены proc_poison_extend, proc_burning_extend, proc_vendetta_refresh.
     */
    public void rollCritProcs(Player attacker, LivingEntity target, boolean wasCrit, boolean isMelee) {
        if (attacker == null || target == null || !wasCrit) {
            return;
        }
        Spec2Service svc = plugin.getSpec2Service();
        if (svc == null) {
            return;
        }
        UUID uuid = attacker.getUniqueId();

        // 3C1: bleed_on_crit (только мили)
        if (isMelee) {
            double bleedOnCrit = svc.procBonus(uuid, "bleed_on_crit");
            if (Double.isFinite(bleedOnCrit) && bleedOnCrit > 0.0) {
                plugin.getCombat().dots().applyById(attacker, target, "bleed");
            }
        }

        // 3C1: apply_poison (только мили, шанс)
        if (isMelee) {
            double applyPoison = svc.procBonus(uuid, "apply_poison");
            if (Double.isFinite(applyPoison) && applyPoison > 0.0) {
                if (ThreadLocalRandom.current().nextDouble() < applyPoison / 100.0) {
                    plugin.getCombat().dots().applyById(attacker, target, "poison");
                }
            }
        }

        // 3C2: poison_extend (только мили) — продлить poison DoT на +2 с за ранг
        if (isMelee) {
            double poisonExtend = svc.procBonus(uuid, "poison_extend");
            if (Double.isFinite(poisonExtend) && poisonExtend > 0.0) {
                rollDotExtend(attacker, target, "poison", poisonExtend);
            }
        }

        // 3C2: burning_extend (магия) — продлить burning DoT на +2 с за ранг
        if (!isMelee) {
            double burningExtend = svc.procBonus(uuid, "burning_extend");
            if (Double.isFinite(burningExtend) && burningExtend > 0.0) {
                rollDotExtend(attacker, target, "burning", burningExtend);
            }
        }

        // 3C2: vendetta_refresh — обновить вендетту на цели (продлить срок)
        double vendettaRefresh = svc.procBonus(uuid, "vendetta_refresh");
        if (Double.isFinite(vendettaRefresh) && vendettaRefresh > 0.0) {
            if (ThreadLocalRandom.current().nextDouble() < vendettaRefresh / 100.0) {
                RogueAbilities.refreshVendetta(target.getUniqueId());
            }
        }
    }

    /**
     * 3C2: продление DoT (poison_extend / burning_extend).
     * Если на цели есть соответствующий DoT от этого атакующего, обновляет его срок.
     */
    private void rollDotExtend(Player attacker, LivingEntity target, String dotId, double procPercent) {
        if (ThreadLocalRandom.current().nextDouble() >= procPercent / 100.0) {
            return;
        }
        // Применяем тот же DoT — DotService.apply продлевает срок, если DoT уже активен
        plugin.getCombat().dots().applyById(attacker, target, dotId);
    }

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

    /* ------------------------------ crit-mult bonus ------------------------------ */

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

    /* ------------------------------ 3C2: stealth procs ------------------------------ */

    /**
     * proc_stealth_bonus: +50% урона при ударе из невидимости за ранг.
     * Проверяет, был ли атакующий в невидимости (PotionEffect INVISIBILITY) до удара.
     */
    public double rollStealthBonus(Player attacker) {
        if (attacker == null) {
            return 1.0;
        }
        Spec2Service svc = plugin.getSpec2Service();
        if (svc == null) {
            return 1.0;
        }
        PotionEffect invis = attacker.getPotionEffect(PotionEffectType.INVISIBILITY);
        if (invis == null || invis.getDuration() <= 0) {
            return 1.0;
        }
        double proc = svc.procBonus(attacker.getUniqueId(), "stealth_bonus");
        if (!Double.isFinite(proc) || proc <= 0.0) {
            return 1.0;
        }
        return 1.0 + proc / 100.0;
    }

    /**
     * proc_stealth_extend: при выходе из невидимости записывает бонус к длительности
     * следующей невидимости (+1 с за ранг). Вызывается из RogueAbilities.shadowCloak
     * перед применением PotionEffect.
     */
    public double getStealthExtendBonus(Player player) {
        if (player == null) {
            return 0.0;
        }
        Spec2Service svc = plugin.getSpec2Service();
        if (svc == null) {
            return 0.0;
        }
        double proc = svc.procBonus(player.getUniqueId(), "stealth_extend");
        return Double.isFinite(proc) && proc > 0.0 ? proc : 0.0;
    }

    /* ------------------------------ 3C2: undodgeable / armor_pen ------------------------------ */

    /**
     * proc_undodgeable: при crit мили ставит флаг "следующий удар нельзя уклонить" на 5 с.
     * Вызывается из rollCritProcs после crit.
     */
    public void setUndodgeable(Player attacker) {
        if (attacker == null) {
            return;
        }
        Spec2Service svc = plugin.getSpec2Service();
        if (svc == null) {
            return;
        }
        double proc = svc.procBonus(attacker.getUniqueId(), "undodgeable");
        if (!Double.isFinite(proc) || proc <= 0.0) {
            return;
        }
        if (ThreadLocalRandom.current().nextDouble() >= proc / 100.0) {
            return;
        }
        undodgeableExpiry.put(attacker.getUniqueId(), System.currentTimeMillis() + 5_000L);
    }

    /**
     * Проверка флага undodgeable. Вызывается из AvoidanceService.tryAvoid.
     * Если флаг активен, уклонение невозможно (возвращает NONE).
     */
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

    /**
     * Сброс флага undodgeable после применения (вызывается из AvoidanceService после проверки).
     */
    public void consumeUndodgeable(Player attacker) {
        if (attacker == null) {
            return;
        }
        undodgeableExpiry.remove(attacker.getUniqueId());
    }

    /**
     * proc_armor_pen: при ударе из невидимости игнорирует % брони цели (20% за ранг).
     * Возвращает долю игнорируемой брони (0.2 = 20%).
     */
    public double rollArmorPen(Player attacker, LivingEntity target) {
        if (attacker == null || target == null) {
            return 0.0;
        }
        Spec2Service svc = plugin.getSpec2Service();
        if (svc == null) {
            return 0.0;
        }
        PotionEffect invis = attacker.getPotionEffect(PotionEffectType.INVISIBILITY);
        if (invis == null || invis.getDuration() <= 0) {
            return 0.0;
        }
        double proc = svc.procBonus(attacker.getUniqueId(), "armor_pen");
        if (!Double.isFinite(proc) || proc <= 0.0) {
            return 0.0;
        }
        return proc / 100.0;
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
