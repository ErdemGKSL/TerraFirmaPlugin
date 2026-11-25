/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/client/render/blockentity/CrankshaftBlockEntityRenderer.java @ c3b4d9536 (the axle
 *       extension: a ConnectedAxleBlock at pos.relative(face.getCounterClockWise()) while the node is connected, drawn with
 *       its axle's texture)
 */
package dev.tfp.paper.rotation.spin;

import dev.tfp.core.crankshaft.CrankshaftRules;
import dev.tfp.core.crankshaft.CrankshaftRules.Part;
import dev.tfp.core.crankshaft.CrankshaftRules.Piece;
import dev.tfp.core.rotation.Rotation;
import dev.tfp.core.world.BlockPos;
import dev.tfp.core.world.Direction;
import dev.tfp.paper.crankshaft.CrankRotation;
import dev.tfp.paper.crankshaft.CrankshaftBlocks;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.pump.PumpBlockStates;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

/**
 * What {@link SpinCranks} draws, read from the world (PAR-MECH-26). Every reading is a snapshot on the base's region thread
 * handed to the cranks (no world writes): the base's facing, its shaft in front (and a steel pump of its facing in front of
 * that), the rotation the network decided for it
 * ({@link CrankRotation}, never blocks) and the stub's axle item (the spinning shafts' item of the axle on the sink face).
 *
 * <ul>
 *   <li>A chunk load reads the bases the crankshaft module lists ({@code CrankshaftChunkIndex}); its unload forgets them.</li>
 *   <li>A placed base is read two ticks later (after the rotation module set its facing); a broken piece's base one tick
 *       later.</li>
 *   <li>Every {@link #SWEEP_TICKS} ticks each known base is read again on its region thread (one task per base, as the
 *       crankshaft's own run): speed changes, a shaft placed with a rod, an axle next to it and any other removal show
 *       within a second.</li>
 * </ul>
 */
public final class CrankFeed {
    public static final long SWEEP_TICKS = 20L;

    private final Scheduler scheduler;
    private final CustomBlocks blocks;
    private final CrankRotation rotations;
    private final SpinCranks cranks;
    private final BiFunction<UUID, BlockPos, Optional<String>> axleItems;
    private final Function<UUID, World> worlds;

    /**
     * @param axleItems the spinning display item of the axle at a position ({@link SpinShafts#item}), any thread
     */
    public CrankFeed(Scheduler scheduler, CustomBlocks blocks, CrankRotation rotations, SpinCranks cranks,
            BiFunction<UUID, BlockPos, Optional<String>> axleItems, Function<UUID, World> worlds) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.blocks = Objects.requireNonNull(blocks, "blocks");
        this.rotations = Objects.requireNonNull(rotations, "rotations");
        this.cranks = Objects.requireNonNull(cranks, "cranks");
        this.axleItems = Objects.requireNonNull(axleItems, "axleItems");
        this.worlds = Objects.requireNonNull(worlds, "worlds");
    }

    /**
     * The stub's item for an axle's spinning display item: the plain axle of the same wood (TFC draws the extension with the
     * axle's texture, also next to a bladed axle); empty for anything else.
     */
    public static Optional<String> stubItem(String axleDisplayItem) {
        if (axleDisplayItem == null) {
            return Optional.empty();
        }
        if (axleDisplayItem.startsWith(SpinVisuals.BLADED_RENDER_PREFIX)
            && axleDisplayItem.length() > SpinVisuals.BLADED_RENDER_PREFIX.length()) {
            return Optional.of(SpinVisuals.RENDER_PREFIX + axleDisplayItem.substring(SpinVisuals.BLADED_RENDER_PREFIX.length()));
        }
        return axleDisplayItem.startsWith(SpinVisuals.RENDER_PREFIX) && axleDisplayItem.length() > SpinVisuals.RENDER_PREFIX.length()
            ? Optional.of(axleDisplayItem) : Optional.empty();
    }

    /** The listed bases of a loaded chunk. Region thread of the chunk. */
    public void chunkLoaded(Chunk chunk, List<BlockPos> bases) {
        World world = chunk.getWorld();
        bases.forEach(pos -> check(world, pos));
    }

    /** The bases of an unloading chunk go. Region thread of the chunk. */
    public void chunkUnloaded(Chunk chunk) {
        UUID world = chunk.getWorld().getUID();
        cranks.inChunk(world, chunk.getX(), chunk.getZ()).forEach(pos -> cranks.remove(world, pos));
    }

    /** Reads every known base again, each on its region thread. Global scheduler. */
    public void sweep() {
        for (UUID id : cranks.worlds()) {
            World world = worlds.apply(id);
            if (world == null) {
                cranks.bases(id).forEach(pos -> cranks.remove(id, pos));
                continue;
            }
            for (BlockPos pos : cranks.bases(id)) {
                scheduler.region(world, pos.x() >> 4, pos.z() >> 4, () -> check(world, pos));
            }
        }
    }

    /** Reads the base at {@code base} and hands it to the cranks (or drops it). Region thread of the block. */
    public void check(World world, BlockPos base) {
        UUID id = world.getUID();
        if (!world.isChunkLoaded(base.x() >> 4, base.z() >> 4)) {
            cranks.remove(id, base);
            return;
        }
        Optional<Piece> piece = pieceAt(world, base);
        if (piece.isEmpty() || piece.get().part() != Part.BASE) {
            cranks.remove(id, base);
            return;
        }
        Direction facing = piece.get().facing();
        BlockPos shaftPos = CrankshaftRules.partner(base, piece.get());
        boolean shaft = pieceAt(world, shaftPos).filter(new Piece(Part.SHAFT, facing)::equals).isPresent();
        boolean pump = shaft && pumpAt(world, shaftPos.offset(facing), facing);
        Optional<Rotation> rotation = rotations.rotation(id, base);
        CrankReport report = rotation.map(r -> new CrankReport(base, facing, shaft, pump, true, r.direction(), r.speed(),
            axleItems.apply(id, base.offset(CrankshaftRules.sinkFace(facing))).flatMap(CrankFeed::stubItem).orElse(null)))
            .orElseGet(() -> CrankReport.resting(base, facing, shaft, pump));
        cranks.crank(id, report);
    }

    /** The crankshaft piece at {@code pos} when this thread owns its loaded chunk. */
    private Optional<Piece> pieceAt(World world, BlockPos pos) {
        int cx = pos.x() >> 4;
        int cz = pos.z() >> 4;
        if (!world.isChunkLoaded(cx, cz) || !scheduler.isOwner(world, cx, cz)) {
            return Optional.empty();
        }
        return blocks.stateAt(world.getBlockAt(pos.x(), pos.y(), pos.z())).flatMap(CrankshaftBlocks::piece);
    }

    /** TFC: a steel pump in front of the shaft whose {@code FACING} is the crank's facing. */
    private boolean pumpAt(World world, BlockPos pos, Direction facing) {
        int cx = pos.x() >> 4;
        int cz = pos.z() >> 4;
        if (!world.isChunkLoaded(cx, cz) || !scheduler.isOwner(world, cx, cz)) {
            return false;
        }
        return blocks.stateAt(world.getBlockAt(pos.x(), pos.y(), pos.z()))
            .filter(s -> s.id().equals(PumpBlockStates.PUMP) && PumpBlockStates.facing(s) == facing).isPresent();
    }

    static BlockPos pos(Block block) {
        return new BlockPos(block.getX(), block.getY(), block.getZ());
    }

    /** Placed bases and broken pieces. */
    public final class Changes implements Listener {
        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onPlace(BlockPlaceEvent event) {
            Block block = event.getBlockPlaced();
            World world = block.getWorld();
            BlockPos pos = pos(block);
            scheduler.regionDelayed(world, pos.x() >> 4, pos.z() >> 4, 2, () -> {
                if (world.isChunkLoaded(pos.x() >> 4, pos.z() >> 4)
                    && pieceAt(world, pos).filter(p -> p.part() == Part.BASE).isPresent()) {
                    check(world, pos);
                }
            });
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onBreak(BlockBreakEvent event) {
            Block block = event.getBlock();
            World world = block.getWorld();
            Optional<Piece> piece = blocks.stateAt(block).flatMap(CrankshaftBlocks::piece);
            if (piece.isEmpty()) {
                return;
            }
            BlockPos at = pos(block);
            BlockPos base = piece.get().part() == Part.BASE ? at : CrankshaftRules.partner(at, piece.get());
            scheduler.regionDelayed(world, base.x() >> 4, base.z() >> 4, 1, () -> check(world, base));
        }
    }
}
