/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/plant/coral/TFCCoralPlantBlock.java,
 *       CoralWallFanBlock.java, TFCSeaPickleBlock.java @ c3b4d9536 (updateShape: an unsupported block is destroyed with drops)
 */
package dev.tfp.paper.coral;

import dev.tfp.core.neighbour.Neighbourhood;
import dev.tfp.core.world.Direction;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockSupport;
import org.bukkit.block.data.BlockData;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPhysicsEvent;

/**
 * PAR-WORLD-50: TFC's coral and sea pickle {@code updateShape} with drops. TFC's {@code updateShape} returns air for an
 * unsupported block, which the level destroys with its drops ({@code updateOrDestroy}); the neighbour runner writes air and
 * drops nothing, so the rule runs here instead: the owner thread snapshots the block, its six neighbours and the face tests
 * of their real block data one tick after the physics update, the CPU pool decides with {@link CoralSupport#decide}, and the
 * owner thread re-validates (same state) and removes it through the custom block, which drops TFC's loot. Requests for one
 * block within a tick coalesce. A CraftEngine support answers by its real server block (the face tests are taken on the
 * owner thread, so they are known).
 */
public final class CoralSupportQueue implements Listener {
    private record Key(UUID world, int x, int y, int z) {}

    /** The owner-thread snapshot: the block, its neighbours by {@link Direction#ordinal()}, and their face answers. */
    record Snapshot(BlockStateRef center, BlockStateRef[] neighbours, Map<String, Boolean> sturdy, Map<String, Boolean> top) {
        /** Pure: whether the block must go (TFC {@code updateShape} returns air). */
        boolean unsupported() {
            final CoralSupport rule = new CoralSupport(new CoralSupport.Faces() {
                @Override
                public Optional<Boolean> sturdy(BlockStateRef state, Direction face) {
                    return Optional.ofNullable(Snapshot.this.sturdy.get(state + "|" + face));
                }

                @Override
                public Optional<Boolean> topNonEmpty(BlockStateRef state) {
                    return Optional.ofNullable(top.get(state.toString()));
                }
            });
            final Neighbourhood<BlockStateRef> n = Neighbourhood.of((dx, dy, dz) -> {
                if (dx == 0 && dy == 0 && dz == 0) {
                    return center;
                }
                for (Direction d : Direction.values()) {
                    if (d.dx() == dx && d.dy() == dy && d.dz() == dz) {
                        return neighbours[d.ordinal()];
                    }
                }
                return null;
            });
            return CoralSupport.AIR.equals(rule.decide(n));
        }
    }

    /** {@code BlockData.isFaceSturdy} (replaceable in tests: MockBukkit does not implement it). */
    @FunctionalInterface
    public interface FaceTest {
        boolean sturdy(BlockData data, BlockFace face, BlockSupport support);
    }

    private final Scheduler scheduler;
    private final CustomBlocks custom;
    private final FaceTest faces;
    private final Map<Key, Boolean> pending = new ConcurrentHashMap<>();

    public CoralSupportQueue(Scheduler scheduler, CustomBlocks custom) {
        this(scheduler, custom, BlockData::isFaceSturdy);
    }

    CoralSupportQueue(Scheduler scheduler, CustomBlocks custom, FaceTest faces) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.custom = Objects.requireNonNull(custom, "custom");
        this.faces = Objects.requireNonNull(faces, "faces");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPhysics(BlockPhysicsEvent event) {
        request(event.getBlock());
    }

    /** Owner thread: checks {@code block} one tick later, if it is a coral or sea pickle. */
    public void request(Block block) {
        final Optional<BlockStateRef> state = custom.stateAt(block);
        if (state.isEmpty() || !CoralStates.IDS.contains(state.get().id())) {
            return;
        }
        final Key key = new Key(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
        if (pending.putIfAbsent(key, Boolean.TRUE) != null) {
            return;
        }
        final World world = block.getWorld();
        final Chunk chunk = block.getChunk();
        scheduler.regionDelayed(world, chunk.getX(), chunk.getZ(), 1, () -> {
            final Optional<Snapshot> snapshot;
            try {
                snapshot = snapshot(block);
            } finally {
                pending.remove(key);
            }
            if (snapshot.isEmpty()) {
                return;
            }
            final Snapshot s = snapshot.get();
            scheduler.cpu(() -> {
                if (s.unsupported()) {
                    scheduler.region(world, chunk.getX(), chunk.getZ(), () -> commit(block, s));
                }
            });
        });
    }

    /** Owner thread. */
    Optional<Snapshot> snapshot(Block block) {
        final Optional<BlockStateRef> center = custom.stateAt(block);
        if (center.isEmpty() || !CoralStates.IDS.contains(center.get().id())) {
            return Optional.empty();
        }
        final BlockStateRef[] neighbours = new BlockStateRef[6];
        final Map<String, Boolean> sturdy = new HashMap<>();
        final Map<String, Boolean> top = new HashMap<>();
        for (Direction d : Direction.values()) {
            final Block b = block.getRelative(d.dx(), d.dy(), d.dz());
            final BlockData data = b.getBlockData();
            final BlockStateRef ref = custom.stateAt(b).orElseGet(() -> BlockStateRef.parse(data.getAsString()));
            neighbours[d.ordinal()] = ref;
            // the face toward the coral: a wall fan asks its wall for the face it faces, a standing coral its floor for UP
            final Direction toward = d.opposite();
            sturdy.put(ref + "|" + toward, faces.sturdy(data, BlockFace.valueOf(toward.name()), BlockSupport.FULL));
            if (d == Direction.DOWN) {
                top.put(ref.toString(), faces.sturdy(data, BlockFace.UP, BlockSupport.FULL)
                    || faces.sturdy(data, BlockFace.UP, BlockSupport.CENTER) || faces.sturdy(data, BlockFace.UP, BlockSupport.RIGID));
            }
        }
        return Optional.of(new Snapshot(center.get(), neighbours, Map.copyOf(sturdy), Map.copyOf(top)));
    }

    /** Owner thread: removes the block with its drops if it still is what was decided about. */
    void commit(Block block, Snapshot s) {
        final Optional<BlockStateRef> now = custom.stateAt(block);
        if (now.isEmpty() || !now.get().equals(s.center())) {
            return;
        }
        custom.remove(block);
    }

    /** The number of checks waiting (tests). */
    int pending() {
        return pending.size();
    }
}
