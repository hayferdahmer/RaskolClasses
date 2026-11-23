#!/usr/bin/env python3
# © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
# SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
"""Проверяет боевой diff и требует тесты для затронутых игровых подсистем."""

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
    "src/main/java/dev/raskol/classes/classsystem/",
    "src/main/java/dev/raskol/classes/item/",
    "src/main/java/dev/raskol/classes/effect/",
    "src/main/java/dev/raskol/classes/faction/",
    "src/main/java/dev/raskol/classes/fx/",
)
TEST_ROOT = "src/test/java/dev/raskol/classes/"
SELFTEST_RUNNER = "src/main/java/dev/raskol/classes/selftest/SelftestRunner.java"


def git_changed_files(base: str, head: str) -> list[str]:
    if not head:
        print("::error::Не удалось определить HEAD SHA для анализа боевого diff.")
        raise SystemExit(2)
    if not base or set(base) == {"0"}:
        parent = subprocess.run(
            ["git", "rev-parse", f"{head}^"],
            check=False,
            capture_output=True,
            text=True,
        )
        base = parent.stdout.strip() if parent.returncode == 0 else head
    result = subprocess.run(
        ["git", "diff", "--name-only", "--diff-filter=ACMRT", f"{base}...{head}"],
        check=False,
        capture_output=True,
        text=True,
    )
    if result.returncode:
        print("::error::git diff завершился с ошибкой:")
        print(result.stderr)
        raise SystemExit(result.returncode)
    return [line.strip() for line in result.stdout.splitlines() if line.strip()]


def package_of(path: str) -> str | None:
    for prefix in COMBAT_PREFIXES:
        if path.startswith(prefix):
            remainder = path[len(prefix):]
            package = prefix.rstrip("/").split("/")[-1]
            return package if "/" not in remainder else package + "/" + remainder.split("/")[0]
    return None


def main() -> int:
    base = os.environ.get("BASE_SHA", "")
    head = os.environ.get("HEAD_SHA", "")
    changed = git_changed_files(base, head)
    combat = [path for path in changed if any(path.startswith(prefix) for prefix in COMBAT_PREFIXES)]
    tests = [path for path in changed if path.startswith("src/test/java/")]
    selftest_changed = SELFTEST_RUNNER in changed
    packages = sorted({package_of(path) for path in combat if package_of(path)})
    missing: list[str] = []

    for package in packages:
        expected_prefix = TEST_ROOT + package + "/"
        if not any(path.startswith(expected_prefix) for path in tests) and not selftest_changed:
            missing.append(package)

    print(f"Изменённых файлов: {len(changed)}")
    print(f"Боевых файлов: {len(combat)}")
    for path in combat:
        print(f"  COMBAT: {path}")
    for path in tests:
        print(f"  TEST:   {path}")
    for package in packages:
        print(f"  PACKAGE: {package}")

    with open(os.environ["GITHUB_OUTPUT"], "a", encoding="utf-8") as output:
        output.write(f"combat_changed={'true' if combat else 'false'}\n")
        output.write(f"tests_changed={'true' if tests or selftest_changed else 'false'}\n")
        output.write(f"combat_packages={','.join(packages)}\n")

    if missing:
        print("::error::Для изменённых боевых подсистем не найдены тесты в соответствующих test-пакетах.")
        for package in missing:
            print(f"::error::Нет изменённого теста для пакета: {package}")
        print("Добавьте regression-тест в src/test/java/dev/raskol/classes/<package>/ либо обновите SelftestRunner.")
        return 1

    print("Контракт тестового сопровождения выполнен." if combat else "Боевые исходники не изменены.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
