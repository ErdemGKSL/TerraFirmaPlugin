/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.worldgen.decoration;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.worldgen.groundcover.GroundView;
import dev.tfp.core.worldgen.random.XoroshiroRandomSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * PAR-WORLD-21: golden values of TFC's forest floor patches and island guano at the pinned commit (c3b4d9536).
 *
 * <p>The expected values came from a scratch oracle (kept outside the repo) that runs vanilla's {@code RarityFilter} /
 * {@code CountPlacement}, {@code InSquarePlacement}, TFC's {@code ClimatePlacement.isValid} (copied), {@code RandomPatchFeature}
 * and the placed groundcover filters ({@code WORLD_SURFACE_WG}, {@code tfc:replaceable}, {@code would_survive},
 * {@code tfc:intertidal}, {@code tfc:on_top}) on Minecraft 1.21.11's own {@code XoroshiroRandomSource} from the Paper 1.21.11
 * server jar the project's run-paper tooling fetches, over the synthetic view below. Each case seeds
 * {@code new XoroshiroRandomSource(seed ^ cx * 341873128712L ^ cz * 132897987541L ^ f * 0x9E3779B97F4A7C15L)} for chunk
 * {@code (cx, cz)} in [-4, 3] and patch number {@code f}, and hashes (FNV-1a 64) the lines
 * {@code "cx cz|x,y,z=block;...|<next long>"}.
 */
class DecorationGoldenTest {
    /** The oracle's view: surface 60 to 68, temperature -10 to 29, groundwater 50 to 499, forest 0 to 4 per chunk. */
    static class View implements GroundView {
        @Override
        public int minY() {
            return -64;
        }

        @Override
        public int seaLevel() {
            return 63;
        }

        @Override
        public int oceanFloor(int x, int z) {
            return worldSurface(x, z);
        }

        @Override
        public int worldSurface(int x, int z) {
            return 60 + Math.floorMod(x * 31 + z * 17, 9);
        }

        @Override
        public boolean onIce(int x, int z) {
            return false;
        }

        @Override
        public boolean saltWater(int x, int z) {
            return false;
        }

        @Override
        public int surfaceRock(int x, int z) {
            return 0;
        }

        @Override
        public int biome(int x, int z) {
            return 0;
        }

        @Override
        public float averageTemperature(int x, int z) {
            return -10 + Math.floorMod(x * 7 + z * 3, 40);
        }

        @Override
        public float averageRainfall(int x, int z) {
            return groundwater(x, z);
        }

        @Override
        public float groundwater(int x, int z) {
            return 50 + Math.floorMod(x * 11 + z * 5, 450);
        }

        @Override
        public float rainVariance(int x, int z) {
            return 0f;
        }

        @Override
        public double tideLevel(int x, int z) {
            return 62.0;
        }

        @Override
        public int forestDensity(int x, int z) {
            return Math.floorMod((x >> 4) + (z >> 4), 5);
        }

        @Override
        public boolean topBlockStone(int x, int z) {
            return Math.floorMod(x + z, 3) == 0;
        }
    }

    private record Golden(long seed, String block, String hash, int covers) {
    }

    private static List<Golden> goldens() throws IOException {
        final List<Golden> out = new ArrayList<>();
        try (InputStream in = DecorationGoldenTest.class.getResourceAsStream("/dev/tfp/core/worldgen/decoration/forest_floor.tsv")) {
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                if (!line.isBlank() && !line.startsWith("#")) {
                    final String[] f = line.split("\t");
                    out.add(new Golden(Long.parseLong(f[0]), f[1], f[2], Integer.parseInt(f[3])));
                }
            }
        }
        return out;
    }

    static long fnv(String s) {
        long h = 0xcbf29ce484222325L;
        for (byte b : s.getBytes(StandardCharsets.UTF_8)) {
            h ^= b & 0xff;
            h *= 0x100000001b3L;
        }
        return h;
    }

    @Test
    void forestFloorAndIslandGuanoMatchTheOracle() throws IOException {
        final List<ForestFloor.Patch> patches = new ArrayList<>(ForestFloor.FOREST_PLANTS);
        patches.add(ForestFloor.EXTRA_ISLAND_GUANO);
        final List<String> blocks = patches.stream().map(p -> p.block().substring("tfp:groundcover/".length())).toList();
        assertThat(blocks).isEqualTo(Arrays.asList("stick", "pinecone", "salt_lick", "humus", "rotten_flesh", "bone", "guano"));
        final List<Golden> goldens = goldens();
        assertThat(goldens).hasSize(21);
        final View view = new View();
        for (Golden g : goldens) {
            final int f = blocks.indexOf(g.block());
            final StringBuilder out = new StringBuilder();
            int covers = 0;
            for (int cx = -4; cx < 4; cx++) {
                for (int cz = -4; cz < 4; cz++) {
                    final XoroshiroRandomSource r = new XoroshiroRandomSource(
                        g.seed() ^ cx * 341873128712L ^ cz * 132897987541L ^ f * 0x9E3779B97F4A7C15L);
                    final StringBuilder line = new StringBuilder();
                    final List<ForestFloor.Cover> placed = new ArrayList<>();
                    ForestFloor.patch(r, patches.get(f), cx << 4, cz << 4, view, placed::add);
                    for (ForestFloor.Cover c : placed) {
                        line.append(c.x()).append(',').append(c.y()).append(',').append(c.z()).append('=')
                            .append(c.block().substring("tfp:groundcover/".length())).append(';');
                    }
                    covers += placed.size();
                    out.append(cx).append(' ').append(cz).append('|').append(line).append('|').append(r.nextLong()).append('\n');
                }
            }
            assertThat(Long.toHexString(fnv(out.toString()))).as(g.seed() + " " + g.block()).isEqualTo(g.hash());
            assertThat(covers).as(g.seed() + " " + g.block()).isEqualTo(g.covers());
        }
    }

    /** The ids of PAR-WORLD-21 (the audit of 2026-11-06), minus the five volcanic ones ({@code VolcanicPlacedIdsTest}). */
    private static final List<String> ITEM_IDS = List.of("tfc:sticks_forest_patch", "tfc:pinecone_patch", "tfc:salt_lick_patch",
        "tfc:humus_patch", "tfc:rotten_flesh_patch", "tfc:bone_patch", "tfc:extra_island_guano_patch", "tfc:atoll_guano",
        "tfc:tide_pool", "tfc:big_tide_pool", "tfc:sea_stacks_patch", "tfc:bamboo", "tfc:rare_bamboo", "tfc:bamboo_golden",
        "tfc:rare_bamboo_golden");

    @Test
    void everyIdIsRuledOrAnOpenGap() throws IOException {
        final List<String> gaps;
        try (InputStream in = DecorationGoldenTest.class.getResourceAsStream("/dev/tfp/core/worldgen/decoration/open_gaps.txt")) {
            gaps = Arrays.stream(new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")).map(String::trim)
                .filter(l -> !l.isEmpty() && !l.startsWith("#")).toList();
        }
        final List<String> ruled = new ArrayList<>(ForestFloor.FOREST_PLANTS.stream().map(ForestFloor.Patch::id).toList());
        ruled.add(ForestFloor.EXTRA_ISLAND_GUANO.id());
        ruled.add(ForestFloor.ATOLL_GUANO);
        final List<String> covered = new ArrayList<>(ruled);
        covered.addAll(gaps);
        assertThat(covered).containsExactlyInAnyOrderElementsOf(ITEM_IDS);
        assertThat(gaps).doesNotContainAnyElementsOf(ruled);
    }

    @Test
    void forestPatchesNeedAKnownForest() {
        final View unknown = new View() {
            @Override
            public int forestDensity(int x, int z) {
                return -1;
            }
        };
        final ForestFloor floor = new ForestFloor(5L);
        final List<ForestFloor.Cover> known = new ArrayList<>();
        final List<ForestFloor.Cover> none = new ArrayList<>();
        for (int c = -8; c < 8; c++) {
            floor.forestPlants(c, c * 3, new View(), known::add);
            floor.forestPlants(c, c * 3, unknown, none::add);
        }
        assertThat(known).isNotEmpty();
        // only the patches without forest bounds (rotten flesh, bone) place where the forest is unknown
        assertThat(none).allSatisfy(cover -> assertThat(cover.block()).isIn("tfp:groundcover/rotten_flesh", "tfp:groundcover/bone"));
    }

    @Test
    void atollGuanoIsOnTheChunkCornerColumn() {
        final List<ForestFloor.Cover> out = new ArrayList<>();
        ForestFloor.atollGuano(3, -2, new View(), out::add);
        assertThat(out).containsExactly(new ForestFloor.Cover(48, new View().worldSurface(48, -32), -32, "tfp:groundcover/guano"));
    }
}
