// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.integrity.manifest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SignedManifestTest {

    private static final byte[] VALID_MANIFEST = {
            1, 2, 3
    };

    private static final byte[] VALID_SIGNATURE = new byte[64];

    @Test
    void shouldRejectEmptyManifest() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SignedManifest(
                        new byte[0],
                        VALID_SIGNATURE
                )
        );
    }

    @Test
    void shouldRejectInvalidSignatureLength() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SignedManifest(
                        VALID_MANIFEST,
                        new byte[10]
                )
        );
    }

    @Test
    void shouldDefensivelyCopyInputArrays() {
        byte[] manifest = VALID_MANIFEST.clone();
        byte[] signature = VALID_SIGNATURE.clone();

        SignedManifest signedManifest =
                new SignedManifest(manifest, signature);

        manifest[0] = 99;
        signature[0] = 99;

        assertArrayEquals(
                VALID_MANIFEST,
                signedManifest.manifestBytes()
        );

        assertArrayEquals(
                VALID_SIGNATURE,
                signedManifest.signature()
        );
    }

    @Test
    void shouldReturnDefensiveCopies() {
        SignedManifest signedManifest =
                new SignedManifest(
                        VALID_MANIFEST,
                        VALID_SIGNATURE
                );

        byte[] returnedManifest = signedManifest.manifestBytes();
        byte[] returnedSignature = signedManifest.signature();

        returnedManifest[0] = 99;
        returnedSignature[0] = 99;

        assertArrayEquals(
                VALID_MANIFEST,
                signedManifest.manifestBytes()
        );

        assertArrayEquals(
                VALID_SIGNATURE,
                signedManifest.signature()
        );
    }
}
