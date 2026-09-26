package com.canreadit.shared;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Opaque cursors for keyset pagination. A cursor is a versioned list of string parts, encoded
 * as unpadded base64url. Clients must treat it as opaque.
 */
public final class Cursors {

    private static final String VERSION = "v1";
    private static final String SEPARATOR = "\u001F";
    private static final int MAX_LENGTH = 1024;

    private Cursors() {}

    public static String encode(String... parts) {
        for (String part : parts) {
            if (part.contains(SEPARATOR)) {
                throw new IllegalArgumentException("Cursor part contains the separator");
            }
        }
        String raw = VERSION + SEPARATOR + String.join(SEPARATOR, parts);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /** Decodes a cursor into exactly {@code expectedParts} parts, or fails with 400. */
    public static List<String> decode(String cursor, int expectedParts) {
        if (cursor.length() > MAX_LENGTH) {
            throw invalid();
        }
        String raw;
        try {
            raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw invalid();
        }
        List<String> parts = List.of(raw.split(Pattern.quote(SEPARATOR), -1));
        if (parts.size() != expectedParts + 1 || !parts.getFirst().equals(VERSION)) {
            throw invalid();
        }
        return parts.subList(1, parts.size());
    }

    private static ApiException invalid() {
        return ApiException.badRequest("invalid_cursor", "The cursor is malformed or expired.");
    }
}
