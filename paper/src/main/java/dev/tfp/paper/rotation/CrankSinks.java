/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/rotation/CrankshaftBlockEntity.java @ c3b4d9536 (onLoadAdditional:
 *       ADD of the base's SinkNode, onUnloadAdditional / onRemove: REMOVE; getRotationNode().rotation()),
 *       src/main/java/net/dries007/tfc/common/blocks/rotation/CrankshaftBlock.java @ c3b4d9536 (getStateForPlacement)
 */
package dev.tfp.paper.rotation;

import dev.tfp.core.crankshaft.CrankshaftRules;
import dev.tfp.core.rotation.MechanicalBlock;
import dev.tfp.core.rotation.Rotation;
import dev.tfp.core.world.BlockPos;
import dev.tfp.core.world.Direction;
import dev.tfp.paper.crankshaft.CrankRotation;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.BlockVisuals;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;

/**
 * PAR-MECH-21: the crankshaft base as a sink of the rotation network, and the {@link CrankRotation} service the crankshaft
 * module reads. The crankshaft's device kind belongs to that module, so the base reaches the network from here:
 *
 * <ul>
 *   <li>its placement ({@link Placement}: one tick after the place event the base takes TFC's facing, the clockwise side of the
 *       player's horizontal look, and joins),</li>
 *   <li>every other access to it (the device service's access hook for {@code tfp:crankshaft}: a rod use, a neighbour update,
 *       its removal),</li>
 *   <li>its first read ({@link #rotation} from the base's own region thread, which the powered bellows task makes every
 *       second: a base of a chunk that loaded, or that was placed without a player, joins then),</li>
 *   <li>and its chunk's unload ({@link #chunkUnloaded}, TFC {@code onUnloadAdditional}).</li>
 * </ul>
 *
 * Every refresh only snapshots the block and hands its state to the network actor; the rotation read returns what the actor
 * published after its last batch ({@link RotationService#rotation}) and never blocks.
 */
public final class CrankSinks implements CrankRotation {
    private final RotationService networks;
    private final CustomBlocks blocks;
    private final BiConsumer<Block, BlockStateRef> writer;
    private final Scheduler scheduler;
    private final Function<UUID, World> worlds;
    /** The bases handed to the network and their sink state, per world. */
    private final Map<UUID, Map<BlockPos, MechanicalBlock.State>> known = new ConcurrentHashMap<>();

    /**
     * @param writer writes a crankshaft's own new state in place, firing no callback (region thread; {@link #visualsWriter} live)
     */
    public CrankSinks(RotationService networks, CustomBlocks blocks, BiConsumer<Block, BlockStateRef> writer, Scheduler scheduler,
            Function<UUID, World> worlds) {
        this.networks = Objects.requireNonNull(networks, "networks");
        this.blocks = Objects.requireNonNull(blocks, "blocks");
        this.writer = Objects.requireNonNull(writer, "writer");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.worlds = Objects.requireNonNull(worlds, "worlds");
    }

    /** The live writer: the block data of the state, set without physics (as {@link PaperRotationWorld#setState}). */
    public static BiConsumer<Block, BlockStateRef> visualsWriter(Supplier<BlockVisuals> visuals) {
        Objects.requireNonNull(visuals, "visuals");
        return (block, state) -> {
            Optional<BlockData> data = visuals.get().blockData(state);
            data.ifPresent(d -> block.setBlockData(d, false));
        };
    }

    /** The sink state of a crankshaft block state: present for a base, empty for a shaft or any other block. */
    static Optional<MechanicalBlock.State> sink(BlockStateRef state) {
        if (!state.id().equals(RotationBlockStates.CRANKSHAFT)) {
            return Optional.empty();
        }
        return RotationBlockStates.parse(state, 0);
    }

    /**
     * Hands the block at {@code block} to the network as it is now: a base joins (or stays), anything else that was a base
     * here leaves. Region thread of the block.
     */
    public void refresh(Block block) {
        UUID world = block.getWorld().getUID();
        BlockPos pos = RotationDevices.pos(block);
        Optional<MechanicalBlock.State> now = blocks.stateAt(block).flatMap(CrankSinks::sink);
        Map<BlockPos, MechanicalBlock.State> inWorld = known.computeIfAbsent(world, w -> new ConcurrentHashMap<>());
        if (now.isPresent()) {
            if (!now.get().equals(inWorld.put(pos, now.get()))) {
                networks.put(world, pos, now.get());
            }
        } else if (inWorld.remove(pos) != null) {
            networks.remove(world, pos);
        }
    }

    /** Whether the base at {@code pos} was handed to the network. Any thread. */
    public boolean known(UUID world, BlockPos pos) {
        Map<BlockPos, MechanicalBlock.State> inWorld = known.get(world);
        return inWorld != null && inWorld.containsKey(pos);
    }

    /**
     * The rotation the base at {@code base} receives on its sink face, as the network decided after its last batch; empty when
     * it is in no network. Any thread, never blocks. Called from the base's own region thread for a base the network does not
     * know yet, it snapshots the block and hands it over (the answer comes with a later read).
     */
    @Override
    public Optional<Rotation> rotation(UUID world, BlockPos base) {
        if (!known(world, base)) {
            World w = worlds.apply(world);
            int cx = base.x() >> 4;
            int cz = base.z() >> 4;
            if (w != null && w.isChunkLoaded(cx, cz) && scheduler.isOwner(w, cx, cz)) {
                refresh(w.getBlockAt(base.x(), base.y(), base.z()));
            }
            return Optional.empty();
        }
        return networks.rotation(world, base);
    }

    /** TFC {@code onUnloadAdditional}: the bases of an unloading chunk leave the network. Region thread of the chunk. */
    public void chunkUnloaded(Chunk chunk) {
        UUID world = chunk.getWorld().getUID();
        Map<BlockPos, MechanicalBlock.State> inWorld = known.get(world);
        if (inWorld == null) {
            return;
        }
        List<BlockPos> gone = inWorld.keySet().stream()
            .filter(p -> p.x() >> 4 == chunk.getX() && p.z() >> 4 == chunk.getZ()).toList();
        gone.forEach(inWorld::remove);
        if (!gone.isEmpty()) {
            networks.removeAll(world, gone);
        }
    }

    /** The access hook for {@code tfp:crankshaft}: after a use, a neighbour update or the removal, refresh the block. */
    public DeviceService.AccessHook hook() {
        return new DeviceService.AccessHook() {
            @Override
            public void after(Block block) {
                refresh(block);
                // a removal may route before the block is air: look again once it is
                scheduler.regionDelayed(block.getWorld(), block.getX() >> 4, block.getZ() >> 4, 1, () -> {
                    if (block.getWorld().isChunkLoaded(block.getX() >> 4, block.getZ() >> 4)) {
                        refresh(block);
                    }
                });
            }
        };
    }

    /**
     * TFC {@code getStateForPlacement}: {@code facing = context.getHorizontalDirection().getClockWise()}. CraftEngine places the
     * default state; one tick after the place event, on the block's region thread, a crankshaft base still in that state takes
     * the facing (a plain block data write: no placement callback runs again) and joins the network.
     */
    public final class Placement implements Listener {
        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onPlace(BlockPlaceEvent event) {
            Block block = event.getBlockPlaced();
            Player player = event.getPlayer();
            Direction look = horizontal(player.getLocation().getYaw());
            scheduler.regionDelayed(block.getWorld(), block.getX() >> 4, block.getZ() >> 4, 1, () -> placed(block, look));
        }
    }

    /** Region thread of the block. */
    void placed(Block block, Direction look) {
        Optional<BlockStateRef> state = blocks.stateAt(block);
        if (state.isEmpty() || sink(state.get()).isEmpty()) {
            return;
        }
        String facing = RotationBlockStates.name(CrankshaftRules.placementFacing(look));
        if (!facing.equals(state.get().properties().get(RotationBlockStates.FACING))) {
            writer.accept(block, state.get().with(RotationBlockStates.FACING, facing));
        }
        refresh(block);
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
}
