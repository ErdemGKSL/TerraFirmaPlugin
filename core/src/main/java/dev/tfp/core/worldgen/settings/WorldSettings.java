/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/settings/Settings.java @ c3b4d9536 (record and codec field names), with the
 * overworld preset src/generated/resources/data/tfc/worldgen/world_preset/overworld.json @ c3b4d9536 (the defaults)
 */
package dev.tfp.core.worldgen.settings;

import dev.tfp.core.worldgen.region.RegionSettings;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * TFC's world generator settings ({@code Settings}, PAR-WORLD-22). TFC sets them on the Create TFC World screen and keeps
 * them in level.dat; here they come from the server's {@code worldgen-settings.yml} and are stored in the world folder the
 * first time the generator runs (NP-CLIENT-05). Immutable and thread-safe.
 */
public record WorldSettings(
    boolean flatBedrock,
    int spawnDistance,
    int spawnCenterX,
    int spawnCenterZ,
    int temperatureScale,
    float temperatureConstant,
    int rainfallScale,
    float rainfallConstant,
    RockLayerConfig rockLayerSettings,
    float continentalness,
    float grassDensity,
    boolean finiteContinents
) {
    /** TFC's codec field names, in codec order. */
    public static final List<String> FIELDS = List.of("flat_bedrock", "spawn_distance", "spawn_center_x", "spawn_center_z",
        "temperature_scale", "temperature_constant", "rainfall_scale", "rainfall_constant", "rock_layer_settings",
        "continentalness", "grass_density", "finite_continents");

    /** TFC's overworld preset ({@code BuiltinWorldPreset.defaultSettings}). */
    public static final WorldSettings DEFAULT = new WorldSettings(false, 4_000, 0, 0, 20_000, 0f, 20_000, 0f, RockLayerConfig.PRESET,
        0.5f, 0.5f, false);

    public WorldSettings {
        if (rockLayerSettings == null) {
            throw new IllegalArgumentException("rock_layer_settings is required");
        }
    }

    /** The six fields the region generator reads. */
    public RegionSettings regionSettings() {
        return new RegionSettings(continentalness, finiteContinents, temperatureScale, temperatureConstant, rainfallScale,
            rainfallConstant);
    }

    /** The codec form, TFC's field names in codec order. */
    public Map<String, Object> toMap() {
        final Map<String, Object> out = new LinkedHashMap<>();
        out.put("flat_bedrock", flatBedrock);
        out.put("spawn_distance", spawnDistance);
        out.put("spawn_center_x", spawnCenterX);
        out.put("spawn_center_z", spawnCenterZ);
        out.put("temperature_scale", temperatureScale);
        out.put("temperature_constant", (double) temperatureConstant);
        out.put("rainfall_scale", rainfallScale);
        out.put("rainfall_constant", (double) rainfallConstant);
        out.put("rock_layer_settings", rockLayerSettings.toMap());
        out.put("continentalness", (double) continentalness);
        out.put("grass_density", (double) grassDensity);
        out.put("finite_continents", finiteContinents);
        return out;
    }

    /**
     * Reads the codec form over {@code base}: a field that is present replaces the base value, a missing one keeps it (TFC's
     * codec requires most fields; a server config only names what it changes). {@code rock_layer_settings}, when present, is
     * read whole.
     *
     * @throws IllegalArgumentException on an unknown field or a value of the wrong type
     */
    public static WorldSettings fromMap(Map<?, ?> in, WorldSettings base) {
        for (Object key : in.keySet()) {
            if (!FIELDS.contains(String.valueOf(key))) {
                throw new IllegalArgumentException("unknown world setting '" + key + "' (known: " + FIELDS + ")");
            }
        }
        final Object rocks = in.get("rock_layer_settings");
        final RockLayerConfig rockLayers;
        if (rocks == null) {
            rockLayers = base.rockLayerSettings;
        } else if (rocks instanceof Map<?, ?> m) {
            rockLayers = RockLayerConfig.fromMap(m);
        } else {
            throw new IllegalArgumentException("rock_layer_settings must be a map");
        }
        return new WorldSettings(
            bool(in, "flat_bedrock", base.flatBedrock),
            integer(in, "spawn_distance", base.spawnDistance),
            integer(in, "spawn_center_x", base.spawnCenterX),
            integer(in, "spawn_center_z", base.spawnCenterZ),
            integer(in, "temperature_scale", base.temperatureScale),
            decimal(in, "temperature_constant", base.temperatureConstant),
            integer(in, "rainfall_scale", base.rainfallScale),
            decimal(in, "rainfall_constant", base.rainfallConstant),
            rockLayers,
            decimal(in, "continentalness", base.continentalness),
            decimal(in, "grass_density", base.grassDensity),
            bool(in, "finite_continents", base.finiteContinents));
    }

    private static boolean bool(Map<?, ?> in, String key, boolean fallback) {
        final Object v = in.get(key);
        if (v == null) {
            return fallback;
        }
        if (v instanceof Boolean b) {
            return b;
        }
        throw new IllegalArgumentException(key + " must be true or false, got " + v);
    }

    private static int integer(Map<?, ?> in, String key, int fallback) {
        final Object v = in.get(key);
        if (v == null) {
            return fallback;
        }
        if (v instanceof Number n && n.doubleValue() == Math.rint(n.doubleValue())) {
            return n.intValue();
        }
        throw new IllegalArgumentException(key + " must be a whole number, got " + v);
    }

    private static float decimal(Map<?, ?> in, String key, float fallback) {
        final Object v = in.get(key);
        if (v == null) {
            return fallback;
        }
        if (v instanceof Number n) {
            return n.floatValue();
        }
        throw new IllegalArgumentException(key + " must be a number, got " + v);
    }
}
