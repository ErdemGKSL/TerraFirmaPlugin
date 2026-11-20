/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/plant/coral/LivingCoralPlantBlock.java @ c3b4d9536 (onPlace,
 *       updateShape: tryScheduleDieTick; tick: die when scanForWater fails),
 *       src/main/java/net/dries007/tfc/common/blocks/plant/coral/LivingCoralWallFanBlock.java @ c3b4d9536 (the same, facing
 *       kept), src/main/java/net/dries007/tfc/common/blocks/plant/coral/TFCCoralPlantBlock.java @ c3b4d9536
 *       (tryScheduleDieTick: 60 + nextInt(40) ticks)
 */
package dev.tfp.paper.coral;

import dev.tfp.core.coral.CoralRules;
import dev.tfp.core.world.Direction;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.neighbour.WorldBlocks;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPlaceEvent;

/**
 * Living corals out of water die (TFC {@code tryScheduleDieTick} / {@code tick}), decide async, apply sync: a neighbour
 * change or a placement at a living coral snapshots its own state and its six neighbours on the owner thread; the CPU pool
 * runs {@code scanForWater}; a dry coral gets one region task {@code 60 + nextInt(40)} ticks later (a second request while
 * one waits is dropped, as {@code scheduleTick} of an already scheduled tick); that task re-reads the block, re-runs the
 * pure scan and, when the coral is still living and dry, writes its dead state without physics (TFC flag 2). A neighbour in
 * a chunk this region does not own, or not loaded, is unknown and keeps the coral alive.
 */
public final class CoralDeath implements Listener {
    private record Key(UUID world, int x, int y, int z) {}

    /** The owner-thread snapshot: the coral and its six neighbours, by {@link Direction#ordinal()}. */
    record Snapshot(BlockStateRef center, BlockStateRef[] neighbours) {
        Optional<Boolean> wet() {
            final Boolean[] water = new Boolean[6];
            for (Direction d : Direction.values()) {
                water[d.ordinal()] = CoralStates.water(neighbours[d.ordinal()]);
            }
            return CoralRules.scanForWater(Boolean.TRUE.equals(CoralStates.water(center)), water);
        }
    }

    private final Scheduler scheduler;
    private final CustomBlocks custom;
    private final Supplier<RandomGenerator> random;
    private final Set<Key> pending = ConcurrentHashMap.newKeySet();

    public CoralDeath(Scheduler scheduler, CustomBlocks custom, Supplier<RandomGenerator> random) {
        this.scheduler = scheduler;
        this.custom = custom;
        this.random = random;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPhysics(BlockPhysicsEvent event) {
        check(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        check(event.getBlockPlaced());
    }

    /** A living coral may have lost its water (owner thread): snapshot, decide on the CPU pool, schedule when dry. */
    public void check(Block block) {
        final Optional<BlockStateRef> state = custom.stateAt(block);
        if (state.isEmpty() || !CoralStates.living(state.get())) {
            return;
        }
        final Snapshot snap = snapshot(block, state.get());
        final World world = block.getWorld();
        final int x = block.getX();
        final int y = block.getY();
        final int z = block.getZ();
        scheduler.cpu(() -> {
            if (snap.wet().equals(Optional.of(false))) {
                scheduler.region(world, x >> 4, z >> 4, () -> schedule(world, x, y, z));
            }
        });
    }

    /** {@code scheduleTick(pos, this, 60 + nextInt(40))}, once per position while one waits (region thread). */
    void schedule(World world, int x, int y, int z) {
        final Key key = new Key(world.getUID(), x, y, z);
        if (!pending.add(key)) {
            return;
        }
        scheduler.regionDelayed(world, x >> 4, z >> 4, CoralRules.dieDelay(random.get()), () -> {
            pending.remove(key);
            tick(world.getBlockAt(x, y, z));
        });
    }

    /** TFC {@code tick}: re-validate and die (region thread). */
    void tick(Block block) {
        final Optional<BlockStateRef> state = custom.stateAt(block);
        if (state.isEmpty() || !CoralStates.living(state.get())) {
            return;
        }
        if (snapshot(block, state.get()).wet().equals(Optional.of(false))) {
            CoralStates.dead(state.get()).ifPresent(dead -> custom.place(block, dead, false));
        }
    }

    private Snapshot snapshot(Block block, BlockStateRef center) {
        final World world = block.getWorld();
        final BlockStateRef[] n = new BlockStateRef[6];
        for (Direction d : Direction.values()) {
            final int x = block.getX() + d.dx();
            final int y = block.getY() + d.dy();
            final int z = block.getZ() + d.dz();
            if (y < world.getMinHeight() || y >= world.getMaxHeight()) {
                n[d.ordinal()] = BlockStateRef.of("minecraft:void_air");
            } else if (world.isChunkLoaded(x >> 4, z >> 4) && scheduler.isOwner(world, x >> 4, z >> 4)) {
                n[d.ordinal()] = WorldBlocks.stateOf(world.getBlockAt(x, y, z), custom);
            }
        }
        return new Snapshot(center, n);
    }

    /** Positions waiting for their die tick (tests, debug). */
    int pending() {
        return pending.size();
    }
}
