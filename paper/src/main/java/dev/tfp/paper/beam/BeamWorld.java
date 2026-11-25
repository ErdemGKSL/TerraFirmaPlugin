/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/resources/data/tfc/loot_table/blocks/wood/vertical_support/oak.json @ c3b4d9536 (a beam drops its support
 * item), src/main/java/net/dries007/tfc/common/blocks/wood/VerticalSupportBlock.java @ c3b4d9536 (isEmptyOrValidFluid)
 */
package dev.tfp.paper.beam;

import dev.tfp.core.beam.BeamShape;
import dev.tfp.core.world.BlockPos;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.integration.CustomItems;
import dev.tfp.paper.pipeline.BlockAccess;
import dev.tfp.paper.scheduler.ThreadAsserts;
import java.util.Optional;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Predicate;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Levelled;
import org.bukkit.block.BlockSupport;

/**
 * The beam runner's {@link BlockAccess} of one world. Reads a block as a {@link BeamState}; writes a beam state through the
 * {@link CustomBlocks} adapter, and air over a beam as TFC's {@code updateShape} returning air does
 * ({@code Block.updateOrDestroy} destroys it with its drops): the beam is removed and its support item dropped. Owner thread of
 * the position only.
 */
public final class BeamWorld implements BlockAccess<BeamState> {
    /** {@code isFaceSturdy(UP, CENTER)} of the live block (its real server state, so also a CraftEngine block's carrier). */
    public static final Predicate<Block> STURDY_UP = b -> b.getBlockData().isFaceSturdy(BlockFace.UP, BlockSupport.CENTER);

    private final World world;
    private final CustomBlocks blocks;
    private final CustomItems items;
    private final Predicate<Block> sturdyUp;
    final LongAdder drops = new LongAdder();

    public BeamWorld(World world, CustomBlocks blocks, CustomItems items, Predicate<Block> sturdyUp) {
        this.world = world;
        this.blocks = blocks;
        this.items = items;
        this.sturdyUp = sturdyUp;
    }

    /** The beam view of {@code block} (owner thread). */
    public static BeamState read(Block block, CustomBlocks blocks, Predicate<Block> sturdyUp) {
        Optional<BlockStateRef> custom = blocks.stateAt(block);
        if (custom.isPresent()) {
            boolean beam = BeamBlocks.shape(custom.get().id()) != BeamShape.NONE;
            return new BeamState(custom.get(), !beam && sturdyUp.test(block), false);
        }
        BlockData data = block.getBlockData();
        BlockStateRef state = BlockStateRef.parse(data.getAsString());
        return new BeamState(state, sturdyUp.test(block), open(data));
    }

    /** TFC {@code isEmptyOrValidFluid}: air, or a water source (TFC's beams hold water and salt water). */
    static boolean open(BlockData data) {
        Material m = data.getMaterial();
        if (m.isAir()) {
            return true;
        }
        return m == Material.WATER && data instanceof Levelled l && l.getLevel() == 0;
    }

    @Override
    public BeamState read(BlockPos pos) {
        if (!world.isChunkLoaded(pos.chunkX(), pos.chunkZ())) {
            return null;
        }
        if (pos.y() < world.getMinHeight() || pos.y() >= world.getMaxHeight()) {
            return new BeamState(BlockStateRef.of("minecraft:void_air"), false, true);
        }
        ThreadAsserts.owned(world, pos.chunkX(), pos.chunkZ());
        return read(world.getBlockAt(pos.x(), pos.y(), pos.z()), blocks, sturdyUp);
    }

    @Override
    public void write(BlockPos pos, BeamState state) {
        ThreadAsserts.owned(world, pos.chunkX(), pos.chunkZ());
        Block block = world.getBlockAt(pos.x(), pos.y(), pos.z());
        if (BeamBlocks.shape(state.state().id()) != BeamShape.NONE) {
            if (!blocks.place(block, state.state(), false)) {
                throw new IllegalStateException("cannot place " + state.state() + " at " + pos + " (unknown custom block?)");
            }
            return;
        }
        Optional<String> wood = blocks.stateAt(block).flatMap(s -> BeamBlocks.woodOfBlock(s.id()));
        blocks.remove(block);
        if (!block.getType().isAir()) {
            block.setBlockData(Bukkit.createBlockData(state.state().toString()), false);
        }
        wood.flatMap(w -> items.create(BeamBlocks.itemId(w), 1))
            .ifPresent(item -> world.dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), item));
        wood.ifPresent(w -> drops.increment());
    }

    public World world() {
        return world;
    }
}
