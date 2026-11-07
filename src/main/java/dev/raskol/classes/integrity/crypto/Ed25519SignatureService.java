// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.integrity.crypto;

import dev.raskol.classes.RaskolClasses;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Stateless Ed25519-подписи для RIPS (Release Integrity and Package Signing).
 *
 * <p>Контракт:
 * <ul>
 *   <li>{@link #sign(byte[], PrivateKey)} — детерминированная подпись 64 байта.</li>
 *   <li>{@link #verify(byte[], byte[], PublicKey)} — верификация по явно переданному ключу.</li>
 *   <li>{@link #decodePublicKey(String)} — статический парсер Base64-DER X.509 Ed25519 ключа
 *       (используется {@link TrustedPublicKeyRegistry} и {@link TrustedPublicKeyLoader}).</li>
 *   <li>{@link #decodePrivateKey(String)} — статический парсер Base64-PKCS#8 Ed25519 ключа.</li>
 * </ul>
 *
 * <p>Приватный ключ НЕ хранится в полях сервиса — владелец ключа (обычно CI или
 * ManifestSignatureService) передаёт его явно в каждом вызове. Это исключает
 * утечку ключа через ссылки на синглтон и упрощает ротацию.
 *
 * 1.15.8: сервис переписан как stateless для соответствия контракту ManifestSignatureService;
 * добавлен публичный decodePublicKey для TrustedPublicKeyRegistry/Loader.
 */
public final class Ed25519SignatureService {

    private static final Logger LOGGER = Logger.getLogger("RaskolClasses.RIPS");

    /** Имя алгоритма подписи в JCA (Java 15+, Ed25519 нативно). */
    public static final String SIGNATURE_ALGORITHM = "Ed25519";

    /** Имя KeyFactory для Ed25519. */
    public static final String KEY_ALGORITHM = "Ed25519";

    /** Длина Ed25519-подписи в байтах. */
    public static final int SIGNATURE_LENGTH = 64;

    private final RaskolClasses plugin;

    public Ed25519SignatureService(RaskolClasses plugin) {
        this.plugin = plugin;
    }

    /* ------------------------------ core API ------------------------------ */

    /**
     * Подписывает данные переданным приватным ключом. Возвращает 64-байтовую Ed25519-подпись.
     *
     * @param data данные для подписи (обычно SHA-256 дайджест артефакта)
     * @param privateKey Ed25519 приватный ключ в PKCS#8
     * @return 64 байта подписи; byte[0] при ошибке или null-ключах
     */
    public byte[] sign(byte[] data, PrivateKey privateKey) {
        if (data == null || privateKey == null) {
            return new byte[0];
        }
        try {
            Signature signer = Signature.getInstance(SIGNATURE_ALGORITHM);
            signer.initSign(privateKey);
            signer.update(data);
            byte[] signature = signer.sign();
            if (signature.length != SIGNATURE_LENGTH) {
                LOGGER.warning("RIPS: unexpected signature length " + signature.length
                        + " (expected " + SIGNATURE_LENGTH + ")");
            }
            return signature;
        } catch (GeneralSecurityException ex) {
            LOGGER.log(Level.WARNING, "RIPS: signing failed", ex);
            return new byte[0];
        }
    }

    /**
     * Верифицирует подпись по явно переданному публичному ключу.
     *
     * @param data подписанные данные
     * @param signature 64-байтовая Ed25519-подпись
     * @param publicKey Ed25519 публичный ключ X.509
     * @return true если криптографически корректна
     */
    public boolean verify(byte[] data, byte[] signature, PublicKey publicKey) {
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

    /* ------------------------------ static key parsers ------------------------------ */

    /**
     * Парсит публичный Ed25519 ключ из Base64-DER X.509 SubjectPublicKeyInfo.
     * Используется {@link TrustedPublicKeyRegistry} и {@link TrustedPublicKeyLoader}
     * при загрузке доверенных ключей из конфигурации.
     *
     * @param base64Der Base64-строка X.509 DER (44 байта для Ed25519)
     * @return PublicKey или null при ошибке
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
     * Парсит приватный Ed25519 ключ из Base64-PKCS#8 DER.
     * Проверяет каноничность Base64 (повторное кодирование совпадает с исходным).
     *
     * @param base64Pkcs8 Base64-строка PKCS#8 DER
     * @return PrivateKey или null при ошибке/неканоничном Base64
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

    /* ------------------------------ convenience ------------------------------ */

    /** Подписывает UTF-8 строку. */
    public byte[] signString(String message, PrivateKey privateKey) {
        if (message == null) {
            return new byte[0];
        }
        return sign(message.getBytes(StandardCharsets.UTF_8), privateKey);
    }

    /** Возвращает подпись в Base64 (для .sig-файлов). */
    public String signToBase64(byte[] data, PrivateKey privateKey) {
        byte[] sig = sign(data, privateKey);
        return sig.length == 0 ? "" : Base64.getEncoder().encodeToString(sig);
    }

    /** Декодирует Base64-подпись и верифицирует её по переданному ключу. */
    public boolean verifyFromBase64(byte[] data, String signatureBase64, PublicKey publicKey) {
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

    /** Плагин-референс для интеграций. */
    public RaskolClasses plugin() {
        return plugin;
    }
}
