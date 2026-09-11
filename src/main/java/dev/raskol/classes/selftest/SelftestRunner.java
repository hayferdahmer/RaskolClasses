// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.selftest;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.attribute.AttributeMath;
import dev.raskol.classes.attribute.PowerService;
import dev.raskol.classes.balance.BalanceSimulator;
import dev.raskol.classes.classsystem.CharacterLevelService;
import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.combat.CombatService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Locale;

/**
 * Headless-самотестирование формул плагина (/rc selftest).
 * Чеки 1–16: формулы атрибутов/avoidance/DR/критов/HP/капа.
 * Чеки 17–18: TTK-санити (воин-зеркало ∈ [10,60]; жрец-зеркало heal-war ≥30/timeout).
 * Чеки 19–20: pure-формула сводного уровня topNAverage (1.8.0).
 * Чек 21 (1.8.1, S6): фракционный гейт canHit — self и среда проходят всегда.
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

        // 21 (1.8.1, S6): фракционный гейт canHit — self и среда проходят всегда
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
