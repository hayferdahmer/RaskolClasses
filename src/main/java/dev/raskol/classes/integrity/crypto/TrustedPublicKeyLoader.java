// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.integrity.crypto;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.util.Base64;
import java.util.Objects;

/**
 * Loads the trusted Ed25519 public key embedded in the plugin artifact.
 *
 * <p>The corresponding private signing key must never be embedded
 * in the plugin or stored in the source repository.</p>
 */
public final class TrustedPublicKeyLoader {

    public static final String RESOURCE_PATH =
            "/META-INF/raskol/integrity/public-key.txt";

    private static final int MAX_RESOURCE_SIZE = 4096;

    private TrustedPublicKeyLoader() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Loads the trusted public key from the specified class's resources.
     *
     * @param anchor class used to locate the resource
     * @return decoded Ed25519 public key
     * @throws IOException if the resource is missing or invalid
     * @throws GeneralSecurityException if the key cannot be decoded
     */
    public static PublicKey load(Class<?> anchor)
            throws IOException, GeneralSecurityException {
        Objects.requireNonNull(anchor, "anchor cannot be null");

        try (InputStream input = anchor.getResourceAsStream(RESOURCE_PATH)) {
            if (input == null) {
                throw new IOException(
                        "Trusted public key resource is missing: "
                                + RESOURCE_PATH
                );
            }

            byte[] bytes = input.readNBytes(MAX_RESOURCE_SIZE + 1);

            if (bytes.length > MAX_RESOURCE_SIZE) {
                throw new IOException(
                        "Trusted public key resource is too large"
                );
            }

            for (byte value : bytes) {
                if (value < 0 || value > 127) {
                    throw new IOException(
                            "Trusted public key resource must contain ASCII text"
                    );
                }
            }

            String encodedKey = new String(
                    bytes,
                    StandardCharsets.US_ASCII
            );

            try {
                return decode(encodedKey);
            } catch (IllegalArgumentException exception) {
                throw new IOException(
                        "Trusted public key resource is malformed",
                        exception
                );
            }
        }
    }

    /**
     * Decodes a canonical Base64 X.509 Ed25519 public key.
     *
     * <p>Surrounding whitespace is ignored to permit a trailing newline
     * in the resource file. Internal whitespace is not permitted.</p>
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
                    "Trusted public key cannot be blank"
            );
        }

        final byte[] decoded;

        try {
            decoded = Base64.getDecoder().decode(normalized);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "Trusted public key is not valid Base64",
                    exception
            );
        }

        String canonical = Base64.getEncoder()
                .encodeToString(decoded);

        if (!canonical.equals(normalized)) {
            throw new IllegalArgumentException(
                    "Trusted public key is not in canonical Base64 format"
            );
        }

        return Ed25519SignatureService.decodePublicKey(normalized);
    }
}
