/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/plant/PlantRegrowth.java @ c3b4d9536 (placeRisingRock),
 *       src/main/java/net/dries007/tfc/common/blocks/soil/ConnectedGrassBlock.java @ c3b4d9536 (randomTick: the call),
 *       src/main/java/net/dries007/tfc/config/ServerConfig.java @ c3b4d9536 (grassSpawningRocksChance, 0.001)
 */
package dev.tfp.paper.grass;

import dev.tfp.core.calendar.CalendarTicks;
import dev.tfp.core.calendar.Month;
import dev.tfp.core.calendar.Season;
import dev.tfp.core.climate.OverworldClimate;
import dev.tfp.core.climate.Solar;
import dev.tfp.core.clock.ClockSnapshot;
import dev.tfp.core.soil.GrassRules;
import dev.tfp.core.world.BlockPos;
import dev.tfp.paper.climate.ChunkClimateSource;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;

/**
 * PAR-GATHER-14: the rock that rises on grass (TFC {@code PlantRegrowth.placeRisingRock}, called from every surviving grass
 * random tick). The sweep ({@link GrassSweeper}) hands this class the grass of the chunk: the region thread snapshots the free
 * spots above it and the players ({@link #snapshot}), the CPU pool draws the ticks and the gates ({@link #decide}: the chance
 * roll, spring in the hemisphere, an annual average temperature of at most 8 C at the spot, no player within 20 blocks, and the
 * slime chunk gate), and the region thread places the rocks ({@link #apply}), re-validating the spot, finding the rock type and
 * refusing a spot that has a loose rock within two blocks on its row (TFC checks that before it writes).
 *
 * <p>Ledger divergences: the rock is the first raw rock block under the grass (TFC reads its rock layer data at the position);
 * a surviving tick is every tick of the sweep's Poisson draw; the "add one to a loose rock there" branch of TFC never runs
 * (its own five by five scan finds that rock first), so it is not ported.
 */
public final class RisingRocks {
    /** TFC's search for the rock type: the first {@code tfp:rock/raw/<rock>} block under the grass, at most this deep. */
    static final int SCAN_DEPTH = 24;
    static final String RAW = "tfp:rock/raw/";
    static final String LOOSE = "tfp:rock/loose/";
    /** TFC {@code for x, z in -2..2}: a loose rock this close on the same row stops the rising rock. */
    static final int CLEARANCE = 2;

    /** A free spot: the position above a grass block. */
    record Spot(BlockPos pos) {
    }

    /** The region-thread facts the decision needs. */
    record Snapshot(List<Spot> spots, List<double[]> players, boolean spring, long seed, int chunkX, int chunkZ) {
    }

    private final CustomBlocks blocks;
    private final Supplier<ClockSnapshot> clock;
    private final double chance;

    public RisingRocks(CustomBlocks blocks, Supplier<ClockSnapshot> clock) {
        this(blocks, clock, GrassRules.DEFAULT_ROCK_CHANCE);
    }

    public RisingRocks(CustomBlocks blocks, Supplier<ClockSnapshot> clock, double chance) {
        this.blocks = Objects.requireNonNull(blocks, "blocks");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.chance = chance;
    }

    /** TFC {@code getHemispheralCalendarMonthOfYear(northern).getSeason() == SPRING}. */
    static boolean spring(long calendarTicks, int daysInMonth, boolean northern) {
        Month month = CalendarTicks.month(calendarTicks, daysInMonth);
        return (northern ? month : month.opposite()).season() == Season.SPRING;
    }

    /**
     * Region thread of the chunk: the free spots above the given grass positions and the players of the world. Empty when the
     * calendar is not set, the chunk gate fails or it is not spring (nothing can rise then).
     */
    Optional<Snapshot> snapshot(World world, int chunkX, int chunkZ, List<BlockPos> grass) {
        ClockSnapshot now = clock.get();
        if (!now.isSet() || chance <= 0 || !GrassRules.risingRockChunk(chunkX, chunkZ, world.getSeed())) {
            return Optional.empty();
        }
        boolean northern = world.getEnvironment() != World.Environment.NORMAL
            || Solar.inNorthernHemisphere((chunkZ << 4) + 8, OverworldClimate.forWorldSeed(world.getSeed(),
                OverworldClimate.DEFAULT_TEMPERATURE_SCALE).hemisphereScale());
        if (!spring(now.calendarTicks(), now.daysInMonth(), northern)) {
            return Optional.empty();
        }
        List<Spot> spots = new ArrayList<>();
        for (BlockPos g : grass) {
            Block above = world.getBlockAt(g.x(), g.y() + 1, g.z());
            if (above.getType().isAir() && blocks.stateAt(above).isEmpty()) {
                spots.add(new Spot(new BlockPos(g.x(), g.y() + 1, g.z())));
            }
        }
        if (spots.isEmpty()) {
            return Optional.empty();
        }
        List<double[]> players = new ArrayList<>();
        for (Player p : world.getPlayers()) {
            try {
                var l = p.getLocation();
                players.add(new double[] {l.getX(), l.getY(), l.getZ()});
            } catch (RuntimeException e) {
                players.add(new double[] {Double.NaN, Double.NaN, Double.NaN}); // unreadable here: counts as near
            }
        }
        return Optional.of(new Snapshot(List.copyOf(spots), List.copyOf(players), true, world.getSeed(), chunkX, chunkZ));
    }

    /** TFC {@code hasPlayerNearby}: {@code player.distanceToSqr(x, y, z) < 20 * 20}. */
    static boolean playerNearby(List<double[]> players, BlockPos pos) {
        for (double[] p : players) {
            if (Double.isNaN(p[0])) {
                return true;
            }
            double dx = p[0] - pos.x();
            double dy = p[1] - pos.y();
            double dz = p[2] - pos.z();
            if (dx * dx + dy * dy + dz * dz < (double) GrassRules.ROCK_PLAYER_DISTANCE * GrassRules.ROCK_PLAYER_DISTANCE) {
                return true;
            }
        }
        return false;
    }

    /**
     * Any thread: the spots where a rock rises over {@code lambda} expected ticks per block. Per tick TFC's order: the chance
     * roll, spring, temperature, players; a spot takes at most one rock per sweep and the spots of one row within
     * {@value #CLEARANCE} blocks of it are left (TFC's clearance scan would refuse them once it is there).
     */
    List<BlockPos> decide(Snapshot s, double lambda, double chanceOverride, RandomGenerator random) {
        OverworldClimate climate = OverworldClimate.forWorldSeed(s.seed(), OverworldClimate.DEFAULT_TEMPERATURE_SCALE);
        return decide(s, lambda, chanceOverride, random, pos -> climate.averageTemperature(pos,
            ChunkClimateSource.climateAt(s.seed(), pos.x(), pos.z())));
    }

    /** {@link #decide} with the annual average temperature at a position given (tests). */
    List<BlockPos> decide(Snapshot s, double lambda, double chanceOverride, RandomGenerator random,
            java.util.function.ToDoubleFunction<BlockPos> temperature) {
        List<BlockPos> out = new ArrayList<>();
        for (Spot spot : s.spots()) {
            int ticks = dev.tfp.paper.grass.GrassSweepRules.poisson(lambda, random);
            for (int t = 0; t < ticks; t++) {
                float roll = random.nextFloat();
                if (roll > chanceOverride) {
                    continue; // the common case: no need for the temperature
                }
                BlockPos pos = spot.pos();
                float average = (float) temperature.applyAsDouble(pos);
                if (!GrassRules.risingRockGates(roll, chanceOverride, s.spring(), average, playerNearby(s.players(), pos))) {
                    continue;
                }
                if (out.stream().noneMatch(o -> o.y() == pos.y() && Math.abs(o.x() - pos.x()) <= CLEARANCE
                        && Math.abs(o.z() - pos.z()) <= CLEARANCE)) {
                    out.add(pos);
                    break;
                }
            }
        }
        return List.copyOf(out);
    }

    /** Any thread: {@link #decide} with this instance's chance. */
    List<BlockPos> decide(Snapshot s, double lambda, RandomGenerator random) {
        return decide(s, lambda, chance, random);
    }

    /**
     * Region thread: places the rising rocks. Each spot is re-checked as TFC does: still air with no block of ours, grass below,
     * a rock under the grass, no loose rock within two blocks on the row, then {@code tfp:rock/loose/<rock>[count=1]}.
     *
     * @return the number of rocks placed
     */
    int apply(World world, List<BlockPos> spots) {
        int placed = 0;
        for (BlockPos pos : spots) {
            if (!world.isChunkLoaded(pos.x() >> 4, pos.z() >> 4)) {
                continue;
            }
            Block block = world.getBlockAt(pos.x(), pos.y(), pos.z());
            if (!block.getType().isAir() || blocks.stateAt(block).isPresent()) {
                continue;
            }
            Block below = block.getRelative(BlockFace.DOWN);
            if (blocks.stateAt(below).map(s -> dev.tfp.core.soil.SoilTable.isGrassBlock(s.id())).orElse(false) == false) {
                continue;
            }
            Optional<String> rock = rockUnder(below);
            if (rock.isEmpty() || !blocks.exists(LOOSE + rock.get()) || looseRockNear(block)) {
                continue;
            }
            if (blocks.place(block, BlockStateRef.of(LOOSE + rock.get(), Map.of("count", "1")), true)) {
                placed++;
            }
        }
        return placed;
    }

    /** The rock of the first {@code tfp:rock/raw/<rock>} block under {@code grass}. */
    Optional<String> rockUnder(Block grass) {
        Block b = grass;
        for (int i = 0; i < SCAN_DEPTH && b.getY() > b.getWorld().getMinHeight(); i++) {
            b = b.getRelative(BlockFace.DOWN);
            Optional<BlockStateRef> state = blocks.stateAt(b);
            if (state.isPresent() && state.get().id().startsWith(RAW)) {
                return Optional.of(state.get().id().substring(RAW.length()));
            }
        }
        return Optional.empty();
    }

    private boolean looseRockNear(Block block) {
        for (int dx = -CLEARANCE; dx <= CLEARANCE; dx++) {
            for (int dz = -CLEARANCE; dz <= CLEARANCE; dz++) {
                if (blocks.stateAt(block.getRelative(dx, 0, dz)).map(s -> s.id().startsWith(LOOSE)
                        || s.id().startsWith("tfp:rock/mossy_loose/")).orElse(false)) {
                    return true;
                }
            }
        }
        return false;
    }
}
