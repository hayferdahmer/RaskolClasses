// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.integrity.crypto;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;

import org.junit.jupiter.api.Test;

class Ed25519SignatureServiceTest {

    @Test
    void shouldGenerateEd25519KeyPair() throws Exception {
        KeyPair keyPair = Ed25519SignatureService.generateKeyPair();

        assertNotNull(keyPair.getPrivate());
        assertNotNull(keyPair.getPublic());

        assertEquals("EdDSA", keyPair.getPrivate().getAlgorithm());
    }

    @Test
    void shouldSignAndVerifyData() throws Exception {
        KeyPair keyPair = Ed25519SignatureService.generateKeyPair();

        byte[] data = "RaskolClasses official build".getBytes(StandardCharsets.UTF_8);

        byte[] signature = Ed25519SignatureService.sign(data, keyPair.getPrivate());

        assertEquals(64, signature.length);

        assertTrue(Ed25519SignatureService.verify(data, signature, keyPair.getPublic()));
    }

    @Test
    void shouldRejectModifiedData() throws Exception {
        KeyPair keyPair = Ed25519SignatureService.generateKeyPair();

        byte[] originalData = "official-build".getBytes(StandardCharsets.UTF_8);

        byte[] modifiedData = "modified-build".getBytes(StandardCharsets.UTF_8);

        byte[] signature = Ed25519SignatureService.sign(originalData, keyPair.getPrivate());

        assertFalse(Ed25519SignatureService.verify(modifiedData, signature, keyPair.getPublic()));
    }

    @Test
    void shouldRejectSignatureFromAnotherKey() throws Exception {
        KeyPair trustedKeyPair = Ed25519SignatureService.generateKeyPair();

        KeyPair untrustedKeyPair = Ed25519SignatureService.generateKeyPair();

        byte[] data = "trusted-release".getBytes(StandardCharsets.UTF_8);

        byte[] signature = Ed25519SignatureService.sign(data, untrustedKeyPair.getPrivate());

        assertFalse(Ed25519SignatureService.verify(data, signature, trustedKeyPair.getPublic()));
    }

    @Test
    void shouldRejectInvalidSignatureLength() throws Exception {
        KeyPair keyPair = Ed25519SignatureService.generateKeyPair();

        byte[] data = "test-data".getBytes(StandardCharsets.UTF_8);

        byte[] invalidSignature = new byte[32];

        assertFalse(Ed25519SignatureService.verify(data, invalidSignature, keyPair.getPublic()));
    }

    @Test
    void shouldEncodeAndDecodeKeys() throws Exception {
        KeyPair originalKeyPair = Ed25519SignatureService.generateKeyPair();

        String encodedPrivateKey =
                Base64.getEncoder().encodeToString(originalKeyPair.getPrivate().getEncoded());

        String encodedPublicKey = Ed25519SignatureService.encodePublicKey(originalKeyPair.getPublic());

        PrivateKey decodedPrivateKey = Ed25519SignatureService.decodePrivateKey(encodedPrivateKey);

        PublicKey decodedPublicKey = Ed25519SignatureService.decodePublicKey(encodedPublicKey);

        byte[] data = "key-round-trip-test".getBytes(StandardCharsets.UTF_8);

        byte[] signature = Ed25519SignatureService.sign(data, decodedPrivateKey);

        assertTrue(Ed25519SignatureService.verify(data, signature, decodedPublicKey));
    }

    @Test
    void shouldRejectInvalidBase64Key() {
        assertNull(Ed25519SignatureService.decodePublicKey("not-a-valid-base64-key!"));
    }

    @Test
    void shouldRejectBlankPrivateKey() {
        assertNull(Ed25519SignatureService.decodePrivateKey(" "));
    }

    @Test
    void shouldHandleNullDataSafely() throws Exception {
        KeyPair keyPair = Ed25519SignatureService.generateKeyPair();

        assertEquals(0, Ed25519SignatureService.sign(null, keyPair.getPrivate()).length);

        assertFalse(Ed25519SignatureService.verify(
                null, new byte[Ed25519SignatureService.SIGNATURE_LENGTH], keyPair.getPublic()));
    }
}
