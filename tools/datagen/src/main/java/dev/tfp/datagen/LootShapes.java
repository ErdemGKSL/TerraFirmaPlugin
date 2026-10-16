/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * The TFC block loot table shapes (as written by TFC's mcresources, ids already renamed) that our
 * {@code tfp:loot_table/*} templates reproduce. A table is converted only if it equals one of these exactly.
 */
final class LootShapes {
    private LootShapes() {
    }

    private static final String POOL_OPEN = "{\"type\":\"minecraft:block\",\"pools\":[{\"name\":\"loot_pool\",\"rolls\":1,\"entries\":[";
    private static final String POOL_CLOSE = "],\"conditions\":[{\"condition\":\"minecraft:survives_explosion\"}]}]}";

    /** One pool, one item entry. */
    static JsonElement single(String item) {
        return parse(POOL_OPEN + "{\"type\":\"minecraft:item\",\"name\":\"" + item + "\"}" + POOL_CLOSE);
    }

    /** The item of a single-entry table, or null. */
    static String singleItem(JsonElement table) {
        try {
            JsonObject entry = table.getAsJsonObject().getAsJsonArray("pools").get(0).getAsJsonObject()
                .getAsJsonArray("entries").get(0).getAsJsonObject();
            return entry.has("name") ? entry.get("name").getAsString() : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** One pool, one item entry with a uniform count (TFC clay → clay balls). Returns {item, min, max} or null. */
    static String[] singleCount(JsonElement table) {
        try {
            JsonObject entry = table.getAsJsonObject().getAsJsonArray("pools").get(0).getAsJsonObject()
                .getAsJsonArray("entries").get(0).getAsJsonObject();
            JsonObject count = entry.getAsJsonArray("functions").get(0).getAsJsonObject().getAsJsonObject("count");
            String[] r = {entry.get("name").getAsString(), count.get("min").getAsString(), count.get("max").getAsString()};
            return table.equals(singleCount(r[0], r[1], r[2])) ? r : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    static JsonElement singleCount(String item, String min, String max) {
        return parse(POOL_OPEN + "{\"type\":\"minecraft:item\",\"name\":\"" + item + "\",\"functions\":[{\"function\":"
            + "\"minecraft:set_count\",\"count\":{\"min\":" + min + ",\"max\":" + max + ",\"type\":\"minecraft:uniform\"}}]}"
            + POOL_CLOSE);
    }

    /** Raw/hardened rock: raw block if isolated, else 1-4 loose rocks. Returns {raw, loose} or null. */
    static String[] rockToLoose(JsonElement table) {
        try {
            JsonObject alt = table.getAsJsonObject().getAsJsonArray("pools").get(0).getAsJsonObject()
                .getAsJsonArray("entries").get(0).getAsJsonObject();
            String raw = alt.getAsJsonArray("children").get(0).getAsJsonObject().get("name").getAsString();
            String loose = alt.getAsJsonArray("children").get(1).getAsJsonObject().get("name").getAsString();
            return table.equals(rockToLoose(raw, loose)) ? new String[] {raw, loose} : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    static JsonElement rockToLoose(String raw, String loose) {
        return parse(POOL_OPEN + "{\"type\":\"minecraft:alternatives\",\"children\":["
            + "{\"type\":\"minecraft:item\",\"name\":\"" + raw + "\",\"conditions\":[{\"condition\":\"tfp:is_isolated\"}]},"
            + "{\"type\":\"minecraft:item\",\"name\":\"" + loose + "\",\"functions\":[{\"function\":\"minecraft:set_count\","
            + "\"count\":{\"min\":1,\"max\":4,\"type\":\"minecraft:uniform\"}}]}]}" + POOL_CLOSE);
    }

    /** Vanilla-style gravel: silk touch → self; else flint (fortune table bonus) or self. */
    static JsonElement gravel(String self) {
        return parse(POOL_OPEN + "{\"type\":\"minecraft:alternatives\",\"children\":["
            + "{\"type\":\"minecraft:item\",\"name\":\"" + self + "\",\"conditions\":[{\"condition\":\"minecraft:match_tool\","
            + "\"predicate\":{\"predicates\":{\"minecraft:enchantments\":[{\"enchantments\":\"minecraft:silk_touch\","
            + "\"levels\":{\"min\":1}}]}}}]},"
            + "{\"type\":\"minecraft:alternatives\",\"children\":["
            + "{\"type\":\"minecraft:item\",\"name\":\"minecraft:flint\",\"conditions\":[{\"condition\":\"minecraft:table_bonus\","
            + "\"enchantment\":\"minecraft:fortune\",\"chances\":[0.1,0.14285715,0.25,1.0]}]},"
            + "{\"type\":\"minecraft:item\",\"name\":\"" + self + "\"}],"
            + "\"conditions\":[{\"condition\":\"minecraft:survives_explosion\"}]}]}" + POOL_CLOSE);
    }

    private static JsonElement parse(String s) {
        return Json.parse(s, "LootShapes");
    }
}
