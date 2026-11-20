/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.worldgen.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.tfp.core.worldgen.Seed;
import dev.tfp.core.worldgen.region.Region;
import dev.tfp.core.worldgen.region.RegionGenerator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** PAR-WORLD-22: TFC's {@code Settings} codec names, the overworld preset defaults, the map round trip and the bedrock floor. */
class WorldSettingsTest {
    private static final Path PRESET = Path.of("/opt/tfc/src/generated/resources/data/tfc/worldgen/world_preset/overworld.json");

    @SuppressWarnings("unchecked")
    private static Map<String, Object> tfcSettings() throws IOException {
        assumeTrue(Files.isRegularFile(PRESET), "no TFC checkout at /opt/tfc");
        final JsonObject root = JsonParser.parseString(Files.readString(PRESET)).getAsJsonObject();
        final JsonObject s = root.getAsJsonObject("dimensions").getAsJsonObject("minecraft:overworld")
            .getAsJsonObject("generator").getAsJsonObject("tfc_settings");
        return new Gson().fromJson(s, LinkedHashMap.class);
    }

    @Test
    void defaultsAreTfcsOverworldPreset() throws IOException {
        final Map<String, Object> tfc = tfcSettings();
        // the preset omits the optional constants (codec default 0); every other field is in it
        assertThat(WorldSettings.FIELDS).containsAll(tfc.keySet());
        assertThat(WorldSettings.fromMap(tfc, WorldSettings.DEFAULT)).isEqualTo(WorldSettings.DEFAULT);
        assertThat(RockLayerConfig.fromMap((Map<?, ?>) tfc.get("rock_layer_settings"))).isEqualTo(RockLayerConfig.PRESET);
        // a base of all-different values: the preset alone yields the defaults (the constants are 0 in TFC's codec)
        final WorldSettings other = new WorldSettings(true, 1, 2, 3, 4, 0f, 6, 0f, RockLayerConfig.PRESET, 0.9f, 0.1f, true);
        assertThat(WorldSettings.fromMap(tfc, other)).isEqualTo(WorldSettings.DEFAULT);
    }

    @Test
    void roundTrip() {
        final WorldSettings s = new WorldSettings(true, 2000, 100, -300, 10_000, 0.25f, 30_000, -0.5f, RockLayerConfig.PRESET, 0.7f,
            0.2f, true);
        assertThat(WorldSettings.fromMap(s.toMap(), WorldSettings.DEFAULT)).isEqualTo(s);
        assertThat(WorldSettings.fromMap(WorldSettings.DEFAULT.toMap(), s)).isEqualTo(WorldSettings.DEFAULT);
        assertThat(s.toMap().keySet()).containsExactlyElementsOf(WorldSettings.FIELDS);
    }

    @Test
    void partialMapsKeepTheBase() {
        final WorldSettings s = WorldSettings.fromMap(Map.of("temperature_scale", 5000, "finite_continents", true), WorldSettings.DEFAULT);
        assertThat(s.temperatureScale()).isEqualTo(5000);
        assertThat(s.finiteContinents()).isTrue();
        assertThat(s.spawnDistance()).isEqualTo(4000);
        assertThat(s.regionSettings().temperatureScale()).isEqualTo(5000);
    }

    @Test
    void badFieldsAreRefused() {
        assertThatThrownBy(() -> WorldSettings.fromMap(Map.of("temperatur_scale", 1), WorldSettings.DEFAULT))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("temperatur_scale");
        assertThatThrownBy(() -> WorldSettings.fromMap(Map.of("spawn_distance", 1.5), WorldSettings.DEFAULT))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> WorldSettings.fromMap(Map.of("flat_bedrock", "yes"), WorldSettings.DEFAULT))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> WorldSettings.fromMap(Map.of("rock_layer_settings", Map.of("rocks", Map.of())), WorldSettings.DEFAULT))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nonDefaultSettingsChangeTheRegionMap() {
        final WorldSettings finite = WorldSettings.fromMap(Map.of("finite_continents", true, "temperature_scale", 5000,
            "continentalness", 0.2f), WorldSettings.DEFAULT);
        final Region.Point a = new RegionGenerator(WorldSettings.DEFAULT.regionSettings(), Seed.of(42)).getOrCreateRegionPoint(300, 300);
        final Region.Point b = new RegionGenerator(finite.regionSettings(), Seed.of(42)).getOrCreateRegionPoint(300, 300);
        assertThat(b.temperature).isNotEqualTo(a.temperature);
    }

    @Test
    void bedrockFloor() {
        assertThat(BedrockLayer.columns(3, -4, true)).containsOnly(1);
        final int[] natural = BedrockLayer.columns(3, -4, false);
        assertThat(natural).isEqualTo(BedrockLayer.columns(3, -4, false));
        int blocks = 0;
        for (int mask : natural) {
            assertThat(mask & 1).isEqualTo(1);
            assertThat(mask >> BedrockLayer.HEIGHT).isZero();
            blocks += Integer.bitCount(mask);
        }
        // expected blocks per column: sum over y of (6 - y) / 6 = 3.5
        assertThat(blocks / 256.0).isBetween(3.2, 3.8);
        assertThat(BedrockLayer.columns(4, -4, false)).isNotEqualTo(natural);
        assertThat(List.of(natural.length)).containsExactly(256);
    }
}
