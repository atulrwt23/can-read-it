package com.canreadit.catalog.internal;

enum BrowseSort {
    /** Most recently updated first. */
    UPDATED,
    /** Most recently added first. */
    NEWEST,
    /** Alphabetical. */
    TITLE,
    /** Most viewed in the last 7 days first. */
    POPULAR,
    /** Best match for the search text first. The default when searching; without search text it means UPDATED. */
    RELEVANCE
}
