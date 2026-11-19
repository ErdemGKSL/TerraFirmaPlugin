/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.worldgen.volcano;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.worldgen.biome.TfcBiomes;
import dev.tfp.core.worldgen.chunk.PlaceholderRocks;
import dev.tfp.core.worldgen.random.XoroshiroRandomSource;
import dev.tfp.core.worldgen.tree.TreeGround;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.IntBinaryOperator;
import org.junit.jupiter.api.Test;

/**
 * The springs, rivulets, crater lakes, groundcover patches and stratovolcano placements of PAR-WORLD-14, on small synthetic
 * worlds: each rule against the behaviour of TFC's feature (the configs themselves are checked against TFC's table by
 * {@code VolcanoFeatureGoldenTest}).
 */
class VolcanicFeaturesTest {
    private static final int BASALT = PlaceholderRocks.names().indexOf("basalt");
    private static final int SHALE = PlaceholderRocks.names().indexOf("shale");
    private static final int TUFF = PlaceholderRocks.names().indexOf("tuff");
    private static final int ANDESITE = PlaceholderRocks.names().indexOf("andesite");

    /** A world of one raw rock with a surface height function, air above it (water up to {@code water}), one biome. */
    private static class Shape extends VolcanoTestView {
        final IntBinaryOperator top;
        final int rock;
        final int biome;
        int water = Integer.MIN_VALUE;
        final Set<Long> holes = new HashSet<>();
        final Set<Long> packedIce = new HashSet<>();

        Shape(IntBinaryOperator top, int rock, int biome) {
            this.top = top;
            this.rock = rock;
            this.biome = biome;
        }

        @Override
        public TreeGround ground(int x, int y, int z) {
            if (holes.contains(VolcanoLevel.key(x, y, z))) {
                return TreeGround.AIR;
            }
            if (packedIce.contains(VolcanoLevel.key(x, y, z))) {
                return TreeGround.SOLID;
            }
            if (y <= top.applyAsInt(x, z)) {
                return TreeGround.STONE;
            }
            return y <= water ? TreeGround.WATER : TreeGround.AIR;
        }

        @Override
        public boolean packedIce(int x, int y, int z) {
            return packedIce.contains(VolcanoLevel.key(x, y, z));
        }

        @Override
        public int rockAt(int x, int y, int z) {
            return rock;
        }

        @Override
        public int oceanFloor(int x, int z) {
            return top.applyAsInt(x, z) + 1;
        }

        @Override
        public int worldSurface(int x, int z) {
            return Math.max(top.applyAsInt(x, z), water) + 1;
        }

        @Override
        public int biome(int x, int z) {
            return biome;
        }
    }

    private static Map<Long, VolcanicBlock> byPos(List<VolcanicBlock> blocks) {
        final Map<Long, VolcanicBlock> out = new HashMap<>();
        for (VolcanicBlock b : blocks) {
            assertThat(out.put(VolcanoLevel.key(b.x(), b.y(), b.z()), b)).as("one block per position").isNull();
        }
        return out;
    }

    // ---- rivulets ----

    @Test
    void rivuletRunsDownTheSlopeAndLaysMagmaUnderTheCut() {
        // A slope falling to the east: the surface drops one block every two columns
        final Shape view = new Shape((x, z) -> 100 - Math.floorDiv(x, 2), BASALT, TfcBiomes.CANYONS);
        int longest = 0;
        for (long seed = 0; seed < 20; seed++) {
            final VolcanoLevel level = new VolcanoLevel(view);
            assertThat(Rivulet.place(level, new XoroshiroRandomSource(seed, 7), 4, 4, Rivulet.Config.BASALT)).isTrue();
            final Map<Long, VolcanicBlock> blocks = byPos(level.volcanicBlocks());
            int cuts = 0;
            for (VolcanicBlock b : blocks.values()) {
                final int top = view.top.applyAsInt(b.x(), b.z());
                // Inside the feature's box: the chunk and 14 blocks around it
                assertThat(b.x()).isBetween(-14, 29);
                assertThat(b.z()).isBetween(-14, 29);
                // Never uphill of the start
                assertThat(top).isLessThanOrEqualTo(view.top.applyAsInt(4, 4));
                if (b.kind() == VolcanicKind.AIR) {
                    assertThat(b.y()).as("the cut is the top block").isEqualTo(top);
                    cuts++;
                    for (int dy = 1; dy <= 2; dy++) {
                        final VolcanicBlock magma = blocks.get(VolcanoLevel.key(b.x(), top - dy, b.z()));
                        assertThat(magma).isNotNull();
                        assertThat(magma.kind()).isEqualTo(VolcanicKind.MAGMA);
                        assertThat(magma.rock()).isEqualTo(BASALT);
                    }
                } else {
                    assertThat(b.kind()).isEqualTo(VolcanicKind.MAGMA);
                }
            }
            // The start and its first step at least: a branch ends where it steps back onto the path
            assertThat(cuts).isGreaterThanOrEqualTo(2);
            assertThat(blocks).hasSize(cuts * 3);
            assertThat(blocks).containsKey(VolcanoLevel.key(4, view.top.applyAsInt(4, 4), 4));
            longest = Math.max(longest, cuts);
        }
        // The main branch is up to 24 steps long
        assertThat(longest).isGreaterThanOrEqualTo(10);
    }

    @Test
    void rivuletLeavesRocksItsConfigDoesNotName() {
        final IntBinaryOperator slope = (x, z) -> 100 - Math.floorDiv(x, 2);
        // volcano_rivulet replaces basalt only
        final VolcanoLevel shale = new VolcanoLevel(new Shape(slope, SHALE, TfcBiomes.CANYONS));
        assertThat(Rivulet.place(shale, new XoroshiroRandomSource(1, 2), 4, 4, Rivulet.Config.BASALT)).isTrue();
        assertThat(shale.volcanicBlocks()).isNotEmpty().allSatisfy(b -> assertThat(b.kind()).isEqualTo(VolcanicKind.AIR));
        // stratovolcano_rivulet turns tuff into andesite magma, sulfur_rivulet into tuff sulfur ore
        final VolcanoLevel tuff = new VolcanoLevel(new Shape(slope, TUFF, TfcBiomes.VOLCANIC_MOUNTAINS));
        Rivulet.place(tuff, new XoroshiroRandomSource(1, 2), 4, 4, Rivulet.Config.STRATOVOLCANO);
        assertThat(tuff.volcanicBlocks()).anySatisfy(b -> assertThat(b.kind()).isEqualTo(VolcanicKind.MAGMA))
            .allSatisfy(b -> assertThat(b.kind() == VolcanicKind.AIR || b.rock() == ANDESITE).isTrue());
        final VolcanoLevel sulfur = new VolcanoLevel(new Shape(slope, TUFF, TfcBiomes.VOLCANIC_MOUNTAINS));
        Rivulet.place(sulfur, new XoroshiroRandomSource(1, 2), 4, 4, Rivulet.Config.SULFUR);
        assertThat(sulfur.volcanicBlocks()).anySatisfy(b -> assertThat(b.kind()).isEqualTo(VolcanicKind.SULFUR_ORE))
            .allSatisfy(b -> assertThat(b.kind() == VolcanicKind.AIR || b.rock() == TUFF).isTrue());
        // Both runs drew the same path
        assertThat(sulfur.volcanicBlocks()).hasSameSizeAs(tuff.volcanicBlocks());
    }

    @Test
    void rivuletConfigsNameTfcsRocks() {
        assertThat(Rivulet.Config.BASALT.states()).isEqualTo(Map.of("basalt", "basalt"));
        assertThat(Rivulet.Config.OCEAN_RIDGE.states().keySet())
            .containsExactlyInAnyOrder("rhyolite", "basalt", "andesite", "dacite", "granite", "diorite", "gabbro");
        assertThat(Rivulet.Config.OCEAN_RIDGE.states()).allSatisfy((from, to) -> assertThat(to).isEqualTo(from));
        assertThat(Rivulet.Config.STRATOVOLCANO.states()).isEqualTo(Map.of("andesite", "andesite", "dacite", "dacite",
            "basalt", "basalt", "rhyolite", "rhyolite", "tuff", "andesite"));
        assertThat(Rivulet.Config.SULFUR.states()).isEqualTo(Map.of("andesite", "andesite", "dacite", "dacite",
            "basalt", "basalt", "rhyolite", "rhyolite", "tuff", "tuff"));
        assertThat(Rivulet.Config.SULFUR.result()).isEqualTo(VolcanicKind.SULFUR_ORE);
    }

    @Test
    void rivuletDoesNotStartOrRunOverAFluid() {
        final Shape view = new Shape((x, z) -> 60, BASALT, TfcBiomes.OCEAN_RIDGE);
        view.water = 63;
        // The block under the start is rock, so it starts. On a flat sea floor the cut's neighbours are rock, so TFC's
        // replace state is air (the water above flows in on its first update)
        final VolcanoLevel level = new VolcanoLevel(view);
        assertThat(Rivulet.place(level, new XoroshiroRandomSource(3, 4), 4, 4, Rivulet.Config.OCEAN_RIDGE)).isTrue();
        assertThat(level.volcanicBlocks()).allSatisfy(b -> assertThat(b.kind()).isIn(VolcanicKind.AIR, VolcanicKind.MAGMA));
        // On a stepped floor a cut beside the water of the lower step takes that water
        final Shape steps = new Shape((x, z) -> 55 - Math.floorDiv(x, 2), BASALT, TfcBiomes.OCEAN_RIDGE);
        steps.water = 63;
        boolean water = false;
        for (long seed = 0; seed < 20 && !water; seed++) {
            final VolcanoLevel stepped = new VolcanoLevel(steps);
            Rivulet.place(stepped, new XoroshiroRandomSource(seed, 4), 4, 4, Rivulet.Config.OCEAN_RIDGE);
            water = stepped.volcanicBlocks().stream().anyMatch(b -> b.kind() == VolcanicKind.WATER);
        }
        assertThat(water).isTrue();
        // A lava lake placed first: the start column's floor is lava, so no rivulet
        final VolcanoLevel lava = new VolcanoLevel(view);
        lava.set(4, 60, 4, VolcanicKind.LAVA);
        assertThat(Rivulet.place(lava, new XoroshiroRandomSource(3, 4), 4, 4, Rivulet.Config.OCEAN_RIDGE)).isFalse();
        assertThat(lava.volcanicBlocks()).hasSize(1);
    }

    // ---- springs ----

    /** A cliff face at x = 0: rock for x <= 0 up to y 100, floor at 60 beyond. */
    private static Shape cliff(int rock) {
        return new Shape((x, z) -> x <= 0 ? 100 : 60, rock, TfcBiomes.PLAINS);
    }

    @Test
    void springNeedsFourRocksAndOneHole() {
        final Shape view = cliff(SHALE);
        // In the face itself: rock above and below, north and south and west, air to the east: five rocks, no spring
        VolcanoLevel level = new VolcanoLevel(view);
        assertThat(Spring.place(level, 0, 80, 0, Spring.Config.WATER)).isFalse();
        // With the block to the north turned into gravel by another feature: four rocks, one hole
        level = new VolcanoLevel(view);
        level.set(0, 80, -1, VolcanicKind.GRAVEL, SHALE, null);
        assertThat(Spring.place(level, 0, 80, 0, Spring.Config.WATER)).isTrue();
        final Map<Long, VolcanicBlock> blocks = byPos(level.volcanicBlocks());
        assertThat(blocks.get(VolcanoLevel.key(0, 80, 0)).kind()).isEqualTo(VolcanicKind.WATER);
        final VolcanicBlock above = blocks.get(VolcanoLevel.key(0, 81, 0));
        assertThat(above.kind()).isEqualTo(VolcanicKind.HARDENED_ROCK);
        assertThat(above.rock()).isEqualTo(SHALE);
        assertThat(blocks).hasSize(3);
        // Two holes: none
        level = new VolcanoLevel(view);
        level.set(0, 80, -1, VolcanicKind.AIR);
        assertThat(Spring.place(level, 0, 80, 0, Spring.Config.LAVA)).isFalse();
        // In open air, and deep inside the rock (six rocks): none
        assertThat(Spring.place(new VolcanoLevel(view), 5, 80, 0, Spring.Config.WATER)).isFalse();
        assertThat(Spring.place(new VolcanoLevel(view), -5, 80, 0, Spring.Config.WATER)).isFalse();
    }

    @Test
    void lavaSpringPlacesLavaAndGlacialSpringsNeedPackedIce() {
        final Shape view = cliff(BASALT);
        VolcanoLevel level = new VolcanoLevel(view);
        level.set(0, 80, 1, VolcanicKind.DIRT);
        assertThat(Spring.place(level, 0, 80, 0, Spring.Config.LAVA)).isTrue();
        assertThat(byPos(level.volcanicBlocks()).get(VolcanoLevel.key(0, 80, 0)).kind()).isEqualTo(VolcanicKind.LAVA);
        // Raw rock is not a glacial spring's block
        level = new VolcanoLevel(view);
        level.set(0, 80, 1, VolcanicKind.DIRT);
        assertThat(Spring.place(level, 0, 80, 0, Spring.Config.GLACIAL)).isFalse();
        assertThat(Spring.place(level, 0, 80, 0, Spring.Config.ICE_SHEET)).isFalse();
        // A wall of packed ice: water, and no hardened rock above
        for (int y = 78; y <= 82; y++) {
            for (int z = -2; z <= 2; z++) {
                for (int x = -2; x <= 0; x++) {
                    view.packedIce.add(VolcanoLevel.key(x, y, z));
                }
            }
        }
        level = new VolcanoLevel(view);
        level.set(0, 80, 1, VolcanicKind.DIRT);
        assertThat(Spring.place(level, 0, 80, 0, Spring.Config.GLACIAL)).isTrue();
        assertThat(level.volcanicBlocks()).hasSize(2);
        assertThat(Spring.place(new VolcanoLevel(view), 0, 80, 0, Spring.Config.ICE_SHEET)).isFalse();
        assertThat(Spring.Valid.RAW_ROCK.size()).isEqualTo(21);
        assertThat(Spring.Valid.PACKED_ICE.size()).isEqualTo(1);
    }

    // ---- flood fill lakes ----

    /** A crater: a bowl of radius 6 around (8, 8) with its floor at 70 and the land around at 80. */
    private static Shape crater() {
        return new Shape((x, z) -> (x - 8) * (x - 8) + (z - 8) * (z - 8) <= 36 ? 70 : 80, ANDESITE, TfcBiomes.VOLCANIC_MOUNTAINS);
    }

    @Test
    void craterLakesFillToTheRimWithTheirFluid() {
        final EnumMap<VolcanicKind, Caldera.Config> configs = new EnumMap<>(VolcanicKind.class);
        configs.put(VolcanicKind.LAVA, Caldera.Config.LAVA);
        configs.put(VolcanicKind.SPRING_WATER, Caldera.Config.SPRING_WATER);
        configs.put(VolcanicKind.WATER, Caldera.Config.WATER);
        final Shape view = crater();
        for (Map.Entry<VolcanicKind, Caldera.Config> e : configs.entrySet()) {
            final VolcanoLevel level = new VolcanoLevel(view);
            assertThat(Caldera.place(level, 8, view.worldSurface(8, 8), 8, e.getValue())).isTrue();
            final List<VolcanicBlock> blocks = level.volcanicBlocks();
            assertThat(blocks).allSatisfy(b -> {
                assertThat(b.kind()).isEqualTo(e.getKey());
                assertThat(b.y()).isBetween(71, 80);
                assertThat((b.x() - 8) * (b.x() - 8) + (b.z() - 8) * (b.z() - 8)).isLessThanOrEqualTo(36);
            });
            // The first layer and every overfilled layer up to the rim: 113 columns, 10 layers
            assertThat(blocks).hasSize(113 * 10);
        }
    }

    @Test
    void onlyLavaAndSpringWaterReplaceWater() {
        final Shape view = crater();
        view.water = 74;
        final VolcanoLevel lava = new VolcanoLevel(view);
        assertThat(Caldera.place(lava, 8, 71, 8, Caldera.Config.LAVA)).isTrue();
        assertThat(lava.volcanicBlocks()).anySatisfy(b -> assertThat(b.y()).isEqualTo(71));
        // kelimutu_water replaces no fluid: the start position holds water, so nothing is filled
        final VolcanoLevel water = new VolcanoLevel(view);
        assertThat(Caldera.place(water, 8, 71, 8, Caldera.Config.WATER)).isFalse();
        assertThat(water.volcanicBlocks()).isEmpty();
    }

    // ---- groundcover patches ----

    @Test
    void patchPlacesGroundcoverOnSturdyGroundWithinTenBlocks() {
        final Shape view = new Shape((x, z) -> 70 + Math.floorMod(x + z, 3), BASALT, TfcBiomes.ACTIVE_SHIELD_VOLCANO);
        for (long seed = 0; seed < 30; seed++) {
            final VolcanoLevel level = new VolcanoLevel(view);
            final int placed = GroundcoverPatch.place(level, new XoroshiroRandomSource(seed, 1), 100, -40, VolcanicKind.PUMICE);
            final List<VolcanicBlock> blocks = level.volcanicBlocks();
            // A try on a column an earlier try took places nothing
            assertThat(blocks).hasSize(placed);
            assertThat(placed).isBetween(1, GroundcoverPatch.TRIES);
            assertThat(blocks).allSatisfy(b -> {
                assertThat(b.kind()).isEqualTo(VolcanicKind.PUMICE);
                assertThat(Math.abs(b.x() - 100)).isLessThanOrEqualTo(GroundcoverPatch.XZ_SPREAD);
                assertThat(Math.abs(b.z() + 40)).isLessThanOrEqualTo(GroundcoverPatch.XZ_SPREAD);
                assertThat(b.y()).isEqualTo(view.worldSurface(b.x(), b.z()));
            });
        }
        // Over water the floor is not sturdy: nothing
        final Shape sea = new Shape((x, z) -> 50, BASALT, TfcBiomes.SHIELD_VOLCANO_SHORE);
        sea.water = 63;
        final VolcanoLevel level = new VolcanoLevel(sea);
        assertThat(GroundcoverPatch.place(level, new XoroshiroRandomSource(5, 1), 0, 0, VolcanicKind.OBSIDIAN_SHARD)).isZero();
        // The patch draws six numbers per try, whatever it places
        final XoroshiroRandomSource a = new XoroshiroRandomSource(9, 9);
        final XoroshiroRandomSource b = new XoroshiroRandomSource(9, 9);
        GroundcoverPatch.place(new VolcanoLevel(sea), a, 0, 0, VolcanicKind.PUMICE);
        for (int i = 0; i < 10; i++) {
            b.nextInt(11);
            b.nextInt(11);
            b.nextInt(2);
            b.nextInt(2);
            b.nextInt(11);
            b.nextInt(11);
        }
        assertThat(a.nextLong()).isEqualTo(b.nextLong());
    }

    // ---- stratovolcano and tuya placements ----

    @Test
    void stratovolcanoCentersLieInTheirChunkAndFollowTheHashRanges() {
        final Shape view = new Shape((x, z) -> 70, ANDESITE, TfcBiomes.VOLCANIC_OCEANIC_MOUNTAINS);
        final Stratovolcanoes s = new Stratovolcanoes(42L);
        final EnumMap<Stratovolcanoes.Variant, Integer> variants = new EnumMap<>(Stratovolcanoes.Variant.class);
        int lava = 0;
        int spring = 0;
        int water = 0;
        int offsetCenters = 0;
        for (int cx = -150; cx < 150; cx++) {
            for (int cz = -150; cz < 150; cz++) {
                final int x0 = cx << 4;
                final int z0 = cz << 4;
                final long any = s.center(Stratovolcanoes.Placement.center(null, true, 0f, 1f), view, x0, z0);
                if (any == Long.MIN_VALUE) {
                    continue;
                }
                assertThat(CinderCones.unpackX(any) >> 4).isEqualTo(cx);
                assertThat(CinderCones.unpackZ(any) >> 4).isEqualTo(cz);
                final Stratovolcanoes.Variant variant = s.variantAt(x0, z0);
                variants.merge(variant, 1, Integer::sum);
                // The lava, spring water and water lakes of one crater exclude each other
                int lakes = 0;
                for (float[] range : new float[][] {{0f, 0.25f}, {0.25f, 0.75f}, {0.75f, 1f}}) {
                    final long c = s.center(Stratovolcanoes.Placement.center(Stratovolcanoes.Variant.KELIMUTU, true, range[0], range[1]),
                        view, x0, z0);
                    if (c != Long.MIN_VALUE) {
                        assertThat(variant).isEqualTo(Stratovolcanoes.Variant.KELIMUTU);
                        assertThat(c).isEqualTo(any);
                        lakes++;
                        if (range[0] == 0f) {
                            lava++;
                        } else if (range[0] == 0.25f) {
                            spring++;
                        } else {
                            water++;
                        }
                    }
                }
                assertThat(lakes).isEqualTo(variant == Stratovolcanoes.Variant.KELIMUTU ? 1 : 0);
                // Without the offset, the center is the cell's own (or none, when the offset center was another crater)
                final long plain = s.center(Stratovolcanoes.Placement.center(null, false, 0f, 1f), view, x0, z0);
                if (plain != any) {
                    offsetCenters++;
                    assertThat(variant).isIn(Stratovolcanoes.Variant.KELIMUTU, Stratovolcanoes.Variant.CRATER_LAKE);
                }
                // A wrong variant never passes
                final Stratovolcanoes.Variant other = variant == Stratovolcanoes.Variant.FUJI ? Stratovolcanoes.Variant.TAHOMA
                    : Stratovolcanoes.Variant.FUJI;
                assertThat(s.center(Stratovolcanoes.Placement.center(other, true, 0f, 1f), view, x0, z0)).isEqualTo(Long.MIN_VALUE);
            }
        }
        assertThat(variants.keySet()).contains(Stratovolcanoes.Variant.FUJI, Stratovolcanoes.Variant.KELIMUTU);
        assertThat(lava + spring + water).isPositive();
        assertThat(offsetCenters).isPositive();
        // No stratovolcano biome: no center, no column near one
        final Shape plains = new Shape((x, z) -> 70, ANDESITE, TfcBiomes.PLAINS);
        for (int c = -40; c < 40; c++) {
            assertThat(s.center(Stratovolcanoes.Placement.center(null, true, 0f, 1f), plains, c << 4, c << 3)).isEqualTo(Long.MIN_VALUE);
            assertThat(s.test(Stratovolcanoes.Placement.near(null, 0f, 0f, 1f), plains, c << 4, c << 3)).isFalse();
        }
    }

    @Test
    void stratovolcanoDistancePlacementIsARingAroundTheCenter() {
        final Shape view = new Shape((x, z) -> 70, ANDESITE, TfcBiomes.VOLCANIC_OCEANIC_MOUNTAINS);
        final Stratovolcanoes s = new Stratovolcanoes(7L);
        final Stratovolcanoes.Placement near = Stratovolcanoes.Placement.near(null, 0.8f, 0f, 1f);
        int inside = 0;
        for (int x = -2000; x < 2000; x += 7) {
            for (int z = -2000; z < 2000; z += 7) {
                final float easing = s.easing(x, z, 1f);
                final boolean ok = s.test(near, view, x, z);
                assertThat(ok).isEqualTo(easing > 0.8f && easing < 1f);
                inside += ok ? 1 : 0;
            }
        }
        assertThat(inside).isPositive();
    }

    @Test
    void biomeTablesMatchTfc() {
        // TFCBiomes: tuyas(1/2 or 1/3, ...) and stratovolcanoes(frequency, ...)
        assertThat(Tuyas.frequency(TfcBiomes.TUYAS)).isEqualTo(0.5f);
        assertThat(Tuyas.frequency(TfcBiomes.ICE_SHEET_TUYAS)).isEqualTo(1f / 3);
        assertThat(Tuyas.frequency(TfcBiomes.ICE_SHEET_TUYAS_EDGE)).isEqualTo(1f / 3f);
        assertThat(Stratovolcanoes.frequency(TfcBiomes.OCEANIC_VOLCANIC_ARC)).isEqualTo(0.7f);
        assertThat(Stratovolcanoes.frequency(TfcBiomes.VOLCANIC_OCEANIC_MOUNTAINS)).isEqualTo(1f);
        int tuyas = 0;
        int strato = 0;
        int shield = 0;
        int volcanic = 0;
        for (int biome = 0; biome < TfcBiomes.COUNT; biome++) {
            tuyas += Tuyas.hasTuyas(biome) ? 1 : 0;
            strato += Stratovolcanoes.hasStratovolcanoes(biome) ? 1 : 0;
            shield += VolcanicFeatures.hasShieldFeatures(biome) ? 1 : 0;
            volcanic += VolcanicFeatures.hasVolcanicFeatures(biome) ? 1 : 0;
            assertThat(Tuyas.hasTuyas(biome)).isEqualTo(Tuyas.frequency(biome) != 0);
        }
        // The in_biome/large_features lists at c3b4d9536: 3 with #feature/tuyas, 12 with #feature/stratovolcanoes, 7 with
        // #feature/shield_volcanoes, 20 with #feature/volcanic (the 5 cinder cone biomes too)
        assertThat(tuyas).isEqualTo(3);
        assertThat(strato).isEqualTo(12);
        assertThat(shield).isEqualTo(7);
        assertThat(volcanic).isEqualTo(20);
    }

    // ---- the whole feature list ----

    private static final int[] BIOMES = {TfcBiomes.CANYONS, TfcBiomes.TUYAS, TfcBiomes.VOLCANIC_OCEANIC_MOUNTAINS,
        TfcBiomes.ACTIVE_SHIELD_VOLCANO, TfcBiomes.OCEAN_RIDGE, TfcBiomes.PLAINS};

    private static VolcanoTestView only(int biome) {
        return new VolcanoTestView() {
            @Override
            public int biome(int x, int z) {
                return biome;
            }
        };
    }

    @Test
    void decisionsAreDeterministicAndThreadSafe() throws Exception {
        final VolcanicFeatures features = new VolcanicFeatures(42L);
        for (int biome : BIOMES) {
            final VolcanoView view = only(biome);
            final List<VolcanicBlock> expected = new ArrayList<>();
            for (int c = -6; c < 6; c++) {
                expected.addAll(features.decorate(c, 3 * c, view));
            }
            final ExecutorService pool = Executors.newFixedThreadPool(4);
            try {
                final List<Future<List<VolcanicBlock>>> runs = new ArrayList<>();
                for (int t = 0; t < 4; t++) {
                    runs.add(pool.submit(() -> {
                        final List<VolcanicBlock> out = new ArrayList<>();
                        for (int c = -6; c < 6; c++) {
                            out.addAll(features.decorate(c, 3 * c, view));
                        }
                        return out;
                    }));
                }
                for (Future<List<VolcanicBlock>> run : runs) {
                    assertThat(run.get()).isEqualTo(expected);
                }
            } finally {
                pool.shutdown();
            }
            assertThat(new VolcanicFeatures(43L).decorate(0, 0, view)).isNotNull();
        }
    }

    @Test
    void everyFamilyPlacesItsBlocksAndOnlyThose() {
        final VolcanicFeatures features = new VolcanicFeatures(1234L);
        final Map<Integer, Set<VolcanicKind>> kinds = new HashMap<>();
        for (int biome : BIOMES) {
            final VolcanoView view = only(biome);
            final Set<VolcanicKind> seen = new HashSet<>();
            for (int cx = -20; cx < 20; cx++) {
                for (int cz = -20; cz < 20; cz++) {
                    for (VolcanicBlock b : features.decorate(cx, cz, view)) {
                        seen.add(b.kind());
                        assertThat(b.y()).isBetween(view.minY(), 319);
                        // Each feature stays within 14 blocks of its chunk (a fissure wanders 6 more from its center)
                        assertThat(b.x()).isBetween((cx << 4) - 22, (cx << 4) + 37);
                        assertThat(b.z()).isBetween((cz << 4) - 22, (cz << 4) + 37);
                    }
                }
            }
            kinds.put(biome, seen);
        }
        // Groundcover: the cinder cone, tuya, stratovolcano and shield volcano biomes only
        assertThat(kinds.get(TfcBiomes.ACTIVE_SHIELD_VOLCANO)).contains(VolcanicKind.PUMICE, VolcanicKind.OBSIDIAN_SHARD);
        assertThat(kinds.get(TfcBiomes.PLAINS)).doesNotContain(VolcanicKind.PUMICE, VolcanicKind.OBSIDIAN_SHARD, VolcanicKind.LAVA,
            VolcanicKind.MAGMA, VolcanicKind.SPRING_WATER, VolcanicKind.SULFUR_ORE, VolcanicKind.GEM_ORE);
        assertThat(kinds.get(TfcBiomes.OCEAN_RIDGE)).doesNotContain(VolcanicKind.PUMICE, VolcanicKind.OBSIDIAN_SHARD,
            VolcanicKind.SPRING_WATER, VolcanicKind.SULFUR_ORE, VolcanicKind.GEM_ORE);
        // Sulfur ore is a crater lake rivulet's only
        for (int biome : BIOMES) {
            if (biome != TfcBiomes.VOLCANIC_OCEANIC_MOUNTAINS) {
                assertThat(kinds.get(biome)).doesNotContain(VolcanicKind.SULFUR_ORE);
            }
        }
    }

    @Test
    void placeGivesTheChunksOwnBlocksOfItsNeighbourhood() {
        final VolcanicFeatures features = new VolcanicFeatures(77L);
        for (int biome : new int[] {TfcBiomes.CANYONS, TfcBiomes.VOLCANIC_OCEANIC_MOUNTAINS, TfcBiomes.TUYAS}) {
            final VolcanoView view = only(biome);
            int total = 0;
            for (int cx = -8; cx < 8; cx++) {
                for (int cz = -8; cz < 8; cz++) {
                    final List<VolcanicBlock> placed = new ArrayList<>();
                    features.place(cx, cz, view, placed::add);
                    final int chunkX = cx;
                    final int chunkZ = cz;
                    assertThat(placed).allSatisfy(b -> assertThat(b.inChunk(chunkX, chunkZ)).isTrue());
                    // The chunk's own features, springs included, are all there
                    final List<VolcanicBlock> own = new ArrayList<>();
                    for (VolcanicBlock b : features.decorate(cx, cz, view)) {
                        if (b.inChunk(cx, cz)) {
                            own.add(b);
                        }
                    }
                    assertThat(placed).containsAll(own);
                    total += placed.size();
                }
            }
            assertThat(total).isPositive();
        }
    }

    @Test
    void coneBiomesKeepTheCalderaFissureAndHotSpringRules() {
        // In a biome with hot springs only, the features are Volcanoes' own, block for block
        final VolcanoView view = only(TfcBiomes.HIGHLANDS);
        final Volcanoes volcanoes = new Volcanoes(5L);
        final VolcanicFeatures features = new VolcanicFeatures(5L, volcanoes);
        int springs = 0;
        for (int cx = -40; cx < 40; cx++) {
            for (int cz = -40; cz < 40; cz++) {
                final VolcanoLevel level = new VolcanoLevel(view);
                features.largeFeatures(level, cx, cz);
                assertThat(level.blocks()).isEqualTo(volcanoes.decorate(cx, cz, view));
                springs += level.blocks().isEmpty() ? 0 : 1;
            }
        }
        assertThat(springs).isPositive();
        assertThat(VolcanicFeatures.iceAndSnow()).isTrue();
    }

    @Test
    void kindsMapToTheLegacyKinds() {
        for (VolcanoKind kind : VolcanoKind.values()) {
            final VolcanicKind k = VolcanicKind.of(kind);
            assertThat(k.legacy()).isEqualTo(kind);
            assertThat(k.hasRock()).isEqualTo(kind.hasRock());
            assertThat(k.isFluid()).isEqualTo(kind.isFluid());
        }
        assertThat(VolcanicKind.PUMICE.isSolid()).isFalse();
        assertThat(VolcanicKind.WATER.isFluid()).isTrue();
        assertThat(VolcanicKind.SULFUR_ORE.hasRock()).isTrue();
    }
}
