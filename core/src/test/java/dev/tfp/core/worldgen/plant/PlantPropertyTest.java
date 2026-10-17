/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.worldgen.plant;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.worldgen.biome.TfcBiomes;
import dev.tfp.core.worldgen.groundcover.ClimateFilter;
import dev.tfp.core.worldgen.random.XoroshiroRandomSource;
import dev.tfp.core.worldgen.tree.TreeGround;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

/** Properties of the plant, wild crop and coral decisions over the synthetic terrain of {@link PlantTestView}. */
class PlantPropertyTest {
    private static final long SEED = 20260517L;

    private static List<PlantBlock> chunk(Plants plants, PlantTestView view, int cx, int cz, Plants.Step step) {
        final List<PlantBlock> out = new ArrayList<>();
        plants.place(cx, cz, view, step, out::add);
        return out;
    }

    private static List<PlantBlock> area(Plants plants, PlantTestView view, Plants.Step step) {
        final List<PlantBlock> out = new ArrayList<>();
        for (int cx = -3; cx < 3; cx++) {
            for (int cz = -3; cz < 3; cz++) {
                out.addAll(chunk(plants, view, cx, cz, step));
            }
        }
        return out;
    }

    @Test
    void deterministicPerSeedAndAcrossInstances() {
        final PlantTestView view = new PlantTestView(TfcBiomes.PLAINS);
        final Plants a = new Plants(SEED, PlantTestData.data());
        final Plants b = new Plants(SEED, PlantTestData.data());
        final List<PlantBlock> first = area(a, view, Plants.Step.SURFACE_DECORATION);
        assertThat(first).isNotEmpty();
        assertThat(area(a, view, Plants.Step.SURFACE_DECORATION)).isEqualTo(first);
        assertThat(area(b, view, Plants.Step.SURFACE_DECORATION)).isEqualTo(first);
        assertThat(area(new Plants(SEED + 1, PlantTestData.data()), view, Plants.Step.SURFACE_DECORATION)).isNotEqualTo(first);
    }

    @Test
    void sameResultOnFourThreads() throws Exception {
        final PlantTestView view = new PlantTestView(TfcBiomes.OCEAN_REEF);
        final Plants plants = new Plants(SEED, PlantTestData.data());
        final List<PlantBlock> expected = area(plants, view, Plants.Step.LARGE_FEATURES);
        assertThat(expected).isNotEmpty();
        final ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            final List<Future<List<PlantBlock>>> futures = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                futures.add(pool.submit(() -> area(plants, view, Plants.Step.LARGE_FEATURES)));
            }
            for (Future<List<PlantBlock>> f : futures) {
                assertThat(f.get()).isEqualTo(expected);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void eachChunkGetsItsOwnBlocksOncePerPosition() {
        final PlantTestView view = new PlantTestView(TfcBiomes.PLAINS);
        final Plants plants = new Plants(SEED, PlantTestData.data());
        for (int cx = -2; cx < 2; cx++) {
            for (int cz = -2; cz < 2; cz++) {
                final Set<Long> seen = new HashSet<>();
                final List<PlantBlock> blocks = chunk(plants, view, cx, cz, Plants.Step.SURFACE_DECORATION);
                for (PlantBlock b : blocks) {
                    assertThat(b.inChunk(cx, cz)).as("%s in chunk %d %d", b, cx, cz).isTrue();
                    seen.add(PlantLevel.key(b.x(), b.y(), b.z()));
                }
                // A chunk's decision is the union of its neighbours' decisions inside it
                final List<PlantBlock> union = new ArrayList<>();
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        for (PlantBlock b : plants.decorate(cx + dx, cz + dz, view, Plants.Step.SURFACE_DECORATION)) {
                            if (b.inChunk(cx, cz)) {
                                union.add(b);
                            }
                        }
                    }
                }
                assertThat(blocks).containsExactlyElementsOf(union);
            }
        }
    }

    @Test
    void kindsAndIdsAreConsistent() {
        final Plants plants = new Plants(SEED, PlantTestData.data());
        final List<PlantBlock> blocks = new ArrayList<>();
        for (int biome : new int[] {TfcBiomes.PLAINS, TfcBiomes.OCEAN, TfcBiomes.LAKE, TfcBiomes.SHORE}) {
            blocks.addAll(area(plants, new PlantTestView(biome), Plants.Step.SURFACE_DECORATION));
        }
        blocks.addAll(area(plants, new PlantTestView(TfcBiomes.OCEAN_REEF), Plants.Step.LARGE_FEATURES));
        final Set<PlantState.Kind> kinds = new HashSet<>();
        for (PlantBlock b : blocks) {
            final PlantState s = b.state();
            kinds.add(s.kind());
            switch (s.kind()) {
                case PLANT -> {
                    assertThat(s.block()).startsWith("tfp:plant/");
                    assertThat(PlantType.of(s.block())).isEqualTo(s.type());
                    assertThat(s.type().survivable()).isTrue();
                    assertThat(s.age() >= 0).isEqualTo(s.type().hasAge());
                }
                case WILD_CROP -> assertThat(WildCrop.of(s.block())).isNotNull();
                case WILD_CROP_FRUIT -> assertThat(s.block()).isIn("tfp:pumpkin", "tfp:melon");
                case CORAL_BLOCK -> assertThat(s.block()).isIn(Corals.CORAL_BLOCKS);
                case CORAL -> assertThat(s.block()).matches("tfp:coral/[a-z]+_coral");
                case CORAL_FAN -> assertThat(s.block()).matches("tfp:coral/[a-z]+_coral_fan");
                case CORAL_WALL_FAN -> {
                    assertThat(s.block()).matches("tfp:coral/[a-z]+_coral_wall_fan");
                    assertThat(s.extra()).isBetween(0, 3);
                }
                case SEA_PICKLE -> assertThat(s.extra()).isBetween(1, 4);
            }
        }
        assertThat(kinds).contains(PlantState.Kind.PLANT, PlantState.Kind.CORAL_BLOCK);
    }

    /** Every placed plant stands where its TFC block can: land plants in air on their soil, water plants in their water. */
    @Test
    void placementsOnlyWhereTfcAllows() {
        final Plants plants = new Plants(SEED, PlantTestData.data());
        int checked = 0;
        for (int biome : new int[] {TfcBiomes.PLAINS, TfcBiomes.OCEAN, TfcBiomes.LAKE, TfcBiomes.SHORE}) {
            final PlantTestView view = new PlantTestView(biome);
            for (PlantBlock b : area(plants, view, Plants.Step.SURFACE_DECORATION)) {
                final PlantState s = b.state();
                final TreeGround at = view.ground(b.x(), b.y(), b.z());
                final TreeGround below = view.ground(b.x(), b.y() - 1, b.z());
                final boolean upper = s.part() == PlantState.Part.UPPER;
                if (s.kind() == PlantState.Kind.PLANT && !upper) {
                    checked++;
                    switch (s.type()) {
                        case FLOATING, FLOATING_FRESH -> {
                            assertThat(at).as("%s", b).isEqualTo(TreeGround.AIR);
                            assertThat(below).as("%s", b).isEqualTo(s.type() == PlantType.FLOATING ? TreeGround.SALT_WATER : TreeGround.WATER);
                        }
                        case WATER, WATER_FRESH, GRASS_WATER, GRASS_WATER_FRESH, TALL_WATER, TALL_WATER_FRESH -> {
                            final TreeGround water = s.type().fluidProperty() == PlantState.Fluid.SALT_WATER ? TreeGround.SALT_WATER : TreeGround.WATER;
                            assertThat(at).as("%s", b).isEqualTo(water);
                            assertThat(below.seaBushPlantableOn()).as("%s", b).isTrue();
                            assertThat(s.fluid()).isEqualTo(s.type().fluidProperty());
                        }
                        case BEACH_GRASS -> assertThat(below.isSand()).as("%s", b).isTrue();
                        case DRY, PASSABLE_CACTUS, CACTUSBED -> assertThat(PlantType.dryPlantableOn(below)).as("%s", b).isTrue();
                        default -> {
                            assertThat(at).as("%s", b).isEqualTo(TreeGround.AIR);
                            assertThat(below.bushPlantableOn()).as("%s", b).isTrue();
                        }
                    }
                } else if (s.kind() == PlantState.Kind.WILD_CROP && !upper) {
                    checked++;
                    assertThat(below.treeGrowsOn()).as("%s", b).isTrue();
                }
            }
        }
        assertThat(checked).isGreaterThan(100);
        final PlantTestView reef = new PlantTestView(TfcBiomes.OCEAN_REEF);
        int corals = 0;
        for (PlantBlock b : area(plants, reef, Plants.Step.LARGE_FEATURES)) {
            assertThat(reef.ground(b.x(), b.y(), b.z())).as("%s", b).isEqualTo(TreeGround.SALT_WATER);
            corals++;
        }
        assertThat(corals).isGreaterThan(0);
        // Corals only in the biomes whose large features list them
        assertThat(area(plants, new PlantTestView(TfcBiomes.PLAINS), Plants.Step.LARGE_FEATURES)).isEmpty();
        assertThat(area(plants, new PlantTestView(TfcBiomes.OCEAN), Plants.Step.LARGE_FEATURES)).isEmpty();
    }

    @Test
    void biomeListsFollowTheTags() {
        final Plants plants = new Plants(SEED, PlantTestData.data());
        final List<String> plains = plants.features(Plants.Step.SURFACE_DECORATION, TfcBiomes.PLAINS).stream()
            .map(PlantFeature.Placed::id).toList();
        assertThat(plains).contains("tfp:plant/allium_patch", "tfp:surface_grasses", "tfp:plant/wild_crop/wheat_patch",
            "tfp:plant/cattail_patch", "tfp:plant/duckweed_patch");
        assertThat(plains.indexOf("tfp:plant/wild_crop/cassava_patch")).isLessThan(plains.indexOf("tfp:surface_grasses"));
        assertThat(plants.features(Plants.Step.SURFACE_DECORATION, TfcBiomes.OCEAN).stream().map(PlantFeature.Placed::id))
            .contains("tfp:plant/manatee_grass_patch").doesNotContain("tfp:plant/allium_patch");
        assertThat(plants.features(Plants.Step.LARGE_FEATURES, TfcBiomes.OCEAN_REEF).stream().map(PlantFeature.Placed::id))
            .containsExactly("tfp:coral_mushroom", "tfp:coral_tree", "tfp:coral_claw");
        // Not ported: atoll corals (tfc:atoll), vines, kelp, epiphytes, creeping plants, cacti, fruit trees and bushes
        assertThat(plants.skipped()).containsKeys("tfp:atoll_coral_tree", "tfp:plant/jungle_vines", "tfp:plant/giant_kelp_patch",
            "tfp:plant/moss_cover", "tfp:plant/barrel_cactus_patch");
        for (int biome = 0; biome < TfcBiomes.COUNT; biome++) {
            for (Plants.Step step : Plants.Step.values()) {
                for (PlantFeature.Placed p : plants.features(step, biome)) {
                    assertThat(plants.skipped()).doesNotContainKey(p.id());
                }
            }
        }
    }

    @Test
    void climateForestBounds() {
        final ClimateFilter filter = new ClimateFilter(-50, 50, 0, 600, -1, 1, false, 2, 3, -64, 320, false, false);
        final PlantTestView view = new PlantTestView();
        final XoroshiroRandomSource random = new XoroshiroRandomSource(1L);
        assertThat(filter.hasForestBounds()).isTrue();
        assertThat(filter.isValid(view, 0, 64, 0, random, 1)).isFalse();
        assertThat(filter.isValid(view, 0, 64, 0, random, 2)).isTrue();
        assertThat(filter.isValid(view, 0, 64, 0, random, 3)).isTrue();
        assertThat(filter.isValid(view, 0, 64, 0, random, 4)).isFalse();
        // Without a density the bounds are not checked (the groundcover callers)
        assertThat(filter.isValid(view, 0, 64, 0, random)).isTrue();
    }

    @Test
    void dynamicPatchTriesFollowHeightAndDensity() {
        final PlantLevel level = new PlantLevel(new PlantTestView(), Set.of(), Set.of());
        // chunk (0, 0): density 0, so grass_density 0.5 * 2 keeps the tries
        assertThat(PlantPlacer.dynamicTries(level, 0, 64, 0, 10)).isEqualTo(10);
        // high up: (int) (10 * (1f - clampedMap(163, 88, 163, 0, 0.8))) = (int) 1.9999999 = 1, float math as in TFC
        assertThat(PlantPlacer.dynamicTries(level, 0, 163, 0, 10)).isEqualTo(1);
        assertThat(PlantPlacer.dynamicTries(level, 0, 88, 0, 10)).isEqualTo(10);
        // chunk (1, 0): density 3, at most 14
        assertThat(PlantPlacer.dynamicTries(level, 16, 64, 0, 100)).isEqualTo(14);
    }
}
