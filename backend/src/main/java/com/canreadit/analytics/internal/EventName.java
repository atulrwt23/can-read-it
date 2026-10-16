package com.canreadit.analytics.internal;

/** The events we count. A fixed list, so nothing unexpected (or personal) is stored. */
enum EventName {
    /** A page was shown; {@code path} is its route template, such as {@code /series/[slug]}. */
    PAGE_VIEW,
    SERIES_VIEW,
    CHAPTER_OPEN,
    /** The reader reached the end of a chapter. */
    CHAPTER_FINISH,
    /** A search ran; {@code value} is the number of results. The search text is never sent. */
    SEARCH,
    FOLLOW,
    /** Counted by the API when an account is created; browsers can't send it. */
    SIGN_UP;

    boolean fromBrowser() {
        return this != SIGN_UP;
    }

    boolean aboutSeries() {
        return this == SERIES_VIEW || this == CHAPTER_OPEN || this == CHAPTER_FINISH || this == FOLLOW;
    }
}
