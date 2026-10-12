package com.canreadit.identity;

import java.time.Instant;
import java.util.UUID;

/** A new account was created. Published through the Modulith outbox, after the registration commits. */
public record UserRegistered(UUID userId, String handle, Instant registeredAt) {}
