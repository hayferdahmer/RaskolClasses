#!/usr/bin/env python3
# © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
# SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
"""Read-only, deterministic security audit for the RIPS source tree.

This is a guardrail, not a proof of security. It never reads CI secrets,
creates signatures, modifies source files, or accesses the network.
"""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
MAIN = ROOT / "src/main/java/dev/raskol/classes/integrity"
TEST = ROOT / "src/test/java/dev/raskol/classes/integrity"
RESULTS: list[dict[str, str]] = []


def record(check_id: str, status: str, detail: str) -> None:
    RESULTS.append({"id": check_id, "status": status, "detail": detail})
    print(f"[{status}] {check_id}: {detail}")


def read(relative: str) -> str | None:
    path = ROOT / relative
    try:
        return path.read_text(encoding="utf-8")
    except OSError as exc:
        record("INPUT-" + relative.replace("/", "-"), "FAIL", f"Cannot read required file: {exc}")
        return None


def require_file(relative: str) -> bool:
    path = ROOT / relative
    if path.is_file() and path.stat().st_size > 0:
        record("FILE-" + relative.replace("/", "-"), "PASS", "Required source/test file exists.")
        return True
    record("FILE-" + relative.replace("/", "-"), "FAIL", "Required source/test file is missing or empty.")
    return False


def contains(source: str | None, token: str) -> bool:
    return source is not None and token in source


def main() -> int:
    print("RIPS Security Auditor v1 — read-only static checks")
    print(f"Repository root: {ROOT}")

    required = [
        "src/main/java/dev/raskol/classes/integrity/crypto/Ed25519SignatureService.java",
        "src/main/java/dev/raskol/classes/integrity/crypto/TrustedPublicKeyRegistry.java",
        "src/main/java/dev/raskol/classes/integrity/manifest/IntegrityManifestParser.java",
        "src/main/java/dev/raskol/classes/integrity/verification/StartupIntegrityVerifier.java",
        "src/main/java/dev/raskol/classes/integrity/artifact/JarArchiveIntegrityVerifier.java",
        "src/main/java/dev/raskol/classes/integrity/runtime/RuntimeIntegrityMonitor.java",
        "src/main/java/dev/raskol/classes/integrity/policy/RuntimeIntegrityEnforcementPolicy.java",
        "src/test/java/dev/raskol/classes/integrity/artifact/RuntimeArtifactTamperingIntegrationTest.java",
        "src/test/java/dev/raskol/classes/integrity/runtime/RuntimeIntegrityMonitorConcurrencyTest.java",
        "src/test/java/dev/raskol/classes/integrity/verification/StartupIntegrityVerifierKeyRotationSecurityTest.java",
    ]
    for item in required:
        require_file(item)

    signature = read("src/main/java/dev/raskol/classes/integrity/crypto/Ed25519SignatureService.java")
    registry = read("src/main/java/dev/raskol/classes/integrity/crypto/TrustedPublicKeyRegistry.java")
    archive = read("src/main/java/dev/raskol/classes/integrity/artifact/JarArchiveIntegrityVerifier.java")
    parser = read("src/main/java/dev/raskol/classes/integrity/manifest/IntegrityManifestParser.java")
    startup = read("src/main/java/dev/raskol/classes/integrity/verification/StartupIntegrityVerifier.java")
    monitor = read("src/main/java/dev/raskol/classes/integrity/runtime/RuntimeIntegrityMonitor.java")
    policy = read("src/main/java/dev/raskol/classes/integrity/policy/RuntimeIntegrityEnforcementPolicy.java")
    tamper_test = read("src/test/java/dev/raskol/classes/integrity/artifact/RuntimeArtifactTamperingIntegrationTest.java")

    record("CRYPTO-ED25519", "PASS" if contains(signature, '"Ed25519"') else "FAIL",
           "Ed25519 algorithm identifier is present." if contains(signature, '"Ed25519"') else "Ed25519 algorithm identifier is missing.")
    record("CRYPTO-SIGNATURE-LENGTH", "PASS" if contains(signature, "SIGNATURE_LENGTH = 64") else "FAIL",
           "Ed25519 signature length is pinned to 64 bytes." if contains(signature, "SIGNATURE_LENGTH = 64") else "Expected 64-byte signature length declaration was not found.")
    record("CRYPTO-VERIFY-FAIL-CLOSED", "PASS" if contains(signature, "return false;") and contains(signature, "verifier.verify(signature)") else "FAIL",
           "Verification has a false-return failure path." if contains(signature, "return false;") and contains(signature, "verifier.verify(signature)") else "Could not confirm a fail-closed verification path.")

    key_block = ""
    if registry:
        match = re.search(r"PINNED_KEYS\s*=\s*List\.of\((.*?)\);", registry, re.S)
        key_block = match.group(1) if match else ""
    active_count = len(re.findall(r"TrustedKeyStatus\.ACTIVE", key_block))
    if active_count == 1:
        record("KEYS-ACTIVE-COUNT", "PASS", "Pinned registry declares exactly one ACTIVE key.")
    else:
        record("KEYS-ACTIVE-COUNT", "FAIL", f"Pinned registry declares {active_count} ACTIVE keys; expected exactly one.")

    record("KEYS-CONSTANT-TIME-COMPARE", "PASS" if contains(registry, "MessageDigest.isEqual") else "WARN",
           "Registry uses constant-time byte comparison." if contains(registry, "MessageDigest.isEqual") else "No constant-time key comparison marker found.")

    record("ARCHIVE-CONSTANT-TIME-HASH", "PASS" if contains(archive, "MessageDigest.isEqual") else "FAIL",
           "Archive hashes are compared with MessageDigest.isEqual." if contains(archive, "MessageDigest.isEqual") else "Constant-time hash comparison was not found.")
    bounded = archive is not None and all(x in archive for x in ("maxEntryBytes", "maxTotalBytes", "maxArchiveEntries"))
    record("ARCHIVE-RESOURCE-LIMITS", "PASS" if bounded else "FAIL",
           "Entry, total decompressed-size, and entry-count limits are present." if bounded else "One or more archive resource limits are missing.")

    canonical_markers = parser is not None and any(x in parser.lower() for x in ("canonical", "line.separator", "line ending"))
    record("MANIFEST-CANONICAL-PARSER", "PASS" if canonical_markers else "WARN",
           "Parser contains canonical-format validation markers." if canonical_markers else "Canonical manifest validation needs manual review.")
    record("STARTUP-SIGNATURE-CHECK", "PASS" if startup is not None and ("signature" in startup.lower() and "manifest" in startup.lower()) else "FAIL",
           "Startup verifier references signed-manifest verification." if startup is not None and ("signature" in startup.lower() and "manifest" in startup.lower()) else "Could not confirm signed-manifest verification in startup verifier.")

    record("RUNTIME-MONITOR", "PASS" if monitor is not None and "verify" in monitor.lower() else "FAIL",
           "Runtime integrity monitor exposes verification logic." if monitor is not None and "verify" in monitor.lower() else "Runtime verification marker was not found.")
    record("RUNTIME-ENFORCEMENT", "PASS" if policy is not None and ("disable" in policy.lower() or "shutdown" in policy.lower() or "fatal" in policy.lower()) else "WARN",
           "Runtime policy contains an enforcement decision." if policy is not None and ("disable" in policy.lower() or "shutdown" in policy.lower() or "fatal" in policy.lower()) else "Could not identify a fatal enforcement decision.")
    tamper_markers = tamper_test is not None and all(x in tamper_test for x in ("PROTECTED_FILE_HASH_MISMATCH", "RuntimeIntegrityMonitor", "AtomicBoolean"))
    record("RUNTIME-TAMPERING-TEST", "PASS" if tamper_markers else "FAIL",
           "Runtime tampering integration test checks hash mismatch and enforcement callback." if tamper_markers else "Runtime tampering integration test is missing expected assertions.")

    private_key_pattern = re.compile(
        b"-----BEGIN " + b"(?:RSA |EC |OPENSSH )?PRIVATE KEY-----|" + b"-----BEGIN " + b"PRIVATE KEY-----"
    )
    scanned = 0
    leaked_paths: list[str] = []
    for folder in (ROOT / "src/main", ROOT / "tools/rips"):
        if not folder.exists():
            continue
        for path in folder.rglob("*"):
            if path.is_file() and path.suffix.lower() in {".java", ".py", ".properties", ".yml", ".yaml", ".txt"}:
                scanned += 1
                try:
                    if private_key_pattern.search(path.read_bytes()):
                        leaked_paths.append(str(path.relative_to(ROOT)))
                except OSError:
                    record("SECRET-SCAN-READ", "WARN", f"Could not read {path.relative_to(ROOT)}.")
    record("SECRET-PRIVATE-KEY-PEM-SCAN", "FAIL" if leaked_paths else "PASS",
           "Private-key PEM marker detected in: " + ", ".join(leaked_paths) if leaked_paths else f"No private-key PEM blocks found in {scanned} source/tool files.")

    warnings = sum(item["status"] == "WARN" for item in RESULTS)
    failures = sum(item["status"] == "FAIL" for item in RESULTS)
    passed = sum(item["status"] == "PASS" for item in RESULTS)
    report = {"auditor": "RIPS Security Auditor v1", "passed": passed, "warnings": warnings,
              "failures": failures, "checks": RESULTS}
    output = ROOT / "target/rips-security-audit.json"
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")

    summary = Path(__import__("os").environ["GITHUB_STEP_SUMMARY"]) if "GITHUB_STEP_SUMMARY" in __import__("os").environ else None
    if summary:
        with summary.open("a", encoding="utf-8") as stream:
            stream.write("## RIPS Security Auditor v1\n\n")
            stream.write(f"- PASS: **{passed}**\n- WARN: **{warnings}**\n- FAIL: **{failures}**\n\n")
            stream.write("| Check | Status | Detail |\n|---|---|---|\n")
            for item in RESULTS:
                detail = item["detail"].replace("|", "\\|")
                stream.write(f"| {item['id']} | {item['status']} | {detail} |\n")
            stream.write("\n> Static guardrail only; this report is not a proof of security and does not replace adversarial testing or runtime validation.\n")

    print(f"Summary: PASS={passed} WARN={warnings} FAIL={failures}")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
