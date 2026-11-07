// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.integrity.crypto;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TrustedPublicKeyLoaderTest {

    @Test
    void shouldDecodeGeneratedEd25519PublicKey()
            throws GeneralSecurityException {
        KeyPair keyPair =
                Ed25519SignatureService.generateKeyPair();

        String encoded = Ed25519SignatureService.encodePublicKey(
                keyPair.getPublic()
        );

        var decoded = TrustedPublicKeyLoader.decode(encoded);

        assertEquals(
                keyPair.getPublic().getAlgorithm(),
                decoded.getAlgorithm()
        );

        assertArrayEquals(
                keyPair.getPublic().getEncoded(),
                decoded.getEncoded()
        );
    }

    @Test
    void shouldAcceptTrailingNewline()
            throws GeneralSecurityException {
        KeyPair keyPair =
                Ed25519SignatureService.generateKeyPair();

        String encoded = Ed25519SignatureService.encodePublicKey(
                keyPair.getPublic()
        );

        var decoded = TrustedPublicKeyLoader.decode(encoded + "\n");

        assertArrayEquals(
                keyPair.getPublic().getEncoded(),
                decoded.getEncoded()
        );
    }

    @Test
    void shouldRejectBlankKey() {
        assertThrows(
                IllegalArgumentException.class,
                () -> TrustedPublicKeyLoader.decode("   ")
        );
    }

    @Test
    void shouldRejectInvalidBase64() {
        assertThrows(
                IllegalArgumentException.class,
                () -> TrustedPublicKeyLoader.decode("not-a-public-key")
        );
    }

    @Test
    void shouldRejectInternalWhitespace()
            throws GeneralSecurityException {
        KeyPair keyPair =
                Ed25519SignatureService.generateKeyPair();

        String encoded = Ed25519SignatureService.encodePublicKey(
                keyPair.getPublic()
        );

        String modified = encoded.substring(0, 8)
                + " "
                + encoded.substring(8);

        assertThrows(
                IllegalArgumentException.class,
                () -> TrustedPublicKeyLoader.decode(modified)
        );
    }

    @Test
    void shouldRejectDataThatIsNotAnEd25519Key() {
        String encoded = Base64.getEncoder()
                .encodeToString(
                        "not-an-ed25519-key".getBytes(StandardCharsets.UTF_8)
                );

        assertThrows(
                GeneralSecurityException.class,
                () -> TrustedPublicKeyLoader.decode(encoded)
        );
    }

    @Test
    void shouldFailWhenTrustedKeyResourceIsMissing() {
        assertThrows(
                IOException.class,
                () -> TrustedPublicKeyLoader.load(
                        TrustedPublicKeyLoaderTest.class
                )
        );
    }
}
