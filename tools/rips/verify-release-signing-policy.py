#!/usr/bin/env python3
# © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
# SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
"""
RIPS release signing policy enforcement.

Checks:
1. TrustedPublicKeyRegistry contains exactly one ACTIVE key.
2. Every pinned key is a canonical Base64 Ed25519 X.509 public key.
3. Duplicate public keys are rejected.
4. The CI private key is a valid PKCS#8 Ed25519 key.
5. The public key derived from the CI private key matches the ACTIVE key.

Private key material is never printed or stored in the repository.
"""

from __future__ import annotations

import argparse
import base64
import hashlib
import hmac
import os
import re
import shutil
import subprocess
import sys
import tempfile

from dataclasses import dataclass
from pathlib import Path


REGISTRY_RELATIVE_PATH = Path(
    "src/main/java/dev/raskol/classes/integrity/crypto/"
    "TrustedPublicKeyRegistry.java"
)

PRIVATE_KEY_ENV = "RIPS_SIGNING_PRIVATE_KEY_B64"

ED25519_SPKI_PREFIX = bytes.fromhex(
    "302a300506032b6570032100"
)


PINNED_BLOCK_PATTERN = re.compile(
    r"private\s+static\s+final\s+List<PinnedKeyConfiguration>\s+"
    r"PINNED_KEYS\s*=\s*List\.of\s*\((?P<body>.*?)\)\s*;",
    re.DOTALL,
)

KEY_ENTRY_PATTERN = re.compile(
    r'new\s+PinnedKeyConfiguration\s*\(\s*'
    r'"(?P<encoded>[A-Za-z0-9+/=]+)"\s*,\s*'
    r'TrustedKeyStatus\.(?P<status>ACTIVE|VERIFY_ONLY|REVOKED)'
    r"\s*\)"
)


@dataclass(frozen=True)
class RegistryKey:
    status: str
    fingerprint: str


def is_ed25519_x509_public_key(encoded: bytes) -> bool:
    """
    Validates the expected Ed25519 SubjectPublicKeyInfo encoding.
    """

    return (
        len(encoded) == 44
        and encoded.startswith(ED25519_SPKI_PREFIX)
    )


def parse_pinned_keys(source: str) -> tuple[RegistryKey, ...]:
    """
    Strictly parses the PINNED_KEYS configuration.

    Unknown expressions or unsupported configuration syntax
    cause a failure instead of being silently ignored.
    """

    declarations = list(PINNED_BLOCK_PATTERN.finditer(source))

    if len(declarations) != 1:
        raise ValueError(
            "Expected exactly one PINNED_KEYS declaration"
        )

    body = declarations[0].group("body")
    matches = list(KEY_ENTRY_PATTERN.finditer(body))

    if not matches:
        raise ValueError(
            "PINNED_KEYS does not contain any valid key entries"
        )

    entries: list[RegistryKey] = []
    fingerprints: set[str] = set()

    cursor = 0

    for match in matches:
        gap = body[cursor:match.start()]

        if not re.fullmatch(r"[\s,]*", gap):
            raise ValueError(
                "Unsupported expression in PINNED_KEYS configuration"
            )

        encoded_key = match.group("encoded")
        status = match.group("status")

        try:
            decoded_key = base64.b64decode(
                encoded_key,
                validate=True
            )
        except ValueError as exception:
            raise ValueError(
                "Invalid Base64 trusted public key"
            ) from exception

        if base64.b64encode(decoded_key).decode("ascii") != encoded_key:
            raise ValueError(
                "Non-canonical Base64 trusted public key"
            )

        if not is_ed25519_x509_public_key(decoded_key):
            raise ValueError(
                "Pinned public key is not a valid Ed25519 X.509 key"
            )

        fingerprint = hashlib.sha256(decoded_key).hexdigest()

        if fingerprint in fingerprints:
            raise ValueError(
                f"Duplicate trusted public key: {fingerprint}"
            )

        fingerprints.add(fingerprint)

        entries.append(
            RegistryKey(
                status=status,
                fingerprint=fingerprint
            )
        )

        cursor = match.end()

    remaining = body[cursor:]

    if not re.fullmatch(r"[\s,]*", remaining):
        raise ValueError(
            "Unsupported trailing expression in PINNED_KEYS"
        )

    active_keys = [
        entry
        for entry in entries
        if entry.status == "ACTIVE"
    ]

    if len(active_keys) != 1:
        raise ValueError(
            "Release policy requires exactly one ACTIVE key; "
            f"found {len(active_keys)}"
        )

    return tuple(entries)


def derive_private_key_public_fingerprint(
    encoded_private_key: str
) -> str:
    """
    Derives the public key from a Base64 PKCS#8 private key.

    OpenSSL is used only for local key decoding and public-key derivation.
    Private key data is written to a temporary file with restricted
    permissions and removed automatically afterwards.
    """

    encoded_private_key = encoded_private_key.strip()

    if not encoded_private_key:
        raise ValueError(
            f"Required environment variable {PRIVATE_KEY_ENV} is empty"
        )

    try:
        private_key_der = base64.b64decode(
            encoded_private_key,
            validate=True
        )
    except ValueError as exception:
        raise ValueError(
            "The CI private key is not valid Base64"
        ) from exception

    if (
        base64.b64encode(private_key_der).decode("ascii")
        != encoded_private_key
    ):
        raise ValueError(
            "The CI private key Base64 encoding is not canonical"
        )

    openssl = shutil.which("openssl")

    if openssl is None:
        raise ValueError(
            "OpenSSL is required for release signing policy validation"
        )

    with tempfile.TemporaryDirectory(
        prefix="rips-signing-policy-"
    ) as temporary_directory:

        temporary_path = Path(temporary_directory)

        try:
            os.chmod(temporary_path, 0o700)
        except OSError:
            pass

        private_path = temporary_path / "private.pk8"
        public_path = temporary_path / "public.der"

        private_path.write_bytes(private_key_der)

        try:
            os.chmod(private_path, 0o600)
        except OSError:
            pass

        try:
            result = subprocess.run(
                [
                    openssl,
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
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
                check=False,
                timeout=20,
            )
        except subprocess.SubprocessError as exception:
            raise ValueError(
                "OpenSSL could not process the CI private key"
            ) from exception

        if result.returncode != 0:
            raise ValueError(
                "The CI private key is not a valid PKCS#8 Ed25519 key"
            )

        try:
            public_key_der = public_path.read_bytes()
        except OSError as exception:
            raise ValueError(
                "Could not read the derived public key"
            ) from exception

    if not is_ed25519_x509_public_key(public_key_der):
        raise ValueError(
            "The CI private key did not produce a valid Ed25519 public key"
        )

    return hashlib.sha256(public_key_der).hexdigest()


def verify_release_signing_policy(
    registry_source: str,
    encoded_private_key: str,
) -> str:
    """
    Enforces the complete release signing policy.

    Returns the ACTIVE public key fingerprint on success.
    """

    entries = parse_pinned_keys(registry_source)

    active_key = next(
        entry
        for entry in entries
        if entry.status == "ACTIVE"
    )

    private_key_fingerprint = (
        derive_private_key_public_fingerprint(
            encoded_private_key
        )
    )

    if not hmac.compare_digest(
        private_key_fingerprint,
        active_key.fingerprint
    ):
        raise ValueError(
            "CI private key does not match the ACTIVE trusted public key"
        )

    return active_key.fingerprint


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Validate RIPS release signing policy"
    )

    parser.add_argument(
        "--registry",
        type=Path,
        default=None,
        help="Path to TrustedPublicKeyRegistry.java",
    )

    arguments = parser.parse_args()

    repository_root = Path(__file__).resolve().parents[2]

    registry_path = (
        arguments.registry
        if arguments.registry is not None
        else repository_root / REGISTRY_RELATIVE_PATH
    )

    try:
        registry_source = registry_path.read_text(
            encoding="utf-8"
        )

        encoded_private_key = os.environ.get(
            PRIVATE_KEY_ENV,
            ""
        )

        fingerprint = verify_release_signing_policy(
            registry_source,
            encoded_private_key
        )

    except (
        OSError,
        ValueError,
        subprocess.SubprocessError,
    ) as exception:
        print(
            f"RIPS release signing policy: FAILED: {exception}",
            file=sys.stderr,
        )
        return 1

    print("RIPS release signing policy: PASS")
    print(f"ACTIVE signing key fingerprint: {fingerprint}")

    return 0


if __name__ == "__main__":
    sys.exit(main())
