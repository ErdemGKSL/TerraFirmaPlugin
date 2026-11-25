/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * files: src/main/resources/assets/tfc/sounds.json and assets/tfc/sounds/** @ c3b4d9536
 */
package dev.tfp.datagen.sound;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.tfp.datagen.DatagenException;
import dev.tfp.datagen.Json;
import dev.tfp.datagen.OutputTree;
import dev.tfp.datagen.TfcCheckout;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * PAR-DATA-08: TFC's non-entity sound events ({@code block.*}, {@code item.*}, {@code random.*}) and their oggs in the
 * resource pack namespace {@code tfp}: {@code tfp:block.anvil.hit} and friends. The animal events live in
 * {@code tfp_animals} (PAR-FAUNA-20), the peat events in {@code tfp_soil} and the firestarter event in {@code tfp_fire};
 * those are skipped here so no ogg ships twice. Entries that only point at a vanilla event ({@code type: event}) or at a
 * vanilla file keep that reference. The CC-BY-4.0 ogg is listed in {@code third-party.json} and NOTICE. Subtitle keys keep
 * TFC's names; TFC's translations of the ones TFC owns ship in {@code tfp_sound/lang}.
 */
public final class TfcSounds {
    public static final String NS = Json.TO_NS;
    static final String RP = "craftengine/tfp/resourcepack/assets/" + NS + "/";
    static final String LANG_RP = "craftengine/tfp/resourcepack/assets/tfp_sound/lang/";
    /** The shipped event names, for the Paper sound facade and its tests. */
    public static final String EVENTS_FILE = "data/tfp/data_maps/sounds.json";
    static final String SOUNDS = TfcCheckout.ASSETS + "sounds.json";
    static final String LANG = TfcCheckout.ASSETS + "lang/";
    /** Events that ship under another namespace already. */
    static final List<String> SHIPPED_ELSEWHERE = List.of("entity.", "block.peat.", "item.firestarter.");
    static final String CC_BY_OGG = "random/rock_slide_long_3";
    static final String CC_BY_SOURCE = "https://freesound.org/people/Benboncan/sounds/60085/";

    private TfcSounds() {
    }

    public static boolean available(TfcCheckout tfc) {
        return tfc.exists(SOUNDS);
    }

    /** @return {events written, ogg files copied} */
    public static int[] run(TfcCheckout tfc, OutputTree out) {
        JsonObject all = Json.parse(tfc.readString(SOUNDS), SOUNDS).getAsJsonObject();
        JsonObject root = new JsonObject();
        Set<String> copied = new TreeSet<>();
        Set<String> subtitles = new TreeSet<>();
        for (Map.Entry<String, JsonElement> e : all.entrySet()) {
            if (SHIPPED_ELSEWHERE.stream().anyMatch(e.getKey()::startsWith)) {
                continue;
            }
            JsonObject src = e.getValue().getAsJsonObject();
            JsonArray list = new JsonArray();
            for (JsonElement s : src.getAsJsonArray("sounds")) {
                JsonObject entry = s.isJsonObject() ? s.getAsJsonObject().deepCopy() : null;
                String name = entry == null ? s.getAsString() : entry.get("name").getAsString();
                if (name.startsWith(Json.FROM_NS + ":")) {
                    String path = name.substring(Json.FROM_NS.length() + 1);
                    String ogg = TfcCheckout.ASSETS + "sounds/" + path + ".ogg";
                    if (!tfc.exists(ogg)) {
                        throw new DatagenException(SOUNDS + ": " + e.getKey() + " names " + name + " but " + ogg + " is missing");
                    }
                    if (copied.add(path)) {
                        out.put(RP + "sounds/" + path + ".ogg", tfc.read(ogg));
                    }
                    name = NS + ":" + path;
                }
                if (entry == null) {
                    list.add(name);
                } else {
                    entry.addProperty("name", name);
                    list.add(entry);
                }
            }
            JsonObject ev = new JsonObject();
            if (src.has("subtitle")) {
                ev.add("subtitle", src.get("subtitle"));
                subtitles.add(src.get("subtitle").getAsString());
            }
            ev.add("sounds", list);
            root.add(e.getKey(), ev);
        }
        out.put(RP + "sounds.json", Json.write(root));
        JsonArray events = new JsonArray();
        root.keySet().forEach(events::add);
        out.put(EVENTS_FILE, Json.write(events));
        writeLang(tfc, out, subtitles);
        return new int[] {root.size(), copied.size()};
    }

    /** TFC's translations of the subtitle keys it owns (vanilla's {@code subtitles.block.generic.*} are left to the game). */
    private static void writeLang(TfcCheckout tfc, OutputTree out, Set<String> subtitles) {
        for (String f : tfc.list(LANG)) {
            if (!f.endsWith(".json")) {
                continue;
            }
            String locale = f.substring(LANG.length(), f.length() - ".json".length());
            JsonObject src = Json.parse(tfc.readString(f), f).getAsJsonObject();
            JsonObject o = new JsonObject();
            for (String key : subtitles) {
                if (src.has(key)) {
                    o.addProperty(key, src.get(key).getAsString());
                } else if (locale.equals("en_us") && !vanillaKey(key)) {
                    throw new DatagenException("TFC's English lang has no subtitle " + key);
                }
            }
            if (o.size() > 0) {
                out.put(LANG_RP + locale + ".json", Json.write(o));
            }
        }
    }

    static boolean vanillaKey(String key) {
        return key.startsWith("subtitles.block.generic.") || key.startsWith("subtitles.entity.generic.");
    }

    /** The third-party manifest: the one CC-BY-4.0 file this generator copies (checked against NOTICE by {@code checkNotice}). */
    public static String thirdParty(OutputTree out) {
        List<String> rows = new ArrayList<>();
        String path = RP + "sounds/" + CC_BY_OGG + ".ogg";
        if (out.contains(path)) {
            JsonObject o = new JsonObject();
            o.addProperty("path", path);
            o.addProperty("license", "CC-BY-4.0");
            o.addProperty("source", CC_BY_SOURCE);
            rows.add(Json.write(o).strip());
        }
        return rows.isEmpty() ? "[]\n" : "[\n" + String.join(",\n", rows) + "\n]\n";
    }
}
