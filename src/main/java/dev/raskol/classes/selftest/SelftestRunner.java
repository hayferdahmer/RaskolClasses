// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.selftest;

import dev.raskol.classes.RaskolClasses;
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
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 1.6.13: headless-самотестирование плагина (/rc selftest).
 * 1.7.0 пакет 4: итого 14 групп проверок:
 *   1–7  — резисты/клампы/реестр/PDC/спек/конфиг/fx (fx печатает точных виновников);
 *   8    — HP-формула (воин 240 / маг 180 при str=60, level=40);
 *   9    — DR: raw 80→70, 100→75, 200→75 (hard-cap);
 *   10   — AGI-основные: dodge 33.33 + refund 5.63 = 38.96; parry raw 11.76;
 *   11   — STR-реген (нерф 1.7.0.4, perStr 0.025): 1.5 вне боя, 0.525 в бою,
 *          кап 3.0 при str=1000/maxHp=200;
 *   12   — углы: фронт 0° (isFront=true, isBack=false), спина 180° (наоборот),
 *          бок 90° = граница фронта (isFront=true);
 *   13   — крит мили: AGI 55 → 7.75%; кап 40 при AGI 1000;
 *   14   — крит магии: INT 60 main → 10.2%; без main-mult → 6.8%; кап 35.
 * Ничего не мутирует; все проверки — чистая математика AttributeMath + реестры.
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
        dev.raskol.classes.ability.AbilityDef def =
                plugin.getAbilities().getBySlot(PlayerClass.WARRIOR, 1);
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

        // 7. Валидность fx-каталога С ПЕЧАТЬЮ ВИНОВНИКОВ
        List<String> fxBad = new ArrayList<>();
        ConfigurationSection vfx = plugin.getConfig().getConfigurationSection("vfx");
        if (vfx != null) {
            for (String key : vfx.getKeys(false)) {
                if (key.equals("proc") || key.equals("trails")) {
                    continue;
                }
                ConfigurationSection entry = vfx.getConfigurationSection(key);
                if (entry != null) {
                    collectFxProblems(plugin, fxBad, "vfx." + key, entry);
                }
            }
            ConfigurationSection proc = vfx.getConfigurationSection("proc");
            if (proc != null) {
                for (String key : proc.getKeys(false)) {
                    ConfigurationSection entry = proc.getConfigurationSection(key);
                    if (entry != null) {
                        collectFxProblems(plugin, fxBad, "vfx.proc." + key, entry);
                    }
                }
            }
        }
        if (fxBad.isEmpty()) {
            pass++;
            sender.sendMessage(Component.text(
                    "[PASS] Fx-каталог: все имена звуков/партиклов резолвятся",
                    NamedTextColor.GREEN));
        } else {
            fail++;
            sender.sendMessage(Component.text(
                    "[FAIL] Fx-каталог: " + fxBad.size() + " неизвестных имён",
                    NamedTextColor.RED));
            for (String bad : fxBad) {
                sender.sendMessage(Component.text("        • " + bad,
                        NamedTextColor.RED));
            }
        }

        // 8. HP-формула: воин (STR основной) и маг (STR не основной)
        double hpWarrior = AttributeMath.maxHp(60.0, 40.0, true, 2.0, 1.0, 1.0);
        double hpMage = AttributeMath.maxHp(60.0, 40.0, false, 2.0, 1.0, 1.0);
        if (Math.abs(hpWarrior - 240.0) < 0.01 && Math.abs(hpMage - 180.0) < 0.01) {
            pass++;
            sender.sendMessage(Component.text(
                    "[PASS] HP-формула: воин 240, маг 180 (str=60, level=40)",
                    NamedTextColor.GREEN));
        } else {
            fail++;
            sender.sendMessage(Component.text(
                    "[FAIL] HP-формула: воин=" + hpWarrior + " (ожидалось 240), маг="
                            + hpMage + " (ожидалось 180)",
                    NamedTextColor.RED));
        }

        // 9. DR: soft-cap 60, dr-factor 0.5, hard-cap 75
        double dr80 = AttributeMath.applyDR(80.0, 60.0, 0.5, 75.0);
        double dr100 = AttributeMath.applyDR(100.0, 60.0, 0.5, 75.0);
        double dr200 = AttributeMath.applyDR(200.0, 60.0, 0.5, 75.0);
        if (Math.abs(dr80 - 70.0) < 0.01
                && Math.abs(dr100 - 75.0) < 0.01
                && Math.abs(dr200 - 75.0) < 0.01) {
            pass++;
            sender.sendMessage(Component.text(
                    "[PASS] DR: raw 80→70, 100→75, 200→75 (hard-cap)",
                    NamedTextColor.GREEN));
        } else {
            fail++;
            sender.sendMessage(Component.text(
                    "[FAIL] DR: dr80=" + dr80 + ", dr100=" + dr100 + ", dr200=" + dr200,
                    NamedTextColor.RED));
        }

        // 10. AGI-основные: микро-парирование + refund половины потерянного в dodge
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
                            + String.format(Locale.ROOT, "%.2f", parry),
                    NamedTextColor.GREEN));
        } else {
            fail++;
            sender.sendMessage(Component.text(
                    "[FAIL] AGI-основные: dodge=" + dodge + ", parry=" + parry
                            + ", refund=" + refund + ", sum=" + dodgeWithRefund,
                    NamedTextColor.RED));
        }

        // 11. STR-реген (нерф 1.7.0.4, perStr 0.025):
        //     str=60 → 1.5 вне боя; ×0.35 → 0.525 в бою;
        //     кап: str=1000, perStr 0.05, maxHp 200 → rate 50 → cap 3.0
        double regenOut = AttributeMath.strRegenPerSecond(60.0, 0.025, 235.0, false, 0.35, 1.5);
        double regenIn = AttributeMath.strRegenPerSecond(60.0, 0.025, 235.0, true, 0.35, 1.5);
        double regenCap = AttributeMath.strRegenPerSecond(1000.0, 0.05, 200.0, false, 1.0, 1.5);
        if (Math.abs(regenOut - 1.5) < 0.01
                && Math.abs(regenIn - 0.525) < 0.01
                && Math.abs(regenCap - 3.0) < 0.01) {
            pass++;
            sender.sendMessage(Component.text(
                    "[PASS] STR-реген (нерф ×0.5): 1.5 вне боя, 0.525 в бою, кап 3.0",
                    NamedTextColor.GREEN));
        } else {
            fail++;
            sender.sendMessage(Component.text(
                    "[FAIL] STR-реген: out=" + regenOut + ", in=" + regenIn
                            + ", cap=" + regenCap,
                    NamedTextColor.RED));
        }

        // 12. Углы фронт/спина
        double aFront = AttributeMath.angleToAttacker(0, 1, 0, 1);
        double aBack = AttributeMath.angleToAttacker(0, 1, 0, -1);
        double aSide = AttributeMath.angleToAttacker(0, 1, 1, 0);
        boolean anglesOk = Math.abs(aFront) < 0.01
                && Math.abs(aBack - 180.0) < 0.01
                && Math.abs(aSide - 90.0) < 0.01
                && AttributeMath.isFront(aFront, 90.0) && !AttributeMath.isBack(aFront, 135.0)
                && !AttributeMath.isFront(aBack, 90.0) && AttributeMath.isBack(aBack, 135.0)
                && AttributeMath.isFront(aSide, 90.0) && !AttributeMath.isBack(aSide, 135.0);
        if (anglesOk) {
            pass++;
            sender.sendMessage(Component.text(
                    "[PASS] Углы: фронт 0° (front=true/back=false), спина 180° (наоборот), бок 90° = граница фронта",
                    NamedTextColor.GREEN));
        } else {
            fail++;
            sender.sendMessage(Component.text(
                    "[FAIL] Углы: front=" + aFront + ", back=" + aBack + ", side=" + aSide,
                    NamedTextColor.RED));
        }

        // 13. Крит мили (пакет 4): AGI 55 → 5 + 55×0.05 = 7.75%; кап 40 при AGI 1000
        double critMelee = AttributeMath.critMelee(55.0, 5.0, 0.05, 40.0);
        double critMeleeCap = AttributeMath.critMelee(1000.0, 5.0, 0.05, 40.0);
        if (Math.abs(critMelee - 7.75) < 0.01 && Math.abs(critMeleeCap - 40.0) < 0.01) {
            pass++;
            sender.sendMessage(Component.text(
                    "[PASS] Крит мили: AGI 55 → 7.75%; кап 40 (AGI 1000)",
                    NamedTextColor.GREEN));
        } else {
            fail++;
            sender.sendMessage(Component.text(
                    "[FAIL] Крит мили: crit=" + critMelee + ", cap=" + critMeleeCap,
                    NamedTextColor.RED));
        }

        // 14. Крит магии (пакет 4): INT 60 main → (5+1.8)×1.5 = 10.2%;
        //     без main-mult → 6.8%; кап 35 при INT 2000
        double critSpellMain = AttributeMath.critSpell(60.0, true, 5.0, 0.03, 1.5, 35.0);
        double critSpellOff = AttributeMath.critSpell(60.0, false, 5.0, 0.03, 1.5, 35.0);
        double critSpellCap = AttributeMath.critSpell(2000.0, true, 5.0, 0.03, 1.5, 35.0);
        if (Math.abs(critSpellMain - 10.2) < 0.01
                && Math.abs(critSpellOff - 6.8) < 0.01
                && Math.abs(critSpellCap - 35.0) < 0.01) {
            pass++;
            sender.sendMessage(Component.text(
                    "[PASS] Крит магии: INT 60 main → 10.2%, без main → 6.8%, кап 35",
                    NamedTextColor.GREEN));
        } else {
            fail++;
            sender.sendMessage(Component.text(
                    "[FAIL] Крит магии: main=" + critSpellMain + ", off=" + critSpellOff
                            + ", cap=" + critSpellCap,
                    NamedTextColor.RED));
        }

        int total = pass + fail;
        sender.sendMessage(Component.text("Итог: " + pass + "/" + total + " PASS",
                fail == 0 ? NamedTextColor.GREEN : NamedTextColor.RED));
    }

    /** Точный виновник: путь ключа + значение, которое не резолвится. */
    private static void collectFxProblems(RaskolClasses plugin, List<String> sink,
                                          String path, ConfigurationSection entry) {
        String sound = entry.getString("cast-sound", "");
        if (sound != null && !sound.isEmpty()
                && plugin.getFx().resolveSound(sound) == null) {
            sink.add(path + ".cast-sound = " + sound);
        }
        String particle = entry.getString("cast-particle", "");
        if (particle != null && !particle.isEmpty()
                && plugin.getFx().resolveParticle(particle) == null) {
            sink.add(path + ".cast-particle = " + particle);
        }
    }
}
