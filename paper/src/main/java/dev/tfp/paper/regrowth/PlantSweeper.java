/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/plant/PlantRegrowth.java @ c3b4d9536 (spreadSelf),
 *       src/main/java/net/dries007/tfc/common/blocks/plant/ShortGrassBlock.java @ c3b4d9536 (randomTick),
 *       src/main/java/net/dries007/tfc/common/blocks/plant/TFCTallGrassBlock.java @ c3b4d9536 (randomTick, placeTwoHalves),
 *       src/main/java/net/dries007/tfc/common/blocks/plant/CreepingPlantBlock.java @ c3b4d9536 (randomTick, updateStateFromSides),
 *       src/main/java/net/dries007/tfc/common/blocks/plant/FloatingWaterPlantBlock.java @ c3b4d9536 (randomTick)
 */
package dev.tfp.paper.regrowth;

import dev.tfp.core.calendar.CalendarTicks;
import dev.tfp.core.calendar.Month;
import dev.tfp.core.calendar.Season;
import dev.tfp.core.climate.OverworldClimate;
import dev.tfp.core.climate.Solar;
import dev.tfp.core.clock.ClockSnapshot;
import dev.tfp.core.regrowth.CreepKind;
import dev.tfp.core.regrowth.CreepingRules;
import dev.tfp.core.regrowth.FloatKind;
import dev.tfp.core.regrowth.FloatingRules;
import dev.tfp.core.regrowth.PlantKinds;
import dev.tfp.core.regrowth.PlantRegrowth;
import dev.tfp.core.regrowth.PlantSweepRules;
import dev.tfp.core.regrowth.PlantSweepRules.Cell;
import dev.tfp.core.regrowth.PlantSweepRules.Spread;
import dev.tfp.core.regrowth.SpreadKind;
import dev.tfp.core.world.BlockPos;
import dev.tfp.core.worldwater.WaterKind;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.plant.growth.BlockGrowthWorld;
import dev.tfp.paper.plant.growth.Face;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.tags.Tags;
import dev.tfp.paper.worldgen.WorldSettingsLookup;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.persistence.PersistentDataType;

/**
 * Sweeps a loaded chunk's spreading plants the way TFC's random ticks would have over the time since the last sweep
 * ({@link PlantSweepRules}). "Decide async, apply sync": the region thread snapshots the plants and the free cells at the surface
 * ({@link #snapshot}), the CPU pool decides, and the region thread places the new plants in batches of {@link #BATCH} per tick,
 * re-validating each cell. Creeping plants (moss, ivy, lichens) and floating water plants (algae, lilies) spread in the same sweep
 * ({@link CreepingRules}, {@link FloatingRules}). The time since the last sweep is kept in the chunk's persistent data ({@link #STAMP}); a chunk without a stamp is
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
    static final String CREEPING_NOT_ON = "tfc:creeping_plant_not_plantable_on";
    static final String CREEPING_STONE_ON = "tfc:creeping_stone_plantable_on";
    /** The state property of each side, in {@link CreepingRules#DIRS} order. */
    static final String[] FACE_KEYS = {"down", "up", "north", "south", "west", "east"};

    /** What the region thread read: the cells for the decision and the plant states to copy. */
    record Snapshot(Map<BlockPos, Cell> cells, Map<BlockPos, BlockStateRef> states, Map<BlockPos, String> creepers,
            Map<BlockPos, CreepingRules.Cell> creepCells, Map<BlockPos, FloatingRules.Cell> floatCells) {
    }

    /** One decided placement, for the region thread. */
    enum Kind { SELF, CREEP, FLOAT }

    record Placement(BlockPos from, BlockPos to, String plant, Kind kind, int faces) {
    }

    private final CustomBlocks blocks;
    private final Tags tags;
    private final Scheduler scheduler;
    private final ToIntFunction<World> randomTickSpeed;
    private final ToLongFunction<World> gameTime;
    private final Supplier<ClockSnapshot> clock;
    private final Set<Long> inFlight = ConcurrentHashMap.newKeySet();
    private volatile Function<Block, WaterKind> water = block -> WaterKind.FRESH;

    /** The classifier of the water under floating plants (the world water service). Region thread of the block. */
    public PlantSweeper withWater(Function<Block, WaterKind> classifier) {
        this.water = Objects.requireNonNull(classifier, "classifier");
        return this;
    }

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
            || Solar.inNorthernHemisphere((chunkZ << 4) + 8, WorldSettingsLookup.climate(world).hemisphereScale());
        boolean winter = winter(snapshotClock.calendarTicks(), snapshotClock.daysInMonth(), northern);
        scheduler.cpu(() -> {
            List<Placement> spreads = new java.util.ArrayList<>();
            try {
                ThreadLocalRandom random = ThreadLocalRandom.current();
                for (Spread s : PlantSweepRules.sweep(snapshot.cells(), lambda, PlantRegrowth.SPREAD_CHANCE, winter, random)) {
                    spreads.add(new Placement(s.from(), s.to(), s.plant(), Kind.SELF, 0));
                }
                for (CreepingRules.Spread s : CreepingRules.sweep(snapshot.creepers(), snapshot.creepCells(), lambda,
                        PlantRegrowth.SPREAD_CHANCE, winter, random)) {
                    spreads.add(new Placement(s.from(), s.to(), s.plant(), Kind.CREEP, s.faces()));
                }
                for (FloatingRules.Spread s : FloatingRules.sweep(snapshot.floatCells(), lambda, PlantRegrowth.SPREAD_CHANCE, winter,
                        random)) {
                    spreads.add(new Placement(s.from(), s.to(), s.plant(), Kind.FLOAT, 0));
                }
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

    private void apply(World world, int chunkX, int chunkZ, ArrayDeque<Placement> queue, Map<BlockPos, BlockStateRef> states, long key) {
        int written = 0;
        while (!queue.isEmpty() && written < BATCH) {
            Placement s = queue.poll();
            BlockStateRef parent = states.get(s.from());
            if (parent != null && s.kind() != Kind.SELF && world.isChunkLoaded(s.to().x() >> 4, s.to().z() >> 4)) {
                if (applyOther(world, s, parent)) {
                    written++;
                }
                continue;
            }
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

    /** Region thread: places a creeping plant (on the sides that still have ground) or a floating plant (on water still below). */
    private boolean applyOther(World world, Placement s, BlockStateRef parent) {
        Block target = world.getBlockAt(s.to().x(), s.to().y(), s.to().z());
        if (!free(target)) {
            return false;
        }
        if (s.kind() == Kind.CREEP) {
            Optional<CreepKind> kind = PlantKinds.creeping(s.plant());
            if (kind.isEmpty()) {
                return false;
            }
            int faces = creepFaces(live(world), target.getX(), target.getY(), target.getZ(), kind.get(),
                target.getChunk().getX(), target.getChunk().getZ());
            return faces != 0 && blocks.place(target, creepState(parent, faces), true);
        }
        if (target.getRelative(BlockFace.DOWN).getType() != Material.WATER) {
            return false;
        }
        return blocks.place(target, newState(parent, false), true);
    }

    private GrowthLive live(World world) {
        BlockGrowthWorld w = new BlockGrowthWorld(world, blocks, tags::is);
        return new GrowthLive() {
            @Override
            public boolean sturdy(int x, int y, int z, Face face) {
                return w.sturdy(x, y, z, face);
            }

            @Override
            public boolean tagged(int x, int y, int z, String tag) {
                return w.tagged(x, y, z, tag);
            }
        };
    }

    /** TFC {@code updateStateFromSides}: the parent's state with each side set to whether it has ground to creep on. */
    static BlockStateRef creepState(BlockStateRef parent, int faces) {
        BlockStateRef out = parent;
        for (int i = 0; i < FACE_KEYS.length; i++) {
            out = out.with(FACE_KEYS[i], (faces & (1 << i)) != 0 ? "true" : "false");
        }
        return out;
    }

    /**
     * The sides of the cell that have ground for {@code kind}: bit {@code i} for side {@link CreepingRules#DIRS}{@code [i]} when the
     * neighbour's opposite face is sturdy and ({@code NORMAL}) not in {@code tfc:creeping_plant_not_plantable_on} or ({@code STONE}) in
     * {@code tfc:creeping_stone_plantable_on}. Neighbours outside the chunk (another region on Folia) count as no ground.
     */
    static int creepFaces(GrowthLive live, int x, int y, int z, CreepKind kind, int chunkX, int chunkZ) {
        int mask = 0;
        for (int i = 0; i < CreepingRules.DIRS.length; i++) {
            int nx = x + CreepingRules.DIRS[i][0];
            int ny = y + CreepingRules.DIRS[i][1];
            int nz = z + CreepingRules.DIRS[i][2];
            if (nx >> 4 != chunkX || nz >> 4 != chunkZ || !live.sturdy(nx, ny, nz, Face.values()[i].opposite())) {
                continue;
            }
            boolean ground = kind == CreepKind.NORMAL ? !live.tagged(nx, ny, nz, CREEPING_NOT_ON) : live.tagged(nx, ny, nz, CREEPING_STONE_ON);
            if (ground) {
                mask |= 1 << i;
            }
        }
        return mask;
    }

    /** The two reads {@link #creepFaces} needs of the world. */
    interface GrowthLive {
        boolean sturdy(int x, int y, int z, Face face);

        boolean tagged(int x, int y, int z, String tag);
    }

    private boolean free(Block block) {
        return block.getType().isAir() && blocks.stateAt(block).isEmpty();
    }

    /** Region thread: the cells at and just under the surface of every column, and the states of the spreading plants. */
    Snapshot snapshot(World world, Chunk chunk) {
        Map<BlockPos, Cell> cells = new HashMap<>();
        Map<BlockPos, BlockStateRef> states = new HashMap<>();
        Map<BlockPos, String> creepers = new HashMap<>();
        List<BlockPos> floaters = new java.util.ArrayList<>();
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
                        } else if (name.isPresent() && PlantKinds.creeping(name.get()).isPresent()) {
                            creepers.put(new BlockPos(x, y, z), name.get());
                            states.put(new BlockPos(x, y, z), state.get());
                        } else if (name.isPresent() && PlantKinds.floating(name.get()).isPresent()) {
                            floaters.add(new BlockPos(x, y, z));
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
        return new Snapshot(cells, states, creepers, creepCells(world, chunk, creepers), floatCells(world, chunk, floaters));
    }

    private static boolean inChunk(Chunk chunk, int x, int z) {
        return x >> 4 == chunk.getX() && z >> 4 == chunk.getZ();
    }

    /** The cells a creeping plant may spread into: its five sides (up for down) and the block above each. In this chunk only. */
    private Map<BlockPos, CreepingRules.Cell> creepCells(World world, Chunk chunk, Map<BlockPos, String> creepers) {
        Map<BlockPos, CreepingRules.Cell> out = new HashMap<>();
        if (creepers.isEmpty()) {
            return out;
        }
        GrowthLive live = live(world);
        for (BlockPos p : creepers.keySet()) {
            for (int i = 1; i < CreepingRules.DIRS.length; i++) {
                for (int up = 0; up <= 1; up++) {
                    BlockPos t = new BlockPos(p.x() + CreepingRules.DIRS[i][0], p.y() + CreepingRules.DIRS[i][1] + up,
                        p.z() + CreepingRules.DIRS[i][2]);
                    if (out.containsKey(t) || !inChunk(chunk, t.x(), t.z()) || t.y() <= world.getMinHeight() || t.y() >= world.getMaxHeight() - 1) {
                        continue;
                    }
                    Block b = world.getBlockAt(t.x(), t.y(), t.z());
                    boolean air = b.getType().isAir() && blocks.stateAt(b).isEmpty();
                    out.put(t, new CreepingRules.Cell(air, air ? creepFaces(live, t.x(), t.y(), t.z(), CreepKind.NORMAL, chunk.getX(), chunk.getZ()) : 0,
                        air ? creepFaces(live, t.x(), t.y(), t.z(), CreepKind.STONE, chunk.getX(), chunk.getZ()) : 0));
                }
            }
        }
        return out;
    }

    /** The cells around each floating plant out to the density radius: what stands there and what is under it. In this chunk only. */
    private Map<BlockPos, FloatingRules.Cell> floatCells(World world, Chunk chunk, List<BlockPos> floaters) {
        Map<BlockPos, FloatingRules.Cell> out = new HashMap<>();
        int reach = FloatingRules.RANGE + FloatingRules.RADIUS;
        for (BlockPos p : floaters) {
            for (int dx = -reach; dx <= reach; dx++) {
                for (int dz = -reach; dz <= reach; dz++) {
                    BlockPos t = new BlockPos(p.x() + dx, p.y(), p.z() + dz);
                    if (out.containsKey(t) || !inChunk(chunk, t.x(), t.z())) {
                        continue;
                    }
                    out.put(t, floatCell(world, t));
                }
            }
        }
        return out;
    }

    private FloatingRules.Cell floatCell(World world, BlockPos t) {
        Block b = world.getBlockAt(t.x(), t.y(), t.z());
        Optional<BlockStateRef> state = blocks.stateAt(b);
        String plant = state.flatMap(s -> plantName(s.id())).filter(n -> PlantKinds.floating(n).isPresent()).orElse(null);
        boolean air = state.isEmpty() && b.getType().isAir();
        Block below = b.getRelative(BlockFace.DOWN);
        FloatKind fluid = null;
        boolean river = false;
        if (below.getType() == Material.WATER && blocks.stateAt(below).isEmpty()) {
            WaterKind kind = water.apply(below);
            river = kind == WaterKind.RIVER;
            fluid = kind == WaterKind.FRESH ? FloatKind.FRESH : kind == WaterKind.SALT ? FloatKind.SALT : null;
        }
        boolean deep = b.getRelative(0, -5, 0).isLiquid();
        return new FloatingRules.Cell(plant, air, fluid, deep, river, state.isPresent() && tags.is(b, NATURAL));
    }
}
