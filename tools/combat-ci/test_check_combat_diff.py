# © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
# SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
"""Регрессионные тесты для аудита боевого diff."""

import unittest
from subprocess import CompletedProcess
from unittest.mock import patch

from check_combat_diff import git_changed_files, missing_test_packages, package_of, test_package_of


class CombatDiffAuditTest(unittest.TestCase):
    def test_nested_source_maps_to_root_subsystem(self):
        self.assertEqual(
            "combat",
            package_of("src/main/java/dev/raskol/classes/combat/pipeline/DamageRouter.java"),
        )

    def test_nested_test_covers_root_subsystem(self):
        self.assertEqual(
            "combat",
            test_package_of("src/test/java/dev/raskol/classes/combat/pipeline/DamageRouterTest.java"),
        )

    def test_matching_package_test_is_required(self):
        combat = ["src/main/java/dev/raskol/classes/combat/DamageRouter.java"]
        tests = ["src/test/java/dev/raskol/classes/ability/UnrelatedAbilityTest.java"]
        self.assertEqual(["combat"], missing_test_packages(combat, tests, False))

    def test_matching_package_test_satisfies_gate(self):
        combat = ["src/main/java/dev/raskol/classes/combat/DamageRouter.java"]
        tests = ["src/test/java/dev/raskol/classes/combat/DamageRouterTest.java"]
        self.assertEqual([], missing_test_packages(combat, tests, False))

    def test_selftest_runner_is_an_explicit_fallback(self):
        combat = ["src/main/java/dev/raskol/classes/combat/DamageRouter.java"]
        self.assertEqual([], missing_test_packages(combat, [], True))

    @patch("check_combat_diff.subprocess.run")
    def test_zero_base_uses_root_commit_diff(self, run):
        run.return_value = CompletedProcess(
            [], 0, "src/main/java/dev/raskol/classes/combat/A.java\n", ""
        )
        files = git_changed_files("0000000000000000000000000000000000000000", "abc123")
        self.assertEqual(["src/main/java/dev/raskol/classes/combat/A.java"], files)
        self.assertEqual("diff-tree", run.call_args.args[0][1])

    @patch("check_combat_diff.subprocess.run")
    def test_missing_base_is_fetched_before_diff(self, run):
        run.side_effect = [
            CompletedProcess([], 1, "", "missing"),
            CompletedProcess([], 0, "", ""),
            CompletedProcess(
                [], 0, "src/main/java/dev/raskol/classes/pet/Pet.java\n", ""
            ),
        ]
        files = git_changed_files("base-sha", "head-sha")
        self.assertEqual(["src/main/java/dev/raskol/classes/pet/Pet.java"], files)
        self.assertEqual("fetch", run.call_args_list[1].args[0][1])
        self.assertIn("base-sha", run.call_args_list[1].args[0])

    def test_multiple_subsystems_are_checked_independently(self):
        combat = [
            "src/main/java/dev/raskol/classes/combat/DamageRouter.java",
            "src/main/java/dev/raskol/classes/pet/PetCombatService.java",
        ]
        tests = ["src/test/java/dev/raskol/classes/combat/DamageRouterTest.java"]
        self.assertEqual(["pet"], missing_test_packages(combat, tests, False))


if __name__ == "__main__":
    unittest.main()
