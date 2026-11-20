/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/plant/PlantRegrowth.java @ c3b4d9536 (spreadSelf),
 *       src/main/java/net/dries007/tfc/common/blocks/plant/ShortGrassBlock.java @ c3b4d9536 (randomTick),
 *       src/main/java/net/dries007/tfc/common/blocks/plant/TFCTallGrassBlock.java @ c3b4d9536 (randomTick)
 */
package dev.tfp.core.regrowth;

import dev.tfp.core.world.BlockPos;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;

/**
 * The lazy sweep of TFC's plant spread random tick, pure. Like the grass sweep, a chunk is swept when it loads or is seen, and every
 * spreading plant draws the Poisson number of random ticks it would have had over the elapsed time ({@link #lambda}); each tick is TFC's
 * {@code randomTick}: {@link PlantRegrowth#canSpread}, then {@code spreadSelf} (an offset in range, a free cell with ground the plant
 * survives on, and no more than {@code maxPlants} natural plants in the square around it), then the new plant at age 0.
 *
 * <p>Differences from TFC, recorded in the ledger: the cells are a snapshot of one chunk, a target or a density square leaving the
 * snapshot counts as unloaded (TFC's {@code isLoaded} check), plants born in the sweep do not tick in it, and the "dead crop" placement
 * target is not used.
 */
public final class PlantSweepRules {
    /** A block's random tick chance per game tick is {@code speed / 4096}. */
    public static final int SECTION_BLOCKS = 4096;
    /** Upper bound of the ticks one plant gets in one sweep (about a day at the default speed). */
    public static final double MAX_LAMBDA = 24.0;

    private PlantSweepRules() {
    }

    /**
     * What a snapshot position holds.
     *
     * @param plant      the TFC plant name when a spreading plant stands here (its lower half for a tall one), else null
     * @param free       the cell is air (the placement test)
     * @param groundOk   the block below is a block the plant survives on ({@code GRASS_PLANTABLE_ON})
     * @param airAbove   the cell above is air (a tall plant needs it)
     * @param natural    a plant of the natural regrowing set stands here (counts for the density)
     */
    public record Cell(String plant, boolean free, boolean groundOk, boolean airAbove, boolean natural) {
        Cell planted(String name) {
            return new Cell(name, false, groundOk, false, true);
        }
    }

    /** One new plant: where it came from, where it goes and what it is. */
    public record Spread(BlockPos from, BlockPos to, String plant) {
    }

    /** The expected number of random ticks of one plant over {@code elapsedTicks}, capped at {@link #MAX_LAMBDA}. */
    public static double lambda(long elapsedTicks, int randomTickSpeed) {
        if (elapsedTicks <= 0 || randomTickSpeed <= 0) {
            return 0.0;
        }
        return Math.min(MAX_LAMBDA, (double) elapsedTicks * randomTickSpeed / SECTION_BLOCKS);
    }

    /** Knuth's Poisson sampler, exact for the lambdas here. */
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
     * The spreads of one sweep, in the order they happen. Plants are visited in a fixed order (y, x, z) so a seeded random is reproducible.
     *
     * @param cells  the snapshot
     * @param lambda the expected ticks per plant ({@link #lambda})
     * @param chance TFC's {@code plantSpreadChance}
     * @param winter whether it is winter where the chunk is
     */
    public static List<Spread> sweep(Map<BlockPos, Cell> cells, double lambda, float chance, boolean winter, RandomGenerator random) {
        Map<BlockPos, Cell> work = new HashMap<>(cells);
        List<BlockPos> plants = cells.entrySet().stream().filter(e -> e.getValue().plant() != null && PlantKinds.of(e.getValue().plant()).isPresent())
            .map(Map.Entry::getKey)
            .sorted(Comparator.comparingInt(BlockPos::y).thenComparingInt(BlockPos::x).thenComparingInt(BlockPos::z)).toList();
        List<Spread> out = new ArrayList<>();
        for (BlockPos pos : plants) {
            String name = cells.get(pos).plant();
            SpreadKind kind = PlantKinds.of(name).orElseThrow();
            int ticks = poisson(lambda, random);
            for (int t = 0; t < ticks; t++) {
                if (!PlantRegrowth.canSpread(random, chance, winter)) {
                    continue;
                }
                BlockPos target = spreadSelf(work, pos, kind, random);
                if (target != null) {
                    work.put(target, work.get(target).planted(name));
                    out.add(new Spread(pos, target, name));
                }
            }
        }
        return out;
    }

    /** {@code spreadSelf}: the new position, or null. */
    static BlockPos spreadSelf(Map<BlockPos, Cell> cells, BlockPos pos, SpreadKind kind, RandomGenerator random) {
        int[] d = PlantRegrowth.spreadOffset(random, kind.range());
        BlockPos target = new BlockPos(pos.x() + d[0], pos.y(), pos.z() + d[1]);
        Cell at = cells.get(target);
        if (at == null || !at.free() || !at.groundOk() || kind.tall() && !at.airAbove()) {
            return null;
        }
        int plants = 0;
        for (int x = -kind.radius(); x <= kind.radius(); x++) {
            for (int z = -kind.radius(); z <= kind.radius(); z++) {
                Cell around = cells.get(new BlockPos(target.x() + x, target.y(), target.z() + z));
                if (around == null) {
                    return null; // not loaded
                }
                if (around.natural() && PlantRegrowth.tooDense(plants++, kind.maxPlants())) {
                    return null;
                }
            }
        }
        return target;
    }
}
