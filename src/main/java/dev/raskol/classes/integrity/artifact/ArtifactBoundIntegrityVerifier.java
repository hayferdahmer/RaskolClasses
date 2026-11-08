// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.integrity.artifact;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.security.PublicKey;
import java.util.List;
import java.util.Objects;

import dev.raskol.classes.integrity.build.BuildIdentity;
import dev.raskol.classes.integrity.build.BuildIdentityLoader;
import dev.raskol.classes.integrity.build.DevelopmentMode;
import dev.raskol.classes.integrity.crypto.TrustedPublicKeyRegistry;
import dev.raskol.classes.integrity.manifest.IntegrityManifest;
import dev.raskol.classes.integrity.manifest.IntegrityManifestParser;
import dev.raskol.classes.integrity.verification.IntegrityStatus;
import dev.raskol.classes.integrity.verification.IntegrityVerificationResult;
import dev.raskol.classes.integrity.verification.StartupIntegrityVerifier;

/**
 * Coordinates artifact-bound integrity verification.
 *
 * <p>Official builds are verified directly from their physical JAR artifact.
 * Development builds are accepted only when explicitly authorized through
 * the JVM development-mode property.</p>
 *
 * <p>The verification sequence is:</p>
 * <ol>
 *     <li>Load build identity.</li>
 *     <li>Check explicit development-mode authorization.</li>
 *     <li>Locate the physical plugin artifact.</li>
 *     <li>Load the source-pinned trusted Ed25519 public keys.</li>
 *     <li>Verify the signed manifest and protected files.</li>
 *     <li>Parse the verified manifest.</li>
 *     <li>Verify the complete JAR archive.</li>
 * </ol>
 *
 * <p>This class does not interact with the Bukkit or Paper API.</p>
 */
public final class ArtifactBoundIntegrityVerifier {

    private static final int MAX_MANIFEST_SIZE = 1024 * 1024;

    private final Class<?> anchor;

    public ArtifactBoundIntegrityVerifier(Class<?> anchor) {
        this.anchor = Objects.requireNonNull(anchor, "anchor");
    }

    /**
     * Executes artifact-bound integrity verification.
     *
     * @return combined integrity verification result
     */
    public IntegrityVerificationResult verify() {
        final BuildIdentity buildIdentity;

        try {
            buildIdentity = BuildIdentityLoader.load();
        } catch (RuntimeException exception) {
            return failure(IntegrityStatus.INVALID_MANIFEST, "Unable to load or validate build identity.");
        }

        if (buildIdentity.isDevelopment()) {
            if (!DevelopmentMode.isEnabled()) {
                return failure(
                        IntegrityStatus.DEVELOPMENT_MODE_REQUIRED,
                        "Development build rejected. " + "Explicit JVM development mode is required.");
            }

            return new IntegrityVerificationResult(
                    IntegrityStatus.DEVELOPMENT_BUILD,
                    "Development build explicitly authorized by the server operator.",
                    0,
                    List.of());
        }

        final Path artifactPath;

        try {
            artifactPath = ArtifactLocator.locate(anchor);
        } catch (IOException | SecurityException exception) {
            return failure(
                    IntegrityStatus.ARTIFACT_UNAVAILABLE, "Unable to locate or access the physical plugin artifact.");
        }

        final JarFileIntegrityResourceProvider resourceProvider;

        try {
            resourceProvider = new JarFileIntegrityResourceProvider(artifactPath);
        } catch (IOException | SecurityException exception) {
            return failure(IntegrityStatus.ARTIFACT_UNAVAILABLE, "Unable to open the physical plugin artifact.");
        }

        final List<PublicKey> trustedPublicKeys;

        try {
            trustedPublicKeys = TrustedPublicKeyRegistry.trustedKeys();

            if (trustedPublicKeys.isEmpty()) {
                return failure(IntegrityStatus.TRUSTED_KEY_UNAVAILABLE, "RIPS trusted public key registry is empty.");
            }
        } catch (ExceptionInInitializerError exception) {
            return failure(IntegrityStatus.TRUSTED_KEY_UNAVAILABLE, "RIPS trusted public key registry is invalid.");
        }

        StartupIntegrityVerifier startupVerifier =
                new StartupIntegrityVerifier(resourceProvider, buildIdentity, null, trustedPublicKeys);

        IntegrityVerificationResult startupResult = startupVerifier.verify();

        if (!startupResult.isVerified()) {
            return startupResult;
        }

        final IntegrityManifest manifest;

        try {
            manifest = loadVerifiedManifest(resourceProvider);
        } catch (IOException exception) {
            return failure(IntegrityStatus.RESOURCE_READ_ERROR, "Unable to read the verified integrity manifest.");
        } catch (IllegalArgumentException exception) {
            return failure(IntegrityStatus.INVALID_MANIFEST, "The integrity manifest is not canonical or valid.");
        }

        JarArchiveIntegrityVerificationResult archiveResult =
                new JarArchiveIntegrityVerifier(artifactPath).verify(manifest);

        if (!archiveResult.isVerified()) {
            IntegrityStatus failureStatus = switch (archiveResult.status()) {
                case ARCHIVE_READ_ERROR, ENTRY_READ_ERROR -> IntegrityStatus.ARTIFACT_UNAVAILABLE;

                default -> IntegrityStatus.ARCHIVE_INTEGRITY_FAILURE;
            };

            return new IntegrityVerificationResult(
                    failureStatus,
                    "Full archive verification failed: " + archiveResult.status() + ". " + archiveResult.message(),
                    archiveResult.checkedFiles(),
                    archiveResult.failedFiles());
        }

        return new IntegrityVerificationResult(
                IntegrityStatus.VERIFIED,
                "Artifact signature, build identity, protected files and complete JAR archive were verified successfully.",
                archiveResult.checkedFiles(),
                List.of());
    }

    private IntegrityManifest loadVerifiedManifest(JarFileIntegrityResourceProvider resourceProvider)
            throws IOException {

        try (InputStream input = resourceProvider.openResource(StartupIntegrityVerifier.MANIFEST_RESOURCE)) {
            if (input == null) {
                throw new IOException("Integrity manifest disappeared from the artifact.");
            }

            byte[] manifestBytes = input.readNBytes(MAX_MANIFEST_SIZE + 1);

            if (manifestBytes.length > MAX_MANIFEST_SIZE) {
                throw new IOException("Integrity manifest exceeds the maximum allowed size.");
            }

            return IntegrityManifestParser.parse(manifestBytes);
        }
    }

    private IntegrityVerificationResult failure(IntegrityStatus status, String message) {
        return new IntegrityVerificationResult(status, message, 0, List.of());
    }
}
