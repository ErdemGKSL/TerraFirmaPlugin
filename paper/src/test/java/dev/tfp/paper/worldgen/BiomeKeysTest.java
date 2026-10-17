/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.worldgen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.tfp.core.worldgen.biome.TfcBiomes;
import java.util.OptionalLong;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The position to biome id mapping and the placeholder vanilla biomes of M2-GEN-07, with fakes. */
class BiomeKeysTest {
    @Test
    void everyTfcBiomeMapsToAVanillaPlaceholder() {
        for (int id = 0; id < TfcBiomes.COUNT; id++) {
            assertThat(BiomeKeys.VANILLA).contains(BiomeKeys.vanillaKey(id));
        }
    }

    @Test
    void waterAndIceGoToTheirVanillaKinds() {
        assertThat(BiomeKeys.vanillaKey(TfcBiomes.OCEAN)).isEqualTo("ocean");
        assertThat(BiomeKeys.vanillaKey(TfcBiomes.DEEP_OCEAN)).isEqualTo("deep_ocean");
        assertThat(BiomeKeys.vanillaKey(TfcBiomes.LAKE)).isEqualTo("river");
        assertThat(BiomeKeys.vanillaKey(TfcBiomes.ICE_SHEET)).isEqualTo("snowy_plains");
    }

    @Test
    void landKindsGoToTheirVanillaKinds() {
        assertThat(BiomeKeys.vanillaKey(TfcBiomes.PLAINS)).isEqualTo("plains");
        assertThat(BiomeKeys.vanillaKey(TfcBiomes.DUNE_SEA)).isEqualTo("desert");
        assertThat(BiomeKeys.vanillaKey(TfcBiomes.BADLANDS)).isEqualTo("badlands");
        assertThat(BiomeKeys.vanillaKey(TfcBiomes.MOUNTAINS)).isEqualTo("windswept_hills");
    }

    @Test
    void rejectsIdsOutsideTheTfcRange() {
        assertThatThrownBy(() -> BiomeKeys.vanillaKey(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BiomeKeys.vanillaKey(TfcBiomes.COUNT)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void positionsFloorToTheirGridCell() {
        // A fake grid: the id is the grid coordinates packed, so the lookup shows which cell it asked for.
        java.util.function.IntBinaryOperator grid = (gx, gz) -> gx * 1000 + gz;
        assertThat(BiomeKeys.biomeIdAt(grid, 0, 0)).isEqualTo(0);
        assertThat(BiomeKeys.biomeIdAt(grid, 127, 127)).isEqualTo(0);
        assertThat(BiomeKeys.biomeIdAt(grid, 128, 5)).isEqualTo(1000);
        // Negative coordinates floor (block >> 7), not truncate toward zero
        assertThat(BiomeKeys.biomeIdAt(grid, -1, -1)).isEqualTo(-1000 - 1);
        assertThat(BiomeKeys.biomeIdAt(grid, -128, 0)).isEqualTo(-1000);
    }

    @Test
    void theVanillaListHasNoDuplicates() {
        assertThat(List.copyOf(BiomeKeys.VANILLA)).doesNotHaveDuplicates();
    }

    @Test
    void generatorIdIsAnOptionalSeed() {
        assertThat(TfcChunkGenerator.parseSeed(null)).isEqualTo(OptionalLong.empty());
        assertThat(TfcChunkGenerator.parseSeed(" ")).isEqualTo(OptionalLong.empty());
        assertThat(TfcChunkGenerator.parseSeed("42")).isEqualTo(OptionalLong.of(42));
        assertThat(TfcChunkGenerator.parseSeed("-7")).isEqualTo(OptionalLong.of(-7));
        assertThatThrownBy(() -> TfcChunkGenerator.parseSeed("tfc")).isInstanceOf(IllegalArgumentException.class);
    }
}
