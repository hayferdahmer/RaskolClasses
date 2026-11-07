// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0

package dev.raskol.classes.integrity.verification;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/**
 * Reads integrity resources through the class loader
 * associated with the specified anchor class.
 */
public final class ClassLoaderIntegrityResourceProvider
        implements IntegrityResourceProvider {

    private final Class<?> anchor;

    public ClassLoaderIntegrityResourceProvider(Class<?> anchor) {
        this.anchor = Objects.requireNonNull(
                anchor,
                "anchor cannot be null"
        );
    }

    @Override
    public InputStream openResource(String resourcePath)
            throws IOException {

        Objects.requireNonNull(
                resourcePath,
                "resourcePath cannot be null"
        );

        if (!resourcePath.startsWith("/")
                || resourcePath.length() < 2) {
            throw new IllegalArgumentException(
                    "Resource path must be absolute"
            );
        }

        return anchor.getResourceAsStream(resourcePath);
    }
}
