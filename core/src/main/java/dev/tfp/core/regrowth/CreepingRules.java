/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/plant/CreepingPlantBlock.java @ c3b4d9536 (randomTick, updateStateFromSides)
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
 * The lazy sweep of a creeping plant's random tick, pure: {@code canSpread}, one random direction (down becomes up), a 0.2 chance
 * of one block higher, and the new plant on an air cell that has ground to creep on at least one of its six sides
 * ({@code updateStateFromSides}). Directions are Minecraft's {@code get3DDataValue} order (down, up, north, south, west, east), and
 * a face mask has bit {@code i} set when side {@code i} has ground.
 */
public final class CreepingRules {
    public static final int[][] DIRS = {{0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}};
    /** TFC's {@code random.nextFloat() < 0.2f}: the new plant one block up. */
    public static final float STEP_UP_CHANCE = 0.2f;

    private CreepingRules() {
    }

    /**
     * What a snapshot position holds.
     *
     * @param air         the cell is air
     * @param normalFaces the sides with ground for {@link CreepKind#NORMAL}
     * @param stoneFaces  the sides with ground for {@link CreepKind#STONE}
     */
    public record Cell(boolean air, int normalFaces, int stoneFaces) {
        int faces(CreepKind kind) {
            return kind == CreepKind.NORMAL ? normalFaces : stoneFaces;
        }

        Cell taken() {
            return new Cell(false, normalFaces, stoneFaces);
        }
    }

    /** One new creeping plant: the parent, the cell, the plant and the sides that hold it. */
    public record Spread(BlockPos from, BlockPos to, String plant, int faces) {
    }

    /** The target of one spread tick: the direction draw, then the step-up draw (TFC's order). */
    public static BlockPos target(RandomGenerator random, BlockPos pos) {
        int direction = random.nextInt(DIRS.length);
        if (direction == 0) {
            direction = 1;
        }
        int[] d = DIRS[direction];
        int up = random.nextFloat() < STEP_UP_CHANCE ? 1 : 0;
        return new BlockPos(pos.x() + d[0], pos.y() + d[1] + up, pos.z() + d[2]);
    }

    /**
     * The spreads of one sweep. Plants are visited in (y, x, z) order; plants born in the sweep do not tick in it.
     *
     * @param plants the creeping plants by position (their TFC plant name)
     * @param cells  the targets around them
     */
    public static List<Spread> sweep(Map<BlockPos, String> plants, Map<BlockPos, Cell> cells, double lambda, float chance, boolean winter,
            RandomGenerator random) {
        Map<BlockPos, Cell> work = new HashMap<>(cells);
        List<BlockPos> order = plants.keySet().stream()
            .filter(p -> PlantKinds.creeping(plants.get(p)).isPresent())
            .sorted(Comparator.comparingInt(BlockPos::y).thenComparingInt(BlockPos::x).thenComparingInt(BlockPos::z)).toList();
        List<Spread> out = new ArrayList<>();
        for (BlockPos pos : order) {
            String name = plants.get(pos);
            CreepKind kind = PlantKinds.creeping(name).orElseThrow();
            int ticks = PlantSweepRules.poisson(lambda, random);
            for (int t = 0; t < ticks; t++) {
                if (!PlantRegrowth.canSpread(random, chance, winter)) {
                    continue;
                }
                BlockPos target = target(random, pos);
                Cell at = work.get(target);
                if (at == null || !at.air()) {
                    continue;
                }
                int faces = at.faces(kind);
                if (faces != 0) {
                    work.put(target, at.taken());
                    out.add(new Spread(pos, target, name, faces));
                }
            }
        }
        return out;
    }
}
