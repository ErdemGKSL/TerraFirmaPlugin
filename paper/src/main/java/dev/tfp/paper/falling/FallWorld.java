/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.falling;

import dev.tfp.core.falling.FallCell;
import dev.tfp.paper.integration.BlockStateRef;
import java.util.Optional;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.LivingEntity;

/**
 * The world reads and writes a falling block needs (PAR-PHYS-01), so that {@link FallingBlocks} stays testable on
 * MockBukkit. {@link BukkitFallWorld} is the server one. Every method runs on the owner thread of the block.
 */
public interface FallWorld {
    /** The cell at {@code block} for a downward fall (the up face's sturdiness). */
    FallCell cell(Block block);

    /** TFC {@code getBlockToughness} of a falling state. */
    int toughness(BlockStateRef falling);

    /** Whether {@code falling} would survive at {@code at} ({@code BlockState.canSurvive}). */
    boolean survives(BlockStateRef falling, Block at);

    /** Whether {@code block} is a moving piston. */
    boolean isMovingPiston(Block block);

    /**
     * The collision boxes of {@code block} under a falling block's footprint (x and z within 0.01..0.99 of the cell), as
     * {@code {minY, maxY}} pairs relative to the block.
     */
    double[][] collisionHeights(Block block);

    /** The server block data that carries {@code state} (the falling entity's block), or empty when unknown. */
    Optional<BlockData> blockData(BlockStateRef state);

    /** The state at {@code block} in our vocabulary (custom first). */
    BlockStateRef stateAt(Block block);

    /** TFC {@code level.destroyBlock(pos, true)}: the break effect, the block's drops with an empty tool, then air. */
    void destroy(Block block);

    /** TFC {@code level.removeBlock(pos, false)}: the block becomes air, no drops (a block that starts to fall). */
    void remove(Block block);

    /** Deals vanilla falling-block damage ({@code damageSources().fallingBlock(source)}) to {@code target}. */
    void hurt(LivingEntity target, float amount, FallingBlock source);

    /** TFC {@code level.setBlockAndUpdate}: places {@code state} with neighbour updates. False when refused. */
    boolean place(Block block, BlockStateRef state);

    /** TFC {@code attemptToDropAsItem}: the state's loot with an empty tool at {@code at}, if entity drops are on. */
    void dropAsItem(Location at, BlockStateRef state);
}
