/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/plant/Plant.java @ c3b4d9536 (the bloom fields of every plant),
 *       src/main/resources/assets/tfc/models/block/plant/*.json @ c3b4d9536 (the tfc:plant loader models)
 */
package dev.tfp.datagen.plant.stage;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.tfp.datagen.DatagenException;
import dev.tfp.datagen.Json;
import dev.tfp.datagen.OutputTree;
import dev.tfp.datagen.TfcCheckout;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PAR-WORLD-48: the data of TFC's seasonal plant stages, for {@code dev.tfp.core.plant.lifecycle.PlantLifecycle} and the
 * stage visuals. Writes {@value #FILE}: per plant its bloom fields (parsed from the {@code Plant} enum, the constructor
 * defaults applied), per {@code tfc:plant} loader model its six stage models, and the look counts the visual budget needs.
 */
public final class PlantStageGen {
    public static final String PLANT_JAVA = "src/main/java/net/dries007/tfc/common/blocks/plant/Plant.java";
    public static final String MODELS = "src/main/resources/assets/tfc/models/block";
    public static final String FILE = "data/tfp/data_maps/plant_lifecycle.json";
    /** The keys of a {@code tfc:plant} model, in {@code PlantBlockModel}'s field order. */
    public static final List<String> STAGES = List.of("dormant", "sprouting", "budding", "blooming", "seeding", "dying");

    private static final Pattern ENTRY = Pattern.compile("^ {4}([A-Z_]+)\\(BlockType\\.([A-Z_]+),([^)]*)\\)", Pattern.MULTILINE);

    /** The bloom fields of one plant ({@code RegistryPlant}). */
    public record Bloom(String name, String type, boolean wetSeasonBlooming, float bloomOffset, float bloomingEnd, float seedingEnd,
                        float dyingEnd, float dormantEnd, float sproutingEnd, int startHour, int endHour) {}

    private PlantStageGen() {}

    private static float f(String s) {
        return Float.parseFloat(s.trim().replaceAll("[fF]$", ""));
    }

    /** Every plant of the enum in TFC's order, with the constructor chain's defaults. */
    public static List<Bloom> parse(TfcCheckout tfc) {
        Matcher m = ENTRY.matcher(tfc.readString(PLANT_JAVA));
        List<Bloom> out = new java.util.ArrayList<>();
        while (m.find()) {
            String[] a = m.group(3).split(",");
            // a[0] speedFactor; Plant(type, speed) -> wet false; (type, speed, wet) -> default bloom fields
            boolean wet = a.length > 1 && Boolean.parseBoolean(a[1].trim());
            String name = m.group(1).toLowerCase(Locale.ROOT);
            Bloom b;
            if (a.length <= 2) {
                b = new Bloom(name, m.group(2), wet, -0.124f, 0.25f, 0.42f, 0.5f, 0.75f, 0.88f, 0, 0);
            } else if (a.length == 8 || a.length == 10) {
                int start = a.length == 10 ? Integer.parseInt(a[8].trim()) : 0;
                int end = a.length == 10 ? Integer.parseInt(a[9].trim()) : 0;
                b = new Bloom(name, m.group(2), wet, f(a[2]), f(a[3]), f(a[4]), f(a[5]), f(a[6]), f(a[7]), start, end);
            } else {
                throw new DatagenException(PLANT_JAVA + ": unexpected constructor of " + m.group(1) + ": " + m.group(3));
            }
            out.add(b);
        }
        if (out.isEmpty()) {
            throw new DatagenException(PLANT_JAVA + ": no plant entries found");
        }
        return out;
    }

    /** Every {@code tfc:plant} loader model ({@code tfc:block/...}) and its stage models, by stage key. */
    public static Map<String, Map<String, String>> stageModels(TfcCheckout tfc) {
        Map<String, Map<String, String>> out = new TreeMap<>();
        for (String path : tfc.list(MODELS)) {
            if (!path.endsWith(".json")) {
                continue;
            }
            String text = tfc.readString(path);
            if (!text.contains("\"tfc:plant\"")) {
                continue;
            }
            JsonObject j = Json.parse(text, path).getAsJsonObject();
            if (!"tfc:plant".equals(j.has("loader") ? j.get("loader").getAsString() : null)) {
                continue;
            }
            Map<String, String> stages = new LinkedHashMap<>();
            for (String s : STAGES) {
                JsonElement e = j.get(s);
                if (e == null || !e.getAsJsonObject().has("parent")) {
                    throw new DatagenException(path + ": stage " + s + " without a parent");
                }
                stages.put(s, e.getAsJsonObject().get("parent").getAsString());
            }
            String id = "tfc:block/" + path.substring(MODELS.length() + 1, path.length() - ".json".length());
            out.put(id, stages);
        }
        return out;
    }

    public static int run(TfcCheckout tfc, OutputTree out) {
        List<Bloom> plants = parse(tfc);
        Map<String, Map<String, String>> models = stageModels(tfc);
        JsonObject p = new JsonObject();
        for (Bloom b : plants) {
            JsonObject o = new JsonObject();
            o.addProperty("wet_season_blooming", b.wetSeasonBlooming());
            o.addProperty("bloom_offset", b.bloomOffset());
            o.addProperty("blooming_end", b.bloomingEnd());
            o.addProperty("seeding_end", b.seedingEnd());
            o.addProperty("dying_end", b.dyingEnd());
            o.addProperty("dormant_end", b.dormantEnd());
            o.addProperty("sprouting_end", b.sproutingEnd());
            o.addProperty("start_hour", b.startHour());
            o.addProperty("end_hour", b.endHour());
            p.add(b.name(), o);
        }
        JsonObject m = new JsonObject();
        Set<String> looks = new TreeSet<>();
        int extra = 0;
        for (Map.Entry<String, Map<String, String>> e : models.entrySet()) {
            JsonObject o = new JsonObject();
            e.getValue().forEach(o::addProperty);
            m.add(e.getKey(), o);
            Set<String> own = new TreeSet<>(e.getValue().values());
            looks.addAll(own);
            extra += own.size() - 1;
        }
        JsonObject budget = new JsonObject();
        budget.addProperty("stage_models", models.size());
        budget.addProperty("distinct_looks", looks.size());
        // the looks beyond the one each model draws today (PAR-WORLD-12 draws one stage per model)
        budget.addProperty("extra_looks", extra);
        JsonObject root = new JsonObject();
        root.add("plants", p);
        root.add("models", m);
        root.add("budget", budget);
        byte[] old = out.get(FILE);
        if (old == null) {
            out.put(FILE, Json.write(root));
        } else {
            // PlantBlockGen ran first: keep its dormant table
            JsonObject before = Json.parse(new String(old, StandardCharsets.UTF_8), FILE).getAsJsonObject();
            if (before.has(DORMANT_STATES)) {
                root.add(DORMANT_STATES, before.get(DORMANT_STATES));
            }
            out.replace(FILE, Json.write(root).getBytes(StandardCharsets.UTF_8));
        }
        return plants.size() + models.size();
    }

    /** The key of PAR-WORLD-61's table: base CE plant state to {@code {plant, state}} of its dormant look. */
    public static final String DORMANT_STATES = "dormant_states";

    /**
     * PAR-WORLD-61: puts {@code PlantBlockGen}'s dormant table into {@value #FILE} under {@value #DORMANT_STATES}, whichever
     * of the two generators runs first.
     */
    public static void mergeDormantStates(OutputTree out, JsonObject table) {
        byte[] old = out.get(FILE);
        if (old == null) {
            JsonObject root = new JsonObject();
            root.add(DORMANT_STATES, table);
            out.put(FILE, Json.write(root));
            return;
        }
        JsonObject root = Json.parse(new String(old, StandardCharsets.UTF_8), FILE).getAsJsonObject();
        root.add(DORMANT_STATES, table);
        out.replace(FILE, Json.write(root).getBytes(StandardCharsets.UTF_8));
    }
}
