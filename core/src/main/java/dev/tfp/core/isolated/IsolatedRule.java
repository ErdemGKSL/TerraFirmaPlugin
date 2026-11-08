/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/tracker/WorldTracker.java @ c3b4d9536
 */
package dev.tfp.core.isolated;

import dev.tfp.core.world.Direction;
import java.util.function.Function;

/**
 * TFC's isolated-block check ({@code WorldTracker.tick} over {@code isolatedPositions}, {@code isIsolated}): a queued
 * position whose block is still in {@code #tfc:breaks_when_isolated} (our {@code #tfp:breaks_when_isolated}, i.e.
 * {@code #c:stones/raw}) and none of whose six face neighbours has a collision shape is destroyed with
 * {@code TFCLoot.ISOLATED} set, so its loot table drops the raw block itself. Pure; any thread.
 */
public final class IsolatedRule {
    /** {@code TFCTags.Blocks.BREAKS_WHEN_ISOLATED}, renamed to our namespace by datagen. */
    public static final String BREAKS_WHEN_ISOLATED = "tfp:breaks_when_isolated";

    private IsolatedRule() {
    }

    /**
     * TFC's {@code isIsolated}: true when every face neighbour has an empty collision shape. A neighbour that is
     * {@code null} is unknown (its chunk is not loaded) and counts as support, so an unloaded border never pops a
     * block.
     */
    public static boolean isIsolated(Function<Direction, IsolatedCell> neighbours) {
        for (Direction d : Direction.values()) {
            IsolatedCell n = neighbours.apply(d);
            if (n == null || n.hasCollision()) {
                return false;
            }
        }
        return true;
    }

    /**
     * The tracker's decision for one queued position: break when the block (still) is in the tag and is isolated.
     *
     * @param centre the block at the queued position; {@code null} when unknown (keeps it)
     */
    public static boolean shouldBreak(IsolatedCell centre, Function<Direction, IsolatedCell> neighbours) {
        return centre != null && centre.breaksWhenIsolated() && isIsolated(neighbours);
    }
}
