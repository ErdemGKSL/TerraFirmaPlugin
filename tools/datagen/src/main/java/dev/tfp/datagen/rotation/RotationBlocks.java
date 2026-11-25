/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/wood/Wood.java @ c3b4d9536 (AXLE, ENCASED_AXLE, CLUTCH, GEAR_BOX, BLADED_AXLE
 *       properties),
 *       src/main/java/net/dries007/tfc/common/blocks/TFCBlocks.java @ c3b4d9536 (CREATIVE_MOTOR),
 *       src/main/resources/assets/tfc/blockstates/wood/{axle,encased_axle,clutch,gear_box,bladed_axle}/*.json @ c3b4d9536,
 *       src/main/resources/assets/tfc/models/block/bladed_axle.json @ c3b4d9536 (the shaft and blade model, PAR-MECH-20),
 *       src/main/resources/assets/tfc/blockstates/creative_motor.json @ c3b4d9536
 */
package dev.tfp.datagen.rotation;

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
import dev.tfp.datagen.hitbox.HitboxAllocation;
import dev.tfp.datagen.hitbox.HitboxDemands;
import dev.tfp.datagen.rendered.RenderedCubeSpec;
import dev.tfp.datagen.rendered.RenderedTemplates;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
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
 * PAR-MECH-01: TFC's rotation network blocks as CraftEngine blocks, one file per block type under
 * {@code configuration/rotation/}: {@code tfp:wood/axle/<wood>}, {@code tfp:wood/encased_axle/<wood>},
 * {@code tfp:wood/clutch/<wood>}, {@code tfp:wood/gear_box/<wood>} (TFC's 20 woods each) and {@code tfp:creative_motor}.
 *
 * <ul>
 *   <li>Every block carries the {@code tfp:device} behaviour (the plugin's {@code dev.tfp.paper.rotation} kinds) and TFC's
 *       state properties: {@code axis} (axle, encased axle, motor), {@code axis} + {@code powered} (clutch), the six face
 *       booleans (gearbox).</li>
 *   <li>The full-cube blocks (encased axle, clutch, gearbox, motor) are renderer cubes (PAR-INFRA-17, NP-VIS-02): the
 *       {@code tfp:block/rendered_cube} template expanded with TFC's settings (so the sounds keep the SoundType volume and
 *       pitch, the {@code destroy_stages} cracks and the break particles), with {@code states} in place of the template's one
 *       {@code state}: CraftEngine templates cannot drop a key, so the entry is written expanded. Each state's barrier
 *       carrier is drawn by item displays of TFC's models, turned as TFC's blockstate turns them ({@link #rotation}).</li>
 *   <li>The axle is a transparent carrier from the hitbox allocation ({@link #axleCarrier}, PAR-MECH-15) with no display
 *       element: the plugin's spinning display draws TFC's shaft model (its render item), also at rest (PAR-MECH-12).</li>
 *   <li>PAR-MECH-20: {@code tfp:wood/bladed_axle/<wood>} is the same carrier; its render item is TFC's bladed axle model (the
 *       shaft and the steel blade).</li>
 * </ul>
 * TFC's models (renamed under {@code tfp:block/rotation/}), the textures they name, TFC's item models and the block names
 * ({@code assets/tfp_rotation/lang/}) are written beside the entries.
 */
public final class RotationBlocks {
    static final String WOOD_JAVA = "src/main/java/net/dries007/tfc/common/blocks/wood/Wood.java";
    static final String BLOCKS_JAVA = "src/main/java/net/dries007/tfc/common/blocks/TFCBlocks.java";
    static final String BLOCKSTATES = TfcCheckout.ASSETS + "blockstates/";
    static final String MODELS = TfcCheckout.ASSETS + "models/";
    static final String LANG = TfcCheckout.ASSETS + "lang/";
    static final String PACK = "craftengine/tfp/";
    public static final String OUTPUT_DIR = PACK + "configuration/rotation/";
    static final String RP = PACK + "resourcepack/assets/tfp/";
    static final String LANG_RP = PACK + "resourcepack/assets/tfp_rotation/lang/";
    static final String HEADER = DeviceYaml.HEADER + "\nPAR-MECH-01: rotation network blocks (dev.tfp.datagen.rotation.RotationBlocks).";
    /** The model namespace of our copies of TFC's models: {@code tfc:block/x} becomes {@code tfp:block/rotation/x}. */
    static final String MODEL_PREFIX = "block/rotation/";
    public static final String MOTOR = Json.TO_NS + ":creative_motor";
    public static final List<String> TYPES = List.of("axle", "encased_axle", "clutch", "gear_box", "bladed_axle");
    /** TFC {@code GearBoxBlock.createBlockStateDefinition}: {@code builder.add(UP, DOWN, NORTH, SOUTH, EAST, WEST)}. */
    public static final List<String> FACES = List.of("up", "down", "north", "south", "east", "west");
    /**
     * PAR-MECH-15: the axle's carrier along {@code axis}, from the hitbox allocation ({@code HitboxDemands.AXLE_ID}, TFC's
     * shaft outline {@code AbstractShaftAxleBlock.SHAPE_X/Y/Z}); every wood shares it. The best-scoring states, the
     * lightning rods, keep their vanilla look (a crafted block, PAR-CLIM-27), so the shaft is hitbox-only on the best free
     * transparent carrier (ledger divergence).
     */
    public static HitboxAllocation.Assignment axleCarrier(String axis) {
        HitboxAllocation.Assignment a = HitboxAllocation.agreed(HitboxDemands.AXLE_ID, axis);
        if (a.mode() != HitboxAllocation.Mode.HITBOX_ONLY) {
            throw new DatagenException("axle " + axis + " got a " + a.mode() + " carrier; RotationBlocks draws an item display");
        }
        if (a.hideTarget() != null) {
            throw new DatagenException("axle carrier " + a.state() + " needs a hide mapping to " + a.hideTarget()
                + " (emit it once for the 20 axle blocks)");
        }
        return a;
    }

    /** TFC's AXLE registration ({@code noOcclusion().strength(2.5F)}). */
    private static final Pattern WOOD_TYPE = Pattern.compile(
        "\\n\\s*([A-Z_]+)\\(\\(self, wood\\) -> new (\\w+)\\(properties\\(wood\\)([^;]*?)\\.blockEntity\\(TFCBlockEntities\\.([A-Z_]+)\\)");
    private static final Pattern STRENGTH = Pattern.compile("\\.strength\\(([0-9.]+)[fF]\\)");
    private static final Pattern TFC_TEXTURE = Pattern.compile("\"(" + Json.FROM_NS + ":block/[a-z0-9_/]+)\"");

    private RotationBlocks() {
    }

    /** True when the checkout has the rotation blocks (the small test fixtures do not). */
    public static boolean available(TfcCheckout tfc) {
        return tfc.exists(BLOCKSTATES + "creative_motor.json") && tfc.isDirectory(BLOCKSTATES + "wood/gear_box");
    }

    /** Writes everything. Returns the number of server states. */
    public static long run(TfcCheckout tfc, OutputTree out) {
        List<String> woods = woods(tfc);
        Map<String, String> strengths = strengths(tfc);
        Models models = new Models(tfc, out);
        long states = 0;
        Map<String, Object> blocks = new LinkedHashMap<>();
        Map<String, Object> items = new LinkedHashMap<>();
        for (String type : TYPES) {
            blocks.clear();
            items.clear();
            for (String wood : woods) {
                Entry e = entry(tfc, models, type, wood, strengths.get(type));
                blocks.putAll(e.blocks());
                items.putAll(e.items());
                states += e.states();
            }
            out.put(OUTPUT_DIR + type + ".yml", Yaml.write(HEADER, root(blocks, items)));
        }
        Entry motor = motor(tfc, models);
        out.put(OUTPUT_DIR + "creative_motor.yml", Yaml.write(HEADER, root(motor.blocks(), motor.items())));
        states += motor.states();
        writeLang(tfc, out, woods);
        return states;
    }

    private static Map<String, Object> root(Map<String, Object> blocks, Map<String, Object> items) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("blocks", new LinkedHashMap<>(blocks));
        root.put("items", new LinkedHashMap<>(items));
        return root;
    }

    /** The woods that have every rotation block, from TFC's blockstate folders. */
    static List<String> woods(TfcCheckout tfc) {
        Set<String> woods = null;
        for (String type : TYPES) {
            Set<String> here = new TreeSet<>();
            for (String f : tfc.list(BLOCKSTATES + "wood/" + type + "/")) {
                if (f.endsWith(".json")) {
                    here.add(f.substring(f.lastIndexOf('/') + 1, f.length() - ".json".length()));
                }
            }
            if (woods == null) {
                woods = here;
            } else if (!woods.equals(here)) {
                throw new DatagenException("TFC's " + type + " woods " + here + " differ from " + woods);
            }
        }
        if (woods == null || woods.isEmpty()) {
            throw new DatagenException("no TFC rotation blockstates");
        }
        return List.copyOf(woods);
    }

    /** {@code type -> hardness} of TFC's {@code Wood.BlockType} registrations; each must keep {@code SoundType.WOOD}. */
    static Map<String, String> strengths(TfcCheckout tfc) {
        String src = tfc.readString(WOOD_JAVA);
        if (!src.contains("ExtendedProperties.of(wood.woodColor()).sound(SoundType.WOOD)")) {
            throw new DatagenException(WOOD_JAVA + ": the wood block properties no longer use SoundType.WOOD");
        }
        Map<String, String> out = new TreeMap<>();
        Matcher m = WOOD_TYPE.matcher(src);
        while (m.find()) {
            String type = m.group(1).toLowerCase(Locale.ROOT);
            if (!TYPES.contains(type)) {
                continue;
            }
            Matcher st = STRENGTH.matcher(m.group(3));
            if (!st.find()) {
                throw new DatagenException(WOOD_JAVA + ": " + m.group(1) + " has no one-argument strength");
            }
            out.put(type, st.group(1));
        }
        if (!out.keySet().equals(new TreeSet<>(TYPES))) {
            throw new DatagenException(WOOD_JAVA + ": found strengths of " + out.keySet() + ", need " + TYPES);
        }
        return out;
    }

    /** One generated block entry and its items. */
    record Entry(Map<String, Object> blocks, Map<String, Object> items, long states) {
    }

    static String blockId(String type, String wood) {
        return Json.TO_NS + ":wood/" + type + "/" + wood;
    }

    static String langKey(String id) {
        return "block." + id.replace(':', '.').replace('/', '.');
    }

    /** {@code tfp:render/rotation/<model path>}: the display-only item of one of our model copies. */
    static String renderItem(String modelPath) {
        if (!modelPath.startsWith(MODEL_PREFIX)) {
            throw new DatagenException("not one of our rotation models: " + modelPath);
        }
        return Json.TO_NS + ":render/rotation/" + modelPath.substring(MODEL_PREFIX.length());
    }

    static Entry entry(TfcCheckout tfc, Models models, String type, String wood, String hardness) {
        String id = blockId(type, wood);
        DeviceBlockSpec.Settings settings = DeviceBlockSpec.Settings.of(hardness, hardness, DeviceSound.WOOD,
            tool(tfc, "wood/" + type + "/" + wood));
        DeviceBlockSpec.Builder b = DeviceBlockSpec.builder(id).settings(settings)
            .loot(DeviceLoot.fromTfc(tfc, "wood/" + type + "/" + wood)).deviceBehaviour().blockItem(langKey(id));
        JsonObject blockstate = blockstate(tfc, "wood/" + type + "/" + wood);
        switch (type) {
            case "axle", "bladed_axle" -> {
                // TFC draws the axle with its block entity renderer (the blockstate is tfc:block/empty); the shaft model
                // is tfc:block/wood/axle/<wood> along z, turned to x and y as AxleBlockEntityRenderer does.
                // PAR-MECH-20: the bladed axle (an AbstractShaftAxleBlock, the axle's outline) draws TFC's own
                // tfc:block/wood/bladed_axle/<wood> model, the shaft plus the steel blade cuboid, turning as one.
                String model = models.copy("block/wood/" + type + "/" + wood);
                b.property(DeviceProperty.names("axis", List.of("x", "y", "z"), "x")).renderItem(renderItem(model), tfp(model));
                for (String axis : List.of("x", "y", "z")) {
                    int[] xy = switch (axis) {
                        case "x" -> new int[] {0, 90};
                        case "y" -> new int[] {90, 0};
                        default -> new int[] {0, 0};
                    };
                    b.appearance(DeviceAppearance.rendered(axis, DeviceAppearance.Carrier.state(axleCarrier(axis).state()),
                        List.of(DeviceAppearance.Renderer.item(renderItem(model)).rotated(rotation(xy[0], xy[1])))));
                }
                b.everyState(s -> s.get("axis"), null);
            }
            case "encased_axle" -> {
                b.property(DeviceProperty.names("axis", List.of("x", "y", "z"), "y"));
                variantCubes(b, models, blockstate, s -> "axis=" + s.get("axis"));
                b.everyState(s -> s.get("axis"), null);
            }
            case "clutch" -> {
                b.property(DeviceProperty.names("axis", List.of("x", "y", "z"), "y")).property(DeviceProperty.bool("powered", false));
                variantCubes(b, models, blockstate, s -> "axis=" + s.get("axis") + ",powered=" + s.get("powered"));
                b.everyState(s -> s.get("axis") + ("true".equals(s.get("powered")) ? "_powered" : ""), null);
            }
            case "gear_box" -> {
                for (String f : FACES) {
                    b.property(DeviceProperty.bool(f, false));
                }
                gearBox(b, models, blockstate);
                b.everyState(RotationBlocks::gearBoxAppearance, null);
            }
            default -> throw new DatagenException("rotation type " + type);
        }
        models.itemModel("wood/" + type + "/" + wood);
        DeviceBlockSpec spec = b.build();
        boolean cube = !isShaft(type);
        Entry e = finish(spec, cube, false);
        if (!cube) {
            withoutShaftDisplays(e, spec.id());
        }
        return e;
    }

    /** The axle and the bladed axle: transparent shaft carriers drawn by the spinning display. */
    static boolean isShaft(String type) {
        return type.equals("axle") || type.equals("bladed_axle");
    }

    /**
     * PAR-MECH-12: the axle's appearances keep only the transparent carrier (TFC's outline); the shaft is drawn by the plugin's
     * spinning display ({@code dev.tfp.paper.rotation.spin}), also at rest, so a static element would draw it twice. The
     * render item stays declared: the spinning display shows it.
     */
    @SuppressWarnings("unchecked")
    static void withoutShaftDisplays(Entry e, String id) {
        Map<String, Object> block = (Map<String, Object>) e.blocks().get(id);
        Map<String, Object> appearances = (Map<String, Object>) ((Map<String, Object>) block.get("states")).get("appearances");
        for (Object a : appearances.values()) {
            ((Map<String, Object>) a).remove("entity_renderer");
        }
    }

    static Entry motor(TfcCheckout tfc, Models models) {
        String src = tfc.readString(BLOCKS_JAVA);
        String expect = "register(\"creative_motor\", () -> new CreativeRotationBlock(ExtendedProperties.of(Blocks.IRON_BLOCK).noLootTable()";
        if (!src.contains(expect)) {
            throw new DatagenException(BLOCKS_JAVA + ": the creative motor is no longer an iron block without loot");
        }
        // Blocks.IRON_BLOCK: strength(5.0F, 6.0F), SoundType.METAL, requiresCorrectToolForDrops (vanilla Blocks.java).
        DeviceBlockSpec.Settings settings = DeviceBlockSpec.Settings.of("5.0", "6.0", DeviceSound.METAL,
            tool(tfc, "creative_motor"));
        DeviceBlockSpec.Builder b = DeviceBlockSpec.builder(MOTOR).settings(settings).deviceBehaviour().blockItem(langKey(MOTOR))
            .property(DeviceProperty.names("axis", List.of("x", "y", "z"), "y"));
        variantCubes(b, models, blockstate(tfc, "creative_motor"), s -> "axis=" + s.get("axis"));
        b.everyState(s -> s.get("axis"), null);
        models.itemModel("creative_motor");
        return finish(b.build(), true, true);
    }

    /** Adds the render item of {@code model} once per block. */
    private static void renderOnce(DeviceBlockSpec.Builder b, Set<String> rendered, String model) {
        if (rendered.add(model)) {
            b.renderItem(renderItem(model), tfp(model));
        }
    }

    /** The {@code tfp:block/rendered_cube} template body as {@link RenderedTemplates} writes it. */
    @SuppressWarnings("unchecked")
    static Map<String, Object> cubeTemplate() {
        Map<String, Object> templates = (Map<String, Object>) RenderedTemplates.yaml().get("templates#rendered_block");
        return (Map<String, Object>) templates.get(RenderedTemplates.CUBE);
    }

    /** The one {@code minecraft:mineable/<tool>} tag of TFC's generated data that holds {@code tfc:<block>}. */
    static String tool(TfcCheckout tfc, String block) {
        String found = null;
        for (String t : List.of("axe", "hoe", "pickaxe", "shovel")) {
            String src = TfcCheckout.GENERATED + "data/minecraft/tags/block/mineable/" + t + ".json";
            for (JsonElement v : Json.parse(tfc.readString(src), src).getAsJsonObject().getAsJsonArray("values")) {
                if (v.isJsonPrimitive() && v.getAsString().equals(Json.FROM_NS + ":" + block)) {
                    if (found != null) {
                        throw new DatagenException("tfc:" + block + " is in two mineable tags: " + found + ", " + t);
                    }
                    found = t;
                }
            }
        }
        if (found == null) {
            throw new DatagenException("tfc:" + block + " is in no minecraft:mineable tag");
        }
        return found;
    }

    /** The TFC blockstate JSON of {@code tfc:<path>}. */
    static JsonObject blockstate(TfcCheckout tfc, String path) {
        String src = BLOCKSTATES + path + ".json";
        return Json.parse(tfc.readString(src), src).getAsJsonObject();
    }

    /** One appearance per TFC variant key: a barrier carrier drawn by the variant's model, turned by its x and y. */
    private static void variantCubes(DeviceBlockSpec.Builder b, Models models, JsonObject blockstate,
                                     java.util.function.Function<Map<String, String>, String> key) {
        Set<String> rendered = new TreeSet<>();
        JsonObject variants = blockstate.getAsJsonObject("variants");
        if (variants == null) {
            throw new DatagenException("rotation blockstate without variants: " + blockstate);
        }
        Set<String> done = new TreeSet<>();
        for (Map.Entry<String, JsonElement> v : variants.entrySet()) {
            Map<String, String> props = new TreeMap<>();
            for (String kv : v.getKey().split(",")) {
                String[] p = kv.split("=", 2);
                props.put(p[0], p[1]);
            }
            String name = props.get("axis") + ("true".equals(props.get("powered")) ? "_powered" : "");
            if (!key.apply(props).equals(v.getKey()) || !done.add(name)) {
                throw new DatagenException("unexpected rotation blockstate variant " + v.getKey());
            }
            JsonObject apply = v.getValue().getAsJsonObject();
            String model = models.copy(tfcPath(apply.get("model").getAsString()));
            renderOnce(b, rendered, model);
            b.appearance(DeviceAppearance.rendered(name, DeviceAppearance.Carrier.state(RenderedTemplates.CARRIER),
                List.of(DeviceAppearance.Renderer.item(renderItem(model)).rotated(rotation(intOf(apply, "x"), intOf(apply, "y"))))));
        }
    }

    /** The gearbox: one appearance per face combination, each drawing TFC's six multipart faces (port or plain). */
    private static void gearBox(DeviceBlockSpec.Builder b, Models models, JsonObject blockstate) {
        Set<String> rendered = new TreeSet<>();
        Map<String, JsonObject[]> parts = new LinkedHashMap<>();
        for (JsonElement e : blockstate.getAsJsonArray("multipart")) {
            JsonObject part = e.getAsJsonObject();
            Map.Entry<String, JsonElement> when = part.getAsJsonObject("when").entrySet().iterator().next();
            JsonObject[] pair = parts.computeIfAbsent(when.getKey(), k -> new JsonObject[2]);
            pair[when.getValue().getAsBoolean() ? 1 : 0] = part.getAsJsonObject("apply");
        }
        if (!parts.keySet().equals(new TreeSet<>(FACES)) && !new TreeSet<>(parts.keySet()).equals(new TreeSet<>(FACES))) {
            throw new DatagenException("gearbox multipart faces " + parts.keySet());
        }
        Map<String, DeviceAppearance.Renderer[]> renderers = new LinkedHashMap<>();
        for (String face : FACES) {
            JsonObject[] pair = parts.get(face);
            DeviceAppearance.Renderer[] r = new DeviceAppearance.Renderer[2];
            for (int i = 0; i < 2; i++) {
                if (pair[i] == null) {
                    throw new DatagenException("gearbox face " + face + " lacks a " + (i == 1) + " part");
                }
                String model = models.copy(tfcPath(pair[i].get("model").getAsString()));
                renderOnce(b, rendered, model);
                r[i] = DeviceAppearance.Renderer.item(renderItem(model)).rotated(rotation(intOf(pair[i], "x"), intOf(pair[i], "y")));
            }
            renderers.put(face, r);
        }
        for (int mask = 0; mask < 64; mask++) {
            List<DeviceAppearance.Renderer> list = new ArrayList<>();
            for (int i = 0; i < FACES.size(); i++) {
                list.add(renderers.get(FACES.get(i))[(mask >> i) & 1]);
            }
            b.appearance(DeviceAppearance.rendered(gearBoxName(mask), DeviceAppearance.Carrier.state(RenderedTemplates.CARRIER), list));
        }
    }

    static String gearBoxName(int mask) {
        StringBuilder sb = new StringBuilder("faces_");
        for (int i = 0; i < FACES.size(); i++) {
            sb.append((mask >> i) & 1);
        }
        return sb.toString();
    }

    static String gearBoxAppearance(Map<String, String> state) {
        int mask = 0;
        for (int i = 0; i < FACES.size(); i++) {
            if ("true".equals(state.get(FACES.get(i)))) {
                mask |= 1 << i;
            }
        }
        return gearBoxName(mask);
    }

    private static int intOf(JsonObject o, String key) {
        return o.has(key) ? o.get(key).getAsInt() : 0;
    }

    /** {@code tfc:block/wood/axle/oak} -> {@code block/wood/axle/oak}. */
    static String tfcPath(String model) {
        if (!model.startsWith(Json.FROM_NS + ":")) {
            throw new DatagenException("not a TFC model: " + model);
        }
        return model.substring(Json.FROM_NS.length() + 1);
    }

    static String tfp(String path) {
        return Json.TO_NS + ":" + path;
    }

    /**
     * The display rotation that draws an item model as TFC's blockstate draws the block model with rotation (x, y).
     * Vanilla turns a variant model by {@code rotateYXZ(-y, -x, 0)} about the block centre; an item display draws its
     * item turned half a turn about y against the block model (PAR-METAL-20, the log pile's '0,180,0'), so that half turn
     * is undone last. Written as a quaternion {@code x,y,z,w} so CraftEngine's Euler order does not matter.
     */
    public static String rotation(int xDegrees, int yDegrees) {
        double[] q = mul(mul(axis(0, 1, 0, -yDegrees), axis(1, 0, 0, -xDegrees)), axis(0, 1, 0, 180));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            BigDecimal v = BigDecimal.valueOf(q[i]).setScale(6, RoundingMode.HALF_EVEN).stripTrailingZeros();
            if (v.signum() == 0) {
                v = BigDecimal.ZERO;
            }
            sb.append(i == 0 ? "" : ",").append(v.toPlainString());
        }
        return sb.toString();
    }

    /** A rotation of {@code degrees} about a unit axis, {x, y, z, w}. */
    static double[] axis(double ax, double ay, double az, double degrees) {
        double h = Math.toRadians(degrees) / 2;
        double s = Math.sin(h);
        return new double[] {ax * s, ay * s, az * s, Math.cos(h)};
    }

    /** The Hamilton product {@code a * b} (b applied first). */
    static double[] mul(double[] a, double[] b) {
        return new double[] {
            a[3] * b[0] + a[0] * b[3] + a[1] * b[2] - a[2] * b[1],
            a[3] * b[1] - a[0] * b[2] + a[1] * b[3] + a[2] * b[0],
            a[3] * b[2] + a[0] * b[1] - a[1] * b[0] + a[2] * b[3],
            a[3] * b[3] - a[0] * b[0] - a[1] * b[1] - a[2] * b[2]};
    }

    /**
     * The block entry: the device spec's literal YAML; for a renderer cube its settings and events are the expanded
     * {@code tfp:block/rendered_cube} template with this block's arguments, and every display gets the cube's view range.
     */
    @SuppressWarnings("unchecked")
    static Entry finish(DeviceBlockSpec spec, boolean cube, boolean requiresCorrectTool) {
        Map<String, Object> root = DeviceYaml.literal(spec);
        Map<String, Object> blocks = (Map<String, Object>) root.get("blocks");
        Map<String, Object> block = (Map<String, Object>) blocks.get(spec.id());
        if (cube) {
            RenderedCubeSpec cubeSpec = new RenderedCubeSpec(spec.id(), spec.renderItems().keySet().iterator().next(),
                spec.renderItems().values().iterator().next(), spec.settings(), requiresCorrectTool, spec.id(),
                spec.blockItemLangKey());
            Map<String, Object> expanded = expand(cubeTemplate(), cubeSpec.arguments());
            Map<String, Object> next = new LinkedHashMap<>();
            Map<String, Object> settings = new LinkedHashMap<>((Map<String, Object>) expanded.get("settings"));
            if (requiresCorrectTool) {
                settings.put("correct_tools", List.of("#minecraft:" + cubeSpec.tool() + "s", "#tfp:tools/" + cubeSpec.tool()));
            }
            next.put("settings", settings);
            if (block.containsKey("loot")) {
                next.put("loot", block.get("loot"));
            }
            next.put("behavior", block.get("behavior"));
            next.put("events", expanded.get("events"));
            next.put("states", block.get("states"));
            block = next;
            blocks.put(spec.id(), block);
        }
        Map<String, Object> appearances = (Map<String, Object>) ((Map<String, Object>) block.get("states")).get("appearances");
        for (Object a : appearances.values()) {
            Map<String, Object> appearance = (Map<String, Object>) a;
            if (RenderedTemplates.CARRIER.equals(appearance.get("state"))) {
                appearance.remove("transparent"); // as tfp:block/rendered_cube: the barrier has no model to hide
            }
            for (Object r : (List<Object>) ((Map<String, Object>) a).get("entity_renderer")) {
                ((Map<String, Object>) r).put("view_range", new BigDecimal(RenderedTemplates.CUBE_VIEW_RANGE));
            }
        }
        Map<String, Object> items = (Map<String, Object>) root.getOrDefault("items", Map.of());
        return new Entry(blocks, items, spec.stateCount());
    }

    /** The template body with every {@code ${name}} and {@code ${name:-default}} replaced; a lone placeholder keeps its value's type. */
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

    /** Copies TFC models into {@code tfp:block/rotation/**} with their parents and textures. */
    static final class Models {
        private final TfcCheckout tfc;
        private final OutputTree out;
        private final Set<String> done = new TreeSet<>();

        Models(TfcCheckout tfc, OutputTree out) {
            this.tfc = tfc;
            this.out = out;
        }

        /**
         * Copies TFC's model {@code path} ({@code block/...}) and its TFC parents; returns our model path
         * ({@code block/rotation/...}). Texture references keep TFC's paths in our namespace and are copied too.
         */
        String copy(String path) {
            if (!path.startsWith("block/")) {
                throw new DatagenException("rotation model outside block/: " + path);
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

        /** TFC's item model of {@code tfc:<path>} (a block model parent), as {@code tfp:item/<path>}. */
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
                    continue; // a model reference (parent), not a texture
                }
                out.put(RP + "textures/" + ref + ".png", tfc.read(png));
                if (tfc.exists(png + ".mcmeta")) {
                    out.put(RP + "textures/" + ref + ".png.mcmeta", tfc.read(png + ".mcmeta"));
                }
            }
        }
    }

    /** {@code block.tfp.wood.<type>.<wood>} and {@code block.tfp.creative_motor} from every TFC locale that names them. */
    static void writeLang(TfcCheckout tfc, OutputTree out, List<String> woods) {
        List<String> ids = new ArrayList<>();
        for (String type : TYPES) {
            for (String wood : woods) {
                ids.add(blockId(type, wood));
            }
        }
        ids.add(MOTOR);
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
