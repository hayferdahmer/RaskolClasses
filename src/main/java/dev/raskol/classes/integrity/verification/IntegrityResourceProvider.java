
package dev.raskol.classes.integrity.verification;

import java.io.IOException;
import java.io.InputStream;

/**
 * Provides access to resources inside the plugin artifact.
 *
 * <p>Returns null when the requested resource does not exist.
 * The caller owns and must close the returned stream.</p>
 */
@FunctionalInterface
public interface IntegrityResourceProvider {

    /**
     * Opens a resource from the plugin artifact.
     *
     * @param resourcePath absolute classpath resource path
     * @return resource stream, or null when missing
     * @throws IOException if the resource cannot be read
     */
    InputStream openResource(String resourcePath) throws IOException;
}
