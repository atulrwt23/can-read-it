package com.canreadit.shared;

import java.util.List;
import org.jspecify.annotations.Nullable;

/** One page of a cursor-paginated list. {@code nextCursor} is null on the last page. */
public record CursorPage<T>(List<T> items, @Nullable String nextCursor) {}
