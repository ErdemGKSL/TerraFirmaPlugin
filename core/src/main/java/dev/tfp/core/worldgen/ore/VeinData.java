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
 * {@code data/tfp/worldgen/configured_feature/vein/} and the order of the vein tag
 * {@code data/tfp/tags/worldgen/placed_feature/in_biome/veins.json}. Pure; throws {@link IllegalArgumentException} on data
 * that does not follow TFC's codecs.
 */
public final class VeinData {
    /** The configured feature folder of the veins, below a data root. */
    public static final String FEATURE_DIR = "tfp/worldgen/configured_feature/vein/";
    /** The vein tag of every overworld biome, below a data root. */
    public static final String VEIN_TAG = "tfp/tags/worldgen/placed_feature/in_biome/veins.json";

    private static final String CLUSTER = "tfp:cluster_vein";
    /** Vein types that exist in TFC but are not ported yet (disc and pipe veins): read as absent. */
    private static final Set<String> NOT_PORTED = Set.of("tfp:disc_vein", "tfp:kaolin_disc_vein", "tfp:pipe_vein");
    private static final Set<String> CLUSTER_KEYS = Set.of("blocks", "indicator", "rarity", "density", "min_y", "max_y",
        "project", "project_offset", "random_name", "near_lava", "size");

    private VeinData() {}

    /**
     * The vein names of the tag, in order: {@code tfp:vein/<name>} entries give {@code <name>}; other features of the tag (the
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
     * A configured vein feature: the cluster vein's configuration, or empty for the vein types that are not ported yet.
     *
     * @param name the vein name (the file name without {@code .json})
     */
    public static Optional<ClusterVeinConfig> parse(String name, JsonObject root) {
        final String type = string(root, "type");
        if (NOT_PORTED.contains(type)) {
            return Optional.empty();
        }
        if (!type.equals(CLUSTER)) {
            throw new IllegalArgumentException(name + ": unknown vein type " + type);
        }
        final JsonObject c = object(root, "config");
        for (String key : c.keySet()) {
            if (!CLUSTER_KEYS.contains(key)) {
                throw new IllegalArgumentException(name + ": unknown key " + key);
            }
        }
        if (bool(c, "project") || bool(c, "project_offset") || bool(c, "near_lava")) {
            throw new IllegalArgumentException(name + ": projected or near-lava cluster veins are not supported");
        }
        final int rarity = c.get("rarity").getAsInt();
        final float density = c.get("density").getAsFloat();
        if (rarity <= 0 || density < 0 || density > 1) {
            throw new IllegalArgumentException(name + ": rarity must be positive and density in [0, 1]");
        }
        final int size = c.get("size").getAsInt();
        if (size <= 0) {
            throw new IllegalArgumentException(name + ": size must be positive");
        }
        final JsonPrimitive randomName = c.getAsJsonPrimitive("random_name");
        final long seed = randomName.isString() ? ClusterVeinConfig.hash(randomName.getAsString()) : randomName.getAsLong();
        return Optional.of(new ClusterVeinConfig(name, rarity, density, c.get("min_y").getAsInt(), c.get("max_y").getAsInt(),
            size, seed, blocks(name, array(c, "blocks")), c.has("indicator") ? indicator(name, object(c, "indicator")) : null));
    }

    private static final Set<String> INDICATOR_KEYS = Set.of("depth", "rarity", "underground_rarity", "underground_count", "blocks");

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
