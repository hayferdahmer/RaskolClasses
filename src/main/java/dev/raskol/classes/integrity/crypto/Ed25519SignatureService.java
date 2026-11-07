```java
// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.integrity.crypto;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Objects;

/**
 * Provides Ed25519 key generation, signing and signature verification.
 *
 * <p>Private keys must remain outside the plugin artifact and source
 * repository. Public keys may be embedded into the plugin.</p>
 *
 * <p>This class does not establish build authenticity by itself.
 * Callers must verify signatures against trusted public keys and
 * trusted signed content.</p>
 */
public final class Ed25519SignatureService {

    private static final String ALGORITHM = "Ed25519";

    private static final int SIGNATURE_LENGTH = 64;

    private Ed25519SignatureService() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Generates a new Ed25519 key pair.
     *
     * @return generated key pair
     * @throws GeneralSecurityException if Ed25519 is unavailable
     */
    public static KeyPair generateKeyPair()
            throws GeneralSecurityException {
        KeyPairGenerator generator =
                KeyPairGenerator.getInstance(ALGORITHM);

        return generator.generateKeyPair();
    }

    /**
     * Signs the supplied data using an Ed25519 private key.
     *
     * @param data data to sign
     * @param privateKey private signing key
     * @return 64-byte Ed25519 signature
     * @throws GeneralSecurityException if signing fails
     */
    public static byte[] sign(
            byte[] data,
            PrivateKey privateKey
    ) throws GeneralSecurityException {
        Objects.requireNonNull(data, "data cannot be null");
        Objects.requireNonNull(privateKey, "privateKey cannot be null");

        Signature signer = Signature.getInstance(ALGORITHM);
        signer.initSign(privateKey);
        signer.update(data);

        byte[] signature = signer.sign();

        if (signature.length != SIGNATURE_LENGTH) {
            throw new GeneralSecurityException(
                    "Unexpected Ed25519 signature length"
            );
        }

        return signature;
    }

    /**
     * Verifies an Ed25519 signature.
     *
     * @param data original signed data
     * @param signature signature to verify
     * @param publicKey trusted public key
     * @return true when the signature is valid
     * @throws GeneralSecurityException if verification cannot be performed
     */
    public static boolean verify(
            byte[] data,
            byte[] signature,
            PublicKey publicKey
    ) throws GeneralSecurityException {
        Objects.requireNonNull(data, "data cannot be null");
        Objects.requireNonNull(signature, "signature cannot be null");
        Objects.requireNonNull(publicKey, "publicKey cannot be null");

        if (signature.length != SIGNATURE_LENGTH) {
            return false;
        }

        Signature verifier = Signature.getInstance(ALGORITHM);
        verifier.initVerify(publicKey);
        verifier.update(data);

        return verifier.verify(signature);
    }

    /**
     * Encodes a private key as Base64 PKCS#8.
     *
     * <p>The returned value is sensitive and must never be committed
     * to the repository, printed to logs or embedded into a JAR.</p>
     *
     * @param privateKey private key
     * @return Base64-encoded PKCS#8 key
     */
    public static String encodePrivateKey(PrivateKey privateKey) {
        Objects.requireNonNull(
                privateKey,
                "privateKey cannot be null"
        );

        return Base64.getEncoder().encodeToString(
                privateKey.getEncoded()
        );
    }

    /**
     * Encodes a public key as Base64 X.509 SubjectPublicKeyInfo.
     *
     * @param publicKey public key
     * @return Base64-encoded X.509 key
     */
    public static String encodePublicKey(PublicKey publicKey) {
        Objects.requireNonNull(
                publicKey,
                "publicKey cannot be null"
        );

        return Base64.getEncoder().encodeToString(
                publicKey.getEncoded()
        );
    }

    /**
     * Decodes a Base64 PKCS#8 Ed25519 private key.
     *
     * @param encodedKey Base64-encoded private key
     * @return decoded private key
     * @throws GeneralSecurityException if the key is invalid
     */
    public static PrivateKey decodePrivateKey(String encodedKey)
            throws GeneralSecurityException {
        byte[] decoded = decodeBase64(encodedKey, "private key");

        PKCS8EncodedKeySpec keySpec =
                new PKCS8EncodedKeySpec(decoded);

        KeyFactory keyFactory = KeyFactory.getInstance(ALGORITHM);

        return keyFactory.generatePrivate(keySpec);
    }

    /**
     * Decodes a Base64 X.509 Ed25519 public key.
     *
     * @param encodedKey Base64-encoded public key
     * @return decoded public key
     * @throws GeneralSecurityException if the key is invalid
     */
    public static PublicKey decodePublicKey(String encodedKey)
            throws GeneralSecurityException {
        byte[] decoded = decodeBase64(encodedKey, "public key");

        X509EncodedKeySpec keySpec =
                new X509EncodedKeySpec(decoded);

        KeyFactory keyFactory = KeyFactory.getInstance(ALGORITHM);

        return keyFactory.generatePublic(keySpec);
    }

    private static byte[] decodeBase64(
            String encodedKey,
            String keyName
    ) throws InvalidKeySpecException {
        Objects.requireNonNull(
                encodedKey,
                keyName + " cannot be null"
        );

        if (encodedKey.isBlank()) {
            throw new InvalidKeySpecException(
                    keyName + " cannot be blank"
            );
        }

        try {
            return Base64.getDecoder().decode(encodedKey.trim());
        } catch (IllegalArgumentException exception) {
            InvalidKeySpecException invalidKey =
                    new InvalidKeySpecException(
                            "Invalid Base64 " + keyName
                    );

            invalidKey.initCause(exception);
            throw invalidKey;
        }
    }
}
```
