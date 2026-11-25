/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/rotation/FluidPipeBlock.java @ c3b4d9536 (updateShape, getStateForPlacement,
 *       updateConnectedSides, connectsToPipeInDirection),
 *       src/main/java/net/dries007/tfc/common/blocks/rotation/FluidPumpBlock.java @ c3b4d9536 (getStateForPlacement, onRemove)
 */
package dev.tfp.paper.pump;

import dev.tfp.core.persist.VersionedCodec;
import dev.tfp.core.world.BlockPos;
import dev.tfp.core.world.Direction;
import dev.tfp.paper.device.DeviceContext;
import dev.tfp.paper.device.DeviceHandler;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceResult;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.util.Vector;

/**
 * The device kinds of the pipe and the pump, and the placement listener. Every callback runs on the block's region thread.
 *
 * <p>Pipe connections are "decide async, apply sync" in the small: a neighbour change snapshots the six neighbours' kinds,
 * the pure {@link PumpBlockStates#connections} decides, and a changed state is written back through {@link #write}. The snapshot
 * reads only blocks the thread owns (a pipe across a Folia region border does not connect until its own side updates).
 */
public final class PumpDevices {
    /** The record state of both blocks: none (TFC keeps no data in the pump's block entity). */
    static final VersionedCodec<Boolean> CODEC = VersionedCodec.<Boolean>builder("pump", 1)
        .build((v, out) -> out.putInt("v", 0), in -> Boolean.TRUE);

    private final Scheduler scheduler;
    private final CustomBlocks blocks;
    private final BlockWriter writer;
    private final PumpService service;
    private final PumpChunkIndex index;

    public PumpDevices(Scheduler scheduler, CustomBlocks blocks, BlockWriter writer, PumpService service,
            PumpChunkIndex index) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.blocks = Objects.requireNonNull(blocks, "blocks");
        this.writer = Objects.requireNonNull(writer, "writer");
        this.service = Objects.requireNonNull(service, "service");
        this.index = Objects.requireNonNull(index, "index");
    }

    public List<DeviceKind<Boolean>> kinds() {
        return List.of(kind(PumpBlockStates.PIPE, new PipeHandler()), kind(PumpBlockStates.PUMP, new PumpHandler()));
    }

    private static DeviceKind<Boolean> kind(String id, DeviceHandler<Boolean> handler) {
        return DeviceKind.builder(id, CODEC, Boolean.TRUE).removal(DeviceKind.RemoveBehaviour.NOOP).handler(handler).build();
    }

    /** Writes {@code next} over the block now if it still holds a state of the same block. Region thread. */
    void write(Block block, BlockStateRef next) {
        Optional<BlockStateRef> now = blocks.stateAt(block);
        if (now.isEmpty() || !now.get().id().equals(next.id()) || now.get().equals(next)) {
            return;
        }
        writer.set(block, next);
    }

    /** TFC {@code connectsToPipeInDirection} for one neighbour: a pipe, or a pump above. Region thread. */
    boolean connects(Block neighbour, Direction direction) {
        if (!scheduler.isOwner(neighbour.getWorld(), neighbour.getX() >> 4, neighbour.getZ() >> 4)
            || !neighbour.getWorld().isChunkLoaded(neighbour.getX() >> 4, neighbour.getZ() >> 4)) {
            return false;
        }
        Optional<BlockStateRef> state = blocks.stateAt(neighbour);
        if (state.isEmpty()) {
            return false;
        }
        String id = state.get().id();
        return id.equals(PumpBlockStates.PIPE) || id.equals(PumpBlockStates.PUMP) && direction == Direction.UP;
    }

    /** TFC {@code updateConnectedSides}. Region thread. */
    BlockStateRef connected(Block block, BlockStateRef state, Direction preferred) {
        Set<Direction> connecting = EnumSet.noneOf(Direction.class);
        for (Direction d : Direction.values()) {
            if (connects(block.getRelative(d.dx(), d.dy(), d.dz()), d)) {
                connecting.add(d);
            }
        }
        return PumpBlockStates.withFaces(state, PumpBlockStates.connections(connecting, PumpBlockStates.faces(state), preferred));
    }

    private final class PipeHandler implements DeviceHandler<Boolean> {
        @Override
        public DeviceResult<Boolean> neighbourChanged(DeviceContext<Boolean> ctx) {
            write(ctx.block(), connected(ctx.block(), ctx.state(), null));
            return DeviceResult.pass();
        }
    }

    private final class PumpHandler implements DeviceHandler<Boolean> {
        @Override
        public DeviceResult<Boolean> placed(DeviceContext<Boolean> ctx) {
            index.track(ctx.block());
            service.start(ctx.block());
            return DeviceResult.pass();
        }

        @Override
        public void removed(DeviceContext<Boolean> ctx) {
            index.untrack(ctx.block());
            service.removed(ctx.block(), ctx.state());
        }
    }

    /**
     * TFC {@code getStateForPlacement} of both blocks. CraftEngine places the default state; one tick after the placement event
     * the pipe takes its connections (the look direction when none connect) and the fluid of the block it replaced, and the pump
     * takes the player's horizontal direction as its facing. A pump placed over a pipe's top connects it, so the pipe below is
     * updated too.
     */
    public final class Placement implements Listener {
        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onPlace(BlockPlaceEvent event) {
            Block block = event.getBlockPlaced();
            boolean wet = event.getBlockReplacedState().getType() == Material.WATER;
            Player player = event.getPlayer();
            Direction look = nearestLooking(player.getEyeLocation().getDirection());
            Direction horizontal = horizontal(player.getLocation().getYaw());
            scheduler.region(block.getLocation(), () -> placed(block, wet, look, horizontal));
        }
    }

    void placed(Block block, boolean wet, Direction look, Direction horizontal) {
        Optional<BlockStateRef> state = blocks.stateAt(block);
        if (state.isEmpty()) {
            return;
        }
        if (state.get().id().equals(PumpBlockStates.PIPE)) {
            BlockStateRef next = connected(block, state.get(), look)
                .with(PumpBlockStates.FLUID, wet ? PumpBlockStates.WATER : PumpBlockStates.EMPTY);
            write(block, next);
        } else if (state.get().id().equals(PumpBlockStates.PUMP)) {
            write(block, state.get().with(PumpBlockStates.FACING, PumpBlockStates.name(horizontal)));
            Block below = block.getRelative(0, -1, 0);
            blocks.stateAt(below).filter(s -> s.id().equals(PumpBlockStates.PIPE))
                .ifPresent(s -> write(below, connected(below, s, null)));
        }
    }

    /** Vanilla {@code Direction.orderedByNearest(entity)[0]}: the axis of the largest component of the look vector. */
    static Direction nearestLooking(Vector v) {
        double ax = Math.abs(v.getX());
        double ay = Math.abs(v.getY());
        double az = Math.abs(v.getZ());
        if (ay >= ax && ay >= az) {
            return v.getY() >= 0 ? Direction.UP : Direction.DOWN;
        }
        if (ax >= az) {
            return v.getX() >= 0 ? Direction.EAST : Direction.WEST;
        }
        return v.getZ() >= 0 ? Direction.SOUTH : Direction.NORTH;
    }

    /** Vanilla {@code Direction.fromYRot}: {@code floor(yaw / 90 + 0.5) & 3} is south, west, north, east. */
    static Direction horizontal(float yaw) {
        return switch (((int) Math.floor(yaw / 90.0 + 0.5)) & 3) {
            case 0 -> Direction.SOUTH;
            case 1 -> Direction.WEST;
            case 2 -> Direction.NORTH;
            default -> Direction.EAST;
        };
    }

    static BlockPos pos(Block b) {
        return new BlockPos(b.getX(), b.getY(), b.getZ());
    }
}
