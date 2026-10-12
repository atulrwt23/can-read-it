package com.canreadit.identity;

import java.time.Instant;
import java.util.UUID;

/** A user proved they own their account's email. */
public record EmailVerified(UUID userId, Instant verifiedAt) {}
