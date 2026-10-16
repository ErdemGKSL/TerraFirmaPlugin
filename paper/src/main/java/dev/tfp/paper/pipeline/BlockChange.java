/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.pipeline;

import dev.tfp.core.world.BlockPos;
import java.util.Objects;

/**
 * A decided change: write {@code next} at {@code pos} if the block still is {@code expectedOld}.
 * {@code snapshotNanos} is when the deciding snapshot was captured: when two decisions target the
 * same position, the one from the newer snapshot wins.
 */
public record BlockChange<S>(BlockPos pos, S expectedOld, S next, long snapshotNanos) {
    public BlockChange {
        Objects.requireNonNull(pos);
        Objects.requireNonNull(next);
    }
}
