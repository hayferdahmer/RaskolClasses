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
 * 1.14.6-fix (Sprint 1, P0-3): контракт единиц v1 — value = шанс за ранг,
 * сила из конфига spec2.procs.<id>.amp.
 * 1.14.7 (Sprint 3, P0-3 вариант C): контракт единиц v2 (финальный):
 *   - value  = ШАНС за ранг (доля 0..1), Σ по рангам, кламп [0,1];
 *     value = 1.0 у узла означает детерминированный триггер по условию
 *     (крит/порог HP/счётчик ударов) — бросок всегда успешен;
 *   - value2 = СИЛА за ранг, Σ по рангам (agg.procAmp); если Σ value2 == 0,
 *     сила берётся из конфига spec2.procs.<id>.amp (фиксированная из описаний);
 *   - один бросок rollChance(Σvalue) → применить (Σvalue2 ?: amp).
 *   Обратная совместимость: деревья без value2 работают как в Sprint 1.
 *
 * 1.14.6-fix (Sprint 1, P0-2): ключи agg.proc приходят БЕЗ префикса proc_
 * (срезается в Spec2Service.accumulate).
 *
 * 1.14.7 (Sprint 3, P0-6A): обработчики A2-проков Воина:
 *   - onAbilityCast → fury_battle_cry (ярость на каст);
 *   - onMeleeHitLand → battle_trance (счётчик 3-го удара);
 *   - enrageMult → fury_enrage (+% урона при HP<50%);
 *   - onCritBleed → deep_wounds (bleed при крите; TODO: кастомный dps через DotService).
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

    /** 1.14.7 (P0-6A): счётчик ударов для battle_trance (сбрасывается через 2 с). */
    private final Map<UUID, Integer> meleeHitCounter = new ConcurrentHashMap<>();
    private final Map<UUID, Long> meleeHitCounterExpiry = new ConcurrentHashMap<>();

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

    /* ------------------------------ чтение агрегата: шанс и сила (контракт C) ------------------------------ */

    private double proc(UUID uuid, String id) {
        Spec2Service svc = plugin.getSpec2Service();
        return svc == null ? 0.0 : svc.procBonus(uuid, id);
    }

    /** Σ value2 за ранги (сила из дерева); 0 если узел не несёт силу. */
    private double procAmp(UUID uuid, String id) {
        Spec2Service svc = plugin.getSpec2Service();
        return svc == null ? 0.0 : svc.procAmpBonus(uuid, id);
    }

    /**
     * Итоговая сила прока: Σvalue2 из дерева (масштабируется рангами),
     * иначе фиксированная из конфига spec2.procs.<id>.amp, иначе def.
     */
    private double strength(UUID uuid, String id, double defAmp) {
        double fromTree = procAmp(uuid, id);
        if (fromTree > 0.0) {
            return fromTree;
        }
        double v = plugin.getConfig().getDouble("spec2.procs." + id + ".amp", defAmp);
        return Double.isFinite(v) && v >= 0.0 ? v : defAmp;
    }

    /** Шанс срабатывания: Σvalue за рангам, кламп [0,1]. */
    private boolean roll(UUID uuid, String id) {
        return rollChance(proc(uuid, id));
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

    /* ------------------------------ expose (входящий урон цели) ------------------------------ */

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
     * Контракт C: шанс = Σvalue (sv_expose_weakness 0.20/ранг),
     * сила = Σvalue2 ?: spec2.procs.expose.amp (0.05 = +5% за применение),
     * суммарный кап = spec2.procs.expose.cap (0.30).
     */
    public void applyExpose(Player attacker, LivingEntity target) {
        if (attacker == null || target == null) {
            return;
        }
        if (!roll(attacker.getUniqueId(), "expose")) {
            return;
        }
        double add = strength(attacker.getUniqueId(), "expose", 0.05);
        if (add <= 0.0) {
            return;
        }
        double cap = plugin.getConfig().getDouble("spec2.procs.expose.cap", 0.30);
        cap = Double.isFinite(cap) && cap > 0.0 ? cap : 0.30;
        UUID uuid = target.getUniqueId();
        long now = System.currentTimeMillis();
        Double cur = exposeMult.get(uuid);
        Long exp = exposeExpiry.get(uuid);
        double newMult = (cur != null && cur > 1.0 && exp != null && now <= exp)
                ? Math.min(cur + add, 1.0 + cap)
                : 1.0 + add;
        exposeMult.put(uuid, newMult);
        exposeExpiry.put(uuid, now + 6_000L);
    }

    /* ------------------------------ on-avoid / on-block ------------------------------ */

    public void onDefenderAvoid(Player defender, Player attacker, AvoidResult result) {
        if (defender == null || attacker == null || result == AvoidResult.NONE) {
            return;
        }
        UUID duuid = defender.getUniqueId();
        long durationMs = 8_000L;
        if (result == AvoidResult.PARRY) {
            if (roll(duuid, "riposte")) {
                setAmplifier(defender, 1.0 + strength(duuid, "riposte", 0.50), durationMs);
            }
        } else if (result == AvoidResult.DODGE) {
            if (roll(duuid, "counterattack")) {
                setAmplifier(defender, 1.0 + strength(duuid, "counterattack", 0.50), durationMs);
            }
        }
    }

    public void onDefenderBlock(Player defender, Player attacker) {
        if (defender == null || attacker == null) {
            return;
        }
        UUID duuid = defender.getUniqueId();
        long durationMs = 8_000L;
        if (roll(duuid, "revenge")) {
            setAmplifier(defender, 1.0 + strength(duuid, "revenge", 0.10), durationMs);
        }
        if (roll(duuid, "shield_slam")) {
            setAmplifier(defender, 1.0 + strength(duuid, "shield_slam", 0.50), durationMs);
        }
    }

    /* ------------------------------ on-damaged ------------------------------ */

    public void onDefenderDamaged(LivingEntity victim, double formulaDamage) {
        if (!(victim instanceof Player p) || formulaDamage <= 0.0) {
            return;
        }
        UUID uuid = p.getUniqueId();
        if (!roll(uuid, "second_wind")) {
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
        double heal = maxHp * strength(uuid, "second_wind", 0.08);
        plugin.getHpBarService().heal(p, heal, null);
        secondWindCd.put(uuid, now + 30_000L);
    }

    public void onDefenderDamagedByMagic(LivingEntity victim, Player attacker, double magicDamage) {
        if (!(victim instanceof Player defender) || attacker == null || magicDamage <= 0.0) {
            return;
        }
        UUID duuid = defender.getUniqueId();
        if (!roll(duuid, "reflect_magic")) {
            return;
        }
        double reflect = magicDamage * strength(duuid, "reflect_magic", 0.20);
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

    public void rollCritProcs(Player attacker, LivingEntity target, boolean wasCrit, boolean isMelee) {
        if (attacker == null || target == null || !wasCrit) {
            return;
        }
        UUID uuid = attacker.getUniqueId();

        if (isMelee && roll(uuid, "bleed_on_crit")) {
            // 1.14.7 (P0-6A): deep_wounds — применяет стандартный bleed.
            // TODO (доставка 3): кастомный dps через DotService.applyBleedWithCustomDps
            // (procAmp 0.5 за ранг сейчас игнорируется).
            plugin.getCombat().dots().applyById(attacker, target, "bleed");
        }
        if (isMelee && roll(uuid, "apply_poison")) {
            plugin.getCombat().dots().applyById(attacker, target, "poison");
        }
        if (isMelee && roll(uuid, "poison_extend")) {
            plugin.getCombat().dots().applyById(attacker, target, "poison"); // refresh
        }
        if (!isMelee && roll(uuid, "burning_extend")) {
            plugin.getCombat().dots().applyById(attacker, target, "burning"); // refresh
        }
        if (roll(uuid, "vendetta_refresh")) {
            RogueAbilities.refreshVendetta(target.getUniqueId());
        }
    }

    public boolean rollDoubleStrike(Player attacker, LivingEntity target, Runnable strike) {
        if (attacker == null || target == null || strike == null) {
            return false;
        }
        if (!roll(attacker.getUniqueId(), "double_strike")) {
            return false;
        }
        strike.run();
        return true;
    }

    /**
     * crit_bonus / savage / headshot: для каждого — бросок шанса (Σvalue);
     * при успехе crit-множитель умножается на (1 + сила), где сила = Σvalue2 ?: amp.
     * Вызывается ОДИН раз на крит (CombatService.meleeMult/spellMult).
     */
    public double critMultBonus(Player attacker) {
        if (attacker == null) {
            return 1.0;
        }
        UUID uuid = attacker.getUniqueId();
        double mult = 1.0;
        if (roll(uuid, "crit_bonus")) {
            mult *= 1.0 + strength(uuid, "crit_bonus", 0.30);
        }
        if (roll(uuid, "savage")) {
            mult *= 1.0 + strength(uuid, "savage", 0.30);
        }
        if (roll(uuid, "headshot")) {
            mult *= 1.0 + strength(uuid, "headshot", 1.00);
        }
        return mult;
    }

    /* ------------------------------ stealth ------------------------------ */

    public double rollStealthBonus(Player attacker) {
        if (attacker == null) {
            return 1.0;
        }
        PotionEffect invis = attacker.getPotionEffect(PotionEffectType.INVISIBILITY);
        if (invis == null || invis.getDuration() <= 0) {
            return 1.0;
        }
        if (!roll(attacker.getUniqueId(), "stealth_bonus")) {
            return 1.0;
        }
        return 1.0 + strength(attacker.getUniqueId(), "stealth_bonus", 0.50);
    }

    public double getStealthExtendBonus(Player player) {
        if (player == null) {
            return 0.0;
        }
        if (!roll(player.getUniqueId(), "stealth_extend")) {
            return 0.0;
        }
        return strength(player.getUniqueId(), "stealth_extend", 1.0);
    }

    public double rollArmorPen(Player attacker, LivingEntity target) {
        if (attacker == null || target == null) {
            return 0.0;
        }
        PotionEffect invis = attacker.getPotionEffect(PotionEffectType.INVISIBILITY);
        if (invis == null || invis.getDuration() <= 0) {
            return 0.0;
        }
        if (!roll(attacker.getUniqueId(), "armor_pen")) {
            return 0.0;
        }
        return strength(attacker.getUniqueId(), "armor_pen", 0.20);
    }

    /* ------------------------------ undodgeable ------------------------------ */

    public void setUndodgeable(Player attacker) {
        if (attacker == null) {
            return;
        }
        if (!roll(attacker.getUniqueId(), "undodgeable")) {
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

    /* ------------------------------ 1.14.7 (P0-6A): A2 Воина ------------------------------ */

    /**
     * A2: fury_battle_cry — при успешном касте с шансом Σvalue даёт +Σvalue2 ярости.
     * Вызывается из AbilityRegistry.castOn после успешного каста.
     */
    public void onAbilityCast(Player caster) {
        if (caster == null) {
            return;
        }
        UUID uuid = caster.getUniqueId();
        if (!roll(uuid, "rage_on_cast")) {
            return;
        }
        double rage = strength(uuid, "rage_on_cast", 3.0);
        plugin.getResources().add(uuid, rage);
    }

    /**
     * A2: battle_trance — счётчик ударов. На 3-м ударе за 2-секундное окно
     * детерминированно (value=1.0) даёт +Σvalue2 ярости. Сброс счётчика после триггера.
     * Вызывается из CombatService.dealDamage для каждого мили-удара.
     */
    public void onMeleeHitLand(Player attacker, LivingEntity target) {
        if (attacker == null || target == null) {
            return;
        }
        UUID uuid = attacker.getUniqueId();
        long now = System.currentTimeMillis();
        Long exp = meleeHitCounterExpiry.get(uuid);
        int count = (exp != null && now <= exp) ? meleeHitCounter.getOrDefault(uuid, 0) : 0;
        count++;
        meleeHitCounter.put(uuid, count);
        meleeHitCounterExpiry.put(uuid, now + 2_000L);  // окно 2 секунды
        if (count >= 3) {
            double chance = proc(uuid, "trance");
            if (chance >= 1.0 || rollChance(chance)) {
                double rage = strength(uuid, "trance", 5.0);
                plugin.getResources().add(uuid, rage);
            }
            meleeHitCounter.put(uuid, 0);
            meleeHitCounterExpiry.put(uuid, now + 2_000L);
        }
    }

    /**
     * A2: fury_enrage — множитель физ-урона при HP<50%.
     * Возвращает 1.0 + Σvalue2/100 если HP<50% и roll прошёл (или value=1.0), иначе 1.0.
     * Вызывается из CombatService.dealDamage перед применением урона.
     */
    public double enrageMult(Player attacker) {
        if (attacker == null) {
            return 1.0;
        }
        UUID uuid = attacker.getUniqueId();
        double chance = proc(uuid, "enrage_dmg");
        if (chance <= 0.0) {
            return 1.0;
        }
        double max = plugin.getHpBarService().formulaMaxHp(uuid);
        double cur = plugin.getHpBarService().currentFormulaHp(attacker);
        if (max <= 0.0 || cur / max >= 0.50) {
            return 1.0;
        }
        if (chance < 1.0 && !rollChance(chance)) {
            return 1.0;
        }
        double pct = strength(uuid, "enrage_dmg", 5.0);
        return 1.0 + pct / 100.0;
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
        meleeHitCounterExpiry.entrySet().removeIf(e -> e.getValue() < now);
        meleeHitCounter.keySet().removeIf(id -> !meleeHitCounterExpiry.containsKey(id));
    }
}
