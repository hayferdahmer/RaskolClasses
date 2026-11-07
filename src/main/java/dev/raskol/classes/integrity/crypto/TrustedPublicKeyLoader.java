// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.integrity.crypto;

import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.util.List;
import java.util.Objects;

/**
 * Provides trusted Ed25519 public keys from the source-pinned registry.
 *
 * <p>No external resource is used as a trust anchor.</p>
 *
 * <p>The legacy single-key method is retained for compatibility.
 * New verification code should use {@link #loadAll()}.</p>
 */
public final class TrustedPublicKeyLoader {

    private TrustedPublicKeyLoader() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Returns the primary pinned trusted public key.
     *
     * @param anchor retained for API compatibility; must not be null
     * @return primary trusted public key
     * @throws GeneralSecurityException if the registry cannot be initialized
     */
    public static PublicKey load(Class<?> anchor)
            throws GeneralSecurityException {
        Objects.requireNonNull(anchor, "anchor cannot be null");

        List<PublicKey> keys = loadAll();

        if (keys.isEmpty()) {
            throw new GeneralSecurityException(
                    "RIPS trusted public key registry is empty"
            );
        }

        return keys.get(0);
    }

    /**
     * Returns all source-pinned trusted public keys.
     *
     * @return immutable list of trusted public keys
     * @throws GeneralSecurityException if the registry cannot be initialized
     */
    public static List<PublicKey> loadAll()
            throws GeneralSecurityException {
        try {
            List<PublicKey> keys = TrustedPublicKeyRegistry.trustedKeys();

            if (keys.isEmpty()) {
                throw new GeneralSecurityException(
                        "RIPS trusted public key registry is empty"
                );
            }

            return keys;
        } catch (ExceptionInInitializerError error) {
            throw new GeneralSecurityException(
                    "RIPS trusted public key registry is invalid",
                    error
            );
        }
    }

    /**
     * Decodes a canonical Base64 X.509 Ed25519 public key.
     *
     * <p>This method parses a key but does not make it trusted.
     * Trust is established exclusively by the pinned registry.</p>
     *
     * @param encodedKey Base64-encoded X.509 public key
     * @return decoded public key
     * @throws GeneralSecurityException if the key is invalid
     */
    public static PublicKey decode(String encodedKey)
            throws GeneralSecurityException {
        Objects.requireNonNull(
                encodedKey,
                "encodedKey cannot be null"
        );

        String normalized = encodedKey.strip();

        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(
                    "Public key cannot be blank"
            );
        }

        if (!normalized.equals(encodedKey.strip())) {
            throw new IllegalArgumentException(
                    "Invalid public key formatting"
            );
        }

        return Ed25519SignatureService.decodePublicKey(normalized);
    }
}
