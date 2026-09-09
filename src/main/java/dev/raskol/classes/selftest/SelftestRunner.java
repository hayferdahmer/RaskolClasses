// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.selftest;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.ability.AbilityDef;
import dev.raskol.classes.attribute.AttributeMath;
import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.combat.DamageProfile;
import dev.raskol.classes.combat.ResistService;
import dev.raskol.classes.install.InstallationType;
import dev.raskol.classes.spec.Spec;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * 1.6.13: headless-самотестирование плагина (/rc selftest).
 * 1.7.0 пакет 1: добавлены три группы проверок формул атрибутов AttributeMath
 * (HP воина, DR свыше soft-cap, микро-парирование AGI-основных + refund в dodge);
 * итого 10 групп проверок. Ничего не мутирует.
 */
public final class SelftestRunner {

    private SelftestRunner() {
    }

    public static void run(RaskolClasses plugin, CommandSender sender) {
        int pass = 0;
        int fail = 0;
        sender.sendMessage(Component.text("--- RaskolClasses Selftest ---",
                NamedTextColor.GOLD));

        Player probe = sender instanceof Player p
                ? p
                : Bukkit.getOnlinePlayers().stream().findFirst().orElse(null);

        // 1. Согласованность формулы: simulateTaken == 100 × factor
        if (probe == null) {
            sender.sendMessage(Component.text(
                    "[SKIP] Формула резистов: нет онлайн-игрока для живой проверки",
                    NamedTextColor.GRAY));
        } else if (plugin.getResists().disabledIn(probe.getWorld())) {
            sender.sendMessage(Component.text(
                    "[SKIP] Формула резистов: игрок в мире из resist.disabled-worlds",
                    NamedTextColor.GRAY));
        } else {
            UUID uuid = probe.getUniqueId();
            double fp = plugin.getResists().physicalFactor(uuid);
            double fm = plugin.getResists().magicFactor(uuid);
            double simPhys = plugin.getCombat().simulateTaken(probe,
                    DamageProfile.physical(100));
            double simMagic = plugin.getCombat().simulateTaken(probe,
                    DamageProfile.magic(100));
            boolean ok = Math.abs(simPhys - 100.0 * fp) < 0.01
                    && Math.abs(simMagic - 100.0 * fm) < 0.01
                    && fp >= 0.0 && fp <= 1.0 && fm >= 0.0 && fm <= 1.0;
            if (ok) {
                pass++;
                sender.sendMessage(Component.text(
                        "[PASS] Формула резистов: симулятор согласован с факторами",
                        NamedTextColor.GREEN));
            } else {
                fail++;
                sender.sendMessage(Component.text(
                        "[FAIL] Формула резистов: расхождение симулятора и факторов ("
                                + "simPhys=" + simPhys + ", fp=" + fp
                                + ", simMagic=" + simMagic + ", fm=" + fm + ")",
                        NamedTextColor.RED));
            }
        }

        // 2. Клампы и NaN-гарды (headless, без мутаций)
        UUID clampUuid = probe != null ? probe.getUniqueId() : UUID.randomUUID();
        ResistService rs = plugin.getResists();
        double capZero = rs.breakdown(clampUuid, 0.0).physicalTotal();
        double capHundred = rs.breakdown(clampUuid, 100.0).physicalTotal();
        double factorNaN = rs.physicalFactor(clampUuid, Double.NaN);
        boolean clampOk = capZero == 0.0 && capHundred <= 100.0
                && Double.isFinite(factorNaN);
        if (clampOk) {
            pass++;
            sender.sendMessage(Component.text(
                    "[PASS] Клампы: cap=0 → 0, cap=100 → ≤100, NaN-cap → конечный фактор",
                    NamedTextColor.GREEN));
        } else {
            fail++;
            sender.sendMessage(Component.text(
                    "[FAIL] Клампы: capZero=" + capZero + ", capHundred=" + capHundred
                            + ", factorNaN=" + factorNaN,
                    NamedTextColor.RED));
        }

        // 3. Целостность реестра кастеров
        var registryProblems = plugin.getAbilities().integrityProblems();
        if (registryProblems.isEmpty()) {
            pass++;
            sender.sendMessage(Component.text(
                    "[PASS] Реестр: у всех способностей есть кастеры, targeted-пары полны, сирот нет",
                    NamedTextColor.GREEN));
        } else {
            fail++;
            sender.sendMessage(Component.text(
                    "[FAIL] Реестр: " + registryProblems.size() + " проблем(ы)",
                    NamedTextColor.RED));
            for (String problem : registryProblems) {
                sender.sendMessage(Component.text("        • " + problem,
                        NamedTextColor.RED));
            }
        }

        // 4. PDC-раундтрип токенов (временные предметы, без мира)
        AbilityDef def = plugin.getAbilities().getBySlot(PlayerClass.WARRIOR, 1);
        ItemStack abilityScroll = def != null
                ? plugin.getTokens().create(def, PlayerClass.WARRIOR)
                : null;
        ItemStack plain = new ItemStack(Material.STONE);
        Spec spec = Spec.values()[0];
        ItemStack specScroll = plugin.getSpecToken().create(spec, PlayerClass.WARRIOR);
        InstallationType type = InstallationType.values()[0];
        ItemStack installScroll = plugin.getInstallToken().create(type, PlayerClass.WARRIOR);
        boolean pdcOk = abilityScroll != null
                && def.id().equals(plugin.getTokens().readId(abilityScroll))
                && plugin.getTokens().readId(plain) == null
                && !plugin.getSpecToken().isSpecScroll(plain)
                && !plugin.getInstallToken().isInstallScroll(plain)
                && plugin.getSpecToken().readSpec(specScroll) == spec
                && plugin.getSpecToken().isSpecScroll(specScroll)
                && plugin.getInstallToken().readType(installScroll) == type
                && plugin.getInstallToken().isInstallScroll(installScroll);
        if (pdcOk) {
            pass++;
            sender.sendMessage(Component.text(
                    "[PASS] PDC: раундтрип ability/spec/install-токенов, пустой предмет не читается",
                    NamedTextColor.GREEN));
        } else {
            fail++;
            sender.sendMessage(Component.text(
                    "[FAIL] PDC: раундтрип токенов нарушен",
                    NamedTextColor.RED));
        }

        // 5. Полнота спек-реестра
        boolean specOk = true;
        for (Spec s : Spec.values()) {
            if (plugin.getSpecRegistry().get(s) == null) {
                specOk = false;
                sender.sendMessage(Component.text(
                        "        • спек " + s.id() + " не имеет SpecDef",
                        NamedTextColor.RED));
            }
        }
        if (specOk) {
            pass++;
            sender.sendMessage(Component.text(
                    "[PASS] Спек-реестр: у всех спек есть SpecDef",
                    NamedTextColor.GREEN));
        } else {
            fail++;
            sender.sendMessage(Component.text(
                    "[FAIL] Спек-реестр: отсутствуют SpecDef (см. выше)",
                    NamedTextColor.RED));
        }

        // 6. Конфиг-валидация резистов/урона
        int cfgProblems = plugin.getConfigValidator().validate();
        if (cfgProblems == 0) {
            pass++;
            sender.sendMessage(Component.text(
                    "[PASS] Конфиг: резисты/урон валидны (диапазоны, типы, конечность)",
                    NamedTextColor.GREEN));
        } else {
            fail++;
            sender.sendMessage(Component.text(
                    "[FAIL] Конфиг: " + cfgProblems + " проблем(ы) — см. WARNING в консоли",
                    NamedTextColor.RED));
        }

        // 7. Валидность fx-каталога
        int fxProblems = plugin.getFx().countProblems();
        if (fxProblems == 0) {
            pass++;
            sender.sendMessage(Component.text(
                    "[PASS] Fx-каталог: все имена звуков/партиклов резолвятся",
                    NamedTextColor.GREEN));
        } else {
            fail++;
            sender.sendMessage(Component.text(
                    "[FAIL] Fx-каталог: " + fxProblems + " неизвестных имён",
                    NamedTextColor.RED));
        }

        // --- 1.7.0 пакет 1: формулы атрибутов AttributeMath (pure, headless) ---

        // 8. HP-формула воина (STR основной): 20 + STR×perStr + level×perLevel + STR×mainBonus
        //    При perStr=2, mainBonus=1, STR=60, level=40 → 20 + 120 + 40 + 60 = 240
        double hpWarrior = AttributeMath.maxHp(60.0, 40.0, true, 2.0, 1.0, 1.0);
        double hpMage = AttributeMath.maxHp(60.0, 40.0, false, 2.0, 1.0, 1.0);
        if (Math.abs(hpWarrior - 240.0) < 0.01 && Math.abs(hpMage - 200.0) < 0.01) {
            pass++;
            sender.sendMessage(Component.text(
                    "[PASS] HP-формула: воин 240, маг 200 (STR=60, level=40)",
                    NamedTextColor.GREEN));
        } else {
            fail++;
            sender.sendMessage(Component.text(
                    "[FAIL] HP-формула: воин=" + hpWarrior + " (ожидалось 240), маг=" + hpMage + " (ожидалось 200)",
                    NamedTextColor.RED));
        }

        // 9. DR: raw=80 → eff=70 при softCap=60, drFactor=0.5, hardCap=75
        //    raw=100 → eff=80 (60 + (100-60)×0.5 = 80); raw=200 → eff=75 (жёсткий кап)
        double dr80 = AttributeMath.applyDR(80.0, 60.0, 0.5, 75.0);
        double dr100 = AttributeMath.applyDR(100.0, 60.0, 0.5, 75.0);
        double dr200 = AttributeMath.applyDR(200.0, 60.0, 0.5, 75.0);
        if (Math.abs(dr80 - 70.0) < 0.01
                && Math.abs(dr100 - 80.0) < 0.01
                && Math.abs(dr200 - 75.0) < 0.01) {
            pass++;
            sender.sendMessage(Component.text(
                    "[PASS] DR: raw 80→70, 100→80, 200→75 (hard-cap)",
                    NamedTextColor.GREEN));
        } else {
            fail++;
            sender.sendMessage(Component.text(
                    "[FAIL] DR: dr80=" + dr80 + ", dr100=" + dr100 + ", dr200=" + dr200,
                    NamedTextColor.RED));
        }

        // 10. AGI-основные: raw-dodge с refund половины потерянного парирования
        //    dodgeRaw(AGI=50, k=100) = 100×50/150 = 33.33
        //    parryRaw(STR=20, k=150) = 100×20/170 = 11.76
        //    lost = 11.76 − 0.5 (micro) = 11.26
        //    refund = 11.26 × 0.5 = 5.63
        //    итого dodge = 33.33 + 5.63 = 38.96
        double dodge = AttributeMath.dodgeRaw(50.0, 100.0);
        double parry = AttributeMath.parryRaw(20.0, 150.0);
        double micro = 0.5;
        double lost = Math.max(0.0, parry - micro);
        double refund = lost * 0.5;
        double dodgeWithRefund = dodge + refund;
        if (Math.abs(dodgeWithRefund - 38.96) < 0.5
                && parry >= 10.0 && parry <= 13.0
                && dodge >= 30.0 && dodge <= 36.0) {
            pass++;
            sender.sendMessage(Component.text(
                    "[PASS] AGI-основные: dodge=33.33 + refund=5.63 = 38.96; parry raw="
                            + String.format(java.util.Locale.ROOT, "%.2f", parry),
                    NamedTextColor.GREEN));
        } else {
            fail++;
            sender.sendMessage(Component.text(
                    "[FAIL] AGI-основные: dodge=" + dodge + ", parry=" + parry
                            + ", refund=" + refund + ", sum=" + dodgeWithRefund,
                    NamedTextColor.RED));
        }

        int total = pass + fail;
        sender.sendMessage(Component.text("Итог: " + pass + "/" + total + " PASS",
                fail == 0 ? NamedTextColor.GREEN : NamedTextColor.RED));
    }
}
