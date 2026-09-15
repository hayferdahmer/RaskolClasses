// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.selftest;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.attribute.AttributeMath;
import dev.raskol.classes.attribute.PowerService;
import dev.raskol.classes.balance.BalanceSimulator;
import dev.raskol.classes.classsystem.CharacterLevelService;
import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.combat.CombatService;
import dev.raskol.classes.resource.ResourceState;
import dev.raskol.classes.spec.Spec;
import dev.raskol.classes.talent.TalentModel;
import dev.raskol.classes.talent.TalentsRegistry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Headless-самотестирование формул плагина (/rc selftest).
 * Чеки 1–16: формулы атрибутов/avoidance/DR/критов/HP/капа.
 * Чеки 17–18: TTK-санити. Чеки 19–20: сводный уровень topNAverage (1.8.0).
 * Чек 21: фракционный гейт canHit (1.8.1).
 * Чеки 22–23: экономика очков талантов и стоимость дерева (1.9.0).
 * Чек 24: reconcile-цикл талантов (1.9.0).
 * Чек 29: боевое окно markCombat/isInCombat (1.9.1).
 * Чек 30: семантика consume — регресс-замок бага «ресурс не списывается» (1.9.1).
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
        if (check(report, "8", "applyDR(80)=70 (наклон DR после soft-cap)", Math.abs(dr2 - 70.0) < 1e-6, "applyDR", dr2)) passed++; else failed++;
        if (check(report, "9", "applyDR(200)=75 (hard-cap)", dr3 == 75.0, "applyDR", dr3)) passed++; else failed++;

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
        if (check(report, "16c", "capped(500,1000,0)=500 (кап выкл)", c3 == 500.0,
                "cappedDamage", c3)) passed++; else failed++;

        BalanceSimulator.DuelResult ww = BalanceSimulator.duel(
                plugin, PlayerClass.WARRIOR, PlayerClass.WARRIOR, 40, 42L);
        boolean ok17 = !ww.timeout() && ww.ttkSeconds() >= 10.0 && ww.ttkSeconds() <= 60.0;
        if (check(report, "17", "TTK воин↔воин ∈ [10,60] с (получено "
                + fmt(ww.ttkSeconds()) + " с)", ok17, "BalanceSimulator", ww.ttkSeconds())) {
            passed++;
        } else {
            failed++;
        }

        BalanceSimulator.DuelResult pp = BalanceSimulator.duel(
                plugin, PlayerClass.PRIEST, PlayerClass.PRIEST, 40, 42L);
        boolean ok18 = pp.timeout() || pp.ttkSeconds() >= 30.0;
        if (check(report, "18", "жрец↔жрец ≥30 с или timeout (получено "
                + fmt(pp.ttkSeconds()) + " с)", ok18, "BalanceSimulator", pp.ttkSeconds())) {
            passed++;
        } else {
            failed++;
        }

        int cl1 = CharacterLevelService.topNAverage(new int[]{99, 70, 40, 20, 10, 5, 0}, 5);
        if (check(report, "19", "topNAverage([99,70,40,20,10,5,0],5)=47", cl1 == 47,
                "topNAverage", cl1)) {
            passed++;
        } else {
            failed++;
        }

        int cl2 = CharacterLevelService.topNAverage(new int[]{15, 0, 0, 0, 0}, 5);
        if (check(report, "20", "topNAverage([15,0,0,0,0],5)=3", cl2 == 3,
                "topNAverage", cl2)) {
            passed++;
        } else {
            failed++;
        }

        Player probe = sender instanceof Player sp
                ? sp
                : Bukkit.getOnlinePlayers().stream().findFirst().orElse(null);
        if (probe == null) {
            if (check(report, "21", "canHit: self/среда (пропущено: нет онлайн-игрока)",
                    true, "canHit", "skip")) {
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
        if (check(report, "22", "очки талантов: 39→0, 40→1, 60→21, 99→21 (кап)",
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
        if (check(report, "23", "стоимость полного дерева талантов = 21", cost == 21,
                "treeCost", cost)) {
            passed++;
        } else {
            failed++;
        }

        if (probe == null) {
            if (check(report, "24", "reconcile-цикл (пропущено: нет онлайн-игрока)",
                    true, "reconcile", "skip")) {
                passed++;
            } else {
                failed++;
            }
        } else {
            boolean ok24 = false;
            String got24 = "no-spec";
            UUID probeUuid = probe.getUniqueId();
            Spec probeSpec = plugin.getSpecService().getSpec(probeUuid);
            if (probeSpec != null) {
                String specId = probeSpec.id();
                TalentModel.TalentTree tree = TalentsRegistry.treeOf(specId);
                if (tree != null) {
                    TalentModel.TalentNode t1a = null;
                    for (TalentModel.TalentNode node : tree.nodes()) {
                        if (node.tier() == 1 && "A".equals(node.branch())
                                && node.prereqs().isEmpty()) {
                            t1a = node;
                            break;
                        }
                    }
                    if (t1a != null) {
                        List<String> before = plugin.getTalentsStorage()
                                .getPurchased(probeUuid, specId);
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
            }
            if (check(report, "24", "reconcile-цикл: покупка→reconcile→откат без рассинхрона",
                    ok24, "reconcile", got24)) {
                passed++;
            } else {
                failed++;
            }
        }

        // 29 (1.9.1): боевое окно: свежее состояние вне боя, после markCombat — в бою
        ResourceState rsWindow = new ResourceState();
        boolean freshOut = !rsWindow.isInCombat(5000L);
        rsWindow.markCombat();
        boolean nowIn = rsWindow.isInCombat(5000L);
        if (check(report, "29", "боевое окно: свежее вне боя, после markCombat в бою",
                freshOut && nowIn, "ResourceState", freshOut + "/" + nowIn)) {
            passed++;
        } else {
            failed++;
        }

        // 30 (1.9.1): семантика consume — регресс-замок бага «ресурс не списывается»
        ResourceState rsConsume = new ResourceState();
        rsConsume.setValue(10.0);
        boolean overDenied = !rsConsume.consume(15.0);
        boolean overIntact = rsConsume.getValue() == 10.0;
        boolean exactOk = rsConsume.consume(10.0);
        boolean zeroed = rsConsume.getValue() == 0.0;
        boolean freeOk = rsConsume.consume(0.0);
        if (check(report, "30", "consume: сверх отказа без изменений, точное обнуляет, 0 бесплатна",
                overDenied && overIntact && exactOk && zeroed && freeOk,
                "ResourceState.consume",
                overDenied + "/" + overIntact + "/" + exactOk + "/" + zeroed + "/" + freeOk)) {
            passed++;
        } else {
            failed++;
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
                    "Есть проблемы — смотри виновника в каждой строке. Проверь конфиг/формулы.",
                    NamedTextColor.RED));
        }
        plugin.getLogger().info("Selftest: " + passed + "/" + total + " PASS");
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
