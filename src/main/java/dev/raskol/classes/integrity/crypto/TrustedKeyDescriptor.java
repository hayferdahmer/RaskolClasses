// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.integrity.crypto;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Immutable lifecycle descriptor for a trusted RIPS public key.
 *
 * <p>The descriptor stores only the SHA-256 fingerprint of the public
 * key. It never stores private key material.</p>
 *
 * @param fingerprint lowercase hexadecimal SHA-256 fingerprint
 * @param status lifecycle status
 */
public record TrustedKeyDescriptor(
        String fingerprint,
        TrustedKeyStatus status
) {

    private static final Pattern FINGERPRINT_PATTERN =
            Pattern.compile("[0-9a-f]{64}");

    public TrustedKeyDescriptor {
        Objects.requireNonNull(fingerprint, "fingerprint");
        Objects.requireNonNull(status, "status");

        if (!FINGERPRINT_PATTERN.matcher(fingerprint).matches()) {
            throw new IllegalArgumentException(
                    "Fingerprint must be 64 lowercase hexadecimal characters"
            );
        }
    }

    /**
     * Indicates whether this key may verify official signatures.
     *
     * @return true for ACTIVE and VERIFY_ONLY
     */
    public boolean canVerify() {
        return status == TrustedKeyStatus.ACTIVE
                || status == TrustedKeyStatus.VERIFY_ONLY;
    }

    /**
     * Indicates whether this key is authorized to sign new releases.
     *
     * <p>This is a policy descriptor only. Actual signing must remain
     * in the trusted external release pipeline.</p>
     *
     * @return true only for ACTIVE
     */
    public boolean canSign() {
        return status == TrustedKeyStatus.ACTIVE;
    }

    /**
     * Creates a new descriptor with the requested lifecycle status.
     *
     * <p>Allowed transitions:</p>
     * <ul>
     *     <li>ACTIVE → VERIFY_ONLY</li>
     *     <li>ACTIVE → REVOKED</li>
     *     <li>VERIFY_ONLY → REVOKED</li>
     * </ul>
     *
     * <p>REVOKED is terminal. Reactivation requires a new key
     * and a new fingerprint.</p>
     *
     * @param newStatus requested status
     * @return new immutable descriptor
     * @throws NullPointerException if newStatus is null
     * @throws IllegalStateException if the transition is not allowed
     */
    public TrustedKeyDescriptor transitionTo(
            TrustedKeyStatus newStatus
    ) {
        Objects.requireNonNull(newStatus, "newStatus");

        boolean allowed = switch (status) {
            case ACTIVE ->
                    newStatus == TrustedKeyStatus.VERIFY_ONLY
                    || newStatus == TrustedKeyStatus.REVOKED;
            case VERIFY_ONLY ->
                    newStatus == TrustedKeyStatus.REVOKED;
            case REVOKED -> false;
        };

        if (!allowed) {
            throw new IllegalStateException(
                    "Invalid trusted key lifecycle transition: "
                            + status + " -> " + newStatus
            );
        }

        return new TrustedKeyDescriptor(fingerprint, newStatus);
    }
}
