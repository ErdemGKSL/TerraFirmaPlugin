/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/ChannelBlockEntity.java @ c3b4d9536 (isFlowing, notifyBrokenLink's
 *       upstream walk),
 *       src/main/java/net/dries007/tfc/common/blockentities/MoldTableBlockEntity.java @ c3b4d9536 (isFlowing)
 */
package dev.tfp.core.channel;

import dev.tfp.core.world.BlockPos;

/**
 * What the flow checks read from the world: the channel at a position (null when it is none), whether a cell is air and
 * whether it holds a crucible. The caller answers from a snapshot or from the owner thread.
 */
public interface FlowFacts {
    /** The channel state at {@code pos}, or null when no channel is there. */
    ChannelData channel(BlockPos pos);

    boolean isAir(BlockPos pos);

    boolean isCrucible(BlockPos pos);
}
