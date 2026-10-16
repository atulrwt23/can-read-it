package com.canreadit.shared;

import java.time.LocalDate;
import java.util.Optional;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Anonymous visitors, for counting (CLAUDE.md section 2, "View counting"). A visitor is a keyed hash of request
 * details such as the IP and user agent, with a random key that changes every UTC day and then expires: it can't be
 * reversed or linked across days, and nothing personal is stored.
 */
public interface Visitors {

    /** Crawlers and link previews are not readers. */
    Pattern BOTS =
            Pattern.compile("bot|crawl|spider|slurp|preview|facebookexternalhit|headless", Pattern.CASE_INSENSITIVE);

    static boolean isBot(@Nullable String userAgent) {
        return userAgent == null
                || userAgent.isBlank()
                || BOTS.matcher(userAgent).find();
    }

    /**
     * The day's fingerprint of these details (for example IP and user agent), or empty if the key store is
     * unavailable: counting then skips the visitor rather than failing the request.
     */
    Optional<String> fingerprint(LocalDate day, String details);
}
