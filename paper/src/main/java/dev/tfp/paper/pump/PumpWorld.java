/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/rotation/PumpBlockEntity.java @ c3b4d9536 (the block predicates;
 *       the world writes of setBlockAndUpdate)
 */
package dev.tfp.paper.pump;

import dev.tfp.core.machine.PumpCell;
import dev.tfp.core.machine.PumpRules;
import dev.tfp.core.world.BlockPos;
import dev.tfp.core.world.Direction;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Levelled;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.block.BlockSupport;

/**
 * The world side of the pump: reads blocks as {@link PumpCell}s and writes the changes {@link PumpRules} returns. Region
 * thread of the pump. A block outside the region the current thread owns reads as a solid wall (Folia may not read it), so a
 * search or flood never reaches across a region border.
 */
public final class PumpWorld implements PumpRules.World {
    /** Water as the pump sees it: vanilla water (TFC {@code Fluids.WATER}); salt and spring water are not told apart. */
    static final String WATER = "water";
    static final String FLOWING_WATER = "flowing_water";

    private final Scheduler scheduler;
    private final CustomBlocks blocks;
    private final BlockWriter writer;
    private final World world;

    public PumpWorld(Scheduler scheduler, CustomBlocks blocks, BlockWriter writer, World world) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.blocks = Objects.requireNonNull(blocks, "blocks");
        this.writer = Objects.requireNonNull(writer, "writer");
        this.world = Objects.requireNonNull(world, "world");
    }

    private boolean owned(BlockPos pos) {
        return scheduler.isOwner(world, pos.x() >> 4, pos.z() >> 4);
    }

    private Block block(BlockPos pos) {
        return world.getBlockAt(pos.x(), pos.y(), pos.z());
    }

    @Override
    public PumpCell cellAt(BlockPos pos) {
        if (!owned(pos) || !world.isChunkLoaded(pos.x() >> 4, pos.z() >> 4)) {
            return PumpCell.solid();
        }
        Block block = block(pos);
        Optional<BlockStateRef> custom = blocks.stateAt(block);
        if (custom.isPresent()) {
            BlockStateRef state = custom.get();
            if (state.id().equals(PumpBlockStates.PIPE)) {
                return PumpCell.steelPipe(PumpBlockStates.faces(state), PumpBlockStates.fluid(state));
            }
            if (state.id().startsWith("tfp:aqueduct")) {
                return PumpCell.aqueductBlock();
            }
            return PumpCell.solid();
        }
        BlockData data = block.getBlockData();
        Material type = data.getMaterial();
        if (type.isAir()) {
            return PumpCell.AIR;
        }
        if (type == Material.WATER && data instanceof Levelled levelled) {
            return levelled.getLevel() == 0 ? PumpCell.source(WATER) : PumpCell.flowing(FLOWING_WATER);
        }
        Set<Direction> sturdy = EnumSet.noneOf(Direction.class);
        for (Direction d : Direction.values()) {
            if (data.isFaceSturdy(face(d), BlockSupport.FULL)) {
                sturdy.add(d);
            }
        }
        if (data instanceof Waterlogged logged) {
            boolean wet = logged.isWaterlogged();
            return new PumpCell(false, wet ? WATER : null, wet, false, false, Set.of(), false, true, sturdy);
        }
        return PumpCell.solid(sturdy);
    }

    /** Writes the changes of one decision, re-reading each block first (the world may have moved on since the decision). */
    public void apply(List<PumpRules.Change> changes) {
        for (PumpRules.Change change : changes) {
            if (owned(change.pos()) && world.isChunkLoaded(change.pos().x() >> 4, change.pos().z() >> 4)) {
                write(block(change.pos()), change.cell());
            }
        }
    }

    private void write(Block block, PumpCell target) {
        Optional<BlockStateRef> custom = blocks.stateAt(block);
        if (custom.isPresent()) {
            if (custom.get().id().equals(PumpBlockStates.PIPE)) {
                String fluid = target.fluid() == null ? PumpBlockStates.EMPTY : PumpBlockStates.WATER;
                BlockStateRef next = custom.get().with(PumpBlockStates.FLUID, fluid);
                if (!next.equals(custom.get())) {
                    writer.set(block, next);
                }
            }
            return; // any other custom block is never replaced
        }
        BlockData now = block.getBlockData();
        if (now instanceof Waterlogged logged && !now.getMaterial().isAir() && now.getMaterial() != Material.WATER) {
            boolean wet = target.fluid() != null;
            if (logged.isWaterlogged() != wet) {
                logged.setWaterlogged(wet);
                block.setBlockData(logged, true);
            }
            return;
        }
        if (!now.getMaterial().isAir() && now.getMaterial() != Material.WATER) {
            return; // became something else since the decision
        }
        if (target.air()) {
            block.setType(Material.AIR, true);
        } else if (target.fluid() != null && target.plainFluid()) {
            block.setType(Material.WATER, true); // a source: Levelled level 0
        }
    }

    static BlockFace face(Direction d) {
        return switch (d) {
            case UP -> BlockFace.UP;
            case DOWN -> BlockFace.DOWN;
            case NORTH -> BlockFace.NORTH;
            case SOUTH -> BlockFace.SOUTH;
            case EAST -> BlockFace.EAST;
            case WEST -> BlockFace.WEST;
        };
    }
}
