package dev.raskol.classes.integrity.artifact;

import java.io.IOException;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Objects;

/**
 * Validates JAR entry paths and rejects ambiguous or unsafe names.
 *
 * <p>The validator applies portable path rules to prevent traversal,
 * filesystem aliasing and cross-platform path ambiguity.</p>
 */
public final class ArchiveEntryPathValidator {

    private static final int MAX_PATH_LENGTH = 4096;
    private static final int MAX_SEGMENT_LENGTH = 255;

    private ArchiveEntryPathValidator() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Validates an archive entry name.
     *
     * @param entryName raw JAR entry name
     * @param directory whether the entry represents a directory
     * @return validated path without a trailing slash
     * @throws IOException if the path is invalid or ambiguous
     */
    public static String validate(String entryName, boolean directory) throws IOException {
        Objects.requireNonNull(entryName, "entryName");

        if (entryName.isEmpty() || entryName.length() > MAX_PATH_LENGTH) {
            throw new IOException("Archive entry path has an invalid length");
        }

        if (!Normalizer.isNormalized(entryName, Normalizer.Form.NFC)) {
            throw new IOException("Archive entry path is not Unicode NFC-normalized");
        }

        if (entryName.startsWith("/")
                || entryName.startsWith("\\")
                || entryName.contains("\\")
                || entryName.indexOf('\0') >= 0) {
            throw new IOException("Archive entry path is absolute or contains forbidden separators");
        }

        String path = entryName;

        if (directory) {
            if (!path.endsWith("/") || path.endsWith("//")) {
                throw new IOException("Directory entry must have exactly one trailing slash");
            }

            path = path.substring(0, path.length() - 1);
        } else if (path.endsWith("/")) {
            throw new IOException("File entry must not end with a slash");
        }

        if (path.isEmpty() || path.length() > MAX_PATH_LENGTH) {
            throw new IOException("Archive entry path is empty or too long");
        }

        if (path.matches("^[A-Za-z]:.*")) {
            throw new IOException("Drive-qualified archive paths are not allowed");
        }

        String[] segments = path.split("/", -1);

        for (String segment : segments) {
            validateSegment(segment);
        }

        return path;
    }

    /**
     * Produces a portable comparison key for a validated path.
     *
     * <p>This key is used only to detect case-insensitive path collisions.
     * It must not be used as the actual archive entry name.</p>
     *
     * @param validatedPath previously validated path
     * @return lowercase comparison key
     */
    public static String comparisonKey(String validatedPath) {
        Objects.requireNonNull(validatedPath, "validatedPath");
        return validatedPath.toLowerCase(Locale.ROOT);
    }

    private static void validateSegment(String segment) throws IOException {
        if (segment.isEmpty()
                || ".".equals(segment)
                || "..".equals(segment)) {
            throw new IOException("Archive entry contains an invalid path segment");
        }

        if (segment.length() > MAX_SEGMENT_LENGTH) {
            throw new IOException("Archive path segment is too long");
        }

        if (segment.endsWith(".") || segment.endsWith(" ")) {
            throw new IOException("Archive path segment has a non-portable suffix");
        }

        for (int offset = 0; offset < segment.length();) {
            int codePoint = segment.codePointAt(offset);
            offset += Character.charCount(codePoint);

            int type = Character.getType(codePoint);

            if (Character.isISOControl(codePoint)
                    || type == Character.FORMAT
                    || type == Character.SURROGATE) {
                throw new IOException("Archive path contains control or formatting characters");
            }

            if (codePoint == '<'
                    || codePoint == '>'
                    || codePoint == ':'
                    || codePoint == '"'
                    || codePoint == '|'
                    || codePoint == '?'
                    || codePoint == '*') {
                throw new IOException("Archive path contains non-portable characters");
            }
        }

        String deviceName = segment;
        int extensionIndex = deviceName.indexOf('.');

        if (extensionIndex >= 0) {
            deviceName = deviceName.substring(0, extensionIndex);
        }

        String upperName = deviceName.toUpperCase(Locale.ROOT);

        if (upperName.equals("CON")
                || upperName.equals("PRN")
                || upperName.equals("AUX")
                || upperName.equals("NUL")
                || upperName.matches("COM[1-9]")
                || upperName.matches("LPT[1-9]")) {
            throw new IOException("Archive path contains a reserved device name");
        }
    }
}
