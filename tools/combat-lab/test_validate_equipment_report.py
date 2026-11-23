import unittest

from validate_equipment_report import EXPECTED, EquipmentReportError, validate_report


def valid_report():
    scenarios = []
    for scenario_id in sorted(EXPECTED):
        level = int(scenario_id.rsplit("_", 1)[1])
        scenarios.append({
            "scenario_id": scenario_id,
            "mode": "pvp",
            "sample_count": 1200,
            "invariant_violations": [],
            "equipment": {
                "first_items": ["warrior_weapon"] if level >= 30 else [],
                "second_items": ["mage_weapon"] if level >= 30 else [],
                "first_weapon_type": "SWORD",
                "second_weapon_type": "STAFF",
                "first_attack_type": "PHYSICAL",
                "second_attack_type": "MAGIC",
                "first_mastery": 1.0,
                "second_mastery": 1.0,
            },
            "metrics": {
                "win_rate": {"value": 0.5, "ci95_low": 0.47, "ci95_high": 0.53, "ci95_method": "wilson"},
                "ttk_median_seconds": {"value": 10.0, "ci95_low": 9.0, "ci95_high": 11.0,
                                       "ci95_method": "bootstrap_percentile"},
                "dps_mean": {"value": 12.0, "ci95_low": 11.0, "ci95_high": 13.0,
                             "ci95_method": "bootstrap_percentile"},
                "hps_mean": {"value": 1.0, "ci95_low": 0.9, "ci95_high": 1.1,
                             "ci95_method": "bootstrap_percentile"},
                "crit_rate": {"value": 0.1, "ci95_low": 0.08, "ci95_high": 0.12, "ci95_method": "wilson"},
                "avoidance_rate": {"value": 0.2, "ci95_low": 0.18, "ci95_high": 0.22, "ci95_method": "wilson"},
            },
        })
    return {
        "schema_version": "1.0",
        "report_type": "combat-lab-equipment-report",
        "simulator_version": "headless-equipment-duel-0.1.0",
        "scenario_set": "pvp-equipment-auto-attack-v1",
        "simulation_scope": "production items.yml and equipment-aware auto-attacks",
        "source_commit": "a" * 40,
        "seed": 42,
        "iterations_per_scenario": 1200,
        "generated_at": "2026-11-23T07:00:00Z",
        "loaded_item_definitions": 100,
        "rarity": "EPIC",
        "scenarios": scenarios,
    }


class EquipmentReportValidationTests(unittest.TestCase):
    def test_complete_equipment_matrix_is_valid(self):
        validate_report(valid_report())

    def test_missing_scenario_is_rejected(self):
        report = valid_report()
        report["scenarios"].pop()
        with self.assertRaises(EquipmentReportError):
            validate_report(report)

    def test_missing_equipment_at_gear_level_is_rejected(self):
        report = valid_report()
        scenario = next(s for s in report["scenarios"] if s["scenario_id"].endswith("_level_60"))
        scenario["equipment"]["first_items"] = []
        with self.assertRaises(EquipmentReportError):
            validate_report(report)

    def test_magic_weapon_must_use_magic_auto_attack(self):
        report = valid_report()
        scenario = report["scenarios"][0]
        scenario["equipment"]["second_attack_type"] = "PHYSICAL"
        with self.assertRaises(EquipmentReportError):
            validate_report(report)

    def test_invariant_violations_are_rejected(self):
        report = valid_report()
        report["scenarios"][0]["invariant_violations"] = ["unexpected"]
        with self.assertRaises(EquipmentReportError):
            validate_report(report)


if __name__ == "__main__":
    unittest.main()
