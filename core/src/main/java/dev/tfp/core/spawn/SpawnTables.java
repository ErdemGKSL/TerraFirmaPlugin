/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * files: src/main/java/net/dries007/tfc/common/entities/Fauna.java (CODEC defaults),
 * src/main/java/net/dries007/tfc/world/placement/ClimatePlacement.java (CODEC defaults) @ c3b4d9536
 */
package dev.tfp.core.spawn;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.tfp.core.calendar.Month;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The spawn data of a TFC world as datagen ships it ({@code data/spawn_table.json}): each biome's creature lists and
 * creature-spawn probability, and the fauna gate of every species. Immutable, thread-safe.
 *
 * @param biomes biome name ({@code plateau}) to its spawn lists
 * @param fauna  species ({@code tfc:cow}) to its gate
 */
public record SpawnTables(Map<String, BiomeSpawns> biomes, Map<String, FaunaRule> fauna) {
    /**
     * @param probability TFC's {@code creature_spawn_probability} of the biome (chunk generation spawns)
     * @param entries     the TFC entries per category, in the biome JSON's order
     */
    public record BiomeSpawns(float probability, Map<SpawnCategory, List<SpawnEntry>> entries) {
        public List<SpawnEntry> of(SpawnCategory category) {
            return entries.getOrDefault(category, List.of());
        }
    }

    public SpawnTables {
        biomes = Map.copyOf(biomes);
        fauna = Map.copyOf(fauna);
    }

    public Optional<BiomeSpawns> biome(String name) {
        return Optional.ofNullable(biomes.get(name));
    }

    /** Parses {@code spawn_table.json}. */
    public static SpawnTables parse(JsonObject root) {
        Map<String, BiomeSpawns> biomes = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> b : root.getAsJsonObject("biomes").entrySet()) {
            JsonObject o = b.getValue().getAsJsonObject();
            Map<SpawnCategory, List<SpawnEntry>> entries = new EnumMap<>(SpawnCategory.class);
            for (Map.Entry<String, JsonElement> c : o.entrySet()) {
                if (c.getKey().equals("probability")) {
                    continue;
                }
                SpawnCategory category = SpawnCategory.fromSerialized(c.getKey());
                List<SpawnEntry> list = new ArrayList<>();
                for (JsonElement r : c.getValue().getAsJsonArray()) {
                    JsonArray row = r.getAsJsonArray();
                    list.add(new SpawnEntry(b.getKey(), category, row.get(0).getAsString(), row.get(1).getAsInt(),
                            row.get(2).getAsInt(), row.get(3).getAsInt()));
                }
                entries.put(category, List.copyOf(list));
            }
            biomes.put(b.getKey(), new BiomeSpawns(o.get("probability").getAsFloat(), entries));
        }
        Map<String, FaunaRule> fauna = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> f : root.getAsJsonObject("fauna").entrySet()) {
            fauna.put(f.getKey(), rule(f.getValue().getAsJsonObject()));
        }
        return new SpawnTables(biomes, fauna);
    }

    /** One fauna JSON with TFC's codec defaults ({@code Fauna.CODEC}, {@code ClimatePlacement.CODEC}). */
    static FaunaRule rule(JsonObject o) {
        List<Month> months = new ArrayList<>();
        if (o.has("months")) {
            for (JsonElement m : o.getAsJsonArray("months")) {
                months.add(Month.valueOf(m.getAsString().toUpperCase(Locale.ROOT)));
            }
        }
        return new FaunaRule(
                integer(o, "chance", 1),
                integer(o, "distance_below_sea_level", -1),
                number(o, "min_temperature", Float.NEGATIVE_INFINITY),
                number(o, "max_temperature", Float.POSITIVE_INFINITY),
                number(o, "min_groundwater", Float.NEGATIVE_INFINITY),
                number(o, "max_groundwater", Float.POSITIVE_INFINITY),
                integer(o, "min_forest", 0),
                integer(o, "max_forest", 4),
                o.has("fuzzy") && o.get("fuzzy").getAsBoolean(),
                o.has("ignore_rivers") && o.get("ignore_rivers").getAsBoolean(),
                integer(o, "max_brightness", -1),
                months);
    }

    private static int integer(JsonObject o, String key, int fallback) {
        return o.has(key) ? o.get(key).getAsInt() : fallback;
    }

    private static float number(JsonObject o, String key, float fallback) {
        return o.has(key) ? o.get(key).getAsFloat() : fallback;
    }
}
