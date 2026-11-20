/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.worldgen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.tfp.core.worldgen.biome.TfcBiomes;
import dev.tfp.core.worldgen.ore.KaolinDisc;
import dev.tfp.core.worldgen.ore.OreVeins;
import dev.tfp.core.worldgen.ore.VeinBlock;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.IntPredicate;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** PAR-WORLD-30: the vein step of the terrain fill over the shipped datagen output. */
class VeinStepWiringTest {
    private static final int MONTANE = 1 << (VeinStep.BIOME_PASSES.indexOf("montane") + 1);
    private static final int RIVER = 1 << (VeinStep.BIOME_PASSES.indexOf("river") + 1);
    private static final VeinStep STEP = new VeinStep(7L, OreVeinWiringTest.read(), TfcTerrain.ROCK_IDS);

    private static OreVeins.WorldView view(int height, boolean lava) {
        return new OreVeins.WorldView() {
            @Override
            public int surfaceHeight(int x, int z) {
                return height;
            }

            @Override
            public boolean isLava(int x, int y, int z) {
                return lava;
            }
        };
    }

    /** Counts the rock positions of a chunk area whose code matches, with every rock tried at each position. */
    private static int count(int tags, boolean kaolin, OreVeins.WorldView view, int yMin, int yMax, IntPredicate code) {
        return count(6, tags, kaolin, view, yMin, yMax, code);
    }

    private static int count(int r, int tags, boolean kaolin, OreVeins.WorldView view, int yMin, int yMax, IntPredicate code) {
        int n = 0;
        for (int cx = -r; cx < r; cx++) {
            for (int cz = -r; cz < r; cz++) {
                final VeinStep.Chunk chunk = STEP.chunk(cx, cz, tags, kaolin, view);
                if (chunk.isEmpty()) {
                    continue;
                }
                for (int i = 0; i < 256; i++) {
                    final int x = (cx << 4) + (i & 15);
                    final int z = (cz << 4) + (i >> 4);
                    for (int y = yMin; y <= yMax; y++) {
                        final int rock = Math.floorMod(x * 7 + z * 3 + y / 4, TfcTerrain.ROCK_IDS.size());
                        if (code.test(chunk.rock(x, y, z, rock))) {
                            n++;
                        }
                    }
                }
            }
        }
        return n;
    }

    private static IntPredicate paletteMatches(String part) {
        return c -> c >= 0 && STEP.paletteIds().get(c).contains(part);
    }

    private static IntPredicate customIs(String id) {
        return c -> VeinStep.isCustom(c) && STEP.customIds().get(VeinStep.customOf(c)).equals(id);
    }

    @Test
    void biomeTagsMatchTheBiomesThatListThem() {
        assertThat(VeinStep.biomeTags(TfcBiomes.PLAINS)).isZero();
        assertThat(VeinStep.biomeTags(TfcBiomes.MOUNTAINS)).isEqualTo(MONTANE);
        assertThat(VeinStep.biomeTags(TfcBiomes.COLLISIONAL_MOUNTAINS)).isEqualTo(MONTANE | 1 << 2);
        assertThat(VeinStep.biomeTags(TfcBiomes.RIVER)).isEqualTo(RIVER);
        assertThat(VeinStep.biomeTags(TfcBiomes.RIFT_LAKE)).isEqualTo(VeinStep.KAOLIN | RIVER);
        assertThat(VeinStep.biomeTags(TfcBiomes.OLD_MOUNTAINS)).isEqualTo(VeinStep.KAOLIN | MONTANE);
    }

    @Test
    void biomeTagsEqualTheTfcBiomeFilesWhenTheCheckoutIsThere() throws IOException {
        final Path biomes = Path.of(System.getProperty("tfc.checkout", "/opt/tfc"), "src/main/resources/data/tfc/worldgen/biome");
        assumeTrue(Files.isDirectory(biomes), "no TFC checkout");
        for (Map.Entry<String, List<String>> e : VeinStep.TAG_BIOMES.entrySet()) {
            final TreeSet<String> listing = new TreeSet<>();
            try (Stream<Path> files = Files.list(biomes)) {
                for (Path f : files.toList()) {
                    if (Files.readString(f).contains("\"#tfc:in_biome/veins/" + e.getKey() + "\"")) {
                        listing.add(f.getFileName().toString().replace(".json", ""));
                    }
                }
            }
            assertThat(e.getValue()).as(e.getKey()).containsExactlyElementsOf(listing);
        }
    }

    @Test
    void aChunkRunsTheTagsOfItsThreeByThreeArea() {
        // A river column two chunks east of the origin chunk: chunk 1 sees it, chunk 0 does not
        final VeinStep.BiomeAt biomes = (x, z) -> x >= 32 && x < 36 ? TfcBiomes.RIVER : TfcBiomes.PLAINS;
        assertThat(VeinStep.chunkTags(1, 0, biomes)).isEqualTo(RIVER);
        assertThat(VeinStep.chunkTags(3, 0, biomes)).isEqualTo(RIVER);
        assertThat(VeinStep.chunkTags(0, 0, biomes)).isZero();
        assertThat(VeinStep.chunkTags(4, 0, biomes)).isZero();
    }

    @Test
    void biomePassesRunOnlyWithTheirTag() {
        final OreVeins.WorldView view = view(80, false);
        final IntPredicate opalOrAmethyst = paletteMatches("ore/opal/").or(paletteMatches("ore/amethyst/"));
        assertThat(count(0, false, view, -64, 120, opalOrAmethyst)).isZero();
        assertThat(count(RIVER, false, view, -64, 120, opalOrAmethyst)).isPositive();
        final IntPredicate any = c -> c >= TfcTerrain.ROCK_IDS.size() && STEP.paletteIds().get(c).startsWith("tfp:ore/");
        assertThat(count(MONTANE, false, view, -64, 120, any)).isGreaterThan(count(0, false, view, -64, 120, any));
    }

    @Test
    void projectedTypesFollowTheGroundHeight() {
        final IntPredicate lignite = customIs("tfp:lignite");
        // The lignite discs lie 8 to 20 blocks below the ground (rarity 160): they move up with the ground
        assertThat(count(20, 0, false, view(40, false), 18, 34, lignite)).isPositive();
        assertThat(count(20, 0, false, view(100, false), 18, 34, lignite)).isZero();
        assertThat(count(20, 0, false, view(100, false), 78, 94, lignite)).isPositive();
    }

    @Test
    void nearLavaTypesOnlyNearLava() {
        final IntPredicate sulfurInNonTuff = c -> c >= 0 && STEP.paletteIds().get(c).startsWith("tfp:ore/sulfur/")
            && !STEP.paletteIds().get(c).endsWith("/tuff");
        assertThat(count(0, false, view(80, false), -64, 0, sulfurInNonTuff)).isZero();
        assertThat(count(0, false, view(80, true), -64, 0, sulfurInNonTuff)).isPositive();
    }

    @Test
    void kaolinOnlyInItsBiomesAndClimateAndOnSoil() {
        final OreVeins.WorldView view = view(100, false);
        final IntPredicate clay = c -> VeinStep.isCustom(c) && STEP.customIds().get(VeinStep.customOf(c)).contains("kaolin");
        assertThat(count(12, 0, true, view, 92, 100, clay)).isZero();
        assertThat(count(12, VeinStep.KAOLIN, false, view, 92, 100, clay)).isZero();
        assertThat(count(12, VeinStep.KAOLIN, true, view, 92, 100, clay)).isPositive();
        int grass = 0;
        for (int cx = -12; cx < 12; cx++) {
            for (int cz = -12; cz < 12; cz++) {
                final VeinStep.Chunk chunk = STEP.chunk(cx, cz, VeinStep.KAOLIN, true, view);
                for (int i = 0; i < 256; i++) {
                    final int x = (cx << 4) + (i & 15);
                    final int z = (cz << 4) + (i >> 4);
                    assertThat(chunk.soil(x, 99, z, KaolinDisc.Soil.OTHER)).isEqualTo(VeinStep.KEEP);
                    if (customIs("tfp:kaolin_clay_grass").test(chunk.soil(x, 99, z, KaolinDisc.Soil.GRASS))) {
                        grass++;
                    }
                }
            }
        }
        assertThat(grass).isPositive();
        assertThat(TfcTerrain.kaolinSoil(dev.tfp.core.worldgen.surface.SurfaceKind.SAND)).isEqualTo(KaolinDisc.Soil.OTHER);
        assertThat(TfcTerrain.kaolinSoil(dev.tfp.core.worldgen.surface.SurfaceKind.GRASS)).isEqualTo(KaolinDisc.Soil.GRASS);
    }

    @Test
    void depositsKeepTheRock() {
        assertThat(VeinBlock.parse("tfp:deposit/native_copper/basalt", "x").kind()).isEqualTo(VeinBlock.Kind.DEPOSIT);
        assertThat(STEP.paletteIds()).noneMatch(id -> id.startsWith("tfp:deposit/"));
        assertThat(STEP.customIds()).noneMatch(id -> id.startsWith("tfp:deposit/"));
    }
}
