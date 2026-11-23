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
    """Возвращает добавленные/изменённые файлы; удалённые тесты не засчитываются."""
    if not head:
        print("::error::Не удалось определить HEAD SHA для анализа боевого diff.")
        raise SystemExit(2)

    if not base or set(base) == {"0"}:
        result = subprocess.run(
            ["git", "diff-tree", "--root", "--no-commit-id", "--name-only", "-r", head],
            check=False,
            capture_output=True,
            text=True,
        )
    else:
        exists = subprocess.run(
            ["git", "cat-file", "-e", f"{base}^{{commit}}"],
            check=False,
            capture_output=True,
            text=True,
        )
        if exists.returncode:
            fetch = subprocess.run(
                ["git", "fetch", "--no-tags", "--depth=1", "origin", base],
                check=False,
                capture_output=True,
                text=True,
            )
            if fetch.returncode:
                print("::error::Не удалось получить базовый commit для diff-аудита.")
                print(fetch.stderr)
                raise SystemExit(fetch.returncode)

        result = subprocess.run(
            ["git", "diff", "--name-only", "--diff-filter=ACMRT", base, head],
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
    """Возвращает корневую подсистему, не дробя её по вложенным каталогам."""
    for prefix in COMBAT_PREFIXES:
        if path.startswith(prefix):
            remainder = path[len(prefix):]
            if not remainder:
                return None
            return prefix.rstrip("/").split("/")[-1]
    return None


def test_package_of(path: str) -> str | None:
    if not path.startswith(TEST_ROOT):
        return None
    remainder = path[len(TEST_ROOT):]
    if not remainder:
        return None
    return remainder.split("/", 1)[0]


def missing_test_packages(combat_paths: list[str], test_paths: list[str], selftest_changed: bool) -> list[str]:
    packages = {package_of(path) for path in combat_paths}
    packages.discard(None)
    covered = {test_package_of(path) for path in test_paths}
    covered.discard(None)
    return sorted(package for package in packages if package not in covered and not selftest_changed)


def main() -> int:
    base = os.environ.get("BASE_SHA", "")
    head = os.environ.get("HEAD_SHA", "")
    changed = git_changed_files(base, head)
    combat = [path for path in changed if package_of(path)]
    tests = [path for path in changed if path.startswith("src/test/java/")]
    selftest_changed = SELFTEST_RUNNER in changed
    packages = sorted({package_of(path) for path in combat if package_of(path)})
    missing = missing_test_packages(combat, tests, selftest_changed)

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
        output.write(f"missing_test_packages={','.join(missing)}\n")

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
