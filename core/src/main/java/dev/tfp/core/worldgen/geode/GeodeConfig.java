/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/resources/data/tfc/worldgen/configured_feature/geode.json @ c3b4d9536 and placed_feature/geode.json (kept
 * verbatim below), read with the defaults of Minecraft 1.21 GeodeConfiguration, GeodeLayerSettings and GeodeCrackSettings
 * (codec defaults checked against the 1.21.11 server bytecode).
 */
package dev.tfp.core.worldgen.geode;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * A {@code minecraft:geode} configuration: the block of each layer (ids in our namespace), the layer and crack settings, the
 * chances, the distribution and offset ranges ({@code UniformInt} min and max) and the invalid-block threshold, plus the
 * placement of TFC's placed feature. Immutable.
 */
public record GeodeConfig(
    String filling, String outerLayer, String middleLayer, String innerLayer, String alternateInnerLayer,
    double filling2, double innerLayer2, double middleLayer2, double outerLayer2,
    double generateCrackChance, double baseCrackSize, int crackPointOffset,
    double usePotentialPlacementsChance, double useAlternateLayer0Chance, boolean placementsRequireLayer0Alternate,
    int outerWallDistanceMin, int outerWallDistanceMax, int distributionPointsMin, int distributionPointsMax,
    int pointOffsetMin, int pointOffsetMax, int minGenOffset, int maxGenOffset, double noiseMultiplier, int invalidBlocksThreshold,
    int rarity, int minY, int maxY) {

    /** {@code configured_feature/geode.json}. */
    static final String CONFIGURED = """
        {"type":"minecraft:geode","config":{"blocks":{"filling_provider":{"type":"minecraft:simple_state_provider","state":\
        {"Name":"minecraft:air"}},"outer_layer_provider":{"type":"minecraft:simple_state_provider","state":\
        {"Name":"tfc:rock/hardened/basalt"}},"middle_layer_provider":{"type":"minecraft:simple_state_provider","state":\
        {"Name":"tfc:rock/hardened/quartzite"}},"inner_layer_provider":{"type":"minecraft:simple_state_provider","state":\
        {"Name":"tfc:rock/hardened/quartzite"}},"alternate_inner_layer_provider":{"type":"minecraft:simple_state_provider",\
        "state":{"Name":"tfc:ore/amethyst/quartzite"}},"invalid_blocks":"#minecraft:geode_invalid_blocks","cannot_replace":\
        "#minecraft:features_cannot_replace","inner_placements":[{"Name":"minecraft:air"}]},"use_alternate_layer0_chance":0.3,\
        "invalid_blocks_threshold":1,"crack":{},"layers":{}}}""";

    /** {@code placed_feature/geode.json}. */
    static final String PLACED = """
        {"feature":"tfc:geode","placement":[{"type":"minecraft:rarity_filter","chance":50},{"type":"minecraft:in_square"},\
        {"type":"minecraft:height_range","height":{"type":"uniform","min_inclusive":{"absolute":-48},"max_inclusive":\
        {"absolute":30}}}]}""";

    /** TFC's geode. */
    public static final GeodeConfig TFC = parse(JsonParser.parseString(CONFIGURED).getAsJsonObject(),
        JsonParser.parseString(PLACED).getAsJsonObject());

    /** Parses a configured and a placed geode feature, with the vanilla codec defaults for the keys they omit. */
    static GeodeConfig parse(JsonObject configured, JsonObject placed) {
        final JsonObject c = configured.getAsJsonObject("config");
        final JsonObject blocks = c.getAsJsonObject("blocks");
        final JsonObject layers = c.has("layers") ? c.getAsJsonObject("layers") : new JsonObject();
        final JsonObject crack = c.has("crack") ? c.getAsJsonObject("crack") : new JsonObject();
        final int[] wall = uniform(c, "outer_wall_distance", 4, 5);
        final int[] points = uniform(c, "distribution_points", 3, 4);
        final int[] offset = uniform(c, "point_offset", 1, 2);
        int rarity = 1;
        int minY = Integer.MIN_VALUE;
        int maxY = Integer.MAX_VALUE;
        for (JsonElement e : placed.getAsJsonArray("placement")) {
            final JsonObject p = e.getAsJsonObject();
            switch (p.get("type").getAsString()) {
                case "minecraft:rarity_filter" -> rarity = p.get("chance").getAsInt();
                case "minecraft:in_square" -> { }
                case "minecraft:height_range" -> {
                    final JsonObject h = p.getAsJsonObject("height");
                    if (!h.get("type").getAsString().equals("uniform")) {
                        throw new IllegalArgumentException("height provider not ported: " + h);
                    }
                    minY = h.getAsJsonObject("min_inclusive").get("absolute").getAsInt();
                    maxY = h.getAsJsonObject("max_inclusive").get("absolute").getAsInt();
                }
                default -> throw new IllegalArgumentException("placement not ported: " + p);
            }
        }
        if (!blocks.get("invalid_blocks").getAsString().equals("#minecraft:geode_invalid_blocks")
            || !blocks.get("cannot_replace").getAsString().equals("#minecraft:features_cannot_replace")
            || blocks.getAsJsonArray("inner_placements").size() != 1
            || !state(blocks.getAsJsonArray("inner_placements").get(0)).equals("minecraft:air")) {
            throw new IllegalArgumentException("geode tags or inner placements not ported: " + blocks);
        }
        return new GeodeConfig(provider(blocks, "filling_provider"), provider(blocks, "outer_layer_provider"),
            provider(blocks, "middle_layer_provider"), provider(blocks, "inner_layer_provider"),
            provider(blocks, "alternate_inner_layer_provider"),
            number(layers, "filling", 1.7), number(layers, "inner_layer", 2.2), number(layers, "middle_layer", 3.2),
            number(layers, "outer_layer", 4.2),
            number(crack, "generate_crack_chance", 1.0), number(crack, "base_crack_size", 2.0),
            (int) number(crack, "crack_point_offset", 2),
            number(c, "use_potential_placements_chance", 0.35), number(c, "use_alternate_layer0_chance", 0.0),
            !c.has("placements_require_layer0_alternate") || c.get("placements_require_layer0_alternate").getAsBoolean(),
            wall[0], wall[1], points[0], points[1], offset[0], offset[1],
            (int) number(c, "min_gen_offset", -16), (int) number(c, "max_gen_offset", 16), number(c, "noise_multiplier", 0.05),
            c.get("invalid_blocks_threshold").getAsInt(), rarity, minY, maxY);
    }

    private static double number(JsonObject o, String key, double fallback) {
        return o.has(key) ? o.get(key).getAsDouble() : fallback;
    }

    private static int[] uniform(JsonObject o, String key, int min, int max) {
        if (!o.has(key)) {
            return new int[] {min, max};
        }
        final JsonObject u = o.getAsJsonObject(key);
        if (!u.get("type").getAsString().equals("minecraft:uniform")) {
            throw new IllegalArgumentException("int provider not ported: " + u);
        }
        return new int[] {u.get("min_inclusive").getAsInt(), u.get("max_inclusive").getAsInt()};
    }

    private static String provider(JsonObject blocks, String key) {
        final JsonObject p = blocks.getAsJsonObject(key);
        if (!p.get("type").getAsString().equals("minecraft:simple_state_provider")) {
            throw new IllegalArgumentException("block state provider not ported: " + p);
        }
        return state(p.get("state"));
    }

    /** A block state's name in our namespace ({@code tfc:} becomes {@code tfp:}). */
    private static String state(JsonElement state) {
        final String name = state.getAsJsonObject().get("Name").getAsString();
        return name.startsWith("tfc:") ? "tfp:" + name.substring(4) : name;
    }
}
