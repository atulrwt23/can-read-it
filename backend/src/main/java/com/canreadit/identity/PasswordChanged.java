package com.canreadit.identity;

import java.time.Instant;
import java.util.UUID;

/** A user's password was set or replaced. Their other sessions have been revoked. */
public record PasswordChanged(UUID userId, Instant changedAt) {}
