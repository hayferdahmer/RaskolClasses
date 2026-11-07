// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.integrity.manifest;

import dev.raskol.classes.integrity.crypto.Ed25519SignatureService;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;
import java.util.Objects;

/**
 * Signs and verifies canonical RaskolClasses integrity manifests.
 *
 * <p>The signature is calculated over the exact canonical manifest bytes.
 * Verification never parses and reserializes the manifest.</p>
 */
public final class ManifestSignatureService {

    private static final int SIGNATURE_LENGTH = 64;

    private ManifestSignatureService() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Creates a signed manifest using an Ed25519 private key.
     *
     * @param manifest integrity manifest
     * @param privateKey trusted release signing key
     * @return immutable signed manifest
     * @throws GeneralSecurityException if signing fails
     */
    public static SignedManifest sign(
            IntegrityManifest manifest,
            PrivateKey privateKey
    ) throws GeneralSecurityException {
        Objects.requireNonNull(manifest, "manifest cannot be null");
        Objects.requireNonNull(privateKey, "privateKey cannot be null");

        byte[] canonicalBytes = manifest.toCanonicalBytes();

        byte[] signature = Ed25519SignatureService.sign(
                canonicalBytes,
                privateKey
        );

        return new SignedManifest(canonicalBytes, signature);
    }

    /**
     * Verifies a signed manifest using a trusted Ed25519 public key.
     *
     * @param signedManifest signed manifest
     * @param publicKey trusted public key
     * @return true if the signature is valid
     * @throws GeneralSecurityException if verification cannot be performed
     */
    public static boolean verify(
            SignedManifest signedManifest,
            PublicKey publicKey
    ) throws GeneralSecurityException {
        Objects.requireNonNull(
                signedManifest,
                "signedManifest cannot be null"
        );
        Objects.requireNonNull(
                publicKey,
                "publicKey cannot be null"
        );

        return Ed25519SignatureService.verify(
                signedManifest.manifestBytes(),
                signedManifest.signature(),
                publicKey
        );
    }

    /**
     * Verifies exact manifest bytes and a detached signature.
     *
     * <p>This overload is intended for verification after loading
     * the manifest and signature from separate files.</p>
     *
     * @param manifestBytes exact bytes read from the manifest file
     * @param signature detached Ed25519 signature
     * @param publicKey trusted public key
     * @return true if the signature is valid
     * @throws GeneralSecurityException if verification cannot be performed
     */
    public static boolean verify(
            byte[] manifestBytes,
            byte[] signature,
            PublicKey publicKey
    ) throws GeneralSecurityException {
        Objects.requireNonNull(
                manifestBytes,
                "manifestBytes cannot be null"
        );
        Objects.requireNonNull(signature, "signature cannot be null");
        Objects.requireNonNull(publicKey, "publicKey cannot be null");

        return Ed25519SignatureService.verify(
                manifestBytes,
                signature,
                publicKey
        );
    }

    /**
     * Encodes a detached signature as Base64 ASCII text.
     *
     * @param signature raw 64-byte Ed25519 signature
     * @return Base64 signature without line breaks
     */
    public static String encodeSignature(byte[] signature) {
        validateSignature(signature);

        return Base64.getEncoder().encodeToString(signature);
    }

    /**
     * Decodes a Base64 detached signature.
     *
     * @param encodedSignature Base64 signature text
     * @return decoded 64-byte signature
     */
    public static byte[] decodeSignature(String encodedSignature) {
        Objects.requireNonNull(
                encodedSignature,
                "encodedSignature cannot be null"
        );

        if (encodedSignature.isBlank()) {
            throw new IllegalArgumentException(
                    "Encoded signature cannot be blank"
            );
        }

        final byte[] decoded;

        try {
            decoded = Base64.getDecoder().decode(
                    encodedSignature.getBytes(StandardCharsets.US_ASCII)
            );
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "Invalid Base64 signature",
                    exception
            );
        }

        validateSignature(decoded);
        return decoded;
    }

    private static void validateSignature(byte[] signature) {
        Objects.requireNonNull(
                signature,
                "signature cannot be null"
        );

        if (signature.length != SIGNATURE_LENGTH) {
            throw new IllegalArgumentException(
                    "Ed25519 signature must contain exactly 64 bytes"
            );
        }
    }
}
