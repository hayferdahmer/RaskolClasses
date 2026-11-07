
// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.integrity.crypto;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * Реестр публичных ключей, которым доверяет RIPS.
 *
 * <p>Доверенные ключи закрепляются непосредственно в исходном коде плагина.
 * Внешние ресурсы не должны использоваться в качестве корня доверия.</p>
 *
 * <p>Каждый закреплённый ключ имеет явный статус жизненного цикла.
 * Для проверки подписей используются ACTIVE и VERIFY_ONLY.
 * REVOKED сохраняются для аудита, но больше не считаются доверенными.</p>
 *
 * <p>В реестре разрешено хранить только публичные ключи.
 * Приватные ключи должны находиться вне репозитория и быть доступны
 * исключительно доверенному процессу подписания релизов.</p>
 */
public final class TrustedPublicKeyRegistry {

    /*
     * Политика ротации ключей:
     *
     * 1. Добавить новый публичный ключ со статусом VERIFY_ONLY.
     * 2. Выпустить переходную версию плагина.
     * 3. Перевести старый ACTIVE-ключ в VERIFY_ONLY,
     *    а новый ключ — в ACTIVE.
     * 4. Переключить CI на новый приватный ключ.
     * 5. Сохранять VERIFY_ONLY-ключи, пока необходима проверка
     *    подписей ранее выпущенных версий.
     * 6. Скомпрометированный ключ немедленно переводить в REVOKED.
     *
     * Никогда не добавлять приватные ключи в этот реестр.
     */
    private static final List<PinnedKeyConfiguration> PINNED_KEYS =
            List.of(
                    new PinnedKeyConfiguration(
                            "MCowBQYDK2V2AyEAESiWqMfemhrSC9x1q+sRz7Lo3cd1KIQSvWGBA/4In6Q=",
                            TrustedKeyStatus.ACTIVE
                    ),
                    new PinnedKeyConfiguration(
                            "MCowBQYDK2VwAyEAESiWqMfemhrSC9x1q+sRz7Lo3cd1KIQSvWGBA/4In6Q=",
                            TrustedKeyStatus.VERIFY_ONLY
                    )
            );

    private static final List<TrustedPublicKeyEntry> ALL_ENTRIES =
            loadPinnedEntries();

    /**
     * Содержит только ключи, разрешённые для проверки подписей.
     */
    private static final List<PublicKey> TRUSTED_KEYS =
            verificationKeys(ALL_ENTRIES);

    private TrustedPublicKeyRegistry() {
        throw new UnsupportedOperationException(
                "TrustedPublicKeyRegistry is a utility class"
        );
    }

    /**
     * Возвращает неизменяемый список ключей, разрешённых для проверки подписей.
     *
     * <p>В список входят ACTIVE и VERIFY_ONLY.
     * REVOKED исключаются.</p>
     *
     * @return неизменяемый список доверенных публичных ключей
     */
    public static List<PublicKey> trustedKeys() {
        return TRUSTED_KEYS;
    }

    /**
     * Возвращает единственный ACTIVE-публичный ключ.
     *
     * <p>Только этот ключ разрешён политикой для подписания новых релизов.
     * Возвращает null, если ACTIVE-ключ не настроен.</p>
     *
     * @return ACTIVE-публичный ключ или null
     */
    public static PublicKey activePublicKey() {
        for (TrustedPublicKeyEntry entry : ALL_ENTRIES) {
            if (entry.descriptor().status() == TrustedKeyStatus.ACTIVE) {
                return entry.publicKey();
            }
        }
        return null;
    }

    /**
     * Возвращает все ключи, разрешённые для проверки подписей.
     *
     * <p>Метод является псевдонимом trustedKeys() для совместимости
     * с контрактом Ed25519SignatureService.</p>
     *
     * @return неизменяемый список публичных ключей
     */
    public static List<PublicKey> verifiablePublicKeys() {
        return TRUSTED_KEYS;
    }

    /**
     * Возвращает все записи закреплённых ключей, включая отозванные.
     *
     * <p>Предназначен для анализа жизненного цикла и диагностики безопасности.
     * Код проверки подписей должен использовать trustedKeys().</p>
     *
     * @return неизменяемый список всех записей
     */
    public static List<TrustedPublicKeyEntry> allEntries() {
        return ALL_ENTRIES;
    }

    /**
     * Извлекает ключи, разрешённые для проверки подписей.
     *
     * @param entries записи доверенных ключей
     * @return неизменяемый список ключей для проверки
     */
    public static List<PublicKey> verificationKeys(
            List<TrustedPublicKeyEntry> entries
    ) {
        Objects.requireNonNull(entries, "entries");

        List<PublicKey> keys = new ArrayList<>(entries.size());

        for (TrustedPublicKeyEntry entry : entries) {
            Objects.requireNonNull(entry, "trusted key entry");

            if (entry.canVerify()) {
                keys.add(entry.publicKey());
            }
        }

        return List.copyOf(keys);
    }

    /**
     * Проверяет, является ли переданный публичный ключ доверенным.
     *
     * @param candidate проверяемый публичный ключ
     * @return true, если ключ имеет статус ACTIVE или VERIFY_ONLY
     */
    public static boolean isTrusted(PublicKey candidate) {
        if (candidate == null || candidate.getEncoded() == null) {
            return false;
        }

        byte[] candidateBytes = candidate.getEncoded();

        for (PublicKey trustedKey : TRUSTED_KEYS) {
            byte[] trustedBytes = trustedKey.getEncoded();

            if (MessageDigest.isEqual(candidateBytes, trustedBytes)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Возвращает SHA-256-отпечатки ключей, разрешённых для проверки подписей.
     *
     * @return неизменяемый список отпечатков в нижнем регистре
     */
    public static List<String> fingerprints() {
        List<String> fingerprints =
                new ArrayList<>(TRUSTED_KEYS.size());

        for (PublicKey key : TRUSTED_KEYS) {
            fingerprints.add(fingerprint(key));
        }

        return List.copyOf(fingerprints);
    }

    /**
     * Вычисляет SHA-256-отпечаток публичного ключа в формате X.509.
     *
     * @param publicKey публичный ключ
     * @return SHA-256-отпечаток в виде строки из 64 hex-символов
     */
    public static String fingerprint(PublicKey publicKey) {
        Objects.requireNonNull(publicKey, "publicKey");

        byte[] encoded = Objects.requireNonNull(
                publicKey.getEncoded(),
                "Public key encoding must not be null"
        );

        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(encoded);

            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 is not available in this Java runtime",
                    exception
            );
        }
    }

    /**
     * Декодирует и проверяет список закреплённых Ed25519-ключей.
     *
     * @param encodedKeys публичные ключи в Base64
     * @return неизменяемый список декодированных ключей
     * @throws GeneralSecurityException если список или ключ некорректен
     */
    static List<PublicKey> decodePinnedKeys(
            List<String> encodedKeys
    ) throws GeneralSecurityException {

        Objects.requireNonNull(encodedKeys, "encodedKeys");

        if (encodedKeys.isEmpty()) {
            throw new GeneralSecurityException(
                    "RIPS trusted public key registry is empty"
            );
        }

        List<PublicKey> keys =
                new ArrayList<>(encodedKeys.size());

        for (String encodedKey : encodedKeys) {
            if (encodedKey == null || encodedKey.isBlank()) {
                throw new GeneralSecurityException(
                        "Trusted public key cannot be null or blank"
                );
            }

            final byte[] decoded;

            try {
                decoded = Base64.getDecoder().decode(encodedKey);
            } catch (IllegalArgumentException exception) {
                throw new GeneralSecurityException(
                        "Invalid Base64 trusted public key",
                        exception
                );
            }

            if (!Base64.getEncoder()
                    .encodeToString(decoded)
                    .equals(encodedKey)) {
                throw new GeneralSecurityException(
                        "Non-canonical Base64 public key"
                );
            }

            PublicKey publicKey =
                    Ed25519SignatureService.decodePublicKey(encodedKey);

            if (publicKey.getEncoded() == null) {
                throw new GeneralSecurityException(
                        "Decoded public key has no encoded representation"
                );
            }

            for (PublicKey existing : keys) {
                if (MessageDigest.isEqual(
                        existing.getEncoded(),
                        publicKey.getEncoded()
                )) {
                    throw new GeneralSecurityException(
                            "Duplicate trusted public key"
                    );
                }
            }

            keys.add(publicKey);
        }

        return List.copyOf(keys);
    }

    /**
     * Декодирует конфигурации ключей и присваивает им статусы жизненного цикла.
     *
     * @param configurations конфигурации закреплённых ключей
     * @return неизменяемый список записей, включая REVOKED
     * @throws GeneralSecurityException если конфигурация некорректна
     */
    static List<TrustedPublicKeyEntry> decodePinnedEntries(
            List<PinnedKeyConfiguration> configurations
    ) throws GeneralSecurityException {

        Objects.requireNonNull(configurations, "configurations");

        if (configurations.isEmpty()) {
            throw new GeneralSecurityException(
                    "RIPS trusted public key registry is empty"
            );
        }

        List<String> encodedKeys = new ArrayList<>(
                configurations.size()
        );

        for (PinnedKeyConfiguration configuration : configurations) {
            if (configuration == null) {
                throw new GeneralSecurityException(
                        "Pinned key configuration cannot be null"
                );
            }

            if (configuration.status() == null) {
                throw new GeneralSecurityException(
                        "Pinned key status cannot be null"
                );
            }

            encodedKeys.add(configuration.encodedPublicKey());
        }

        List<PublicKey> decodedKeys = decodePinnedKeys(encodedKeys);

        List<TrustedPublicKeyEntry> entries =
                new ArrayList<>(decodedKeys.size());

        for (int i = 0; i < decodedKeys.size(); i++) {
            PublicKey publicKey = decodedKeys.get(i);

            TrustedKeyDescriptor descriptor =
                    new TrustedKeyDescriptor(
                            fingerprint(publicKey),
                            configurations.get(i).status()
                    );

            entries.add(
                    new TrustedPublicKeyEntry(
                            publicKey,
                            descriptor
                    )
            );
        }

        return List.copyOf(entries);
    }

    private static List<TrustedPublicKeyEntry> loadPinnedEntries() {
        try {
            return decodePinnedEntries(PINNED_KEYS);
        } catch (GeneralSecurityException
                 | IllegalArgumentException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    /**
     * Конфигурация одного закреплённого публичного ключа.
     *
     * @param encodedPublicKey публичный ключ в Base64
     * @param status статус жизненного цикла
     */
    record PinnedKeyConfiguration(
            String encodedPublicKey,
            TrustedKeyStatus status
    ) {
    }
}
