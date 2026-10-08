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
 * Семантика amplifier (исправлено в фикс-патче красного рана #1301):
 *   riposte (PARRY без щита), counterattack (DODGE), revenge/shield_slam (PARRY со щитом)
 *   усиливают СЛЕДУЮЩИЙ УДАР ЗАЩИЩАВШЕГОСЯ (defender), а не атакующего.
 *   Amplifier хранится на defender и снимается, когда defender сам наносит урон
 *   (CombatService.dealDamage / VanillaDamageListener.applyOutgoingOffense).
 *
 * Триггеры:
 *   onDefenderAvoid(defender, attacker, result) → riposte (PARRY) / counterattack (DODGE)
 *   onDefenderBlock(blocker, opponent)          → revenge / shield_slam
 *   onDefenderDamaged(victim, formulaDamage)    → second_wind (HP<35%, КД 30 с)
 *   onDefenderDamagedByMagic(victim, attacker, magicDamage) → reflect_magic
 *   rollCritProcs(attacker, target, wasCrit, isMelee) → bleed_on_crit / apply_poison /
 *                                                       poison_extend / burning_extend /
 *                                                       vendetta_refresh
 *   rollDoubleStrike(attacker, target, strike)  → double_strike (recurse, fromProc-гейт)
 *   applyExpose(attacker, target)               → expose (+X% incoming, 6 с)
 *   rollStealthBonus(attacker) / rollArmorPen(attacker, target) → stealth_bonus / armor_pen
 *   setUndodgeable(attacker) / hasUndodgeable / consumeUndodgeable → undodgeable
 *   getStealthExtendBonus(player)               → stealth_extend (секунды к shadow_cloak)
 *   critMultBonus(attacker)                     → crit_bonus / savage / headshot
 *
 * Все чтения безопасны к пустому агрегату spec2: procBonus=0 → множитель 1.0,
 * состояния пусты → чеки selftest 1–104 стабильны.
 */
public final class ProcService {

    /** Результат ролла уклонения/парирования (AvoidanceService.tryAvoid). */
    public enum AvoidResult { NONE, DODGE, PARRY }

    private final RaskolClasses plugin;

    /** Множитель следующего удара (ставится на ЗАЩИЩАВШЕГОСЯ). */
    private final Map<UUID, Double> nextHitMult = new ConcurrentHashMap<>();
    private final Map<UUID, Long> nextHitExpiry = new ConcurrentHashMap<>();

    /** Дебафф incoming damage на цели (proc_expose). */
    private final Map<UUID, Long> exposeExpiry = new ConcurrentHashMap<>();
    private final Map<UUID, Double> exposeMult = new ConcurrentHashMap<>();

    /** Cooldown proc_second_wind per player. */
    private final Map<UUID, Long> secondWindCd = new ConcurrentHashMap<>();

    /** Флаг «следующий удар нельзя уклонить» (proc_undodgeable). */
    private final Map<UUID, Long> undodgeableExpiry = new ConcurrentHashMap<>();

    public ProcService(RaskolClasses plugin) {
        this.plugin = plugin;
    }

    /* ------------------------------ amplifier (next-hit bonus) ------------------------------ */

    /** Текущий множитель следующего удара игрока (1.0 если нет активного прока). */
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

    /** Сброс amplifier после применения (одноразовый). */
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

    /**
     * PARRY → proc_riposte: защищавшийся получает +X% к следующему удару.
     * DODGE → proc_counterattack: уклонившийся получает +X% к следующему удару.
     * Amplifier ставится на DEFENDER (исправлено: ранее ошибочно на attacker).
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
                setAmplifier(defender, 1.0 + proc / 100.0, durationMs);
            }
        } else if (result == AvoidResult.DODGE) {
            double proc = svc.procBonus(duuid, "counterattack");
            if (Double.isFinite(proc) && proc > 0.0) {
                setAmplifier(defender, 1.0 + proc / 100.0, durationMs);
            }
        }
    }

    /**
     * Блок щитом (PARRY + щит): proc_revenge (гарант) и proc_shield_slam (шанс).
     * Amplifier ставится на BLOCKER (defender).
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
            setAmplifier(defender, 1.0 + revenge / 100.0, durationMs);
        }

        double shieldSlam = svc.procBonus(duuid, "shield_slam");
        if (Double.isFinite(shieldSlam) && shieldSlam > 0.0) {
            if (ThreadLocalRandom.current().nextDouble() < shieldSlam / 100.0) {
                setAmplifier(defender, 1.5, durationMs);
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
     * proc_reflect_magic: отражение % маг-урона обратно атакующему.
     * Дизайн: отражённый удар идёт через ванильный damage() под suppress —
     * резисты не применяет (чистое отражение), может быть уклонён (контр-плей),
     * beginReflect глушит рефлект-цепочки и откат чернокнижника.
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

    public void rollCritProcs(Player attacker, LivingEntity target, boolean wasCrit, boolean isMelee) {
        if (attacker == null || target == null || !wasCrit) {
            return;
        }
        Spec2Service svc = plugin.getSpec2Service();
        if (svc == null) {
            return;
        }
        UUID uuid = attacker.getUniqueId();

        if (isMelee) {
            double bleedOnCrit = svc.procBonus(uuid, "bleed_on_crit");
            if (Double.isFinite(bleedOnCrit) && bleedOnCrit > 0.0) {
                plugin.getCombat().dots().applyById(attacker, target, "bleed");
            }
        }

        if (isMelee) {
            double applyPoison = svc.procBonus(uuid, "apply_poison");
            if (Double.isFinite(applyPoison) && applyPoison > 0.0) {
                if (ThreadLocalRandom.current().nextDouble() < applyPoison / 100.0) {
                    plugin.getCombat().dots().applyById(attacker, target, "poison");
                }
            }
        }

        if (isMelee) {
            double poisonExtend = svc.procBonus(uuid, "poison_extend");
            if (Double.isFinite(poisonExtend) && poisonExtend > 0.0) {
                rollDotExtend(attacker, target, "poison", poisonExtend);
            }
        }

        if (!isMelee) {
            double burningExtend = svc.procBonus(uuid, "burning_extend");
            if (Double.isFinite(burningExtend) && burningExtend > 0.0) {
                rollDotExtend(attacker, target, "burning", burningExtend);
            }
        }

        double vendettaRefresh = svc.procBonus(uuid, "vendetta_refresh");
        if (Double.isFinite(vendettaRefresh) && vendettaRefresh > 0.0) {
            if (ThreadLocalRandom.current().nextDouble() < vendettaRefresh / 100.0) {
                RogueAbilities.refreshVendetta(target.getUniqueId());
            }
        }
    }

    /** Продление DoT: повторный applyById освежает срок активного стека. */
    private void rollDotExtend(Player attacker, LivingEntity target, String dotId, double procPercent) {
        if (ThreadLocalRandom.current().nextDouble() >= procPercent / 100.0) {
            return;
        }
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

    /* ------------------------------ stealth procs ------------------------------ */

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

    /* ------------------------------ undodgeable / armor_pen ------------------------------ */

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
