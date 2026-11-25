/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/TFCBlocks.java @ c3b4d9536 (TRIP_HAMMER: strength 3, SoundType.METAL),
 *       src/main/java/net/dries007/tfc/common/blocks/TripHammerBlock.java @ c3b4d9536 (FACING),
 *       src/main/java/net/dries007/tfc/common/blocks/TripHammerBlock.java @ c3b4d9536 (FACING, BASE_SHAPES),
 *       src/main/resources/assets/tfc/blockstates/trip_hammer.json @ c3b4d9536,
 *       src/main/resources/assets/tfc/models/block/trip_hammer.json @ c3b4d9536,
 *       src/main/resources/assets/tfc/models/item/trip_hammer.json @ c3b4d9536,
 *       src/main/resources/data/tfc/loot_table/blocks/trip_hammer.json @ c3b4d9536
 */
package dev.tfp.datagen.triphammer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.tfp.datagen.DatagenException;
import dev.tfp.datagen.Json;
import dev.tfp.datagen.OutputTree;
import dev.tfp.datagen.TfcCheckout;
import dev.tfp.datagen.Yaml;
import dev.tfp.datagen.device.DeviceAppearance;
import dev.tfp.datagen.device.DeviceBlockSpec;
import dev.tfp.datagen.device.DeviceProperty;
import dev.tfp.datagen.device.DeviceSound;
import dev.tfp.datagen.device.DeviceYaml;
import dev.tfp.datagen.rendered.RenderedCubeSpec;
import dev.tfp.datagen.rendered.RenderedTemplates;
import dev.tfp.datagen.rotation.RotationBlocks;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PAR-MECH-27: TFC's trip hammer as the CraftEngine block {@code tfp:trip_hammer}, {@code configuration/triphammer/trip_hammer.yml}.
 *
 * <ul>
 *   <li>Property: TFC's {@code facing} (north, east, south, west), 4 server states; the {@code tfp:device} behaviour (the
 *       trip hammer module's device kind), TFC's settings (strength 3, metal sounds, the pickaxe of TFC's mineable tag) and
 *       TFC's loot (the block itself).</li>
 *   <li>Look: a barrier carrier per state drawn by TFC's {@code trip_hammer} model as an item display, turned as TFC's
 *       blockstate turns it. TFC's shape ({@code BASE_SHAPES}: two posts and a base plate, 10 deep) is not the barrier's
 *       full cube: a ledger divergence (the hitbox allocation has no trip hammer row); TFC's renderer swings the held hammer,
 *       which is not drawn.</li>
 *   <li>The item: TFC's {@code item/trip_hammer} model (parent {@code block/trip_hammer}); the names from every TFC locale.</li>
 * </ul>
 */
public final class TripHammerBlock {
    static final String BLOCKS_JAVA = "src/main/java/net/dries007/tfc/common/blocks/TFCBlocks.java";
    static final String BLOCKSTATE = TfcCheckout.ASSETS + "blockstates/trip_hammer.json";
    static final String MODELS = TfcCheckout.ASSETS + "models/";
    static final String LOOT = TfcCheckout.MAIN + "data/" + Json.FROM_NS + "/loot_table/blocks/trip_hammer.json";
    static final String LANG = TfcCheckout.ASSETS + "lang/";
    static final String PACK = "craftengine/tfp/";
    public static final String OUTPUT = PACK + "configuration/triphammer/trip_hammer.yml";
    static final String RP = PACK + "resourcepack/assets/tfp/";
    static final String LANG_RP = PACK + "resourcepack/assets/tfp_triphammer/lang/";
    static final String HEADER = DeviceYaml.HEADER + "\nPAR-MECH-27: the trip hammer (dev.tfp.datagen.triphammer.TripHammerBlock).";
    public static final String ID = Json.TO_NS + ":trip_hammer";
    static final String MODEL_PREFIX = "block/trip_hammer/";
    public static final List<String> FACINGS = List.of("north", "east", "south", "west");
    static final String RENDER = Json.TO_NS + ":render/trip_hammer";
    private static final Pattern STRENGTH = Pattern.compile("\\.strength\\(([0-9.]+)[fF]\\)");
    private static final Pattern TFC_TEXTURE = Pattern.compile("\"(" + Json.FROM_NS + ":block/[a-z0-9_/]+)\"");

    private TripHammerBlock() {
    }

    /** True when the checkout has the trip hammer (the small test fixtures do not). */
    public static boolean available(TfcCheckout tfc) {
        return tfc.exists(BLOCKSTATE) && tfc.exists(BLOCKS_JAVA) && tfc.exists(LOOT)
            && tfc.exists(MODELS + "block/trip_hammer.json") && tfc.exists(MODELS + "item/trip_hammer.json");
    }

    /** Writes everything. Returns the number of server states. */
    public static long run(TfcCheckout tfc, OutputTree out) {
        String hardness = strength(tfc);
        Map<String, Integer> yRotation = rotations(tfc);
        Models models = new Models(tfc, out);
        String model = models.copy("block/trip_hammer");
        models.itemModel();

        DeviceBlockSpec.Settings settings = DeviceBlockSpec.Settings.of(hardness, hardness, DeviceSound.METAL, "pickaxe");
        DeviceBlockSpec.Builder b = DeviceBlockSpec.builder(ID).settings(settings).deviceBehaviour().blockItem(langKey())
            .property(DeviceProperty.names("facing", FACINGS, "north"))
            .renderItem(RENDER, tfp(model));
        for (String facing : FACINGS) {
            String rot = RotationBlocks.rotation(0, yRotation.get(facing));
            b.appearance(DeviceAppearance.rendered(facing, DeviceAppearance.Carrier.state(RenderedTemplates.CARRIER),
                List.of(DeviceAppearance.Renderer.item(RENDER).rotated(rot))));
        }
        b.everyState(s -> s.get("facing"), null);
        DeviceBlockSpec spec = b.build();
        out.put(OUTPUT, Yaml.write(HEADER, finish(spec, loot(tfc))));
        writeLang(tfc, out);
        return spec.stateCount();
    }

    static String langKey() {
        return "block." + Json.TO_NS + ".trip_hammer";
    }

    static String tfp(String path) {
        return Json.TO_NS + ":" + path;
    }

    /** TFC's registration: {@code strength(3f)} with {@code SoundType.METAL}. */
    static String strength(TfcCheckout tfc) {
        String src = tfc.readString(BLOCKS_JAVA);
        int at = src.indexOf("register(\"trip_hammer\"");
        if (at < 0) {
            throw new DatagenException(BLOCKS_JAVA + ": no trip_hammer registration");
        }
        String line = src.substring(at, src.indexOf('\n', at));
        Matcher m = STRENGTH.matcher(line);
        if (!m.find() || !line.contains(".sound(SoundType.METAL)")) {
            throw new DatagenException(BLOCKS_JAVA + ": the trip hammer is no longer strength(x) with SoundType.METAL");
        }
        return m.group(1);
    }

    /** The y turn of each facing from TFC's blockstate (north 0, east 90, south 180, west 270). */
    static Map<String, Integer> rotations(TfcCheckout tfc) {
        JsonObject variants = Json.parse(tfc.readString(BLOCKSTATE), BLOCKSTATE).getAsJsonObject().getAsJsonObject("variants");
        Map<String, Integer> out = new TreeMap<>();
        for (Map.Entry<String, JsonElement> v : variants.entrySet()) {
            JsonObject apply = v.getValue().getAsJsonObject();
            Matcher m = Pattern.compile("facing=([a-z]+)").matcher(v.getKey());
            if (!m.matches() || !FACINGS.contains(m.group(1)) || !apply.get("model").getAsString().equals(Json.FROM_NS + ":block/trip_hammer")
                || apply.has("x")) {
                throw new DatagenException(BLOCKSTATE + ": unexpected variant " + v.getKey() + " -> " + apply);
            }
            out.put(m.group(1), apply.has("y") ? apply.get("y").getAsInt() : 0);
        }
        if (!out.keySet().equals(new TreeSet<>(FACINGS))) {
            throw new DatagenException(BLOCKSTATE + ": variants " + variants.keySet());
        }
        return out;
    }

    /** TFC's loot table: one pool rolled once under {@code survives_explosion}, the trip hammer itself. */
    static List<Object> loot(TfcCheckout tfc) {
        JsonObject root = Json.rename(Json.parse(tfc.readString(LOOT), LOOT)).getAsJsonObject();
        JsonArray pools = root.getAsJsonArray("pools");
        if (pools.size() != 1) {
            throw new DatagenException(LOOT + ": " + pools.size() + " pools");
        }
        JsonObject pool = pools.get(0).getAsJsonObject();
        JsonArray conditions = pool.getAsJsonArray("conditions");
        JsonArray entries = pool.getAsJsonArray("entries");
        if (pool.get("rolls").getAsInt() != 1 || conditions == null || conditions.size() != 1
            || !"minecraft:survives_explosion".equals(conditions.get(0).getAsJsonObject().get("condition").getAsString())
            || entries.size() != 1) {
            throw new DatagenException(LOOT + ": unexpected pool " + pool);
        }
        JsonObject entry = entries.get(0).getAsJsonObject();
        if (!"minecraft:item".equals(entry.get("type").getAsString()) || entry.has("conditions")
            || !ID.equals(entry.get("name").getAsString())) {
            throw new DatagenException(LOOT + ": unexpected entry " + entry);
        }
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("type", "item");
        e.put("item", ID);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("rolls", 1);
        out.put("conditions", List.of(Map.of("type", "survives_explosion")));
        out.put("entries", List.of(e));
        return List.of(out);
    }

    /** The device spec's literal YAML with the rendered cube's settings and break events, TFC's loot, view ranges. */
    @SuppressWarnings("unchecked")
    static Map<String, Object> finish(DeviceBlockSpec spec, List<Object> pools) {
        Map<String, Object> root = DeviceYaml.literal(spec);
        Map<String, Object> blocks = (Map<String, Object>) root.get("blocks");
        Map<String, Object> block = (Map<String, Object>) blocks.get(spec.id());
        RenderedCubeSpec cubeSpec = new RenderedCubeSpec(spec.id(), RENDER, spec.renderItems().get(RENDER),
            spec.settings(), false, spec.id(), spec.blockItemLangKey());
        Map<String, Object> expanded = expand(cubeTemplate(), cubeSpec.arguments());
        Map<String, Object> next = new LinkedHashMap<>();
        next.put("settings", expanded.get("settings"));
        next.put("loot", Map.of("pools", pools));
        next.put("behavior", block.get("behavior"));
        next.put("events", expanded.get("events"));
        next.put("states", block.get("states"));
        blocks.put(spec.id(), next);
        Map<String, Object> appearances = (Map<String, Object>) ((Map<String, Object>) next.get("states")).get("appearances");
        for (Object a : appearances.values()) {
            Map<String, Object> appearance = (Map<String, Object>) a;
            appearance.remove("transparent");
            for (Object r : (List<Object>) appearance.get("entity_renderer")) {
                ((Map<String, Object>) r).put("view_range", new BigDecimal(RenderedTemplates.CUBE_VIEW_RANGE));
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("blocks", blocks);
        out.put("items", root.getOrDefault("items", Map.of()));
        return out;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> cubeTemplate() {
        Map<String, Object> templates = (Map<String, Object>) RenderedTemplates.yaml().get("templates#rendered_block");
        return (Map<String, Object>) templates.get(RenderedTemplates.CUBE);
    }

    /** The template body with every {@code ${name}} and {@code ${name:-default}} replaced; a lone placeholder keeps its type. */
    @SuppressWarnings("unchecked")
    static <T> T expand(T node, Map<String, Object> args) {
        if (node instanceof Map<?, ?> m) {
            Map<String, Object> out = new LinkedHashMap<>();
            m.forEach((k, v) -> out.put((String) k, expand(v, args)));
            return (T) out;
        }
        if (node instanceof List<?> l) {
            List<Object> out = new ArrayList<>();
            l.forEach(v -> out.add(expand(v, args)));
            return (T) out;
        }
        if (node instanceof String s && s.startsWith("${") && s.endsWith("}") && s.indexOf("${", 2) < 0) {
            String body = s.substring(2, s.length() - 1);
            int d = body.indexOf(":-");
            String name = d < 0 ? body : body.substring(0, d);
            Object v = args.get(name);
            if (v == null && d >= 0) {
                return (T) new BigDecimal(body.substring(d + 2));
            }
            if (v == null) {
                throw new DatagenException("rendered_cube argument " + name + " missing");
            }
            return (T) v;
        }
        if (node instanceof String s && s.contains("${")) {
            throw new DatagenException("rendered_cube placeholder inside text: " + s);
        }
        return node;
    }

    /** Copies TFC's trip hammer models into {@code tfp:block/trip_hammer/**} with their textures. */
    static final class Models {
        private final TfcCheckout tfc;
        private final OutputTree out;

        Models(TfcCheckout tfc, OutputTree out) {
            this.tfc = tfc;
            this.out = out;
        }

        /** TFC {@code block/<name>} as {@code block/trip_hammer/<name>}; a vanilla parent stays. */
        String copy(String path) {
            String ours = MODEL_PREFIX + path.substring("block/".length());
            String src = MODELS + path + ".json";
            JsonObject model = Json.parse(tfc.readString(src), src).getAsJsonObject();
            copyTextures(tfc.readString(src));
            if (model.has("parent") && model.get("parent").getAsString().startsWith(Json.FROM_NS + ":")) {
                throw new DatagenException(src + ": a TFC parent " + model.get("parent"));
            }
            model.remove("__comment__");
            model.remove("credit");
            out.put(RP + "models/" + ours + ".json", Json.write(Json.rename(model)));
            return ours;
        }

        /** TFC {@code item/trip_hammer} (parent {@code tfc:block/trip_hammer}) with the parent renamed to our copy. */
        void itemModel() {
            String src = MODELS + "item/trip_hammer.json";
            JsonObject model = Json.parse(tfc.readString(src), src).getAsJsonObject();
            String parent = model.get("parent").getAsString();
            if (!parent.equals(Json.FROM_NS + ":block/trip_hammer")) {
                throw new DatagenException(src + ": parent " + parent + " is not TFC's trip hammer model");
            }
            model.addProperty("parent", tfp(MODEL_PREFIX + "trip_hammer"));
            model.remove("__comment__");
            out.put(RP + "models/item/trip_hammer.json", Json.write(Json.rename(model)));
        }

        void texture(String ref) {
            String png = TfcCheckout.ASSETS + "textures/" + ref + ".png";
            if (!tfc.exists(png)) {
                throw new DatagenException("no TFC texture " + png);
            }
            out.put(RP + "textures/" + ref + ".png", tfc.read(png));
            if (tfc.exists(png + ".mcmeta")) {
                out.put(RP + "textures/" + ref + ".png.mcmeta", tfc.read(png + ".mcmeta"));
            }
        }

        private void copyTextures(String json) {
            Matcher m = TFC_TEXTURE.matcher(json);
            while (m.find()) {
                texture(m.group(1).substring(Json.FROM_NS.length() + 1));
            }
        }
    }

    /** {@code block.tfp.trip_hammer} from every TFC locale that names it. */
    static void writeLang(TfcCheckout tfc, OutputTree out) {
        String tfcKey = "block." + Json.FROM_NS + ".trip_hammer";
        for (String f : tfc.list(LANG)) {
            if (!f.endsWith(".json")) {
                continue;
            }
            String locale = f.substring(LANG.length(), f.length() - ".json".length());
            JsonObject src = Json.parse(tfc.readString(f), f).getAsJsonObject();
            if (!src.has(tfcKey)) {
                if (locale.equals("en_us")) {
                    throw new DatagenException("TFC has no English name " + tfcKey);
                }
                continue;
            }
            JsonObject o = new JsonObject();
            o.addProperty(langKey(), src.get(tfcKey).getAsString());
            out.put(LANG_RP + locale + ".json", Json.write(o));
        }
    }
}
