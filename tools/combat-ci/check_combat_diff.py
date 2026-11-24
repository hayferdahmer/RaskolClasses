#!/usr/bin/env python3
# © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
# SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
"""Проверяет боевой diff и наличие актуальных тестов для затронутых контрактов."""

from __future__ import annotations

import os
import subprocess
import sys

COMBAT_PREFIXES = (
    "src/main/java/dev/raskol/classes/combat/",
    "src/main/java/dev/raskol/classes/ability/",
    "src/main/java/dev/raskol/classes/attribute/",
    "src/main/java/dev/raskol/classes/pet/",
    "src/main/java/dev/raskol/classes/resource/",
    "src/main/java/dev/raskol/classes/cc/",
    "src/main/java/dev/raskol/classes/spec/",
    "src/main/java/dev/raskol/classes/install/",
    "src/main/java/dev/raskol/classes/selftest/",
    "src/main/resources/kits/",
    "src/main/resources/talents/",
)
COMBAT_FILES = {"src/main/resources/config.yml", "src/main/resources/items.yml"}
TEST_PREFIX = "src/test/java/"
SELFTEST_PREFIX = "src/main/java/dev/raskol/classes/selftest/"
RESOURCE_TEST_HINTS = {
    "src/main/resources/config.yml": ("config",),
    "src/main/resources/items.yml": ("item",),
}


def is_combat_path(path: str) -> bool:
    return path.startswith(COMBAT_PREFIXES) or path in COMBAT_FILES


def is_test_path(path: str) -> bool:
    return path.startswith(TEST_PREFIX) or path.startswith(SELFTEST_PREFIX)


def existing_paths_at_head(paths: list[str]) -> list[str]:
    """Исключает удалённые из HEAD файлы: они не могут подтверждать покрытие."""
    existing = []
    for path in paths:
        result = subprocess.run(
            ["git", "cat-file", "-e", f"HEAD:{path}"],
            check=False, capture_output=True, text=True,
        )
        if result.returncode == 0:
            existing.append(path)
    return existing


def matching_test_exists(source_path: str, test_paths: set[str]) -> bool:
    if source_path.startswith(SELFTEST_PREFIX):
        return any(path.startswith(SELFTEST_PREFIX) or path.startswith(TEST_PREFIX) for path in test_paths)
    if source_path.startswith("src/main/java/"):
        package_path = source_path.removeprefix("src/main/java/").rsplit("/", 1)[0]
        package_test_prefix = TEST_PREFIX + package_path + "/"
        source_name = source_path.rsplit("/", 1)[-1].removesuffix(".java").lower()
        return any(
            path.startswith(package_test_prefix)
            or (path.startswith(TEST_PREFIX) and source_name in path.lower())
            for path in test_paths
        )

    hints = RESOURCE_TEST_HINTS.get(source_path)
    if hints is None and source_path.startswith("src/main/resources/kits/"):
        hints = ("kit",)
    if hints is None and source_path.startswith("src/main/resources/talents/"):
        hints = ("talent",)
    return bool(hints) and any(
        any(hint in path.lower() for hint in hints)
        for path in test_paths
    )


def changed_files(base: str, head: str, event_name: str) -> list[str]:
    if not head:
        raise ValueError("Не удалось определить HEAD SHA для анализа боевого diff.")
    if not base or set(base) == {"0"}:
        base = subprocess.run(
            ["git", "hash-object", "-t", "tree", "/dev/null"],
            check=True, capture_output=True, text=True,
        ).stdout.strip()
    revision = f"{base}...{head}" if event_name == "pull_request" else f"{base} {head}"
    result = subprocess.run(
        ["git", "diff", "--name-only", "--diff-filter=ACMRTD", *revision.split()],
        check=False, capture_output=True, text=True,
    )
    if result.returncode:
        raise RuntimeError(f"git diff завершился с ошибкой: {result.stderr.strip()}")
    return sorted({line.strip() for line in result.stdout.splitlines() if line.strip()})


def audit(combat_paths: list[str], test_paths: list[str]) -> list[str]:
    tests = set(test_paths)
    failures = []
    for source in combat_paths:
        if not matching_test_exists(source, tests):
            failures.append(f"{source}: нет актуального сопровождающего теста в соответствующем контракте")
    return failures


def main() -> int:
    try:
        changed = changed_files(
            os.environ.get("BASE_SHA", ""),
            os.environ.get("HEAD_SHA", ""),
            os.environ.get("EVENT_NAME", ""),
        )
        combat = existing_paths_at_head([path for path in changed if is_combat_path(path)])
        tests = existing_paths_at_head([path for path in changed if is_test_path(path)])
    except (ValueError, RuntimeError) as exc:
        print(f"::error::{exc}")
        return 2
    combat_changed = [path for path in changed if is_combat_path(path)]
    test_changed = [path for path in changed if is_test_path(path)]
    output_path = os.environ.get("GITHUB_OUTPUT")
    if output_path:
        with open(output_path, "a", encoding="utf-8") as output:
            output.write(f"combat_changed={'true' if combat_changed else 'false'}\n")
            output.write(f"tests_changed={'true' if test_changed else 'false'}\n")
            output.write(f"combat_file_count={len(combat_changed)}\n")
            output.write(f"test_file_count={len(test_changed)}\n")
    print(f"Изменённых файлов: {len(changed)}")
    print(f"Боевых файлов: {len(combat_changed)}")
    print(f"Актуальных тестовых файлов: {len(tests)}")
    if not combat_changed:
        print("Боевые изменения не обнаружены: Arena-контракт проверен, статистический прогон не заявляется.")
        return 0
    failures = audit(combat, tests)
    if failures:
        for failure in failures:
            print(f"::error::{failure}")
        return 1
    print("Для каждого изменённого боевого контракта найден актуальный сопровождающий тест.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
