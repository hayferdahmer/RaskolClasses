// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.balance;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.attribute.AttributeMath;
import dev.raskol.classes.attribute.PowerService;
import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.combat.CombatService;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumMap;
import java.util.Map;
import java.util.Random;

/**
 * 1.7.6: HEADLESS-СИМУЛЯТОР ДУЭЛЕЙ (баланс-харнесс, вариант A).
 * Модель: два виртуальных игрока уровня level; атрибуты/HP/WP/SP/HPow/резисты/
 * avoidance/криты/STR-реген/анти-ваншот/burst-окно — ТЕ ЖЕ формулы, что в бою.
 * 1.7.6.3: прибавка ресурса за попадание/получение урона читается из конфига
 * по классу (resource-on-deal / resource-on-take, кап 1 раз/с) вместо хардкода
 * воина — охотник больше не голодает в длинных дуэлях.
 * Упрощения (документированы): авто-атака каждые 0.8 с, атака всегда во фронт,
 * защитник держит мили-оружие, утилити-абилки без эффекта, ресурс 100 на старте,
 * лимит дуэли 60 с (timeout = «не убивает»). Детерминированность: seed → Random.
 */
public final class BalanceSimulator {

    /** Результат дуэли: победитель, время до смерти проигравшего, флаги и счётчики. */
    public record DuelResult(PlayerClass winner, double ttkSeconds, boolean timeout,
                             int castsA, int castsB, int dodgesA, int dodgesB) {
    }

    private static final double TICK = 0.1;
    private static final double LIMIT = 60.0;
    private static final double AUTO_INTERVAL = 0.8;
    private static final double BERSERK_MULT = 1.25;
    private static final double MARK_MULT = 1.10;

    /** База ванильного оружия для авто-атаки (допущение симулятора). */
    private static final Map<PlayerClass, Double> WEAPON_BASE = Map.of(
            PlayerClass.WARRIOR, 7.0,
            PlayerClass.HUNTER, 6.0,
            PlayerClass.PRIEST, 5.0,
            PlayerClass.MAGE, 5.0,
            PlayerClass.ROGUE, 6.0);

    /** Порядок абилок = слотам 1–5 (совпадает с AbilityRegistry DEFAULTS). */
    private static final Map<PlayerClass, String[]> IDS = new EnumMap<>(PlayerClass.class);

    static {
        IDS.put(PlayerClass.WARRIOR, new String[]{
                "tyr_strike", "balder_skin", "berserkergang", "fenrir_blood", "ragnarok"});
        IDS.put(PlayerClass.HUNTER, new String[]{
                "wolf_mark", "swallow", "piercing_shot", "arrow_fan", "arrow_rain"});
        IDS.put(PlayerClass.PRIEST, new String[]{
                "saint_tear", "word_of_life", "aegis_faith", "circle_elysium", "wrath_heaven"});
        IDS.put(PlayerClass.MAGE, new String[]{
                "fire_prometheus", "hermes_step", "boreas_breath", "athena_aegis", "zeus_wrath"});
        IDS.put(PlayerClass.ROGUE, new String[]{
                "shadow_cloak", "blade_fan", "strangle", "borgia_poison", "shadow_dance"});
    }

    /** Числа способности: base, coeff, cooldown, cost, duration, threshold, execute-mult. */
    private record Ab(double base, double coeff, double cd, double cost,
                      double duration, double threshold, double exec) {
    }

    private static final Map<String, Ab> DEFAULT_AB = Map.ofEntries(
            Map.entry("tyr_strike", new Ab(10, 0.6, 8, 20, 0, 1, 1)),
            Map.entry("balder_skin", new Ab(15, 0.05, 30, 25, 5, 1, 1)),
            Map.entry("berserkergang", new Ab(0, 0, 45, 35, 6, 1, 1)),
            Map.entry("fenrir_blood", new Ab(15, 0.5, 25, 30, 0, 1, 1)),
            Map.entry("ragnarok", new Ab(20, 1.8, 60, 60, 0, 0.25, 3)),
            Map.entry("wolf_mark", new Ab(8, 0.5, 12, 20, 6, 1, 1)),
            Map.entry("swallow", new Ab(0, 0, 40, 15, 8, 1, 1)),
            Map.entry("piercing_shot", new Ab(12, 1.4, 20, 30, 0, 1, 1)),
            Map.entry("arrow_fan", new Ab(6, 0.35, 22, 35, 0, 1, 1)),
            Map.entry("arrow_rain", new Ab(10, 0.9, 90, 60, 0, 1, 1)),
            Map.entry("saint_tear", new Ab(10, 0.35, 3, 10, 0, 1, 1)),
            Map.entry("word_of_life", new Ab(20, 0.6, 6, 20, 0, 1, 1)),
            Map.entry("aegis_faith", new Ab(12, 0.04, 30, 30, 5, 1, 1)),
            Map.entry("circle_elysium", new Ab(15, 0.45, 60, 50, 0, 1, 1)),
            Map.entry("wrath_heaven", new Ab(20, 1.6, 90, 60, 0, 0.25, 3)),
            Map.entry("fire_prometheus", new Ab(15, 1.2, 6, 15, 0, 1, 1)),
            Map.entry("hermes_step", new Ab(0, 0, 20, 20, 0, 1, 1)),
            Map.entry("boreas_breath", new Ab(12, 1.0, 45, 40, 4, 1, 1)),
            Map.entry("athena_aegis", new Ab(15, 0.05, 30, 30, 5, 1, 1)),
            Map.entry("zeus_wrath", new Ab(25, 2.0, 90, 60, 0, 0.25, 3)),
            Map.entry("shadow_cloak", new Ab(0, 0, 30, 30, 15, 1, 1)),
            Map.entry("blade_fan", new Ab(8, 0.8, 15, 25, 0, 1, 1)),
            Map.entry("strangle", new Ab(10, 0.9, 40, 40, 0, 1, 1)),
            Map.entry("borgia_poison", new Ab(5, 0.3, 30, 35, 0, 1, 1)),
            Map.entry("shadow_dance", new Ab(30, 0, 120, 60, 4, 1, 1)));

    private BalanceSimulator() {
    }

    /* ------------------------------ публичный API ------------------------------ */

    /** Дуэль A против B; ttkSeconds = время до смерти проигравшего (LIMIT = timeout). */
    public static DuelResult duel(RaskolClasses plugin, PlayerClass ca, PlayerClass cb,
                                  int level, long seed) {
        Random rnd = new Random(seed);
        Fighter a = newFighter(plugin, ca, level);
        Fighter b = newFighter(plugin, cb, level);
        double pctCap = cfg(plugin, "combat.max-single-hit-pct", 35.0);
        double t = 0.0;
        while (t <= LIMIT) {
            step(plugin, a, t);
            step(plugin, b, t);
            recomputeAvoidance(plugin, a, t);
            recomputeAvoidance(plugin, b, t);
            act(plugin, a, b, t, rnd, pctCap);
            if (b.hp <= 0.0) {
                return new DuelResult(a.pc, t, false, a.casts, b.casts, a.dodges, b.dodges);
            }
            act(plugin, b, a, t, rnd, pctCap);
            if (a.hp <= 0.0) {
                return new DuelResult(b.pc, t, false, a.casts, b.casts, a.dodges, b.dodges);
            }
            t += TICK;
        }
        PlayerClass leader = a.hp == b.hp ? null : (a.hp > b.hp ? a.pc : b.pc);
        return new DuelResult(leader, LIMIT, true, a.casts, b.casts, a.dodges, b.dodges);
    }

    /**
     * Матрица TTK: m[i][j] = секунды, за которые класс i убивает класс j в дуэли.
     * POSITIVE_INFINITY = i не убил j за 60 с (или сам погиб первым).
     */
    public static double[][] matrix(RaskolClasses plugin, int level, long seed) {
        PlayerClass[] pcs = PlayerClass.values();
        double[][] m = new double[pcs.length][pcs.length];
        for (int i = 0; i < pcs.length; i++) {
            for (int j = 0; j < pcs.length; j++) {
                DuelResult r = duel(plugin, pcs[i], pcs[j], level, seed);
                m[i][j] = (!r.timeout() && r.winner() == pcs[i])
                        ? r.ttkSeconds()
                        : Double.POSITIVE_INFINITY;
            }
        }
        return m;
    }

    /* -------------------------------- модель -------------------------------- */

    private static final class Fighter {
        PlayerClass pc;
        double hp, maxHp, resource;
        double str, agi0, int0;
        double wp, sp, hpow;
        double dodgeEff, parryEff;
        double resistPhysBase, resistMagicBase;
        double grantPhys, grantMagic, grantUntil;
        double dmgMult = 1.0, dmgMultUntil;
        double markMult = 1.0, markUntil;
        double agiBonus, agiBonusUntil;
        final double[] cd = new double[5];
        double nextAuto = 0.0;
        double lastGainDeal = -10.0;
        double lastGainTake = -10.0;
        int casts, dodges;
        /** Журнал урона по burst-окну: [simTime, amount]. */
        final Deque<double[]> recent = new ArrayDeque<>();
    }

    private static double cfg(RaskolClasses plugin, String path, double def) {
        double v = plugin.getConfig().getDouble(path, def);
        return Double.isFinite(v) ? v : def;
    }

    private static final Map<PlayerClass, double[]> BASES = new EnumMap<>(PlayerClass.class);
    private static final Map<PlayerClass, double[]> GROWTHS = new EnumMap<>(PlayerClass.class);

    static {
        BASES.put(PlayerClass.WARRIOR, new double[]{12, 6, 4});
        BASES.put(PlayerClass.HUNTER, new double[]{6, 12, 4});
        BASES.put(PlayerClass.PRIEST, new double[]{5, 5, 12});
        BASES.put(PlayerClass.MAGE, new double[]{4, 6, 12});
        BASES.put(PlayerClass.ROGUE, new double[]{7, 11, 4});
        GROWTHS.put(PlayerClass.WARRIOR, new double[]{1.2, 0.5, 0.3});
        GROWTHS.put(PlayerClass.HUNTER, new double[]{0.5, 1.2, 0.3});
        GROWTHS.put(PlayerClass.PRIEST, new double[]{0.4, 0.4, 1.2});
        GROWTHS.put(PlayerClass.MAGE, new double[]{0.3, 0.5, 1.2});
        GROWTHS.put(PlayerClass.ROGUE, new double[]{0.6, 1.1, 0.3});
    }

    private static double attrBase(RaskolClasses plugin, PlayerClass pc, int idx) {
        String[] names = {"str", "agi", "int"};
        return cfg(plugin, "attributes.classes." + pc.name() + ".base-" + names[idx],
                BASES.get(pc)[idx]);
    }

    private static double attrGrowth(RaskolClasses plugin, PlayerClass pc, int idx) {
        String[] names = {"str", "agi", "int"};
        return cfg(plugin, "attributes.classes." + pc.name() + ".growth-" + names[idx],
                GROWTHS.get(pc)[idx]);
    }

    private static boolean mainIsAgi(RaskolClasses plugin, PlayerClass pc) {
        String m = plugin.getConfig().getString(
                "attributes.classes." + pc.name() + ".main",
                pc == PlayerClass.WARRIOR ? "STR"
                        : (pc == PlayerClass.HUNTER || pc == PlayerClass.ROGUE) ? "AGI" : "INT");
        return "AGI".equalsIgnoreCase(m);
    }

    private static Fighter newFighter(RaskolClasses plugin, PlayerClass pc, int level) {
        Fighter f = new Fighter();
        f.pc = pc;
        f.str = attrBase(plugin, pc, 0) + attrGrowth(plugin, pc, 0) * level;
        f.agi0 = attrBase(plugin, pc, 1) + attrGrowth(plugin, pc, 1) * level;
        f.int0 = attrBase(plugin, pc, 2) + attrGrowth(plugin, pc, 2) * level;
        f.maxHp = cfg(plugin, "attributes.hp.base-hp", 100.0)
                + f.str * cfg(plugin, "attributes.hp.per-str", 20.0);
        f.hp = f.maxHp;
        f.resource = 100.0;
        f.wp = PowerService.weaponPowerFormula(
                cfg(plugin, "attributes.power.base-wp." + pc.name(), defaultWp(pc)),
                f.str, f.agi0,
                cfg(plugin, "attributes.power.str-to-wp", 1.5),
                cfg(plugin, "attributes.power.agi-to-wp", 0.5));
        f.sp = PowerService.spellPowerFormula(
                cfg(plugin, "attributes.power.base-sp." + pc.name(), defaultSp(pc)),
                f.int0,
                cfg(plugin, "attributes.power.int-to-sp", 1.5));
        f.hpow = PowerService.healPowerFormula(
                cfg(plugin, "attributes.power.base-hpow." + pc.name(), defaultHp(pc)),
                f.int0,
                cfg(plugin, "attributes.power.int-to-hpow", 1.4));
        f.resistPhysBase = cfg(plugin, "resist.classes." + pc.name() + ".physical", 0.0);
        f.resistMagicBase = cfg(plugin, "resist.classes." + pc.name() + ".magic", 0.0);
        return f;
    }

    private static double defaultWp(PlayerClass pc) {
        return switch (pc) {
            case WARRIOR -> 30; case HUNTER -> 35; case ROGUE -> 30; case MAGE -> 5; case PRIEST -> 10;
        };
    }

    private static double defaultSp(PlayerClass pc) {
        return switch (pc) {
            case WARRIOR, HUNTER, ROGUE -> 5; case MAGE -> 30; case PRIEST -> 25;
        };
    }

    private static double defaultHp(PlayerClass pc) {
        return switch (pc) {
            case WARRIOR, HUNTER, ROGUE -> 0; case MAGE -> 15; case PRIEST -> 25;
        };
    }

    private static double agiOf(Fighter f, double t) {
        return f.agi0 + (t < f.agiBonusUntil ? f.agiBonus : 0.0);
    }

    private static void recomputeAvoidance(RaskolClasses plugin, Fighter f, double t) {
        double agi = agiOf(f, t);
        double dodge = AttributeMath.dodgeRaw(agi, cfg(plugin, "avoidance.dodge-k", 100.0));
        double parryFull = AttributeMath.parryRaw(f.str, cfg(plugin, "avoidance.parry-k", 300.0));
        double parry;
        if (mainIsAgi(plugin, f.pc)) {
            double micro = cfg(plugin, "avoidance.agi-main-parry-micro", 0.5);
            dodge += Math.max(0.0, parryFull - micro)
                    * cfg(plugin, "avoidance.agi-main-dodge-refund", 0.5);
            parry = micro;
        } else {
            parry = parryFull; // допущение: атака всегда во фронт, мили в руке
        }
        dodge *= cfg(plugin, "avoidance.dodge-mult", 0.5);
        double eff = AttributeMath.applyDR(dodge + parry,
                cfg(plugin, "avoidance.soft-cap", 60.0),
                cfg(plugin, "avoidance.dr-factor", 0.5),
                cfg(plugin, "avoidance.hard-cap", 75.0));
        double[] split = AttributeMath.splitEff(dodge, parry, eff);
        f.dodgeEff = split[0];
        f.parryEff = split[1];
    }

    private static void step(RaskolClasses plugin, Fighter f, double t) {
        double rate;
        switch (f.pc) {
            case PRIEST -> rate = cfg(plugin, "classes.PRIEST.resource-regen", 2.0);
            case ROGUE -> rate = cfg(plugin, "classes.ROGUE.resource-regen", 10.0);
            case MAGE -> {
                double v = f.resource;
                rate = v < 25 ? cfg(plugin, "classes.MAGE.regen-tier-1", 3.0)
                        : v < 50 ? cfg(plugin, "classes.MAGE.regen-tier-2", 4.0)
                        : v < 75 ? cfg(plugin, "classes.MAGE.regen-tier-3", 5.0)
                        : cfg(plugin, "classes.MAGE.regen-tier-4", 6.0);
            }
            default -> rate = cfg(plugin, "classes." + f.pc.name() + ".resource-regen", 0.0);
        }
        f.resource = Math.max(0.0, Math.min(100.0, f.resource + rate * TICK));
        double regen = AttributeMath.strRegenPerSecond(f.str,
                cfg(plugin, "attributes.hp.regen-per-str", 0.025),
                f.maxHp, true,
                cfg(plugin, "attributes.hp.regen-combat-factor", 0.35),
                cfg(plugin, "attributes.hp.regen-cap-pct", 1.5));
        f.hp = Math.min(f.maxHp, f.hp + regen * TICK);
    }

    private static Ab ab(RaskolClasses plugin, PlayerClass pc, String id) {
        Ab def = DEFAULT_AB.get(id);
        String p = "classes." + pc.name() + ".abilities." + id + ".";
        return new Ab(
                cfg(plugin, p + "base", def.base),
                cfg(plugin, p + "coeff", def.coeff),
                cfg(plugin, p + "cooldown", def.cd),
                cfg(plugin, p + "cost", def.cost),
                cfg(plugin, p + "duration", def.duration),
                cfg(plugin, p + "threshold", def.threshold),
                cfg(plugin, p + "execute-mult", def.exec));
    }

    private static void act(RaskolClasses plugin, Fighter f, Fighter foe,
                            double t, Random rnd, double pctCap) {
        String[] ids = IDS.get(f.pc);
        for (int slot = 0; slot < 5; slot++) {
            if (foe.hp <= 0.0) {
                return;
            }
            tryCast(plugin, f, foe, slot, ids[slot], t, rnd, pctCap);
        }
        if (t >= f.nextAuto && foe.hp > 0.0) {
            f.nextAuto = t + AUTO_INTERVAL;
            double dmg = WEAPON_BASE.get(f.pc)
                    + f.wp * cfg(plugin, "attributes.offense.basic-coeff", 0.35);
            double chance = AttributeMath.critMelee(agiOf(f, t),
                    cfg(plugin, "attributes.crit.melee-base", 5.0),
                    cfg(plugin, "attributes.crit.melee-per-agi", 0.05),
                    cfg(plugin, "attributes.crit.melee-cap", 40.0));
            if (rnd.nextDouble() * 100.0 < chance) {
                dmg *= cfg(plugin, "attributes.crit.melee-mult", 1.5);
            }
            hit(plugin, f, foe, dmg, 0.0, false, t, rnd, pctCap);
        }
    }

    private static void tryCast(RaskolClasses plugin, Fighter f, Fighter foe,
                                int slot, String id, double t, Random rnd, double pctCap) {
        if (t < f.cd[slot]) {
            return;
        }
        Ab a = ab(plugin, f.pc, id);
        if (f.resource < a.cost) {
            return;
        }
        boolean cast = false;
        switch (id) {
            case "tyr_strike", "piercing_shot", "strangle", "blade_fan", "arrow_rain" -> {
                hit(plugin, f, foe, a.base + f.wp * a.coeff, 0.0, false, t, rnd, pctCap);
                cast = true;
            }
            case "arrow_fan" -> {
                double dmg = a.base + f.wp * a.coeff;
                for (int i = 0; i < 3; i++) {
                    hit(plugin, f, foe, dmg, 0.0, false, t, rnd, pctCap);
                }
                cast = true;
            }
            case "fire_prometheus" -> {
                double total = a.base + f.sp * a.coeff;
                hit(plugin, f, foe, total * 0.3, total * 0.7, false, t, rnd, pctCap);
                cast = true;
            }
            case "boreas_breath" -> {
                hit(plugin, f, foe, 0.0, a.base + f.sp * a.coeff, false, t, rnd, pctCap);
                cast = true;
            }
            case "zeus_wrath", "wrath_heaven" -> {
                double dmg = a.base + f.sp * a.coeff;
                boolean exec = foe.hp / foe.maxHp < a.threshold;
                if (exec) {
                    dmg *= a.exec;
                }
                hit(plugin, f, foe, 0.0, dmg, exec, t, rnd, pctCap);
                cast = true;
            }
            case "saint_tear", "word_of_life", "circle_elysium" -> {
                if (f.hp < f.maxHp * 0.75) {
                    f.hp = Math.min(f.maxHp, f.hp + a.base + f.hpow * a.coeff);
                    cast = true;
                }
            }
            case "fenrir_blood" -> {
                if (f.hp < f.maxHp * 0.75) {
                    f.hp = Math.min(f.maxHp, f.hp + a.base + f.wp * a.coeff);
                    cast = true;
                }
            }
            case "balder_skin" -> {
                if (t >= f.grantUntil) {
                    f.grantPhys = a.base + f.wp * a.coeff;
                    f.grantMagic = 0.0;
                    f.grantUntil = t + a.duration;
                    cast = true;
                }
            }
            case "aegis_faith" -> {
                if (t >= f.grantUntil) {
                    double g = a.base + f.hpow * a.coeff;
                    f.grantPhys = g;
                    f.grantMagic = g;
                    f.grantUntil = t + a.duration;
                    cast = true;
                }
            }
            case "athena_aegis" -> {
                if (t >= f.grantUntil) {
                    f.grantMagic = a.base + f.sp * a.coeff;
                    f.grantPhys = 0.0;
                    f.grantUntil = t + a.duration;
                    cast = true;
                }
            }
            case "berserkergang" -> {
                if (t >= f.dmgMultUntil) {
                    f.dmgMult = BERSERK_MULT;
                    f.dmgMultUntil = t + a.duration;
                    cast = true;
                }
            }
            case "wolf_mark" -> {
                hit(plugin, f, foe, a.base + f.wp * a.coeff, 0.0, false, t, rnd, pctCap);
                foe.markMult = MARK_MULT;
                foe.markUntil = t + a.duration;
                cast = true;
            }
            case "shadow_dance" -> {
                if (t >= f.agiBonusUntil) {
                    f.agiBonus = a.base;
                    f.agiBonusUntil = t + a.duration;
                    cast = true;
                }
            }
            default -> {
                // swallow / hermes_step / shadow_cloak — утилити, в симуляции без эффекта
            }
        }
        if (cast) {
            f.cd[slot] = t + a.cd;
            f.resource = Math.max(0.0, f.resource - a.cost);
            f.casts++;
        }
    }

    /**
     * Burst-window cap внутри симуляции: суммарный урон за combat.burst-window-seconds
     * ≤ combat.burst-window-pct% maxHP. Execute-удары окно не читают и не пишут.
     */
    private static double applyBurstWindow(RaskolClasses plugin, Fighter def,
                                           double damage, double t, boolean execute) {
        if (execute || damage <= 0.0) {
            return damage;
        }
        double seconds = cfg(plugin, "combat.burst-window-seconds", 3.0);
        double pct = cfg(plugin, "combat.burst-window-pct", 18.0);
        if (seconds <= 0.0 || pct <= 0.0) {
            return damage;
        }
        double cap = def.maxHp * pct / 100.0;
        while (!def.recent.isEmpty() && t - def.recent.peekFirst()[0] > seconds) {
            def.recent.pollFirst();
        }
        double sum = 0.0;
        for (double[] e : def.recent) {
            sum += e[1];
        }
        double allowed = Math.max(0.0, cap - sum);
        double finalDmg = Math.min(damage, allowed);
        if (finalDmg > 0.0) {
            def.recent.addLast(new double[]{t, finalDmg});
        }
        return finalDmg;
    }

    private static void hit(RaskolClasses plugin, Fighter att, Fighter def,
                            double phys, double magic, boolean execute,
                            double t, Random rnd, double pctCap) {
        if (phys > 0.0) {
            double r = rnd.nextDouble() * 100.0;
            if (r < def.dodgeEff + def.parryEff) {
                def.dodges++;
                return;
            }
        }
        double rp = Math.min(90.0, def.resistPhysBase + (t < def.grantUntil ? def.grantPhys : 0.0));
        double rm = Math.min(90.0, def.resistMagicBase + (t < def.grantUntil ? def.grantMagic : 0.0));
        double p = phys * (1.0 - rp / 100.0);
        double m = magic * (1.0 - rm / 100.0);
        double mult = (t < att.dmgMultUntil ? att.dmgMult : 1.0)
                * (t < def.markUntil ? def.markMult : 1.0);
        double total = (p + m) * mult;
        if (!execute) {
            total = CombatService.cappedDamage(total, def.maxHp, pctCap);
        }
        total = applyBurstWindow(plugin, def, total, t, execute);
        def.hp -= total;
        // 1.7.6.3: прибавка ресурса за попадание/получение — из конфига по классу
        double onDeal = cfg(plugin, "classes." + att.pc.name() + ".resource-on-deal", 0.0);
        if (onDeal > 0.0 && t - att.lastGainDeal >= 1.0) {
            att.lastGainDeal = t;
            att.resource = Math.min(100.0, att.resource + onDeal);
        }
        double onTake = cfg(plugin, "classes." + def.pc.name() + ".resource-on-take", 0.0);
        if (onTake > 0.0 && t - def.lastGainTake >= 1.0) {
            def.lastGainTake = t;
            def.resource = Math.min(100.0, def.resource + onTake);
        }
    }
}
