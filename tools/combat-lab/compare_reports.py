#!/usr/bin/env python3
# © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
# SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
"""Сравнивает воспроизводимый Combat Lab report с утверждённым baseline."""

from __future__ import annotations

import argparse
import json
import math
import sys
from pathlib import Path
from typing import Any

SCHEMA_VERSION = "1.0"
RATE_METRICS = {"win_rate", "proc_rate", "pet_damage_share", "resource_waste_pct"}
METRIC_POLICIES = {
    # Доли сравниваются в процентных пунктах, а не относительно малой базы.
    "win_rate": {"max_abs_delta": 0.05},
    "proc_rate": {"max_abs_delta": 0.03},
    "pet_damage_share": {"max_abs_delta": 0.05},
    "resource_waste_pct": {"max_abs_delta": 0.05},
    "ttk_median_seconds": {"max_relative_delta": 0.10},
    "dps_mean": {"max_relative_delta": 0.10},
    "hps_mean": {"max_relative_delta": 0.10},
}
REQUIRED_METRICS = tuple(METRIC_POLICIES)


class GateError(ValueError):
    """Ошибка входного отчёта или нарушение статистического gate."""


def _number(value: Any, label: str, minimum: float = 0.0, maximum: float | None = None) -> float:
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        raise GateError(f"{label}: ожидалось числовое значение")
    number = float(value)
    if not math.isfinite(number) or number < minimum or (maximum is not None and number > maximum):
        raise GateError(f"{label}: значение вне допустимого диапазона")
    return number


def load_json(path: Path) -> dict[str, Any]:
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise GateError(f"Не удалось прочитать JSON {path}: {exc}") from exc
    if not isinstance(data, dict):
        raise GateError(f"{path}: корневое значение должно быть объектом")
    return data


def validate_baseline(baseline: dict[str, Any]) -> None:
    if baseline.get("schema_version") != SCHEMA_VERSION:
        raise GateError("Baseline: неподдерживаемая schema_version")
    if not baseline.get("baseline_id") or not baseline.get("simulator_version"):
        raise GateError("Baseline: отсутствует baseline_id или simulator_version")
    status = baseline.get("status")
    if status not in {"pending", "approved"}:
        raise GateError("Baseline: status должен быть pending или approved")
    scenarios = baseline.get("scenarios")
    if not isinstance(scenarios, list):
        raise GateError("Baseline: scenarios должен быть массивом")
    if status == "pending":
        if scenarios:
            raise GateError("Pending baseline не должен содержать неподтверждённые игровые значения")
        if not baseline.get("pending_reason"):
            raise GateError("Pending baseline должен объяснять причину ожидания")
        return
    if not scenarios:
        raise GateError("Approved baseline должен содержать сценарии")
    ids: set[str] = set()
    for scenario in scenarios:
        scenario_id = scenario.get("scenario_id")
        if not isinstance(scenario_id, str) or not scenario_id or scenario_id in ids:
            raise GateError("Baseline: scenario_id должен быть уникальной непустой строкой")
        ids.add(scenario_id)
        metrics = scenario.get("metrics")
        if not isinstance(metrics, dict):
            raise GateError(f"{scenario_id}: отсутствует metrics")
        for metric in REQUIRED_METRICS:
            if metric not in metrics:
                raise GateError(f"{scenario_id}: отсутствует baseline-метрика {metric}")
            _number(metrics[metric], f"{scenario_id}.{metric}", 0.0, 1.0 if metric in RATE_METRICS else None)


def validate_report(report: dict[str, Any], baseline: dict[str, Any]) -> None:
    if report.get("schema_version") != SCHEMA_VERSION or report.get("report_type") != "combat-lab-report":
        raise GateError("Report: неподдерживаемая схема или report_type")
    for key in ("simulator_version", "scenario_set", "source_commit", "generated_at"):
        if not report.get(key):
            raise GateError(f"Report: отсутствует {key}")
    if report.get("simulator_version") != baseline.get("simulator_version"):
        raise GateError("Версия симулятора отчёта не совпадает с baseline")
    if report.get("scenario_set") != baseline.get("scenario_set"):
        raise GateError("Набор сценариев отчёта не совпадает с baseline")
    seed = report.get("seed")
    iterations = report.get("iterations_per_scenario")
    if isinstance(seed, bool) or not isinstance(seed, int) or seed < 0:
        raise GateError("Report: seed должен быть целым неотрицательным числом")
    if isinstance(iterations, bool) or not isinstance(iterations, int) or iterations < 1:
        raise GateError("Report: iterations_per_scenario должен быть положительным целым")
    scenarios = report.get("scenarios")
    if not isinstance(scenarios, list) or not scenarios:
        raise GateError("Report: scenarios должен быть непустым массивом")
    baseline_ids = {item["scenario_id"] for item in baseline["scenarios"]}
    report_ids: set[str] = set()
    for scenario in scenarios:
        scenario_id = scenario.get("scenario_id")
        if not isinstance(scenario_id, str) or not scenario_id or scenario_id in report_ids:
            raise GateError("Report: scenario_id должен быть уникальным")
        report_ids.add(scenario_id)
        if scenario_id not in baseline_ids:
            raise GateError(f"Нет утверждённого baseline для сценария {scenario_id}")
        sample_count = scenario.get("sample_count")
        if isinstance(sample_count, bool) or not isinstance(sample_count, int) or sample_count < 1:
            raise GateError(f"{scenario_id}: sample_count должен быть положительным целым")
        if sample_count < iterations:
            raise GateError(f"{scenario_id}: sample_count меньше iterations_per_scenario")
        violations = scenario.get("invariant_violations")
        if not isinstance(violations, list):
            raise GateError(f"{scenario_id}: invariant_violations должен быть массивом")
        if violations:
            raise GateError(f"{scenario_id}: нарушены игровые инварианты: {'; '.join(map(str, violations))}")
        metrics = scenario.get("metrics")
        if not isinstance(metrics, dict):
            raise GateError(f"{scenario_id}: отсутствует metrics")
        for metric in REQUIRED_METRICS:
            value = metrics.get(metric)
            if not isinstance(value, dict) or "value" not in value:
                raise GateError(f"{scenario_id}.{metric}: некорректная структура метрики")
            _number(value["value"], f"{scenario_id}.{metric}.value", 0.0, 1.0 if metric in RATE_METRICS else None)
            if metric in RATE_METRICS:
                low = _number(value.get("ci95_low"), f"{scenario_id}.{metric}.ci95_low", 0.0, 1.0)
                high = _number(value.get("ci95_high"), f"{scenario_id}.{metric}.ci95_high", 0.0, 1.0)
                if low > value["value"] or value["value"] > high:
                    raise GateError(f"{scenario_id}.{metric}: CI95 не содержит оценку")


def compare_reports(baseline: dict[str, Any], report: dict[str, Any]) -> list[str]:
    validate_baseline(baseline)
    if baseline["status"] != "approved":
        raise GateError("Статистический gate заблокирован: baseline ещё не утверждён")
    validate_report(report, baseline)
    baseline_by_id = {item["scenario_id"]: item for item in baseline["scenarios"]}
    report_by_id = {item["scenario_id"]: item for item in report["scenarios"]}
    expected_ids = set(baseline_by_id)
    if set(report_by_id) != expected_ids:
        missing = sorted(expected_ids - set(report_by_id))
        extra = sorted(set(report_by_id) - expected_ids)
        raise GateError(f"Неполная матрица сценариев; missing={missing}, extra={extra}")

    failures: list[str] = []
    for scenario_id in sorted(expected_ids):
        before = baseline_by_id[scenario_id]["metrics"]
        after = report_by_id[scenario_id]["metrics"]
        for metric, policy in METRIC_POLICIES.items():
            old = _number(before[metric], f"baseline.{scenario_id}.{metric}", 0.0, 1.0 if metric in RATE_METRICS else None)
            new = float(after[metric]["value"])
            delta = abs(new - old)
            if "max_abs_delta" in policy:
                limit = policy["max_abs_delta"]
                if delta > limit:
                    failures.append(
                        f"{scenario_id}.{metric}: |{new:.4f} - {old:.4f}| = {delta:.4f} > {limit:.4f}"
                    )
            else:
                relative = delta / max(abs(old), 1e-9)
                limit = policy["max_relative_delta"]
                if relative > limit:
                    failures.append(
                        f"{scenario_id}.{metric}: relative delta {relative:.1%} > {limit:.1%} "
                        f"(current={new:.4f}, baseline={old:.4f})"
                    )
    return failures


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--baseline", type=Path, required=True)
    parser.add_argument("--report", type=Path)
    parser.add_argument("--validate-only", action="store_true")
    args = parser.parse_args()
    try:
        baseline = load_json(args.baseline)
        validate_baseline(baseline)
        if args.validate_only:
            print(f"Baseline contract: valid ({baseline['status']})")
            return 0
        if args.report is None:
            parser.error("--report обязателен без --validate-only")
        report = load_json(args.report)
        failures = compare_reports(baseline, report)
        if failures:
            print("COMBAT LAB GATE: FAIL")
            for failure in failures:
                print(f"::error::{failure}")
            return 1
        print("COMBAT LAB GATE: PASS")
        print(f"Сценариев проверено: {len(report['scenarios'])}")
        return 0
    except GateError as exc:
        print(f"::error::COMBAT LAB GATE: {exc}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
