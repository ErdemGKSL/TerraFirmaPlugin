/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/plant/PlantRegrowth.java @ c3b4d9536 (canSpread, spreadSelf)
 */
package dev.tfp.core.regrowth;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.regrowth.PlantSweepRules.Cell;
import dev.tfp.core.regrowth.PlantSweepRules.Spread;
import dev.tfp.core.world.BlockPos;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.Test;

/** Plant spread: the kinds against {@code Plant.java}, TFC's arithmetic, and the lazy sweep. */
class PlantRegrowthTest {
    /** A generator that returns scripted values. */
    private static RandomGenerator scripted(int... ints) {
        return new RandomGenerator() {
            int i;

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
                return 0f;
            }
        };
    }

    @Test
    void kindsMatchTfcsBlockTypes() throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/dev/tfp/core/regrowth/plant-kinds.txt")) {
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            int spreading = 0;
            int creeping = 0;
            int floating = 0;
            for (String line : text.strip().split("\n")) {
                String[] p = line.split(" ");
                Optional<SpreadKind> kind = PlantKinds.of(p[0]);
                switch (p[1]) {
                    case "short_grass", "beach_grass" -> {
                        assertThat(kind).as(p[0]).contains(SpreadKind.SHORT);
                        spreading++;
                    }
                    case "tall_grass" -> {
                        assertThat(kind).as(p[0]).contains(SpreadKind.TALL);
                        spreading++;
                    }
                    case "creeping" -> {
                        assertThat(kind).as(p[0]).isEmpty();
                        assertThat(PlantKinds.creeping(p[0])).as(p[0]).contains(CreepKind.NORMAL);
                        creeping++;
                    }
                    case "creeping_stone" -> {
                        assertThat(kind).as(p[0]).isEmpty();
                        assertThat(PlantKinds.creeping(p[0])).as(p[0]).contains(CreepKind.STONE);
                        creeping++;
                    }
                    case "floating", "floating_fresh" -> {
                        assertThat(kind).as(p[0]).isEmpty();
                        assertThat(PlantKinds.floating(p[0])).as(p[0]).contains(p[1].equals("floating") ? FloatKind.SALT : FloatKind.FRESH);
                        floating++;
                    }
                    default -> assertThat(kind).as(p[0]).isEmpty();
                }
                if (!p[1].startsWith("creeping") && !p[1].startsWith("floating")) {
                    assertThat(PlantKinds.creeping(p[0])).as(p[0]).isEmpty();
                    assertThat(PlantKinds.floating(p[0])).as(p[0]).isEmpty();
                }
            }
            assertThat(creeping).isEqualTo(PlantKinds.CREEPING.size() + PlantKinds.CREEPING_STONE.size());
            assertThat(floating).isEqualTo(PlantKinds.FLOATING_SALT.size() + PlantKinds.FLOATING_FRESH.size());
            assertThat(spreading).isEqualTo(PlantKinds.SHORT.size() + PlantKinds.TALL.size());
        }
    }

    @Test
    void canSpreadTakesTheDrawBeforeTheSeasonCheck() {
        assertThat(PlantRegrowth.canSpread(scripted(), 0.001f, false)).isTrue(); // draw 0 < chance
        assertThat(PlantRegrowth.canSpread(scripted(), 0.001f, true)).isFalse();
        assertThat(PlantRegrowth.canSpread(scripted(), 0f, false)).isFalse();
    }

    @Test
    void spreadOffsetRedrawsWhenItHitsTheOwnCell() {
        assertThat(PlantRegrowth.spreadOffset(scripted(2, 1), 2)).containsExactly(2, 1);
        // (0, 0) is the plant's own cell: a second draw in [1, 2] on both axes (nextInt(2) + 1)
        assertThat(PlantRegrowth.spreadOffset(scripted(0, 0, 1, 0), 2)).containsExactly(2, 1);
        assertThat(PlantRegrowth.nextInt(scripted(), 3, 3)).isEqualTo(3);
    }

    @Test
    void densityDeniesWhenTheCountBeforeAddingExceedsTheMaximum() {
        // plants++ > maxPlants: the sixth plant found (count before = 5) denies at max 4
        assertThat(PlantRegrowth.tooDense(4, 4)).isFalse();
        assertThat(PlantRegrowth.tooDense(5, 4)).isTrue();
    }

    private static Map<BlockPos, Cell> meadow(int size, String plantAt) {
        Map<BlockPos, Cell> cells = new HashMap<>();
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                cells.put(new BlockPos(x, 64, z), new Cell(null, true, true, true, false));
            }
        }
        if (plantAt != null) {
            cells.put(new BlockPos(5, 64, 5), new Cell(plantAt, false, true, false, true));
        }
        return cells;
    }

    @Test
    void aLoneGrassSpreadsWithinRangeAndNotOnItself() {
        List<Spread> spreads = PlantSweepRules.sweep(meadow(12, "ryegrass"), 24.0, 1f, false, new Random(7));
        assertThat(spreads).isNotEmpty();
        for (Spread s : spreads) {
            assertThat(s.to()).isNotEqualTo(s.from());
            assertThat(s.to().x() - 5).isBetween(0, 2);
            assertThat(s.to().z() - 5).isBetween(0, 2);
            assertThat(s.plant()).isEqualTo("ryegrass");
        }
        // a plant born in the sweep never spreads in it and no cell is taken twice
        assertThat(spreads.stream().map(Spread::to).distinct().count()).isEqualTo(spreads.size());
        assertThat(spreads.stream().allMatch(s -> s.from().equals(new BlockPos(5, 64, 5)))).isTrue();
    }

    @Test
    void winterAndZeroChanceStopTheSpread() {
        assertThat(PlantSweepRules.sweep(meadow(12, "ryegrass"), 24.0, 1f, true, new Random(7))).isEmpty();
        assertThat(PlantSweepRules.sweep(meadow(12, "ryegrass"), 24.0, 0f, false, new Random(7))).isEmpty();
        assertThat(PlantSweepRules.sweep(meadow(12, "ryegrass"), 0.0, 1f, false, new Random(7))).isEmpty();
    }

    @Test
    void denseMeadowsStopSpreading() {
        Map<BlockPos, Cell> cells = meadow(12, "ryegrass");
        for (int x = 3; x <= 8; x++) {
            for (int z = 3; z <= 8; z++) {
                if (x != 7 || z != 7) {
                    cells.put(new BlockPos(x, 64, z), new Cell(null, false, true, false, true));
                }
            }
        }
        // every cell around (7, 64, 7) is natural: more than max plants in its square, so nothing may be placed there
        assertThat(PlantSweepRules.spreadSelf(cells, new BlockPos(5, 64, 5), SpreadKind.SHORT, scripted(2, 2))).isNull();
    }

    @Test
    void aTallPlantNeedsAirAboveAndGround() {
        Map<BlockPos, Cell> cells = meadow(12, "rose");
        BlockPos target = new BlockPos(7, 64, 5);
        cells.put(target, new Cell(null, true, true, false, false)); // no air above
        assertThat(PlantSweepRules.spreadSelf(cells, new BlockPos(5, 64, 5), SpreadKind.TALL, scripted(2, 0))).isNull();
        cells.put(target, new Cell(null, true, false, true, false)); // no ground
        assertThat(PlantSweepRules.spreadSelf(cells, new BlockPos(5, 64, 5), SpreadKind.TALL, scripted(2, 0))).isNull();
        cells.put(target, new Cell(null, true, true, true, false));
        assertThat(PlantSweepRules.spreadSelf(cells, new BlockPos(5, 64, 5), SpreadKind.TALL, scripted(2, 0))).isEqualTo(target);
    }

    @Test
    void aTargetOutsideTheSnapshotCountsAsUnloaded() {
        Map<BlockPos, Cell> cells = meadow(8, "ryegrass");
        // the density square of (7, 64, 5) reaches x = 9, outside the 8 x 8 snapshot
        assertThat(PlantSweepRules.spreadSelf(cells, new BlockPos(5, 64, 5), SpreadKind.SHORT, scripted(2, 0))).isNull();
    }

    @Test
    void lambdaIsTheGrassSweepsPoissonRate() {
        assertThat(PlantSweepRules.lambda(4096, 3)).isEqualTo(3.0);
        assertThat(PlantSweepRules.lambda(10_000_000, 3)).isEqualTo(PlantSweepRules.MAX_LAMBDA);
        assertThat(PlantSweepRules.lambda(0, 3)).isZero();
    }

    @Test
    void theSweepIsReproducible() {
        assertThat(PlantSweepRules.sweep(meadow(12, "bluegrass"), 24.0, 1f, false, new Random(3)))
            .isEqualTo(PlantSweepRules.sweep(meadow(12, "bluegrass"), 24.0, 1f, false, new Random(3)));
    }
}
