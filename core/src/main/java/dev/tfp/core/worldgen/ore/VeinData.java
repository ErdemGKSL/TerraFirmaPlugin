/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/feature/vein/VeinConfig.java @ c3b4d9536 (CODEC), with ClusterVeinConfig.java
 * (CODEC), Indicator.java (CODEC) and src/main/java/net/dries007/tfc/world/Codecs.java (BLOCK_TO_WEIGHTED_BLOCKSTATE, weightedCodec).
 */
package dev.tfp.core.worldgen.ore;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Reads the vein data that datagen exports from TFC: the configured features under
 * {@code data/tfp/worldgen/configured_feature/vein/}, the order of the vein tag
 * {@code data/tfp/tags/worldgen/placed_feature/in_biome/veins.json} and of the biome vein tags below {@code in_biome/veins/}. Pure; throws {@link IllegalArgumentException} on data
 * that does not follow TFC's codecs.
 */
public final class VeinData {
    /** The configured feature folder of the veins, below a data root. */
    public static final String FEATURE_DIR = "tfp/worldgen/configured_feature/vein/";
    /** The vein tag of every overworld biome, below a data root. */
    public static final String VEIN_TAG = "tfp/tags/worldgen/placed_feature/in_biome/veins.json";

    /** The biome vein tags, below a data root: the key of {@link OreVeins}' biome veins, then {@code .json}. */
    public static final String BIOME_VEIN_TAG_DIR = "tfp/tags/worldgen/placed_feature/in_biome/veins/";
    /** The biome vein tags whose types replace rock, in the order a chunk runs them. The kaolin tag is {@link KaolinDisc}'s. */
    public static final List<String> BIOME_VEIN_TAGS = List.of("montane", "collisional_mountains", "river");
    /** The biome vein tag of the kaolin disc. */
    public static final String KAOLIN_TAG = "kaolin";

    private static final String CLUSTER = "tfp:cluster_vein";
    private static final String DISC = "tfp:disc_vein";
    private static final String KAOLIN_DISC = "tfp:kaolin_disc_vein";
    private static final String PIPE = "tfp:pipe_vein";
    private static final Set<String> COMMON_KEYS = Set.of("blocks", "indicator", "rarity", "density", "min_y", "max_y",
        "project", "project_offset", "random_name", "near_lava");
    private static final Set<String> CLUSTER_KEYS = Set.of("size");
    private static final Set<String> DISC_KEYS = Set.of("size", "height");
    private static final Set<String> PIPE_KEYS = Set.of("height", "radius", "min_skew", "max_skew", "min_slant", "max_slant", "sign");

    private VeinData() {}

    /**
     * The vein names of a tag, in order: {@code tfp:vein/<name>} entries give {@code <name>}; other features of the tag (the
     * geode) are left out.
     */
    public static List<String> tagOrder(JsonObject tag) {
        final List<String> out = new ArrayList<>();
        for (JsonElement e : array(tag, "values")) {
            final String id = e.getAsString();
            if (id.startsWith("tfp:vein/")) {
                out.add(id.substring("tfp:vein/".length()));
            }
        }
        return out;
    }

    /**
     * A configured vein feature as a cluster vein's configuration, or empty for the other vein types (see {@link #parseAny}).
     *
     * @param name the vein name (the file name without {@code .json})
     */
    public static Optional<ClusterVeinConfig> parse(String name, JsonObject root) {
        return parseAny(name, root) instanceof ClusterVeinConfig c ? Optional.of(c) : Optional.empty();
    }

    /**
     * A configured vein feature of any type: {@code cluster_vein}, {@code disc_vein}, {@code kaolin_disc_vein} or
     * {@code pipe_vein}.
     *
     * @param name the vein name (the file name without {@code .json})
     */
    public static VeinConfig parseAny(String name, JsonObject root) {
        final String type = string(root, "type");
        final Set<String> keys = switch (type) {
            case CLUSTER -> CLUSTER_KEYS;
            case DISC, KAOLIN_DISC -> DISC_KEYS;
            case PIPE -> PIPE_KEYS;
            default -> throw new IllegalArgumentException(name + ": unknown vein type " + type);
        };
        final JsonObject c = object(root, "config");
        for (String key : c.keySet()) {
            if (!COMMON_KEYS.contains(key) && !keys.contains(key)) {
                throw new IllegalArgumentException(name + ": unknown key " + key);
            }
        }
        for (String key : keys) {
            if (!c.has(key)) {
                throw new IllegalArgumentException(name + ": missing key " + key);
            }
        }
        final boolean project = bool(c, "project");
        final boolean projectOffset = bool(c, "project_offset");
        final boolean nearLava = bool(c, "near_lava");
        final int rarity = c.get("rarity").getAsInt();
        final float density = c.get("density").getAsFloat();
        if (rarity <= 0 || density < 0 || density > 1) {
            throw new IllegalArgumentException(name + ": rarity must be positive and density in [0, 1]");
        }
        final int minY = c.get("min_y").getAsInt();
        final int maxY = c.get("max_y").getAsInt();
        final JsonPrimitive randomName = c.getAsJsonPrimitive("random_name");
        final long seed = randomName.isString() ? ClusterVeinConfig.hash(randomName.getAsString()) : randomName.getAsLong();
        final Map<String, Weighted<VeinBlock>> blocks = blocks(name, array(c, "blocks"));
        final Indicator indicator = c.has("indicator") ? indicator(name, object(c, "indicator")) : null;
        switch (type) {
            case CLUSTER -> {
                if (project || projectOffset || nearLava) {
                    throw new IllegalArgumentException(name + ": projected or near-lava cluster veins are not supported");
                }
                final int size = c.get("size").getAsInt();
                if (size <= 0) {
                    throw new IllegalArgumentException(name + ": size must be positive");
                }
                return new ClusterVeinConfig(name, rarity, density, minY, maxY, size, seed, blocks, indicator);
            }
            case PIPE -> {
                final float sign = c.get("sign").getAsFloat();
                if (sign < 0 || sign > 1) {
                    throw new IllegalArgumentException(name + ": sign must be in [0, 1]");
                }
                return new PipeVeinConfig(name, rarity, density, minY, maxY, c.get("height").getAsInt(), c.get("radius").getAsInt(),
                    c.get("min_skew").getAsInt(), c.get("max_skew").getAsInt(), c.get("min_slant").getAsInt(),
                    c.get("max_slant").getAsInt(), sign, seed, blocks, indicator, project, projectOffset, nearLava);
            }
            default -> {
                return new DiscVeinConfig(name, rarity, density, minY, maxY, c.get("size").getAsInt(), c.get("height").getAsInt(),
                    seed, blocks, indicator, project, projectOffset, nearLava, type.equals(KAOLIN_DISC));
            }
        }
    }

    /** {@code spread} is in the kaolin disc's data but not in TFC's codec, which ignores it. */
    private static final Set<String> INDICATOR_KEYS = Set.of("depth", "rarity", "underground_rarity", "underground_count", "blocks",
        "spread");

    /** {@code Indicator.CODEC}: depth and underground rarity positive, the blocks a weighted list of {@code block} entries. */
    private static Indicator indicator(String name, JsonObject o) {
        for (String key : o.keySet()) {
            if (!INDICATOR_KEYS.contains(key)) {
                throw new IllegalArgumentException(name + ": unknown indicator key " + key);
            }
        }
        final List<String> elements = new ArrayList<>();
        final List<Double> weights = new ArrayList<>();
        for (JsonElement e : array(o, "blocks")) {
            final JsonObject entry = e.getAsJsonObject();
            elements.add(string(entry, "block"));
            weights.add(entry.has("weight") ? entry.get("weight").getAsDouble() : 1d);
        }
        try {
            return new Indicator(o.get("depth").getAsInt(), o.get("rarity").getAsInt(), o.get("underground_rarity").getAsInt(),
                o.get("underground_count").getAsInt(), Weighted.of(elements, weights));
        } catch (NullPointerException e) {
            throw new IllegalArgumentException(name + ": incomplete indicator", e);
        }
    }

    /** {@code BLOCK_TO_WEIGHTED_BLOCKSTATE}: each entry's {@code replace} blocks map to the weighted list of its {@code with}. */
    private static Map<String, Weighted<VeinBlock>> blocks(String name, JsonArray entries) {
        final Map<String, Weighted<VeinBlock>> out = new LinkedHashMap<>();
        for (JsonElement e : entries) {
            final JsonObject entry = e.getAsJsonObject();
            final List<VeinBlock> elements = new ArrayList<>();
            final List<Double> weights = new ArrayList<>();
            for (JsonElement w : array(entry, "with")) {
                final JsonObject with = w.getAsJsonObject();
                elements.add(VeinBlock.parse(string(with, "block"), name));
                weights.add(with.has("weight") ? with.get("weight").getAsDouble() : 1d);
            }
            final Weighted<VeinBlock> weighted = Weighted.of(elements, weights);
            for (JsonElement r : array(entry, "replace")) {
                out.put(r.getAsString(), weighted);
            }
        }
        return out;
    }

    private static boolean bool(JsonObject o, String key) {
        return o.has(key) && o.get(key).getAsBoolean();
    }

    private static String string(JsonObject o, String key) {
        if (!o.has(key)) {
            throw new IllegalArgumentException("missing key " + key);
        }
        return o.get(key).getAsString();
    }

    private static JsonObject object(JsonObject o, String key) {
        if (!o.has(key) || !o.get(key).isJsonObject()) {
            throw new IllegalArgumentException("missing object " + key);
        }
        return o.getAsJsonObject(key);
    }

    private static JsonArray array(JsonObject o, String key) {
        if (!o.has(key) || !o.get(key).isJsonArray()) {
            throw new IllegalArgumentException("missing array " + key);
        }
        return o.getAsJsonArray(key);
    }
}
