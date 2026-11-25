/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.worldgen;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.worldgen.biome.TfcBiomes;
import dev.tfp.core.worldgen.carver.Carve;
import dev.tfp.core.worldgen.cave.CaveCell;
import dev.tfp.core.worldgen.chunk.PlaceholderRocks;
import dev.tfp.core.worldgen.tree.ForestType;
import dev.tfp.core.worldgen.tree.TreeGround;
import dev.tfp.core.worldgen.volcano.VolcanoView;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** PAR-WORLD-64: the cave decoration adapter: the terrain cells it reads, the blocks it writes, only the decorated chunk's. */
class CaveDecorationStepTest {
    private static final int MAX_Y = 320;

    /** Caves: rooms at y 10..20 in a 10-block grid (carved by a carver), lava below y -40 in them; dirt over rock below 60. */
    static boolean room(int x, int y, int z) {
        return y >= 10 && y <= 20 && Math.floorMod(x, 10) >= 2 && Math.floorMod(x, 10) <= 7 && Math.floorMod(z, 10) >= 2
            && Math.floorMod(z, 10) <= 7;
    }

    static final CaveDecorationStep.Terrain TERRAIN = new CaveDecorationStep.Terrain() {
        @Override
        public Carve caveCarve(int x, int y, int z) {
            if (room(x, y, z)) {
                return Carve.AIR;
            }
            if (room(x, y + 50, z)) {
                return y < -35 ? Carve.LAVA : Carve.WATER;
            }
            return Carve.KEEP;
        }

        @Override
        public int rock(int x, int y, int z) {
            return y <= 56 && caveCarve(x, y, z) == Carve.KEEP ? Math.floorMod(x + z, 5) : -1;
        }

        @Override
        public int terrainTop(int x, int z) {
            return 60;
        }

        @Override
        public boolean carved(int x, int y, int z) {
            return room(x, y, z);
        }
    };

    static class View implements VolcanoView {
        @Override
        public TreeGround ground(int x, int y, int z) {
            if (y > 60) {
                return TreeGround.AIR;
            }
            if (y > 56) {
                return y == 60 ? TreeGround.GRASS : TreeGround.DIRT;
            }
            return TERRAIN.caveCarve(x, y, z) == Carve.KEEP ? TreeGround.STONE : TreeGround.AIR;
        }

        @Override
        public int rockAt(int x, int y, int z) {
            return Math.floorMod(x + z, 5);
        }

        @Override
        public ForestType forestType(int chunkX, int chunkZ) {
            return ForestType.GRASSLAND;
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
            return 61;
        }

        @Override
        public int worldSurface(int x, int z) {
            return 63;
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
            return TfcBiomes.PLAINS;
        }

        @Override
        public float averageTemperature(int x, int z) {
            return 24f;
        }

        @Override
        public float averageRainfall(int x, int z) {
            return 300f;
        }

        @Override
        public float groundwater(int x, int z) {
            return 300f;
        }

        @Override
        public float rainVariance(int x, int z) {
            return 0f;
        }

        @Override
        public double tideLevel(int x, int z) {
            return 62.0;
        }
    }

    @Test
    void terrainCellsFollowTheCarvesAndTheRock() {
        final View view = new View();
        assertThat(CaveDecorationStep.terrainCell(view, TERRAIN, 3, 12, 3, MAX_Y)).isEqualTo(CaveCell.CAVE_AIR);
        assertThat(CaveDecorationStep.terrainCell(view, TERRAIN, 3, -30, 3, MAX_Y)).isEqualTo(CaveCell.fluid(CaveCell.Fluid.WATER));
        assertThat(CaveDecorationStep.terrainCell(view, TERRAIN, 3, -39, 3, MAX_Y).fluid()).isEqualTo(CaveCell.Fluid.LAVA);
        assertThat(CaveDecorationStep.terrainCell(view, TERRAIN, 0, 12, 3, MAX_Y)).isEqualTo(CaveCell.raw(3));
        assertThat(CaveDecorationStep.terrainCell(view, TERRAIN, 0, 58, 3, MAX_Y)).isEqualTo(CaveCell.OTHER);
        assertThat(CaveDecorationStep.terrainCell(view, TERRAIN, 0, 70, 3, MAX_Y)).isEqualTo(CaveCell.AIR);
        assertThat(CaveDecorationStep.terrainCell(view, TERRAIN, 0, -64, 3, MAX_Y)).isEqualTo(CaveCell.OTHER);
        assertThat(CaveDecorationStep.terrainCell(view, TERRAIN, 0, MAX_Y, 3, MAX_Y)).isEqualTo(CaveCell.OTHER);
    }

    @Test
    void decidesOnlyTheChunksOwnBlocksTheSameEveryTime() {
        final CaveDecorationStep step = new CaveDecorationStep(77L);
        final View view = new View();
        int spikes = 0;
        int vegetation = 0;
        for (int cx = -2; cx <= 2; cx++) {
            for (int cz = -2; cz <= 2; cz++) {
                final Map<CaveDecorationStep.Stage, List<CaveDecorationStep.Write>> a = step.decide(cx, cz, view, TERRAIN, MAX_Y);
                final Map<CaveDecorationStep.Stage, List<CaveDecorationStep.Write>> b = new CaveDecorationStep(77L).decide(cx, cz, view,
                    TERRAIN, MAX_Y);
                assertThat(a).isEqualTo(b);
                for (List<CaveDecorationStep.Write> writes : a.values()) {
                    for (CaveDecorationStep.Write w : writes) {
                        assertThat(w.x() >> 4).isEqualTo(cx);
                        assertThat(w.z() >> 4).isEqualTo(cz);
                        assertThat(w.before()).isEqualTo(CaveDecorationStep.terrainCell(view, TERRAIN, w.x(), w.y(), w.z(), MAX_Y));
                        if (w.cell().kind() == CaveCell.Kind.SPIKE) {
                            spikes++;
                        }
                    }
                }
                vegetation += a.get(CaveDecorationStep.Stage.VEGETATION).size();
                for (CaveDecorationStep.Write w : a.get(CaveDecorationStep.Stage.DECORATION)) {
                    assertThat(w.cell().kind()).isIn(CaveCell.Kind.SPIKE, CaveCell.Kind.HARDENED);
                }
            }
        }
        assertThat(spikes).isPositive();
        assertThat(vegetation).isPositive();
    }

    @Test
    void caveCellsHaveCraftEngineRefs() {
        final int granite = PlaceholderRocks.names().indexOf("granite");
        assertThat(CaveDecorationStep.ceRef(CaveCell.spike(granite, CaveCell.Part.TIP, CaveCell.Fluid.EMPTY)))
            .isEqualTo("tfp:rock/spike/granite[part=tip,waterlogged=false]");
        assertThat(CaveDecorationStep.ceRef(CaveCell.spike(granite, CaveCell.Part.BASE, CaveCell.Fluid.SALT_WATER)))
            .isEqualTo("tfp:rock/spike/granite[part=base,waterlogged=true]");
        assertThat(CaveDecorationStep.ceRef(CaveCell.spike(granite, CaveCell.Part.MIDDLE, CaveCell.Fluid.LAVA)))
            .isEqualTo("tfp:rock/spike/granite[part=middle,waterlogged=false]");
        assertThat(CaveDecorationStep.ceRef(CaveCell.hardened(granite))).isEqualTo("tfp:rock/hardened/granite");
        assertThat(CaveDecorationStep.ceRef(CaveCell.of(CaveCell.Kind.MOSSY_COBBLE, granite))).isEqualTo("tfp:rock/mossy_cobble/granite");
        assertThat(CaveDecorationStep.ceRef(CaveCell.of(CaveCell.Kind.COBBLE, granite))).isEqualTo("tfp:rock/cobble/granite");
        assertThat(CaveDecorationStep.ceRef(CaveCell.of(CaveCell.Kind.GUANO, -1))).isEqualTo("tfp:groundcover/guano");
        assertThat(CaveDecorationStep.ceRef(CaveCell.of(CaveCell.Kind.COBWEB, -1))).isNull();
        assertThat(CaveDecorationStep.ceRef(CaveCell.fluid(CaveCell.Fluid.WATER))).isNull();
    }

    @Test
    void writesOnlyWhereTheRealBlockIsStillTheTerrainCell() {
        assertThat(CaveDecorationStep.fits(CaveCell.CAVE_AIR, true, false)).isTrue();
        assertThat(CaveDecorationStep.fits(CaveCell.CAVE_AIR, false, false)).isFalse(); // a geode or ore is there now
        assertThat(CaveDecorationStep.fits(CaveCell.raw(2), false, true)).isTrue();
        assertThat(CaveDecorationStep.fits(CaveCell.raw(2), false, false)).isFalse(); // an ore replaced the rock
        assertThat(CaveDecorationStep.fits(CaveCell.OTHER, true, true)).isFalse();
    }

    @Test
    void erosionHoldsUpOrRemovesAnUnsupportedSandRun() {
        // column (0, 0): rock up to 50, air 51..52, sand 53..55, air above; column (1, 0): a single sand block over air
        final Map<String, String> blocks = new HashMap<>();
        for (int y = -64; y <= 50; y++) {
            blocks.put("0," + y, "rock");
            blocks.put("1," + y, "rock");
        }
        for (int y = 53; y <= 55; y++) {
            blocks.put("0," + y, "sand");
        }
        blocks.put("1,53", "sand");
        final View view = new View() {
            @Override
            public int worldSurface(int x, int z) {
                return 56;
            }
        };
        CaveDecorationStep.erode(0, 0, view, new CaveDecorationStep.ErosionTarget() {
            private String at(int x, int y, int z) {
                return z == 0 && x <= 1 ? blocks.getOrDefault(x + "," + y, "air") : "rock";
            }

            @Override
            public boolean air(int x, int y, int z) {
                return at(x, y, z).equals("air");
            }

            @Override
            public boolean landslides(int x, int y, int z) {
                return at(x, y, z).equals("sand");
            }

            @Override
            public boolean fragile(int x, int y, int z) {
                return air(x, y, z);
            }

            @Override
            public int rawRock(int x, int y, int z) {
                return at(x, y, z).equals("rock") ? 1 : -1;
            }

            @Override
            public int rockAt(int x, int y, int z) {
                return 1;
            }

            @Override
            public void setHardened(int x, int y, int z, int rock) {
                if (z == 0 && x <= 1) {
                    blocks.put(x + "," + y, "hardened");
                }
            }

            @Override
            public Carve substance(int x, int y, int z) {
                return Carve.AIR;
            }

            @Override
            public void setSubstance(int x, int y, int z, Carve substance) {
                blocks.put(x + "," + y, "air");
            }
        });
        // the three-block run is held up by hardened rock at its foot; the single block is removed
        assertThat(blocks.get("0,53")).isEqualTo("hardened");
        assertThat(blocks.get("0,54")).isEqualTo("sand");
        assertThat(blocks.get("1,53")).isEqualTo("air");
        // raw rock over rock stays raw
        assertThat(blocks.get("0,50")).isEqualTo("rock");
    }
}
