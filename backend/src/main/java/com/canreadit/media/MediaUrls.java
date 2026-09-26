package com.canreadit.media;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * The only way other modules turn asset IDs into URLs (ADR 0004). Resolves in one batch; IDs
 * that are unknown, not ready, or protected are absent from the result.
 */
public interface MediaUrls {

    Map<UUID, ImageRef> resolve(Collection<UUID> assetIds, ImageVariant variant);
}
