/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * files: src/main/resources/assets/tfc/sounds.json (entity.*) and assets/tfc/sounds/entity/** @ c3b4d9536
 */
package dev.tfp.datagen.animal;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.tfp.datagen.Json;
import dev.tfp.datagen.OutputTree;
import dev.tfp.datagen.TfcCheckout;
import java.util.Map;

/**
 * PAR-FAUNA-20: TFC's animal sound events ({@code entity.<animal>.<ambient|hurt|death|attack|sleep|step|...>}) and their ogg
 * files in a resource pack namespace {@code tfp_animals}: the carrier mobs are silent and the plugin plays
 * {@code tfp_animals:entity.cow.ambient} and friends on its own events. Events that only point at a vanilla sound
 * ({@code type: event}) keep that reference.
 */
public final class AnimalSounds {
    public static final String NS = "tfp_animals";
    static final String RP = "craftengine/tfp/resourcepack/assets/" + NS + "/";
    /** The sound event names shipped, for the Paper sound player ({@code data_maps} is a folder the data loader skips). */
    public static final String EVENTS_FILE = "data/tfp/data_maps/animal_sounds.json";
    static final String SOUNDS = TfcCheckout.ASSETS + "sounds.json";

    private AnimalSounds() {
    }

    /** @return {events written, ogg files copied} */
    public static int[] run(TfcCheckout tfc, OutputTree out) {
        JsonObject all = Json.parse(tfc.readString(SOUNDS), SOUNDS).getAsJsonObject();
        JsonObject root = new JsonObject();
        int oggs = 0;
        java.util.Set<String> copied = new java.util.TreeSet<>();
        for (Map.Entry<String, JsonElement> e : all.entrySet()) {
            if (!e.getKey().startsWith("entity.")) {
                continue;
            }
            JsonObject src = e.getValue().getAsJsonObject();
            JsonArray list = new JsonArray();
            for (JsonElement s : src.getAsJsonArray("sounds")) {
                JsonObject entry = s.isJsonObject() ? s.getAsJsonObject().deepCopy() : new JsonObject();
                String name = s.isJsonObject() ? entry.get("name").getAsString() : s.getAsString();
                boolean event = entry.has("type") && entry.get("type").getAsString().equals("event");
                if (!name.startsWith(Json.FROM_NS + ":")) {
                    if (!event) {
                        continue; // a vanilla file name, nothing of ours to ship
                    }
                    list.add(s);
                    continue;
                }
                String path = name.substring(Json.FROM_NS.length() + 1);
                String ogg = TfcCheckout.ASSETS + "sounds/" + path + ".ogg";
                if (!tfc.exists(ogg)) {
                    continue;
                }
                if (copied.add(path)) {
                    out.put(RP + "sounds/" + path + ".ogg", tfc.read(ogg));
                    oggs++;
                }
                if (s.isJsonObject()) {
                    entry.addProperty("name", NS + ":" + path);
                    list.add(entry);
                } else {
                    list.add(NS + ":" + path);
                }
            }
            if (!list.isEmpty()) {
                JsonObject ev = new JsonObject();
                ev.add("sounds", list);
                root.add(e.getKey(), ev);
            }
        }
        out.put(RP + "sounds.json", Json.write(root));
        JsonArray events = new JsonArray();
        root.keySet().forEach(events::add);
        out.put(EVENTS_FILE, Json.write(events)); // the runtime plays only events that exist
        return new int[] {root.size(), oggs};
    }
}
