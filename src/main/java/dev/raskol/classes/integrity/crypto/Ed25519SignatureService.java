// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.integrity.crypto;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Статический сервис Ed25519-подписей для RIPS.
 * Ключи передаются параметрами и не сохраняются сервисом.
 */
public final class Ed25519SignatureService {

    private static final Logger LOGGER = Logger.getLogger("RaskolClasses.RIPS");

    public static final String SIGNATURE_ALGORITHM = "Ed25519";
    public static final String KEY_ALGORITHM = "Ed25519";
    public static final int SIGNATURE_LENGTH = 64;

    private Ed25519SignatureService() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Создаёт новую пару ключей Ed25519.
     *
     * @return новая пара открытого и закрытого ключей
     * @throws GeneralSecurityException если алгоритм Ed25519 недоступен
     */
    public static KeyPair generateKeyPair() throws GeneralSecurityException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance(KEY_ALGORITHM);
        return generator.generateKeyPair();
    }

    /**
     * Кодирует открытый ключ Ed25519 в Base64 X.509 DER.
     *
     * @param publicKey открытый ключ
     * @return Base64-представление открытого ключа
     */
    public static String encodePublicKey(PublicKey publicKey) {
        if (publicKey == null) {
            throw new IllegalArgumentException("publicKey cannot be null");
        }

        return Base64.getEncoder().encodeToString(publicKey.getEncoded());
    }

    /**
     * Подписывает данные приватным ключом. 64 байта Ed25519-подписи.
     */
    public static byte[] sign(byte[] data, PrivateKey privateKey) {
        if (data == null || privateKey == null) {
            return new byte[0];
        }

        try {
            Signature signer = Signature.getInstance(SIGNATURE_ALGORITHM);
            signer.initSign(privateKey);
            signer.update(data);

            byte[] signature = signer.sign();

            if (signature.length != SIGNATURE_LENGTH) {
                LOGGER.warning("RIPS: unexpected signature length " + signature.length + " (expected "
                        + SIGNATURE_LENGTH + ")");
            }

            return signature;
        } catch (GeneralSecurityException ex) {
            LOGGER.log(Level.WARNING, "RIPS: signing failed", ex);
            return new byte[0];
        }
    }

    /**
     * Проверяет подпись по публичному ключу.
     */
    public static boolean verify(byte[] data, byte[] signature, PublicKey publicKey) {
        if (data == null || signature == null || publicKey == null) {
            return false;
        }

        if (signature.length != SIGNATURE_LENGTH) {
            return false;
        }

        try {
            Signature verifier = Signature.getInstance(SIGNATURE_ALGORITHM);
            verifier.initVerify(publicKey);
            verifier.update(data);

            return verifier.verify(signature);
        } catch (GeneralSecurityException ex) {
            LOGGER.log(Level.FINE, "RIPS: verification error", ex);
            return false;
        }
    }

    /**
     * Декодирует публичный Ed25519-ключ из Base64 X.509 DER.
     */
    public static PublicKey decodePublicKey(String base64Der) {
        if (base64Der == null || base64Der.isBlank()) {
            return null;
        }

        try {
            byte[] derBytes = Base64.getDecoder().decode(base64Der.trim());
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(derBytes);
            KeyFactory keyFactory = KeyFactory.getInstance(KEY_ALGORITHM);

            return keyFactory.generatePublic(keySpec);
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            LOGGER.log(Level.FINE, "RIPS: failed to parse public key from Base64", ex);
            return null;
        }
    }

    /**
     * Декодирует приватный Ed25519-ключ из Base64 PKCS#8 DER.
     */
    public static PrivateKey decodePrivateKey(String base64Pkcs8) {
        if (base64Pkcs8 == null || base64Pkcs8.isBlank()) {
            return null;
        }

        String trimmed = base64Pkcs8.trim();
        byte[] derBytes;

        try {
            derBytes = Base64.getDecoder().decode(trimmed);
        } catch (IllegalArgumentException ex) {
            LOGGER.log(Level.FINE, "RIPS: private key is not valid Base64", ex);
            return null;
        }

        if (derBytes.length == 0) {
            return null;
        }

        String reencoded = Base64.getEncoder().encodeToString(derBytes);

        if (!reencoded.equals(trimmed)) {
            LOGGER.warning("RIPS: private key Base64 is not canonical");
            return null;
        }

        try {
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(derBytes);
            KeyFactory keyFactory = KeyFactory.getInstance(KEY_ALGORITHM);

            return keyFactory.generatePrivate(keySpec);
        } catch (GeneralSecurityException ex) {
            LOGGER.log(Level.WARNING, "RIPS: failed to parse PKCS#8 Ed25519 private key", ex);
            return null;
        }
    }

    /**
     * Подписывает UTF-8 строку.
     */
    public static byte[] signString(String message, PrivateKey privateKey) {
        if (message == null) {
            return new byte[0];
        }

        return sign(message.getBytes(StandardCharsets.UTF_8), privateKey);
    }

    /**
     * Подписывает данные и возвращает подпись в Base64.
     */
    public static String signToBase64(byte[] data, PrivateKey privateKey) {
        byte[] signature = sign(data, privateKey);

        return signature.length == 0 ? "" : Base64.getEncoder().encodeToString(signature);
    }

    /**
     * Проверяет Base64-подпись.
     */
    public static boolean verifyFromBase64(byte[] data, String signatureBase64, PublicKey publicKey) {
        if (signatureBase64 == null || signatureBase64.isEmpty()) {
            return false;
        }

        try {
            byte[] signature = Base64.getDecoder().decode(signatureBase64);

            return verify(data, signature, publicKey);
        } catch (IllegalArgumentException ex) {
            LOGGER.log(Level.FINE, "RIPS: invalid base64 signature", ex);
            return false;
        }
    }
}
