/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/resources/data/tfc/loot_table/blocks/{firepit,grill,pot,charcoal_forge,bellows}.json @ c3b4d9536
 *       (the pool shape: rolls 1, survives_explosion, one item entry, optional set_count)
 */
package dev.tfp.datagen.device;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.tfp.datagen.DatagenException;
import dev.tfp.datagen.Json;
import dev.tfp.datagen.TfcCheckout;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What a device block drops (PAR-INFRA-08), in the shape of TFC's device loot tables: one or more pools, each rolled once
 * under {@code survives_explosion}, each dropping one item (the block itself or another item) in an optional constant
 * count. TFC's device tables differ per device: the firepit drops wood ash, the grill and the pot wood ash plus their
 * grill or ceramic pot, the charcoal forge 7 charcoal (it has no item of its own), the bellows itself.
 *
 * <p>Written with the {@code tfp:loot_table/*} templates where one fits exactly (self, one item, one item with a count),
 * otherwise as literal pools of the same shape.
 *
 * @param pools the pools in TFC's order, at least one
 */
public record DeviceLoot(List<Pool> pools) {
    static final String LOOT_SELF = "tfp:loot_table/self";
    static final String LOOT_ITEM = "tfp:loot_table/item";
    static final String LOOT_ITEM_COUNT = "tfp:loot_table/item_count";

    /**
     * One pool: rolls 1, {@code survives_explosion}, one item entry.
     *
     * @param item  the dropped item id, or null for the block itself
     * @param count the constant {@code set_count}, or null for one
     */
    public record Pool(String item, Integer count) {
        public Pool {
            if (item != null && !item.matches("[a-z0-9_.\\-]+:[a-z0-9_./\\-]+")) {
                throw new DatagenException("device loot item " + item + " is not a namespaced id");
            }
            if (count != null && count < 1) {
                throw new DatagenException("device loot count " + count + " is below 1");
            }
        }
    }

    public DeviceLoot {
        pools = List.copyOf(pools);
        if (pools.isEmpty()) {
            throw new DatagenException("device loot has no pool");
        }
    }

    /** The block drops itself. */
    public static DeviceLoot self() {
        return new DeviceLoot(List.of(new Pool(null, null)));
    }

    /** The block drops one other item. */
    public static DeviceLoot item(String item) {
        return new DeviceLoot(List.of(new Pool(item, null)));
    }

    /** The block drops {@code count} of another item (TFC: {@code set_count} with a constant). */
    public static DeviceLoot item(String item, int count) {
        return new DeviceLoot(List.of(new Pool(item, count)));
    }

    /** One pool per item, each dropping one (the grill: wood ash and the wrought iron grill). */
    public static DeviceLoot items(String... items) {
        List<Pool> pools = new ArrayList<>();
        for (String i : items) {
            pools.add(new Pool(i, null));
        }
        return new DeviceLoot(pools);
    }

    /**
     * Reads a TFC block loot table ({@code data/tfc/loot_table/blocks/<block>.json}, already renamed to {@code tfp:}).
     * Only the device shape is accepted (see the class comment); anything else (e.g. the crucible's
     * {@code copy_components}) fails, so nothing is dropped silently.
     *
     * @param table  the renamed table
     * @param selfId the block id, {@code tfp:...}: an entry naming it becomes a self drop
     * @param source the path, for errors
     */
    public static DeviceLoot fromTfc(JsonElement table, String selfId, String source) {
        JsonObject root = object(table, source);
        keys(root, Set.of("type", "pools"), source);
        if (!"minecraft:block".equals(string(root, "type", source))) {
            throw new DatagenException(source + ": not a block loot table");
        }
        List<Pool> pools = new ArrayList<>();
        for (JsonElement p : array(root, "pools", source)) {
            JsonObject pool = object(p, source);
            keys(pool, Set.of("name", "rolls", "entries", "conditions"), source);
            if (!pool.has("rolls") || !pool.get("rolls").isJsonPrimitive() || pool.get("rolls").getAsDouble() != 1) {
                throw new DatagenException(source + ": a pool does not roll exactly once");
            }
            JsonArray conditions = array(pool, "conditions", source);
            if (conditions.size() != 1 || !"minecraft:survives_explosion".equals(string(object(conditions.get(0), source), "condition", source))
                    || object(conditions.get(0), source).size() != 1) {
                throw new DatagenException(source + ": a pool's conditions are not exactly survives_explosion");
            }
            JsonArray entries = array(pool, "entries", source);
            if (entries.size() != 1) {
                throw new DatagenException(source + ": a pool has " + entries.size() + " entries, not one");
            }
            JsonObject entry = object(entries.get(0), source);
            keys(entry, Set.of("type", "name", "functions"), source);
            if (!"minecraft:item".equals(string(entry, "type", source))) {
                throw new DatagenException(source + ": an entry is not an item entry");
            }
            String item = string(entry, "name", source);
            Integer count = null;
            if (entry.has("functions")) {
                JsonArray functions = array(entry, "functions", source);
                JsonObject f = functions.size() == 1 ? object(functions.get(0), source) : null;
                if (f == null || f.size() != 2 || !"minecraft:set_count".equals(string(f, "function", source))
                        || !f.get("count").isJsonPrimitive() || !f.get("count").getAsJsonPrimitive().isNumber()) {
                    throw new DatagenException(source + ": an entry function is not one constant set_count");
                }
                count = f.get("count").getAsInt();
            }
            pools.add(new Pool(item.equals(selfId) ? null : item, count));
        }
        return new DeviceLoot(pools);
    }

    /** Reads {@code data/tfc/loot_table/blocks/<block>.json} through {@link #fromTfc(JsonElement, String, String)}. */
    public static DeviceLoot fromTfc(TfcCheckout tfc, String block) {
        String src = TfcCheckout.MAIN + "data/" + Json.FROM_NS + "/loot_table/blocks/" + block + ".json";
        if (!tfc.exists(src)) {
            throw new DatagenException("no TFC loot table for " + block + " (" + src + ")");
        }
        return fromTfc(Json.rename(Json.parse(tfc.readString(src), src)), Json.TO_NS + ":" + block, src);
    }

    /** The CraftEngine {@code loot} value of block {@code blockId}. */
    Map<String, Object> yaml(String blockId) {
        if (pools.size() == 1) {
            Pool p = pools.getFirst();
            if (p.item() == null && p.count() == null) {
                return Map.of("template", LOOT_SELF);
            }
            if (p.item() != null) {
                Map<String, Object> args = new LinkedHashMap<>();
                args.put("loot_item", p.item());
                if (p.count() == null) {
                    return call(LOOT_ITEM, args);
                }
                args.put("loot_count_min", p.count());
                args.put("loot_count_max", p.count());
                return call(LOOT_ITEM_COUNT, args);
            }
        }
        List<Object> out = new ArrayList<>();
        for (Pool p : pools) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("type", "item");
            entry.put("item", p.item() == null ? blockId : p.item());
            if (p.count() != null) {
                Map<String, Object> f = new LinkedHashMap<>();
                f.put("type", "set_count");
                f.put("count", p.count());
                entry.put("functions", List.of(f));
            }
            Map<String, Object> pool = new LinkedHashMap<>();
            pool.put("rolls", 1);
            pool.put("conditions", List.of(Map.of("type", "survives_explosion")));
            pool.put("entries", List.of(entry));
            out.add(pool);
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("pools", out);
        return m;
    }

    private static Map<String, Object> call(String template, Map<String, Object> args) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("template", template);
        m.put("arguments", args);
        return m;
    }

    private static JsonObject object(JsonElement e, String source) {
        if (e == null || !e.isJsonObject()) {
            throw new DatagenException(source + ": expected an object");
        }
        return e.getAsJsonObject();
    }

    private static JsonArray array(JsonObject o, String key, String source) {
        if (!o.has(key) || !o.get(key).isJsonArray()) {
            throw new DatagenException(source + ": expected the array " + key);
        }
        return o.getAsJsonArray(key);
    }

    private static String string(JsonObject o, String key, String source) {
        if (!o.has(key) || !o.get(key).isJsonPrimitive()) {
            throw new DatagenException(source + ": expected the string " + key);
        }
        return o.get(key).getAsString();
    }

    private static void keys(JsonObject o, Set<String> allowed, String source) {
        for (String k : o.keySet()) {
            if (!allowed.contains(k)) {
                throw new DatagenException(source + ": unexpected key " + k + " (not a device loot shape)");
            }
        }
    }
}
