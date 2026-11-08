// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.integrity.artifact;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.raskol.classes.integrity.build.BuildIdentity;
import dev.raskol.classes.integrity.build.BuildType;
import dev.raskol.classes.integrity.crypto.Ed25519SignatureService;
import dev.raskol.classes.integrity.manifest.IntegrityManifest;
import dev.raskol.classes.integrity.manifest.IntegrityManifestParser;
import dev.raskol.classes.integrity.manifest.ManifestFileEntry;
import dev.raskol.classes.integrity.manifest.ManifestSignatureService;
import dev.raskol.classes.integrity.manifest.Sha256HashService;
import dev.raskol.classes.integrity.manifest.SignedManifest;
import dev.raskol.classes.integrity.policy.RuntimeIntegrityEnforcementPolicy;
import dev.raskol.classes.integrity.runtime.RuntimeIntegrityMonitor;
import dev.raskol.classes.integrity.runtime.RuntimeIntegritySnapshot;
import dev.raskol.classes.integrity.runtime.RuntimeIntegrityState;
import dev.raskol.classes.integrity.verification.IntegrityStatus;
import dev.raskol.classes.integrity.verification.IntegrityVerificationResult;
import dev.raskol.classes.integrity.verification.StartupIntegrityVerifier;

/**
 * Exercises the real runtime monitor against a signed temporary JAR.
 * All mutation is confined to JUnit's temporary directory.
 */
class RuntimeArtifactTamperingIntegrationTest {

    private static final String PROTECTED_PATH = "plugin.yml";
    private static final String MANIFEST_PATH = StartupIntegrityVerifier.MANIFEST_RESOURCE;
    private static final String SIGNATURE_PATH = StartupIntegrityVerifier.SIGNATURE_RESOURCE;

    @TempDir
    Path temporaryDirectory;

    @Test
    void shouldDetectOnDiskArtifactTamperingDuringRuntimeCheck() throws Exception {
        KeyPair keyPair = Ed25519SignatureService.generateKeyPair();
        BuildIdentity identity = new BuildIdentity(
                "RaskolClasses",
                "1.15.8",
                "RIPS-RUNTIME-TEST",
                "0123456789abcdef0123456789abcdef01234567",
                Instant.parse("2026-01-01T00:00:00Z"),
                "https://github.com/hayferdahmer/RaskolClasses",
                BuildType.OFFICIAL);

        byte[] original = bytes("name: RaskolClasses\n");
        byte[] tampered = bytes("name: RaskolEvilxxx\n");
        assertEquals(original.length, tampered.length);

        IntegrityManifest manifest = new IntegrityManifest(
                identity,
                List.of(new ManifestFileEntry(
                        PROTECTED_PATH, original.length, Sha256HashService.hash(original))));
        SignedManifest signedManifest = ManifestSignatureService.sign(manifest, keyPair.getPrivate());

        Path jar = temporaryDirectory.resolve("runtime-test.jar");
        writeJar(jar, original, signedManifest);

        RuntimeIntegrityEnforcementPolicy policy = new RuntimeIntegrityEnforcementPolicy();
        AtomicBoolean shutdownRequested = new AtomicBoolean();

        try (RuntimeIntegrityMonitor monitor = new RuntimeIntegrityMonitor(
                () -> verifyArtifact(jar, identity, keyPair),
                Duration.ofHours(1),
                java.util.concurrent.Executors.newSingleThreadScheduledExecutor(),
                new dev.raskol.classes.integrity.runtime.RuntimeIntegrityStateManager(),
                null,
                snapshot -> {
                    if (policy.shouldDisable(snapshot)) {
                        shutdownRequested.set(true);
                    }
                })) {

            RuntimeIntegritySnapshot initial = monitor.checkNow().get(3, TimeUnit.SECONDS);
            assertEquals(RuntimeIntegrityState.VERIFIED, initial.state());
            assertEquals(0, initial.consecutiveFailures());
            assertTrue(!shutdownRequested.get());

            // Rewrite only the isolated test artifact; the signed manifest remains unchanged.
            writeJar(jar, tampered, signedManifest);

            RuntimeIntegritySnapshot attacked = monitor.checkNow().get(3, TimeUnit.SECONDS);
            assertEquals(RuntimeIntegrityState.FAILED, attacked.state());
            assertEquals(IntegrityStatus.PROTECTED_FILE_HASH_MISMATCH, attacked.lastResult().status());
            assertTrue(policy.shouldDisable(attacked), "Confirmed hash mismatch must be fatal immediately.");
            assertTrue(shutdownRequested.get(), "Runtime failure callback must request enforcement.");
        }
    }

    private static IntegrityVerificationResult verifyArtifact(
            Path jar, BuildIdentity identity, KeyPair keyPair) {
        try {
            JarFileIntegrityResourceProvider resources = new JarFileIntegrityResourceProvider(jar);
            IntegrityVerificationResult startup = new StartupIntegrityVerifier(
                    resources, identity, keyPair.getPublic()).verify();
            if (!startup.isVerified()) {
                return startup;
            }

            byte[] manifestBytes;
            try (var input = resources.openResource(MANIFEST_PATH)) {
                manifestBytes = input.readAllBytes();
            }

            IntegrityManifest parsed = IntegrityManifestParser.parse(manifestBytes);
            JarArchiveIntegrityVerificationResult archive = new JarArchiveIntegrityVerifier(jar).verify(parsed);
            if (!archive.isVerified()) {
                return new IntegrityVerificationResult(
                        IntegrityStatus.ARCHIVE_INTEGRITY_FAILURE,
                        "Runtime archive verification failed: " + archive.status(),
                        archive.checkedFiles(),
                        archive.failedFiles());
            }

            return new IntegrityVerificationResult(
                    IntegrityStatus.VERIFIED,
                    "Runtime test artifact verified.",
                    archive.checkedFiles(),
                    List.of());
        } catch (Exception exception) {
            return new IntegrityVerificationResult(
                    IntegrityStatus.ARTIFACT_UNAVAILABLE,
                    "Runtime test verification could not read the artifact.",
                    0,
                    List.of());
        }
    }

    private static void writeJar(Path jar, byte[] protectedFile, SignedManifest signedManifest)
            throws Exception {
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
            put(output, PROTECTED_PATH, protectedFile);
            put(output, MANIFEST_PATH, signedManifest.manifestBytes());
            put(output, SIGNATURE_PATH,
                    ManifestSignatureService.encodeSignature(signedManifest.signature())
                            .getBytes(StandardCharsets.US_ASCII));
        }
    }

    private static void put(JarOutputStream output, String name, byte[] content) throws Exception {
        output.putNextEntry(new JarEntry(name));
        output.write(content);
        output.closeEntry();
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
