/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.worldgen.rock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.tfp.core.worldgen.Seed;
import dev.tfp.core.worldgen.chunk.ChunkDataGenerator;
import dev.tfp.core.worldgen.chunk.PlaceholderRocks;
import dev.tfp.core.worldgen.region.Region;
import dev.tfp.core.worldgen.region.RegionGenerator;
import dev.tfp.core.worldgen.region.RegionSettings;
import dev.tfp.core.worldgen.settings.RockLayerConfig;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

/** PAR-WORLD-52: a world's own rock_layer_settings decide its rock columns and karst points. */
class WorldRockLayersTest {
    private static final long SEED = 4_815_162_342L;
    /** The karst rocks of TFC's rock settings data (chalk, dolomite, limestone, marble; RockSettingsTest checks the data). */
    private static final Predicate<String> KARST = Set.of("tfc:chalk", "tfc:dolomite", "tfc:limestone", "tfc:marble")::contains;

    /** Land and uplift are marble over granite, oceans and volcanic regions basalt over granite. */
    private static RockLayerConfig custom() {
        Map<String, String> rocks = new LinkedHashMap<>();
        rocks.put("granite", "tfc:granite");
        rocks.put("marble", "tfc:marble");
        rocks.put("basalt", "tfc:basalt");
        return new RockLayerConfig(rocks, List.of("granite"),
            List.of(new RockLayerConfig.Layer("marble", Map.of("marble", "bottom")),
                new RockLayerConfig.Layer("basalt", Map.of("basalt", "bottom"))),
            List.of("basalt"), List.of("basalt"), List.of("marble"), List.of("marble"));
    }

    private record Sampled(Set<String> surface, int karst, Set<Integer> columnRocks) {}

    private static Sampled sample(RockLayerSampler rockLayers) {
        Seed seed = Seed.of(SEED);
        RegionGenerator regions = new RegionGenerator(RegionSettings.OVERWORLD, seed, rockLayers, KARST);
        ChunkDataGenerator chunks = new ChunkDataGenerator(regions, seed);
        Set<String> surface = new HashSet<>();
        Set<Integer> column = new HashSet<>();
        int karst = 0;
        for (int gx = -600; gx <= 600; gx += 40) {
            for (int gz = -600; gz <= 600; gz += 40) {
                Region.Point point = regions.getOrCreateRegionPoint(gx, gz);
                String rock = regions.getSurfaceRock(point);
                surface.add(rock);
                // the karst flag follows the world's surface rock
                assertThat(point.isSurfaceRockKarst).as("karst at %s", rock).isEqualTo(KARST.test(rock));
                if (point.isSurfaceRockKarst) {
                    karst++;
                }
                int x = gx * 8;
                int z = gz * 8;
                for (int y : new int[] {60, 0, -50}) {
                    ChunkDataGenerator.RockSample s = chunks.generateRock(x, y, z, 64);
                    column.add(PlaceholderRocks.index(rockLayers, s.point(), s.layer()));
                }
            }
        }
        return new Sampled(surface, karst, column);
    }

    @Test
    void aNonPresetConfigChangesTheRocksAndTheKarstFlagConsistently() {
        RockLayerSampler world = PlaceholderRocks.requireKnown(RockLayerSampler.decode(custom()));
        Sampled own = sample(world);
        Sampled preset = sample(RockLayerSampler.PRESET);

        assertThat(own.surface()).isSubsetOf("tfc:marble", "tfc:basalt").contains("tfc:marble");
        assertThat(own.karst()).isPositive();
        Set<Integer> allowed = Set.of(PlaceholderRocks.indexOf("tfc:granite"), PlaceholderRocks.indexOf("tfc:marble"),
            PlaceholderRocks.indexOf("tfc:basalt"));
        assertThat(own.columnRocks()).isSubsetOf(allowed).contains(PlaceholderRocks.indexOf("tfc:granite"));

        // the preset walk over the same seed gives other rocks: the config, not the preset, decided
        assertThat(preset.surface()).hasSizeGreaterThan(3);
        assertThat(preset.columnRocks()).hasSizeGreaterThan(3);
        assertThat(PlaceholderRocks.index(0b1010, 0)).isEqualTo(PlaceholderRocks.index(RockLayerSampler.PRESET, 0b1010, 0));
    }

    @Test
    void aRockWithoutRockSettingsIsRefusedAtLoad() {
        Map<String, String> rocks = new LinkedHashMap<>(custom().rocks());
        rocks.put("pumice", "mymod:pumice");
        RockLayerConfig c = custom();
        RockLayerConfig bad = new RockLayerConfig(rocks, c.bottom(), c.layers(), c.oceanFloor(), c.volcanic(), c.land(),
            c.uplift());
        RockLayerSampler sampler = RockLayerSampler.decode(bad);
        assertThatThrownBy(() -> PlaceholderRocks.requireKnown(sampler))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("mymod:pumice");
        assertThat(PlaceholderRocks.requireKnown(RockLayerSampler.PRESET)).isSameAs(RockLayerSampler.PRESET);
    }
}
