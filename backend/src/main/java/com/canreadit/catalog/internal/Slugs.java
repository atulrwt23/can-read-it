package com.canreadit.catalog.internal;

import java.text.Normalizer;
import java.util.Locale;

/** URL names for series: lowercase ASCII words joined by hyphens (the {@code catalog.series} check). */
final class Slugs {

    static final int MAX_LENGTH = 80;

    private Slugs() {}

    /** A slug from a title, or an empty string when the title has no Latin letters or digits (Korean, say). */
    static String fromTitle(String title) {
        String ascii = Normalizer.normalize(title, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT);
        String slug = ascii.replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (slug.length() > MAX_LENGTH) {
            slug = slug.substring(0, MAX_LENGTH).replaceAll("-+$", "");
        }
        return slug;
    }
}
