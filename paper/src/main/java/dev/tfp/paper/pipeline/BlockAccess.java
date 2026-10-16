/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.pipeline;

import dev.tfp.core.world.BlockPos;

/**
 * Reads and writes block states of one world. Owner thread of the position only.
 *
 * @param <S> the state representation the pipeline compares (e.g. Bukkit {@code BlockData}, or our own
 *            state key). Must have value equality.
 */
public interface BlockAccess<S> {
    /** @return the state, or {@code null} if the chunk is not loaded */
    S read(BlockPos pos);

    void write(BlockPos pos, S state);
}
