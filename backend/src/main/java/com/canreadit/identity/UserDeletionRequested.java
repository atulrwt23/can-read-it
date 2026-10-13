package com.canreadit.identity;

import java.time.Instant;
import java.util.UUID;

/** A user asked to delete their account. It is erased at {@code eraseAfter} unless they sign in before then. */
public record UserDeletionRequested(UUID userId, Instant requestedAt, Instant eraseAfter) {}
