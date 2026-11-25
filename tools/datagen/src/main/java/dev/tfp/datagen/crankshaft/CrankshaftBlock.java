/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/TFCBlocks.java @ c3b4d9536 (CRANKSHAFT: strength 3, SoundType.METAL),
 *       src/main/java/net/dries007/tfc/common/blocks/rotation/CrankshaftBlock.java @ c3b4d9536 (FACING, PART),
 *       src/main/java/net/dries007/tfc/client/render/blockentity/CrankshaftBlockEntityRenderer.java @ c3b4d9536 (the wheel
 *       model on the base, the shaft's cuboids: connecting box, piston and arm, the rod texture; PAR-MECH-26: the pump attachment
 *       and PUMP_TEXTURE),
 *       src/main/resources/assets/tfc/blockstates/crankshaft.json @ c3b4d9536,
 *       src/main/resources/assets/tfc/models/block/{crankshaft,crankshaft_wheel,crankshaft_with_wheel}.json @ c3b4d9536,
 *       src/main/resources/assets/tfc/models/item/crankshaft.json @ c3b4d9536,
 *       src/main/resources/data/tfc/loot_table/blocks/crankshaft.json @ c3b4d9536
 */
package dev.tfp.datagen.crankshaft;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.tfp.core.crankshaft.CrankshaftRules;
import dev.tfp.datagen.DatagenException;
import dev.tfp.datagen.Json;
import dev.tfp.datagen.OutputTree;
import dev.tfp.datagen.TfcCheckout;
import dev.tfp.datagen.Yaml;
import dev.tfp.datagen.device.DeviceAppearance;
import dev.tfp.datagen.device.DeviceBlockSpec;
import dev.tfp.datagen.device.DeviceProperty;
import dev.tfp.datagen.device.DeviceYaml;
import dev.tfp.datagen.rendered.RenderedCubeSpec;
import dev.tfp.datagen.rendered.RenderedTemplates;
import dev.tfp.datagen.rotation.RotationBlocks;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PAR-MECH-21: TFC's crankshaft as the CraftEngine block {@code tfp:crankshaft}, {@code configuration/crankshaft/crankshaft.yml}.
 *
 * <ul>
 *   <li>Properties: TFC's {@code facing} (north, east, south, west) and {@code part} (base, shaft): 8 server states, the
 *       {@code tfp:device} behaviour (the crankshaft module's device kind), TFC's settings (strength 3, metal sounds, the
 *       pickaxe of TFC's mineable tag) and TFC's loot (the base drops the crankshaft, the shaft a steel rod).</li>
 *   <li>Look: a barrier carrier per state. The base's static element is TFC's {@code crankshaft} model turned as TFC's
 *       blockstate turns it; the shaft draws nothing of its own (TFC's blockstate draws {@code block/empty}). PAR-MECH-26: the
 *       moving parts are packet displays of the spinning visuals ({@code dev.tfp.paper.rotation.spin.SpinCranks}): the wheel
 *       ({@code crankshaft_wheel}), the axle stub (the axle's own render item) and the shaft's rod as two render items of the
 *       renderer's cuboids, both centred on the connecting point the renderer turns about ({@link #sliderModel}: the
 *       connecting box and the piston; {@link #armModel}: the arm), and the brass attachment toward a steel pump
 *       ({@link #pumpModel}, TFC's {@code PUMP_TEXTURE}). The full-cube outlines stay a ledger divergence (TFC's
 *       {@code BASE_SHAPES} / {@code SHAFT_SHAPES} need rows in the TFC shape table first, PAR-MECH-28).</li>
 *   <li>The item: TFC's {@code item/crankshaft} model (parent {@code crankshaft_with_wheel}); the names from every TFC locale.</li>
 * </ul>
 */
public final class CrankshaftBlock {
    static final String BLOCKS_JAVA = "src/main/java/net/dries007/tfc/common/blocks/TFCBlocks.java";
    static final String BLOCKSTATE = TfcCheckout.ASSETS + "blockstates/crankshaft.json";
    static final String MODELS = TfcCheckout.ASSETS + "models/";
    static final String RENDERER = "src/main/java/net/dries007/tfc/client/render/blockentity/CrankshaftBlockEntityRenderer.java";
    static final String LOOT = TfcCheckout.MAIN + "data/" + Json.FROM_NS + "/loot_table/blocks/crankshaft.json";
    static final String LANG = TfcCheckout.ASSETS + "lang/";
    static final String PACK = "craftengine/tfp/";
    public static final String OUTPUT = PACK + "configuration/crankshaft/crankshaft.yml";
    static final String RP = PACK + "resourcepack/assets/tfp/";
    static final String LANG_RP = PACK + "resourcepack/assets/tfp_crankshaft/lang/";
    static final String HEADER = DeviceYaml.HEADER + "\nPAR-MECH-21: the crankshaft (dev.tfp.datagen.crankshaft.CrankshaftBlock).";
    public static final String ID = Json.TO_NS + ":crankshaft";
    static final String MODEL_PREFIX = "block/crankshaft/";
    /** PAR-MECH-26: the shaft's connecting box and piston, centred on the connecting point (written from the renderer). */
    public static final String SLIDER_MODEL = MODEL_PREFIX + "slider";
    /** PAR-MECH-26: the shaft's arm, its near end on the connecting point (written from the renderer). */
    public static final String ARM_MODEL = MODEL_PREFIX + "arm";
    /** PAR-MECH-26: the renderer's brass attachment on the piston of a shaft that drives a steel pump. */
    public static final String PUMP_MODEL = MODEL_PREFIX + "pump";
    public static final List<String> FACINGS = List.of("north", "east", "south", "west");
    public static final List<String> PARTS = List.of("base", "shaft");
    static final String RENDER_BASE = Json.TO_NS + ":render/crankshaft/base";
    static final String RENDER_WHEEL = Json.TO_NS + ":render/crankshaft/wheel";
    /** PAR-MECH-26: the display items of the moving parts (the paper side's {@code CrankParts} names the same ids). */
    public static final String RENDER_SLIDER = Json.TO_NS + ":render/crankshaft/slider";
    public static final String RENDER_ARM = Json.TO_NS + ":render/crankshaft/arm";
    public static final String RENDER_PUMP = Json.TO_NS + ":render/crankshaft/pump";
    private static final Pattern STRENGTH = Pattern.compile("\\.strength\\(([0-9.]+)[fF]\\)");
    private static final Pattern TFC_TEXTURE = Pattern.compile("\"(" + Json.FROM_NS + ":block/[a-z0-9_/]+)\"");
    private static final Pattern ROD_TEXTURE = Pattern.compile("ROD_TEXTURE = Helpers\\.identifier\\(\"([a-z0-9_/]+)\"\\)");
    private static final Pattern PUMP_TEXTURE = Pattern.compile("PUMP_TEXTURE = Helpers\\.identifier\\(\"([a-z0-9_/]+)\"\\)");

    private CrankshaftBlock() {
    }

    /** True when the checkout has the crankshaft (the small test fixtures do not). */
    public static boolean available(TfcCheckout tfc) {
        return tfc.exists(BLOCKSTATE) && tfc.exists(BLOCKS_JAVA) && tfc.exists(RENDERER) && tfc.exists(LOOT);
    }

    /** Writes everything. Returns the number of server states. */
    @SuppressWarnings("unchecked")
    public static long run(TfcCheckout tfc, OutputTree out) {
        String hardness = strength(tfc);
        Models models = new Models(tfc, out);
        Map<String, Integer> yRotation = baseRotations(tfc);
        String base = models.copy("block/crankshaft");
        String wheel = models.copy("block/crankshaft_wheel");
        String rod = rodTexture(tfc);
        models.texture(rod);
        out.put(RP + "models/" + SLIDER_MODEL + ".json", Json.write(sliderModel(Json.TO_NS + ":" + rod)));
        out.put(RP + "models/" + ARM_MODEL + ".json", Json.write(armModel(Json.TO_NS + ":" + rod)));
        String brass = texture(tfc, PUMP_TEXTURE, "PUMP_TEXTURE");
        models.texture(brass);
        out.put(RP + "models/" + PUMP_MODEL + ".json", Json.write(pumpModel(Json.TO_NS + ":" + brass)));
        models.itemModel();

        DeviceBlockSpec.Settings settings = DeviceBlockSpec.Settings.of(hardness, hardness, dev.tfp.datagen.device.DeviceSound.METAL,
            "pickaxe");
        DeviceBlockSpec.Builder b = DeviceBlockSpec.builder(ID).settings(settings).deviceBehaviour().blockItem(langKey())
            .property(DeviceProperty.names("facing", FACINGS, "north"))
            .property(DeviceProperty.names("part", PARTS, "base"))
            .renderItem(RENDER_BASE, tfp(base)).renderItem(RENDER_WHEEL, tfp(wheel)).renderItem(RENDER_SLIDER, tfp(SLIDER_MODEL))
            .renderItem(RENDER_ARM, tfp(ARM_MODEL)).renderItem(RENDER_PUMP, tfp(PUMP_MODEL));
        for (String facing : FACINGS) {
            String rot = RotationBlocks.rotation(0, yRotation.get(facing));
            // PAR-MECH-26: the base without its wheel; the wheel, the stub and the shaft are the spinning visuals' displays
            b.appearance(DeviceAppearance.rendered("base_" + facing, DeviceAppearance.Carrier.state(RenderedTemplates.CARRIER),
                List.of(DeviceAppearance.Renderer.item(RENDER_BASE).rotated(rot))));
            b.appearance(DeviceAppearance.vanilla("shaft_" + facing, DeviceAppearance.Carrier.state(RenderedTemplates.CARRIER)));
        }
        b.everyState(s -> s.get("part") + "_" + s.get("facing"), null);
        DeviceBlockSpec spec = b.build();
        Map<String, Object> root = finish(spec, loot(tfc));
        out.put(OUTPUT, Yaml.write(HEADER, root));
        writeLang(tfc, out);
        return spec.stateCount();
    }

    static String langKey() {
        return "block." + Json.TO_NS + ".crankshaft";
    }

    static String tfp(String path) {
        return Json.TO_NS + ":" + path;
    }

    /** TFC's registration: {@code strength(3f)} with {@code SoundType.METAL}. */
    static String strength(TfcCheckout tfc) {
        String src = tfc.readString(BLOCKS_JAVA);
        int at = src.indexOf("register(\"crankshaft\"");
        if (at < 0) {
            throw new DatagenException(BLOCKS_JAVA + ": no crankshaft registration");
        }
        String line = src.substring(at, src.indexOf('\n', at));
        Matcher m = STRENGTH.matcher(line);
        if (!m.find() || !line.contains(".sound(SoundType.METAL)")) {
            throw new DatagenException(BLOCKS_JAVA + ": the crankshaft is no longer strength(x) with SoundType.METAL");
        }
        return m.group(1);
    }

    /** The renderer's rod texture ({@code ROD_TEXTURE}, TFC's steel block). */
    static String rodTexture(TfcCheckout tfc) {
        return texture(tfc, ROD_TEXTURE, "ROD_TEXTURE");
    }

    private static String texture(TfcCheckout tfc, Pattern constant, String name) {
        Matcher m = constant.matcher(tfc.readString(RENDERER));
        if (!m.find()) {
            throw new DatagenException(RENDERER + ": no " + name);
        }
        return m.group(1);
    }

    /** The y turn of each base facing from TFC's blockstate; every shaft variant is {@code tfc:block/empty}. */
    static Map<String, Integer> baseRotations(TfcCheckout tfc) {
        JsonObject variants = Json.parse(tfc.readString(BLOCKSTATE), BLOCKSTATE).getAsJsonObject().getAsJsonObject("variants");
        Map<String, Integer> out = new TreeMap<>();
        boolean shaft = false;
        for (Map.Entry<String, JsonElement> v : variants.entrySet()) {
            JsonObject apply = v.getValue().getAsJsonObject();
            String model = apply.get("model").getAsString();
            if (v.getKey().equals("part=shaft")) {
                if (!model.equals(Json.FROM_NS + ":block/empty")) {
                    throw new DatagenException(BLOCKSTATE + ": the shaft is drawn by " + model);
                }
                shaft = true;
                continue;
            }
            Matcher m = Pattern.compile("facing=([a-z]+),part=base").matcher(v.getKey());
            if (!m.matches() || !FACINGS.contains(m.group(1)) || !model.equals(Json.FROM_NS + ":block/crankshaft")
                || apply.has("x")) {
                throw new DatagenException(BLOCKSTATE + ": unexpected variant " + v.getKey() + " -> " + apply);
            }
            out.put(m.group(1), apply.has("y") ? apply.get("y").getAsInt() : 0);
        }
        if (!shaft || !out.keySet().equals(new TreeSet<>(FACINGS))) {
            throw new DatagenException(BLOCKSTATE + ": variants " + variants.keySet());
        }
        return out;
    }

    /** The renderer's {@code armRadius} (half the arm's thickness) and {@code boxRadius} (the connecting box). */
    static final float ARM_RADIUS = 1 / 16f;
    static final float BOX_RADIUS = 2 / 16f;
    /** The renderer's piston length: {@code PISTON_LENGTH + 1 / 32f} (off the block border against z-fighting). */
    static final float PISTON = CrankshaftRules.PISTON_LENGTH + 1 / 32f;
    /** The model centre: the display turns and moves its item about it, so the connecting point sits there. */
    private static final float[] CENTRE = {8 / 16f, 8 / 16f, 8 / 16f};

    /**
     * PAR-MECH-26: the connecting box ({@code boxRadius}) and the piston ({@code -pistonLength} to {@code armRadius} along z,
     * {@code armRadius} thick) of TFC's renderer, centred on the connecting point. The display puts it at
     * {@code (8/16 + armRadius, 8/16, lengthEH)} of the shaft block, turned by the facing.
     */
    static JsonObject sliderModel(String texture) {
        JsonArray elements = new JsonArray();
        elements.add(cuboid(CENTRE, -BOX_RADIUS, -BOX_RADIUS, -BOX_RADIUS, BOX_RADIUS, BOX_RADIUS, BOX_RADIUS));
        elements.add(cuboid(CENTRE, -ARM_RADIUS, -ARM_RADIUS, -PISTON, ARM_RADIUS, ARM_RADIUS, ARM_RADIUS));
        return rodModel(texture, elements);
    }

    /**
     * PAR-MECH-26: the arm of TFC's renderer ({@code -armRadius} to {@code ARM_LENGTH + armRadius} along z), its pivot on the
     * model centre; the display raises it by {@code raiseAngle} about x there.
     */
    static JsonObject armModel(String texture) {
        JsonArray elements = new JsonArray();
        elements.add(cuboid(CENTRE, -ARM_RADIUS, -ARM_RADIUS, -ARM_RADIUS, ARM_RADIUS, ARM_RADIUS,
            CrankshaftRules.ARM_LENGTH + ARM_RADIUS));
        return rodModel(texture, elements);
    }

    /**
     * PAR-MECH-26: the renderer's pump attachment ({@code 2/16} about the piston's axis, {@code -pistonLength - 3/32} to
     * {@code -pistonLength + 5/32} along z), about the connecting point like the slider it moves with.
     */
    static JsonObject pumpModel(String texture) {
        JsonArray elements = new JsonArray();
        elements.add(cuboid(CENTRE, -2 / 16f, -2 / 16f, -PISTON - 3 / 32f, 2 / 16f, 2 / 16f, -PISTON + 5 / 32f));
        return rodModel(texture, elements);
    }

    private static JsonObject rodModel(String texture, JsonArray elements) {
        JsonObject textures = new JsonObject();
        textures.addProperty("rod", texture);
        textures.addProperty("particle", texture);
        JsonObject model = new JsonObject();
        model.addProperty("parent", "minecraft:block/block");
        model.add("textures", textures);
        model.add("elements", elements);
        return model;
    }

    private static JsonObject cuboid(float[] o, float x0, float y0, float z0, float x1, float y1, float z1) {
        float[] from = {16 * (o[0] + x0), 16 * (o[1] + y0), 16 * (o[2] + z0)};
        float[] to = {16 * (o[0] + x1), 16 * (o[1] + y1), 16 * (o[2] + z1)};
        JsonObject e = new JsonObject();
        e.add("from", vec(from));
        e.add("to", vec(to));
        JsonObject faces = new JsonObject();
        for (String f : List.of("north", "east", "south", "west", "up", "down")) {
            JsonObject face = new JsonObject();
            face.add("uv", uv(f, from, to));
            face.addProperty("texture", "#rod");
            faces.add(f, face);
        }
        e.add("faces", faces);
        return e;
    }

    /** A face's UV: the face's own extent on the 16 x 16 texture (TFC's {@code renderTexturedCuboid} spans the sprite likewise). */
    private static JsonArray uv(String face, float[] from, float[] to) {
        float w;
        float h;
        switch (face) {
            case "north", "south" -> {
                w = to[0] - from[0];
                h = to[1] - from[1];
            }
            case "east", "west" -> {
                w = to[2] - from[2];
                h = to[1] - from[1];
            }
            default -> {
                w = to[0] - from[0];
                h = to[2] - from[2];
            }
        }
        JsonArray a = new JsonArray();
        a.add(num(0));
        a.add(num(0));
        a.add(num(Math.min(16, w)));
        a.add(num(Math.min(16, h)));
        return a;
    }

    private static JsonArray vec(float[] v) {
        JsonArray a = new JsonArray();
        for (float f : v) {
            a.add(num(f));
        }
        return a;
    }

    private static BigDecimal num(float f) {
        BigDecimal d = new BigDecimal(Float.toString(f)).stripTrailingZeros();
        return d.scale() < 0 ? d.setScale(0) : d;
    }

    /**
     * TFC's loot table: two pools, each rolled once under {@code survives_explosion}: the crankshaft for {@code part=base}, a
     * steel rod for {@code part=shaft} (CraftEngine's {@code match_block_property}).
     */
    static List<Object> loot(TfcCheckout tfc) {
        JsonObject root = Json.rename(Json.parse(tfc.readString(LOOT), LOOT)).getAsJsonObject();
        List<Object> pools = new ArrayList<>();
        Set<String> parts = new TreeSet<>();
        for (JsonElement p : root.getAsJsonArray("pools")) {
            JsonObject pool = p.getAsJsonObject();
            JsonArray conditions = pool.getAsJsonArray("conditions");
            JsonArray entries = pool.getAsJsonArray("entries");
            if (pool.get("rolls").getAsInt() != 1 || conditions.size() != 1
                || !"minecraft:survives_explosion".equals(conditions.get(0).getAsJsonObject().get("condition").getAsString())
                || entries.size() != 1) {
                throw new DatagenException(LOOT + ": unexpected pool " + pool);
            }
            JsonObject entry = entries.get(0).getAsJsonObject();
            JsonArray when = entry.getAsJsonArray("conditions");
            if (!"minecraft:item".equals(entry.get("type").getAsString()) || when == null || when.size() != 1) {
                throw new DatagenException(LOOT + ": unexpected entry " + entry);
            }
            JsonObject c = when.get(0).getAsJsonObject();
            if (!"minecraft:block_state_property".equals(c.get("condition").getAsString())
                || !ID.equals(c.get("block").getAsString()) || c.getAsJsonObject("properties").size() != 1) {
                throw new DatagenException(LOOT + ": unexpected condition " + c);
            }
            String part = c.getAsJsonObject("properties").get("part").getAsString();
            parts.add(part);
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("type", "item");
            e.put("item", entry.get("name").getAsString());
            e.put("conditions", List.of(Map.of("type", "match_block_property", "properties", Map.of("part", part))));
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("rolls", 1);
            out.put("conditions", List.of(Map.of("type", "survives_explosion")));
            out.put("entries", List.of(e));
            pools.add(out);
        }
        if (!parts.equals(new TreeSet<>(PARTS))) {
            throw new DatagenException(LOOT + ": parts " + parts);
        }
        return pools;
    }

    /** The device spec's literal YAML with the rendered cube's settings and break events, TFC's loot, view ranges. */
    @SuppressWarnings("unchecked")
    static Map<String, Object> finish(DeviceBlockSpec spec, List<Object> pools) {
        Map<String, Object> root = DeviceYaml.literal(spec);
        Map<String, Object> blocks = (Map<String, Object>) root.get("blocks");
        Map<String, Object> block = (Map<String, Object>) blocks.get(spec.id());
        RenderedCubeSpec cubeSpec = new RenderedCubeSpec(spec.id(), RENDER_BASE, spec.renderItems().get(RENDER_BASE),
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
            for (Object r : (List<Object>) appearance.getOrDefault("entity_renderer", List.of())) {
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

    /** Copies TFC models into {@code tfp:block/crankshaft/**} with their textures. */
    static final class Models {
        private final TfcCheckout tfc;
        private final OutputTree out;

        Models(TfcCheckout tfc, OutputTree out) {
            this.tfc = tfc;
            this.out = out;
        }

        /** TFC {@code block/<name>} as {@code block/crankshaft/<name>}; a vanilla parent stays. */
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

        /** TFC {@code item/crankshaft}: its parent {@code block/crankshaft_with_wheel} copied, then the item model. */
        void itemModel() {
            String src = MODELS + "item/crankshaft.json";
            JsonObject model = Json.parse(tfc.readString(src), src).getAsJsonObject();
            String parent = model.get("parent").getAsString();
            if (!parent.startsWith(Json.FROM_NS + ":block/")) {
                throw new DatagenException(src + ": parent " + parent + " is not a TFC block model");
            }
            model.addProperty("parent", tfp(copy(parent.substring(Json.FROM_NS.length() + 1))));
            model.remove("__comment__");
            out.put(RP + "models/item/crankshaft.json", Json.write(Json.rename(model)));
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
                String ref = m.group(1).substring(Json.FROM_NS.length() + 1);
                if (tfc.exists(TfcCheckout.ASSETS + "textures/" + ref + ".png")) {
                    texture(ref);
                }
            }
        }
    }

    /** {@code block.tfp.crankshaft} from every TFC locale that names it. */
    static void writeLang(TfcCheckout tfc, OutputTree out) {
        String tfcKey = "block." + Json.FROM_NS + ".crankshaft";
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
