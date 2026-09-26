package com.canreadit.catalog;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * A chapter in lists. {@code number} is a decimal string without trailing zeros ("12", "12.5").
 */
public record ChapterSummary(String number, @Nullable String title, Instant publishedAt, ChapterAccess access) {}
