/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.oreoverlay;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.tfp.core.oreoverlay.census.ExposureRule;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * The datagen runtime data of the ore overlays (PAR-INFRA-15, {@code content/visuals/ore_overlay/}): which CraftEngine
 * block is an ore and which overlay item it shows ({@code ores.json}), and which of our blocks occlude a full face
 * ({@code occluding.json}). Kinds are indices into {@link #overlays()} (sorted overlay ids). Immutable.
 */
public final class OreOverlayData {
    public static final String ORES = "content/visuals/ore_overlay/ores.json";
    public static final String OCCLUDING = "content/visuals/ore_overlay/occluding.json";
    /** Crack stages of the cracked overlay variants ({@code <overlay>/crack_<n>}, PAR-INFRA-17). */
    public static final int CRACK_STAGES = 10;

    private final List<String> overlays;
    private final Map<String, Integer> kindByBlock;
    private final Set<String> occluding;
    private final Set<String> nonOccluding;

    OreOverlayData(Map<String, String> overlayByBlock, Set<String> occluding, Set<String> nonOccluding) {
        this.overlays = List.copyOf(new TreeSet<>(overlayByBlock.values()));
        Map<String, Integer> kinds = new HashMap<>();
        overlayByBlock.forEach((block, overlay) -> kinds.put(block, overlays.indexOf(overlay)));
        this.kindByBlock = Map.copyOf(kinds);
        this.occluding = Set.copyOf(occluding);
        this.nonOccluding = Set.copyOf(nonOccluding);
    }

    /** No ores (the content jar lacks the files). */
    public static OreOverlayData empty() {
        return new OreOverlayData(Map.of(), Set.of(), Set.of());
    }

    /** Reads both files from {@code loader} (the plugin's class loader, which holds the content jar). */
    public static OreOverlayData load(ClassLoader loader) throws IOException {
        try (InputStream ores = loader.getResourceAsStream(ORES);
                InputStream occ = loader.getResourceAsStream(OCCLUDING)) {
            if (ores == null || occ == null) {
                throw new IOException("missing " + (ores == null ? ORES : OCCLUDING) + " in the plugin jar");
            }
            return read(new InputStreamReader(ores, StandardCharsets.UTF_8), new InputStreamReader(occ, StandardCharsets.UTF_8));
        }
    }

    /** Parses {@code ores.json} and {@code occluding.json} (format 1). */
    public static OreOverlayData read(Reader ores, Reader occluding) throws IOException {
        try {
            JsonObject o = JsonParser.parseReader(ores).getAsJsonObject();
            JsonObject c = JsonParser.parseReader(occluding).getAsJsonObject();
            if (o.get("format").getAsInt() != 1 || c.get("format").getAsInt() != 1) {
                throw new IOException("unsupported ore_overlay data format");
            }
            Map<String, String> byBlock = new HashMap<>();
            for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("ores").entrySet()) {
                byBlock.put(e.getKey(), e.getValue().getAsJsonObject().get("overlay").getAsString());
            }
            return new OreOverlayData(byBlock, strings(c, "occluding"), strings(c, "non_occluding"));
        } catch (RuntimeException e) {
            throw new IOException("malformed ore_overlay data: " + e, e);
        }
    }

    private static Set<String> strings(JsonObject o, String key) {
        Set<String> out = new HashSet<>();
        if (o.has(key)) {
            o.getAsJsonArray(key).forEach(e -> out.add(e.getAsString()));
        }
        return out;
    }

    /** The overlay item ids, sorted; a kind is an index into this list. */
    public List<String> overlays() {
        return overlays;
    }

    /** The number of ore blocks. */
    public int ores() {
        return kindByBlock.size();
    }

    /** The overlay kind of a CraftEngine block id, or -1 if it is no ore. */
    public int kind(String blockId) {
        Integer k = kindByBlock.get(blockId);
        return k == null ? -1 : k;
    }

    /**
     * The cell code of a CraftEngine block id ({@link dev.tfp.core.oreoverlay.CellLookup}): its ore kind, else
     * {@link ExposureRule#SOLID} for a block listed as occluding and {@link ExposureRule#OPEN} otherwise (non-occluding
     * blocks, and blocks of other CraftEngine packs, whose look we do not know: an overlay behind a full block is hidden by
     * it, a missing one would show plain rock).
     */
    public int code(String blockId) {
        Integer k = kindByBlock.get(blockId);
        if (k != null) {
            return k;
        }
        return occluding.contains(blockId) ? ExposureRule.SOLID : ExposureRule.OPEN;
    }

    /** True if the block id is listed as not occluding. */
    public boolean listedNonOccluding(String blockId) {
        return nonOccluding.contains(blockId);
    }

    /** The overlay item of a kind, at crack stage {@code stage} (-1: uncracked). */
    public String itemModel(int kind, int stage) {
        String base = overlays.get(kind);
        return stage < 0 ? base : base + "/crack_" + Math.min(CRACK_STAGES - 1, stage);
    }

    /** All block ids that are ores (for tests and stats). */
    public List<String> oreBlocks() {
        return new ArrayList<>(new TreeSet<>(kindByBlock.keySet()));
    }
}
