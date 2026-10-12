package com.canreadit.identity.internal;

import java.time.Instant;

/** A signed-in session: what the cookies (or a mobile client) carry. */
record SignedIn(UserAccount user, AccessTokens.Issued access, String refreshToken, Instant refreshExpiresAt) {}
