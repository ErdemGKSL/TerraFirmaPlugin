/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/feature/plant/{FruitTreeFeature,BananaFeature}.java @ c3b4d9536,
 *       src/main/java/net/dries007/tfc/world/placement/ClimatePlacement.java @ c3b4d9536 (CODEC: field names and defaults),
 *       src/main/resources/data/tfc/worldgen/placed_feature/plant/{banana,cherry,green_apple,red_apple,lemon,olive,orange,peach,plum}.json
 */
package dev.tfp.paper.fruittree;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.tfp.core.worldgen.groundcover.ClimateFilter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * PAR-FARM-27: one placed feature of a fruit tree or the banana, read from the datagen output of TFC's placed and configured
 * features: its climate filter, the {@code rarity_filter}, and the configured feature's type ({@code tfc:fruit_trees} or
 * {@code tfc:bananas}) and block. Immutable.
 *
 * @param id      the placed feature's id as the data names it ({@code tfp:plant/banana})
 * @param kind    the configured feature
 * @param block   the configured block ({@code tfp:plant/cherry_growing_branch}, {@code tfp:plant/banana_plant})
 * @param climate the {@code tfc:climate} placement
 * @param rarity  the {@code rarity_filter} chance (one in this many chunks)
 */
public record GenFeature(String id, Kind kind, String block, ClimateFilter climate, int rarity) {
    /** The configured feature types. */
    public enum Kind { FRUIT_TREE, BANANA }

    public static final String PLACED_DIR = "tfp/worldgen/placed_feature/";
    public static final String CONFIGURED_DIR = "tfp/worldgen/configured_feature/";

    /** The TFC id of the feature ({@code tfc:plant/banana}), which seeds its random as the other features' ids do. */
    public String tfcId() {
        return id.startsWith("tfp:") ? "tfc:" + id.substring("tfp:".length()) : id;
    }

    /** The tree this feature places, when it is a fruit tree ({@code tfp:plant/cherry_growing_branch} is the cherry). */
    public Optional<dev.tfp.core.berry.FruitTree> tree() {
        if (kind != Kind.FRUIT_TREE) {
            return Optional.empty();
        }
        return FruitTreeDevice.parse(dev.tfp.paper.integration.BlockStateRef.of(block)).map(FruitTreeDevice.Parsed::tree);
    }

    /**
     * Reads one placed feature, or empty when the data lacks it or it is not a fruit tree or banana feature.
     *
     * @param id   the placed feature id ({@code tfp:plant/banana})
     * @param read the text of a file below the data root, or empty when it is missing
     */
    public static Optional<GenFeature> read(String id, Function<String, Optional<String>> read) {
        String path = id.substring(id.indexOf(':') + 1);
        Optional<String> placed = read.apply(PLACED_DIR + path + ".json");
        if (placed.isEmpty()) {
            return Optional.empty();
        }
        JsonObject p = JsonParser.parseString(placed.get()).getAsJsonObject();
        String configuredId = p.get("feature").getAsString();
        Optional<String> configured = read.apply(CONFIGURED_DIR + configuredId.substring(configuredId.indexOf(':') + 1) + ".json");
        if (configured.isEmpty()) {
            return Optional.empty();
        }
        JsonObject c = JsonParser.parseString(configured.get()).getAsJsonObject();
        String type = c.get("type").getAsString();
        Kind kind;
        if (type.endsWith(":fruit_trees")) {
            kind = Kind.FRUIT_TREE;
        } else if (type.endsWith(":bananas")) {
            kind = Kind.BANANA;
        } else {
            return Optional.empty();
        }
        String state = c.getAsJsonObject("config").get("state").getAsString();
        ClimateFilter climate = null;
        int rarity = 1;
        List<String> order = new ArrayList<>();
        for (JsonElement e : p.getAsJsonArray("placement")) {
            JsonObject m = e.getAsJsonObject();
            String t = m.get("type").getAsString();
            order.add(t.substring(t.indexOf(':') + 1));
            switch (t.substring(t.indexOf(':') + 1)) {
                case "climate" -> climate = climate(m);
                case "rarity_filter" -> rarity = m.get("chance").getAsInt();
                case "heightmap", "in_square" -> {
                }
                default -> throw new IllegalArgumentException("placement " + t + " of " + id + " is not ported");
            }
        }
        // the planner runs the modifiers in TFC's order: climate, heightmap, in_square, rarity_filter
        if (climate == null || !order.equals(List.of("climate", "heightmap", "in_square", "rarity_filter"))) {
            throw new IllegalArgumentException("placement order of " + id + " is " + order);
        }
        return Optional.of(new GenFeature(id, kind, state, climate, rarity));
    }

    /** {@code ClimatePlacement.CODEC}: the names and defaults (as core's {@code PlantData.climate}). */
    static ClimateFilter climate(JsonObject m) {
        return new ClimateFilter(f(m, "min_temperature", Float.NEGATIVE_INFINITY), f(m, "max_temperature", Float.POSITIVE_INFINITY),
            f(m, "min_groundwater", Float.NEGATIVE_INFINITY), f(m, "max_groundwater", Float.POSITIVE_INFINITY),
            f(m, "min_rain_variance", -1f), f(m, "max_rain_variance", 1f),
            m.has("rain_variance_absolute") && m.get("rain_variance_absolute").getAsBoolean(),
            (int) f(m, "min_forest", 0), (int) f(m, "max_forest", 4), (int) f(m, "min_elevation", -64), (int) f(m, "max_elevation", 320),
            m.has("fuzzy") && m.get("fuzzy").getAsBoolean(), m.has("ignore_rivers") && m.get("ignore_rivers").getAsBoolean());
    }

    private static float f(JsonObject m, String key, float fallback) {
        return m.has(key) ? m.get(key).getAsFloat() : fallback;
    }
}
