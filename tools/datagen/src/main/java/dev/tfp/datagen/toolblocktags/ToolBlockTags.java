/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/generated/resources/data/tfc/tags/block/mineable/*.json @ c3b4d9536 (members resolved to vanilla blocks)
 */
package dev.tfp.datagen.toolblocktags;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.tfp.core.registry.DataLoader;
import dev.tfp.datagen.DatagenException;
import dev.tfp.datagen.Json;
import dev.tfp.datagen.OutputTree;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * PAR-PROC-24: our {@code tfp:mineable/*} block tags as real server tags, so a CE item whose vanilla {@code tool}
 * component names one ({@code rules[].blocks: "#tfp:mineable/glass_saw"}: gem saw, knives, hammers, scythes) can be
 * built. Without the server tag the component's codec throws "Missing tag" and CE fails its whole recipe load.
 *
 * <p>The tags go into the bootstrap datapack {@code tfp_seasonal_biomes} (the one generated datapack {@code TfpBootstrap}
 * registers), at {@code data/tfp/tags/block/mineable/<tool>.json}. Their source is the renamed TFC tag that
 * {@code DataExport} wrote to {@code data/tfp/tags/block/mineable/} (the plugin's own tag runtime). A server tag can
 * only name blocks of the server's block registry, so each entry is resolved:
 * <ul>
 *   <li>{@code #minecraft:x}: kept as a tag reference (vanilla has it);</li>
 *   <li>{@code #c:x}: expanded to its vanilla members (Paper ships no {@code c:} tags): NeoForge's base layer
 *       ({@code data/neoforge_common_tags.json}) plus TFC's additions ({@code data/c/tags/block/x.json}, which replace
 *       the base when they say {@code "replace": true}), recursively;</li>
 *   <li>{@code #tfp:x}: expanded the same way from our own exported tag;</li>
 *   <li>{@code minecraft:x}: kept;</li>
 *   <li>any other id ({@code tfp:} blocks are CE blocks, which the registry holds as {@code craftengine:custom_N}): left
 *       out and listed in {@link #REPORT} (ledger divergence: CE blocks get the tool's speed through their own
 *       {@code settings.tags}, not through this tag).</li>
 * </ul>
 * Every entry is written as {@code {"id": ..., "required": false}}, so a block or tag missing on some supported version
 * drops only that entry instead of the whole tag (a missing tag would bring the CE failure back). Must run after
 * {@code DataExport} and {@code CommonTags}, and before {@code CraftingRecipes} ({@link #isServerTag}).
 */
public final class ToolBlockTags {
    /** The bootstrap datapack ({@code SeasonalBiomes.OUT}, {@code TfpBootstrap.SEASONAL_DATAPACK_RESOURCE}). */
    public static final String DATAPACK = "datapack/tfp_seasonal_biomes/";
    public static final String REPORT = "reports/tool-block-tags.json";
    static final String SOURCE = "data/" + Json.TO_NS + "/tags/block/mineable/";
    private static final String COMMON = "data/" + DataLoader.COMMON_TAGS_FILE;

    private ToolBlockTags() {
    }

    /** One shipped tag: its server entries and the ids left out. */
    public record Shipped(String tag, List<String> entries, Set<String> dropped) {
    }

    /** The datapack path of block tag {@code ns:path}. */
    public static String serverTagPath(String tag) {
        int colon = tag.indexOf(':');
        return DATAPACK + "data/" + tag.substring(0, colon) + "/tags/block/" + tag.substring(colon + 1) + ".json";
    }

    /** Whether block tag {@code ns:path} is a vanilla tag or one this step ships as a server tag. */
    public static boolean isServerTag(OutputTree out, String tag) {
        return tag.startsWith("minecraft:") || out.contains(serverTagPath(tag));
    }

    /** Writes every {@code tfp:mineable/*} block tag of the tree into the bootstrap datapack, plus {@link #REPORT}. */
    public static List<Shipped> run(OutputTree out) {
        Resolver r = new Resolver(out);
        List<Shipped> shipped = new ArrayList<>();
        for (String path : out.files().keySet()) {
            if (!path.startsWith(SOURCE) || !path.endsWith(".json")) {
                continue;
            }
            String tag = Json.TO_NS + ":mineable/" + path.substring(SOURCE.length(), path.length() - ".json".length());
            Set<String> entries = new LinkedHashSet<>();
            Set<String> dropped = new LinkedHashSet<>();
            r.resolve("#" + tag, entries, dropped, new LinkedHashSet<>());
            JsonArray values = new JsonArray();
            for (String e : entries) {
                JsonObject o = new JsonObject();
                o.addProperty("id", e);
                o.addProperty("required", false);
                values.add(o);
            }
            JsonObject file = new JsonObject();
            file.add("values", values);
            out.put(serverTagPath(tag), Json.write(file));
            shipped.add(new Shipped(tag, List.copyOf(entries), Set.copyOf(dropped)));
        }
        JsonObject report = new JsonObject();
        for (Shipped s : shipped) {
            JsonObject o = new JsonObject();
            JsonArray e = new JsonArray();
            s.entries().forEach(e::add);
            JsonArray d = new JsonArray();
            s.dropped().stream().sorted().forEach(d::add);
            o.add("server_entries", e);
            o.add("left_out", d);
            report.add(s.tag(), o);
        }
        out.put(REPORT, Json.write(report));
        return shipped;
    }

    /** Expands tag entries to what a server tag can hold. */
    private static final class Resolver {
        private final OutputTree out;
        private final Map<String, List<String>> common = new TreeMap<>();

        Resolver(OutputTree out) {
            this.out = out;
            byte[] bytes = out.get(COMMON);
            if (bytes != null) {
                JsonObject block = Json.parse(new String(bytes, StandardCharsets.UTF_8), COMMON).getAsJsonObject()
                    .getAsJsonObject("block");
                if (block != null) {
                    block.entrySet().forEach(en -> common.put(en.getKey(), ids(en.getValue().getAsJsonArray())));
                }
            }
        }

        void resolve(String entry, Set<String> entries, Set<String> dropped, Set<String> visiting) {
            if (!entry.startsWith("#")) {
                if (entry.startsWith("minecraft:")) {
                    entries.add(entry);
                } else {
                    dropped.add(entry);
                }
                return;
            }
            String tag = entry.substring(1);
            if (tag.startsWith("minecraft:")) {
                entries.add(entry);
                return;
            }
            if (!visiting.add(tag)) {
                throw new DatagenException("block tag cycle through #" + tag + ": " + visiting);
            }
            List<String> members = members(tag);
            if (members == null) {
                dropped.add(entry);
            } else {
                members.forEach(m -> resolve(m, entries, dropped, visiting));
            }
            visiting.remove(tag);
        }

        /** The members of a {@code c:} or {@code tfp:} block tag, or null when no layer defines it. */
        private List<String> members(String tag) {
            int colon = tag.indexOf(':');
            String ns = tag.substring(0, colon);
            byte[] own = out.get("data/" + ns + "/tags/block/" + tag.substring(colon + 1) + ".json");
            List<String> base = ns.equals("c") ? common.get(tag) : null;
            if (own == null) {
                return base;
            }
            JsonObject file = Json.parse(new String(own, StandardCharsets.UTF_8), tag).getAsJsonObject();
            boolean replace = file.has("replace") && file.get("replace").getAsBoolean();
            List<String> all = new ArrayList<>();
            if (base != null && !replace) {
                all.addAll(base);
            }
            all.addAll(ids(file.getAsJsonArray("values")));
            return all;
        }

        private static List<String> ids(JsonArray values) {
            List<String> ids = new ArrayList<>();
            for (JsonElement v : values) {
                ids.add(v.isJsonObject() ? v.getAsJsonObject().get("id").getAsString() : v.getAsString());
            }
            return ids;
        }
    }
}
