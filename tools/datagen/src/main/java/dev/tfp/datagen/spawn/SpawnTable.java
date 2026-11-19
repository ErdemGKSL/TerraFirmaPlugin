/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * files: src/main/resources/data/tfc/worldgen/biome/*.json (spawners), src/generated/resources/data/tfc/tfc/fauna/*.json
 * @ c3b4d9536
 */
package dev.tfp.datagen.spawn;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.tfp.datagen.Json;
import dev.tfp.datagen.OutputTree;
import dev.tfp.datagen.TfcCheckout;
import java.util.Map;

/**
 * PAR-FAUNA-04: the TFC spawn data the Paper spawn pass reads, in one file {@code data/tfp/data_maps/spawn_table.json} (a folder the data loader skips):
 * <pre>
 * { "biomes": { "plateau": { "probability": 0.1, "creature": [["tfc:cow", weight, min, max], ...], ... } },
 *   "fauna":  { "tfc:cow": { TFC's fauna JSON as written } } }
 * </pre>
 * Only {@code tfc:} entries are kept (the vanilla monsters stay with the base biomes' own spawners, see SeasonalBiomes). The
 * rules stay TFC's raw JSON, parsed with TFC's defaults at runtime by {@code core/spawn/FaunaRules}.
 */
public final class SpawnTable {
    public static final String FILE = "data/tfp/data_maps/spawn_table.json";
    static final String BIOMES = TfcCheckout.MAIN + "data/tfc/worldgen/biome/";
    static final String FAUNA = TfcCheckout.GENERATED + "data/tfc/tfc/fauna/";

    private SpawnTable() {
    }

    /** @return {biomes with a TFC entry, fauna files} */
    public static int[] run(TfcCheckout tfc, OutputTree out) {
        JsonObject biomes = new JsonObject();
        for (String path : tfc.list(BIOMES)) {
            if (!path.endsWith(".json")) {
                continue;
            }
            JsonObject biome = Json.parse(tfc.readString(path), path).getAsJsonObject();
            JsonObject spawners = biome.has("spawners") ? biome.getAsJsonObject("spawners") : new JsonObject();
            JsonObject entry = new JsonObject();
            for (Map.Entry<String, JsonElement> category : spawners.entrySet()) {
                JsonArray kept = new JsonArray();
                for (JsonElement e : category.getValue().getAsJsonArray()) {
                    JsonObject s = e.getAsJsonObject();
                    String type = s.get("type").getAsString();
                    if (type.startsWith(Json.FROM_NS + ":")) {
                        JsonArray row = new JsonArray();
                        row.add(type);
                        row.add(s.get("weight").getAsInt());
                        row.add(s.get("minCount").getAsInt());
                        row.add(s.get("maxCount").getAsInt());
                        kept.add(row);
                    }
                }
                if (!kept.isEmpty()) {
                    entry.add(category.getKey(), kept);
                }
            }
            if (!entry.isEmpty()) {
                entry.addProperty("probability",
                        biome.has("creature_spawn_probability") ? biome.get("creature_spawn_probability").getAsFloat() : 0.1f);
                String name = path.substring(path.lastIndexOf('/') + 1, path.length() - ".json".length());
                biomes.add(name, entry);
            }
        }
        JsonObject fauna = new JsonObject();
        for (String path : tfc.list(FAUNA)) {
            if (path.endsWith(".json")) {
                String name = path.substring(path.lastIndexOf('/') + 1, path.length() - ".json".length());
                fauna.add(Json.FROM_NS + ":" + name, Json.parse(tfc.readString(path), path));
            }
        }
        JsonObject root = new JsonObject();
        root.add("biomes", biomes);
        root.add("fauna", fauna);
        out.put(FILE, Json.write(root));
        return new int[] {biomes.size(), fauna.size()};
    }
}
