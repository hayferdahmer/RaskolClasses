// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.integrity.manifest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class Sha256HashServiceTest {

    private static final String ABC_SHA256 = "ba7816bf8f01cfea414140de5dae2223" + "b00361a396177a9cb410ff61f20015ad";

    @Test
    void shouldCalculateKnownSha256Hash() {
        byte[] data = "abc".getBytes(StandardCharsets.UTF_8);

        assertEquals(ABC_SHA256, Sha256HashService.hash(data));
    }

    @Test
    void shouldHashInputStream() throws IOException {
        byte[] data = "abc".getBytes(StandardCharsets.UTF_8);

        try (ByteArrayInputStream input = new ByteArrayInputStream(data)) {
            assertEquals(ABC_SHA256, Sha256HashService.hash(input));
        }
    }

    @Test
    void shouldNotCloseCallerInputStream() throws IOException {
        byte[] data = "abc".getBytes(StandardCharsets.UTF_8);

        class TrackingInputStream extends ByteArrayInputStream {

            private boolean closed;

            TrackingInputStream(byte[] input) {
                super(input);
            }

            @Override
            public void close() throws IOException {
                closed = true;
                super.close();
            }
        }

        TrackingInputStream input = new TrackingInputStream(data);

        Sha256HashService.hash(input);

        assertFalse(input.closed);
    }

    @Test
    void shouldHashEmptyInput() {
        assertEquals(
                "e3b0c44298fc1c149afbf4c8996fb924" + "27ae41e4649b934ca495991b7852b855",
                Sha256HashService.hash(new byte[0]));
    }

    @Test
    void shouldRejectNullByteArray() {
        assertThrows(NullPointerException.class, () -> Sha256HashService.hash((byte[]) null));
    }

    @Test
    void shouldRejectNullInputStream() {
        assertThrows(NullPointerException.class, () -> Sha256HashService.hash((java.io.InputStream) null));
    }
}
