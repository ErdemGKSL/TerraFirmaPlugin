/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.plant.growth;

import dev.tfp.paper.integration.BlockStateRef;
import java.util.Optional;

/**
 * PAR-FARM-29: the world as the growth and survival rules of the vines, kelp trees, branching cacti and creeping plants see it.
 * Platform-free so the rules are pure and testable; {@link BlockGrowthWorld} reads the live blocks (region thread),
 * {@link CubeSnapshot} a copy of the blocks around one plant (any thread).
 */
public interface GrowthWorld {
    /** The custom block state at a position, empty for a vanilla block or air. */
    Optional<BlockStateRef> custom(int x, int y, int z);

    boolean air(int x, int y, int z);

    /** Whether the block is water (TFC's salt water block, {@code isEmptyWaterBlock}) with no plant in it. */
    boolean water(int x, int y, int z);

    /** {@code BlockState.isFaceSturdy}: whether the block's {@code face} is a full square. */
    boolean sturdy(int x, int y, int z, Face face);

    /** Whether the block is in the block tag ({@code tfc:kelp_branches}, {@code minecraft:leaves}, ...). */
    boolean tagged(int x, int y, int z, String tag);

    /** Whether the block is the vanilla block {@code key} ({@code minecraft:magma_block}). */
    boolean is(int x, int y, int z, String key);

    /** Whether the position is in a loaded chunk ({@code isAreaLoaded}). */
    default boolean loaded(int x, int y, int z) {
        return true;
    }

    int minY();

    /** The exclusive build limit ({@code getMaxBuildHeight}). */
    int maxY();

    /** Places a custom block state; false when the state is unknown or refused. */
    boolean set(int x, int y, int z, BlockStateRef state);

    /** Convenience: the custom block id at a position or null. */
    default String id(int x, int y, int z) {
        return custom(x, y, z).map(BlockStateRef::id).orElse(null);
    }
}
