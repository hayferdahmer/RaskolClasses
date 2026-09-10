// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.selftest;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.attribute.AttributeMath;
import dev.raskol.classes.attribute.PowerService;
import dev.raskol.classes.combat.CombatService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;

import java.util.Locale;

/**
 * Headless-самотестирование формул плагина.
 * Вызывается командой /rc selftest (permission: raskolclasses.debug).
 *
 * Чеки 1–16:
 *  1–3.  dodgeRaw гиперболы AGI (dodge-k=100).
 *  4–6.  parryRaw гиперболы STR (parry-k=150).
 *  7–9.  applyDR: soft-cap, dr-factor, hard-cap.
 *  10–11. splitEff: пропорциональное деление после DR.
 *  12–14. углы isFront/isBack.
 *  15.    WP/SP/HPow референсы на 40 ур. (STR 60 / AGI 30 / INT 60).
 *  16.    анти-ваншот cappedDamage (3 случая).
 *
 * Все формулы — pure-статики (CombatService.cappedDamage, AttributeMath.*,
 * PowerService.*Formula) — те же, что в бою, поэтому тест и рантайм совпадают.
 */
public final class SelftestRunner {

    private SelftestRunner() {
    }

    /** Прогоняет 16 чеков, печатает результат в чат. */
    public static void run(RaskolClasses plugin, CommandSender sender) {
        int passed = 0;
        int failed = 0;
        StringBuilder report = new StringBuilder();

        // 1–3: dodgeRaw
        double d1 = AttributeMath.dodgeRaw(0, 100.0);
        double d2 = AttributeMath.dodgeRaw(100, 100.0);
        double d3 = AttributeMath.dodgeRaw(300, 100.0);
        if (check(report, "1", "dodgeRaw(0,100)=0", d1 == 0.0, "dodgeRaw", d1)) passed++; else failed++;
        if (check(report, "2", "dodgeRaw(100,100)=50", d2 == 50.0, "dodgeRaw", d2)) passed++; else failed++;
        if (check(report, "3", "dodgeRaw(300,100)=75", d3 == 75.0, "dodgeRaw", d3)) passed++; else failed++;

        // 4–6: parryRaw
        double p1 = AttributeMath.parryRaw(0, 150.0);
        double p2 = AttributeMath.parryRaw(150, 150.0);
        double p3 = AttributeMath.parryRaw(450, 150.0);
        if (check(report, "4", "parryRaw(0,150)=0", p1 == 0.0, "parryRaw", p1)) passed++; else failed++;
        if (check(report, "5", "parryRaw(150,150)=50", p2 == 50.0, "parryRaw", p2)) passed++; else failed++;
        if (check(report, "6", "parryRaw(450,150)=75", p3 == 75.0, "parryRaw", p3)) passed++; else failed++;

        // 7–9: applyDR (soft=60, factor=0.5, hard=75)
        double dr1 = AttributeMath.applyDR(0, 60.0, 0.5, 75.0);
        double dr2 = AttributeMath.applyDR(100, 60.0, 0.5, 75.0);
        double dr3 = AttributeMath.applyDR(200, 60.0, 0.5, 75.0);
        if (check(report, "7", "applyDR(0)=0", dr1 == 0.0, "applyDR", dr1)) passed++; else failed++;
        if (check(report, "8", "applyDR(100)=80", dr2 == 80.0, "applyDR", dr2)) passed++; else failed++;
        if (check(report, "9", "applyDR(200)=75 (hard-cap)", dr3 == 75.0, "applyDR", dr3)) passed++; else failed++;

        // 10–11: splitEff
        double[] sp1 = AttributeMath.splitEff(50, 50, 50);
        boolean sp1ok = sp1 != null && sp1.length == 2 && sp1[0] == 25.0 && sp1[1] == 25.0;
        if (check(report, "10", "splitEff(50,50,50)=[25,25]", sp1ok, "splitEff",
                sp1 != null && sp1.length == 2 ? sp1[0] + "," + sp1[1] : "null")) passed++; else failed++;
        double[] sp2 = AttributeMath.splitEff(0, 100, 50);
        boolean sp2ok = sp2 != null && sp2.length == 2 && sp2[0] == 0.0 && sp2[1] == 50.0;
        if (check(report, "11", "splitEff(0,100,50)=[0,50]", sp2ok, "splitEff",
                sp2 != null && sp2.length == 2 ? sp2[0] + "," + sp2[1] : "null")) passed++; else failed++;

        // 12–14: isFront/isBack
        boolean f1 = AttributeMath.isFront(0.0, 90.0);
        boolean f2 = AttributeMath.isFront(180.0, 90.0);
        boolean b1 = AttributeMath.isBack(180.0, 135.0);
        if (check(report, "12", "isFront(0,90)=true", f1, "isFront", f1)) passed++; else failed++;
        if (check(report, "13", "isFront(180,90)=false", !f2, "isFront", f2)) passed++; else failed++;
        if (check(report, "14", "isBack(180,135)=true", b1, "isBack", b1)) passed++; else failed++;

        // 15: WP/SP/HPow референсы на 40 ур.
        // Воин: baseWP=30 + STR 60×1.5 + AGI 30×0.5 = 30+90+15 = 135
        // Охотник: baseWP=35 + 90 + 15 = 140; разбойник: 30+90+15=135
        // Маг: baseSP=30 + INT 60×1.5 = 120
        // Жрец: baseHP=25 + INT 60×1.4 = 109
        double wpWarrior = PowerService.weaponPowerFormula(30, 60, 30, 1.5, 0.5);
        double spMage = PowerService.spellPowerFormula(30, 60, 1.5);
        double hpowPriest = PowerService.healPowerFormula(25, 60, 1.4);
        boolean wp15 = Math.abs(wpWarrior - 135.0) < 1e-6;
        boolean sp15 = Math.abs(spMage - 120.0) < 1e-6;
        boolean hp15 = Math.abs(hpowPriest - 109.0) < 1e-6;
        if (check(report, "15a", "WP(воин 40 ур.)=135", wp15, "weaponPowerFormula", wpWarrior)) passed++; else failed++;
        if (check(report, "15b", "SP(маг 40 ур.)=120", sp15, "spellPowerFormula", spMage)) passed++; else failed++;
        if (check(report, "15c", "HPow(жрец 40 ур.)=109", hp15, "healPowerFormula", hpowPriest)) passed++; else failed++;

        // 16: анти-ваншот cappedDamage (pure-статик)
        double c1 = CombatService.cappedDamage(9999, 1300, 35.0);
        double c2 = CombatService.cappedDamage(100, 1300, 35.0);
        double c3 = CombatService.cappedDamage(500, 1000, 0.0);
        boolean c1ok = Math.abs(c1 - 455.0) < 1e-6;
        boolean c2ok = c2 == 100.0;
        boolean c3ok = c3 == 500.0;
        if (check(report, "16a", "capped(9999,1300,35)=455", c1ok, "cappedDamage", c1)) passed++; else failed++;
        if (check(report, "16b", "capped(100,1300,35)=100", c2ok, "cappedDamage", c2)) passed++; else failed++;
        if (check(report, "16c", "capped(500,1000,0)=500 (кап выкл)", c3ok, "cappedDamage", c3)) passed++; else failed++;

        // Итог
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
