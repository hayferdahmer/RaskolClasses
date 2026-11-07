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
 * Registry of public keys trusted by RIPS.
 *
 * <p>Trusted keys are pinned directly in the plugin source code.
 * External resources must never be used as a trust anchor.</p>
 *
 * <p>Each pinned key has an explicit lifecycle status. Only ACTIVE
 * and VERIFY_ONLY keys are returned for signature verification.
 * REVOKED keys remain recorded for auditability but are never trusted.</p>
 *
 * <p>Only public keys may be stored here. Private keys must remain
 * outside the repository and be available only to the trusted
 * release-signing process.</p>
 */
public final class TrustedPublicKeyRegistry {

    /*
     * Key rotation policy:
     *
     * 1. Add the new public key with status ACTIVE.
     * 2. Change the previous ACTIVE key to VERIFY_ONLY.
     * 3. Sign new releases with the new private key in the external
     *    trusted release pipeline.
     * 4. Keep VERIFY_ONLY keys while old releases must remain valid.
     * 5. Change a compromised key to REVOKED immediately.
     *
     * Never add private keys to this registry.
     */
    private static final List<PinnedKeyConfiguration> PINNED_KEYS =
            List.of(
                    new PinnedKeyConfiguration(
                            "MCowBQYDK2VwAyEAESiWqMfemhrSC9x1q+sRz7Lo3cd1KIQSvWGBA/4In6Q=",
                            TrustedKeyStatus.ACTIVE
                    )
            );

    private static final List<TrustedPublicKeyEntry> ALL_ENTRIES =
            loadPinnedEntries();

    /**
     * Contains only keys authorized for signature verification.
     */
    private static final List<PublicKey> TRUSTED_KEYS =
            verificationKeys(ALL_ENTRIES);

    private TrustedPublicKeyRegistry() {
        throw new UnsupportedOperationException(
                "TrustedPublicKeyRegistry is a utility class"
        );
    }

    /**
     * Returns an immutable list of keys authorized for verification.
     *
     * <p>ACTIVE and VERIFY_ONLY keys are included.
     * REVOKED keys are excluded.</p>
     *
     * @return immutable list of trusted public keys
     */
    public static List<PublicKey> trustedKeys() {
        return TRUSTED_KEYS;
    }

    /**
     * Returns the single ACTIVE public key (the only key authorized
     * for signing new releases).
     *
     * <p>Returns null if no ACTIVE key is configured (should never
     * happen in production — the registry must always have exactly
     * one ACTIVE key).</p>
     *
     * @return ACTIVE public key, or null if none configured
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
     * Returns all keys authorized for signature verification.
     *
     * <p>This is an alias for {@link #trustedKeys()} provided for
     * compatibility with {@link Ed25519SignatureService} contract.</p>
     *
     * @return immutable list of verification-eligible public keys
     */
    public static List<PublicKey> verifiablePublicKeys() {
        return TRUSTED_KEYS;
    }

    /**
     * Returns every pinned key entry, including revoked entries.
     *
     * <p>This method is intended for lifecycle inspection and
     * security diagnostics. Verification code must use trustedKeys().</p>
     *
     * @return immutable list of all configured key entries
     */
    public static List<TrustedPublicKeyEntry> allEntries() {
        return ALL_ENTRIES;
    }

    /**
     * Extracts keys authorized for signature verification from
     * lifecycle entries.
     *
     * <p>ACTIVE and VERIFY_ONLY entries are included.
     * REVOKED entries are excluded.</p>
     *
     * @param entries configured trusted key entries
     * @return immutable list of verification-eligible public keys
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
     * Checks whether the supplied public key is currently trusted
     * for signature verification.
     *
     * @param candidate public key to check
     * @return true if the key is ACTIVE or VERIFY_ONLY
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
     * Returns SHA-256 fingerprints of keys currently trusted
     * for signature verification.
     *
     * @return immutable list of lowercase hexadecimal fingerprints
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
     * Calculates the SHA-256 fingerprint of an X.509 encoded
     * public key.
     *
     * @param publicKey public key
     * @return lowercase hexadecimal SHA-256 fingerprint
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
     * Decodes and validates a list of pinned Ed25519 public keys.
     *
     * <p>This method is retained for compatibility with existing tests
     * and integrations.</p>
     *
     * @param encodedKeys Base64-encoded X.509 public keys
     * @return immutable list of decoded public keys
     * @throws GeneralSecurityException if the list or any key is invalid
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
     * Decodes key configurations and attaches their lifecycle statuses.
     *
     * <p>Package-private to allow isolated lifecycle tests without
     * changing the production trust configuration.</p>
     *
     * @param configurations pinned key configurations
     * @return immutable list of entries, including revoked entries
     * @throws GeneralSecurityException if configuration is invalid
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
     * Source-level configuration for one pinned public key.
     *
     * <p>Package-private for isolated tests.</p>
     */
    record PinnedKeyConfiguration(
            String encodedPublicKey,
            TrustedKeyStatus status
    ) {
    }
}
