# © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
# SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
"""Регрессионные тесты diff-аудитора Arena."""

import subprocess
import unittest
from unittest.mock import patch

from check_combat_diff import (
    audit, existing_paths_at_head, is_combat_path, is_test_path, matching_test_exists,
)


class CombatDiffAuditTests(unittest.TestCase):
    def test_combat_packages_are_detected(self):
        self.assertTrue(is_combat_path("src/main/java/dev/raskol/classes/combat/DamageService.java"))
        self.assertTrue(is_combat_path("src/main/resources/items.yml"))
        self.assertFalse(is_combat_path("src/main/java/dev/raskol/classes/gui/Menu.java"))

    def test_test_paths_include_unit_tests_and_selftests(self):
        self.assertTrue(is_test_path("src/test/java/dev/raskol/classes/combat/DamageServiceTest.java"))
        self.assertTrue(is_test_path("src/main/java/dev/raskol/classes/selftest/CombatSelftest.java"))

    def test_java_change_requires_test_in_same_package(self):
        source = "src/main/java/dev/raskol/classes/combat/DamageService.java"
        tests = {"src/test/java/dev/raskol/classes/combat/DamageServiceTest.java"}
        self.assertTrue(matching_test_exists(source, tests))
        self.assertEqual([], audit([source], list(tests)))

    def test_test_in_unrelated_package_does_not_cover_source(self):
        source = "src/main/java/dev/raskol/classes/combat/DamageService.java"
        tests = ["src/test/java/dev/raskol/classes/gui/MenuTest.java"]
        self.assertEqual(1, len(audit([source], tests)))

    def test_deleted_test_is_not_counted_as_coverage(self):
        with patch("check_combat_diff.subprocess.run", return_value=subprocess.CompletedProcess([], 1)):
            self.assertEqual([], existing_paths_at_head(["src/test/java/dev/raskol/classes/combat/DeletedTest.java"]))

    def test_existing_test_is_kept_as_coverage_candidate(self):
        with patch("check_combat_diff.subprocess.run", return_value=subprocess.CompletedProcess([], 0)):
            self.assertEqual(
                ["src/test/java/dev/raskol/classes/combat/DamageServiceTest.java"],
                existing_paths_at_head(["src/test/java/dev/raskol/classes/combat/DamageServiceTest.java"]),
            )

    def test_resource_change_requires_domain_specific_test(self):
        resource = "src/main/resources/items.yml"
        self.assertEqual(1, len(audit([resource], [])))
        self.assertEqual(1, len(audit([resource], ["src/test/java/dev/raskol/classes/config/ConfigTest.java"])))
        self.assertEqual([], audit([resource], ["src/test/java/dev/raskol/classes/item/ItemConfigLoaderTest.java"]))

    def test_kit_and_talent_resources_require_matching_tests(self):
        self.assertEqual(
            1,
            len(audit(["src/main/resources/kits/warrior.yml"], ["src/test/java/dev/raskol/classes/item/ItemTest.java"])),
        )
        self.assertEqual(
            [],
            audit(["src/main/resources/talents/warrior.yml"], ["src/test/java/dev/raskol/classes/talent/TalentConfigTest.java"]),
        )

    def test_no_combat_changes_is_a_valid_noop(self):
        self.assertEqual([], audit([], []))


if __name__ == "__main__":
    unittest.main()
