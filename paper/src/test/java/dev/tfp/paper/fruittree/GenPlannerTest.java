/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.fruittree;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.tfp.core.berry.FruitTree;
import dev.tfp.paper.banana.GenBananas;
import dev.tfp.core.worldgen.biome.TfcBiomes;
import dev.tfp.core.worldgen.plant.PlantData;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/** PAR-FARM-27: the pure placement decision of the fruit tree and banana features. */
class GenPlannerTest {
    static final String BANANA_PLACED = """
        {"feature":"tfp:plant/banana","placement":[
          {"type":"tfp:climate","min_temperature":11.0,"max_temperature":40,"min_groundwater":280,"max_groundwater":500,"max_forest":3},
          {"type":"minecraft:heightmap","heightmap":"WORLD_SURFACE_WG"},{"type":"minecraft:in_square"},
          {"type":"minecraft:rarity_filter","chance":50}]}""";
    static final String BANANA_CONFIGURED = "{\"type\":\"tfp:bananas\",\"config\":{\"state\":\"tfp:plant/banana_plant\"}}";
    static final String CHERRY_PLACED = BANANA_PLACED.replace("plant/banana", "plant/cherry").replace("\"min_temperature\":11.0", "\"min_temperature\":-5.2")
        .replace("\"max_temperature\":40", "\"max_temperature\":14.0").replace("\"min_groundwater\":280", "\"min_groundwater\":100")
        .replace("\"max_groundwater\":500", "\"max_groundwater\":350");
    static final String CHERRY_CONFIGURED = "{\"type\":\"tfp:fruit_trees\",\"config\":{\"state\":\"tfp:plant/cherry_growing_branch\"}}";

    static Optional<String> data(String path) {
        return switch (path) {
            case "tfp/worldgen/placed_feature/plant/banana.json" -> Optional.of(BANANA_PLACED);
            case "tfp/worldgen/configured_feature/plant/banana.json" -> Optional.of(BANANA_CONFIGURED);
            case "tfp/worldgen/placed_feature/plant/cherry.json" -> Optional.of(CHERRY_PLACED);
            case "tfp/worldgen/configured_feature/plant/cherry.json" -> Optional.of(CHERRY_CONFIGURED);
            default -> Optional.empty();
        };
    }

    static GenFeature banana() {
        return GenFeature.read("tfp:plant/banana", GenPlannerTest::data).orElseThrow();
    }

    static GenFeature cherry() {
        return GenFeature.read("tfp:plant/cherry", GenPlannerTest::data).orElseThrow();
    }

    /** A flat chunk with a settable climate. */
    record Flat(float temperature, float groundwater, int forest, int height) implements GenPlanner.Terrain {
        @Override
        public int minY() {
            return -64;
        }

        @Override
        public int worldSurface(int x, int z) {
            return height;
        }

        @Override
        public float averageTemperature(int x, int z) {
            return temperature;
        }

        @Override
        public float averageRainfall(int x, int z) {
            return groundwater;
        }

        @Override
        public float groundwater(int x, int z) {
            return groundwater;
        }

        @Override
        public float rainVariance(int x, int z) {
            return 0f;
        }

        @Override
        public int forestDensity(int chunkX, int chunkZ) {
            return forest;
        }
    }

    @Test
    void readsTheKindBlockClimateAndRarity() {
        GenFeature b = banana();
        assertThat(b.kind()).isEqualTo(GenFeature.Kind.BANANA);
        assertThat(b.block()).isEqualTo("tfp:plant/banana_plant");
        assertThat(b.rarity()).isEqualTo(50);
        assertThat(b.tfcId()).isEqualTo("tfc:plant/banana");
        GenFeature c = cherry();
        assertThat(c.kind()).isEqualTo(GenFeature.Kind.FRUIT_TREE);
        assertThat(c.tree()).contains(FruitTree.CHERRY);
    }

    @Test
    void aFeatureOfAnotherTypeIsNotOurs() {
        assertThat(GenFeature.read("tfp:plant/missing", GenPlannerTest::data)).isEmpty();
        String other = "{\"type\":\"tfp:tall_plant\",\"config\":{\"state\":{\"Name\":\"tfp:plant/rose\"}}}";
        assertThat(GenFeature.read("tfp:plant/banana", p -> p.contains("configured") ? Optional.of(other) : data(p))).isEmpty();
    }

    @Test
    void aReorderedPlacementIsRefusedLoudly() {
        String swapped = BANANA_PLACED.replace("{\"type\":\"minecraft:heightmap\",\"heightmap\":\"WORLD_SURFACE_WG\"},{\"type\":\"minecraft:in_square\"},", "{\"type\":\"minecraft:in_square\"},{\"type\":\"minecraft:heightmap\",\"heightmap\":\"WORLD_SURFACE_WG\"},");
        assertThatThrownBy(() -> GenFeature.read("tfp:plant/banana", p -> p.contains("placed") ? Optional.of(swapped) : data(p)))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("placement order");
    }

    @Test
    void theRarityPicksAboutOneChunkInFiftyAndOnlyInClimate() {
        GenFeature b = banana();
        Flat good = new Flat(25f, 400f, 1, 70);
        int hits = 0;
        int cold = 0;
        int dense = 0;
        int dry = 0;
        for (int cx = -50; cx < 50; cx++) {
            for (int cz = -50; cz < 50; cz++) {
                if (GenPlanner.plan(b, 1234L, cx, cz, good).isPresent()) {
                    hits++;
                }
                if (GenPlanner.plan(b, 1234L, cx, cz, new Flat(5f, 400f, 1, 70)).isPresent()) {
                    cold++;
                }
                if (GenPlanner.plan(b, 1234L, cx, cz, new Flat(25f, 400f, 4, 70)).isPresent()) {
                    dense++;
                }
                if (GenPlanner.plan(b, 1234L, cx, cz, new Flat(25f, 100f, 1, 70)).isPresent()) {
                    dry++;
                }
            }
        }
        assertThat(hits).isBetween(120, 290); // 10000 chunks / 50 = 200
        assertThat(cold).isZero();
        assertThat(dense).isZero(); // max_forest 3
        assertThat(dry).isZero();
    }

    @Test
    void theSpotIsInTheChunkAtTheCornerColumnsHeightWithTheConfiguredDraws() {
        GenFeature c = cherry();
        GenFeature b = banana();
        int seen = 0;
        for (int cx = 0; cx < 120 && seen < 5; cx++) {
            Optional<GenPlanner.Spot> tree = GenPlanner.plan(c, 99L, cx, 7, new Flat(5f, 200f, 0, 71));
            if (tree.isPresent()) {
                seen++;
                GenPlanner.Spot s = tree.get();
                assertThat(s.x() >> 4).isEqualTo(cx);
                assertThat(s.z() >> 4).isEqualTo(7);
                assertThat(s.y()).isEqualTo(71);
                assertThat(s.draws()).hasSize(1);
                assertThat(s.draws().get(0)).isBetween(2, 4);
            }
            Optional<GenPlanner.Spot> banana = GenPlanner.plan(b, 99L, cx, 7, new Flat(25f, 400f, 0, 71));
            banana.ifPresent(s -> assertThat(s.draws()).hasSize(3).allMatch(h -> h == 2 || h == 3));
        }
        assertThat(seen).isGreaterThan(0);
    }

    @Test
    void theDecisionIsDeterministicAndEachFeatureHasItsOwnRandom() {
        Flat terrain = new Flat(10f, 300f, 0, 70);
        int differing = 0;
        GenFeature cherry = cherry();
        for (int cx = 0; cx < 300; cx++) {
            Optional<GenPlanner.Spot> a = GenPlanner.plan(cherry, 5L, cx, 3, terrain);
            assertThat(GenPlanner.plan(cherry, 5L, cx, 3, terrain)).isEqualTo(a);
            if (a.isPresent() != GenPlanner.plan(cherry, 6L, cx, 3, terrain).isPresent()) {
                differing++;
            }
        }
        assertThat(differing).isGreaterThan(0); // another world seed gives another layout
    }

    @Test
    void anOceanColumnIsNeverAnOrigin() {
        Flat sea = new Flat(25f, 400f, 0, -64);
        for (int cx = 0; cx < 200; cx++) {
            assertThat(GenPlanner.plan(banana(), 3L, cx, 0, sea)).isEmpty();
        }
    }

    @Test
    void theBananaStalkIsTwoStagesOfOneOrTwoThenOneTopBlock() {
        assertThat(GenBananas.column(List.of(2, 2, 2))).containsExactly(0, 1, 2);
        assertThat(GenBananas.column(List.of(3, 2, 3))).containsExactly(0, 0, 1, 2);
        assertThat(GenBananas.column(List.of(3, 3, 3))).containsExactly(0, 0, 1, 1, 2);
    }

    /** The datagen output of the checkout, when it has been generated (the module's working directory is {@code paper}). */
    static Optional<Path> contentData() {
        for (String candidate : List.of("../content/generated/content/data", "content/generated/content/data")) {
            Path p = Path.of(candidate);
            if (Files.isDirectory(p.resolve("tfp/worldgen/placed_feature/plant"))) {
                return Optional.of(p);
            }
        }
        return Optional.empty();
    }

    @Test
    void everyRealFruitTreeAndBananaFeatureParsesAndSomeBiomeListsIt() {
        Optional<Path> root = contentData();
        Assumptions.assumeTrue(root.isPresent(), "the datagen output is not generated here");
        Function<String, Optional<String>> read = path -> {
            try {
                Path f = root.get().resolve(path);
                return Files.exists(f) ? Optional.of(Files.readString(f)) : Optional.empty();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        };
        PlantData data = PlantData.of(read);
        Set<String> listed = new HashSet<>();
        for (int b = 0; b < TfcBiomes.COUNT; b++) {
            for (String id : data.tagValues("tfp:" + PlantData.SURFACE_DECORATION + TfcBiomes.name(b).toLowerCase(Locale.ROOT))) {
                listed.add(id);
            }
        }
        for (String id : List.of("banana", "cherry", "green_apple", "red_apple", "lemon", "olive", "orange", "peach", "plum")) {
            Optional<GenFeature> f = GenFeature.read("tfp:plant/" + id, read);
            assertThat(f).as(id).isPresent();
            assertThat(f.get().rarity()).as(id).isEqualTo(50);
            assertThat(listed).as("a biome lists " + id).contains("tfp:plant/" + id);
        }
        assertThat(GenFeature.read("tfp:plant/banana", read).orElseThrow().climate()).isNotNull();
        assertThat(GenFeature.read("tfp:plant/lemon", read).orElseThrow().tree()).contains(FruitTree.LEMON);
    }
}
