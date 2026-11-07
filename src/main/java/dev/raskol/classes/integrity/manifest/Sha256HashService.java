// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.integrity.manifest;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Provides SHA-256 hashing utilities.
 */
public final class Sha256HashService {

    private static final String ALGORITHM = "SHA-256";

    private static final int BUFFER_SIZE = 8192;

    private Sha256HashService() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Calculates SHA-256 for an in-memory byte array.
     *
     * @param data input data
     * @return lowercase hexadecimal digest
     */
    public static String hash(byte[] data) {
        Objects.requireNonNull(data, "data cannot be null");

        MessageDigest digest = newDigest();
        return toHex(digest.digest(data));
    }

    /**
     * Calculates SHA-256 for an input stream.
     *
     * <p>This method does not close the supplied stream.
     * Stream ownership remains with the caller.</p>
     *
     * @param inputStream input stream
     * @return lowercase hexadecimal digest
     * @throws IOException if reading the stream fails
     */
    public static String hash(InputStream inputStream) throws IOException {
        Objects.requireNonNull(inputStream, "inputStream cannot be null");

        MessageDigest digest = newDigest();
        byte[] buffer = new byte[BUFFER_SIZE];

        int bytesRead;

        while ((bytesRead = inputStream.read(buffer)) != -1) {
            if (bytesRead > 0) {
                digest.update(buffer, 0, bytesRead);
            }
        }

        return toHex(digest.digest());
    }

    private static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance(ALGORITHM);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available in this Java runtime", exception);
        }
    }

    private static String toHex(byte[] digest) {
        return HexFormat.of().formatHex(digest);
    }
}
