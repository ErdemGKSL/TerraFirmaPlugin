/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.plant.lifecycle;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.tfp.core.plant.lifecycle.PlantLifecycle;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * PAR-WORLD-61: the generated part of {@code data/tfp/data_maps/plant_lifecycle.json} the stage looks need: per base CE
 * plant state that has a dormant look (datagen {@code PlantBlockGen}: {@code tfp:plant/allium}, {@code tfp:plant/rose[age=1,
 * half=lower]}) its plant's bloom fields and the CE state of the dormant look ({@code tfp:plant/dormant_look[look=0]}).
 * Immutable; any thread.
 */
public final class PlantStageTable {
    /** The data map in the plugin jar's data root ({@code RegistryService.jarDataRoot}). */
    public static final String FILE = "tfp/data_maps/plant_lifecycle.json";

    /** One base state: its plant, the plant's bloom fields and its dormant look's CE state. */
    public record Entry(String plant, PlantLifecycle.Params params, String dormantState) {
    }

    private final Map<String, Entry> byBase;

    private PlantStageTable(Map<String, Entry> byBase) {
        this.byBase = Map.copyOf(byBase);
    }

    public static final PlantStageTable EMPTY = new PlantStageTable(Map.of());

    /** Reads the table from the data map. */
    public static PlantStageTable read(Path file) {
        try (Reader in = Files.newBufferedReader(file)) {
            return parse(JsonParser.parseReader(in).getAsJsonObject());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Parses the data map's {@code plants} and {@code dormant_states}; a map without the table gives {@link #EMPTY}. */
    public static PlantStageTable parse(JsonObject root) {
        if (!root.has("dormant_states")) {
            return EMPTY;
        }
        JsonObject plants = root.getAsJsonObject("plants");
        Map<String, Entry> m = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("dormant_states").entrySet()) {
            JsonObject o = e.getValue().getAsJsonObject();
            String plant = o.get("plant").getAsString();
            JsonObject b = plants.getAsJsonObject(plant);
            if (b == null) {
                throw new IllegalArgumentException("dormant state " + e.getKey() + " names unknown plant " + plant);
            }
            m.put(e.getKey(), new Entry(plant, params(b), o.get("state").getAsString()));
        }
        return new PlantStageTable(m);
    }

    static PlantLifecycle.Params params(JsonObject b) {
        return new PlantLifecycle.Params(b.get("wet_season_blooming").getAsBoolean(), b.get("bloom_offset").getAsFloat(),
            b.get("blooming_end").getAsFloat(), b.get("seeding_end").getAsFloat(), b.get("dying_end").getAsFloat(),
            b.get("dormant_end").getAsFloat(), b.get("sprouting_end").getAsFloat(), b.get("start_hour").getAsInt(),
            b.get("end_hour").getAsInt());
    }

    /** The entry of base CE state {@code state}, when it has a dormant look. */
    public Optional<Entry> entry(String state) {
        return Optional.ofNullable(byBase.get(state));
    }

    /** Every base state with a dormant look, and its entry. */
    public Map<String, Entry> entries() {
        return byBase;
    }

    public boolean isEmpty() {
        return byBase.isEmpty();
    }
}
