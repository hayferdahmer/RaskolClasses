import unittest

from validate_equipment_report import (
    EXPECTED,
    SPEC_IDS,
    SPEC_WEAPONS,
    EquipmentReportError,
    validate_report,
)


def valid_report():
    scenarios = []
    for scenario_id in sorted(EXPECTED):
        first_spec, second_spec = scenario_id.removeprefix("pvp_equipment_").rsplit("_level_", 1)[0].split("_vs_", 1)
        level = int(scenario_id.rsplit("_", 1)[1])
        equipment = {}
        for side, spec_id in (("first", first_spec), ("second", second_spec)):
            weapon = SPEC_WEAPONS[spec_id] if level >= 30 else "NONE"
            attack_type = "MAGIC" if weapon in {"STAFF", "WAND"} else "PHYSICAL"
            items = [
                f"spec_{spec_id}_{slot}_{level}_epic"
                for slot in ("main_hand", "head", "chest", "legs", "feet")
            ] if level >= 30 else []
            equipment[f"{side}_spec"] = spec_id
            equipment[f"{side}_items"] = items
            equipment[f"{side}_active_sets"] = [f"{spec_id}_regalia"] if level >= 30 else []
            equipment[f"{side}_weapon_type"] = weapon
            equipment[f"{side}_attack_type"] = attack_type
            equipment[f"{side}_mastery"] = 1.0 if level >= 30 else 0.5
            equipment[f"{side}_attack_interval_seconds"] = 0.625 if level >= 30 else 0.25
            equipment[f"{side}_cadence_model"] = (
                "PAPER_26_2_MATERIAL_ATTACK_SPEED"
                if level >= 30 and weapon not in {"BOW"}
                else "BOW_FULL_CHARGE_20_TICKS"
                if weapon == "BOW"
                else "PLAYER_UNARMED_DEFAULT_4_ATTACKS_PER_SECOND"
            )
        scenarios.append({
            "scenario_id": scenario_id,
            "mode": "pvp",
            "sample_count": 1200,
            "invariant_violations": [],
            "equipment": equipment,
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
        "schema_version": "1.2",
        "report_type": "combat-lab-equipment-report",
        "simulator_version": "headless-equipment-duel-0.3.0",
        "scenario_set": "pvp-specialization-equipment-auto-attack-v1",
        "simulation_scope": "18 specialization equipment sets and equipment-aware auto-attacks",
        "source_commit": "a" * 40,
        "seed": 42,
        "iterations_per_scenario": 1200,
        "generated_at": "2026-11-23T07:00:00Z",
        "loaded_item_definitions": 100,
        "specialization_count": len(SPEC_IDS),
        "rarity": "EPIC",
        "scenarios": scenarios,
    }


class EquipmentReportValidationTests(unittest.TestCase):
    def test_complete_specialization_matrix_is_valid(self):
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

    def test_missing_specialization_set_is_rejected(self):
        report = valid_report()
        scenario = next(s for s in report["scenarios"] if s["scenario_id"].endswith("_level_60"))
        scenario["equipment"]["first_active_sets"] = []
        with self.assertRaises(EquipmentReportError):
            validate_report(report)

    def test_wrong_spec_weapon_is_rejected(self):
        report = valid_report()
        scenario = next(s for s in report["scenarios"] if s["scenario_id"].endswith("_level_60"))
        scenario["equipment"]["first_weapon_type"] = "STAFF"
        with self.assertRaises(EquipmentReportError):
            validate_report(report)

    def test_magic_weapon_must_use_magic_auto_attack(self):
        report = valid_report()
        scenario = next(s for s in report["scenarios"] if "shadow_vs_" in s["scenario_id"] and s["scenario_id"].endswith("_level_60"))
        scenario["equipment"]["first_attack_type"] = "PHYSICAL"
        with self.assertRaises(EquipmentReportError):
            validate_report(report)

    def test_invariant_violations_are_rejected(self):
        report = valid_report()
        report["scenarios"][0]["invariant_violations"] = ["unexpected"]
        with self.assertRaises(EquipmentReportError):
            validate_report(report)


if __name__ == "__main__":
    unittest.main()
