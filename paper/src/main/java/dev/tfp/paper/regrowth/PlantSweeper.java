/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/plant/PlantRegrowth.java @ c3b4d9536 (spreadSelf),
 *       src/main/java/net/dries007/tfc/common/blocks/plant/ShortGrassBlock.java @ c3b4d9536 (randomTick),
 *       src/main/java/net/dries007/tfc/common/blocks/plant/TFCTallGrassBlock.java @ c3b4d9536 (randomTick, placeTwoHalves)
 */
package dev.tfp.paper.regrowth;

import dev.tfp.core.calendar.CalendarTicks;
import dev.tfp.core.calendar.Month;
import dev.tfp.core.calendar.Season;
import dev.tfp.core.climate.OverworldClimate;
import dev.tfp.core.climate.Solar;
import dev.tfp.core.clock.ClockSnapshot;
import dev.tfp.core.regrowth.PlantKinds;
import dev.tfp.core.regrowth.PlantRegrowth;
import dev.tfp.core.regrowth.PlantSweepRules;
import dev.tfp.core.regrowth.PlantSweepRules.Cell;
import dev.tfp.core.regrowth.PlantSweepRules.Spread;
import dev.tfp.core.regrowth.SpreadKind;
import dev.tfp.core.world.BlockPos;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.tags.Tags;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;
import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.persistence.PersistentDataType;

/**
 * Sweeps a loaded chunk's spreading plants the way TFC's random ticks would have over the time since the last sweep
 * ({@link PlantSweepRules}). "Decide async, apply sync": the region thread snapshots the plants and the free cells at the surface
 * ({@link #snapshot}), the CPU pool decides, and the region thread places the new plants in batches of {@link #BATCH} per tick,
 * re-validating each cell. The time since the last sweep is kept in the chunk's persistent data ({@link #STAMP}); a chunk without a stamp is
 * only stamped.
 */
public final class PlantSweeper {
    static final int BATCH = 32;
    static final long MIN_ELAPSED = 600;
    /** Layers read around the surface height: plants stand on the surface, the cells above them are free. */
    static final int BELOW = 2;
    static final NamespacedKey STAMP = new NamespacedKey("tfp", "plant_sweep");
    public static final String PLANT_PREFIX = "tfp:plant/";
    public static final String NATURAL = "tfc:natural_regrowing_plants";
    public static final String GROUND = "tfc:grass_plantable_on";

    /** What the region thread read: the cells for the decision and the plant states to copy. */
    record Snapshot(Map<BlockPos, Cell> cells, Map<BlockPos, BlockStateRef> states) {
    }

    private final CustomBlocks blocks;
    private final Tags tags;
    private final Scheduler scheduler;
    private final ToIntFunction<World> randomTickSpeed;
    private final ToLongFunction<World> gameTime;
    private final Supplier<ClockSnapshot> clock;
    private final Set<Long> inFlight = ConcurrentHashMap.newKeySet();

    public PlantSweeper(CustomBlocks blocks, Tags tags, Scheduler scheduler, ToIntFunction<World> randomTickSpeed,
            Supplier<ClockSnapshot> clock) {
        this(blocks, tags, scheduler, randomTickSpeed, World::getGameTime, clock);
    }

    PlantSweeper(CustomBlocks blocks, Tags tags, Scheduler scheduler, ToIntFunction<World> randomTickSpeed, ToLongFunction<World> gameTime,
            Supplier<ClockSnapshot> clock) {
        this.blocks = Objects.requireNonNull(blocks, "blocks");
        this.tags = Objects.requireNonNull(tags, "tags");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.randomTickSpeed = Objects.requireNonNull(randomTickSpeed, "randomTickSpeed");
        this.gameTime = Objects.requireNonNull(gameTime, "gameTime");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** The TFC plant name of a block id ({@code tfp:plant/ryegrass}), empty for anything else. */
    public static Optional<String> plantName(String blockId) {
        return blockId.startsWith(PLANT_PREFIX) ? Optional.of(blockId.substring(PLANT_PREFIX.length())) : Optional.empty();
    }

    /** TFC {@code getHemispheralCalendarMonthOfYear(...).getSeason() == WINTER}. */
    static boolean winter(long calendarTicks, int daysInMonth, boolean northern) {
        Month month = CalendarTicks.month(calendarTicks, daysInMonth);
        return (northern ? month : month.opposite()).season() == Season.WINTER;
    }

    /** Region thread of the chunk. Starts a sweep when the chunk is loaded and due; returns whether one started. */
    public boolean sweepChunk(World world, int chunkX, int chunkZ) {
        World.Environment environment = world.getEnvironment();
        if (environment == World.Environment.NETHER || environment == World.Environment.THE_END || !world.isChunkLoaded(chunkX, chunkZ)) {
            return false;
        }
        ClockSnapshot snapshotClock = clock.get();
        if (!snapshotClock.isSet()) {
            return false;
        }
        Chunk chunk = world.getChunkAt(chunkX, chunkZ, false);
        long now = gameTime.applyAsLong(world);
        Long stamp = chunk.getPersistentDataContainer().get(STAMP, PersistentDataType.LONG);
        if (stamp == null || stamp > now) {
            chunk.getPersistentDataContainer().set(STAMP, PersistentDataType.LONG, now);
            return false;
        }
        long elapsed = now - stamp;
        if (elapsed < MIN_ELAPSED) {
            return false;
        }
        long key = ((long) chunkX << 32) ^ (chunkZ & 0xFFFFFFFFL) ^ world.getUID().getLeastSignificantBits();
        if (!inFlight.add(key)) {
            return false;
        }
        chunk.getPersistentDataContainer().set(STAMP, PersistentDataType.LONG, now);
        Snapshot snapshot = snapshot(world, chunk);
        if (snapshot.states().isEmpty()) {
            inFlight.remove(key);
            return false;
        }
        double lambda = PlantSweepRules.lambda(elapsed, randomTickSpeed.applyAsInt(world));
        boolean northern = world.getEnvironment() != World.Environment.NORMAL
            || Solar.inNorthernHemisphere((chunkZ << 4) + 8, OverworldClimate.forWorldSeed(world.getSeed(),
                OverworldClimate.DEFAULT_TEMPERATURE_SCALE).hemisphereScale());
        boolean winter = winter(snapshotClock.calendarTicks(), snapshotClock.daysInMonth(), northern);
        scheduler.cpu(() -> {
            List<Spread> spreads;
            try {
                spreads = PlantSweepRules.sweep(snapshot.cells(), lambda, PlantRegrowth.SPREAD_CHANCE, winter, ThreadLocalRandom.current());
            } catch (RuntimeException e) {
                inFlight.remove(key);
                throw e;
            }
            if (spreads.isEmpty()) {
                inFlight.remove(key);
                return;
            }
            scheduler.region(world, chunkX, chunkZ, () -> apply(world, chunkX, chunkZ, new ArrayDeque<>(spreads), snapshot.states(), key));
        });
        return true;
    }

    /** The state of a new plant: the parent's, at age 0 (TFC {@code setProperty(state, ageProp, 0)}), as lower half for a tall plant. */
    static BlockStateRef newState(BlockStateRef parent, boolean tall) {
        BlockStateRef state = parent.properties().containsKey("age") ? parent.with("age", "0") : parent;
        return tall ? state.with("half", "lower") : state;
    }

    private void apply(World world, int chunkX, int chunkZ, ArrayDeque<Spread> queue, Map<BlockPos, BlockStateRef> states, long key) {
        int written = 0;
        while (!queue.isEmpty() && written < BATCH) {
            Spread s = queue.poll();
            BlockStateRef parent = states.get(s.from());
            Optional<SpreadKind> kind = PlantKinds.of(s.plant());
            if (parent == null || kind.isEmpty() || !world.isChunkLoaded(s.to().x() >> 4, s.to().z() >> 4)) {
                continue;
            }
            Block target = world.getBlockAt(s.to().x(), s.to().y(), s.to().z());
            if (!free(target) || !tags.is(target.getRelative(BlockFace.DOWN), GROUND)) {
                continue;
            }
            Block above = target.getRelative(BlockFace.UP);
            if (kind.get().tall() && !free(above)) {
                continue;
            }
            BlockStateRef lower = newState(parent, kind.get().tall());
            if (blocks.place(target, lower, true)) {
                if (kind.get().tall()) {
                    blocks.place(above, lower.with("half", "upper"), true);
                }
                written++;
            }
        }
        if (queue.isEmpty()) {
            inFlight.remove(key);
        } else {
            scheduler.regionDelayed(world, chunkX, chunkZ, 1, () -> apply(world, chunkX, chunkZ, queue, states, key));
        }
    }

    private boolean free(Block block) {
        return block.getType().isAir() && blocks.stateAt(block).isEmpty();
    }

    /** Region thread: the cells at and just under the surface of every column, and the states of the spreading plants. */
    Snapshot snapshot(World world, Chunk chunk) {
        Map<BlockPos, Cell> cells = new HashMap<>();
        Map<BlockPos, BlockStateRef> states = new HashMap<>();
        int minY = world.getMinHeight();
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int x = (chunk.getX() << 4) + lx;
                int z = (chunk.getZ() << 4) + lz;
                int top = world.getHighestBlockYAt(x, z);
                for (int y = Math.min(world.getMaxHeight() - 2, top + 1); y >= Math.max(minY + 1, top - BELOW); y--) {
                    Block block = world.getBlockAt(x, y, z);
                    Optional<BlockStateRef> state = blocks.stateAt(block);
                    String plant = null;
                    if (state.isPresent()) {
                        Optional<String> name = plantName(state.get().id());
                        boolean lower = !"upper".equals(state.get().properties().get("half"));
                        if (name.isPresent() && lower && PlantKinds.of(name.get()).isPresent()) {
                            plant = name.get();
                            states.put(new BlockPos(x, y, z), state.get());
                        }
                    }
                    boolean free = plant == null && state.isEmpty() && block.getType().isAir();
                    Block below = block.getRelative(BlockFace.DOWN);
                    boolean airAbove = block.getRelative(BlockFace.UP).getType().isAir() && blocks.stateAt(block.getRelative(BlockFace.UP)).isEmpty();
                    cells.put(new BlockPos(x, y, z), new Cell(plant, free, free && tags.is(below, GROUND), airAbove,
                        state.isPresent() && tags.is(block, NATURAL)));
                }
            }
        }
        return new Snapshot(cells, states);
    }
}
