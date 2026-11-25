/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.worldgen.volcano;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.worldgen.biome.TfcBiomes;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * PAR-WORLD-21: the five volcanic placed ids that are no row of the configured-feature table ({@code volcano_features.tsv}):
 * the stratovolcano rivulets {@code crater_lake_rivulet}, {@code crater_lake_sulfur}, {@code fuji_rivulet},
 * {@code tahoma_rivulet} and {@code glacial_oceanic_spring}. Expected values copied from TFC's
 * {@code data/tfc/worldgen/placed_feature/<id>.json} at c3b4d9536.
 */
class VolcanicPlacedIdsTest {
    private static Map<String, VolcanicFeatures.Rivulets> stratovolcanoRivulets() throws ReflectiveOperationException {
        final Field f = VolcanicFeatures.class.getDeclaredField("STRATOVOLCANO_RIVULETS");
        f.setAccessible(true);
        return Arrays.stream((VolcanicFeatures.Rivulets[]) f.get(null))
            .collect(Collectors.toMap(VolcanicFeatures.Rivulets::id, Function.identity()));
    }

    private static void assertRivulet(VolcanicFeatures.Rivulets r, String configured, int count, Stratovolcanoes.Variant variant,
            float minEasing, float hashMin, float hashMax, Rivulet.Config config) {
        assertThat(r.configured()).as(r.id()).isEqualTo(configured);
        assertThat(r.count()).as(r.id()).isEqualTo(count);
        assertThat(r.placement().variant()).as(r.id()).isEqualTo(variant);
        assertThat(r.placement().center()).as(r.id()).isFalse();
        assertThat(r.placement().minEasing()).as(r.id()).isEqualTo(minEasing);
        assertThat(r.placement().hashMin()).as(r.id()).isEqualTo(hashMin);
        assertThat(r.placement().hashMax()).as(r.id()).isEqualTo(hashMax);
        assertThat(r.config()).as(r.id()).isSameAs(config);
    }

    @Test
    void stratovolcanoRivuletsMatchTheirPlacedFeatures() throws ReflectiveOperationException {
        final Map<String, VolcanicFeatures.Rivulets> r = stratovolcanoRivulets();
        assertThat(r).containsOnlyKeys("tfc:fuji_rivulet", "tfc:tahoma_rivulet", "tfc:crater_lake_rivulet", "tfc:crater_lake_sulfur");
        // count, in_square, tfc:stratovolcano {variant, min_easing, hash_min (default 0), hash_max (default 1)}
        assertRivulet(r.get("tfc:fuji_rivulet"), "tfc:stratovolcano_rivulet", 3, Stratovolcanoes.Variant.FUJI, 0.7f, 0f, 0.4f,
            Rivulet.Config.STRATOVOLCANO);
        assertRivulet(r.get("tfc:tahoma_rivulet"), "tfc:stratovolcano_rivulet", 3, Stratovolcanoes.Variant.TAHOMA, 0.83f, 0f, 0.4f,
            Rivulet.Config.STRATOVOLCANO);
        assertRivulet(r.get("tfc:crater_lake_rivulet"), "tfc:stratovolcano_rivulet", 1, Stratovolcanoes.Variant.CRATER_LAKE, 0.6f,
            0.83f, 1f, Rivulet.Config.STRATOVOLCANO);
        assertRivulet(r.get("tfc:crater_lake_sulfur"), "tfc:sulfur_rivulet", 1, Stratovolcanoes.Variant.CRATER_LAKE, 0.6f, 0.83f,
            1f, Rivulet.Config.SULFUR);
    }

    @Test
    void glacialOceanicSpringMatchesItsPlacedFeature() {
        // count 200, in_square, height_range uniform 80..92, the configured tfc:glacial_spring
        final VolcanicFeatures.Springs s = VolcanicFeatures.GLACIAL_OCEANIC_SPRING;
        assertThat(s.id()).isEqualTo("tfc:glacial_oceanic_spring");
        assertThat(s.count()).isEqualTo(200);
        assertThat(s.minY()).isEqualTo(80);
        assertThat(s.maxY()).isEqualTo(92);
        assertThat(s.biasedToBottom()).isFalse();
        assertThat(s.climate()).isNull();
        assertThat(s.config()).isSameAs(Spring.Config.GLACIAL);
        assertThat(VolcanicFeatures.rules()).doesNotContainKey(s.id());
    }

    @Test
    void glacialOceanicSpringRunsInTheThreeBiomesThatListIt() {
        int listed = 0;
        for (int biome = 0; biome < TfcBiomes.COUNT; biome++) {
            if (VolcanicFeatures.hasGlacialOceanicSpring(biome)) {
                listed++;
            }
        }
        assertThat(listed).isEqualTo(3);
        assertThat(VolcanicFeatures.hasGlacialOceanicSpring(TfcBiomes.GLACIATED_OCEANIC_MOUNTAINS)).isTrue();
        assertThat(VolcanicFeatures.hasGlacialOceanicSpring(TfcBiomes.ICE_SHEET_OCEANIC_MOUNTAINS_EDGE)).isTrue();
        assertThat(VolcanicFeatures.hasGlacialOceanicSpring(TfcBiomes.GLACIATED_VOLCANIC_OCEANIC_MOUNTAINS)).isTrue();
    }
}
