#!/usr/bin/env python3
"""Структурная проверка equipment-aware Combat Lab отчёта."""

from __future__ import annotations

import json
import math
import re
import sys
from pathlib import Path

CLASSES = {"warrior", "hunter", "priest", "mage", "rogue", "warlock"}
LEVELS = {1, 15, 30, 45, 60}
RATE_METRICS = {"win_rate", "crit_rate", "avoidance_rate"}
CONTINUOUS_METRICS = {"ttk_median_seconds", "dps_mean", "hps_mean"}
EXPECTED = {
    f"pvp_equipment_{first}_vs_{second}_level_{level}"
    for level in LEVELS
    for first in CLASSES
    for second in CLASSES
    if first != second
}


class EquipmentReportError(ValueError):
    pass


def require(condition: bool, message: str) -> None:
    if not condition:
        raise EquipmentReportError(message)


def validate_report(report: dict) -> None:
    required = {
        "schema_version", "report_type", "simulator_version", "scenario_set",
        "simulation_scope", "source_commit", "seed", "iterations_per_scenario",
        "generated_at", "loaded_item_definitions", "rarity", "scenarios",
    }
    require(set(report) == required, "Неверный набор полей верхнего уровня")
    require(report["schema_version"] == "1.1", "Неподдерживаемая schema_version")
    require(report["report_type"] == "combat-lab-equipment-report", "Это не equipment report")
    require(report["scenario_set"] == "pvp-equipment-auto-attack-v2", "Неверный scenario_set")
    require(bool(re.fullmatch(r"[0-9a-f]{40}", report["source_commit"])), "Некорректный source_commit")
    require(isinstance(report["seed"], int) and not isinstance(report["seed"], bool) and report["seed"] >= 0,
            "Некорректный seed")
    iterations = report["iterations_per_scenario"]
    require(isinstance(iterations, int) and not isinstance(iterations, bool) and iterations >= 1200,
            "Нужно не менее 1200 итераций")
    require(isinstance(report["generated_at"], str) and report["generated_at"].endswith("Z"),
            "generated_at должен быть UTC ISO-8601")
    require(isinstance(report["loaded_item_definitions"], int) and report["loaded_item_definitions"] > 0,
            "Production item registry пуст")
    require(report["rarity"] == "EPIC", "В отчёте должна быть зафиксирована редкость EPIC")
    scenarios = report["scenarios"]
    require(isinstance(scenarios, list) and len(scenarios) == len(EXPECTED), "Неполная матрица сценариев")

    seen = set()
    for scenario in scenarios:
        require(set(scenario) == {"scenario_id", "mode", "sample_count", "invariant_violations", "equipment", "metrics"},
                "Неверные поля сценария")
        scenario_id = scenario["scenario_id"]
        require(scenario_id in EXPECTED, f"Неизвестный scenario_id: {scenario_id}")
        require(scenario_id not in seen, f"Дублирующийся scenario_id: {scenario_id}")
        seen.add(scenario_id)
        require(scenario["mode"] == "pvp", f"{scenario_id}: режим должен быть pvp")
        require(scenario["sample_count"] == iterations, f"{scenario_id}: sample_count не совпадает")
        require(scenario["invariant_violations"] == [], f"{scenario_id}: есть invariant violations")

        equipment = scenario["equipment"]
        require(set(equipment) == {
            "first_items", "second_items", "first_weapon_type", "second_weapon_type",
            "first_attack_type", "second_attack_type", "first_mastery", "second_mastery",
            "first_attack_interval_seconds", "second_attack_interval_seconds",
            "first_cadence_model", "second_cadence_model",
        }, f"{scenario_id}: неверная структура equipment")
        level = int(scenario_id.rsplit("_", 1)[1])
        if level >= 30:
            require(equipment["first_items"] and equipment["second_items"],
                    f"{scenario_id}: экипировка отсутствует на боевом уровне")
        for side in ("first", "second"):
            weapon = equipment[f"{side}_weapon_type"]
            attack_type = equipment[f"{side}_attack_type"]
            mastery = equipment[f"{side}_mastery"]
            require(attack_type in {"PHYSICAL", "MAGIC"}, f"{scenario_id}: неизвестный attack type")
            if weapon in {"STAFF", "WAND"}:
                require(attack_type == "MAGIC", f"{scenario_id}: магическое оружие размечено неверно")
            else:
                require(attack_type == "PHYSICAL", f"{scenario_id}: физическое оружие размечено неверно")
            require(mastery in {0.5, 0.75, 1.0}, f"{scenario_id}: недопустимый WeaponMastery")
            interval = equipment[f"{side}_attack_interval_seconds"]
            cadence = equipment[f"{side}_cadence_model"]
            require(isinstance(interval, (int, float)) and not isinstance(interval, bool)
                    and math.isfinite(interval) and interval > 0.0,
                    f"{scenario_id}: некорректный интервал автоатаки")
            require(isinstance(cadence, str) and bool(cadence),
                    f"{scenario_id}: отсутствует модель темпа атаки")

        metrics = scenario["metrics"]
        require(set(metrics) == RATE_METRICS | CONTINUOUS_METRICS, f"{scenario_id}: неверный набор метрик")
        for name, metric in metrics.items():
            require(set(metric) == {"value", "ci95_low", "ci95_high", "ci95_method"},
                    f"{scenario_id}.{name}: неверный формат метрики")
            values = (metric["value"], metric["ci95_low"], metric["ci95_high"])
            require(all(isinstance(v, (int, float)) and not isinstance(v, bool) and math.isfinite(v)
                        for v in values), f"{scenario_id}.{name}: нечисловые/неfinite значения")
            value, low, high = values
            require(0 <= low <= value <= high, f"{scenario_id}.{name}: некорректный CI95")
            if name in RATE_METRICS:
                require(high <= 1.0 and metric["ci95_method"] == "wilson",
                        f"{scenario_id}.{name}: ожидался Wilson CI")
            else:
                require(metric["ci95_method"] == "bootstrap_percentile",
                        f"{scenario_id}.{name}: ожидался bootstrap CI")
    require(seen == EXPECTED, "Матрица сценариев не совпадает с ожидаемой")


def main() -> int:
    if len(sys.argv) != 2:
        print("Usage: validate_equipment_report.py <report.json>", file=sys.stderr)
        return 2
    try:
        report = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
        validate_report(report)
    except (OSError, json.JSONDecodeError, EquipmentReportError) as exc:
        print(f"COMBAT LAB EQUIPMENT REPORT INVALID: {exc}", file=sys.stderr)
        return 1
    print(f"COMBAT LAB EQUIPMENT REPORT VALID: {len(report['scenarios'])} scenarios × "
          f"{report['iterations_per_scenario']} samples")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
