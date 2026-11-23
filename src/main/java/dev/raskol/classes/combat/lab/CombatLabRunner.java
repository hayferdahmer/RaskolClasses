// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.combat.lab;

import java.io.IOException;
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

/**
 * CLI для первого воспроизводимого headless-профиля Combat Lab.
 *
 * Профиль ограничен дуэлями без экипировки и активных способностей. Его отчёт
 * имеет отдельный scenario_set и не может быть принят за полный v1.16 baseline.
 */
public final class CombatLabRunner {

    private static final int MINIMUM_ITERATIONS = 1200;
    private static final int BOOTSTRAP_REPLICATES = 400;
    private static final int[] LEVELS = {1, 15, 30, 45, 60};

    private CombatLabRunner() {}

    public static void main(String[] args) throws IOException {
        Arguments options = Arguments.parse(args);
        YamlConfiguration config = YamlConfiguration.loadConfiguration(options.config().toFile());
        String report = run(config, options.seed(), options.iterations(), options.sourceCommit());
        Files.createDirectories(options.output().toAbsolutePath().getParent());
        Files.writeString(options.output(), report);
        System.out.println("Combat Lab report written: " + options.output());
        System.out.println("Scenario set: " + CombatLabDuelEngine.PROFILE_ID);
    }

    public static String run(YamlConfiguration config, long seed, int iterations, String sourceCommit) {
        if (config == null) {
            throw new IllegalArgumentException("config обязателен");
        }
        if (seed < 0L || iterations < MINIMUM_ITERATIONS) {
            throw new IllegalArgumentException("seed должен быть >= 0, iterations >= " + MINIMUM_ITERATIONS);
        }
        if (sourceCommit == null || !sourceCommit.matches("[0-9a-f]{40}")) {
            throw new IllegalArgumentException("Нужен полный source commit SHA (40 lowercase hex)");
        }

        CombatLabDuelEngine.Rules rules = CombatLabDuelEngine.Rules.from(config);
        List<String> scenarios = new ArrayList<>();
        for (int level : LEVELS) {
            for (PlayerClass firstClass : PlayerClass.values()) {
                for (PlayerClass secondClass : PlayerClass.values()) {
                    if (firstClass == secondClass) {
                        continue;
                    }
                    CombatLabCombatantSnapshot first =
                            CombatLabCombatantSnapshot.from(config, firstClass, level);
                    CombatLabCombatantSnapshot second =
                            CombatLabCombatantSnapshot.from(config, secondClass, level);
                    long scenarioSeed = mixSeed(seed, level, firstClass.ordinal(), secondClass.ordinal());
                    scenarios.add(scenarioJson(
                            first, second, rules, scenarioSeed, iterations));
                }
            }
        }

        return "{\n"
                + "  \"schema_version\": \"1.0\",\n"
                + "  \"report_type\": \"combat-lab-report\",\n"
                + "  \"simulator_version\": \"headless-unarmed-probe-0.1.0\",\n"
                + "  \"scenario_set\": \"" + CombatLabDuelEngine.PROFILE_ID + "\",\n"
                + "  \"simulation_scope\": \"unarmed physical auto-attacks only; no items, active abilities, talents, procs, pets, crowd control, resource spending or school effects\",\n"
                + "  \"source_commit\": \"" + sourceCommit + "\",\n"
                + "  \"seed\": " + seed + ",\n"
                + "  \"iterations_per_scenario\": " + iterations + ",\n"
                + "  \"generated_at\": \"" + Instant.now() + "\",\n"
                + "  \"scenarios\": [\n"
                + String.join(",\n", scenarios)
                + "\n  ]\n"
                + "}\n";
    }

    private static String scenarioJson(
            CombatLabCombatantSnapshot first,
            CombatLabCombatantSnapshot second,
            CombatLabDuelEngine.Rules rules,
            long seed,
            int iterations) {
        double[] ttk = new double[iterations];
        double[] dps = new double[iterations];
        double[] hps = new double[iterations];
        int winsCount = 0;
        long totalHits = 0L;
        long totalCrits = 0L;
        long totalAvoids = 0L;
        List<String> violations = new ArrayList<>();
        for (int i = 0; i < iterations; i++) {
            CombatLabDuelEngine.DuelResult result =
                    CombatLabDuelEngine.duel(first, second, rules, mixSeed(seed, i, 0, 0));
            if (result.firstPlayerWon()) winsCount++;
            totalHits += result.firstHits();
            totalCrits += result.firstCrits();
            totalAvoids += result.firstAvoids();
            ttk[i] = result.durationSeconds();
            dps[i] = result.firstDamageDealt() / Math.max(0.001, result.durationSeconds());
            hps[i] = result.firstHealing() / Math.max(0.001, result.durationSeconds());
            if (!Double.isFinite(ttk[i]) || !Double.isFinite(dps[i]) || !Double.isFinite(hps[i])
                    || ttk[i] <= 0.0 || dps[i] < 0.0 || hps[i] < 0.0) {
                violations.add("non_finite_or_negative_result");
                break;
            }
        }
        if (!violations.isEmpty()) {
            return scenarioObject(first, second, iterations, violations,
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        }

        Random bootstrapRandom = new Random(mixSeed(
                seed, first.level(), first.playerClass().ordinal(), second.playerClass().ordinal()));
        double[] ttkCi = bootstrapInterval(ttk, false, bootstrapRandom);
        double[] dpsCi = bootstrapInterval(dps, true, bootstrapRandom);
        double[] hpsCi = bootstrapInterval(hps, true, bootstrapRandom);
        double winRate = winsCount / (double) iterations;
        double[] winCi = wilsonInterval(winsCount, iterations);
        double critRate = totalHits == 0L ? 0.0 : totalCrits / (double) totalHits;
        double avoidanceRate = totalHits == 0L ? 0.0 : totalAvoids / (double) totalHits;
        double[] critCi = wilsonInterval(totalCrits, totalHits);
        double[] avoidanceCi = wilsonInterval(totalAvoids, totalHits);
        return scenarioObject(
                first, second, iterations, violations,
                winRate, winCi[0], winCi[1],
                median(ttk), ttkCi[0], ttkCi[1],
                mean(dps), dpsCi[0], dpsCi[1],
                mean(hps), hpsCi[0], hpsCi[1],
                critRate, critCi[0], critCi[1],
                avoidanceRate, avoidanceCi[0], avoidanceCi[1]);
    }

    private static String scenarioObject(
            CombatLabCombatantSnapshot first,
            CombatLabCombatantSnapshot second,
            int samples,
            List<String> violations,
            double winRate, double winLow, double winHigh,
            double ttk, double ttkLow, double ttkHigh,
            double dps, double dpsLow, double dpsHigh,
            double hps, double hpsLow, double hpsHigh,
            double critRate, double critLow, double critHigh,
            double avoidanceRate, double avoidanceLow, double avoidanceHigh) {
        String id = "pvp_unarmed_" + first.playerClass().name().toLowerCase(Locale.ROOT)
                + "_vs_" + second.playerClass().name().toLowerCase(Locale.ROOT)
                + "_level_" + first.level();
        return "    {\n"
                + "      \"scenario_id\": \"" + id + "\",\n"
                + "      \"mode\": \"pvp\",\n"
                + "      \"sample_count\": " + samples + ",\n"
                + "      \"invariant_violations\": " + stringArray(violations) + ",\n"
                + "      \"metrics\": {\n"
                + "        \"win_rate\": " + metric(winRate, winLow, winHigh, "wilson") + ",\n"
                + "        \"ttk_median_seconds\": " + metric(ttk, ttkLow, ttkHigh, "bootstrap_percentile") + ",\n"
                + "        \"dps_mean\": " + metric(dps, dpsLow, dpsHigh, "bootstrap_percentile") + ",\n"
                + "        \"hps_mean\": " + metric(hps, hpsLow, hpsHigh, "bootstrap_percentile") + ",\n"
                + "        \"crit_rate\": " + metric(critRate, critLow, critHigh, "wilson") + ",\n"
                + "        \"avoidance_rate\": " + metric(avoidanceRate, avoidanceLow, avoidanceHigh, "wilson") + "\n"
                + "      }\n"
                + "    }";
    }

    private static String metric(double value, double low, double high, String method) {
        return "{\"value\":" + number(value)
                + ",\"ci95_low\":" + number(low)
                + ",\"ci95_high\":" + number(high)
                + ",\"ci95_method\":\"" + method + "\"}";
    }

    private static double[] wilsonInterval(int successes, int samples) {
        double z = 1.959963984540054;
        double p = successes / (double) samples;
        double denominator = 1.0 + z * z / samples;
        double center = (p + z * z / (2.0 * samples)) / denominator;
        double margin = z * Math.sqrt(p * (1.0 - p) / samples
                + z * z / (4.0 * samples * samples)) / denominator;
        return new double[] {Math.max(0.0, center - margin), Math.min(1.0, center + margin)};
    }

    private static double[] bootstrapInterval(double[] values, boolean average, Random random) {
        double[] estimates = new double[BOOTSTRAP_REPLICATES];
        double[] sample = new double[values.length];
        for (int replicate = 0; replicate < BOOTSTRAP_REPLICATES; replicate++) {
            for (int i = 0; i < values.length; i++) {
                sample[i] = values[random.nextInt(values.length)];
            }
            estimates[replicate] = average ? mean(sample) : median(sample);
        }
        Arrays.sort(estimates);
        return new double[] {
            estimates[(int) Math.floor((BOOTSTRAP_REPLICATES - 1) * 0.025)],
            estimates[(int) Math.ceil((BOOTSTRAP_REPLICATES - 1) * 0.975)]
        };
    }

    private static double mean(double[] values) {
        double total = 0.0;
        for (double value : values) total += value;
        return total / values.length;
    }

    private static double median(double[] values) {
        double[] sorted = values.clone();
        Arrays.sort(sorted);
        int middle = sorted.length / 2;
        return sorted.length % 2 == 0
                ? (sorted[middle - 1] + sorted[middle]) / 2.0
                : sorted[middle];
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

    private record Arguments(Path config, Path output, long seed, int iterations, String sourceCommit) {

        static Arguments parse(String[] args) {
            Path config = Path.of("src/main/resources/config.yml");
            Path output = Path.of("target/combat-lab/unarmed-probe-report.json");
            long seed = 20261123L;
            int iterations = MINIMUM_ITERATIONS;
            String commit = System.getenv("GITHUB_SHA");
            for (int i = 0; i < args.length; i++) {
                if (i + 1 >= args.length) {
                    throw new IllegalArgumentException("Для аргумента нужен параметр: " + args[i]);
                }
                String value = args[++i];
                switch (args[i - 1]) {
                    case "--config" -> config = Path.of(value);
                    case "--output" -> output = Path.of(value);
                    case "--seed" -> seed = Long.parseLong(value);
                    case "--iterations" -> iterations = Integer.parseInt(value);
                    case "--source-commit" -> commit = value;
                    default -> throw new IllegalArgumentException("Неизвестный аргумент: " + args[i - 1]);
                }
            }
            if (commit == null) {
                throw new IllegalArgumentException("Укажите --source-commit или задайте GITHUB_SHA");
            }
            return new Arguments(config, output, seed, iterations, commit);
        }
    }
}
