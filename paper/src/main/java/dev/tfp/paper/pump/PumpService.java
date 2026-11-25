/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/rotation/PumpBlockEntity.java @ c3b4d9536 (serverTick every 40
 *       ticks, onRemoved)
 */
package dev.tfp.paper.pump;

import dev.tfp.core.machine.PumpRules;
import dev.tfp.core.rotation.Rotation;
import dev.tfp.core.world.BlockPos;
import dev.tfp.core.world.Direction;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * Runs the pumps. TFC ticks a pump's block entity every tick and acts when {@code gameTime % 40 == 0}; a pump places and
 * removes fluid in the world, so it cannot be lazy. Each placed or loaded pump gets one repeating region task with a
 * {@link PumpRules#FLOW_PERIOD_TICKS} period (no per-tick work), cancelled when the pump breaks or its chunk unloads.
 *
 * <p>Each run reads the world through {@link PumpWorld} on the pump's region thread, lets the pure {@link PumpRules} decide, and
 * applies the few changes (at most one block per run, as TFC) straight after: the search is bounded (16 pipes, a 32-cell
 * flood), so snapshot, decision and commit run in one region task (ledger divergence).
 */
public final class PumpService {
    private final Scheduler scheduler;
    private final CustomBlocks blocks;
    private final BlockWriter writer;
    private final Supplier<Optional<ShaftSource>> shafts;
    private final Map<String, Scheduler.TaskHandle> running = new ConcurrentHashMap<>();

    public PumpService(Scheduler scheduler, CustomBlocks blocks, BlockWriter writer,
            Supplier<Optional<ShaftSource>> shafts) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.blocks = Objects.requireNonNull(blocks, "blocks");
        this.writer = Objects.requireNonNull(writer, "writer");
        this.shafts = Objects.requireNonNull(shafts, "shafts");
    }

    static String key(UUID world, BlockPos pos) {
        return world + ":" + pos.x() + "," + pos.y() + "," + pos.z();
    }

    /** Starts the pump at {@code block} (a second start of the same pump is ignored). Region thread. */
    public void start(Block block) {
        UUID id = block.getWorld().getUID();
        BlockPos pos = new BlockPos(block.getX(), block.getY(), block.getZ());
        running.computeIfAbsent(key(id, pos), k -> scheduler.regionRepeating(block.getWorld(), pos.x() >> 4, pos.z() >> 4,
            PumpRules.FLOW_PERIOD_TICKS, PumpRules.FLOW_PERIOD_TICKS, () -> run(block.getWorld(), pos)));
    }

    /** Stops the pump at {@code pos}. */
    public void stop(UUID world, BlockPos pos) {
        Scheduler.TaskHandle handle = running.remove(key(world, pos));
        if (handle != null) {
            handle.cancel();
        }
    }

    public int running() {
        return running.size();
    }

    /** One run of the pump at {@code pos}. Region thread. */
    void run(World world, BlockPos pos) {
        Block block = world.getBlockAt(pos.x(), pos.y(), pos.z());
        Optional<BlockStateRef> state = blocks.stateAt(block);
        if (state.isEmpty() || !state.get().id().equals(PumpBlockStates.PUMP)) {
            stop(world.getUID(), pos); // not a pump any more
            return;
        }
        Direction facing = PumpBlockStates.facing(state.get());
        PumpWorld reader = new PumpWorld(scheduler, blocks, writer, world);
        List<PumpRules.Change> changes = PumpRules.tick(reader, pos, facing, shaft(block, facing), 0L);
        reader.apply(changes);
    }

    /** The crankshaft rotation behind the pump, or {@code null}. */
    Rotation shaft(Block block, Direction facing) {
        return shafts.get().map(s -> s.rotationAt(block, facing.opposite())).orElse(null);
    }

    /** TFC {@code PumpBlockEntity.onRemoved}: removes the fluid the pump placed. {@code removed} is the state that was removed. */
    public void removed(Block block, BlockStateRef removed) {
        stop(block.getWorld().getUID(), new BlockPos(block.getX(), block.getY(), block.getZ()));
        PumpWorld reader = new PumpWorld(scheduler, blocks, writer, block.getWorld());
        BlockPos pos = new BlockPos(block.getX(), block.getY(), block.getZ());
        reader.apply(PumpRules.tick(reader, pos, PumpBlockStates.facing(removed), null, 0L));
    }
}
