/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/resources/data/tfc/loot_table/blocks/** @ c3b4d9536 (the block tables, copied with ids renamed)
 *       src/generated/resources/data/tfc/loot_table/entities/** @ c3b4d9536 (the entity tables, same)
 */
package dev.tfp.datagen;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.tfp.core.animal.CarrierTable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * PAR-INFRA-21: ships TFC's loot tables for the Paper loot runtime ({@code paper/loot}), which loads every
 * {@code data/<ns>/loot_table/**.json} of the jar and drops a CraftEngine block's loot itself when its table is there.
 *
 * <p>Blocks: a generator calls {@link #delegateBlock} for a block whose TFC table has no {@code tfp:loot_table/*}
 * template shape ({@link LootShapes}); that block's CraftEngine entry then carries no {@code loot:}, so the runtime is its
 * only drop source (no double drops). Entities: the table of every TFC living entity the plugin spawns
 * ({@link CarrierTable}). Ids are renamed {@code tfc:} → {@code tfp:} as all other data (item and block names, tags, the
 * TFC condition, function and number types, which {@code LootTableParser} reads as TFC's).
 *
 * <p>{@link #run} (after every block generator) writes the entity tables and checks the whole exported set.
 */
public final class LootTableExport {
    static final String BLOCK_SOURCE = TfcCheckout.MAIN + "data/" + Json.FROM_NS + "/loot_table/blocks/";
    static final String ENTITY_SOURCE = TfcCheckout.GENERATED + "data/" + Json.FROM_NS + "/loot_table/entities/";
    static final String TARGET = "data/" + Json.TO_NS + "/loot_table/";
    /** TFC's own condition, function and number provider types (the {@code tfc:} ones {@code LootTableParser} reads). */
    static final Set<String> TFC_TYPES = Set.of("is_isolated", "is_burnt_out", "is_sluice", "is_male", "not_predated",
        "always_true", "copy_fluid", "rotten", "apply_stack_size", "crop_yield_uniform", "animal_yield");
    private static final Pattern LEFTOVER = Pattern.compile("(?<![A-Za-z0-9_.\\-/])" + Json.FROM_NS + ":");
    private static final Set<String> TYPE_KEYS = Set.of("type", "condition", "function");

    private LootTableExport() {
    }

    /** The renamed TFC table of block {@code id} (tfp path), or null when TFC has none. */
    static JsonElement blockTable(TfcCheckout tfc, String id) {
        String src = BLOCK_SOURCE + id + ".json";
        return tfc.exists(src) ? Json.rename(Json.parse(tfc.readString(src), src)) : null;
    }

    /**
     * Ships {@code table} (TFC's table of block {@code id}, renamed) for the Paper runtime and reports every item it
     * names to {@code itemNamed} (so the caller can make sure those items exist). The caller writes no CraftEngine
     * {@code loot:} for the block.
     */
    public static void delegateBlock(OutputTree out, String id, JsonElement table, Consumer<String> itemNamed) {
        out.put(TARGET + "blocks/" + id + ".json", Json.write(table));
        items(table, itemNamed);
    }

    /** Writes the entity tables and checks every exported table. Returns {@code {blocks, entities}}. */
    public static int[] run(TfcCheckout tfc, OutputTree out) {
        int entities = 0;
        for (String type : entityTypes()) {
            String src = ENTITY_SOURCE + type + ".json";
            if (tfc.exists(src)) {
                out.put(TARGET + "entities/" + type + ".json", Json.write(Json.rename(Json.parse(tfc.readString(src), src))));
                entities++;
            }
        }
        int blocks = 0;
        Map<String, byte[]> files = out.files();
        for (Map.Entry<String, byte[]> e : files.entrySet()) {
            if (!e.getKey().startsWith(TARGET)) {
                continue;
            }
            String rel = e.getKey().substring(TARGET.length());
            JsonElement table = Json.parse(new String(e.getValue(), StandardCharsets.UTF_8), e.getKey());
            check(e.getKey(), table);
            if (rel.startsWith("blocks/")) {
                String id = rel.substring("blocks/".length(), rel.length() - ".json".length());
                requireNoCeLoot(files, Json.TO_NS + ":" + id);
                blocks++;
            }
        }
        return new int[] {blocks, entities};
    }

    /** The TFC entity types (TFC paths, e.g. {@code cow}) whose loot ships: every living entity the plugin spawns. */
    static Set<String> entityTypes() {
        Set<String> out = new TreeSet<>();
        for (String id : CarrierTable.get().ids()) {
            if (id.startsWith(Json.FROM_NS + ":")) {
                out.add(id.substring(Json.FROM_NS.length() + 1));
            }
        }
        return out;
    }

    /**
     * An exported table: a block or entity table (pools a list, if any), no {@code tfc:} id left, and every {@code tfp:} type one of
     * TFC's own (the runtime maps {@code tfp:x} to {@code tfc:x}; any other {@code tfp:} type would be a rename slip).
     */
    static void check(String path, JsonElement table) {
        String text = Json.write(table);
        if (LEFTOVER.matcher(text).find()) {
            throw new DatagenException("loot table " + path + " still names a " + Json.FROM_NS + ": id");
        }
        JsonObject o = table.getAsJsonObject();
        String type = o.has("type") ? o.get("type").getAsString() : "";
        if (!type.equals("minecraft:block") && !type.equals("minecraft:entity")) {
            throw new DatagenException("loot table " + path + " has type " + type + ", expected minecraft:block or entity");
        }
        if (o.has("pools") && !o.get("pools").isJsonArray()) { // no pools is an empty table (TFC's jellyfish)
            throw new DatagenException("loot table " + path + " has pools that are not a list");
        }
        List<String> unknown = new ArrayList<>();
        types(table, t -> {
            if (t.startsWith(Json.TO_NS + ":") && !TFC_TYPES.contains(t.substring(Json.TO_NS.length() + 1))) {
                unknown.add(t);
            }
        });
        if (!unknown.isEmpty()) {
            throw new DatagenException("loot table " + path + " uses unknown types " + unknown);
        }
    }

    /** Every {@code type}/{@code condition}/{@code function} string value in the table, depth first. */
    static void types(JsonElement e, Consumer<String> sink) {
        if (e.isJsonObject()) {
            for (Map.Entry<String, JsonElement> en : e.getAsJsonObject().entrySet()) {
                JsonElement v = en.getValue();
                if (TYPE_KEYS.contains(en.getKey()) && v.isJsonPrimitive() && v.getAsJsonPrimitive().isString()) {
                    sink.accept(v.getAsString());
                } else {
                    types(v, sink);
                }
            }
        } else if (e.isJsonArray()) {
            for (JsonElement c : e.getAsJsonArray()) {
                types(c, sink);
            }
        }
    }

    /** The {@code name} of every {@code minecraft:item} entry, depth first. */
    static void items(JsonElement e, Consumer<String> sink) {
        if (e.isJsonObject()) {
            JsonObject o = e.getAsJsonObject();
            if (o.has("type") && "minecraft:item".equals(o.get("type").getAsString()) && o.has("name")) {
                sink.accept(o.get("name").getAsString());
            }
            for (Map.Entry<String, JsonElement> en : o.entrySet()) {
                items(en.getValue(), sink);
            }
        } else if (e.isJsonArray()) {
            for (JsonElement c : e.getAsJsonArray()) {
                items(c, sink);
            }
        }
    }

    /**
     * The block's CraftEngine entry (in a loaded {@code configuration/} file) must carry no {@code loot:}: the runtime and
     * CraftEngine would both drop. The generated YAML is block style with 2-space indent ({@link Yaml}), so the entry is
     * the lines below {@code "  <id>:"} that are indented deeper.
     */
    static void requireNoCeLoot(Map<String, byte[]> files, String blockId) {
        String head = "  " + blockId + ":";
        for (Map.Entry<String, byte[]> e : files.entrySet()) {
            if (!e.getKey().startsWith(AssetPipeline.CONFIG) || !e.getKey().endsWith(".yml")) {
                continue;
            }
            String[] lines = new String(e.getValue(), StandardCharsets.UTF_8).split("\n");
            boolean inBlocks = false;
            for (int i = 0; i < lines.length; i++) {
                if (!lines[i].startsWith(" ") && !lines[i].startsWith("#")) {
                    inBlocks = lines[i].equals("blocks:");
                }
                if (!inBlocks || !lines[i].equals(head)) {
                    continue;
                }
                for (int j = i + 1; j < lines.length && lines[j].startsWith("    "); j++) {
                    if (lines[j].equals("    loot:") || lines[j].startsWith("    loot: ")) {
                        throw new DatagenException(blockId + " ships a runtime loot table and has a CraftEngine loot: in "
                            + e.getKey() + " (it would drop twice)");
                    }
                }
            }
        }
    }
}
