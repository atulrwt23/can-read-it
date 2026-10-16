package com.canreadit.media;

/**
 * Stored renditions of an image, all WebP. Pages have only {@link #ORIGINAL} (about 1080 px wide); covers also
 * have smaller widths for cards and lists. A variant an asset lacks resolves to its original.
 */
public enum ImageVariant {
    /** The largest stored rendition: pages up to 1080 px wide, covers up to 1200 px. */
    ORIGINAL,
    /** Covers at 600 px wide: series cards and the series page. */
    MEDIUM,
    /** Covers at 300 px wide: thumbnails (search suggestions, ranked lists). */
    SMALL
}
