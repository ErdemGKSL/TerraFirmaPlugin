/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.collapse;

import org.bukkit.block.Block;

/**
 * The world reads and writes a collapse needs (PAR-PHYS-02), so that {@link CollapseService} stays testable on MockBukkit.
 * {@link BukkitCollapseWorld} is the server one. Every method runs on the owner thread of the block.
 */
public interface CollapseWorld {
    /** {@code #can_trigger_collapse}: mining this block may start a collapse. Cheap; called for every block broken. */
    boolean canTrigger(Block block);

    /** The block as the collapse rules see it. */
    CollapseState.Cell cell(Block block);

    /** Whether {@code state} can become a falling block (it has server block data). */
    boolean canFall(String state);

    /** Spawns a falling {@code state} at {@code at} with TFC's collapse damage (2 per block, at most 20). */
    boolean spawnFalling(Block at, String state);

    /** TFC {@code setBlockAndUpdate} followed by the falling block taking the block: air, no drops. */
    void remove(Block block);

    /** TFC {@code level.destroyBlock(pos, true)}: break effect, drops with an empty tool, air. */
    void destroy(Block block);

    /** A sound of the collapse at {@code block}. */
    void sound(Block block, CollapseSound sound);

    /** TFC's fake collapse shows {@code LevelEvent.PARTICLES_DESTROY_BLOCK} of the block at each displayed start. */
    void fakeStart(Block block);

    /** The sounds TFC plays (volume, pitch). */
    enum CollapseSound {
        /** {@code ROCK_SLIDE_LONG} at the mined block when a real collapse starts, 1.0 / 1.0. */
        LONG(1.0f),
        /** {@code ROCK_SLIDE_LONG_FAKE} at the mined block for a fake collapse, 1.0 / 1.0. */
        LONG_FAKE(1.0f),
        /** {@code ROCK_SLIDE_SHORT} at the collapse centre for every propagation step that dropped blocks, 0.6 / 1.0. */
        SHORT(0.6f);

        public final float volume;

        CollapseSound(float volume) {
            this.volume = volume;
        }
    }
}
