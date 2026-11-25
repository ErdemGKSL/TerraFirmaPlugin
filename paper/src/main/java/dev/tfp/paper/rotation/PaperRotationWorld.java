/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/rotation/RotatingBlockEntity.java @ c3b4d9536
 *       (DELAY_FOR_INVALID_IN_NETWORK, destroyIfInvalid),
 *       src/main/java/net/dries007/tfc/common/blocks/rotation/WaterWheelBlock.java @ c3b4d9536 (tick: destroyIfInvalid),
 *       src/main/java/net/dries007/tfc/common/blocks/rotation/WindmillBlock.java @ c3b4d9536 (tick: destroyIfInvalid)
 */
package dev.tfp.paper.rotation;

import dev.tfp.core.device.DeviceRecord;
import dev.tfp.core.world.BlockPos;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.BlockVisuals;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.integration.CustomItems;
import dev.tfp.paper.rotation.spin.SpinVisuals;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.waterwheel.WaterWheelBlocks;
import dev.tfp.paper.windmill.WindmillBlocks;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.bukkit.Chunk;
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;

/**
 * The region-thread side of the rotation network: own-state writes, sounds, TFC's delayed break of an invalid block, and
 * the chunk load and unload hooks. Every method runs on the region thread of the block or chunk it names, except
 * {@link #breakAt}, which the network actor calls and which only schedules.
 */
public final class PaperRotationWorld implements RotationDevices.Ops, RotationService.BreakSink {
    /** TFC {@code RotatingBlockEntity.DELAY_FOR_INVALID_IN_NETWORK}: ticks from the failed action to the break. */
    public static final int DELAY_FOR_INVALID_IN_NETWORK = 4;

    private final Scheduler scheduler;
    private final CustomBlocks blocks;
    private final Supplier<BlockVisuals> visuals;
    private final CustomItems items;
    private final Function<UUID, World> worlds;
    private volatile RotationService networks;
    private volatile DeviceService devices;

    public PaperRotationWorld(Scheduler scheduler, CustomBlocks blocks, Supplier<BlockVisuals> visuals, CustomItems items,
            Function<UUID, World> worlds) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.blocks = Objects.requireNonNull(blocks, "blocks");
        this.visuals = Objects.requireNonNull(visuals, "visuals");
        this.items = Objects.requireNonNull(items, "items");
        this.worlds = Objects.requireNonNull(worlds, "worlds");
    }

    /** Wires the service and the device framework (both built after this sink). */
    public void bind(RotationService networks, DeviceService devices) {
        this.networks = networks;
        this.devices = devices;
    }

    @Override
    public boolean hasNeighborSignal(Block block) {
        return block.isBlockIndirectlyPowered();
    }

    @Override
    public void setState(Block block, BlockStateRef expected, BlockStateRef next) {
        scheduler.regionDelayed(block.getWorld(), block.getX() >> 4, block.getZ() >> 4, 1, () -> {
            Optional<BlockStateRef> now = blocks.stateAt(block);
            if (now.isEmpty() || !now.get().equals(expected)) {
                return; // changed meanwhile: its own callback hands the new state to the network
            }
            Optional<BlockData> data = visuals.get().blockData(next);
            data.ifPresent(d -> block.setBlockData(d, false));
        });
    }

    @Override
    public void placeSound(Block block) {
        block.getWorld().playSound(block.getLocation().add(0.5, 0.5, 0.5), Sound.BLOCK_WOOD_PLACE, SoundCategory.BLOCKS, 1.0f, 0.8f);
    }

    @Override
    public void failSound(Block block) {
        block.getWorld().playSound(block.getLocation().add(0.5, 0.5, 0.5), Sound.ENTITY_ITEM_BREAK, SoundCategory.BLOCKS, 1.0f, 1.0f);
    }

    @Override
    public void breakAt(UUID worldId, BlockPos pos) {
        World world = worlds.apply(worldId);
        if (world == null) {
            return;
        }
        scheduler.regionDelayed(world, pos.x() >> 4, pos.z() >> 4, DELAY_FOR_INVALID_IN_NETWORK,
            () -> destroyIfInvalid(world.getBlockAt(pos.x(), pos.y(), pos.z())));
    }

    /**
     * TFC {@code destroyIfInvalid}: {@code level.destroyBlock(pos, true)}, the break effect and the block's drops (its own
     * item; the creative motor has no loot table), then air. A block that is no longer a rotation block is left alone.
     * PAR-MECH-17: a rejected water wheel drops itself and a rejected windmill its axle (TFC's loot tables); their device
     * removal drops the windmill's blades and stops their samples.
     */
    void destroyIfInvalid(Block block) {
        Optional<BlockStateRef> state = blocks.stateAt(block);
        Optional<Optional<String>> drop = state.flatMap(PaperRotationWorld::dropOf);
        if (drop.isEmpty()) {
            return;
        }
        Location centre = block.getLocation().add(0.5, 0.5, 0.5);
        block.getWorld().playEffect(centre, Effect.STEP_SOUND, block.getBlockData());
        drop.get().flatMap(id -> items.create(id, 1)).ifPresent(stack -> block.getWorld().dropItemNaturally(centre, stack));
        blocks.remove(block);
        RotationService n = networks;
        if (n != null) {
            n.remove(block.getWorld().getUID(), RotationDevices.pos(block));
        }
    }

    /**
     * What a network-invalid block drops when it breaks: empty when it is no block of the network, else the item id (none for
     * the creative motor).
     */
    static Optional<Optional<String>> dropOf(BlockStateRef state) {
        String id = state.id();
        if (RotationBlockStates.block(id).isPresent()) {
            return Optional.of(id.equals(RotationBlockStates.MOTOR) ? Optional.empty() : Optional.of(id));
        }
        if (WaterWheelBlocks.isWheel(id)) {
            return Optional.of(Optional.of(id));
        }
        if (WindmillBlocks.isWindmill(id)) {
            return Optional.of(Optional.of(WindmillBlocks.axleOf(state).id()));
        }
        return Optional.empty();
    }

    /**
     * PAR-MECH-17: after a windmill left the network, the block there may be its axle again ({@code WindmillDevices.update}
     * puts the axle back when the last blade comes out, through {@code CustomBlocks.place}, which fires no device placement).
     * One tick later, on the block's region thread, a rotation block found there is listed and added, as a placement would.
     * Any thread (it only schedules).
     */
    public void reAddAfterSource(UUID worldId, BlockPos pos, RotationChunkIndex index, RotationDevices kinds) {
        World world = worlds.apply(worldId);
        if (world == null) {
            return;
        }
        int cx = pos.x() >> 4;
        int cz = pos.z() >> 4;
        scheduler.regionDelayed(world, cx, cz, 1, () -> {
            if (!world.isChunkLoaded(cx, cz)) {
                return;
            }
            Block block = world.getBlockAt(pos.x(), pos.y(), pos.z());
            Optional<BlockStateRef> state = blocks.stateAt(block);
            if (state.isEmpty() || RotationBlockStates.block(state.get().id()).isEmpty()) {
                return;
            }
            index.track(block);
            kinds.submit(block, state.get(), step(block));
        });
    }

    /** TFC {@code onLoad} ADD / ADD_SOURCE for every listed block of a loaded chunk. Region thread of the chunk. */
    public void chunkLoaded(Chunk chunk, RotationChunkIndex index, RotationDevices kinds) {
        for (BlockPos pos : index.positions(chunk)) {
            Block block = chunk.getWorld().getBlockAt(pos.x(), pos.y(), pos.z());
            Optional<BlockStateRef> state = blocks.stateAt(block);
            if (state.isEmpty() || RotationBlockStates.block(state.get().id()).isEmpty()) {
                index.untrack(block);
                continue;
            }
            kinds.submit(block, state.get(), step(block));
        }
    }

    /**
     * PAR-MECH-11: re-adds the blocks of every chunk of {@code world} that was already loaded when the module enabled (spawn
     * chunks, chunks loaded before the plugin): no chunk load event reaches {@link #chunkLoaded} for them, so their blocks
     * would join a network only at their first interaction or neighbour update. Each chunk is handled on its own region
     * thread; a chunk that unloaded meanwhile is skipped. Any thread (it only schedules).
     */
    public void reAddLoaded(World world, RotationChunkIndex index, RotationDevices kinds) {
        for (Chunk chunk : world.getLoadedChunks()) {
            int cx = chunk.getX();
            int cz = chunk.getZ();
            scheduler.region(world, cx, cz, () -> {
                if (world.isChunkLoaded(cx, cz)) {
                    chunkLoaded(world.getChunkAt(cx, cz), index, kinds);
                }
            });
        }
    }

    /** TFC {@code onChunkUnloaded} REMOVE for every listed block. Region thread of the chunk. */
    public void chunkUnloaded(Chunk chunk, RotationChunkIndex index) {
        RotationService n = networks;
        List<BlockPos> positions = index.positions(chunk);
        if (n != null && !positions.isEmpty()) {
            n.removeAll(chunk.getWorld().getUID(), positions);
        }
    }

    /**
     * PAR-MECH-12: the spinning display's item of the axle at {@code pos} ({@link SpinVisuals#renderItem} of its block id), read
     * on the block's region thread; empty when the world or chunk is gone or the block is no axle. Any thread (it only schedules).
     */
    public void axleItem(UUID worldId, BlockPos pos, Consumer<Optional<String>> done) {
        World world = worlds.apply(worldId);
        if (world == null) {
            done.accept(Optional.empty());
            return;
        }
        scheduler.region(world, pos.x() >> 4, pos.z() >> 4, () -> {
            if (!world.isChunkLoaded(pos.x() >> 4, pos.z() >> 4)) {
                done.accept(Optional.empty());
                return;
            }
            done.accept(blocks.stateAt(world.getBlockAt(pos.x(), pos.y(), pos.z()))
                .flatMap(state -> SpinVisuals.renderItem(state.id())));
        });
    }

    /** The creative motor's stored step at {@code block}, 0 when none. Region thread. */
    int step(Block block) {
        DeviceService d = devices;
        if (d == null) {
            return 0;
        }
        return d.inspect(block).flatMap(DeviceService.Inspection::record).map(DeviceRecord::state)
            .filter(RotationState.class::isInstance).map(s -> ((RotationState) s).step()).orElse(0);
    }
}
