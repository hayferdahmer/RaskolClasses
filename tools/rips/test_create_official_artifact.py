#!/usr/bin/env python3
# © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
# SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0

from __future__ import annotations

import base64
import os
import shutil
import subprocess
import tempfile
import unittest
import zipfile

from pathlib import Path
import importlib.util
import sys

MODULE_PATH = Path(__file__).with_name("create-official-artifact.py")
SPEC = importlib.util.spec_from_file_location("create_official_artifact", MODULE_PATH)
if SPEC is None or SPEC.loader is None:
    raise ImportError("Unable to load official artifact signing module")
MODULE = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)

BUILD_IDENTITY = MODULE.BUILD_IDENTITY
MANIFEST = MODULE.MANIFEST
PUBLIC_KEY = MODULE.PUBLIC_KEY
SIGNATURE = MODULE.SIGNATURE
create_release_artifact = MODULE.create_release_artifact
validate_archive_entry = MODULE.validate_archive_entry


@unittest.skipUnless(shutil.which("openssl"), "OpenSSL is required")
class OfficialArtifactSigningTests(unittest.TestCase):

    def test_archive_path_validation_rejects_runtime_forbidden_paths(self) -> None:
        invalid_files = [
            "../escape.class",
            "/absolute.class",
            "C:/drive.class",
            "META-INF\\\\evil.class",
            "folder//file.class",
            "folder/./file.class",
            "folder/../file.class",
            "CON.txt",
            "trailing-dot.",
            "trailing-space ",
            "non-normalized-e" + chr(0x301) + ".class",
        ]
        for path in invalid_files:
            with self.subTest(path=path):
                with self.assertRaises(ValueError):
                    validate_archive_entry(path, False)

    def test_archive_path_validation_accepts_normal_file_and_directory(self) -> None:
        self.assertEqual(validate_archive_entry("dev/raskol/Test.class", False), "dev/raskol/Test.class")
        self.assertEqual(validate_archive_entry("dev/raskol/", True), "dev/raskol")

    def test_release_packager_rejects_case_insensitive_collisions(self) -> None:
        with tempfile.TemporaryDirectory(prefix="rips-collision-test-") as directory:
            root = Path(directory)
            pem = root / "private.pem"
            der = root / "private.pk8"
            jar = root / "plugin.jar"
            subprocess.run(
                ["openssl", "genpkey", "-algorithm", "Ed25519", "-out", str(pem)],
                check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
            )
            subprocess.run(
                ["openssl", "pkcs8", "-topk8", "-nocrypt", "-in", str(pem),
                 "-outform", "DER", "-out", str(der)],
                check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
            )
            with zipfile.ZipFile(jar, "w") as archive:
                archive.writestr("assets/Config.yml", b"one")
                archive.writestr("assets/config.yml", b"two")
            previous = os.environ.get("RIPS_SIGNING_PRIVATE_KEY_B64")
            os.environ["RIPS_SIGNING_PRIVATE_KEY_B64"] = base64.b64encode(der.read_bytes()).decode("ascii")
            try:
                with self.assertRaisesRegex(ValueError, "case-insensitive path collision"):
                    create_release_artifact(
                        jar, "1.15.8", "test-collision", "a" * 40,
                        "2026-11-08T00:00:00Z",
                        "https://github.com/hayferdahmer/RaskolClasses",
                    )
            finally:
                if previous is None:
                    os.environ.pop("RIPS_SIGNING_PRIVATE_KEY_B64", None)
                else:
                    os.environ["RIPS_SIGNING_PRIVATE_KEY_B64"] = previous

    def test_creates_official_identity_and_valid_signature(self) -> None:
        with tempfile.TemporaryDirectory(prefix="rips-official-test-") as directory:
            root = Path(directory)
            pem = root / "private.pem"
            der = root / "private.pk8"
            jar = root / "plugin.jar"

            subprocess.run(
                ["openssl", "genpkey", "-algorithm", "Ed25519", "-out", str(pem)],
                check=True,
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
            )
            subprocess.run(
                ["openssl", "pkcs8", "-topk8", "-nocrypt", "-in", str(pem),
                 "-outform", "DER", "-out", str(der)],
                check=True,
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
            )

            with zipfile.ZipFile(jar, "w") as archive:
                archive.writestr("plugin.yml", b"name: RaskolClasses\nversion: 1.15.8\n")
                archive.writestr("dev/raskol/Test.class", b"test-class-bytes")

            previous = os.environ.get("RIPS_SIGNING_PRIVATE_KEY_B64")
            os.environ["RIPS_SIGNING_PRIVATE_KEY_B64"] = base64.b64encode(der.read_bytes()).decode("ascii")
            try:
                create_release_artifact(
                    jar,
                    "1.15.8",
                    "test-run-1",
                    "a" * 40,
                    "2026-11-08T00:00:00Z",
                    "https://github.com/hayferdahmer/RaskolClasses",
                )
            finally:
                if previous is None:
                    os.environ.pop("RIPS_SIGNING_PRIVATE_KEY_B64", None)
                else:
                    os.environ["RIPS_SIGNING_PRIVATE_KEY_B64"] = previous

            with zipfile.ZipFile(jar, "r") as archive:
                self.assertEqual(len(archive.namelist()), len(set(archive.namelist())))
                self.assertIsNone(archive.testzip())
                identity = archive.read(BUILD_IDENTITY).decode("utf-8")
                self.assertIn("plugin.version=1.15.8\n", identity)
                self.assertIn("build.type=OFFICIAL\n", identity)
                manifest = archive.read(MANIFEST)
                signature = base64.b64decode(archive.read(SIGNATURE).strip(), validate=True)
                public_key = base64.b64decode(archive.read(PUBLIC_KEY).strip(), validate=True)

            manifest_file = root / "manifest.rips"
            signature_file = root / "manifest.sig"
            public_file = root / "public.der"
            manifest_file.write_bytes(manifest)
            signature_file.write_bytes(signature)
            public_file.write_bytes(public_key)

            subprocess.run(
                ["openssl", "pkeyutl", "-verify", "-pubin", "-inkey", str(public_file),
                 "-keyform", "DER", "-rawin", "-in", str(manifest_file),
                 "-sigfile", str(signature_file)],
                check=True,
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
            )


if __name__ == "__main__":
    unittest.main()
