/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.heat;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * The datagen runtime list of the heat glow (PAR-HEAT-01, {@value #FILE}): which CraftEngine items have a heat glow item
 * model definition and its {@code item_model} id. Only these items glow: another item would show a missing model.
 * Immutable.
 */
public final class HeatGlowData {
    public static final String FILE = "content/visuals/heat_glow.json";

    private final Map<String, String> modelById;
    private final int firstGlowingIndex;

    HeatGlowData(Map<String, String> modelById, int firstGlowingIndex) {
        this.modelById = Map.copyOf(modelById);
        this.firstGlowingIndex = firstGlowingIndex;
    }

    public static HeatGlowData empty() {
        return new HeatGlowData(Map.of(), Integer.MAX_VALUE);
    }

    /** Reads the list from {@code loader} (the plugin's class loader, which holds the content jar). */
    public static HeatGlowData load(ClassLoader loader) throws IOException {
        try (InputStream in = loader.getResourceAsStream(FILE)) {
            if (in == null) {
                throw new IOException("missing " + FILE + " in the plugin jar");
            }
            return read(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    /** Parses the list (format 1). */
    public static HeatGlowData read(Reader reader) throws IOException {
        try {
            JsonObject o = JsonParser.parseReader(reader).getAsJsonObject();
            if (o.get("format").getAsInt() != 1) {
                throw new IOException("unsupported heat_glow data format");
            }
            Map<String, String> byId = new HashMap<>();
            for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("items").entrySet()) {
                byId.put(e.getKey(), e.getValue().getAsString());
            }
            return new HeatGlowData(byId, o.get("first_glowing_index").getAsInt());
        } catch (RuntimeException e) {
            throw new IOException("malformed heat_glow data: " + e, e);
        }
    }

    /** The glow {@code item_model} of a CraftEngine item id, or null when it has none. */
    public String model(String id) {
        return id == null ? null : modelById.get(id);
    }

    /** The {@code custom_model_data} floats[0] value from which the glow shows (datagen's first glowing level). */
    public int firstGlowingIndex() {
        return firstGlowingIndex;
    }

    public int size() {
        return modelById.size();
    }
}
