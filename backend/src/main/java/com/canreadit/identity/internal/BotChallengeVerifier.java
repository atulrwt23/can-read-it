package com.canreadit.identity.internal;

import org.jspecify.annotations.Nullable;

/**
 * Checks the bot challenge token a public form sends (CLAUDE.md section 7). The beta uses Cloudflare Turnstile;
 * another provider is another implementation.
 */
interface BotChallengeVerifier {

    /** Whether the token proves a human; always true when the check is switched off. */
    boolean verify(@Nullable String token, String clientIp);
}
