#!/usr/bin/env python3
# © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
# SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
"""Проверяет diff боевых изменений и наличие сопровождающих тестов."""

from __future__ import annotations

import os
import subprocess
import sys
from pathlib import PurePosixPath

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
)
TEST_PREFIXES = (
    "src/test/java/",
    "src/main/java/dev/raskol/classes/selftest/",
)


def git_changed_files(base: str, head: str) -> list[str]:
    if not base or not head or set(base) == {"0"}:
        print("::error::Не удалось определить базовый SHA для анализа боевого diff.")
        raise SystemExit(2)
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


def main() -> int:
    base = os.environ.get("BASE_SHA", "")
    head = os.environ.get("HEAD_SHA", "")
    changed = git_changed_files(base, head)
    combat = [p for p in changed if p.startswith(COMBAT_PREFIXES)]
    tests = [p for p in changed if p.startswith(TEST_PREFIXES) and "/test/" in p or p.startswith("src/test/java/")]

    print(f"Изменённых файлов: {len(changed)}")
    print(f"Боевых файлов: {len(combat)}")
    for path in combat:
        print(f"  COMBAT: {path}")
    for path in tests:
        print(f"  TEST:   {path}")

    with open(os.environ["GITHUB_OUTPUT"], "a", encoding="utf-8") as output:
        output.write(f"combat_changed={'true' if combat else 'false'}\n")
        output.write(f"tests_changed={'true' if tests else 'false'}\n")

    if combat and not tests:
        print("::error::Боевой код изменён, но в diff нет сопровождающих тестов.")
        print("Добавьте regression/unit test или обновите SelftestRunner для изменённого контракта.")
        return 1

    if not combat:
        print("Боевые исходники не изменены: обязательный Maven gate всё равно выполнится.")
    else:
        print("Контракт наличия тестов выполнен. Далее запускается полный Maven verify и SpotBugs.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
