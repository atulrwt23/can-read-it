package com.canreadit.media;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/** Facts about stored assets, so other modules can check an upload before referencing it. */
public interface MediaAssets {

    /** The given assets that exist; unknown IDs are absent. */
    Map<UUID, AssetInfo> find(Collection<UUID> assetIds);

    /**
     * @param ready whether the asset finished processing and can be shown
     */
    record AssetInfo(UUID id, AssetKind kind, int width, int height, boolean ready) {}
}
