// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.integrity.crypto;

import java.security.MessageDigest;
import java.security.PublicKey;
import java.util.Objects;

/**
 * A pinned public key together with its lifecycle policy.
 *
 * <p>The descriptor contains the key fingerprint and status.
 * The public key itself is retained because signature verification
 * requires the actual key material.</p>
 */
public record TrustedPublicKeyEntry(
        PublicKey publicKey,
        TrustedKeyDescriptor descriptor
) {

    public TrustedPublicKeyEntry {
        Objects.requireNonNull(publicKey, "publicKey");
        Objects.requireNonNull(descriptor, "descriptor");

        Objects.requireNonNull(
                publicKey.getEncoded(),
                "Public key encoding must not be null"
        );

        if (!"EdDSA".equalsIgnoreCase(publicKey.getAlgorithm())
                && !"Ed25519".equalsIgnoreCase(publicKey.getAlgorithm())) {
            throw new IllegalArgumentException(
                    "Only Ed25519 public keys are supported"
            );
        }

        String actualFingerprint =
                TrustedPublicKeyRegistry.fingerprint(publicKey);

        if (!MessageDigest.isEqual(
                actualFingerprint.getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                descriptor.fingerprint().getBytes(java.nio.charset.StandardCharsets.US_ASCII)
        )) {
            throw new IllegalArgumentException(
                    "Trusted key descriptor fingerprint does not match public key"
            );
        }
    }

    /**
     * Indicates whether this entry may be used for signature verification.
     */
    public boolean canVerify() {
        return descriptor.canVerify();
    }

    /**
     * Indicates whether this entry is authorized by policy to sign new releases.
     *
     * <p>This does not perform signing or expose private key material.</p>
     */
    public boolean canSign() {
        return descriptor.canSign();
    }
}
