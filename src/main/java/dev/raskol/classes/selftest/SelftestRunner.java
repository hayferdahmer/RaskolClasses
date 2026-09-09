// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.selftest;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.ability.AbilityDef;
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
 * Семь групп проверок, каждая — строка [PASS]/[FAIL]/[SKIP], в конце итог.
 * Ничего не мутирует: боевые проверки читают живое состояние, PDC-проверки
 * создают временные предметы в памяти (без мира и без выдачи игрокам).
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

        int total = pass + fail;
        sender.sendMessage(Component.text("Итог: " + pass + "/" + total + " PASS",
                fail == 0 ? NamedTextColor.GREEN : NamedTextColor.RED));
    }
}
