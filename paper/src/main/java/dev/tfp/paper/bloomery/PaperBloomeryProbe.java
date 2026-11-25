/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/BloomeryBlock.java @ c3b4d9536 (isBloomeryInsulationBlock)
 */
package dev.tfp.paper.bloomery;

import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.tags.Tags;
import java.util.Objects;
import java.util.Optional;
import org.bukkit.World;
import org.bukkit.block.Block;

/** {@link BloomeryProbe} on the live world: loaded chunks of the caller's own region only. */
public final class PaperBloomeryProbe implements BloomeryProbe {
    public static final String INSULATION = "tfc:bloomery_insulation";

    private final CustomBlocks blocks;
    private final Tags tags;
    private final Scheduler scheduler;

    public PaperBloomeryProbe(CustomBlocks blocks, Tags tags, Scheduler scheduler) {
        this.blocks = Objects.requireNonNull(blocks, "blocks");
        this.tags = Objects.requireNonNull(tags, "tags");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public boolean readable(Block block) {
        World w = block.getWorld();
        int cx = block.getX() >> 4;
        int cz = block.getZ() >> 4;
        return block.getY() >= w.getMinHeight() && block.getY() < w.getMaxHeight() && w.isChunkLoaded(cx, cz)
            && scheduler.isOwner(w, cx, cz);
    }

    @Override
    public Optional<String> blockId(Block block) {
        return blocks.stateAt(block).map(BlockStateRef::id);
    }

    @Override
    public boolean air(Block block) {
        return block.getType().isAir() && blocks.stateAt(block).isEmpty();
    }

    @Override
    public boolean insulation(Block block) {
        return tags.is(block, INSULATION);
    }
}
