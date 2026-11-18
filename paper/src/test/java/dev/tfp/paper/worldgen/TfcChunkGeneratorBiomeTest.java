/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.worldgen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.tfp.core.worldgen.biome.TfcBiomes;
import dev.tfp.paper.testing.MockServerTest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.block.Biome;
import org.junit.jupiter.api.Test;

/**
 * PAR-WORLD-02: the {@code BiomeProvider} of {@link TfcChunkGenerator} returns the datapack base biome {@code tfp:<name>} of
 * each TFC biome, and the vanilla stand-in only when the registry lacks it.
 */
class TfcChunkGeneratorBiomeTest extends MockServerTest {

    @Test
    void everyTfcBiomeAsksForItsDatapackBaseBiomeFirst() {
        List<NamespacedKey> asked = new ArrayList<>();
        Biome stand = Biome.PLAINS;
        Biome[] out = TfcTerrain.resolveBiomes(key -> {
            asked.add(key);
            return key.getNamespace().equals("tfp") ? stand : null;
        });
        assertThat(out).hasSize(TfcBiomes.COUNT).containsOnly(stand);
        assertThat(asked).hasSize(TfcBiomes.COUNT);
        for (int id = 0; id < TfcBiomes.COUNT; id++) {
            assertThat(asked.get(id).toString()).isEqualTo(BiomeKeys.baseId(id));
        }
        assertThat(BiomeKeys.baseId(TfcBiomes.PLAINS)).isEqualTo("tfp:plains");
        assertThat(BiomeKeys.baseId(TfcBiomes.DEEP_OCEAN_TRENCH)).isEqualTo("tfp:deep_ocean_trench");
        assertThat(BiomeKeys.BASE).hasSize(TfcBiomes.COUNT).doesNotHaveDuplicates();
        assertThatThrownBy(() -> BiomeKeys.basePath(TfcBiomes.COUNT)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @SuppressWarnings("deprecation")
    void missingDatapackFallsBackToTheVanillaStandIn() {
        Biome[] out = TfcTerrain.resolveBiomes(key -> key.getNamespace().equals("tfp") ? null : Registry.BIOME.get(key));
        for (int id = 0; id < TfcBiomes.COUNT; id++) {
            assertThat(out[id].getKey()).isEqualTo(NamespacedKey.minecraft(BiomeKeys.vanillaKey(id)));
            assertThat(BiomeKeys.fallbackId(id)).isEqualTo(out[id].getKey().toString());
        }
        assertThat(TfcTerrain.countFallbacks(out)).isEqualTo(TfcBiomes.COUNT);
        assertThatThrownBy(() -> TfcTerrain.resolveBiomes(key -> null)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void theGeneratedDatapackHasEveryBaseBiome() {
        // the datagen output next to content/generated/content/data (the jar ships both)
        Path data = Path.of(System.getProperty("tfp.contentData", "../content/generated/content/data"));
        Path biomes = data.resolveSibling("datapack").resolve("tfp_seasonal_biomes/data/tfp/worldgen/biome");
        for (String path : BiomeKeys.BASE) {
            assertThat(Files.isRegularFile(biomes.resolve(path + ".json"))).as(path).isTrue();
            assertThat(biomes.resolve("seasonal").resolve(path)).as(path).isDirectory();
        }
    }
}
