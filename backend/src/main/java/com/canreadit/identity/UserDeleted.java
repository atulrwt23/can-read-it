package com.canreadit.identity;

import java.time.Instant;
import java.util.UUID;

/**
 * A user's account was erased: identity keeps only a tombstone with the ID. Modules holding personal data keyed by
 * the user (library, progress, comments) delete or anonymize it.
 */
public record UserDeleted(UUID userId, Instant deletedAt) {}
