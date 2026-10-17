/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.worldgen.surface;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.worldgen.biome.TfcBiomes;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** Properties of the surface builders that hold for any column, and the TFC anchors of the climate rule. */
class SurfacePropertyTest {
    private static final int SEA_LEVEL = 63;
    private static final int MIN_Y = -64;

    /** A random column, over the same ranges as the golden draw but wider. */
    private static SurfaceColumn randomColumn(Random r) {
        return new SurfaceColumn(r.nextInt(40000) - 20000, r.nextInt(40000) - 20000, SEA_LEVEL, r.nextDouble() * 20,
            -40f + r.nextFloat() * 70f, r.nextFloat() * 500f, r.nextFloat() * 300f, r.nextFloat() * 2 - 1,
            62 + r.nextInt(7), r.nextBoolean());
    }

    private static ColumnSurface build(SurfaceFamily family, SurfaceColumn column, int top) {
        return ColumnSurface.build(family.builder(), column, top, MIN_Y);
    }

    @Test
    void kindsStayInTheColumnAndTheKindRange() {
        final Random r = new Random(1);
        for (SurfaceFamily family : SurfaceFamily.values()) {
            for (int i = 0; i < 500; i++) {
                final SurfaceColumn column = randomColumn(r);
                final int top = 30 + r.nextInt(60);
                final ColumnSurface surface = build(family, column, top);
                assertThat(surface.endY()).isEqualTo(top - 19);
                int written = 0;
                for (int y = top + 40; y > top; y--) {
                    assertThat(surface.kind(y)).as("above the rock top").isNull();
                }
                for (int y = top; y >= top - 19; y--) {
                    final SurfaceKind kind = surface.kind(y);
                    if (kind != null) {
                        written++;
                        assertThat(SurfaceKind.values()).contains(kind);
                    }
                }
                assertThat(written).isLessThanOrEqualTo(20);
                assertThat(surface.kind(top - 20)).as("below the pass").isNull();
            }
        }
    }

    @Test
    void sameInputsGiveSameKindsEvenInParallel() {
        final Random r = new Random(2);
        final List<SurfaceColumn> columns = IntStream.range(0, 400).mapToObj(i -> randomColumn(r)).toList();
        final SurfaceFamily[] families = SurfaceFamily.values();
        final int[] tops = IntStream.range(0, columns.size()).map(i -> 40 + (i * 7) % 40).toArray();
        final String sequential = IntStream.range(0, columns.size())
            .mapToObj(i -> describe(build(families[i % families.length], columns.get(i), tops[i]), tops[i]))
            .collect(Collectors.joining("|"));
        final String parallel = IntStream.range(0, columns.size()).parallel()
            .mapToObj(i -> describe(build(families[i % families.length], columns.get(i), tops[i]), tops[i]))
            .collect(Collectors.joining("|"));
        assertThat(parallel).isEqualTo(sequential);
    }

    private static String describe(ColumnSurface surface, int top) {
        final StringBuilder out = new StringBuilder();
        for (int y = top; y >= top - 19; y--) {
            out.append(surface.kind(y) == null ? '.' : surface.kind(y).ordinal());
        }
        return out.toString();
    }

    @Test
    void columnsUnderTheWaterAreGravel() {
        // A rock top below the water is the underwater branch of TFC's normal builder, which only lays gravel
        final Random r = new Random(3);
        for (SurfaceFamily family : List.of(SurfaceFamily.NORMAL, SurfaceFamily.ROCKY, SurfaceFamily.VOLCANIC_SOIL)) {
            for (int i = 0; i < 300; i++) {
                final SurfaceColumn column = randomColumn(r);
                final int top = 20 + r.nextInt(SEA_LEVEL - 22);
                final ColumnSurface surface = build(family, column, top);
                for (int y = top; y >= top - 19; y--) {
                    final SurfaceKind kind = surface.kind(y);
                    assertThat(kind == null || kind == SurfaceKind.GRAVEL).as(family + " at " + y).isTrue();
                }
            }
        }
    }

    @Test
    void theClimateRuleAnchorsToTfc() {
        // Warm and wet land above sea level is grass on top, dry land is gravel, and frozen land is snow (TFC's regions)
        final SurfaceColumn warmWet = new SurfaceColumn(0, 0, SEA_LEVEL, 0, 20f, 400f, 0f, 0f, 63, false);
        final SurfaceColumn dry = new SurfaceColumn(0, 0, SEA_LEVEL, 0, 20f, 0f, 0f, 0f, 63, false);
        final SurfaceColumn frozen = new SurfaceColumn(0, 0, SEA_LEVEL, 0, -30f, 400f, 0f, 0f, 63, false);
        assertThat(build(SurfaceFamily.NORMAL, warmWet, 80).kind(80)).isEqualTo(SurfaceKind.GRASS);
        assertThat(build(SurfaceFamily.NORMAL, dry, 80).kind(80)).isEqualTo(SurfaceKind.GRAVEL);
        assertThat(build(SurfaceFamily.NORMAL, frozen, 80).kind(80)).isEqualTo(SurfaceKind.SNOW);
    }

    @Test
    void shoresAreSandOrGravelUnderTheTide() {
        // Beaches take the shore states: the top is sand or gravel, the layers under it sandstone or rock
        final SurfaceColumn column = new SurfaceColumn(5, 9, SEA_LEVEL, 0, 15f, 200f, 0f, 0f, 63, false);
        final ColumnSurface surface = build(SurfaceFamily.SHORE_SANDY, column, 63);
        assertThat(surface.kind(63)).isIn(SurfaceKind.SAND, SurfaceKind.GRAVEL);
        for (int y = 62; y >= 44; y--) {
            assertThat(surface.kind(y)).isIn(null, SurfaceKind.SAND, SurfaceKind.SANDSTONE, SurfaceKind.GRAVEL, SurfaceKind.ROCK_SURFACE);
        }
    }

    @Test
    void slopeOfAFlatChunkIsZeroAndOfALinearFieldIsItsCellSlope() {
        final double[] flat = SurfaceSlope.slopeMap(64, -32, (x, z) -> 70);
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                assertThat(SurfaceSlope.sample(flat, x, z)).isZero();
            }
        }
        // Heights 2x: each cell's four samples are 8 apart along x, so the distances from their mean sum to 16, and the column
        // slope is 0.8 of that everywhere in the chunk.
        final double[] linear = SurfaceSlope.slopeMap(64, -32, (x, z) -> 2 * x);
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                assertThat(SurfaceSlope.sample(linear, x, z)).isCloseTo(16 * 0.8, org.assertj.core.data.Offset.offset(1e-6));
            }
        }
    }

    @Test
    void theBiomeTableNamesTheTfcBuilders() {
        assertThat(SurfaceFamily.forBiome(TfcBiomes.PLAINS)).isEqualTo(SurfaceFamily.NORMAL);
        assertThat(SurfaceFamily.forBiome(TfcBiomes.OCEAN)).isEqualTo(SurfaceFamily.OCEAN);
        assertThat(SurfaceFamily.forBiome(TfcBiomes.SHORE)).isEqualTo(SurfaceFamily.SHORE_SANDY);
        assertThat(SurfaceFamily.forBiome(TfcBiomes.SEA_STACKS)).isEqualTo(SurfaceFamily.SEA_CLIFFS);
        assertThat(SurfaceFamily.forBiome(TfcBiomes.ROCKY_SHORES)).isEqualTo(SurfaceFamily.ROCKY_COAST);
        assertThat(SurfaceFamily.forBiome(TfcBiomes.OCEANIC_MOUNTAINS)).isEqualTo(SurfaceFamily.MOUNTAIN_SHORE);
        assertThat(SurfaceFamily.forBiome(TfcBiomes.COLLISIONAL_MOUNTAINS)).isEqualTo(SurfaceFamily.ROCKY);
        assertThat(SurfaceFamily.forBiome(TfcBiomes.CANYONS)).isEqualTo(SurfaceFamily.VOLCANIC_SOIL);
        assertThat(SurfaceFamily.forBiome(TfcBiomes.LOWLANDS)).as("unported builder falls back").isEqualTo(SurfaceFamily.NORMAL);
        for (int id = 0; id < TfcBiomes.COUNT; id++) {
            assertThat(SurfaceFamily.forBiome(id)).isNotNull();
        }
    }
}
