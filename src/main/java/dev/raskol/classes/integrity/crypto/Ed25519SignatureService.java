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
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Сервис Ed25519-подписей для RIPS (Release Integrity and Package Signing).
 *
 * <p>Назначение: подпись релизных артефактов (jar/sha256-сам) приватным ключом из
 * окружения (CI) и верификация подписей через {@link TrustedPublicKeyRegistry}
 * (публичные ключи со статусами ACTIVE/VERIFY_ONLY/REVOKED).
 *
 * <p>Контракты:
 * <ul>
 *   <li>Приватный ключ читается из переменной окружения {@code RIPS_SIGNING_PRIVATE_KEY_B64}
 *       в формате Base64 PKCS#8 DER.</li>
 *   <li>При отсутствии ключа сервис переходит в read-only режим: {@link #canSign()} = false,
 *       {@link #sign(byte[])} возвращает пустой массив.</li>
 *   <li>Верификация работает независимо от наличия приватного ключа — по ACTIVE-ключу
 *       из {@link TrustedPublicKeyRegistry}.</li>
 *   <li>Подписи детерминированы для одного и того же сообщения и ключа (Ed25519 by RFC 8032).</li>
 * </ul>
 *
 * <p>Поток данных при релизе:
 * <ol>
 *   <li>CI собирает jar, считает SHA-256.</li>
 *   <li>Вызывает {@link #sign(byte[])} от дайджеста — получает 64-байтовую подпись.</li>
 *   <li>Публикует jar + .sig + SHA-256.</li>
 *   <li>Сервер при загрузке проверяет подпись через {@link #verify(byte[], byte[])}.</li>
 * </ol>
 *
 * 1.15.8: сервис добавлен для поддержки RIPS-пайплайна; приватный ключ никогда
 * не логируется, не кешируется в поле дольше времени жизни JVM, при ошибке чтения
 * сервис не падает (read-only graceful degradation).
 */
public final class Ed25519SignatureService {

    private static final Logger LOGGER = Logger.getLogger("RaskolClasses.RIPS");

    /** Имя алгоритма подписи в JCA (Java 15+, Ed25519 поддерживается нативно). */
    private static final String SIGNATURE_ALGORITHM = "Ed25519";

    /** Имя KeyFactory для Ed25519. */
    private static final String KEY_ALGORITHM = "Ed25519";

    /** Имя переменной окружения с Base64-PKCS#8 приватным ключом. */
    public static final String PRIVATE_KEY_ENV = "RIPS_SIGNING_PRIVATE_KEY_B64";

    /** Длина Ed25519-подписи в байтах. */
    public static final int SIGNATURE_LENGTH = 64;

    private final RaskolClasses plugin;
    private final TrustedPublicKeyRegistry registry;
    private final PrivateKey privateKey;

    /**
     * Конструктор. Пытается загрузить приватный ключ из окружения; при неудаче
     * остаётся в read-only режиме (canSign=false). Ошибки логируются как warning,
     * не бросаются — это позволяет плагину запускаться без ключа в dev-среде.
     */
    public Ed25519SignatureService(RaskolClasses plugin, TrustedPublicKeyRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
        this.privateKey = loadPrivateKey();
        if (this.privateKey != null) {
            LOGGER.info("RIPS: Ed25519 signing key loaded (signing enabled)");
        } else {
            LOGGER.info("RIPS: Ed25519 signing key not available (signing disabled, "
                    + "verification only)");
        }
    }

    /**
     * Возвращает true, если сервис может подписывать (приватный ключ доступен).
     */
    public boolean canSign() {
        return privateKey != null;
    }

    /**
     * Подписывает входные байты приватным ключом. Возвращает 64-байтовую Ed25519-подпись.
     * Если ключ недоступен — возвращает пустой массив (read-only режим).
     *
     * @param data данные для подписи (обычно SHA-256 дайджест артефакта)
     * @return 64 байта подписи или byte[0] при отсутствии ключа
     */
    public byte[] sign(byte[] data) {
        if (data == null) {
            throw new IllegalArgumentException("data must not be null");
        }
        if (privateKey == null) {
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
     * Верифицирует подпись по ACTIVE-ключу из реестра. Возвращает true только если
     * подпись криптографически корректна И соответствует активному публичному ключу.
     *
     * @param data подписанные данные
     * @param signature 64-байтовая Ed25519-подпись
     * @return true если валидна
     */
    public boolean verify(byte[] data, byte[] signature) {
        if (data == null || signature == null) {
            return false;
        }
        if (signature.length != SIGNATURE_LENGTH) {
            return false;
        }
        PublicKey activeKey = registry.activePublicKey();
        if (activeKey == null) {
            LOGGER.warning("RIPS: no ACTIVE public key in registry — verification impossible");
            return false;
        }
        return verifyWithKey(data, signature, activeKey);
    }

    /**
     * Верифицирует подпись по ЛЮБОМУ ключу из реестра со статусом ACTIVE или VERIFY_ONLY.
     * Используется для проверки подписей, сделанных ключами в фазе ротации
     * (старый ключ ещё валиден для верификации, но не для подписи).
     *
     * @param data подписанные данные
     * @param signature 64-байтовая Ed25519-подпись
     * @return true если хотя бы один доверенный ключ подтверждает подпись
     */
    public boolean verifyAnyTrusted(byte[] data, byte[] signature) {
        if (data == null || signature == null || signature.length != SIGNATURE_LENGTH) {
            return false;
        }
        List<PublicKey> trustedKeys = registry.verifiablePublicKeys();
        if (trustedKeys.isEmpty()) {
            LOGGER.warning("RIPS: no verifiable public keys in registry");
            return false;
        }
        for (PublicKey key : trustedKeys) {
            if (verifyWithKey(data, signature, key)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Подписывает UTF-8 строку. convenience-обёртка над {@link #sign(byte[])}.
     */
    public byte[] signString(String message) {
        if (message == null) {
            throw new IllegalArgumentException("message must not be null");
        }
        return sign(message.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Возвращает подпись в Base64 (для вставки в .sig-файл или metadata).
     */
    public String signToBase64(byte[] data) {
        byte[] sig = sign(data);
        return sig.length == 0 ? "" : Base64.getEncoder().encodeToString(sig);
    }

    /**
     * Декодирует Base64-подпись и верифицирует её по ACTIVE-ключу.
     */
    public boolean verifyFromBase64(byte[] data, String signatureBase64) {
        if (signatureBase64 == null || signatureBase64.isEmpty()) {
            return false;
        }
        try {
            byte[] signature = Base64.getDecoder().decode(signatureBase64);
            return verify(data, signature);
        } catch (IllegalArgumentException ex) {
            LOGGER.log(Level.FINE, "RIPS: invalid base64 signature", ex);
            return false;
        }
    }

    /* ------------------------------ внутренние методы ------------------------------ */

    private boolean verifyWithKey(byte[] data, byte[] signature, PublicKey publicKey) {
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
     * Читает приватный ключ из переменной окружения. Возвращает null при отсутствии
     * переменной, пустой строке, некорректном Base64 или некорректном PKCS#8 DER.
     * Все ошибки логируются, исключений не бросается.
     */
    private PrivateKey loadPrivateKey() {
        String envValue;
        try {
            envValue = System.getenv(PRIVATE_KEY_ENV);
        } catch (SecurityException ex) {
            LOGGER.log(Level.WARNING, "RIPS: cannot read env " + PRIVATE_KEY_ENV, ex);
            return null;
        }
        if (envValue == null || envValue.isBlank()) {
            return null;
        }
        String trimmed = envValue.trim();
        byte[] derBytes;
        try {
            derBytes = Base64.getDecoder().decode(trimmed);
        } catch (IllegalArgumentException ex) {
            LOGGER.warning("RIPS: " + PRIVATE_KEY_ENV + " is not valid Base64");
            return null;
        }
        if (derBytes.length == 0) {
            LOGGER.warning("RIPS: " + PRIVATE_KEY_ENV + " decoded to empty byte array");
            return null;
        }
        // Каноничность Base64: кодируем обратно и сверяем (защита от padding-вариаций)
        String reencoded = Base64.getEncoder().encodeToString(derBytes);
        if (!reencoded.equals(trimmed)) {
            LOGGER.warning("RIPS: " + PRIVATE_KEY_ENV + " is not canonical Base64 "
                    + "(re-encoded differs)");
            return null;
        }
        try {
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(derBytes);
            KeyFactory keyFactory = KeyFactory.getInstance(KEY_ALGORITHM);
            return keyFactory.generatePrivate(keySpec);
        } catch (GeneralSecurityException ex) {
            LOGGER.log(Level.WARNING, "RIPS: failed to parse " + PRIVATE_KEY_ENV
                    + " as PKCS#8 Ed25519 private key", ex);
            return null;
        }
    }

    /**
     * Читает публичный ключ из Base64 DER (X.509 SubjectPublicKeyInfo).
     * Используется в тестах и при загрузке ключа из внешнего источника.
     *
     * @param base64Der Base64-строка с X.509 DER-представлением Ed25519 публичного ключа
     * @return PublicKey или null при ошибке декодирования/парсинга
     */
    public static PublicKey parsePublicKeyBase64(String base64Der) {
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
     * Возвращает отладочную сводку (для /rc debug): статус ключа, наличие ACTIVE
     * в реестре, количество доверенных ключей. Ключевой материал НЕ включается.
     */
    public String debugSummary() {
        return "Ed25519SignatureService{"
                + "canSign=" + canSign()
                + ", activeKey=" + (registry.activePublicKey() != null)
                + ", verifiableKeys=" + registry.verifiablePublicKeys().size()
                + "}";
    }

    /**
     * Плагин-референс (для интеграций, которым нужен доступ к плагину из сервиса).
     */
    public RaskolClasses plugin() {
        return plugin;
    }
}
