// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.integrity.crypto;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * Registry of public keys trusted by RIPS.
 *
 * <p>Trusted keys are pinned directly in the plugin source code.
 * External resources must never be used as a trust anchor.</p>
 *
 * <p>Multiple keys are supported to allow safe key rotation.
 * Old and new keys may coexist during a migration period.</p>
 *
 * <p>Only public keys may be stored here. Private keys must remain
 * outside the repository and be available only to the trusted
 * release-signing process.</p>
 */
public final class TrustedPublicKeyRegistry {

    /*
     * Key rotation:
     *
     * 1. Add the new public key to this list.
     * 2. Keep the old public key during the migration period.
     * 3. Sign new releases with the new private key.
     * 4. Remove the old public key only after old releases
     *    are no longer required to be supported.
     *
     * Never add private keys to this list.
     */
    private static final List<String> PINNED_PUBLIC_KEYS = List.of(
            "MCowBQYDK2VwAyEAESiWqMfemhrSC9x1q+sRz7Lo3cd1KIQSvWGBA/4In6Q="
    );

    private static final List<PublicKey> TRUSTED_KEYS =
            loadPinnedKeys();

    private TrustedPublicKeyRegistry() {
        throw new UnsupportedOperationException(
                "TrustedPublicKeyRegistry is a utility class"
        );
    }

    /**
     * Returns an immutable list of trusted public keys.
     *
     * @return trusted public keys
     */
    public static List<PublicKey> trustedKeys() {
        return TRUSTED_KEYS;
    }

    /**
     * Checks whether the supplied public key is present in the
     * pinned trust registry.
     *
     * @param candidate public key to check
     * @return true if the key is trusted
     */
    public static boolean isTrusted(PublicKey candidate) {
        if (candidate == null || candidate.getEncoded() == null) {
            return false;
        }

        byte[] candidateBytes = candidate.getEncoded();

        for (PublicKey trustedKey : TRUSTED_KEYS) {
            byte[] trustedBytes = trustedKey.getEncoded();

            if (MessageDigest.isEqual(candidateBytes, trustedBytes)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Returns SHA-256 fingerprints of all pinned public keys.
     *
     * @return immutable list of lowercase hexadecimal fingerprints
     */
    public static List<String> fingerprints() {
        List<String> fingerprints =
                new ArrayList<>(TRUSTED_KEYS.size());

        for (PublicKey key : TRUSTED_KEYS) {
            fingerprints.add(fingerprint(key));
        }

        return List.copyOf(fingerprints);
    }

    /**
     * Calculates the SHA-256 fingerprint of an X.509 encoded
     * public key.
     *
     * @param publicKey public key
     * @return lowercase hexadecimal SHA-256 fingerprint
     */
    public static String fingerprint(PublicKey publicKey) {
        Objects.requireNonNull(publicKey, "publicKey");

        byte[] encoded = Objects.requireNonNull(
                publicKey.getEncoded(),
                "Public key encoding must not be null"
        );

        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(encoded);

            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 is not available in this Java runtime",
                    exception
            );
        }
    }

    /**
     * Decodes and validates a list of pinned Ed25519 public keys.
     *
     * <p>This method is package-private to allow isolated registry
     * tests without changing the production trust configuration.</p>
     *
     * @param encodedKeys Base64-encoded X.509 public keys
     * @return immutable list of decoded public keys
     * @throws GeneralSecurityException if the list or any key is invalid
     */
    static List<PublicKey> decodePinnedKeys(
            List<String> encodedKeys
    ) throws GeneralSecurityException {

        Objects.requireNonNull(encodedKeys, "encodedKeys");

        if (encodedKeys.isEmpty()) {
            throw new GeneralSecurityException(
                    "RIPS trusted public key registry is empty"
            );
        }

        List<PublicKey> keys =
                new ArrayList<>(encodedKeys.size());

        for (String encodedKey : encodedKeys) {
            if (encodedKey == null || encodedKey.isBlank()) {
                throw new GeneralSecurityException(
                        "Trusted public key cannot be null or blank"
                );
            }

            final byte[] decoded;

            try {
                decoded = Base64.getDecoder().decode(encodedKey);
            } catch (IllegalArgumentException exception) {
                throw new GeneralSecurityException(
                        "Invalid Base64 trusted public key",
                        exception
                );
            }

            if (!Base64.getEncoder()
                    .encodeToString(decoded)
                    .equals(encodedKey)) {
                throw new GeneralSecurityException(
                        "Non-canonical Base64 public key"
                );
            }

            PublicKey publicKey =
                    Ed25519SignatureService.decodePublicKey(encodedKey);

            if (publicKey.getEncoded() == null) {
                throw new GeneralSecurityException(
                        "Decoded public key has no encoded representation"
                );
            }

            for (PublicKey existing : keys) {
                if (MessageDigest.isEqual(
                        existing.getEncoded(),
                        publicKey.getEncoded()
                )) {
                    throw new GeneralSecurityException(
                            "Duplicate trusted public key"
                    );
                }
            }

            keys.add(publicKey);
        }

        return List.copyOf(keys);
    }

    private static List<PublicKey> loadPinnedKeys() {
        try {
            return decodePinnedKeys(PINNED_PUBLIC_KEYS);
        } catch (GeneralSecurityException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }
}
