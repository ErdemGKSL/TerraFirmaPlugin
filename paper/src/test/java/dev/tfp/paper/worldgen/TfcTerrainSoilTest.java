/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.worldgen;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.worldgen.surface.SurfaceBlock;
import dev.tfp.core.worldgen.surface.SurfaceKind;
import java.util.List;
import org.junit.jupiter.api.Test;

/** PAR-WORLD-41: the surface blocks map to the palette's CraftEngine soil and gravel ids. */
class TfcTerrainSoilTest {
    private static final List<String> IDS = TfcTerrain.SURFACE_IDS;

    @Test
    void soilVariantsMapToTheirOwnIds() {
        assertThat(IDS.get(TfcTerrain.surfaceIndex(SurfaceBlock.GRASS_ARIDISOL, 0))).isEqualTo("tfp:grass/aridisol");
        assertThat(IDS.get(TfcTerrain.surfaceIndex(SurfaceBlock.DIRT_ANDISOL, 3))).isEqualTo("tfp:dirt/andisol");
        assertThat(IDS.get(TfcTerrain.surfaceIndex(SurfaceBlock.COARSE_DIRT_FLUVISOL, 0))).isEqualTo("tfp:coarse_dirt/fluvisol");
        assertThat(IDS.get(TfcTerrain.surfaceIndex(SurfaceBlock.MUD_OXISOL, 0))).isEqualTo("tfp:mud/oxisol");
    }

    @Test
    void everySoilBlockResolvesToAnId() {
        for (SurfaceBlock block : SurfaceBlock.values()) {
            final String name = block.name();
            if (name.startsWith("GRASS_") || name.startsWith("DIRT_") || name.startsWith("MUD_") || name.startsWith("COARSE_DIRT_")) {
                final int index = TfcTerrain.surfaceIndex(block, 0);
                assertThat(index).as(name).isNotNegative();
                assertThat(IDS.get(index)).startsWith("tfp:" + block.kind().name().toLowerCase(java.util.Locale.ROOT) + "/");
            }
        }
    }

    @Test
    void gravelUsesTheRockOfThePosition() {
        for (int rock = 0; rock < TfcTerrain.ROCK_IDS.size(); rock++) {
            final String raw = TfcTerrain.ROCK_IDS.get(rock).substring("tfp:rock/raw/".length());
            assertThat(IDS.get(TfcTerrain.surfaceIndex(SurfaceBlock.GRAVEL, rock))).isEqualTo("tfp:rock/gravel/" + raw);
        }
    }

    @Test
    void otherKindsKeepTheVanillaBlock() {
        for (SurfaceBlock block : new SurfaceBlock[] {SurfaceBlock.ROCK_SAND, SurfaceBlock.YELLOW_SANDSTONE, SurfaceBlock.SNOW,
            SurfaceBlock.PACKED_ICE, SurfaceBlock.HARDENED_CLAY, SurfaceBlock.RAW}) {
            assertThat(TfcTerrain.surfaceIndex(block, 0)).as(block.name()).isEqualTo(-1);
        }
        assertThat(SurfaceKind.values()).isNotEmpty();
    }

    @Test
    void cobbleBasaltTuffAndSeaIceMapToTheirIds() {
        final int granite = TfcTerrain.ROCK_IDS.indexOf("tfp:rock/raw/granite");
        assertThat(IDS.get(TfcTerrain.surfaceIndex(SurfaceBlock.COBBLE, granite))).isEqualTo("tfp:rock/cobble/granite");
        assertThat(IDS.get(TfcTerrain.surfaceIndex(SurfaceBlock.BASALT_COBBLE, 0))).isEqualTo("tfp:rock/cobble/basalt");
        assertThat(IDS.get(TfcTerrain.surfaceIndex(SurfaceBlock.BASALT_GRAVEL, 0))).isEqualTo("tfp:rock/gravel/basalt");
        assertThat(IDS.get(TfcTerrain.surfaceIndex(SurfaceBlock.TUFF_GRAVEL, 0))).isEqualTo("tfp:rock/gravel/tuff");
        assertThat(IDS.get(TfcTerrain.surfaceIndex(SurfaceBlock.SEA_ICE, 0))).isEqualTo("tfp:sea_ice");
        assertThat(TfcTerrain.needsRock(SurfaceBlock.COBBLE)).isTrue();
        assertThat(TfcTerrain.needsRock(SurfaceBlock.SEA_ICE)).isFalse();
    }
}
