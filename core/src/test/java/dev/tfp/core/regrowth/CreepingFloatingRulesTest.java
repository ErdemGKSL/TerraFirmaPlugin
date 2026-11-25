/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/plant/CreepingPlantBlock.java @ c3b4d9536 (randomTick),
 *       src/main/java/net/dries007/tfc/common/blocks/plant/FloatingWaterPlantBlock.java @ c3b4d9536 (randomTick)
 */
package dev.tfp.core.regrowth;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.world.BlockPos;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.Test;

/** PAR-FARM-22: the creeping and floating plant spread rules. */
class CreepingFloatingRulesTest {
    private static RandomGenerator scripted(int[] ints, float[] floats) {
        return new RandomGenerator() {
            int i;
            int f;

            @Override
            public long nextLong() {
                return 0;
            }

            @Override
            public int nextInt(int bound) {
                return Math.min(ints[i++], bound - 1);
            }

            @Override
            public float nextFloat() {
                return floats[f++];
            }
        };
    }

    @Test
    void creepingTargetIsTheDirectionThenTheStepUpDraw() {
        BlockPos p = new BlockPos(10, 64, 10);
        // direction order: down, up, north, south, west, east; down becomes up
        assertThat(CreepingRules.target(scripted(new int[] {0}, new float[] {0.5f}), p)).isEqualTo(new BlockPos(10, 65, 10));
        assertThat(CreepingRules.target(scripted(new int[] {2}, new float[] {0.5f}), p)).isEqualTo(new BlockPos(10, 64, 9));
        assertThat(CreepingRules.target(scripted(new int[] {3}, new float[] {0.5f}), p)).isEqualTo(new BlockPos(10, 64, 11));
        assertThat(CreepingRules.target(scripted(new int[] {4}, new float[] {0.5f}), p)).isEqualTo(new BlockPos(9, 64, 10));
        assertThat(CreepingRules.target(scripted(new int[] {5}, new float[] {0.5f}), p)).isEqualTo(new BlockPos(11, 64, 10));
        // 0.2 chance of one block higher: the draw is < 0.2
        assertThat(CreepingRules.target(scripted(new int[] {5}, new float[] {0.19f}), p)).isEqualTo(new BlockPos(11, 65, 10));
        assertThat(CreepingRules.target(scripted(new int[] {5}, new float[] {0.2f}), p)).isEqualTo(new BlockPos(11, 64, 10));
    }

    private static Map<BlockPos, CreepingRules.Cell> wall(int normal, int stone) {
        Map<BlockPos, CreepingRules.Cell> cells = new HashMap<>();
        for (int x = 0; x < 12; x++) {
            for (int y = 60; y < 70; y++) {
                for (int z = 0; z < 12; z++) {
                    cells.put(new BlockPos(x, y, z), new CreepingRules.Cell(true, normal, stone));
                }
            }
        }
        return cells;
    }

    @Test
    void creepingPlantsSpreadOnlyWhereThereIsGroundForTheirKind() {
        BlockPos at = new BlockPos(5, 64, 5);
        // ground for the normal kind only: moss spreads, a stone lichen does not
        List<CreepingRules.Spread> moss = CreepingRules.sweep(Map.of(at, "moss"), wall(0b000100, 0), 24, 1f, false, new Random(3));
        assertThat(moss).isNotEmpty().allSatisfy(s -> {
            assertThat(s.faces()).isEqualTo(0b000100);
            assertThat(s.plant()).isEqualTo("moss");
            assertThat(Math.abs(s.to().x() - 5) + Math.abs(s.to().z() - 5) + Math.abs(s.to().y() - 64)).isBetween(1, 2);
        });
        assertThat(CreepingRules.sweep(Map.of(at, "cobblestone_lichen"), wall(0b000100, 0), 24, 1f, false, new Random(3))).isEmpty();
        assertThat(CreepingRules.sweep(Map.of(at, "cobblestone_lichen"), wall(0, 0b100000), 24, 1f, false, new Random(3))).isNotEmpty();
        // no cell is taken twice, and a plant that is not creeping is ignored
        assertThat(moss.stream().map(CreepingRules.Spread::to).distinct().count()).isEqualTo(moss.size());
        assertThat(CreepingRules.sweep(Map.of(at, "ryegrass"), wall(63, 63), 24, 1f, false, new Random(3))).isEmpty();
    }

    @Test
    void creepingStopsInWinterWithoutChanceOrTime() {
        BlockPos at = new BlockPos(5, 64, 5);
        assertThat(CreepingRules.sweep(Map.of(at, "ivy"), wall(63, 63), 24, 1f, true, new Random(3))).isEmpty();
        assertThat(CreepingRules.sweep(Map.of(at, "ivy"), wall(63, 63), 24, 0f, false, new Random(3))).isEmpty();
        assertThat(CreepingRules.sweep(Map.of(at, "ivy"), wall(63, 63), 0, 1f, false, new Random(3))).isEmpty();
    }

    private static Map<BlockPos, FloatingRules.Cell> pond(FloatKind fluid, boolean deep, boolean river) {
        Map<BlockPos, FloatingRules.Cell> cells = new HashMap<>();
        for (int x = 0; x < 12; x++) {
            for (int z = 0; z < 12; z++) {
                cells.put(new BlockPos(x, 64, z), new FloatingRules.Cell(null, true, fluid, deep, river, false));
            }
        }
        cells.put(new BlockPos(5, 64, 5), new FloatingRules.Cell("duckweed", false, fluid, deep, river, true));
        return cells;
    }

    @Test
    void floatingPlantsSpreadOneBlockOverTheirFluid() {
        List<FloatingRules.Spread> spreads = FloatingRules.sweep(pond(FloatKind.FRESH, false, false), 24, 1f, false, new Random(5));
        assertThat(spreads).isNotEmpty().allSatisfy(s -> {
            assertThat(s.to().x() - 5).isBetween(0, 1);
            assertThat(s.to().z() - 5).isBetween(0, 1);
            assertThat(s.to()).isNotEqualTo(s.from());
        });
        // maxPlants 1 denies on the third plant counted (plants++ > 1): the parent and one child, then no more
        assertThat(spreads).hasSize(2);
    }

    @Test
    void floatingPlantsNeedTheirFluidShallowWaterAndNoRiver() {
        // duckweed is fresh: salt water below denies it
        assertThat(FloatingRules.sweep(pond(FloatKind.SALT, false, false), 24, 1f, false, new Random(5))).isEmpty();
        // a fluid five blocks below (deep water) denies it
        assertThat(FloatingRules.sweep(pond(FloatKind.FRESH, true, false), 24, 1f, false, new Random(5))).isEmpty();
        assertThat(FloatingRules.sweep(pond(FloatKind.FRESH, false, true), 24, 1f, false, new Random(5))).isEmpty();
        assertThat(FloatingRules.sweep(pond(FloatKind.FRESH, false, false), 24, 1f, true, new Random(5))).isEmpty();
    }

    @Test
    void floatingSpreadReadsTheDensitySquareAndTreatsAMissingCellAsUnloaded() {
        Map<BlockPos, FloatingRules.Cell> cells = pond(FloatKind.FRESH, false, false);
        cells.remove(new BlockPos(7, 64, 7));
        // every offset of range 1 has (7, 7) in its 5x5 only for the (1, 1) target: that one is denied, the others still spread
        List<FloatingRules.Spread> spreads = FloatingRules.sweep(cells, 24, 1f, false, new Random(11));
        assertThat(spreads).noneMatch(s -> s.to().equals(new BlockPos(6, 64, 6)));
    }
}
