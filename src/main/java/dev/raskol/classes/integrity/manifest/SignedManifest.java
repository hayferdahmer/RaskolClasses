// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.integrity.manifest;

import java.util.Arrays;
import java.util.Objects;

/**
 * Immutable container holding canonical manifest bytes and their signature.
 *
 * <p>Both arrays are defensively copied on input and output.</p>
 */
public final class SignedManifest {

    private final byte[] manifestBytes;
    private final byte[] signature;

    public SignedManifest(byte[] manifestBytes, byte[] signature) {
        Objects.requireNonNull(manifestBytes, "manifestBytes cannot be null");
        Objects.requireNonNull(signature, "signature cannot be null");

        if (manifestBytes.length == 0) {
            throw new IllegalArgumentException("Manifest cannot be empty");
        }

        if (signature.length != 64) {
            throw new IllegalArgumentException("Ed25519 signature must contain exactly 64 bytes");
        }

        this.manifestBytes = Arrays.copyOf(manifestBytes, manifestBytes.length);

        this.signature = Arrays.copyOf(signature, signature.length);
    }

    public byte[] manifestBytes() {
        return Arrays.copyOf(manifestBytes, manifestBytes.length);
    }

    public byte[] signature() {
        return Arrays.copyOf(signature, signature.length);
    }
}
