# © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
# SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
"""Регрессионные тесты статистического Combat Lab gate."""

import copy
import unittest

from compare_reports import GateError, compare_reports, validate_baseline, validate_report


def metric(value, low=None, high=None):
    return {
        "value": value,
        "ci95_low": value if low is None else low,
        "ci95_high": value if high is None else high,
    }


def baseline():
    return {
        "schema_version": "1.0",
        "baseline_id": "fixture-v1",
        "status": "approved",
        "simulator_version": "combat-lab-1",
        "scenario_set": "pvp-pve-core-v1",
        "created_from_commit": "b" * 40,
        "scenarios": [
            {
                "scenario_id": "pvp-warrior-vs-mage-l40",
                "metrics": {
                    "win_rate": 0.52,
                    "proc_rate": 0.20,
                    "pet_damage_share": 0.0,
                    "resource_waste_pct": 0.10,
                    "ttk_median_seconds": 24.0,
                    "dps_mean": 80.0,
                    "hps_mean": 0.0,
                },
            }
        ],
    }


def report():
    return {
        "schema_version": "1.0",
        "report_type": "combat-lab-report",
        "simulator_version": "combat-lab-1",
        "scenario_set": "pvp-pve-core-v1",
        "source_commit": "a" * 40,
        "seed": 42,
        "iterations_per_scenario": 100,
        "generated_at": "2026-11-23T00:00:00Z",
        "scenarios": [
            {
                "scenario_id": "pvp-warrior-vs-mage-l40",
                "mode": "pvp",
                "sample_count": 100,
                "invariant_violations": [],
                "metrics": {
                    "win_rate": metric(0.54, 0.52, 0.56),
                    "proc_rate": metric(0.21, 0.18, 0.22),
                    "pet_damage_share": metric(0.0, 0.0, 0.0),
                    "resource_waste_pct": metric(0.11, 0.08, 0.14),
                    "ttk_median_seconds": metric(25.0, 24.5, 25.5),
                    "dps_mean": metric(82.0, 81.0, 83.0),
                    "hps_mean": metric(0.0, 0.0, 0.0),
                },
            }
        ],
    }


class CombatLabGateTest(unittest.TestCase):
    def test_small_expected_drift_passes(self):
        self.assertEqual([], compare_reports(baseline(), report()))

    def test_win_rate_drift_over_five_percentage_points_fails(self):
        candidate = report()
        candidate["scenarios"][0]["metrics"]["win_rate"] = metric(0.60, 0.58, 0.62)
        failures = compare_reports(baseline(), candidate)
        self.assertTrue(any("win_rate" in failure for failure in failures))

    def test_ttk_relative_drift_over_ten_percent_fails(self):
        candidate = report()
        candidate["scenarios"][0]["metrics"]["ttk_median_seconds"] = metric(27.0, 26.8, 27.2)
        failures = compare_reports(baseline(), candidate)
        self.assertTrue(any("ttk_median_seconds" in failure for failure in failures))

    def test_any_invariant_violation_fails(self):
        candidate = report()
        candidate["scenarios"][0]["invariant_violations"] = ["single-hit-cap"]
        with self.assertRaisesRegex(GateError, "нарушены игровые инварианты"):
            compare_reports(baseline(), candidate)

    def test_missing_scenario_fails(self):
        candidate = report()
        candidate["scenarios"] = []
        with self.assertRaisesRegex(GateError, "непустым массивом"):
            compare_reports(baseline(), candidate)

    def test_incompatible_simulator_version_fails(self):
        candidate = report()
        candidate["simulator_version"] = "combat-lab-2"
        with self.assertRaisesRegex(GateError, "Версия симулятора"):
            compare_reports(baseline(), candidate)

    def test_ci_must_contain_rate_estimate(self):
        candidate = report()
        candidate["scenarios"][0]["metrics"]["proc_rate"] = metric(0.2, 0.3, 0.4)
        with self.assertRaisesRegex(GateError, "CI95 не содержит"):
            compare_reports(baseline(), candidate)

    def test_pending_baseline_cannot_be_used_for_comparison(self):
        pending = {
            "schema_version": "1.0",
            "baseline_id": "pending",
            "status": "pending",
            "simulator_version": "combat-lab-1",
            "scenario_set": "pvp-pve-core-v1",
            "scenarios": [],
            "pending_reason": "No trusted runner baseline yet",
        }
        validate_baseline(pending)
        with self.assertRaisesRegex(GateError, "ещё не утверждён"):
            compare_reports(pending, report())

    def test_duplicate_scenario_ids_are_rejected(self):
        candidate = copy.deepcopy(baseline())
        candidate["scenarios"].append(copy.deepcopy(candidate["scenarios"][0]))
        with self.assertRaisesRegex(GateError, "уникальной"):
            validate_baseline(candidate)


if __name__ == "__main__":
    unittest.main()
