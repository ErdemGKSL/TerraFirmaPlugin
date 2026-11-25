/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.landslide;

import org.bukkit.block.Block;

/**
 * The world reads and writes a landslide needs (PAR-PHYS-03), so that {@link LandslideService} stays testable on
 * MockBukkit. {@link BukkitLandslideWorld} is the server one. Every method runs on the owner thread of the block.
 */
public interface LandslideWorld {
    /** TFC {@code LandslideRecipe.canLandslide}: a landslide recipe accepts the block, or it is in {@code #can_landslide}. Cheap. */
    boolean canLandslide(Block block);

    /** The block as the landslide rules see it. */
    SlideState.Cell cell(Block block);

    /**
     * Only the kind of the block ({@link SlideState.Cell#kind()}), for the support beam cells of the snapshot (PAR-PHYS-04):
     * the wider support reach is read without tags, recipes or shapes. Owner thread.
     */
    default String kind(Block block) {
        return cell(block).kind();
    }

    /** Whether {@code state} can become a falling block (it has server block data). */
    boolean canFall(String state);

    /** Spawns a falling {@code state} at {@code at} with TFC's landslide damage (0.8 per block, at most 10). */
    boolean spawnFalling(Block at, String state);

    /** TFC {@code level.removeBlock(pos, false)}: air, no drops. */
    void remove(Block block);

    /** TFC {@code level.destroyBlock(pos, true)}: break effect, drops with an empty tool, air. */
    void destroy(Block block);

    /** TFC {@code playSound(DIRT_SLIDE_SHORT, BLOCKS, 0.4, 1.0)} at the block that slid. */
    void slideSound(Block block);
}
