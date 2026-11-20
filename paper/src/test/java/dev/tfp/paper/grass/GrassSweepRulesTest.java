/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.grass;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.world.BlockPos;
import dev.tfp.paper.grass.GrassSweepRules.Cell;
import dev.tfp.paper.grass.GrassSweepRules.Change;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

class GrassSweepRulesTest {
    private static final Cell GRASS_OPEN = new Cell("tfp:grass/mollisol", false, false, false, 0, 15);
    private static final Cell GRASS_BURIED = new Cell("tfp:grass/mollisol", false, false, false, 15, 0);
    private static final Cell GRASS_FLOODED = new Cell("tfp:grass/mollisol", false, true, true, 0, 15);
    private static final Cell GRASS_SNOWED = new Cell("tfp:grass/mollisol", true, false, false, 15, 0);
    private static final Cell DIRT_OPEN = new Cell("tfp:dirt/mollisol", false, false, false, 0, 15);
    private static final Cell DIRT_WATER = new Cell("tfp:dirt/mollisol", false, true, true, 0, 15);

    private static BlockPos p(int x, int y, int z) {
        return new BlockPos(x, y, z);
    }

    @Test
    void lambdaIsTicksTimesSpeedOver4096AndCapped() {
        assertThat(GrassSweepRules.lambda(4096, 3)).isEqualTo(3.0);
        assertThat(GrassSweepRules.lambda(0, 3)).isZero();
        assertThat(GrassSweepRules.lambda(100_000_000L, 3)).isEqualTo(GrassSweepRules.MAX_LAMBDA);
    }

    @Test
    void poissonMeanMatchesLambda() {
        Random random = new Random(7);
        double sum = 0;
        int n = 20000;
        for (int i = 0; i < n; i++) {
            sum += GrassSweepRules.poisson(2.5, random);
        }
        assertThat(sum / n).isBetween(2.4, 2.6);
        assertThat(GrassSweepRules.poisson(0, random)).isZero();
    }

    @Test
    void grassUnderAFullBlockDiesToDirt() {
        Map<BlockPos, Cell> cells = Map.of(p(0, 0, 0), GRASS_BURIED);
        List<Change> out = GrassSweepRules.sweep(cells, 24, new Random(1));
        assertThat(out).containsExactly(new Change(p(0, 0, 0), "tfp:grass/mollisol", "tfp:dirt/mollisol"));
    }

    @Test
    void grassUnderAFullWaterBlockDies() {
        List<Change> out = GrassSweepRules.sweep(Map.of(p(0, 0, 0), GRASS_FLOODED), 24, new Random(1));
        assertThat(out).hasSize(1);
        assertThat(out.get(0).to()).isEqualTo("tfp:dirt/mollisol");
    }

    @Test
    void snowKeepsGrassAlive() {
        assertThat(GrassSweepRules.sweep(Map.of(p(0, 0, 0), GRASS_SNOWED), 24, new Random(1))).isEmpty();
    }

    @Test
    void clayGrassDiesToClay() {
        Cell clayGrass = new Cell("tfp:clay_grass/oxisol", false, false, false, 15, 0);
        List<Change> out = GrassSweepRules.sweep(Map.of(p(0, 0, 0), clayGrass), 24, new Random(1));
        assertThat(out).containsExactly(new Change(p(0, 0, 0), "tfp:clay_grass/oxisol", "tfp:clay/oxisol"));
    }

    @Test
    void spreadsOntoNeighbouringDirtInLight() {
        Map<BlockPos, Cell> cells = new HashMap<>();
        cells.put(p(5, 5, 5), GRASS_OPEN);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx != 0 || dz != 0) {
                    cells.put(p(5 + dx, 4, 5 + dz), DIRT_OPEN);
                    cells.put(p(5 + dx, 5, 5 + dz), DIRT_OPEN);
                }
            }
        }
        List<Change> out = GrassSweepRules.sweep(cells, 24, new Random(3));
        assertThat(out).isNotEmpty().allSatisfy(c -> {
            assertThat(c.from()).isEqualTo("tfp:dirt/mollisol");
            assertThat(c.to()).isEqualTo("tfp:grass/mollisol");
        });
    }

    @Test
    void doesNotSpreadInTheDarkOntoWaterOrNonDirt() {
        Cell dark = new Cell("tfp:grass/mollisol", false, false, false, 0, 8);
        Map<BlockPos, Cell> cells = new HashMap<>();
        cells.put(p(0, 0, 0), dark);
        cells.put(p(1, 0, 0), DIRT_OPEN);
        assertThat(GrassSweepRules.sweep(cells, 24, new Random(3))).isEmpty();

        Map<BlockPos, Cell> wet = new HashMap<>();
        wet.put(p(0, 0, 0), GRASS_OPEN);
        wet.put(p(1, 0, 0), DIRT_WATER);
        wet.put(p(0, 0, 1), new Cell("tfp:rock/hardened/granite", false, false, false, 0, 15));
        assertThat(GrassSweepRules.sweep(wet, 24, new Random(3))).isEmpty();
    }

    @Test
    void noElapsedTimeMeansNoChange() {
        Map<BlockPos, Cell> cells = Map.of(p(0, 0, 0), GRASS_BURIED);
        assertThat(GrassSweepRules.sweep(cells, 0, new Random(1))).isEmpty();
    }

    @Test
    void sameSeedSameResult() {
        Map<BlockPos, Cell> cells = new HashMap<>();
        cells.put(p(0, 0, 0), GRASS_OPEN);
        cells.put(p(1, 0, 0), DIRT_OPEN);
        cells.put(p(0, 0, 1), DIRT_OPEN);
        cells.put(p(1, 0, 1), DIRT_OPEN);
        assertThat(GrassSweepRules.sweep(cells, 6, new Random(9))).isEqualTo(GrassSweepRules.sweep(cells, 6, new Random(9)));
    }
}
