// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.config;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.classsystem.PlayerClass;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 1.11.4 (чек 44–45): headless-валидация китов и пассив-мультипликаторов.
 *  - Чек 44: sanity конфиговых чисел всех способностей (base/coeff/cost/
 *    cooldown/unlock/duration/power). Ловит опечатки и дрейфт чисел до деплоя.
 *  - Чек 45: RUNBOOK-замки пассив-мультипликаторов (execute ×3.0, predator ×1.2,
 *    grace ×1.15, sadism +3, black_mass 6.66% и т.д.). Любое отклонение → fail.
 *
 * 1.14.0-fix (чек 44): self-buff способности (без урона) исключены из обязательности
 *    base — у них его по дизайну нет (BERSERKERGANG/SWALLOW/HERMES_STEP/
 *    SHADOW_CLOAK/RUIN_SEAL). Damage-спеллы по-прежнему требуют base строго.
 *    coeff/cost оставлены как в оригинале (optional) — минимальный дифф, ноль риска.
 * 1.14.0-fix (чек 45): длительность яда пассивки сверяется с единственным источником
 *    истины dots.poison_passive.duration (=2), а не с несуществующим дублем
 *    poisoned_blades.duration-seconds.
 *
 * Публичный API:
 *  - validateAbilities(plugin) — список проблем по всем 6 классам;
 *  - validatePassiveMults(plugin) — список проблем по RUNBOOK-числам;
 *  - оба метода safe: не кидают исключения, не зависят от Bukkit-рантайма.
 *
 * Вызывается из SelftestRunner (чеки 44/45) и (опционально) из ConfigValidator.
 */
public final class KitSanity {

    private KitSanity() {
    }

    private static final Set<String> VALID_POWERS = Set.of("wp", "sp", "hpow");

    /** 1.14.0-fix (чек 44): способности без урона — base не обязателен. */
    private static final Set<String> SELF_BUFF = Set.of(
            "berserkergang", "swallow", "hermes_step", "shadow_cloak", "ruin_seal");

    /* ------------------------------ ЧЕК 44: sanity китов ------------------------------ */

    /** Возвращает список проблем; пустой = OK. */
    public static List<String> validateAbilities(RaskolClasses plugin) {
        List<String> problems = new ArrayList<>();
        for (PlayerClass pc : PlayerClass.values()) {
            String path = "classes." + pc.name() + ".abilities";
            ConfigurationSection section = plugin.getConfig().getConfigurationSection(path);
            if (section == null) {
                problems.add(pc.name() + ": секция " + path + " отсутствует");
                continue;
            }
            for (String id : section.getKeys(false)) {
                String p = path + "." + id;
                boolean selfBuff = SELF_BUFF.contains(id);
                // 1.14.0-fix (чек 44): base обязателен только для damage-способностей
                checkDoubleRange(problems, plugin, p + ".base", 0.0, Double.MAX_VALUE, selfBuff);
                checkDoubleRange(problems, plugin, p + ".coeff", 0.0, Double.MAX_VALUE, true);
                checkDoubleRange(problems, plugin, p + ".cost", 0.0, Double.MAX_VALUE, true);
                checkIntRange(problems, plugin, p + ".cooldown", 1, 3600);
                checkIntRange(problems, plugin, p + ".unlock", 1, 80);
                if (plugin.getConfig().contains(p + ".duration")) {
                    checkDoubleRange(problems, plugin, p + ".duration", 0.0, 600.0, false);
                }
                if (plugin.getConfig().contains(p + ".power")) {
                    String pw = plugin.getConfig().getString(p + ".power", "");
                    if (!VALID_POWERS.contains(pw)) {
                        problems.add(pc.name() + "." + id + ": power=\"" + pw
                                + "\" вне {" + String.join(",", VALID_POWERS) + "}");
                    }
                }
                if (plugin.getConfig().contains(p + ".execute-mult")) {
                    checkDoubleRange(problems, plugin, p + ".execute-mult", 1.5, 10.0, false);
                }
                if (plugin.getConfig().contains(p + ".threshold")) {
                    checkDoubleRange(problems, plugin, p + ".threshold", 0.01, 0.99, false);
                }
            }
        }
        return problems;
    }

    /* ------------------------------ ЧЕК 45: RUNBOOK-мульты ------------------------------ */

    /** Возвращает список проблем; пустой = OK. */
    public static List<String> validatePassiveMults(RaskolClasses plugin) {
        List<String> problems = new ArrayList<>();
        expectDoubleEq(problems, plugin,
                "classes.WARRIOR.passives.execute_passive.multiplier", 3.0);
        expectDoubleEq(problems, plugin,
                "classes.WARRIOR.passives.execute_passive.threshold", 0.20);
        expectDoubleEq(problems, plugin,
                "classes.HUNTER.passives.predator.multiplier", 1.20);
        expectDoubleEq(problems, plugin,
                "classes.HUNTER.passives.predator.threshold", 0.80);
        expectDoubleEq(problems, plugin,
                "classes.PRIEST.passives.grace.multiplier", 1.15);
        expectDoubleEq(problems, plugin,
                "classes.ROGUE.passives.poisoned_blades.chance", 0.30);
        // 1.14.0-fix (чек 45): длительность яда = единственный источник истины в dots.*
        expectIntEq(problems, plugin, "dots.poison_passive.duration", 2);
        expectDoubleEq(problems, plugin,
                "classes.ROGUE.passives.sadism.bonus", 3.0);
        expectDoubleEq(problems, plugin,
                "classes.WARLOCK.passives.black_mass.lifesteal", 0.0666);
        expectDoubleEq(problems, plugin,
                "classes.WARLOCK.passives.black_mass.reflect", 0.0666);
        return problems;
    }

    /* ------------------------------ хелперы ------------------------------ */

    private static void checkDoubleRange(List<String> problems, RaskolClasses plugin,
                                         String path, double min, double max,
                                         boolean optional) {
        if (!plugin.getConfig().contains(path)) {
            if (!optional) {
                problems.add(path + ": ключ отсутствует");
            }
            return;
        }
        double v = plugin.getConfig().getDouble(path, Double.NaN);
        if (!Double.isFinite(v)) {
            problems.add(path + ": некорректное число");
        } else if (v < min || v > max) {
            problems.add(String.format(Locale.ROOT,
                    "%s: %.3f вне диапазона [%.2f, %.2f]", path, v, min, max));
        }
    }

    private static void checkIntRange(List<String> problems, RaskolClasses plugin,
                                      String path, int min, int max) {
        if (!plugin.getConfig().contains(path)) {
            problems.add(path + ": ключ отсутствует");
            return;
        }
        int v = plugin.getConfig().getInt(path, -1);
        if (v < min || v > max) {
            problems.add(String.format(Locale.ROOT,
                    "%s: %d вне [%d, %d]", path, v, min, max));
        }
    }

    private static void expectDoubleEq(List<String> problems, RaskolClasses plugin,
                                       String path, double expected) {
        if (!plugin.getConfig().contains(path)) {
            problems.add(path + ": ключ отсутствует (ожидалось " + expected + ")");
            return;
        }
        double v = plugin.getConfig().getDouble(path, Double.NaN);
        if (!Double.isFinite(v) || Math.abs(v - expected) > 1e-4) {
            problems.add(String.format(Locale.ROOT,
                    "%s: %.4f ≠ ожидание %.4f", path, v, expected));
        }
    }

    private static void expectIntEq(List<String> problems, RaskolClasses plugin,
                                    String path, int expected) {
        if (!plugin.getConfig().contains(path)) {
            problems.add(path + ": ключ отсутствует (ожидалось " + expected + ")");
            return;
        }
        int v = plugin.getConfig().getInt(path, -1);
        if (v != expected) {
            problems.add(path + ": " + v + " ≠ ожидание " + expected);
        }
    }
}
