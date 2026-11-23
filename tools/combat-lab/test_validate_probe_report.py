import unittest

from validate_probe_report import EXPECTED_SCENARIOS, ProbeReportError, validate_report


def metric(value=0.5, method="wilson"):
    return {
        "value": value,
        "ci95_low": value,
        "ci95_high": value,
        "ci95_method": method,
    }


def valid_report():
    scenarios = []
    for scenario_id in sorted(EXPECTED_SCENARIOS):
        scenarios.append(
            {
                "scenario_id": scenario_id,
                "mode": "pvp",
                "sample_count": 1200,
                "invariant_violations": [],
                "metrics": {
                    "win_rate": metric(),
                    "crit_rate": metric(),
                    "avoidance_rate": metric(),
                    "ttk_median_seconds": metric(20.0, "bootstrap_percentile"),
                    "dps_mean": metric(10.0, "bootstrap_percentile"),
                    "hps_mean": metric(0.5, "bootstrap_percentile"),
                },
            }
        )
    return {
        "schema_version": "1.0",
        "report_type": "combat-lab-probe-report",
        "simulator_version": "headless-unarmed-probe-0.1.0",
        "scenario_set": "pvp-unarmed-melee-probe-v1",
        "simulation_scope": "unarmed physical auto-attacks only",
        "source_commit": "a" * 40,
        "seed": 123,
        "iterations_per_scenario": 1200,
        "generated_at": "2026-11-23T00:00:00Z",
        "scenarios": scenarios,
    }


class ProbeReportValidationTests(unittest.TestCase):
    def test_complete_probe_matrix_is_valid(self):
        report = valid_report()
        validate_report(report)
        self.assertEqual(len(report["scenarios"]), 150)

    def test_missing_scenario_is_rejected(self):
        report = valid_report()
        report["scenarios"].pop()
        with self.assertRaisesRegex(ProbeReportError, "Неполный набор"):
            validate_report(report)

    def test_duplicate_scenario_is_rejected(self):
        report = valid_report()
        report["scenarios"][-1] = report["scenarios"][0]
        with self.assertRaisesRegex(ProbeReportError, "Дублирующийся"):
            validate_report(report)

    def test_sample_count_below_minimum_is_rejected(self):
        report = valid_report()
        report["iterations_per_scenario"] = 100
        with self.assertRaisesRegex(ProbeReportError, "1200"):
            validate_report(report)

    def test_nonempty_invariant_violations_are_rejected(self):
        report = valid_report()
        report["scenarios"][0]["invariant_violations"] = ["negative_damage"]
        with self.assertRaisesRegex(ProbeReportError, "invariant violations"):
            validate_report(report)

    def test_confidence_interval_must_contain_estimate(self):
        report = valid_report()
        report["scenarios"][0]["metrics"]["win_rate"]["ci95_low"] = 0.7
        with self.assertRaisesRegex(ProbeReportError, "CI95"):
            validate_report(report)

    def test_rate_must_use_wilson_interval(self):
        report = valid_report()
        report["scenarios"][0]["metrics"]["crit_rate"]["ci95_method"] = "bootstrap_percentile"
        with self.assertRaisesRegex(ProbeReportError, "Wilson"):
            validate_report(report)

    def test_continuous_metric_must_use_bootstrap_interval(self):
        report = valid_report()
        report["scenarios"][0]["metrics"]["dps_mean"]["ci95_method"] = "wilson"
        with self.assertRaisesRegex(ProbeReportError, "bootstrap"):
            validate_report(report)

    def test_non_finite_metric_is_rejected(self):
        report = valid_report()
        report["scenarios"][0]["metrics"]["dps_mean"]["value"] = float("inf")
        report["scenarios"][0]["metrics"]["dps_mean"]["ci95_high"] = float("inf")
        with self.assertRaisesRegex(ProbeReportError, "конечными"):
            validate_report(report)

    def test_full_source_commit_is_required(self):
        report = valid_report()
        report["source_commit"] = "deadbeef"
        with self.assertRaisesRegex(ProbeReportError, "source_commit"):
            validate_report(report)


if __name__ == "__main__":
    unittest.main()
