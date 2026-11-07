// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.integrity.verification;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.util.List;
import java.util.Objects;

import dev.raskol.classes.integrity.build.BuildIdentity;
import dev.raskol.classes.integrity.build.BuildIdentityLoader;
import dev.raskol.classes.integrity.build.DevelopmentMode;
import dev.raskol.classes.integrity.crypto.TrustedPublicKeyRegistry;
import dev.raskol.classes.integrity.manifest.IntegrityManifest;
import dev.raskol.classes.integrity.manifest.IntegrityManifestParser;
import dev.raskol.classes.integrity.manifest.ManifestFileEntry;
import dev.raskol.classes.integrity.manifest.ManifestSignatureService;

/**
 * Performs startup integrity verification for RaskolClasses.
 *
 * <p>The verifier checks the detached Ed25519 signature before
 * parsing the manifest, compares build identity and verifies
 * every file declared in the signed manifest.</p>
 *
 * <p>Trusted public keys are obtained exclusively from the
 * source-pinned TrustedPublicKeyRegistry.</p>
 *
 * <p>Development builds require explicit authorization through
 * the JVM development-mode property.</p>
 *
 * <p>This class does not start or stop the Minecraft server.
 * It only returns a verification result.</p>
 */
public final class StartupIntegrityVerifier {

    public static final String MANIFEST_RESOURCE = "/META-INF/raskol/integrity/manifest.rips";

    public static final String SIGNATURE_RESOURCE = "/META-INF/raskol/integrity/manifest.sig";

    private static final int MAX_MANIFEST_SIZE = 1024 * 1024;

    private static final int MAX_SIGNATURE_SIZE = 4096;

    private static final long MAX_PROTECTED_FILE_SIZE = 64L * 1024L * 1024L;

    private static final int BUFFER_SIZE = 8192;

    private final IntegrityResourceProvider resourceProvider;

    private final BuildIdentity expectedIdentity;

    private final List<PublicKey> injectedPublicKeys;

    /**
     * Creates a verifier using the plugin's own classpath resources
     * and automatically loads its build identity.
     *
     * @param anchor plugin class used to locate resources
     */
    public StartupIntegrityVerifier(Class<?> anchor) {
        this(new ClassLoaderIntegrityResourceProvider(anchor), BuildIdentityLoader.load(), null, null);
    }

    /**
     * Creates a verifier using the plugin's own classpath resources.
     *
     * @param anchor plugin class used to locate resources
     * @param expectedIdentity current build identity
     */
    public StartupIntegrityVerifier(Class<?> anchor, BuildIdentity expectedIdentity) {
        this(new ClassLoaderIntegrityResourceProvider(anchor), expectedIdentity, null, null);
    }

    /**
     * Creates a verifier with an injected public key.
     *
     * <p>This constructor is retained for isolated tests and
     * compatibility with existing integrations. Production
     * verification should use the pinned registry.</p>
     *
     * @param resourceProvider resource provider
     * @param expectedIdentity current build identity
     * @param trustedPublicKey trusted Ed25519 public key
     */
    public StartupIntegrityVerifier(
            IntegrityResourceProvider resourceProvider, BuildIdentity expectedIdentity, PublicKey trustedPublicKey) {
        this(
                resourceProvider,
                expectedIdentity,
                null,
                trustedPublicKey == null ? List.of() : List.of(trustedPublicKey));
    }

    /**
     * Creates a verifier with an injected list of trusted public keys.
     *
     * <p>Used by artifact-bound verification and isolated tests.
     * The supplied list is defensively copied.</p>
     *
     * @param resourceProvider resource provider
     * @param expectedIdentity current build identity
     * @param anchor optional class anchor, retained for integration compatibility
     * @param trustedPublicKeys trusted Ed25519 public keys; null uses the pinned registry
     */
    public StartupIntegrityVerifier(
            IntegrityResourceProvider resourceProvider,
            BuildIdentity expectedIdentity,
            Class<?> anchor,
            List<PublicKey> trustedPublicKeys) {
        this.resourceProvider = Objects.requireNonNull(resourceProvider, "resourceProvider cannot be null");

        this.expectedIdentity = Objects.requireNonNull(expectedIdentity, "expectedIdentity cannot be null");

        this.injectedPublicKeys = trustedPublicKeys == null ? null : List.copyOf(trustedPublicKeys);
    }

    /**
     * Executes the complete startup integrity verification.
     *
     * @return immutable verification result
     */
    public IntegrityVerificationResult verify() {

        if (expectedIdentity.isDevelopment()) {
            if (!DevelopmentMode.isEnabled()) {
                return result(
                        IntegrityStatus.DEVELOPMENT_MODE_REQUIRED,
                        "Development build rejected. " + "Explicit JVM development mode is required.",
                        0);
            }

            return result(
                    IntegrityStatus.DEVELOPMENT_BUILD,
                    "Development build explicitly authorized by the server operator.",
                    0);
        }

        final List<PublicKey> trustedPublicKeys;

        try {
            trustedPublicKeys = loadTrustedPublicKeys();
        } catch (GeneralSecurityException | ExceptionInInitializerError exception) {
            return result(
                    IntegrityStatus.TRUSTED_KEY_UNAVAILABLE, "Trusted public key registry is missing or invalid.", 0);
        }

        final byte[] manifestBytes;

        try {
            manifestBytes = readRequiredResource(MANIFEST_RESOURCE, MAX_MANIFEST_SIZE);
        } catch (ResourceTooLargeException exception) {
            return result(
                    IntegrityStatus.MANIFEST_TOO_LARGE, "Integrity manifest exceeds the maximum allowed size.", 0);
        } catch (IOException exception) {
            return result(IntegrityStatus.RESOURCE_READ_ERROR, "Unable to read integrity manifest.", 0);
        }

        if (manifestBytes == null) {
            return result(IntegrityStatus.MANIFEST_MISSING, "Integrity manifest is missing.", 0);
        }

        final byte[] signatureBytes;

        try {
            signatureBytes = readRequiredResource(SIGNATURE_RESOURCE, MAX_SIGNATURE_SIZE);
        } catch (ResourceTooLargeException exception) {
            return result(IntegrityStatus.INVALID_SIGNATURE, "Signature resource exceeds the maximum allowed size.", 0);
        } catch (IOException exception) {
            return result(IntegrityStatus.RESOURCE_READ_ERROR, "Unable to read detached signature.", 0);
        }

        if (signatureBytes == null) {
            return result(IntegrityStatus.SIGNATURE_MISSING, "Detached signature is missing.", 0);
        }

        final byte[] signature;

        try {
            signature = decodeSignatureResource(signatureBytes);
        } catch (IllegalArgumentException exception) {
            return result(IntegrityStatus.INVALID_SIGNATURE, "Detached signature has an invalid format.", 0);
        }

        final boolean signatureValid;

        try {
            signatureValid = verifyAgainstTrustedKeys(manifestBytes, signature, trustedPublicKeys);
        } catch (GeneralSecurityException exception) {
            return result(IntegrityStatus.CRYPTOGRAPHIC_ERROR, "Unable to perform Ed25519 signature verification.", 0);
        }

        if (!signatureValid) {
            return result(IntegrityStatus.INVALID_SIGNATURE, "Integrity manifest signature verification failed.", 0);
        }

        final IntegrityManifest manifest;

        try {
            manifest = IntegrityManifestParser.parse(manifestBytes);
        } catch (IllegalArgumentException exception) {
            return result(IntegrityStatus.INVALID_MANIFEST, "Signed integrity manifest has an invalid format.", 0);
        }

        if (!expectedIdentity.equals(manifest.buildIdentity())) {
            return result(
                    IntegrityStatus.BUILD_IDENTITY_MISMATCH,
                    "Manifest build identity does not match the current build.",
                    0);
        }

        if (manifest.files().isEmpty()) {
            return result(
                    IntegrityStatus.INVALID_MANIFEST, "Official integrity manifest contains no protected files.", 0);
        }

        int checkedFiles = 0;

        for (ManifestFileEntry entry : manifest.files()) {
            FileCheck fileCheck = verifyFile(entry);

            if (fileCheck.status() != null) {
                return new IntegrityVerificationResult(
                        fileCheck.status(), fileCheck.message(), checkedFiles, List.of(entry.path()));
            }

            checkedFiles++;
        }

        return result(
                IntegrityStatus.VERIFIED,
                "Official build integrity verification completed successfully.",
                checkedFiles);
    }

    private List<PublicKey> loadTrustedPublicKeys() throws GeneralSecurityException {

        if (injectedPublicKeys != null) {
            if (injectedPublicKeys.isEmpty()) {
                throw new GeneralSecurityException("Injected trusted public key list is empty");
            }

            return injectedPublicKeys;
        }

        List<PublicKey> trustedKeys = TrustedPublicKeyRegistry.trustedKeys();

        if (trustedKeys.isEmpty()) {
            throw new GeneralSecurityException("RIPS trusted public key registry is empty");
        }

        return trustedKeys;
    }

    private boolean verifyAgainstTrustedKeys(byte[] manifestBytes, byte[] signature, List<PublicKey> trustedKeys)
            throws GeneralSecurityException {

        GeneralSecurityException lastException = null;

        for (PublicKey trustedKey : trustedKeys) {
            try {
                if (ManifestSignatureService.verify(manifestBytes, signature, trustedKey)) {
                    return true;
                }
            } catch (GeneralSecurityException exception) {
                lastException = exception;
            }
        }

        if (lastException != null) {
            throw lastException;
        }

        return false;
    }

    private byte[] readRequiredResource(String resourcePath, int maximumSize) throws IOException {

        try (InputStream input = resourceProvider.openResource(resourcePath)) {
            if (input == null) {
                return null;
            }

            byte[] bytes = input.readNBytes(maximumSize + 1);

            if (bytes.length > maximumSize) {
                throw new ResourceTooLargeException();
            }

            return bytes;
        }
    }

    private static byte[] decodeSignatureResource(byte[] bytes) {

        for (byte value : bytes) {
            if (value < 0) {
                throw new IllegalArgumentException("Signature must contain ASCII characters only");
            }
        }

        String encoded = new String(bytes, StandardCharsets.US_ASCII).strip();

        return ManifestSignatureService.decodeSignature(encoded);
    }

    private FileCheck verifyFile(ManifestFileEntry entry) {

        String resourcePath = "/" + entry.path();

        try (InputStream input = resourceProvider.openResource(resourcePath)) {

            if (input == null) {
                return FileCheck.failed(IntegrityStatus.PROTECTED_FILE_MISSING, "Protected file is missing.");
            }

            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            byte[] buffer = new byte[BUFFER_SIZE];
            long totalBytes = 0;

            int bytesRead;

            while ((bytesRead = input.read(buffer)) != -1) {
                if (bytesRead == 0) {
                    continue;
                }

                totalBytes += bytesRead;

                if (totalBytes > MAX_PROTECTED_FILE_SIZE) {
                    return FileCheck.failed(
                            IntegrityStatus.PROTECTED_FILE_TOO_LARGE,
                            "Protected file exceeds the maximum allowed size.");
                }

                digest.update(buffer, 0, bytesRead);
            }

            if (totalBytes != entry.size()) {
                return FileCheck.failed(
                        IntegrityStatus.PROTECTED_FILE_SIZE_MISMATCH,
                        "Protected file size does not match the manifest.");
            }

            String actualHash = java.util.HexFormat.of().formatHex(digest.digest());

            if (!actualHash.equals(entry.sha256())) {
                return FileCheck.failed(
                        IntegrityStatus.PROTECTED_FILE_HASH_MISMATCH,
                        "Protected file SHA-256 does not match the manifest.");
            }

            return FileCheck.success();

        } catch (IOException exception) {
            return FileCheck.failed(IntegrityStatus.PROTECTED_FILE_READ_ERROR, "Unable to read protected file.");
        } catch (NoSuchAlgorithmException exception) {
            return FileCheck.failed(IntegrityStatus.CRYPTOGRAPHIC_ERROR, "SHA-256 is unavailable.");
        }
    }

    private static IntegrityVerificationResult result(IntegrityStatus status, String message, int checkedFiles) {
        return new IntegrityVerificationResult(status, message, checkedFiles, List.of());
    }

    private record FileCheck(IntegrityStatus status, String message) {

        private static FileCheck success() {
            return new FileCheck(null, null);
        }

        private static FileCheck failed(IntegrityStatus status, String message) {
            return new FileCheck(status, message);
        }
    }

    private static final class ResourceTooLargeException extends IOException {

        private static final long serialVersionUID = 1L;
    }
}
