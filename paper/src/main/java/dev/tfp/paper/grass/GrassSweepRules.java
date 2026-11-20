/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/soil/ConnectedGrassBlock.java @ c3b4d9536 (randomTick)
 */
package dev.tfp.paper.grass;

import dev.tfp.core.soil.GrassRules;
import dev.tfp.core.soil.SoilTable;
import dev.tfp.core.world.BlockPos;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.random.RandomGenerator;

/**
 * The lazy sweep of TFC's grass random tick (PAR-GATHER-11), pure and platform-free. Vanilla gives every block
 * {@code randomTickSpeed / 4096} random ticks per game tick (3/4096 by default); instead of ticking, a chunk is swept once
 * when it loads or is seen, and each grass block draws the Poisson number of ticks it would have had over the elapsed time
 * ({@link #lambda}). One tick is TFC's {@code ConnectedGrassBlock.randomTick}: grass that {@code canBeGrass} no longer holds
 * dies to dirt or clay; otherwise, with raw light 9 or more above, it makes four spread attempts at
 * {@link GrassRules#spreadOffset}, each onto a dirt or clay block that {@link GrassRules#canPropagate}.
 *
 * <p>Grass created during the sweep is not itself ticked in the same sweep, and spread targets are limited to the snapshot
 * (the chunk's surface layers); recorded in the ledger.
 */
public final class GrassSweepRules {
    /** Vanilla default {@code randomTickSpeed}. */
    public static final int RANDOM_TICK_SPEED = 3;
    /** A block's random tick chance per game tick is {@code speed / 4096} (a section holds 4096 blocks). */
    public static final int SECTION_BLOCKS = 4096;
    /** Upper bound of the ticks one block gets in one sweep (about a day of game time at the default speed). */
    public static final double MAX_LAMBDA = 24.0;

    private GrassSweepRules() {}

    /**
     * What stands at a snapshot position: a block id of our soil families (or any other id) and the facts about the block above
     * it that the grass rules read.
     */
    public record Cell(String id, boolean snowAbove, boolean fullFluidAbove, boolean anyFluidAbove, int lightBlockAbove,
            int rawLightAbove) {
        boolean canBeGrass() {
            return GrassRules.canBeGrass(snowAbove, fullFluidAbove, lightBlockAbove);
        }

        boolean canPropagate() {
            return GrassRules.canPropagate(snowAbove, fullFluidAbove, lightBlockAbove, anyFluidAbove);
        }

        Cell withId(String newId) {
            return new Cell(newId, snowAbove, fullFluidAbove, anyFluidAbove, lightBlockAbove, rawLightAbove);
        }
    }

    /** One block to rewrite: it must still be {@code from} when the change is applied. */
    public record Change(BlockPos pos, String from, String to) {}

    /** The expected number of random ticks of one block over {@code elapsedTicks}, capped at {@link #MAX_LAMBDA}. */
    public static double lambda(long elapsedTicks, int randomTickSpeed) {
        if (elapsedTicks <= 0 || randomTickSpeed <= 0) {
            return 0.0;
        }
        return Math.min(MAX_LAMBDA, (double) elapsedTicks * randomTickSpeed / SECTION_BLOCKS);
    }

    /** Knuth's Poisson sampler; exact for the lambdas here (at most {@link #MAX_LAMBDA}). */
    public static int poisson(double lambda, RandomGenerator random) {
        if (lambda <= 0.0) {
            return 0;
        }
        double limit = Math.exp(-lambda);
        double product = 1.0;
        int k = 0;
        do {
            k++;
            product *= random.nextDouble();
        } while (product > limit);
        return k - 1;
    }

    /**
     * The block changes of one sweep over {@code cells}. Grass blocks are visited in a fixed order (y, x, z) so a seeded random
     * gives a reproducible result; the changes are returned in the order they happen, at most one per position (a block that
     * changes twice reports its first {@code from} and last {@code to}).
     */
    public static List<Change> sweep(Map<BlockPos, Cell> cells, double lambda, RandomGenerator random) {
        Map<BlockPos, Cell> work = new HashMap<>(cells);
        Map<BlockPos, String> original = new HashMap<>();
        List<BlockPos> order = new ArrayList<>();
        List<BlockPos> grass = cells.entrySet().stream()
                .filter(e -> SoilTable.isGrassBlock(e.getValue().id()))
                .map(Map.Entry::getKey)
                .sorted(Comparator.comparingInt(BlockPos::y).thenComparingInt(BlockPos::x).thenComparingInt(BlockPos::z))
                .toList();
        for (BlockPos pos : grass) {
            int ticks = poisson(lambda, random);
            for (int t = 0; t < ticks; t++) {
                Cell cell = work.get(pos);
                if (!SoilTable.isGrassBlock(cell.id())) {
                    break; // died earlier in this sweep
                }
                if (!cell.canBeGrass()) {
                    Optional<String> dirt = SoilTable.dirtOf(cell.id());
                    if (dirt.isPresent()) {
                        original.putIfAbsent(pos, cell.id());
                        order.remove(pos);
                        order.add(pos);
                        work.put(pos, cell.withId(dirt.get()));
                    }
                    break;
                }
                if (GrassRules.canSpread(cell.rawLightAbove())) {
                    for (int i = 0; i < GrassRules.SPREAD_ATTEMPTS; i++) {
                        int[] d = GrassRules.spreadOffset(random);
                        BlockPos target = new BlockPos(pos.x() + d[0], pos.y() + d[1], pos.z() + d[2]);
                        Cell at = work.get(target);
                        if (at == null || !SoilTable.isDirtBlock(at.id()) || !at.canPropagate()) {
                            continue;
                        }
                        Optional<String> grassId = SoilTable.grassOf(at.id());
                        if (grassId.isPresent()) {
                            original.putIfAbsent(target, at.id());
                            order.remove(target);
                            order.add(target);
                            work.put(target, at.withId(grassId.get()));
                        }
                    }
                }
            }
        }
        List<Change> out = new ArrayList<>();
        for (BlockPos pos : order) {
            String from = original.get(pos);
            String to = work.get(pos).id();
            if (!from.equals(to)) {
                out.add(new Change(pos, from, to));
            }
        }
        return List.copyOf(out);
    }
}
