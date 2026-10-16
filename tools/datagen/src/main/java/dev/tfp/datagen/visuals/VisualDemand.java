/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.visuals;

import dev.tfp.datagen.CeBlockStates.ModelRef;
import java.util.List;

/**
 * One appearance of one emitted CE block, as read from its TFC blockstate: the block id (tfp path, e.g.
 * {@code rock/raw/granite}), the appearance name ({@code ""} for a single-state block, else e.g. {@code axis_x})
 * and the weighted/rotated models. This is the allocator's demand before deduplication and profile sharing.
 */
public record VisualDemand(String blockId, String appearance, List<ModelRef> models) {
    public VisualDemand {
        models = List.copyOf(models);
    }

    /** Unique key: {@code blockId} or {@code blockId#appearance}. */
    public String key() {
        return appearance.isEmpty() ? blockId : blockId + "#" + appearance;
    }
}
