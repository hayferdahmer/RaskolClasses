// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.selftest;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.ability.WarlockAbilities;
import dev.raskol.classes.ability.WarlockMath;
import dev.raskol.classes.attribute.AttributeMath;
import dev.raskol.classes.attribute.AttributeService;
import dev.raskol.classes.attribute.PowerService;
import dev.raskol.classes.balance.BalanceSimulator;
import dev.raskol.classes.cc.CCFeedback;
import dev.raskol.classes.cc.CCService;
import dev.raskol.classes.cc.CCType;
import dev.raskol.classes.cc.CastChannels;
import dev.raskol.classes.cc.DRCategory;
import dev.raskol.classes.cc.VanillaCCWrapper;
import dev.raskol.classes.classsystem.CharacterLevelService;
import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.combat.CombatMath;
import dev.raskol.classes.combat.CombatService;
import dev.raskol.classes.combat.DamageProfile;
import dev.raskol.classes.combat.DamageType;
import dev.raskol.classes.combat.dot.DotDef;
import dev.raskol.classes.combat.dot.DotInstance;
import dev.raskol.classes.combat.dot.DotMath;
import dev.raskol.classes.combat.dot.DotService;
import dev.raskol.classes.combat.school.PenTraitsService;
import dev.raskol.classes.combat.school.Penetration;
import dev.raskol.classes.combat.school.School;
import dev.raskol.classes.combat.school.SchoolConfig;
import dev.raskol.classes.combat.school.SchoolImmunity;
import dev.raskol.classes.combat.school.SchoolMitigation;
import dev.raskol.classes.combat.school.SchoolProfile;
import dev.raskol.classes.command.sub.CcSub;
import dev.raskol.classes.config.CcSanity;
import dev.raskol.classes.config.RaskolConfig;
import dev.raskol.classes.foliant.FoliantService;
import dev.raskol.classes.hook.GearHook;
import dev.raskol.classes.resource.ResourceState;
import dev.raskol.classes.spec.Spec;
import dev.raskol.classes.spec.SpecLegacyReset;
import dev.raskol.classes.spec.SpecMath;
import dev.raskol.classes.talent.TalentModel;
import dev.raskol.classes.talent.TalentsRegistry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Headless-самотестирование формул плагина (/rc selftest), 94 чека.
 * 1–16 атрибуты/бой; 17–18 TTK; 19–24 уровни/таланты/reconcile; 29–36 ресурсы/план B;
 * 37–48 чернокнижник/WarlockMath/sanity/SpecMath/loader; 49–65 школы 1.12.x;
 * 66–75 DoT 1.12.4–1.12.7; 76–90 CC/DR 1.13.0; 91–92 Spec 18+6 legacy;
 * 93–94 legacy-reset обнуление и идемпотентность (1.14.0 Б2).
 * 1.14.0-fix: resetAllDr (camelCase) вместо ошибочного resetAllDR.
 */
public final class SelftestRunner {

    private SelftestRunner() {
    }

    public static void run(RaskolClasses plugin, CommandSender sender) {
        int passed = 0;
        int failed = 0;
        StringBuilder report = new StringBuilder();

        double d1 = AttributeMath.dodgeRaw(0.0, 100.0);
        double d2 = AttributeMath.dodgeRaw(100.0, 100.0);
        double d3 = AttributeMath.dodgeRaw(300.0, 100.0);
        if (check(report, "1", "dodgeRaw(0,100)=0", d1 == 0.0, "dodgeRaw", d1)) passed++; else failed++;
        if (check(report, "2", "dodgeRaw(100,100)=50", d2 == 50.0, "dodgeRaw", d2)) passed++; else failed++;
        if (check(report, "3", "dodgeRaw(300,100)=75", d3 == 75.0, "dodgeRaw", d3)) passed++; else failed++;

        double p1 = AttributeMath.parryRaw(0.0, 150.0);
        double p2 = AttributeMath.parryRaw(150.0, 150.0);
        double p3 = AttributeMath.parryRaw(450.0, 150.0);
        if (check(report, "4", "parryRaw(0,150)=0", p1 == 0.0, "parryRaw", p1)) passed++; else failed++;
        if (check(report, "5", "parryRaw(150,150)=50", p2 == 50.0, "parryRaw", p2)) passed++; else failed++;
        if (check(report, "6", "parryRaw(450,150)=75", p3 == 75.0, "parryRaw", p3)) passed++; else failed++;

        double dr1 = AttributeMath.applyDR(0.0, 60.0, 0.5, 75.0);
        double dr2 = AttributeMath.applyDR(80.0, 60.0, 0.5, 75.0);
        double dr3 = AttributeMath.applyDR(200.0, 60.0, 0.5, 75.0);
        if (check(report, "7", "applyDR(0)=0", dr1 == 0.0, "applyDR", dr1)) passed++; else failed++;
        if (check(report, "8", "applyDR(80)=70", Math.abs(dr2 - 70.0) < 1e-6, "applyDR", dr2)) passed++; else failed++;
        if (check(report, "9", "applyDR(200)=75", dr3 == 75.0, "applyDR", dr3)) passed++; else failed++;

        double[] sp1 = AttributeMath.splitEff(50.0, 50.0, 50.0);
        boolean sp1ok = sp1 != null && sp1.length == 2 && sp1[0] == 25.0 && sp1[1] == 25.0;
        if (check(report, "10", "splitEff(50,50,50)=[25,25]", sp1ok, "splitEff",
                sp1 != null && sp1.length == 2 ? sp1[0] + "," + sp1[1] : "null")) passed++; else failed++;
        double[] sp2 = AttributeMath.splitEff(0.0, 100.0, 50.0);
        boolean sp2ok = sp2 != null && sp2.length == 2 && sp2[0] == 0.0 && sp2[1] == 50.0;
        if (check(report, "11", "splitEff(0,100,50)=[0,50]", sp2ok, "splitEff",
                sp2 != null && sp2.length == 2 ? sp2[0] + "," + sp2[1] : "null")) passed++; else failed++;

        boolean f1 = AttributeMath.isFront(0.0, 90.0);
        boolean f2 = AttributeMath.isFront(180.0, 90.0);
        boolean b1 = AttributeMath.isBack(180.0, 135.0);
        if (check(report, "12", "isFront(0,90)=true", f1, "isFront", f1)) passed++; else failed++;
        if (check(report, "13", "isFront(180,90)=false", !f2, "isFront", f2)) passed++; else failed++;
        if (check(report, "14", "isBack(180,135)=true", b1, "isBack", b1)) passed++; else failed++;

        double wpWarrior = PowerService.weaponPowerFormula(30.0, 60.0, 30.0, 1.5, 0.5);
        double spMage = PowerService.spellPowerFormula(30.0, 60.0, 1.5);
        double hpowPriest = PowerService.healPowerFormula(25.0, 60.0, 1.4);
        if (check(report, "15a", "WP(воин 40 ур.)=135", Math.abs(wpWarrior - 135.0) < 1e-6,
                "weaponPowerFormula", wpWarrior)) passed++; else failed++;
        if (check(report, "15b", "SP(маг 40 ур.)=120", Math.abs(spMage - 120.0) < 1e-6,
                "spellPowerFormula", spMage)) passed++; else failed++;
        if (check(report, "15c", "HPow(жрец 40 ур.)=109", Math.abs(hpowPriest - 109.0) < 1e-6,
                "healPowerFormula", hpowPriest)) passed++; else failed++;

        double c1 = CombatService.cappedDamage(9999.0, 1300.0, 35.0);
        double c2 = CombatService.cappedDamage(100.0, 1300.0, 35.0);
        double c3 = CombatService.cappedDamage(500.0, 1000.0, 0.0);
        if (check(report, "16a", "capped(9999,1300,35)=455", Math.abs(c1 - 455.0) < 1e-6,
                "cappedDamage", c1)) passed++; else failed++;
        if (check(report, "16b", "capped(100,1300,35)=100", c2 == 100.0,
                "cappedDamage", c2)) passed++; else failed++;
        if (check(report, "16c", "capped(500,1000,0)=500", c3 == 500.0,
                "cappedDamage", c3)) passed++; else failed++;

        BalanceSimulator.DuelResult ww = BalanceSimulator.duel(
                plugin, PlayerClass.WARRIOR, PlayerClass.WARRIOR, 40, 42L);
        boolean ok17 = !ww.timeout() && ww.ttkSeconds() >= 10.0 && ww.ttkSeconds() <= 60.0;
        if (check(report, "17", "TTK воин↔воин ∈ [10,60] с (якорь 20с)", ok17, "BalanceSimulator", ww.ttkSeconds())) {
            passed++;
        } else {
            failed++;
        }

        BalanceSimulator.DuelResult pp = BalanceSimulator.duel(
                plugin, PlayerClass.PRIEST, PlayerClass.PRIEST, 40, 42L);
        boolean ok18 = pp.timeout() || pp.ttkSeconds() >= 30.0;
        if (check(report, "18", "жрец↔жрец ≥30 с или timeout", ok18, "BalanceSimulator", pp.ttkSeconds())) {
            passed++;
        } else {
            failed++;
        }

        int cl1 = CharacterLevelService.topNAverage(new int[]{99, 70, 40, 20, 10, 5, 0}, 5);
        if (check(report, "19", "topNAverage([99..0],5)=47", cl1 == 47, "topNAverage", cl1)) {
            passed++;
        } else {
            failed++;
        }

        int cl2 = CharacterLevelService.topNAverage(new int[]{15, 0, 0, 0, 0}, 5);
        if (check(report, "20", "topNAverage([15,0,0,0,0],5)=3", cl2 == 3, "topNAverage", cl2)) {
            passed++;
        } else {
            failed++;
        }

        Player probe = sender instanceof Player sp
                ? sp
                : Bukkit.getOnlinePlayers().stream().findFirst().orElse(null);
        if (probe == null) {
            if (check(report, "21", "canHit: self/среда (пропущено)", true, "canHit", "skip")) {
                passed++;
            } else {
                failed++;
            }
        } else {
            boolean self = plugin.getCombat().canHit(probe, probe);
            boolean env = plugin.getCombat().canHit(null, probe);
            if (check(report, "21", "canHit: self=true и среда=true",
                    self && env, "canHit", self + "/" + env)) {
                passed++;
            } else {
                failed++;
            }
        }

        int e39 = TalentModel.earnedPoints(39, 40, 1, 21);
        int e40 = TalentModel.earnedPoints(40, 40, 1, 21);
        int e60 = TalentModel.earnedPoints(60, 40, 1, 21);
        int e99 = TalentModel.earnedPoints(99, 40, 1, 21);
        boolean ok22 = e39 == 0 && e40 == 1 && e60 == 21 && e99 == 21;
        if (check(report, "22", "очки талантов: 39→0, 40→1, 60→21, 99→21",
                ok22, "earnedPoints", e39 + "/" + e40 + "/" + e60 + "/" + e99)) {
            passed++;
        } else {
            failed++;
        }

        int cost = TalentModel.treeCost(List.of(
                TalentModel.node("t1a", 1), TalentModel.node("t1b", 1),
                TalentModel.node("t2a1", 2), TalentModel.node("t2a2", 2),
                TalentModel.node("t2b1", 2), TalentModel.node("t2b2", 2),
                TalentModel.node("t3a", 3), TalentModel.node("t3b", 3),
                TalentModel.node("t4", 5)));
        if (check(report, "23", "стоимость дерева талантов = 21", cost == 21,
                "treeCost", cost)) {
            passed++;
        } else {
            failed++;
        }

        if (probe == null) {
            if (check(report, "24", "reconcile-цикл (пропущено)", true, "reconcile", "skip")) {
                passed++;
            } else {
                failed++;
            }
        } else {
            boolean ok24 = false;
            String got24 = "no-tree";
            UUID probeUuid = probe.getUniqueId();
            Spec probeSpec = plugin.getSpecService().getSpec(probeUuid);
            if (probeSpec == null) {
                probeSpec = Spec.values()[0];
            }
            String specId = probeSpec.id();
            TalentModel.TalentTree tree = TalentsRegistry.treeOf(specId);
            if (tree != null) {
                TalentModel.TalentNode t1a = firstT1(tree);
                if (t1a != null) {
                    List<String> before = new ArrayList<>(
                            plugin.getTalentsStorage().getPurchased(probeUuid, specId));
                    boolean cycleOk;
                    try {
                        plugin.getTalentService()
                                .forcePurchaseForTest(probeUuid, specId, t1a.id());
                        boolean bought = plugin.getTalentsStorage()
                                .getPurchased(probeUuid, specId).contains(t1a.id());
                        plugin.getTalentsStorage().setPurchased(probeUuid, specId, before);
                        plugin.getTalentService().reconcile(probeUuid);
                        boolean restored = plugin.getTalentsStorage()
                                .getPurchased(probeUuid, specId).equals(before);
                        cycleOk = bought && restored;
                        got24 = bought + "/" + restored;
                    } catch (RuntimeException ex) {
                        cycleOk = false;
                        got24 = "exception: " + ex.getMessage();
                    }
                    ok24 = cycleOk;
                } else {
                    got24 = "no-t1a-node";
                }
            } else {
                got24 = "no-tree:" + specId;
            }
            if (check(report, "24", "reconcile-цикл: покупка→reconcile→откат",
                    ok24, "reconcile", got24)) {
                passed++;
            } else {
                failed++;
            }
        }

        ResourceState rsWindow = new ResourceState();
        boolean freshOut = !rsWindow.isInCombat(5000L);
        rsWindow.markCombat();
        boolean nowIn = rsWindow.isInCombat(5000L);
        if (check(report, "29", "боевое окно", freshOut && nowIn, "ResourceState", freshOut + "/" + nowIn)) {
            passed++;
        } else {
            failed++;
        }

        ResourceState rsConsume = new ResourceState();
        rsConsume.setValue(10.0);
        boolean overDenied = !rsConsume.consume(15.0);
        boolean overIntact = rsConsume.getValue() == 10.0;
        boolean exactOk = rsConsume.consume(10.0);
        boolean zeroed = rsConsume.getValue() == 0.0;
        boolean freeOk = rsConsume.consume(0.0);
        if (check(report, "30", "consume: сверх/точное/обнуление/0",
                overDenied && overIntact && exactOk && zeroed && freeOk,
                "ResourceState.consume",
                overDenied + "/" + overIntact + "/" + exactOk + "/" + zeroed + "/" + freeOk)) {
            passed++;
        } else {
            failed++;
        }

        if (probe == null) {
            if (check(report, "31", "глобальный бюджет (пропущено)", true, "spentGlobal", "skip")) {
                passed++;
            } else {
                failed++;
            }
        } else {
            boolean ok31 = false;
            String got31 = "need-2-specs";
            UUID pu = probe.getUniqueId();
            Spec[] all = Spec.values();
            if (all.length >= 2) {
                String specA = all[0].id();
                String specB = all[1].id();
                TalentModel.TalentTree treeA = TalentsRegistry.treeOf(specA);
                TalentModel.TalentTree treeB = TalentsRegistry.treeOf(specB);
                if (treeA != null && treeB != null) {
                    List<String> beforeA = new ArrayList<>(plugin.getTalentsStorage().getPurchased(pu, specA));
                    List<String> beforeB = new ArrayList<>(plugin.getTalentsStorage().getPurchased(pu, specB));
                    TalentModel.TalentNode nA = firstT1(treeA);
                    TalentModel.TalentNode nB = firstT1(treeB);
                    if (nA != null && nB != null) {
                        int earned = plugin.getTalentService().earnedPoints(pu);
                        plugin.getTalentService().forcePurchaseForTest(pu, specA, nA.id());
                        plugin.getTalentService().forcePurchaseForTest(pu, specB, nB.id());
                        int spent = plugin.getTalentService().spentGlobal(pu);
                        int availA = plugin.getTalentService().availablePoints(pu, specA);
                        int availB = plugin.getTalentService().availablePoints(pu, specB);
                        ok31 = availA == availB
                                && availA == Math.max(0, earned - spent)
                                && spent >= nA.cost() + nB.cost();
                        got31 = availA + "/" + availB + "/spent=" + spent;
                    } else {
                        got31 = "no-t1-nodes";
                    }
                    plugin.getTalentsStorage().setPurchased(pu, specA, beforeA);
                    plugin.getTalentsStorage().setPurchased(pu, specB, beforeB);
                    plugin.getTalentService().reconcile(pu);
                }
            }
            if (check(report, "31", "глобальный бюджет: 2 дерева съедают общий пул",
                    ok31, "spentGlobal", got31)) {
                passed++;
            } else {
                failed++;
            }
        }

        if (probe == null) {
            if (check(report, "32", "reconcile-прунинг (пропущено)", true, "validatePurchased", "skip")) {
                passed++;
            } else {
                failed++;
            }
        } else {
            boolean ok32 = false;
            String got32 = "no-tree";
            UUID pu = probe.getUniqueId();
            Spec spec = plugin.getSpecService().getSpec(pu);
            if (spec == null) {
                spec = Spec.values()[0];
            }
            TalentModel.TalentTree tree = TalentsRegistry.treeOf(spec.id());
            if (tree != null) {
                List<String> before = new ArrayList<>(plugin.getTalentsStorage().getPurchased(pu, spec.id()));
                plugin.getTalentsStorage().setPurchased(pu, spec.id(),
                        new ArrayList<>(List.of("nonexistent_node_xyz")));
                plugin.getTalentService().reconcile(pu);
                boolean prunedUnknown = plugin.getTalentsStorage().getPurchased(pu, spec.id()).isEmpty();

                TalentModel.TalentNode withPrereq = null;
                for (TalentModel.TalentNode n : tree.nodes()) {
                    if (!n.prereqs().isEmpty()) {
                        withPrereq = n;
                        break;
                    }
                }
                boolean prunedPrereq = true;
                if (withPrereq != null) {
                    plugin.getTalentsStorage().setPurchased(pu, spec.id(),
                            new ArrayList<>(List.of(withPrereq.id())));
                    plugin.getTalentService().reconcile(pu);
                    prunedPrereq = plugin.getTalentsStorage().getPurchased(pu, spec.id()).isEmpty();
                }
                plugin.getTalentsStorage().setPurchased(pu, spec.id(), before);
                plugin.getTalentService().reconcile(pu);
                ok32 = prunedUnknown && prunedPrereq;
                got32 = prunedUnknown + "/" + prunedPrereq;
            }
            if (check(report, "32", "reconcile-прунинг",
                    ok32, "validatePurchased", got32)) {
                passed++;
            } else {
                failed++;
            }
        }

        if (probe == null) {
            if (check(report, "33", "scale (пропущено)", true, "scale", "skip")) passed++; else failed++;
            if (check(report, "34", "healFormula (пропущено)", true, "healFormula", "skip")) passed++; else failed++;
            if (check(report, "35", "targetCarrier (пропущено)", true, "targetCarrier", "skip")) passed++; else failed++;
        } else {
            AttributeService attrs = plugin.getAttributes();
            UUID pu = probe.getUniqueId();
            double formula = attrs.maxHp(pu);
            double carrier = attrs.carrierMaxHp(probe);
            double scale = attrs.scale(probe);
            boolean ok33 = Math.abs(scale - carrier / formula) < 1e-6;
            if (check(report, "33", "scale = carrier/formula", ok33, "scale",
                    String.format(Locale.ROOT, "%.4f", scale))) {
                passed++;
            } else {
                failed++;
            }

            double hpBefore = probe.getHealth();
            attrs.healFormula(probe, formula * 0.5);
            double hpAfter = probe.getHealth();
            boolean ok34 = hpAfter <= carrier + 0.01;
            probe.setHealth(hpBefore);
            if (check(report, "34", "healFormula не превышает carrier", ok34, "healFormula",
                    String.format(Locale.ROOT, "%.1f→%.1f", hpBefore, hpAfter))) {
                passed++;
            } else {
                failed++;
            }

            double target = attrs.targetCarrier(pu);
            boolean ok35 = Math.abs(target - Math.min(formula, AttributeService.VANILLA_MAX_HEALTH_CAP)) < 1e-6;
            if (check(report, "35", "targetCarrier = min(formula, 1024)", ok35, "targetCarrier",
                    String.format(Locale.ROOT, "%.1f", target))) {
                passed++;
            } else {
                failed++;
            }
        }

        ResourceState rsDecay = new ResourceState();
        rsDecay.setValue(40.0);
        rsDecay.tickDelta(-5.0);
        boolean decayed = rsDecay.getValue() == 35.0;
        rsDecay.tickDelta(5.0);
        boolean gained = rsDecay.getValue() == 40.0;
        rsDecay.setValue(0.0);
        rsDecay.tickDelta(-5.0);
        boolean clampedLow = rsDecay.getValue() == 0.0;
        rsDecay.setValue(100.0);
        rsDecay.tickDelta(5.0);
        boolean clampedHigh = rsDecay.getValue() == 100.0;
        if (check(report, "36", "tickDelta: декэй/набор/клампы",
                decayed && gained && clampedLow && clampedHigh, "ResourceState.tickDelta",
                decayed + "/" + gained + "/" + clampedLow + "/" + clampedHigh)) {
            passed++;
        } else {
            failed++;
        }

        UUID stranger = UUID.randomUUID();
        boolean sealEmpty = WarlockAbilities.sealAmplifyOf(stranger) == 0.0;
        boolean antiEmpty = !WarlockAbilities.isAntihealed(stranger);
        boolean gatesMsgOk = true;
        int gateCodes = 0;
        for (FoliantService.TransitionResult r : FoliantService.TransitionResult.values()) {
            gateCodes++;
            String m = r.message(plugin);
            if (m == null || m.isEmpty()) {
                gatesMsgOk = false;
            }
        }
        boolean ok37 = sealEmpty && antiEmpty && gatesMsgOk && gateCodes == 9;
        if (check(report, "37", "чернокнижник: реестры пусты; 9 кодов гейтов",
                ok37, "WarlockAbilities/FoliantService",
                sealEmpty + "/" + antiEmpty + "/" + gatesMsgOk + "/" + gateCodes)) {
            passed++;
        } else {
            failed++;
        }

        RaskolConfig cfg = plugin.getRaskolConfig();
        double open = cfg.warlockThresholdOpen();
        double overflow = cfg.warlockThresholdOverflow();
        double recoil = cfg.warlockRecoilPercent();
        double recoilCap = cfg.warlockRecoilCapPct();
        double nether = cfg.warlockNetherMult();
        double decay = cfg.warlockResourceDecay();
        double onKill = cfg.warlockResourceOnKill();
        boolean ok38 = open > 0.0 && overflow > open
                && recoil > 0.0 && recoil <= 100.0
                && recoilCap > 0.0 && recoilCap <= 100.0
                && nether >= 1.0 && decay < 0.0 && onKill > 0.0;
        if (check(report, "38", "конфиг WARLOCK sanity",
                ok38, "RaskolConfig.warlock*",
                String.format(Locale.ROOT, "%.1f/%.1f/%.2f/%.1f/%.1f/%.1f/%.1f",
                        open, overflow, recoil, recoilCap, nether, decay, onKill))) {
            passed++;
        } else {
            failed++;
        }

        String got39;
        boolean ok39;
        try {
            BalanceSimulator.DuelResult wl = BalanceSimulator.duel(
                    plugin, PlayerClass.WARLOCK, PlayerClass.WARRIOR, 40, 42L);
            ok39 = wl.timeout() || (wl.ttkSeconds() >= 5.0 && wl.ttkSeconds() <= 60.0);
            got39 = wl.timeout() ? "timeout" : fmt(wl.ttkSeconds()) + "s";
        } catch (RuntimeException ex) {
            ok39 = false;
            got39 = "exception: " + ex.getClass().getSimpleName();
        }
        if (check(report, "39", "WARLOCK↔WARRIOR без падений",
                ok39, "BalanceSimulator", got39)) {
            passed++;
        } else {
            failed++;
        }

        String got40;
        boolean ok40;
        try {
            double[][] m6 = BalanceSimulator.matrix(plugin, 40, 42L);
            ok40 = m6.length == 6 && m6[0].length == 6;
            got40 = m6.length + "x" + (m6.length > 0 ? m6[0].length : 0);
        } catch (RuntimeException ex) {
            ok40 = false;
            got40 = "exception: " + ex.getClass().getSimpleName();
        }
        if (check(report, "40", "TTK-матрица 6×6", ok40, "BalanceSimulator.matrix", got40)) {
            passed++;
        } else {
            failed++;
        }

        double r1 = WarlockMath.recoilAmount(100.0, 6.66, 840.0, 30.0);
        double r2 = WarlockMath.recoilAmount(10000.0, 6.66, 840.0, 30.0);
        double rHp = WarlockMath.applyRecoil(5.0, 10.0, 1.0);
        boolean ok41 = Math.abs(r1 - 6.66) < 1e-6
                && Math.abs(r2 - 252.0) < 1e-6
                && rHp == 1.0;
        if (check(report, "41", "recoil: 6.66/252/minHp1",
                ok41, "WarlockMath.recoil",
                String.format(Locale.ROOT, "%.2f/%.1f/%.1f", r1, r2, rHp))) {
            passed++;
        } else {
            failed++;
        }

        double dH1 = WarlockMath.drainHeal(100.0, 0.666, 0.85);
        double dH2 = WarlockMath.drainHeal(100.0, 1.0, 0.85);
        double dH3 = WarlockMath.drainHeal(0.0, 0.666, 0.85);
        boolean ok42 = Math.abs(dH1 - 66.6) < 1e-6
                && Math.abs(dH2 - 85.0) < 1e-6
                && dH3 == 0.0;
        if (check(report, "42", "drain: 66.6/85/0",
                ok42, "WarlockMath.drainHeal",
                String.format(Locale.ROOT, "%.1f/%.1f/%.1f", dH1, dH2, dH3))) {
            passed++;
        } else {
            failed++;
        }

        double m1 = WarlockMath.damageMult(80.0, 75.0, 6.0, 1.1, false);
        double m2 = WarlockMath.damageMult(80.0, 75.0, 6.0, 1.1, true);
        double m3 = WarlockMath.damageMult(50.0, 75.0, 6.0, 1.0, false);
        boolean ig1 = WarlockMath.ignoreMagicResist(0.20, 1.0, 0.25);
        boolean ig2 = WarlockMath.ignoreMagicResist(1.0, 0.25, 0.25);
        boolean ig3 = !WarlockMath.ignoreMagicResist(1.0, 1.0, 0.25);
        boolean ok43 = Math.abs(m1 - 1.32) < 1e-6
                && Math.abs(m2 - 7.92) < 1e-6
                && m3 == 1.0
                && ig1 && ig2 && ig3;
        if (check(report, "43", "damageMult/ignore sanity",
                ok43, "WarlockMath.damageMult/ignore",
                String.format(Locale.ROOT, "%.2f/%.2f/%.1f/%s%s%s", m1, m2, m3, ig1, ig2, ig3))) {
            passed++;
        } else {
            failed++;
        }

        List<String> kitProblems = dev.raskol.classes.config.KitSanity.validateAbilities(plugin);
        boolean ok44 = kitProblems.isEmpty();
        String got44 = ok44 ? "OK" : kitProblems.size() + " проблем: " + kitProblems.get(0);
        if (check(report, "44", "sanity китов", ok44, "KitSanity.validateAbilities", got44)) {
            passed++;
        } else {
            failed++;
            for (String p : kitProblems) {
                report.append("   — ").append(p).append('\n');
            }
        }

        List<String> multProblems = dev.raskol.classes.config.KitSanity.validatePassiveMults(plugin);
        boolean ok45 = multProblems.isEmpty();
        String got45 = ok45 ? "OK" : multProblems.size() + " проблем: " + multProblems.get(0);
        if (check(report, "45", "RUNBOOK пассив-мульты", ok45, "KitSanity.validatePassiveMults", got45)) {
            passed++;
        } else {
            failed++;
            for (String p : multProblems) {
                report.append("   — ").append(p).append('\n');
            }
        }

        double a1 = SpecMath.asFraction(15.0);
        double a2 = SpecMath.asFraction(0.15);
        double a3 = SpecMath.asFraction(100.0);
        boolean ok46 = Math.abs(a1 - 0.15) < 1e-9
                && Math.abs(a2 - 0.15) < 1e-9
                && Math.abs(a3 - 1.0) < 1e-9;
        if (check(report, "46", "asFraction sanity", ok46, "SpecMath.asFraction",
                String.format(Locale.ROOT, "%.3f/%.3f/%.3f", a1, a2, a3))) {
            passed++;
        } else {
            failed++;
        }

        int rc40 = SpecMath.respecCost(40, 250, 10);
        int rc60 = SpecMath.respecCost(60, 250, 10);
        boolean ok47 = rc40 == 650 && rc60 == 850;
        if (check(report, "47", "respecCost: 40→650, 60→850", ok47, "SpecMath.respecCost", rc40 + "/" + rc60)) {
            passed++;
        } else {
            failed++;
        }

        int perClassCount = plugin.getRaskolConfig().kitLoader().loadedCount();
        double recoilFromLoader = plugin.getRaskolConfig().classDouble(
                PlayerClass.WARLOCK, "recoil.percent", 6.66);
        boolean ok48 = recoilFromLoader >= 0.0 && Double.isFinite(recoilFromLoader);
        if (check(report, "48", "per-class loader sanity", ok48, "KitConfigLoader/classDouble",
                perClassCount + "/" + recoilFromLoader)) {
            passed++;
        } else {
            failed++;
        }

        boolean ok49 = School.PHYSICAL.channel() == DamageType.PHYSICAL
                && School.TRUE.channel() == DamageType.TRUE
                && School.FIRE.channel() == DamageType.MAGIC
                && School.FROST.channel() == DamageType.MAGIC
                && School.NATURE.channel() == DamageType.MAGIC
                && School.SHADOW.channel() == DamageType.MAGIC
                && School.HOLY.channel() == DamageType.MAGIC
                && School.ARCANE.channel() == DamageType.MAGIC;
        if (check(report, "49", "School→channel", ok49, "School.channel",
                School.PHYSICAL.channel() + "/" + School.FIRE.channel() + "/" + School.TRUE.channel())) {
            passed++;
        } else {
            failed++;
        }

        SchoolConfig sc = new SchoolConfig(plugin);
        boolean ok50 = sc.schoolOf(EntityDamageEvent.DamageCause.FIRE) == School.FIRE
                && sc.schoolOf(EntityDamageEvent.DamageCause.POISON) == School.NATURE
                && sc.schoolOf(EntityDamageEvent.DamageCause.WITHER) == School.SHADOW
                && sc.schoolOf(EntityDamageEvent.DamageCause.FREEZE) == School.FROST
                && sc.schoolOf(EntityDamageEvent.DamageCause.LIGHTNING) == School.ARCANE
                && sc.schoolOf(EntityDamageEvent.DamageCause.ENTITY_ATTACK) == School.PHYSICAL
                && sc.schoolOf(EntityDamageEvent.DamageCause.FALL) == School.TRUE;
        if (check(report, "50", "vanilla-school map", ok50, "SchoolConfig.schoolOf",
                sc.schoolOf(EntityDamageEvent.DamageCause.FIRE) + "/"
                        + sc.schoolOf(EntityDamageEvent.DamageCause.POISON) + "/"
                        + sc.schoolOf(EntityDamageEvent.DamageCause.FALL))) {
            passed++;
        } else {
            failed++;
        }

        SchoolProfile legacy = SchoolProfile.fromLegacy(new DamageProfile(10.0, 20.0, 5.0));
        DamageProfile back = legacy.toLegacy(sc);
        SchoolProfile fireOnly = SchoolProfile.builder().add(School.FIRE, 30.0).build();
        DamageProfile fireLegacy = fireOnly.toLegacy(sc);
        boolean ok51 = Math.abs(back.physical() - 10.0) < 1e-9
                && Math.abs(back.magic() - 20.0) < 1e-9
                && Math.abs(back.trueDamage() - 5.0) < 1e-9
                && legacy.dominant() == School.ARCANE
                && Math.abs(fireLegacy.magic() - 30.0) < 1e-9
                && fireLegacy.physical() == 0.0
                && fireLegacy.trueDamage() == 0.0;
        if (check(report, "51", "SchoolProfile legacy round-trip", ok51, "SchoolProfile.toLegacy",
                String.format(Locale.ROOT, "%.1f/%.1f/%.1f dom=%s",
                        back.physical(), back.magic(), back.trueDamage(), legacy.dominant()))) {
            passed++;
        } else {
            failed++;
        }

        Penetration pen52 = Penetration.of(20.0, 0.25);
        double mit1 = SchoolMitigation.mitigationFor(50.0, pen52, 0.40, 0.0, false, 0.80);
        double mit2 = SchoolMitigation.mitigationFor(50.0, pen52, 0.40, 30.0, true, 0.80);
        double mit3 = SchoolMitigation.mitigationFor(95.0, Penetration.NONE, 0.40, 0.0, false, 0.80);
        boolean ok52 = Math.abs(mit1 - 0.225) < 1e-6
                && Math.abs(mit2 - 0.4575) < 1e-6
                && Math.abs(mit3 - 0.80) < 1e-6;
        if (check(report, "52", "mitigation с pen/elemental/cap", ok52, "SchoolMitigation",
                String.format(Locale.ROOT, "%.4f/%.4f/%.4f", mit1, mit2, mit3))) {
            passed++;
        } else {
            failed++;
        }

        SchoolImmunity imm = new SchoolImmunity(plugin);
        boolean ok53 = imm.multiplierFor(EntityType.BLAZE, School.FIRE) == 0.0
                && Math.abs(imm.multiplierFor(EntityType.BLAZE, School.FROST) - 1.5) < 1e-9
                && imm.multiplierFor(EntityType.BLAZE, School.HOLY) == 1.0
                && imm.multiplierFor(EntityType.ZOMBIE, School.FIRE) == 1.0
                && Math.abs(imm.multiplierFor(EntityType.WITHER_SKELETON, School.SHADOW) - 0.5) < 1e-9;
        if (check(report, "53", "immunity sanity", ok53, "SchoolImmunity",
                imm.multiplierFor(EntityType.BLAZE, School.FIRE) + "/"
                        + imm.multiplierFor(EntityType.BLAZE, School.FROST) + "/"
                        + imm.multiplierFor(EntityType.WITHER_SKELETON, School.SHADOW))) {
            passed++;
        } else {
            failed++;
        }

        Penetration p54 = Penetration.of(10.0, 0.6).clamped(0.40);
        double taken54 = SchoolMitigation.taken(100.0, 0.25, 1.5);
        boolean ok54 = p54.flat() == 10.0
                && Math.abs(p54.pct() - 0.40) < 1e-9
                && Penetration.NONE.pct() == 0.0
                && Math.abs(taken54 - 112.5) < 1e-6;
        if (check(report, "54", "penetration clamp + taken", ok54, "Penetration/SchoolMitigation",
                String.format(Locale.ROOT, "%.2f/%.2f/%.1f", p54.flat(), p54.pct(), taken54))) {
            passed++;
        } else {
            failed++;
        }

        var elem = plugin.getCombat().elemental();
        UUID eu = UUID.randomUUID();
        elem.addPermanent(eu, "selftest_t1", School.FIRE, 40.0);
        elem.addTimed(eu, "selftest_t2", School.FIRE, 30.0, 60_000L);
        double cappedRes = elem.resistOf(eu, School.FIRE);
        elem.removeBySource(eu, "selftest_t2");
        double singleRes = elem.resistOf(eu, School.FIRE);
        elem.removeAll(eu);
        double zeroRes = elem.resistOf(eu, School.FIRE);
        boolean ok55 = Math.abs(cappedRes - 60.0) < 1e-9
                && Math.abs(singleRes - 40.0) < 1e-9
                && zeroRes == 0.0;
        if (check(report, "55", "elemental: суммирование/кап/снятие", ok55, "ElementalResistService",
                String.format(Locale.ROOT, "%.1f/%.1f/%.1f", cappedRes, singleRes, zeroRes))) {
            passed++;
        } else {
            failed++;
        }

        UUID eu2 = UUID.randomUUID();
        elem.addPermanent(eu2, "selftest_t3", School.FROST, 40.0);
        double elPct = elem.resistOf(eu2, School.FROST);
        double mitCombined = SchoolMitigation.mitigationFor(
                50.0, Penetration.NONE, 0.40, elPct, true, 0.80);
        elem.removeAll(eu2);
        boolean ok56 = Math.abs(elPct - 40.0) < 1e-9
                && Math.abs(mitCombined - 0.70) < 1e-6;
        if (check(report, "56", "elemental→mitigation связка", ok56, "SchoolMitigation(elemental)",
                String.format(Locale.ROOT, "%.1f/%.4f", elPct, mitCombined))) {
            passed++;
        } else {
            failed++;
        }

        double g1 = GearHook.clampPenFraction(60.0, 0.40);
        double g2 = GearHook.clampPenFraction(10.0, 0.40);
        double g3 = GearHook.clampPenFraction(-5.0, 0.40);
        double g4 = GearHook.clampPenFraction(Double.NaN, 0.40);
        boolean ok57 = Math.abs(g1 - 0.40) < 1e-9
                && Math.abs(g2 - 0.10) < 1e-9
                && g3 == 0.0
                && g4 == 0.0;
        if (check(report, "57", "gear-pen clamp", ok57, "GearHook.clampPenFraction",
                String.format(Locale.ROOT, "%.2f/%.2f/%.1f/%.1f", g1, g2, g3, g4))) {
            passed++;
        } else {
            failed++;
        }

        Penetration gearPen = Penetration.of(0.0, GearHook.clampPenFraction(25.0, 0.40));
        double mitGear = SchoolMitigation.mitigationFor(60.0, gearPen, 0.40, 0.0, false, 0.80);
        boolean ok58 = Math.abs(mitGear - 0.45) < 1e-6;
        if (check(report, "58", "gear-pen→mitigation", ok58, "SchoolMitigation(gearPen)",
                String.format(Locale.ROOT, "%.4f", mitGear))) {
            passed++;
        } else {
            failed++;
        }

        double pc1 = PenTraitsService.clampSumPercent(45.0, 0.40);
        double pc2 = PenTraitsService.clampSumPercent(15.0, 0.40);
        double pc3 = PenTraitsService.clampSumPercent(-3.0, 0.40);
        boolean ok59 = Math.abs(pc1 - 0.40) < 1e-9
                && Math.abs(pc2 - 0.15) < 1e-9
                && pc3 == 0.0;
        if (check(report, "59", "pen-сумма clamp", ok59, "PenTraitsService.clampSumPercent",
                String.format(Locale.ROOT, "%.2f/%.2f/%.1f", pc1, pc2, pc3))) {
            passed++;
        } else {
            failed++;
        }

        PenTraitsService pts = new PenTraitsService(plugin);
        UUID pu60 = UUID.randomUUID();
        double t60 = pts.talentPenPercent(pu60, "phys");
        double s60 = pts.specPenPercent(Spec.GUARDIAN, "phys");
        double g60 = pts.schoolPenFraction(pu60, School.FIRE, plugin.getGearHook(), 0.40);
        boolean ok60 = t60 == 0.0 && s60 == 0.0 && g60 == 0.0;
        if (check(report, "60", "pen-трейты без контента = 0", ok60, "PenTraitsService.*",
                String.format(Locale.ROOT, "%.1f/%.1f/%.1f", t60, s60, g60))) {
            passed++;
        } else {
            failed++;
        }

        double l1 = SchoolMitigation.mitigationFor(0.0, Penetration.NONE, 0.40, 0.0, false, 0.80);
        double l2 = SchoolMitigation.mitigationFor(35.0, Penetration.NONE, 0.40, 0.0, false, 0.80);
        double l3 = SchoolMitigation.mitigationFor(90.0, Penetration.NONE, 0.40, 0.0, false, 0.90);
        boolean ok61 = l1 == 0.0
                && Math.abs(l2 - 0.35) < 1e-9
                && Math.abs(l3 - 0.90) < 1e-9;
        if (check(report, "61", "legacy проводка при pen=0", ok61, "SchoolMitigation(legacy)",
                String.format(Locale.ROOT, "%.2f/%.2f/%.2f", l1, l2, l3))) {
            passed++;
        } else {
            failed++;
        }

        double el62 = CombatMath.effectiveResist(40.0, 0.0, 0.25, 0.40);
        double mit62 = SchoolMitigation.mitigationFor(45.0, Penetration.NONE, 0.40, el62, true, 0.90);
        boolean ok62 = Math.abs(el62 - 30.0) < 1e-9
                && Math.abs(mit62 - 0.615) < 1e-6;
        if (check(report, "62", "school-pen→elemental", ok62, "CombatMath/SchoolMitigation",
                String.format(Locale.ROOT, "%.1f/%.4f", el62, mit62))) {
            passed++;
        } else {
            failed++;
        }

        CombatService.setCurrentCastSchool(School.FIRE);
        School s63a = CombatService.currentCastSchool();
        CombatService.clearCastSchool();
        School s63b = CombatService.currentCastSchool();
        CombatService.setCurrentCastSchool(School.PHYSICAL);
        School s63c = CombatService.currentCastSchool();
        CombatService.clearCastSchool();
        boolean ok63 = s63a == School.FIRE && s63b == null && s63c == School.PHYSICAL;
        if (check(report, "63", "cast-school ThreadLocal", ok63, "CombatService",
                s63a + "/" + s63b + "/" + s63c)) {
            passed++;
        } else {
            failed++;
        }

        int covered = plugin.getAbilityRegistry().schoolCoverage();
        boolean ok64 = covered == 30;
        if (check(report, "64", "schoolCoverage: 30/30", ok64, "AbilityRegistry", covered)) {
            passed++;
        } else {
            failed++;
        }

        boolean fp65 = plugin.getAbilityRegistry().findById(PlayerClass.MAGE, "fire_prometheus").school() == School.FIRE;
        boolean bb65 = plugin.getAbilityRegistry().findById(PlayerClass.MAGE, "boreas_breath").school() == School.FROST;
        boolean zw65 = plugin.getAbilityRegistry().findById(PlayerClass.MAGE, "zeus_wrath").school() == School.ARCANE;
        boolean bp65 = plugin.getAbilityRegistry().findById(PlayerClass.ROGUE, "borgia_poison").school() == School.NATURE;
        boolean scCloak65 = plugin.getAbilityRegistry().findById(PlayerClass.ROGUE, "shadow_cloak").school() == School.SHADOW;
        boolean wh65 = plugin.getAbilityRegistry().findById(PlayerClass.PRIEST, "wrath_heaven").school() == School.HOLY;
        boolean rk65 = plugin.getAbilityRegistry().findById(PlayerClass.WARRIOR, "ragnarok").school() == School.PHYSICAL;
        boolean bw65 = plugin.getAbilityRegistry().findById(PlayerClass.WARLOCK, "black_word").school() == School.SHADOW;
        boolean ok65 = fp65 && bb65 && zw65 && bp65 && scCloak65 && wh65 && rk65 && bw65;
        if (check(report, "65", "школы китов по карте", ok65, "AbilityRegistry.findById",
                fp65 + "/" + bb65 + "/" + zw65 + "/" + bp65 + "/" + scCloak65 + "/" + wh65 + "/" + rk65 + "/" + bw65)) {
            passed++;
        } else {
            failed++;
        }

        double[] raw66 = {40.0, 30.0};
        double f66 = DotMath.capFactor(raw66, 60.0);
        double f66b = DotMath.capFactor(new double[]{10.0}, 60.0);
        double f66c = DotMath.capFactor(new double[]{0.0}, 60.0);
        boolean ok66 = Math.abs(f66 - 60.0 / 70.0) < 1e-9 && f66b == 1.0 && f66c == 1.0;
        if (check(report, "66", "dot-cap: [40,30]→6/7; 10→1; 0→1", ok66, "DotMath.capFactor",
                String.format(Locale.ROOT, "%.4f/%.1f/%.1f", f66, f66b, f66c))) {
            passed++;
        } else {
            failed++;
        }

        double w67 = DotMath.withMults(10.0, 1.5, 0.5, 0.26);
        double w67b = DotMath.withMults(10.0, 1.0, 0.0, 0.0);
        double w67c = DotMath.withMults(-5.0, 2.0, 1.0, 0.0);
        boolean ok67 = Math.abs(w67 - 9.45) < 1e-9 && w67b == 0.0 && w67c == 0.0;
        if (check(report, "67", "dot-mults: school×immunity×seal", ok67, "DotMath.withMults",
                String.format(Locale.ROOT, "%.2f/%.1f/%.1f", w67, w67b, w67c))) {
            passed++;
        } else {
            failed++;
        }

        boolean svc68 = plugin.getCombat().dots() != null;
        double capCfg = plugin.getConfig().getDouble("combat.dot-dps-cap-pct", 0.0);
        double lim68 = DotMath.dpsLimit(1000.0, capCfg);
        boolean ok68 = svc68 && Math.abs(capCfg - 6.0) < 1e-9 && Math.abs(lim68 - 60.0) < 1e-9;
        if (check(report, "68", "DotService + cap=6%→60 DPS/1000HP", ok68, "CombatService/DotMath",
                svc68 + "/" + capCfg + "/" + lim68)) {
            passed++;
        } else {
            failed++;
        }

        boolean ex1 = DotService.shouldExtinguish(School.FIRE, Material.WATER);
        boolean ex2 = DotService.shouldExtinguish(School.FIRE, Material.POWDER_SNOW);
        boolean ex3 = DotService.shouldExtinguish(School.FIRE, Material.STONE);
        boolean ex4 = DotService.shouldExtinguish(School.FROST, Material.LAVA);
        boolean ex5 = DotService.shouldExtinguish(School.FROST, Material.WATER);
        boolean ok69 = ex1 && ex2 && !ex3 && ex4 && !ex5;
        if (check(report, "69", "средовые триггеры", ok69, "DotService.shouldExtinguish",
                ex1 + "/" + ex2 + "/" + ex3 + "/" + ex4 + "/" + ex5)) {
            passed++;
        } else {
            failed++;
        }

        var dots70 = plugin.getCombat().dots();
        boolean ok70 = dots70.defById("burning") != null
                && dots70.defById("burning").dps() > 0.0
                && dots70.defById("burning").school() == School.FIRE
                && dots70.defById("poison") != null
                && dots70.defById("poison").school() == School.NATURE
                && dots70.defById("bleed") != null
                && dots70.defById("bleed").school() == School.PHYSICAL
                && dots70.defById("chilled") != null
                && dots70.defById("chilled").school() == School.FROST;
        if (check(report, "70", "dots-реестр: 4 школы", ok70, "DotService.defById",
                (dots70.defById("burning") != null) + "/" + (dots70.defById("poison") != null)
                        + "/" + (dots70.defById("bleed") != null) + "/" + (dots70.defById("chilled") != null))) {
            passed++;
        } else {
            failed++;
        }

        UUID uuid71 = UUID.randomUUID();
        DotInstance inst71 = new DotInstance(
                DotDef.of("selftest_dot", School.FIRE, 5.0, 3000L, 3, "selftest"),
                uuid71, System.currentTimeMillis());
        long t71 = System.currentTimeMillis();
        inst71.refresh(t71);
        inst71.refresh(t71);
        inst71.refresh(t71);
        boolean stacksOk = inst71.stacks() == 3;
        boolean expiryOk = !inst71.expired(t71 + 2999L) && inst71.expired(t71 + 3001L);
        boolean ok71 = stacksOk && expiryOk;
        if (check(report, "71", "DotInstance: refresh/expiry", ok71, "DotInstance",
                inst71.stacks() + "/" + stacksOk + "/" + expiryOk)) {
            passed++;
        } else {
            failed++;
        }

        UUID fresh72 = UUID.randomUUID();
        List<DotInstance> empty72 = plugin.getCombat().dots().activeDotsOf(fresh72);
        boolean ok72 = empty72 != null && empty72.isEmpty();
        if (check(report, "72", "DotService.activeDotsOf: пустой snapshot",
                ok72, "DotService.activeDotsOf",
                empty72 == null ? "null" : String.valueOf(empty72.size()))) {
            passed++;
        } else {
            failed++;
        }

        DotDef ppDef = plugin.getCombat().dots().defById("poison_passive");
        DotDef poisonDef = plugin.getCombat().dots().defById("poison");
        boolean ok73 = ppDef != null
                && poisonDef != null
                && ppDef.school() == School.NATURE
                && ppDef.durationMillis() == 2000L
                && poisonDef.durationMillis() == 5000L
                && ppDef.maxStacks() == 1
                && poisonDef.maxStacks() == 3;
        if (check(report, "73", "dots: poison_passive (2s/×1) vs poison (5s/×3)",
                ok73, "DotService.defById",
                (ppDef != null ? ppDef.durationMillis() + "/×" + ppDef.maxStacks() : "null")
                        + " vs " + (poisonDef != null ? poisonDef.durationMillis() + "/×" + poisonDef.maxStacks() : "null"))) {
            passed++;
        } else {
            failed++;
        }

        String poisonDotId = plugin.getConfig().getString(
                "classes.ROGUE.passives.poisoned_blades.dot", "");
        boolean ok74 = "poison_passive".equals(poisonDotId)
                && ppDef != null && poisonDef != null;
        if (check(report, "74", "миграция poisoned_blades: dot=poison_passive",
                ok74, "config classes.ROGUE.passives.poisoned_blades.dot", poisonDotId)) {
            passed++;
        } else {
            failed++;
        }

        String got75;
        boolean ok75;
        try {
            double[][] m75 = BalanceSimulator.matrix(plugin, 40, 42L);
            boolean clean = true;
            boolean diagOk = true;
            double sum = 0.0;
            int cnt = 0;
            for (int i = 0; i < m75.length; i++) {
                for (int j = 0; j < m75[i].length; j++) {
                    double v = m75[i][j];
                    if (Double.isNaN(v) || v < 0.0) {
                        clean = false;
                    }
                    if (i == j && Double.isFinite(v)) {
                        cnt++;
                        sum += v;
                        if (v < 10.0 || v > 60.0) {
                            diagOk = false;
                        }
                    }
                }
            }
            double avg = cnt > 0 ? sum / cnt : 0.0;
            ok75 = clean && diagOk && cnt >= 4 && avg >= 15.0 && avg <= 25.0;
            got75 = "clean=" + clean + " diag=" + diagOk + " n=" + cnt
                    + " avg=" + String.format(Locale.ROOT, "%.1f", avg);
        } catch (RuntimeException ex) {
            ok75 = false;
            got75 = "exception: " + ex.getClass().getSimpleName();
        }
        if (check(report, "75", "balance-sanity: матрица чистая, диагональ [10,60], avg [15,25]",
                ok75, "BalanceSimulator.matrix", got75)) {
            passed++;
        } else {
            failed++;
        }

        double[] mults76 = {1.0, 0.5, 0.25, 0.0};
        boolean ok76 = CCService.drMultiplier(0, mults76) == 1.0
                && CCService.drMultiplier(1, mults76) == 0.5
                && CCService.drMultiplier(2, mults76) == 0.25
                && CCService.drMultiplier(3, mults76) == 0.0
                && CCService.drMultiplier(4, mults76) == 0.0;
        if (check(report, "76", "DR-множители: 1.0/0.5/0.25/0.0/0.0",
                ok76, "CCService.drMultiplier",
                CCService.drMultiplier(0, mults76) + "/" + CCService.drMultiplier(3, mults76))) {
            passed++;
        } else {
            failed++;
        }

        long now77 = System.currentTimeMillis();
        int reset77 = CCService.stackAfterWindow(now77, now77 - 20_000L, 15_000L, 3);
        int keep77 = CCService.stackAfterWindow(now77, now77 - 5_000L, 15_000L, 3);
        boolean ok77 = reset77 == 0 && keep77 == 3;
        if (check(report, "77", "окно DR: 20с→0, 5с→3",
                ok77, "CCService.stackAfterWindow", reset77 + "/" + keep77)) {
            passed++;
        } else {
            failed++;
        }

        boolean ok78 = CCService.isDrImmune(4, mults76) && !CCService.isDrImmune(3, mults76);
        if (check(report, "78", "DR-иммунитет: стек4=иммун, стек3=нет",
                ok78, "CCService.isDrImmune",
                CCService.isDrImmune(4, mults76) + "/" + CCService.isDrImmune(3, mults76))) {
            passed++;
        } else {
            failed++;
        }

        boolean ok79 = CCType.STUN.category() == DRCategory.STUN
                && CCType.KNOCKBACK.category() == DRCategory.STUN
                && CCType.FEAR.category() == DRCategory.FEAR
                && CCType.CHARM.category() == DRCategory.FEAR
                && CCType.SILENCE.category() == DRCategory.SILENCE
                && CCType.DISARM.category() == DRCategory.SILENCE
                && CCType.ROOT.category() == DRCategory.ROOT
                && CCType.SLOW.category() == DRCategory.SLOW
                && CCType.BLIND.category() == DRCategory.BLIND;
        if (check(report, "79", "категории DR: STUN/KNOCKBACK, FEAR/CHARM, SILENCE/DISARM, ROOT, SLOW, BLIND",
                ok79, "CCType.category",
                CCType.KNOCKBACK.category() + "/" + CCType.CHARM.category() + "/" + CCType.DISARM.category())) {
            passed++;
        } else {
            failed++;
        }

        CCService cc = plugin.getCC();
        boolean ok80 = cc != null && cc.enabled();
        if (check(report, "80", "CCService инициализирован и cc.enabled=true",
                ok80, "RaskolClasses.onEnable/cc.enabled",
                (cc != null) + "/" + (cc != null && cc.enabled()))) {
            passed++;
        } else {
            failed++;
        }

        boolean ok81 = CCType.ROOT.breaksOnDamage()
                && CCType.FEAR.breaksOnDamage()
                && !CCType.STUN.breaksOnDamage()
                && !CCType.SILENCE.breaksOnDamage()
                && !CCType.DISARM.breaksOnDamage()
                && !CCType.SLOW.breaksOnDamage()
                && !CCType.BLIND.breaksOnDamage()
                && !CCType.CHARM.breaksOnDamage();
        if (check(report, "81", "CCType.breaksOnDamage: ROOT/FEAR=true, остальные=false",
                ok81, "CCType.breaksOnDamage",
                CCType.ROOT.breaksOnDamage() + "/" + CCType.STUN.breaksOnDamage())) {
            passed++;
        } else {
            failed++;
        }

        if (probe == null || cc == null) {
            if (check(report, "82", "breakOnDamage (пропущено: нет онлайн-игрока)",
                    true, "CCService.breakOnDamage", "skip")) {
                passed++;
            } else {
                failed++;
            }
        } else {
            UUID u82 = probe.getUniqueId();
            cc.removeAll(u82);
            cc.resetAllDr(u82);
            boolean applied1 = applyUntilOk(cc, probe, CCType.ROOT, 100);
            boolean hasBefore = cc.has(u82, CCType.ROOT);
            cc.breakOnDamage(probe, 60.0, 1000.0);
            boolean hasAfterHigh = cc.has(u82, CCType.ROOT);
            cc.resetAllDr(u82);
            boolean applied2 = applyUntilOk(cc, probe, CCType.ROOT, 100);
            cc.breakOnDamage(probe, 30.0, 1000.0);
            boolean hasAfterLow = cc.has(u82, CCType.ROOT);
            cc.removeAll(u82);
            cc.resetAllDr(u82);
            boolean ok82 = applied1 && hasBefore && !hasAfterHigh && applied2 && hasAfterLow;
            if (check(report, "82", "breakOnDamage: 6% HP снимает ROOT, 3% оставляет",
                    ok82, "CCService.breakOnDamage",
                    applied1 + "/" + hasBefore + "/" + hasAfterHigh + "/" + applied2 + "/" + hasAfterLow)) {
                passed++;
            } else {
                failed++;
            }
        }

        if (probe == null || cc == null) {
            if (check(report, "83", "STUN breakOnDamage (пропущено)", true, "CCService.breakOnDamage", "skip")) {
                passed++;
            } else {
                failed++;
            }
        } else {
            UUID u83 = probe.getUniqueId();
            cc.removeAll(u83);
            cc.resetAllDr(u83);
            boolean applied = applyUntilOk(cc, probe, CCType.STUN, 100);
            boolean hasBefore = cc.has(u83, CCType.STUN);
            cc.breakOnDamage(probe, 60.0, 1000.0);
            boolean hasAfter = cc.has(u83, CCType.STUN);
            cc.removeAll(u83);
            cc.resetAllDr(u83);
            boolean ok83 = applied && hasBefore && hasAfter;
            if (check(report, "83", "STUN не снимается уроном ≥ порога",
                    ok83, "CCService.breakOnDamage",
                    applied + "/" + hasBefore + "/" + hasAfter)) {
                passed++;
            } else {
                failed++;
            }
        }

        if (probe == null || cc == null) {
            if (check(report, "84", "CastGuard.canCast (пропущено)", true, "CastGuard.canCast", "skip")) {
                passed++;
            } else {
                failed++;
            }
        } else {
            UUID u84 = probe.getUniqueId();
            dev.raskol.classes.cc.CastGuard cg = new dev.raskol.classes.cc.CastGuard(plugin);
            cc.removeAll(u84);
            cc.resetAllDr(u84);
            boolean canNormal = cg.canCast(probe, false);
            applyUntilOk(cc, probe, CCType.STUN, 100);
            boolean canStun = cg.canCast(probe, false);
            cc.removeAll(u84);
            cc.resetAllDr(u84);
            applyUntilOk(cc, probe, CCType.SILENCE, 100);
            boolean canSilenceNormal = cg.canCast(probe, false);
            boolean canSilenceInstant = cg.canCast(probe, true);
            cc.removeAll(u84);
            cc.resetAllDr(u84);
            boolean ok84 = canNormal && !canStun && !canSilenceNormal && canSilenceInstant;
            if (check(report, "84", "CastGuard: normal=OK, STUN=no, SILENCE+cast=no, SILENCE+instant=OK",
                    ok84, "CastGuard.canCast",
                    canNormal + "/" + canStun + "/" + canSilenceNormal + "/" + canSilenceInstant)) {
                passed++;
            } else {
                failed++;
            }
        }

        boolean ok85 = VanillaCCWrapper.wrapTarget(PotionEffectType.SLOWNESS) == CCType.SLOW
                && VanillaCCWrapper.wrapTarget(PotionEffectType.BLINDNESS) == CCType.BLIND
                && VanillaCCWrapper.wrapTarget(PotionEffectType.WEAKNESS) == CCType.SILENCE
                && VanillaCCWrapper.wrapTarget(PotionEffectType.REGENERATION) == null;
        if (check(report, "85", "wrapVanilla: SLOWNESS→SLOW, BLINDNESS→BLIND, WEAKNESS→SILENCE, REGEN→null",
                ok85, "VanillaCCWrapper.wrapTarget",
                VanillaCCWrapper.wrapTarget(PotionEffectType.SLOWNESS) + "/"
                        + VanillaCCWrapper.wrapTarget(PotionEffectType.BLINDNESS) + "/"
                        + VanillaCCWrapper.wrapTarget(PotionEffectType.WEAKNESS))) {
            passed++;
        } else {
            failed++;
        }

        String d86 = CCFeedback.describeApply(plugin, CCType.STUN, 60, 0.5);
        boolean ok86 = d86 != null
                && d86.contains(CCType.STUN.ruName())
                && d86.contains("3.0")
                && d86.contains("50");
        if (check(report, "86", "describeApply: «Оцепенение», 3.0с, DR 50% в строке сообщения",
                ok86, "CCFeedback.describeApply", d86)) {
            passed++;
        } else {
            failed++;
        }

        AtomicBoolean cancelled87 = new AtomicBoolean(false);
        UUID ch87 = UUID.randomUUID();
        CastChannels.register(ch87, () -> cancelled87.set(true));
        boolean first87 = CastChannels.interrupt(ch87);
        boolean second87 = CastChannels.interrupt(ch87);
        boolean ok87 = first87 && cancelled87.get() && !second87 && !CastChannels.isChanneling(ch87);
        if (check(report, "87", "CastChannels: interrupt→canceller выполнен, повторный interrupt=false",
                ok87, "CastChannels.interrupt",
                first87 + "/" + cancelled87.get() + "/" + second87)) {
            passed++;
        } else {
            failed++;
        }

        boolean listOk = false;
        boolean seqOk = true;
        try {
            CcSub ccSub = new CcSub(plugin);
            listOk = ccSub.execute(Bukkit.getConsoleSender(), new String[]{"list"});
            if (probe != null) {
                seqOk = ccSub.execute(Bukkit.getConsoleSender(), new String[]{"test", probe.getName(), "STUN"})
                        && ccSub.execute(Bukkit.getConsoleSender(), new String[]{"status", probe.getName()})
                        && ccSub.execute(Bukkit.getConsoleSender(), new String[]{"clear", probe.getName()});
            }
        } catch (RuntimeException ex) {
            listOk = false;
            seqOk = false;
        }
        boolean ok88 = listOk && seqOk;
        if (check(report, "88", "/rc cc list/status/test/clear исполняются без исключений",
                ok88, "CcSub.execute", listOk + "/" + seqOk)) {
            passed++;
        } else {
            failed++;
        }

        List<String> ccProblems = CcSanity.validateCc(plugin);
        boolean ok89 = ccProblems.isEmpty()
                && CcSanity.inRange(0.5, 0.0, 1.0)
                && !CcSanity.inRange(2.0, 0.0, 1.0)
                && !CcSanity.inRange(Double.NaN, 0.0, 1.0);
        if (check(report, "89", "CcSanity: конфиг cc.* без проблем; inRange(0.5)=true, (2.0)=false, (NaN)=false",
                ok89, "CcSanity.validateCc",
                ccProblems.isEmpty() ? "OK" : ccProblems.size() + ": " + ccProblems.get(0))) {
            passed++;
        } else {
            failed++;
        }

        if (probe == null || cc == null) {
            if (check(report, "90", "debug CC/DR данные (пропущено: нет онлайн-игрока)",
                    true, "CCService.activeOf/drState", "skip")) {
                passed++;
            } else {
                failed++;
            }
        } else {
            UUID u90 = probe.getUniqueId();
            cc.removeAll(u90);
            cc.resetAllDr(u90);
            boolean applied90 = applyUntilOk(cc, probe, CCType.STUN, 60);
            boolean activeVisible = !cc.activeOf(u90).isEmpty();
            boolean drVisible = cc.drState(u90, DRCategory.STUN).stackCount() >= 1;
            cc.removeAll(u90);
            cc.resetAllDr(u90);
            boolean cleared90 = cc.activeOf(u90).isEmpty()
                    && cc.drState(u90, DRCategory.STUN).stackCount() == 0;
            boolean ok90 = applied90 && activeVisible && drVisible && cleared90;
            if (check(report, "90", "debug-секция CC/DR: apply→activeOf+drState видны, cleanup пуст",
                    ok90, "CCService.activeOf/drState",
                    applied90 + "/" + activeVisible + "/" + drVisible + "/" + cleared90)) {
                passed++;
            } else {
                failed++;
            }
        }

        // 1.14.0 (Б1): чек 91 — 18 активных спек + 6 legacy = 24 константы; forClass = 3
        boolean ok91 = Spec.values().length == 24
                && Spec.activeValues().length == 18
                && Spec.forClass(PlayerClass.WARRIOR).length == 3
                && Spec.forClass(PlayerClass.HUNTER).length == 3
                && Spec.forClass(PlayerClass.PRIEST).length == 3
                && Spec.forClass(PlayerClass.MAGE).length == 3
                && Spec.forClass(PlayerClass.ROGUE).length == 3
                && Spec.forClass(PlayerClass.WARLOCK).length == 3
                && Spec.TRACKER.legacy() && !Spec.ARMS.legacy();
        if (check(report, "91", "Spec: 24 константы (18 активных + 6 legacy), forClass=3, legacy-флаги верны",
                ok91, "Spec.values/activeValues/forClass",
                Spec.values().length + "/" + Spec.activeValues().length + "/"
                        + Spec.forClass(PlayerClass.WARLOCK).length)) {
            passed++;
        } else {
            failed++;
        }

        // 1.14.0 (Б1): чек 92 — legacy-алиасы нормализуются в современные спеки
        boolean ok92 = Spec.fromId("tracker") == Spec.SURVIVAL
                && Spec.fromId("lightbearer") == Spec.HOLY
                && Spec.fromId("liquidator") == Spec.ASSASSIN
                && Spec.fromId("trickster") == Spec.OUTLAW
                && Spec.fromId("black_mage") == Spec.AFFLICTION
                && Spec.fromId("hell_channel") == Spec.DEMONOLOGY
                && Spec.TRACKER.modernOf() == Spec.SURVIVAL
                && Spec.HELL_CHANNEL.modernOf() == Spec.DEMONOLOGY
                && Spec.ARMS.modernOf() == Spec.ARMS
                && Spec.fromId("arms") == Spec.ARMS
                && Spec.fromId("no_such_spec") == null;
        if (check(report, "92", "legacy-нормализация: tracker→SURVIVAL … hell_channel→DEMONOLOGY; modernOf(id)=id для активных",
                ok92, "Spec.fromId/modernOf",
                Spec.fromId("tracker") + "/" + Spec.fromId("black_mage") + "/" + Spec.TRACKER.modernOf())) {
            passed++;
        } else {
            failed++;
        }

        // 1.14.0 (Б2): чек 93 — legacy-ключи обнуления и сиротские деревья
        boolean ok93 = SpecLegacyReset.legacyTreeKeys().size() == 6
                && SpecLegacyReset.legacyTreeKeys().contains("tracker")
                && SpecLegacyReset.legacyTreeKeys().contains("black_mage")
                && TalentsRegistry.treeOf("tracker") == null
                && TalentsRegistry.treeOf("lightbearer") == null
                && TalentsRegistry.treeOf("liquidator") == null
                && TalentsRegistry.treeOf("trickster") == null
                && TalentsRegistry.treeOf("survival") != null
                && TalentsRegistry.treeOf("holy") != null
                && TalentsRegistry.treeOf("assassin") != null
                && TalentsRegistry.treeOf("outlaw") != null;
        if (check(report, "93", "legacy-ключи: 6 на обнуление; treeOf(tracker/lightbearer/liquidator/trickster)=null, новые деревья живы",
                ok93, "SpecLegacyReset.legacyTreeKeys/TalentsRegistry.treeOf",
                SpecLegacyReset.legacyTreeKeys().size() + "/"
                        + (TalentsRegistry.treeOf("tracker") == null) + "/"
                        + (TalentsRegistry.treeOf("survival") != null))) {
            passed++;
        } else {
            failed++;
        }

        // 1.14.0 (Б2): чек 94 — обнуление идемпотентно и возвращает очки
        if (probe == null) {
            if (check(report, "94", "legacy-reset идемпотентность (пропущено: нет онлайн-игрока)",
                    true, "SpecLegacyReset.resetPlayer", "skip")) {
                passed++;
            } else {
                failed++;
            }
        } else {
            UUID u94 = probe.getUniqueId();
            List<String> before94 = new ArrayList<>(
                    plugin.getTalentsStorage().getPurchased(u94, "tracker"));
            plugin.getTalentsStorage().setPurchased(u94, "tracker",
                    new ArrayList<>(List.of("t_snare_wire")));
            int freed94 = plugin.getSpecLegacyReset().resetPlayer(probe);
            boolean purged94 = plugin.getTalentsStorage().getPurchased(u94, "tracker").isEmpty();
            int freedAgain94 = plugin.getSpecLegacyReset().resetPlayer(probe);
            plugin.getTalentsStorage().setPurchased(u94, "tracker", before94);
            plugin.getTalentService().reconcile(u94);
            boolean ok94 = freed94 == 1 && purged94 && freedAgain94 == 0;
            if (check(report, "94", "legacy-reset: 1-й проход стёр 1 узел и вернул очко, 2-й проход = 0 (идемпотентно)",
                    ok94, "SpecLegacyReset.resetPlayer",
                    freed94 + "/" + purged94 + "/" + freedAgain94)) {
                passed++;
            } else {
                failed++;
            }
        }

        sender.sendMessage(Component.text("────────── Selftest Report ──────────", NamedTextColor.GOLD));
        for (String line : report.toString().split("\n")) {
            if (!line.isEmpty()) {
                sender.sendMessage(Component.text(line,
                        line.startsWith("✓") ? NamedTextColor.GREEN : NamedTextColor.RED));
            }
        }
        int total = passed + failed;
        NamedTextColor color = failed == 0 ? NamedTextColor.GREEN : NamedTextColor.RED;
        sender.sendMessage(Component.text("───────────────────────────────────", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("Итог: " + passed + "/" + total + " PASS", color));
        if (failed > 0) {
            sender.sendMessage(Component.text(
                    "Есть проблемы — смотри виновника в каждой строке.",
                    NamedTextColor.RED));
        }
        plugin.getLogger().info("Selftest: " + passed + "/" + total + " PASS");
    }

    /** retry-обёртка tryApply: ccResist-бросок не должен флапать чеки. */
    private static boolean applyUntilOk(CCService cc, Player target, CCType type, int ticks) {
        for (int i = 0; i < 64; i++) {
            CCService.ApplyResult r = cc.tryApply(null, target, type, ticks);
            if (r.ok()) {
                return true;
            }
            if (r.result() == CCService.CCResult.FAIL_DR_IMMUNE) {
                return false;
            }
        }
        return false;
    }

    /** Первый узел тира 1 без пререквизитов (для тестов). */
    private static TalentModel.TalentNode firstT1(TalentModel.TalentTree tree) {
        for (TalentModel.TalentNode node : tree.nodes()) {
            if (node.tier() == 1 && node.prereqs().isEmpty()) {
                return node;
            }
        }
        return null;
    }

    private static boolean check(StringBuilder report, String num, String desc,
                                 boolean ok, String culprit, Object got) {
        String status = ok ? "✓" : "✗";
        String detail = ok ? "" : " (получено: " + fmt(got) + ", виновник: " + culprit + ")";
        report.append(status).append(" чек ").append(num).append(": ").append(desc).append(detail).append('\n');
        return ok;
    }

    private static String fmt(Object v) {
        if (v instanceof Double d) {
            return String.format(Locale.ROOT, "%.2f", d);
        }
        return String.valueOf(v);
    }
}
