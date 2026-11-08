#!/usr/bin/env python3
# © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
# SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
"""Create an OFFICIAL RIPS identity and signed integrity manifest in a release JAR.

The private key is read only from RIPS_SIGNING_PRIVATE_KEY_B64. It is never
written to the JAR, printed, or placed in a command-line argument.
"""

from __future__ import annotations

import argparse
import base64
import hashlib
import os
import re
import shutil
import subprocess
import tempfile
import unicodedata
import zipfile

from pathlib import Path
from urllib.parse import quote

MANIFEST = "META-INF/raskol/integrity/manifest.rips"
SIGNATURE = "META-INF/raskol/integrity/manifest.sig"
PUBLIC_KEY = "META-INF/raskol/integrity/public-key.txt"
BUILD_IDENTITY = "META-INF/raskol/build.properties"
PRIVATE_KEY_ENV = "RIPS_SIGNING_PRIVATE_KEY_B64"
REPOSITORY = "https://github.com/hayferdahmer/RaskolClasses"
EXCLUDED = {MANIFEST, SIGNATURE, PUBLIC_KEY}
SPKI_PREFIX = bytes.fromhex("302a300506032b6570032100")


def run_openssl(arguments: list[str]) -> None:
    executable = shutil.which("openssl")
    if executable is None:
        raise RuntimeError("OpenSSL is required to create an official RIPS signature")
    result = subprocess.run(
        [executable, *arguments],
        stdout=subprocess.DEVNULL,
        stderr=subprocess.PIPE,
        check=False,
        timeout=30,
    )
    if result.returncode:
        raise RuntimeError("OpenSSL could not sign the official RIPS manifest")


def validate_text(value: str, label: str) -> str:
    if not value or value.strip() != value or any(c in value for c in "\r\n\t"):
        raise ValueError(f"Invalid {label}")
    return value


def validate_archive_entry(name: str, is_directory: bool) -> str:
    """Mirror ArchiveEntryPathValidator so signing never blesses an archive RIPS rejects."""
    def utf16_length(value: str) -> int:
        return len(value.encode("utf-16-le")) // 2

    if not name or utf16_length(name) > 4096:
        raise ValueError("Archive entry path has an invalid length")
    if unicodedata.normalize("NFC", name) != name:
        raise ValueError(f"Archive entry path is not Unicode NFC-normalized: {name!r}")
    if name.startswith(("/", "\\")) or "\\" in name or "\0" in name:
        raise ValueError(f"Archive entry path is absolute or contains forbidden separators: {name!r}")

    path = name
    if is_directory:
        if not path.endswith("/") or path.endswith("//"):
            raise ValueError(f"Directory entry must have exactly one trailing slash: {name!r}")
        path = path[:-1]
    elif path.endswith("/"):
        raise ValueError(f"File entry must not end with a slash: {name!r}")

    if not path or utf16_length(path) > 4096 or re.match(r"^[A-Za-z]:", path):
        raise ValueError(f"Archive entry path is empty, too long, or drive-qualified: {name!r}")

    for segment in path.split("/"):
        if not segment or segment in (".", "..") or utf16_length(segment) > 255:
            raise ValueError(f"Archive entry contains an invalid path segment: {name!r}")
        if segment.endswith((".", " ")):
            raise ValueError(f"Archive path segment has a non-portable suffix: {name!r}")
        for character in segment:
            category = __import__("unicodedata").category(character)
            if category in ("Cc", "Cf", "Cs") or character in '<>:"|?*':
                raise ValueError(f"Archive path contains a non-portable character: {name!r}")
        device = segment.split(".", 1)[0].upper()
        if device in {"CON", "PRN", "AUX", "NUL"} or re.fullmatch(r"(?:COM|LPT)[1-9]", device):
            raise ValueError(f"Archive path contains a reserved device name: {name!r}")
    return path


def canonical_manifest(version: str, build_id: str, commit: str, timestamp: str,
                       repository: str, entries: list[tuple[str, bytes]]) -> bytes:
    fields = [
        ("PRODUCT", "RaskolClasses"),
        ("VERSION", version),
        ("BUILD_ID", build_id),
        ("COMMIT", commit),
        ("TIMESTAMP", timestamp),
        ("REPOSITORY", repository),
        ("BUILD_TYPE", "OFFICIAL"),
    ]
    lines = ["RASKOL-INTEGRITY-MANIFEST\t1"]
    for key, value in fields:
        encoded = base64.urlsafe_b64encode(value.encode("utf-8")).decode("ascii").rstrip("=")
        lines.append(f"{key}\t{encoded}")
    for name, data in sorted(entries, key=lambda item: item[0]):
        encoded_name = base64.urlsafe_b64encode(name.encode("utf-8")).decode("ascii").rstrip("=")
        lines.append(f"FILE\t{encoded_name}\t{len(data)}\t{hashlib.sha256(data).hexdigest()}")
    return ("\n".join(lines) + "\n").encode("utf-8")


def create_release_artifact(jar_path: Path, version: str, build_id: str,
                            commit: str, timestamp: str, repository: str) -> None:
    version = validate_text(version, "version")
    build_id = validate_text(build_id, "build id")
    commit = validate_text(commit, "commit SHA")
    timestamp = validate_text(timestamp, "build timestamp")
    repository = validate_text(repository, "repository")
    if not re.fullmatch(r"[0-9a-f]{40,64}", commit):
        raise ValueError("Official build requires a full Git commit SHA")
    if not re.fullmatch(r"\d+\.\d+\.\d+", version):
        raise ValueError("Release version must be a stable SemVer version")

    encoded_key = os.environ.get(PRIVATE_KEY_ENV, "")
    if not encoded_key:
        raise ValueError(f"Missing required environment variable {PRIVATE_KEY_ENV}")
    try:
        private_der = base64.b64decode(encoded_key, validate=True)
    except ValueError as exc:
        raise ValueError("Release private key is not canonical Base64") from exc
    if not private_der or base64.b64encode(private_der).decode("ascii") != encoded_key:
        raise ValueError("Release private key is not canonical Base64")

    if not jar_path.is_file():
        raise ValueError(f"JAR does not exist: {jar_path}")

    with tempfile.TemporaryDirectory(prefix="rips-release-") as temporary:
        temp = Path(temporary)
        try:
            os.chmod(temp, 0o700)
        except OSError:
            pass
        key_file = temp / "release-key.pk8"
        public_file = temp / "release-public.der"
        manifest_file = temp / "manifest.rips"
        signature_file = temp / "manifest.sig"
        key_file.write_bytes(private_der)
        try:
            os.chmod(key_file, 0o600)
        except OSError:
            pass

        run_openssl(["pkey", "-inform", "DER", "-in", str(key_file),
                     "-pubout", "-outform", "DER", "-out", str(public_file)])
        public_der = public_file.read_bytes()
        if len(public_der) != 44 or not public_der.startswith(SPKI_PREFIX):
            raise ValueError("Release private key is not an Ed25519 PKCS#8 key")

        identity = (
            f"product.id=RaskolClasses\n"
            f"plugin.version={version}\n"
            f"build.id={build_id}\n"
            f"git.commit={commit}\n"
            f"source.repository={repository}\n"
            f"build.type=OFFICIAL\n"
            f"build.timestamp={timestamp}\n"
        ).encode("utf-8")

        with zipfile.ZipFile(jar_path, "r") as source:
            infos = source.infolist()
            names = [item.filename for item in infos]
            if len(names) != len(set(names)):
                raise ValueError("Input JAR contains duplicate ZIP entry names")
            if any(name in names for name in EXCLUDED | {BUILD_IDENTITY}):
                raise ValueError("Input JAR already contains RIPS release metadata")
            content: list[tuple[zipfile.ZipInfo, bytes]] = []
            manifest_entries: list[tuple[str, bytes]] = []
            comparison_keys: set[str] = set()
            for info in infos:
                name = info.filename
                validated_name = validate_archive_entry(name, info.is_dir())
                comparison_key = validated_name.lower()
                if comparison_key in comparison_keys:
                    raise ValueError(f"JAR contains a case-insensitive path collision: {name}")
                comparison_keys.add(comparison_key)
                if info.is_dir():
                    content.append((info, b""))
                    continue
                data = source.read(info)
                content.append((info, data))
                manifest_entries.append((validated_name, data))

        manifest_entries.append((BUILD_IDENTITY, identity))
        manifest_bytes = canonical_manifest(
            version, build_id, commit, timestamp, repository, manifest_entries
        )
        manifest_file.write_bytes(manifest_bytes)
        run_openssl(["pkeyutl", "-sign", "-inkey", str(key_file), "-keyform", "DER",
                     "-rawin", "-in", str(manifest_file), "-out", str(signature_file)])
        signature = signature_file.read_bytes()
        if len(signature) != 64:
            raise RuntimeError("OpenSSL returned an invalid Ed25519 signature")

        additions = [
            (BUILD_IDENTITY, identity),
            (MANIFEST, manifest_bytes),
            (SIGNATURE, base64.b64encode(signature) + b"\n"),
            (PUBLIC_KEY, base64.b64encode(public_der) + b"\n"),
        ]
        temporary_jar = jar_path.with_suffix(jar_path.suffix + ".rips-tmp")
        try:
            with zipfile.ZipFile(temporary_jar, "w") as output:
                for info, data in content:
                    output.writestr(info, data)
                for name, data in additions:
                    output.writestr(name, data, compress_type=zipfile.ZIP_DEFLATED)
            with zipfile.ZipFile(temporary_jar, "r") as check:
                if len(check.namelist()) != len(set(check.namelist())):
                    raise RuntimeError("Generated JAR contains duplicate entries")
                if check.testzip() is not None:
                    raise RuntimeError("Generated JAR failed ZIP integrity validation")
            temporary_jar.replace(jar_path)
        finally:
            if temporary_jar.exists():
                temporary_jar.unlink()


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jar", required=True, type=Path)
    parser.add_argument("--version", required=True)
    parser.add_argument("--build-id", required=True)
    parser.add_argument("--commit", required=True)
    parser.add_argument("--timestamp", required=True)
    parser.add_argument("--repository", default=REPOSITORY)
    args = parser.parse_args()
    try:
        create_release_artifact(args.jar, args.version, args.build_id,
                                args.commit, args.timestamp, args.repository)
    except (OSError, ValueError, RuntimeError, zipfile.BadZipFile,
            subprocess.SubprocessError) as exc:
        print(f"RIPS official artifact signing: FAILED: {exc}", file=__import__("sys").stderr)
        return 1
    print("RIPS official artifact signing: PASS")
    print(f"Official artifact: {args.jar.name}")
    print("Signing key material: not included in artifact or output")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
