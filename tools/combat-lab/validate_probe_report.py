#!/usr/bin/env python3
"""Структурная проверка ограниченного Combat Lab probe-отчёта."""

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
EXPECTED_SCENARIOS = {
    f"pvp_unarmed_{first}_vs_{second}_level_{level}"
    for level in LEVELS
    for first in CLASSES
    for second in CLASSES
    if first != second
}


class ProbeReportError(ValueError):
    pass


def _require(condition: bool, message: str) -> None:
    if not condition:
        raise ProbeReportError(message)


def validate_report(report: dict) -> None:
    required = {
        "schema_version",
        "report_type",
        "simulator_version",
        "scenario_set",
        "simulation_scope",
        "source_commit",
        "seed",
        "iterations_per_scenario",
        "generated_at",
        "scenarios",
    }
    _require(set(report) == required, "Неверный набор полей верхнего уровня")
    _require(report["schema_version"] == "1.0", "Неподдерживаемая schema_version")
    _require(report["report_type"] == "combat-lab-probe-report", "Это не probe-отчёт")
    _require(report["scenario_set"] == "pvp-unarmed-melee-probe-v1", "Неверный scenario_set")
    _require(isinstance(report["simulator_version"], str) and report["simulator_version"], "Нет версии симулятора")
    _require(isinstance(report["simulation_scope"], str) and report["simulation_scope"], "Не указан scope")
    _require(bool(re.fullmatch(r"[0-9a-f]{40}", report["source_commit"])), "Некорректный source_commit")
    _require(isinstance(report["seed"], int) and not isinstance(report["seed"], bool) and report["seed"] >= 0, "Некорректный seed")
    iterations = report["iterations_per_scenario"]
    _require(isinstance(iterations, int) and not isinstance(iterations, bool) and iterations >= 1200, "Нужно не менее 1200 итераций")
    _require(isinstance(report["generated_at"], str) and report["generated_at"].endswith("Z"), "generated_at должен быть UTC ISO-8601")
    scenarios = report["scenarios"]
    _require(isinstance(scenarios, list) and len(scenarios) == len(EXPECTED_SCENARIOS), "Неполный набор probe-сценариев")

    seen = set()
    for scenario in scenarios:
        _require(
            set(scenario) == {"scenario_id", "mode", "sample_count", "invariant_violations", "metrics"},
            "Неверные поля сценария",
        )
        scenario_id = scenario["scenario_id"]
        _require(scenario_id in EXPECTED_SCENARIOS, f"Неизвестный scenario_id: {scenario_id}")
        _require(scenario_id not in seen, f"Дублирующийся scenario_id: {scenario_id}")
        seen.add(scenario_id)
        _require(scenario["mode"] == "pvp", f"{scenario_id}: режим должен быть pvp")
        _require(scenario["sample_count"] == iterations, f"{scenario_id}: sample_count не совпадает с iterations")
        _require(scenario["invariant_violations"] == [], f"{scenario_id}: есть invariant violations")
        metrics = scenario["metrics"]
        _require(set(metrics) == RATE_METRICS | CONTINUOUS_METRICS, f"{scenario_id}: неверный набор метрик")
        for name, metric in metrics.items():
            _require(
                set(metric) == {"value", "ci95_low", "ci95_high", "ci95_method"},
                f"{scenario_id}.{name}: неверный формат метрики",
            )
            value, low, high = metric["value"], metric["ci95_low"], metric["ci95_high"]
            _require(
                all(
                    isinstance(v, (int, float)) and not isinstance(v, bool) and math.isfinite(v)
                    for v in (value, low, high)
                ),
                f"{scenario_id}.{name}: значения должны быть конечными числами",
            )
            _require(all(v >= 0 for v in (value, low, high)), f"{scenario_id}.{name}: отрицательное значение")
            _require(low <= value <= high, f"{scenario_id}.{name}: CI95 не содержит оценку")
            if name in RATE_METRICS:
                _require(high <= 1.0, f"{scenario_id}.{name}: rate выше 1")
                _require(metric["ci95_method"] == "wilson", f"{scenario_id}.{name}: ожидался Wilson CI")
            else:
                _require(metric["ci95_method"] == "bootstrap_percentile",
                         f"{scenario_id}.{name}: ожидался bootstrap CI")
    _require(seen == EXPECTED_SCENARIOS, "Матрица сценариев не совпадает с ожидаемой")


def main() -> int:
    if len(sys.argv) != 2:
        print("Usage: validate_probe_report.py <report.json>", file=sys.stderr)
        return 2
    path = Path(sys.argv[1])
    try:
        report = json.loads(path.read_text(encoding="utf-8"))
        validate_report(report)
    except (OSError, json.JSONDecodeError, ProbeReportError) as exc:
        print(f"COMBAT LAB PROBE INVALID: {exc}", file=sys.stderr)
        return 1
    print(f"COMBAT LAB PROBE VALID: {len(report['scenarios'])} scenarios × {report['iterations_per_scenario']} samples")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
