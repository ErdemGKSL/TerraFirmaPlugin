/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/TFCBlocks.java @ c3b4d9536 (STEEL_PIPE, STEEL_PUMP),
 *       src/main/java/net/dries007/tfc/common/blocks/rotation/FluidPipeBlock.java @ c3b4d9536 (the six connection properties),
 *       src/main/java/net/dries007/tfc/common/blocks/rotation/FluidPumpBlock.java @ c3b4d9536 (FACING),
 *       src/main/resources/assets/tfc/blockstates/{steel_pipe,steel_pump}.json @ c3b4d9536
 */
package dev.tfp.datagen.pump;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.tfp.datagen.DatagenException;
import dev.tfp.datagen.Json;
import dev.tfp.datagen.OutputTree;
import dev.tfp.datagen.TfcCheckout;
import dev.tfp.datagen.Yaml;
import dev.tfp.datagen.device.DeviceAppearance;
import dev.tfp.datagen.device.DeviceBlockSpec;
import dev.tfp.datagen.device.DeviceLoot;
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
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PAR-MECH-09: TFC's steel pipe and steel pump as CraftEngine blocks, {@code configuration/pump/pipe.yml} and
 * {@code configuration/pump/pump.yml}.
 *
 * <ul>
 *   <li>{@code tfp:steel_pipe}: TFC's six connection booleans plus {@code fluid} ({@code empty} or {@code water}; the
 *       plugin cannot fluid-log a CraftEngine block, so the pipe carries the fluid as a property). One appearance per
 *       connection mask (64): a barrier carrier drawn by TFC's {@code pipe_center} and, for each side, {@code pipe_on} or
 *       {@code pipe_off}, turned as TFC's multipart blockstate turns them.</li>
 *   <li>{@code tfp:steel_pump}: TFC's {@code facing}; one appearance per facing, TFC's {@code pump} model turned as its
 *       variants.</li>
 * </ul>
 * Both are renderer cubes (barrier carrier, full-cube outline: a ledger divergence); the hitbox of TFC's thin pipe is not
 * reproduced.
 */
public final class PumpBlocks {
    static final String BLOCKS_JAVA = "src/main/java/net/dries007/tfc/common/blocks/TFCBlocks.java";
    static final String BLOCKSTATES = TfcCheckout.ASSETS + "blockstates/";
    static final String MODELS = TfcCheckout.ASSETS + "models/";
    static final String LANG = TfcCheckout.ASSETS + "lang/";
    static final String PACK = "craftengine/tfp/";
    public static final String OUTPUT_DIR = PACK + "configuration/pump/";
    static final String RP = PACK + "resourcepack/assets/tfp/";
    static final String LANG_RP = PACK + "resourcepack/assets/tfp_pump/lang/";
    static final String HEADER = DeviceYaml.HEADER + "\nPAR-MECH-09: steel pipe and steel pump (dev.tfp.datagen.pump.PumpBlocks).";
    static final String MODEL_PREFIX = "block/pump/";
    public static final String PIPE = Json.TO_NS + ":steel_pipe";
    public static final String PUMP = Json.TO_NS + ":steel_pump";
    /** The property order of the pipe's connection mask: the appearance name {@code conn_<up><down><north><south><east><west>}. */
    public static final List<String> FACES = List.of("up", "down", "north", "south", "east", "west");
    public static final List<String> FACINGS = List.of("north", "east", "south", "west");
    public static final List<String> FLUIDS = List.of("empty", "water");
    private static final Pattern STRENGTH = Pattern.compile("\\.strength\\(([0-9.]+)[fF]\\)");
    private static final Pattern TFC_TEXTURE = Pattern.compile("\"(" + Json.FROM_NS + ":block/[a-z0-9_/]+)\"");

    private PumpBlocks() {
    }

    /** True when the checkout has the pump blocks (the small test fixtures do not). */
    public static boolean available(TfcCheckout tfc) {
        return tfc.exists(BLOCKSTATES + "steel_pump.json") && tfc.exists(BLOCKSTATES + "steel_pipe.json")
            && tfc.exists(BLOCKS_JAVA);
    }

    /** Writes everything. Returns the number of server states. */
    public static long run(TfcCheckout tfc, OutputTree out) {
        String hardness = strength(tfc);
        Models models = new Models(tfc, out);
        Entry pipe = pipe(tfc, models, hardness);
        out.put(OUTPUT_DIR + "pipe.yml", Yaml.write(HEADER, root(pipe)));
        Entry pump = pump(tfc, models, hardness);
        out.put(OUTPUT_DIR + "pump.yml", Yaml.write(HEADER, root(pump)));
        writeLang(tfc, out);
        return pipe.states() + pump.states();
    }

    private static Map<String, Object> root(Entry e) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("blocks", new LinkedHashMap<>(e.blocks()));
        root.put("items", new LinkedHashMap<>(e.items()));
        return root;
    }

    /** TFC's registrations: both are {@code strength(5f)} with {@code SoundType.METAL}. */
    static String strength(TfcCheckout tfc) {
        String src = tfc.readString(BLOCKS_JAVA);
        String found = null;
        for (String id : List.of("steel_pipe", "steel_pump")) {
            int at = src.indexOf("register(\"" + id + "\"");
            if (at < 0) {
                throw new DatagenException(BLOCKS_JAVA + ": no " + id + " registration");
            }
            String line = src.substring(at, src.indexOf('\n', at));
            Matcher m = STRENGTH.matcher(line);
            if (!m.find() || !line.contains(".sound(SoundType.METAL)")) {
                throw new DatagenException(BLOCKS_JAVA + ": " + id + " is no longer strength(x) with SoundType.METAL");
            }
            if (found != null && !found.equals(m.group(1))) {
                throw new DatagenException(BLOCKS_JAVA + ": the pipe and the pump differ in strength");
            }
            found = m.group(1);
        }
        return found;
    }

    record Entry(Map<String, Object> blocks, Map<String, Object> items, long states) {
    }

    static String langKey(String id) {
        return "block." + id.replace(':', '.').replace('/', '.');
    }

    static String renderItem(String modelPath) {
        return Json.TO_NS + ":render/pump/" + modelPath.substring(MODEL_PREFIX.length());
    }

    static String tfp(String path) {
        return Json.TO_NS + ":" + path;
    }

    static String tfcPath(String model) {
        if (!model.startsWith(Json.FROM_NS + ":")) {
            throw new DatagenException("not a TFC model: " + model);
        }
        return model.substring(Json.FROM_NS.length() + 1);
    }

    /** The pipe's appearance name of a connection mask (bit i is {@link #FACES}{@code [i]}). */
    public static String connName(int mask) {
        StringBuilder sb = new StringBuilder("conn_");
        for (int i = 0; i < FACES.size(); i++) {
            sb.append((mask >> i) & 1);
        }
        return sb.toString();
    }

    static String connOf(Map<String, String> state) {
        int mask = 0;
        for (int i = 0; i < FACES.size(); i++) {
            if ("true".equals(state.get(FACES.get(i)))) {
                mask |= 1 << i;
            }
        }
        return connName(mask);
    }

    static Entry pipe(TfcCheckout tfc, Models models, String hardness) {
        JsonObject blockstate = Json.parse(tfc.readString(BLOCKSTATES + "steel_pipe.json"), "steel_pipe.json").getAsJsonObject();
        // face -> {off part, on part}
        Map<String, JsonObject[]> parts = new LinkedHashMap<>();
        JsonObject center = null;
        for (JsonElement e : blockstate.getAsJsonArray("multipart")) {
            JsonObject part = e.getAsJsonObject();
            if (!part.has("when")) {
                center = part.getAsJsonObject("apply");
                continue;
            }
            Map.Entry<String, JsonElement> when = part.getAsJsonObject("when").entrySet().iterator().next();
            parts.computeIfAbsent(when.getKey(), k -> new JsonObject[2])[when.getValue().getAsBoolean() ? 1 : 0] =
                part.getAsJsonObject("apply");
        }
        if (center == null || !parts.keySet().equals(new TreeSet<>(FACES)) && !new TreeSet<>(parts.keySet()).equals(new TreeSet<>(FACES))) {
            throw new DatagenException("steel_pipe blockstate faces " + parts.keySet());
        }
        DeviceBlockSpec.Settings settings = DeviceBlockSpec.Settings.of(hardness, hardness, DeviceSound.METAL, "pickaxe");
        DeviceBlockSpec.Builder b = DeviceBlockSpec.builder(PIPE).settings(settings).loot(DeviceLoot.fromTfc(tfc, "steel_pipe"))
            .deviceBehaviour().blockItem(langKey(PIPE));
        for (String f : FACES) {
            b.property(DeviceProperty.bool(f, false));
        }
        b.property(DeviceProperty.names("fluid", FLUIDS, "empty"));
        Set<String> rendered = new TreeSet<>();
        String centerModel = models.copy(tfcPath(center.get("model").getAsString()));
        once(b, rendered, centerModel);
        DeviceAppearance.Renderer centerRenderer = renderer(centerModel, center);
        Map<String, DeviceAppearance.Renderer[]> sides = new LinkedHashMap<>();
        for (String face : FACES) {
            DeviceAppearance.Renderer[] r = new DeviceAppearance.Renderer[2];
            for (int i = 0; i < 2; i++) {
                if (parts.get(face)[i] == null) {
                    throw new DatagenException("steel_pipe face " + face + " lacks a " + (i == 1) + " part");
                }
                String model = models.copy(tfcPath(parts.get(face)[i].get("model").getAsString()));
                once(b, rendered, model);
                r[i] = renderer(model, parts.get(face)[i]);
            }
            sides.put(face, r);
        }
        for (int mask = 0; mask < 64; mask++) {
            List<DeviceAppearance.Renderer> list = new ArrayList<>();
            list.add(centerRenderer);
            for (int i = 0; i < FACES.size(); i++) {
                list.add(sides.get(FACES.get(i))[(mask >> i) & 1]);
            }
            b.appearance(DeviceAppearance.rendered(connName(mask), DeviceAppearance.Carrier.state(RenderedTemplates.CARRIER), list));
        }
        b.everyState(PumpBlocks::connOf, null);
        models.itemModel("steel_pipe");
        return finish(b.build());
    }

    static Entry pump(TfcCheckout tfc, Models models, String hardness) {
        JsonObject blockstate = Json.parse(tfc.readString(BLOCKSTATES + "steel_pump.json"), "steel_pump.json").getAsJsonObject();
        JsonObject variants = blockstate.getAsJsonObject("variants");
        DeviceBlockSpec.Settings settings = DeviceBlockSpec.Settings.of(hardness, hardness, DeviceSound.METAL, "pickaxe");
        DeviceBlockSpec.Builder b = DeviceBlockSpec.builder(PUMP).settings(settings).loot(DeviceLoot.fromTfc(tfc, "steel_pump"))
            .deviceBehaviour().blockItem(langKey(PUMP)).property(DeviceProperty.names("facing", FACINGS, "north"));
        Set<String> rendered = new TreeSet<>();
        Set<String> done = new TreeSet<>();
        for (Map.Entry<String, JsonElement> v : variants.entrySet()) {
            String facing = v.getKey().substring("facing=".length());
            if (!v.getKey().startsWith("facing=") || !FACINGS.contains(facing) || !done.add(facing)) {
                throw new DatagenException("unexpected steel_pump variant " + v.getKey());
            }
            JsonObject apply = v.getValue().getAsJsonObject();
            String model = models.copy(tfcPath(apply.get("model").getAsString()));
            once(b, rendered, model);
            b.appearance(DeviceAppearance.rendered(facing, DeviceAppearance.Carrier.state(RenderedTemplates.CARRIER),
                List.of(renderer(model, apply))));
        }
        if (!done.equals(new TreeSet<>(FACINGS))) {
            throw new DatagenException("steel_pump facings " + done);
        }
        b.everyState(s -> s.get("facing"), null);
        models.itemModel("steel_pump");
        return finish(b.build());
    }

    private static DeviceAppearance.Renderer renderer(String model, JsonObject apply) {
        return DeviceAppearance.Renderer.item(renderItem(model)).rotated(RotationBlocks.rotation(intOf(apply, "x"), intOf(apply, "y")));
    }

    private static int intOf(JsonObject o, String key) {
        return o.has(key) ? o.get(key).getAsInt() : 0;
    }

    private static void once(DeviceBlockSpec.Builder b, Set<String> rendered, String model) {
        if (rendered.add(model)) {
            b.renderItem(renderItem(model), tfp(model));
        }
    }

    /** The renderer-cube entry: the device spec's literal YAML with the cube template's settings and events. */
    @SuppressWarnings("unchecked")
    static Entry finish(DeviceBlockSpec spec) {
        Map<String, Object> root = DeviceYaml.literal(spec);
        Map<String, Object> blocks = (Map<String, Object>) root.get("blocks");
        Map<String, Object> block = (Map<String, Object>) blocks.get(spec.id());
        RenderedCubeSpec cubeSpec = new RenderedCubeSpec(spec.id(), spec.renderItems().keySet().iterator().next(),
            spec.renderItems().values().iterator().next(), spec.settings(), false, spec.id(), spec.blockItemLangKey());
        Map<String, Object> expanded = expand(cubeTemplate(), cubeSpec.arguments());
        Map<String, Object> next = new LinkedHashMap<>();
        next.put("settings", expanded.get("settings"));
        if (block.containsKey("loot")) {
            next.put("loot", block.get("loot"));
        }
        next.put("behavior", block.get("behavior"));
        next.put("events", expanded.get("events"));
        next.put("states", block.get("states"));
        blocks.put(spec.id(), next);
        Map<String, Object> appearances = (Map<String, Object>) ((Map<String, Object>) next.get("states")).get("appearances");
        for (Object a : appearances.values()) {
            Map<String, Object> appearance = (Map<String, Object>) a;
            if (RenderedTemplates.CARRIER.equals(appearance.get("state"))) {
                appearance.remove("transparent");
            }
            for (Object r : (List<Object>) appearance.get("entity_renderer")) {
                ((Map<String, Object>) r).put("view_range", new BigDecimal(RenderedTemplates.CUBE_VIEW_RANGE));
            }
        }
        Map<String, Object> items = (Map<String, Object>) root.getOrDefault("items", Map.of());
        return new Entry(blocks, items, spec.stateCount());
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

    /** Copies TFC models into {@code tfp:block/pump/**} with their parents and textures. */
    static final class Models {
        private final TfcCheckout tfc;
        private final OutputTree out;
        private final Set<String> done = new TreeSet<>();

        Models(TfcCheckout tfc, OutputTree out) {
            this.tfc = tfc;
            this.out = out;
        }

        String copy(String path) {
            if (!path.startsWith("block/")) {
                throw new DatagenException("pump model outside block/: " + path);
            }
            String ours = MODEL_PREFIX + path.substring("block/".length());
            if (!done.add(path)) {
                return ours;
            }
            String src = MODELS + path + ".json";
            JsonObject model = Json.parse(tfc.readString(src), src).getAsJsonObject();
            copyTextures(tfc.readString(src));
            if (model.has("parent")) {
                String parent = model.get("parent").getAsString();
                if (parent.startsWith(Json.FROM_NS + ":")) {
                    model.addProperty("parent", tfp(copy(tfcPath(parent))));
                }
            }
            model.remove("__comment__");
            out.put(RP + "models/" + ours + ".json", Json.write(Json.rename(model)));
            return ours;
        }

        void itemModel(String path) {
            String src = MODELS + "item/" + path + ".json";
            JsonObject model = Json.parse(tfc.readString(src), src).getAsJsonObject();
            copyTextures(tfc.readString(src));
            String parent = model.get("parent").getAsString();
            if (!parent.startsWith(Json.FROM_NS + ":block/")) {
                throw new DatagenException(src + ": parent " + parent + " is not a TFC block model");
            }
            model.addProperty("parent", tfp(copy(tfcPath(parent))));
            model.remove("__comment__");
            out.put(RP + "models/item/" + path + ".json", Json.write(Json.rename(model)));
        }

        private void copyTextures(String json) {
            Matcher m = TFC_TEXTURE.matcher(json);
            while (m.find()) {
                String ref = tfcPath(m.group(1));
                String png = TfcCheckout.ASSETS + "textures/" + ref + ".png";
                if (!tfc.exists(png)) {
                    continue;
                }
                out.put(RP + "textures/" + ref + ".png", tfc.read(png));
                if (tfc.exists(png + ".mcmeta")) {
                    out.put(RP + "textures/" + ref + ".png.mcmeta", tfc.read(png + ".mcmeta"));
                }
            }
        }
    }

    /** {@code block.tfp.steel_pipe} and {@code block.tfp.steel_pump} from every TFC locale that names them. */
    static void writeLang(TfcCheckout tfc, OutputTree out) {
        List<String> ids = List.of(PIPE, PUMP);
        for (String f : tfc.list(LANG)) {
            if (!f.endsWith(".json")) {
                continue;
            }
            String locale = f.substring(LANG.length(), f.length() - ".json".length());
            JsonObject src = Json.parse(tfc.readString(f), f).getAsJsonObject();
            Map<String, String> keys = new TreeMap<>();
            for (String id : ids) {
                String tfcKey = langKey(id).replace("block." + Json.TO_NS + ".", "block." + Json.FROM_NS + ".");
                if (src.has(tfcKey)) {
                    keys.put(langKey(id), src.get(tfcKey).getAsString());
                } else if (locale.equals("en_us")) {
                    throw new DatagenException("TFC has no English name " + tfcKey);
                }
            }
            if (keys.isEmpty()) {
                continue;
            }
            JsonObject o = new JsonObject();
            keys.forEach(o::addProperty);
            out.put(LANG_RP + locale + ".json", Json.write(o));
        }
    }
}
