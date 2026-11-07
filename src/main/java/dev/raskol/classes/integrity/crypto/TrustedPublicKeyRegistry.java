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
 * <p>Only public keys may be stored here. Private keys must remain
 * outside the repository and be available only to the trusted
 * release-signing process.</p>
 */
public final class TrustedPublicKeyRegistry {

    private static final List<String> PINNED_PUBLIC_KEYS = List.of(
            "MCowBQYDK2VwAyEAESiWqMfemhrSC9x1q+sRz7Lo3cd1KIQSvWGBA/4In6Q="
    );

    private static final List<PublicKey> TRUSTED_KEYS = loadPinnedKeys();

    private TrustedPublicKeyRegistry() {
        throw new UnsupportedOperationException(
                "TrustedPublicKeyRegistry is a utility class"
        );
    }

    /**
     * Returns an immutable list of trusted public keys.
     *
     * @return trusted public keys
     */
    public static List<PublicKey> trustedKeys() {
        return TRUSTED_KEYS;
    }

    /**
     * Checks whether the supplied public key is present in the
     * pinned trust registry.
     *
     * @param candidate public key to check
     * @return true if the key is trusted
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
     * Returns SHA-256 fingerprints of all pinned public keys.
     *
     * @return immutable list of lowercase hexadecimal fingerprints
     */
    public static List<String> fingerprints() {
        List<String> fingerprints = new ArrayList<>(TRUSTED_KEYS.size());

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

    private static List<PublicKey> loadPinnedKeys() {
        if (PINNED_PUBLIC_KEYS.isEmpty()) {
            throw new ExceptionInInitializerError(
                    "RIPS trusted public key registry is empty"
            );
        }

        List<PublicKey> keys = new ArrayList<>(PINNED_PUBLIC_KEYS.size());

        for (String encodedKey : PINNED_PUBLIC_KEYS) {
            try {
                byte[] decoded = Base64.getDecoder().decode(encodedKey);

                if (!Base64.getEncoder().encodeToString(decoded)
                        .equals(encodedKey)) {
                    throw new GeneralSecurityException(
                            "Non-canonical Base64 public key"
                    );
                }

                PublicKey publicKey =
                        Ed25519SignatureService.decodePublicKey(encodedKey);

                if (publicKey == null || publicKey.getEncoded() == null) {
                    throw new GeneralSecurityException(
                            "Decoded public key is invalid"
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
            } catch (IllegalArgumentException | GeneralSecurityException exception) {
                throw new ExceptionInInitializerError(exception);
            }
        }

        return List.copyOf(keys);
    }
}
