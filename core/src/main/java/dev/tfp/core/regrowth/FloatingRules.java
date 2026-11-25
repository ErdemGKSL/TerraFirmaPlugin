/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/plant/FloatingWaterPlantBlock.java @ c3b4d9536 (randomTick, canSurvive),
 *       src/main/java/net/dries007/tfc/common/blocks/plant/PlantRegrowth.java @ c3b4d9536 (spreadSelf(1, 2, 1))
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
 * The lazy sweep of a floating water plant's random tick, pure: {@code canSpread}, then {@code spreadSelf(1, 2, 1)} (an offset of at
 * most one block, an air cell above a fluid the plant survives on, no more than one natural plant in the 5x5 around it), then only
 * where there is no fluid five blocks below the new cell and the block below it is not river water. The new plant is at age 0.
 */
public final class FloatingRules {
    public static final int RANGE = 1;
    public static final int RADIUS = 2;
    public static final int MAX_PLANTS = 1;

    private FloatingRules() {
    }

    /**
     * What a snapshot position holds.
     *
     * @param plant      the floating plant standing here (TFC name), else null
     * @param air        the cell is air
     * @param fluidBelow the fluid of the block below: null when none or not one a floating plant stands on
     * @param deepBelow  there is a fluid five blocks below (TFC {@code getFluidState(newPos.below(5))} is not empty)
     * @param riverBelow the block below is river water
     * @param natural    a plant of the natural regrowing set stands here
     */
    public record Cell(String plant, boolean air, FloatKind fluidBelow, boolean deepBelow, boolean riverBelow, boolean natural) {
        Cell planted(String name) {
            return new Cell(name, false, fluidBelow, deepBelow, riverBelow, true);
        }
    }

    /** One new floating plant. */
    public record Spread(BlockPos from, BlockPos to, String plant) {
    }

    public static List<Spread> sweep(Map<BlockPos, Cell> cells, double lambda, float chance, boolean winter, RandomGenerator random) {
        Map<BlockPos, Cell> work = new HashMap<>(cells);
        List<BlockPos> plants = cells.entrySet().stream().filter(e -> e.getValue().plant() != null && PlantKinds.floating(e.getValue().plant()).isPresent())
            .map(Map.Entry::getKey)
            .sorted(Comparator.comparingInt(BlockPos::y).thenComparingInt(BlockPos::x).thenComparingInt(BlockPos::z)).toList();
        List<Spread> out = new ArrayList<>();
        for (BlockPos pos : plants) {
            String name = cells.get(pos).plant();
            FloatKind kind = PlantKinds.floating(name).orElseThrow();
            int ticks = PlantSweepRules.poisson(lambda, random);
            for (int t = 0; t < ticks; t++) {
                if (!PlantRegrowth.canSpread(random, chance, winter)) {
                    continue;
                }
                BlockPos target = spreadSelf(work, pos, kind, random);
                if (target != null) {
                    Cell at = work.get(target);
                    if (!at.deepBelow() && !at.riverBelow()) {
                        work.put(target, at.planted(name));
                        out.add(new Spread(pos, target, name));
                    }
                }
            }
        }
        return out;
    }

    /** {@code spreadSelf(state, level, pos, random, 1, 2, 1)} with the floating plant's {@code canSurvive}. */
    static BlockPos spreadSelf(Map<BlockPos, Cell> cells, BlockPos pos, FloatKind kind, RandomGenerator random) {
        int[] d = PlantRegrowth.spreadOffset(random, RANGE);
        BlockPos target = new BlockPos(pos.x() + d[0], pos.y(), pos.z() + d[1]);
        Cell at = cells.get(target);
        if (at == null || !at.air() || at.fluidBelow() != kind) {
            return null;
        }
        int plants = 0;
        for (int x = -RADIUS; x <= RADIUS; x++) {
            for (int z = -RADIUS; z <= RADIUS; z++) {
                Cell around = cells.get(new BlockPos(target.x() + x, target.y(), target.z() + z));
                if (around == null) {
                    return null; // not loaded
                }
                if (around.natural() && PlantRegrowth.tooDense(plants++, MAX_PLANTS)) {
                    return null;
                }
            }
        }
        return target;
    }
}
