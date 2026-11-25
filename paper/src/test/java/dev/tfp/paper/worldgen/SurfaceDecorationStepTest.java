/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.worldgen;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.worldgen.biome.TfcBiomes;
import dev.tfp.core.worldgen.chunk.PlaceholderRocks;
import dev.tfp.core.worldgen.decoration.Bamboo;
import dev.tfp.core.worldgen.decoration.DecorationBiomes;
import dev.tfp.core.worldgen.decoration.DecorationLevel;
import dev.tfp.core.worldgen.tree.ForestType;
import dev.tfp.core.worldgen.tree.TreeGround;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

/** PAR-WORLD-66: the surface decoration adapter: the biome lists, the slots, only the decorated chunk's free positions. */
class SurfaceDecorationStepTest {
    /** West of x 0 the tidal flats (low rocky shore, salt water to 62), east of it a warm bamboo forest on grass. */
    static class Terrain implements DecorationLevel.Terrain {
        static int s(int x, int z) {
            return x < 0 ? 60 + Math.floorMod(x * 31 + z * 17, 8) : 64 + Math.floorMod(x * 7 + z * 3, 4);
        }

        @Override
        public TreeGround ground(int x, int y, int z) {
            final int s = s(x, z);
            if (y < s - 3) {
                return TreeGround.STONE;
            }
            if (y < s) {
                return x < 0 ? TreeGround.STONE : y == s - 1 ? TreeGround.GRASS : TreeGround.DIRT;
            }
            return y <= 62 ? TreeGround.SALT_WATER : TreeGround.AIR;
        }

        @Override
        public int rock(int x, int y, int z) {
            return PlaceholderRocks.names().indexOf("basalt");
        }

        @Override
        public ForestType forestType(int chunkX, int chunkZ) {
            return chunkX < 0 ? ForestType.GRASSLAND : ForestType.SECONDARY_BAMBOO;
        }

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
            return s(x, z);
        }

        @Override
        public int worldSurface(int x, int z) {
            return Math.max(s(x, z), 63);
        }

        @Override
        public boolean onIce(int x, int z) {
            return false;
        }

        @Override
        public boolean saltWater(int x, int z) {
            return x < 0;
        }

        @Override
        public int surfaceRock(int x, int z) {
            return PlaceholderRocks.names().indexOf("basalt");
        }

        @Override
        public int biome(int x, int z) {
            return x < 0 ? TfcBiomes.TIDAL_FLATS : TfcBiomes.PLAINS;
        }

        @Override
        public float averageTemperature(int x, int z) {
            return 28f;
        }

        @Override
        public float averageRainfall(int x, int z) {
            return 420f;
        }

        @Override
        public float groundwater(int x, int z) {
            return 420f;
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
            return forestType(x >> 4, z >> 4).density();
        }

        @Override
        public boolean topBlockStone(int x, int z) {
            return x < 0;
        }
    }

    @Test
    void biomeListsAreTfcs() {
        assertThat(IntStream.range(0, TfcBiomes.COUNT).filter(DecorationBiomes::land).count()).isEqualTo(99);
        assertThat(DecorationBiomes.land(TfcBiomes.PLAINS)).isTrue();
        assertThat(DecorationBiomes.land(TfcBiomes.TIDAL_FLATS)).isFalse();
        assertThat(DecorationBiomes.tidalFlats(TfcBiomes.TIDAL_FLATS)).isTrue();
        assertThat(DecorationBiomes.atoll(TfcBiomes.OCEAN_ATOLLS)).isTrue();
        assertThat(DecorationBiomes.guanoIsland(TfcBiomes.GUANO_ISLAND)).isTrue();
        // the large features list rare_bamboo, bamboo, rare_bamboo_golden, bamboo_golden after the forest
        assertThat(DecorationBiomes.BAMBOO_ORDER.stream().map(i -> Bamboo.FEATURES.get(i).id()).toList())
            .containsExactly("tfc:rare_bamboo", "tfc:bamboo", "tfc:rare_bamboo_golden", "tfc:bamboo_golden");
    }

    @Test
    void decidesOnlyTheChunksOwnBlocksInTheirSlots() {
        final SurfaceDecorationStep step = new SurfaceDecorationStep(9L);
        final Terrain terrain = new Terrain();
        int bamboo = 0;
        int pools = 0;
        int covers = 0;
        for (int cx = -3; cx <= 2; cx++) {
            for (int cz = -2; cz <= 2; cz++) {
                final Map<SurfaceDecorationStep.Slot, List<SurfaceDecorationStep.Write>> a = step.decide(cx, cz, terrain);
                assertThat(a).isEqualTo(new SurfaceDecorationStep(9L).decide(cx, cz, terrain));
                for (Map.Entry<SurfaceDecorationStep.Slot, List<SurfaceDecorationStep.Write>> e : a.entrySet()) {
                    for (SurfaceDecorationStep.Write w : e.getValue()) {
                        assertThat(w.x() >> 4).isEqualTo(cx);
                        assertThat(w.z() >> 4).isEqualTo(cz);
                        assertThat(w.slot()).isEqualTo(e.getKey());
                        assertThat(w.before()).isEqualTo(terrain.ground(w.x(), w.y(), w.z()));
                        switch (w.block().kind()) {
                            case BAMBOO, ROOTED_DIRT -> {
                                assertThat(e.getKey()).isEqualTo(SurfaceDecorationStep.Slot.BAMBOO);
                                assertThat(terrain.biome(w.x(), w.z())).isEqualTo(TfcBiomes.PLAINS);
                                bamboo++;
                            }
                            case RAW, COBBLE, SALT_WATER, SALT_WATER_COVER -> {
                                assertThat(e.getKey()).isEqualTo(SurfaceDecorationStep.Slot.AFTER_SHORE);
                                pools++;
                            }
                            case HARDENED -> assertThat(e.getKey()).isEqualTo(SurfaceDecorationStep.Slot.BEFORE_PLANTS);
                            case COVER -> {
                                if (e.getKey() == SurfaceDecorationStep.Slot.BEFORE_PLANTS) {
                                    covers++;
                                }
                            }
                        }
                    }
                }
            }
        }
        assertThat(bamboo).isPositive();
        assertThat(pools).isPositive();
        assertThat(covers).isPositive();
    }

    @Test
    void blocksHaveCraftEngineRefsAndPlaceholders() {
        final int basalt = PlaceholderRocks.names().indexOf("basalt");
        assertThat(SurfaceDecorationStep.ceRef(new DecorationLevel.Block(DecorationLevel.Kind.COBBLE, basalt, null, null)))
            .isEqualTo("tfp:rock/cobble/basalt");
        assertThat(SurfaceDecorationStep.ceRef(new DecorationLevel.Block(DecorationLevel.Kind.HARDENED, basalt, null, null)))
            .isEqualTo("tfp:rock/hardened/basalt");
        assertThat(SurfaceDecorationStep.ceRef(new DecorationLevel.Block(DecorationLevel.Kind.SALT_WATER_COVER, -1,
            "tfp:groundcover/clam", null))).isEqualTo("tfp:groundcover/clam[fluid=water]");
        assertThat(SurfaceDecorationStep.ceRef(new DecorationLevel.Block(DecorationLevel.Kind.BAMBOO, -1, Bamboo.BAMBOO,
            Bamboo.Stalk.TRUNK))).isNull();
        assertThat(SurfaceDecorationStep.ceRef(new DecorationLevel.Block(DecorationLevel.Kind.BAMBOO, -1, Bamboo.GOLDEN_BAMBOO,
            Bamboo.Stalk.FINAL_LARGE))).isEqualTo("tfp:plant/golden_bamboo[age=1,leaves=large,stage=1]");
        assertThat(SurfaceDecorationStep.ceRef(new DecorationLevel.Block(DecorationLevel.Kind.RAW, basalt, null, null))).isNull();
        for (String cover : List.of("stick", "pinecone", "salt_lick", "humus", "rotten_flesh", "bone", "guano", "clam", "mollusk",
            "mussel", "sea_urchin")) {
            assertThat(Material.valueOf(SurfaceDecorationStep.coverPlaceholder("tfp:groundcover/" + cover))).isNotNull();
        }
    }

    @Test
    void writesCoversOnlyOnFreePositionsAndNeverOverTrees() {
        final DecorationLevel.Block cover = new DecorationLevel.Block(DecorationLevel.Kind.COVER, -1, "tfp:groundcover/stick", null);
        final DecorationLevel.Block rim = new DecorationLevel.Block(DecorationLevel.Kind.RAW, 0, null, null);
        assertThat(SurfaceDecorationStep.fits(cover, TreeGround.AIR, true, false)).isTrue();
        assertThat(SurfaceDecorationStep.fits(cover, TreeGround.AIR, false, true)).isFalse(); // a plant or boulder is there now
        assertThat(SurfaceDecorationStep.fits(cover, TreeGround.GRASS, false, true)).isFalse();
        assertThat(SurfaceDecorationStep.fits(rim, TreeGround.SAND, false, true)).isTrue();
        assertThat(SurfaceDecorationStep.fits(rim, TreeGround.SAND, true, false)).isFalse();
        assertThat(SurfaceDecorationStep.fits(rim, TreeGround.LEAVES, false, true)).isFalse();
        assertThat(SurfaceDecorationStep.fits(rim, TreeGround.SALT_WATER, true, false)).isTrue();
    }
}
