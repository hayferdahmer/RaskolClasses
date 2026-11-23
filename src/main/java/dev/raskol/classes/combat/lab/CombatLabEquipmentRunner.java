// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.combat.lab;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import org.bukkit.configuration.file.YamlConfiguration;

import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.item.config.ItemConfigLoader;
import dev.raskol.classes.item.model.ItemRarity;
import dev.raskol.classes.item.service.ItemRegistry;
import dev.raskol.classes.spec.Spec;

/** CLI-раннер equipment-aware профиля на настоящем items.yml и production item registry. */
public final class CombatLabEquipmentRunner {

    private static final int MINIMUM_ITERATIONS = 1200;
    private static final int BOOTSTRAP_REPLICATES = 400;
    private static final int[] LEVELS = {1, 15, 30, 45, 60};
    private static final ItemRarity RARITY = ItemRarity.EPIC;

    private CombatLabEquipmentRunner() {}

    public static void main(String[] args) throws Exception {
        Arguments options = Arguments.parse(args);
        YamlConfiguration config =
                YamlConfiguration.loadConfiguration(options.config().toFile());
        List<String> warnings = new ArrayList<>();
        ItemRegistry registry = ItemConfigLoader.loadHeadless(options.items().toFile(), warnings::add);
        if (registry.size() == 0) {
            throw new IllegalStateException("Production items.yml не загрузил ни одного предмета");
        }
        if (!warnings.isEmpty()) {
            warnings.forEach(warning -> System.err.println("items.yml warning: " + warning));
        }
        String report = run(config, registry, options.seed(), options.iterations(), options.sourceCommit());
        Path parent = options.output().toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        Files.writeString(options.output(), report);
        System.out.println("Combat Lab equipment report written: " + options.output());
        System.out.println("Loaded production item definitions: " + registry.size());
        System.out.println("Scenario set: " + CombatLabDuelEngine.EQUIPMENT_PROFILE_ID);
    }

    public static String run(
            YamlConfiguration config, ItemRegistry registry, long seed, int iterations, String sourceCommit) {
        if (config == null || registry == null) throw new IllegalArgumentException("config и registry обязательны");
        if (seed < 0L || iterations < MINIMUM_ITERATIONS) {
            throw new IllegalArgumentException("seed должен быть >= 0, iterations >= " + MINIMUM_ITERATIONS);
        }
        if (sourceCommit == null || !sourceCommit.matches("[0-9a-f]{40}")) {
            throw new IllegalArgumentException("Нужен полный source commit SHA (40 lowercase hex)");
        }

        CombatLabDuelEngine.Rules rules = CombatLabDuelEngine.Rules.from(config);
        List<String> scenarios = new ArrayList<>();
        for (int level : LEVELS) {
            for (Spec firstSpec : Spec.values()) {
                for (Spec secondSpec : Spec.values()) {
                    if (firstSpec == secondSpec) continue;
                    PlayerClass firstClass = firstSpec.playerClass();
                    PlayerClass secondClass = secondSpec.playerClass();
                    long firstEquipmentSeed = mixSeed(seed, level, firstSpec.ordinal(), RARITY.ordinal());
                    long secondEquipmentSeed = mixSeed(seed, level, secondSpec.ordinal(), RARITY.ordinal());
                    CombatLabEquipmentSnapshot firstEquipment = CombatLabEquipmentSnapshot.from(
                            registry, firstClass, level, RARITY, firstSpec.id(), firstEquipmentSeed);
                    CombatLabEquipmentSnapshot secondEquipment = CombatLabEquipmentSnapshot.from(
                            registry, secondClass, level, RARITY, secondSpec.id(), secondEquipmentSeed);
                    CombatLabCombatantSnapshot first =
                            CombatLabCombatantSnapshot.from(config, firstClass, level, firstEquipment);
                    CombatLabCombatantSnapshot second =
                            CombatLabCombatantSnapshot.from(config, secondClass, level, secondEquipment);
                    long scenarioSeed = mixSeed(seed, level, firstSpec.ordinal(), secondSpec.ordinal());
                    scenarios.add(scenarioJson(
                            firstSpec, secondSpec, first, second, firstEquipment, secondEquipment, rules, scenarioSeed, iterations));
                }
            }
        }
        return "{\n"
                + "  \"schema_version\": \"1.2\",\n"
                + "  \"report_type\": \"combat-lab-equipment-report\",\n"
                + "  \"simulator_version\": \"headless-equipment-duel-0.3.0\",\n"
                + "  \"scenario_set\": \"" + CombatLabDuelEngine.EQUIPMENT_PROFILE_ID + "\",\n"
                + "  \"simulation_scope\": \"production items.yml; 18 specialization-locked five-piece sets, Paper 26.2 material attack-speed reference profiles, full-draw bow and crossbow reload cadence, production affix roller and set bonuses; physical/magic auto-attacks and WeaponMastery only\",\n"
                + "  \"source_commit\": \"" + sourceCommit + "\",\n"
                + "  \"seed\": " + seed + ",\n"
                + "  \"iterations_per_scenario\": " + iterations + ",\n"
                + "  \"generated_at\": \"" + Instant.now() + "\",\n"
                + "  \"loaded_item_definitions\": " + registry.size() + ",\n"
                + "  \"specialization_count\": " + Spec.values().length + ",\n"
                + "  \"rarity\": \"" + RARITY.name() + "\",\n"
                + "  \"scenarios\": [\n"
                + String.join(",\n", scenarios)
                + "\n  ]\n"
                + "}\n";
    }

    private static String scenarioJson(
            Spec firstSpec,
            Spec secondSpec,
            CombatLabCombatantSnapshot first,
            CombatLabCombatantSnapshot second,
            CombatLabEquipmentSnapshot firstEquipment,
            CombatLabEquipmentSnapshot secondEquipment,
            CombatLabDuelEngine.Rules rules,
            long seed,
            int iterations) {
        double[] ttk = new double[iterations];
        double[] dps = new double[iterations];
        double[] hps = new double[iterations];
        int wins = 0;
        long hits = 0L, crits = 0L, avoids = 0L;
        List<String> violations = new ArrayList<>();
        for (int i = 0; i < iterations; i++) {
            CombatLabDuelEngine.DuelResult result =
                    CombatLabDuelEngine.duelEquipped(first, second, rules, mixSeed(seed, i, 0, 0));
            if (result.firstPlayerWon()) wins++;
            hits += result.firstHits();
            crits += result.firstCrits();
            avoids += result.firstAvoids();
            ttk[i] = result.durationSeconds();
            dps[i] = result.firstDamageDealt() / Math.max(0.001, result.durationSeconds());
            hps[i] = result.firstHealing() / Math.max(0.001, result.durationSeconds());
            if (!Double.isFinite(ttk[i])
                    || !Double.isFinite(dps[i])
                    || !Double.isFinite(hps[i])
                    || ttk[i] <= 0.0
                    || dps[i] < 0.0
                    || hps[i] < 0.0) {
                violations.add("non_finite_or_negative_result");
                break;
            }
        }
        if (violations.isEmpty()
                && first.level() >= 30
                && (firstEquipment.equippedItemIds().size() != 5
                        || secondEquipment.equippedItemIds().size() != 5
                        || !firstEquipment.activeSetIds().contains(firstSpec.id() + "_regalia")
                        || !secondEquipment.activeSetIds().contains(secondSpec.id() + "_regalia"))) {
            violations.add("expected_specialization_set_missing_at_level_" + first.level());
        }
        String id = "pvp_equipment_" + firstSpec.id() + "_vs_" + secondSpec.id()
                + "_level_" + first.level();
        if (!violations.isEmpty()) {
            return scenarioObject(
                    id, firstSpec.id(), secondSpec.id(), iterations, violations,
                    firstEquipment, secondEquipment,
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        }

        Random bootstrap = new Random(mixSeed(seed, first.level(), firstSpec.ordinal(), secondSpec.ordinal()));
        double[] ttkCi = bootstrapInterval(ttk, false, bootstrap);
        double[] dpsCi = bootstrapInterval(dps, true, bootstrap);
        double[] hpsCi = bootstrapInterval(hps, true, bootstrap);
        double[] winCi = wilsonInterval(wins, iterations);
        double critRate = hits == 0 ? 0 : crits / (double) hits;
        double avoidRate = hits == 0 ? 0 : avoids / (double) hits;
        double[] critCi = wilsonInterval(crits, hits);
        double[] avoidCi = wilsonInterval(avoids, hits);
        return scenarioObject(
                id, firstSpec.id(), secondSpec.id(), iterations, violations,
                firstEquipment, secondEquipment,
                wins / (double) iterations, winCi[0], winCi[1],
                median(ttk), ttkCi[0], ttkCi[1],
                mean(dps), dpsCi[0], dpsCi[1],
                mean(hps), hpsCi[0], hpsCi[1],
                critRate, critCi[0], critCi[1],
                avoidRate, avoidCi[0], avoidCi[1]);
    }

    private static String scenarioObject(
            String id,
            String firstSpec,
            String secondSpec,
            int samples,
            List<String> violations,
            CombatLabEquipmentSnapshot firstEquipment,
            CombatLabEquipmentSnapshot secondEquipment,
            double win,
            double winLow,
            double winHigh,
            double ttk,
            double ttkLow,
            double ttkHigh,
            double dps,
            double dpsLow,
            double dpsHigh,
            double hps,
            double hpsLow,
            double hpsHigh,
            double crit,
            double critLow,
            double critHigh,
            double avoid,
            double avoidLow,
            double avoidHigh) {
        return "    {\n"
                + "      \"scenario_id\": \"" + id + "\",\n"
                + "      \"mode\": \"pvp\",\n"
                + "      \"sample_count\": " + samples + ",\n"
                + "      \"invariant_violations\": " + stringArray(violations) + ",\n"
                + "      \"equipment\": {\"first_spec\":\"" + firstSpec + "\","
                + "\"second_spec\":\"" + secondSpec + "\""
                + ",\"first_items\":" + stringArray(firstEquipment.equippedItemIds())
                + ",\"second_items\":" + stringArray(secondEquipment.equippedItemIds())
                + ",\"first_active_sets\":" + stringArray(firstEquipment.activeSetIds())
                + ",\"second_active_sets\":" + stringArray(secondEquipment.activeSetIds())
                + ",\"first_weapon_type\":\""
                + (firstEquipment.weaponType() == null ? "NONE" : firstEquipment.weaponType().name())
                + "\",\"second_weapon_type\":\""
                + (secondEquipment.weaponType() == null ? "NONE" : secondEquipment.weaponType().name())
                + "\",\"first_attack_type\":\"" + firstEquipment.autoAttackType()
                + "\",\"second_attack_type\":\"" + secondEquipment.autoAttackType()
                + "\",\"first_mastery\":" + number(firstEquipment.weaponMasteryMultiplier())
                + ",\"second_mastery\":" + number(secondEquipment.weaponMasteryMultiplier())
                + ",\"first_attack_interval_seconds\":" + number(firstEquipment.attackIntervalSeconds())
                + ",\"second_attack_interval_seconds\":" + number(secondEquipment.attackIntervalSeconds())
                + ",\"first_cadence_model\":\"" + firstEquipment.cadenceModel() + "\""
                + ",\"second_cadence_model\":\"" + secondEquipment.cadenceModel() + "\"},\n"
                + "      \"metrics\": {\n"
                + "        \"win_rate\": " + metric(win, winLow, winHigh, "wilson") + ",\n"
                + "        \"ttk_median_seconds\": " + metric(ttk, ttkLow, ttkHigh, "bootstrap_percentile") + ",\n"
                + "        \"dps_mean\": " + metric(dps, dpsLow, dpsHigh, "bootstrap_percentile") + ",\n"
                + "        \"hps_mean\": " + metric(hps, hpsLow, hpsHigh, "bootstrap_percentile") + ",\n"
                + "        \"crit_rate\": " + metric(crit, critLow, critHigh, "wilson") + ",\n"
                + "        \"avoidance_rate\": " + metric(avoid, avoidLow, avoidHigh, "wilson") + "\n"
                + "      }\n"
                + "    }";
    }

    private static String metric(double value, double low, double high, String method) {
        return "{\"value\":" + number(value) + ",\"ci95_low\":" + number(low) + ",\"ci95_high\":" + number(high)
                + ",\"ci95_method\":\"" + method + "\"}";
    }

    private static double[] wilsonInterval(long successes, long samples) {
        if (samples <= 0) return new double[] {0.0, 0.0};
        double z = 1.959963984540054, p = successes / (double) samples;
        double denominator = 1.0 + z * z / samples;
        double center = (p + z * z / (2.0 * samples)) / denominator;
        double margin = z * Math.sqrt(p * (1.0 - p) / samples + z * z / (4.0 * samples * samples)) / denominator;
        return new double[] {Math.max(0.0, center - margin), Math.min(1.0, center + margin)};
    }

    private static double[] bootstrapInterval(double[] values, boolean average, Random random) {
        double[] estimates = new double[BOOTSTRAP_REPLICATES], sample = new double[values.length];
        for (int r = 0; r < BOOTSTRAP_REPLICATES; r++) {
            for (int i = 0; i < values.length; i++) sample[i] = values[random.nextInt(values.length)];
            estimates[r] = average ? mean(sample) : median(sample);
        }
        Arrays.sort(estimates);
        return new double[] {
            estimates[(int) Math.floor((BOOTSTRAP_REPLICATES - 1) * 0.025)],
            estimates[(int) Math.ceil((BOOTSTRAP_REPLICATES - 1) * 0.975)]
        };
    }

    private static double mean(double[] values) {
        double sum = 0;
        for (double value : values) sum += value;
        return sum / values.length;
    }

    private static double median(double[] values) {
        double[] sorted = values.clone();
        Arrays.sort(sorted);
        int middle = sorted.length / 2;
        return sorted.length % 2 == 0 ? (sorted[middle - 1] + sorted[middle]) / 2.0 : sorted[middle];
    }

    private static String stringArray(List<String> values) {
        if (values.isEmpty()) return "[]";
        return "[\"" + String.join("\", \"", values) + "\"]";
    }

    private static String number(double value) {
        return String.format(Locale.ROOT, "%.8f", value);
    }

    private static long mixSeed(long base, int a, int b, int c) {
        long value = base ^ (0x9E3779B97F4A7C15L * (a + 1L));
        value ^= 0xBF58476D1CE4E5B9L * (b + 1L);
        value ^= 0x94D049BB133111EBL * (c + 1L);
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }

    private record Arguments(Path config, Path items, Path output, long seed, int iterations, String sourceCommit) {
        static Arguments parse(String[] args) {
            Path config = Path.of("src/main/resources/config.yml");
            Path items = Path.of("src/main/resources/items.yml");
            Path output = Path.of("target/combat-lab/equipment-duel-report.json");
            long seed = 20261123L;
            int iterations = MINIMUM_ITERATIONS;
            String commit = System.getenv("GITHUB_SHA");
            for (int i = 0; i < args.length; i++) {
                if (i + 1 >= args.length)
                    throw new IllegalArgumentException("Для аргумента нужен параметр: " + args[i]);
                String value = args[++i];
                switch (args[i - 1]) {
                    case "--config" -> config = Path.of(value);
                    case "--items" -> items = Path.of(value);
                    case "--output" -> output = Path.of(value);
                    case "--seed" -> seed = Long.parseLong(value);
                    case "--iterations" -> iterations = Integer.parseInt(value);
                    case "--source-commit" -> commit = value;
                    default -> throw new IllegalArgumentException("Неизвестный аргумент: " + args[i - 1]);
                }
            }
            if (commit == null) throw new IllegalArgumentException("Укажите --source-commit или задайте GITHUB_SHA");
            return new Arguments(config, items, output, seed, iterations, commit);
        }
    }
}
