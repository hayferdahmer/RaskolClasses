#!/usr/bin/env python3
# © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
# SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0

import base64
import importlib.util
import shutil
import subprocess
import sys
import tempfile
import unittest

from pathlib import Path

# 1.15.8-fix: файл политики называется verify-release-signing-policy.py (дефисы —
# CLI-соглашение), поэтому обычный `import verify_release_signing_policy` невозможен
# (ModuleNotFoundError в CI). Загружаем модуль явно по пути через importlib.
# ВАЖНО: регистрируем модуль в sys.modules ДО exec_module — иначе декоратор
# @dataclass внутри загружаемого файла падает на обращении к sys.modules[cls.__module__]
# с AttributeError: 'NoneType' object has no attribute '__dict__'.
_MODULE_PATH = Path(__file__).resolve().parent / "verify-release-signing-policy.py"
_spec = importlib.util.spec_from_file_location(
    "verify_release_signing_policy",
    _MODULE_PATH,
)
_module = importlib.util.module_from_spec(_spec)
sys.modules[_module.__name__] = _module
_spec.loader.exec_module(_module)

parse_pinned_keys = _module.parse_pinned_keys
verify_release_signing_policy = _module.verify_release_signing_policy


ED25519_SPKI_PREFIX = bytes.fromhex(
    "302a300506032b6570032100"
)


def encode_public_key(public_key: bytes) -> str:
    return base64.b64encode(public_key).decode("ascii")


def build_registry(entries: list[tuple[str, str]]) -> str:
    configured_entries = []

    for encoded_key, status in entries:
        configured_entries.append(
            "new PinnedKeyConfiguration("
            f'"{encoded_key}", TrustedKeyStatus.{status}'
            ")"
        )

    body = ",\n".join(configured_entries)

    return f"""
    private static final List<PinnedKeyConfiguration> PINNED_KEYS =
            List.of(
                {body}
            );
    """


class ReleaseWorkflowGatePolicyTest(unittest.TestCase):

    def test_analyze_is_required_for_official_release_jobs(self):
        workflow_path = (
            Path(__file__).resolve().parents[2]
            / ".github"
            / "workflows"
            / "build.yml"
        )
        workflow = workflow_path.read_text(encoding="utf-8")

        self.assertIn(
            "continue-on-error: ${{ !startsWith(github.ref, 'refs/tags/v') "
            "&& !(github.event_name == 'workflow_dispatch' "
            "&& github.ref == 'refs/heads/main') }}",
            workflow,
        )
        self.assertIn(
            "signing-verification:\n    needs: [build, analyze]",
            workflow,
        )
        self.assertIn(
            "if: github.event_name == 'workflow_dispatch' && github.ref == 'refs/heads/main'",
            workflow,
        )
        self.assertIn('ET.parse("pom.xml")', workflow)
        self.assertIn('f"plugin.version={expected_version}"', workflow)
        self.assertIn(
            "release:\n    needs: [build, analyze]",
            workflow,
        )
        self.assertIn("if: startsWith(github.ref, 'refs/tags/v')", workflow)
        self.assertNotIn("refs/heads/release/1.15.8", workflow)
        self.assertNotIn("raskol-classes-1.15.8.jar", workflow)


class TrustedPublicKeyRegistryPolicyTest(unittest.TestCase):

    def setUp(self) -> None:
        self.public_key_a = encode_public_key(
            ED25519_SPKI_PREFIX + bytes([1] * 32)
        )

        self.public_key_b = encode_public_key(
            ED25519_SPKI_PREFIX + bytes([2] * 32)
        )

    def test_accepts_one_active_and_other_lifecycle_states(self):
        source = build_registry([
            (self.public_key_a, "ACTIVE"),
            (self.public_key_b, "VERIFY_ONLY"),
        ])

        entries = parse_pinned_keys(source)

        self.assertEqual(
            ["ACTIVE", "VERIFY_ONLY"],
            [entry.status for entry in entries]
        )

    def test_rejects_registry_without_active_key(self):
        source = build_registry([
            (self.public_key_a, "VERIFY_ONLY"),
            (self.public_key_b, "REVOKED"),
        ])

        with self.assertRaisesRegex(
            ValueError,
            "exactly one ACTIVE"
        ):
            parse_pinned_keys(source)

    def test_rejects_multiple_active_keys(self):
        source = build_registry([
            (self.public_key_a, "ACTIVE"),
            (self.public_key_b, "ACTIVE"),
        ])

        with self.assertRaisesRegex(
            ValueError,
            "exactly one ACTIVE"
        ):
            parse_pinned_keys(source)

    def test_rejects_duplicate_public_keys(self):
        source = build_registry([
            (self.public_key_a, "ACTIVE"),
            (self.public_key_a, "VERIFY_ONLY"),
        ])

        with self.assertRaisesRegex(
            ValueError,
            "Duplicate trusted public key"
        ):
            parse_pinned_keys(source)

    def test_rejects_invalid_base64(self):
        source = build_registry([
            ("not-valid-base64!", "ACTIVE"),
        ])

        with self.assertRaises(ValueError):
            parse_pinned_keys(source)

    def test_rejects_invalid_ed25519_encoding(self):
        invalid_key = encode_public_key(b"invalid-key")

        source = build_registry([
            (invalid_key, "ACTIVE"),
        ])

        with self.assertRaisesRegex(
            ValueError,
            "not a valid Ed25519"
        ):
            parse_pinned_keys(source)

    def test_rejects_unknown_registry_expression(self):
        # 1.15.8-fix: первый ключ должен быть ВАЛИДНЫМ Ed25519 (44 байта),
        # иначе парсер падает на проверке ключа раньше, чем доходит до
        # разбора неизвестного выражения UNKNOWN_KEY_CONFIGURATION.
        source = f"""
        private static final List<PinnedKeyConfiguration> PINNED_KEYS =
                List.of(
                    new PinnedKeyConfiguration(
                        "{self.public_key_a}",
                        TrustedKeyStatus.ACTIVE
                    ),
                    UNKNOWN_KEY_CONFIGURATION
                );
        """

        with self.assertRaisesRegex(
            ValueError,
            "Unsupported expression"
        ):
            parse_pinned_keys(source)


@unittest.skipUnless(
    shutil.which("openssl"),
    "OpenSSL is required for this integration test",
)
class ReleaseSigningKeyMatchTest(unittest.TestCase):

    def test_private_key_matches_active_public_key(self):
        with tempfile.TemporaryDirectory() as directory:
            folder = Path(directory)

            private_path = folder / "private.pk8"
            public_path = folder / "public.der"

            subprocess.run(
                [
                    shutil.which("openssl"),
                    "genpkey",
                    "-algorithm",
                    "ED25519",
                    "-outform",
                    "DER",
                    "-out",
                    str(private_path),
                ],
                check=True,
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
                timeout=20,
            )

            subprocess.run(
                [
                    shutil.which("openssl"),
                    "pkey",
                    "-inform",
                    "DER",
                    "-in",
                    str(private_path),
                    "-pubout",
                    "-outform",
                    "DER",
                    "-out",
                    str(public_path),
                ],
                check=True,
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
                timeout=20,
            )

            private_key_b64 = base64.b64encode(
                private_path.read_bytes()
            ).decode("ascii")

            public_key_b64 = base64.b64encode(
                public_path.read_bytes()
            ).decode("ascii")

            registry_source = build_registry([
                (public_key_b64, "ACTIVE"),
            ])

            fingerprint = verify_release_signing_policy(
                registry_source,
                private_key_b64,
            )

            self.assertEqual(64, len(fingerprint))


if __name__ == "__main__":
    unittest.main()
