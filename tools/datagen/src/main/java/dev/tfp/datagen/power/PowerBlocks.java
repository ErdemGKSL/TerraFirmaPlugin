/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/wood/Wood.java @ c3b4d9536 (WINDMILL, WATER_WHEEL: strength 9,
 *       SoundType.WOOD, waterWheelTexture),
 *       src/main/java/net/dries007/tfc/common/blocks/rotation/WaterWheelBlock.java @ c3b4d9536 (AXIS, default x),
 *       src/main/java/net/dries007/tfc/common/blocks/rotation/WindmillBlock.java @ c3b4d9536 (AXIS, COUNT 1..5, default 1),
 *       src/main/java/net/dries007/tfc/common/items/TFCItems.java @ c3b4d9536 (WINDMILL_BLADES, LATTICE_/RUSTIC_WINDMILL_BLADE),
 *       src/main/java/net/dries007/tfc/client/render/blockentity/WaterWheelBlockEntityRenderer.java @ c3b4d9536
 *       (translate(0.5, -0.5, 0.5), YN 90 for axis z),
 *       src/main/java/net/dries007/tfc/client/render/blockentity/WindmillBlockEntityRenderer.java @ c3b4d9536 (the blade frame
 *       XN 90 / YN 90 + XN 90, translate(0.5, -2 or -1, 0.5), blade i at 2 pi / count * i, the default blade texture),
 *       src/main/resources/assets/tfc/models/item/{wood/water_wheel/*,windmill_blade/*,lattice_windmill_blade,
 *       rustic_windmill_blade}.json @ c3b4d9536,
 *       src/main/resources/assets/minecraft/atlases/blocks.json @ c3b4d9536 (the water wheel icon's paletted_permutations),
 *       src/main/resources/data/tfc/loot_table/blocks/wood/{water_wheel,windmill}/*.json @ c3b4d9536
 */
package dev.tfp.datagen.power;

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
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;

/**
 * PAR-MECH-18: TFC's water wheel and windmill as CraftEngine blocks, and the windmill blade items, under
 * {@code configuration/power/}.
 *
 * <ul>
 *   <li>{@code tfp:wood/water_wheel/<wood>}: TFC's {@code axis} (CraftEngine's {@code axis} type: a two-value string property
 *       named axis crashes CraftEngine's placement, PAR-MECH-24; the device turns a y placement to the player's horizontal
 *       axis), the {@code tfp:device} behaviour (the waterwheel module's kind), TFC's settings and loot. A barrier carrier
 *       (TFC's outline {@code box(0, 0.125, 0, 16, 15.875, 16)} is all but a full cube) expanded from
 *       {@code tfp:block/rendered_cube}, drawn by an item display of TFC's {@code WaterWheelModel} baked into an item model
 *       ({@link EntityModelBaker}) with the wood's {@code textures/entity/water_wheel/<wood>} texture.</li>
 *   <li>{@code tfp:wood/windmill/<wood>}: {@code axis} and {@code count} 1..5, the axle's transparent carrier (TFC's outline
 *       is the axle's), the device behaviour, TFC's loot (the axle; the blades drop from the device inventory). It is drawn by
 *       the axle's shaft and {@code count} default blades ({@code WindmillBladeModel}, white) evenly spaced as TFC's renderer
 *       spaces them, at rest: the spinning and the per-slot blade kind need the spin service (follow-up item).</li>
 *   <li>The blade items {@code tfp:windmill_blade/<colour>}, {@code tfp:lattice_windmill_blade},
 *       {@code tfp:rustic_windmill_blade} with TFC's item models; the water wheel's item; the names from every TFC locale.</li>
 * </ul>
 */
public final class PowerBlocks {
    static final String WOOD_JAVA = "src/main/java/net/dries007/tfc/common/blocks/wood/Wood.java";
    static final String MODEL_JAVA = "src/main/java/net/dries007/tfc/client/model/entity/";
    static final String WHEEL_MODEL = MODEL_JAVA + "WaterWheelModel.java";
    static final String BLADE_MODEL = MODEL_JAVA + "WindmillBladeModel.java";
    static final String LATTICE_MODEL = MODEL_JAVA + "WindmillBladeLatticeModel.java";
    static final String WHEEL_RENDERER = "src/main/java/net/dries007/tfc/client/render/blockentity/WaterWheelBlockEntityRenderer.java";
    static final String WINDMILL_RENDERER = "src/main/java/net/dries007/tfc/client/render/blockentity/WindmillBlockEntityRenderer.java";
    static final String BLOCKSTATES = TfcCheckout.ASSETS + "blockstates/";
    static final String MODELS = TfcCheckout.ASSETS + "models/";
    static final String TEXTURES = TfcCheckout.ASSETS + "textures/";
    static final String LANG = TfcCheckout.ASSETS + "lang/";
    static final String MINEABLE = TfcCheckout.GENERATED + "data/minecraft/tags/block/mineable/";
    static final String PACK = "craftengine/tfp/";
    public static final String OUTPUT_DIR = PACK + "configuration/power/";
    public static final String WHEEL_FILE = OUTPUT_DIR + "water_wheel.yml";
    public static final String WINDMILL_FILE = OUTPUT_DIR + "windmill.yml";
    public static final String BLADE_FILE = OUTPUT_DIR + "windmill_blade.yml";
    static final String RP = PACK + "resourcepack/assets/tfp/";
    static final String LANG_RP = PACK + "resourcepack/assets/tfp_power/lang/";
    static final String HEADER = DeviceYaml.HEADER + "\nPAR-MECH-18: water wheels, windmills, windmill blades (dev.tfp.datagen.power.PowerBlocks).";
    public static final String WHEEL = Json.TO_NS + ":wood/water_wheel/";
    public static final String WINDMILL = Json.TO_NS + ":wood/windmill/";
    public static final String LATTICE = Json.TO_NS + ":lattice_windmill_blade";
    public static final String RUSTIC = Json.TO_NS + ":rustic_windmill_blade";
    public static final String COLOURED = Json.TO_NS + ":windmill_blade/";
    /** Our models are written this many times smaller than TFC's (the element range); the displays scale them back. */
    static final double SCALE = 4;
    static final String RENDER = Json.TO_NS + ":render/power/";
    static final String WHEEL_RENDER = RENDER + "water_wheel/";
    static final String AXLE_RENDER = RENDER + "windmill_axle/";
    public static final String BLADE_RENDER = RENDER + "windmill_blade";
    public static final String LATTICE_RENDER = RENDER + "windmill_blade_lattice";
    static final String MODEL_DIR = "block/power/";
    public static final int MAX_BLADES = 5;
    public static final List<String> AXES = List.of("x", "y", "z");
    private static final Pattern WHEEL_ICON = Pattern.compile("item/wood/water_wheel_([a-z_]+)");
    static final String ATLAS = TfcCheckout.MAIN + "assets/minecraft/atlases/blocks.json";
    private static final Pattern STRENGTH = Pattern.compile("\\.strength\\(([0-9.]+)[fF]\\)");

    private PowerBlocks() {
    }

    /** True when the checkout has both block families and the models (the small test fixtures do not). */
    public static boolean available(TfcCheckout tfc) {
        return tfc.isDirectory(BLOCKSTATES + "wood/water_wheel") && tfc.isDirectory(BLOCKSTATES + "wood/windmill")
            && tfc.exists(WHEEL_MODEL) && tfc.exists(BLADE_MODEL) && tfc.exists(LATTICE_MODEL);
    }

    /** Writes everything; returns the number of server states. */
    public static long run(TfcCheckout tfc, OutputTree out) {
        List<String> woods = woods(tfc);
        Map<String, String> strengths = strengths(tfc);
        checkRenderers(tfc);
        writeModels(tfc, out, woods);
        long states = 0;
        Map<String, Object> blocks = new LinkedHashMap<>();
        Map<String, Object> items = new LinkedHashMap<>();
        for (String wood : woods) {
            Entry e = wheel(tfc, out, wood, strengths.get("water_wheel"));
            blocks.putAll(e.blocks());
            items.putAll(e.items());
            states += e.states();
        }
        out.put(WHEEL_FILE, Yaml.write(HEADER, root(blocks, items)));
        blocks.clear();
        items.clear();
        for (String wood : woods) {
            Entry e = windmill(tfc, wood, strengths.get("windmill"));
            blocks.putAll(e.blocks());
            items.putAll(e.items());
            states += e.states();
        }
        out.put(WINDMILL_FILE, Yaml.write(HEADER, root(blocks, items)));
        List<String> colours = colours(tfc);
        out.put(BLADE_FILE, Yaml.write(HEADER, root(Map.of(), bladeItems(tfc, out, colours))));
        writeLang(tfc, out, woods, colours);
        return states;
    }

    record Entry(Map<String, Object> blocks, Map<String, Object> items, long states) {
    }

    private static Map<String, Object> root(Map<String, Object> blocks, Map<String, Object> items) {
        Map<String, Object> root = new LinkedHashMap<>();
        if (!blocks.isEmpty()) {
            root.put("blocks", new LinkedHashMap<>(blocks));
        }
        root.put("items", new LinkedHashMap<>(items));
        return root;
    }

    /** TFC's woods: the water wheel and windmill blockstate folders, which must agree. */
    static List<String> woods(TfcCheckout tfc) {
        TreeSet<String> wheel = names(tfc, BLOCKSTATES + "wood/water_wheel/");
        TreeSet<String> mill = names(tfc, BLOCKSTATES + "wood/windmill/");
        if (wheel.isEmpty() || !wheel.equals(mill)) {
            throw new DatagenException("TFC's water wheel woods " + wheel + " differ from the windmill's " + mill);
        }
        return List.copyOf(wheel);
    }

    private static TreeSet<String> names(TfcCheckout tfc, String dir) {
        TreeSet<String> out = new TreeSet<>();
        for (String f : tfc.list(dir)) {
            if (f.endsWith(".json")) {
                out.add(f.substring(f.lastIndexOf('/') + 1, f.length() - ".json".length()));
            }
        }
        return out;
    }

    /** {@code Wood.BlockType} WINDMILL and WATER_WHEEL: {@code properties(wood).strength(9f)} with SoundType.WOOD. */
    static Map<String, String> strengths(TfcCheckout tfc) {
        String src = tfc.readString(WOOD_JAVA);
        if (!src.contains("ExtendedProperties.of(wood.woodColor()).sound(SoundType.WOOD)")) {
            throw new DatagenException(WOOD_JAVA + ": the wood block properties no longer use SoundType.WOOD");
        }
        Map<String, String> out = new TreeMap<>();
        for (String type : List.of("WINDMILL", "WATER_WHEEL")) {
            Matcher line = Pattern.compile("\\n\\s*" + type + "\\(\\(self, wood\\) -> new \\w+\\(properties\\(wood\\)([^;]*?)\\.blockEntity")
                .matcher(src);
            if (!line.find()) {
                throw new DatagenException(WOOD_JAVA + ": no " + type + " registration");
            }
            Matcher st = STRENGTH.matcher(line.group(1));
            if (!st.find()) {
                throw new DatagenException(WOOD_JAVA + ": " + type + " has no one-argument strength");
            }
            out.put(type.toLowerCase(java.util.Locale.ROOT), st.group(1));
        }
        if (!src.contains("textures/entity/water_wheel/\" + wood.getSerializedName() + \".png\"")) {
            throw new DatagenException(WOOD_JAVA + ": the water wheel texture is no longer textures/entity/water_wheel/<wood>");
        }
        return out;
    }

    /** The renderer frames this generator bakes in; a change in TFC fails the run. */
    static void checkRenderers(TfcCheckout tfc) {
        String wheel = tfc.readString(WHEEL_RENDERER).replaceAll("\\s+", " ");
        String mill = tfc.readString(WINDMILL_RENDERER).replaceAll("\\s+", " ");
        for (String need : List.of("stack.translate(0.5f, -0.5f, 0.5f);",
            "if (wheel.getBlockState().getValue(WaterWheelBlock.AXIS) == Direction.Axis.Z) { stack.mulPose(Axis.YN.rotationDegrees(90f)); }")) {
            if (!wheel.contains(need)) {
                throw new DatagenException(WHEEL_RENDERER + " no longer contains \"" + need + "\"");
            }
        }
        for (String need : List.of("stack.mulPose(Axis.YN.rotationDegrees(90f)); stack.mulPose(Axis.XN.rotationDegrees(90f));",
            "else { stack.mulPose(Axis.XN.rotationDegrees(90f)); }", "stack.translate(0.5f, axisX ? -2 : -1, 0.5f);",
            "final float offsetAngle = Mth.TWO_PI / bladeCount;", "textures/entity/misc/windmill_blade.png",
            "textures/entity/misc/windmill_blade_lattice.png")) {
            if (!mill.contains(need)) {
                throw new DatagenException(WINDMILL_RENDERER + " no longer contains \"" + need + "\"");
            }
        }
    }

    // ---- models -------------------------------------------------------------------------------------------------------

    static String wheelModel(String wood) {
        return MODEL_DIR + "water_wheel/" + wood;
    }

    /**
     * The baked wheel (one geometry, a texture per wood), the default and lattice blades, their textures, and our copy of the
     * axle shaft as the windmill's own render item model (the rotation blocks write {@code tfp:block/rotation/wood/axle/*}).
     */
    static void writeModels(TfcCheckout tfc, OutputTree out, List<String> woods) {
        // WaterWheelBlockEntityRenderer: translate(0.5, -0.5, 0.5); the model's main part sits 16 px up: the hub on the centre
        EntityModelBaker wheel = EntityModelBaker.read(WHEEL_MODEL, tfc.readString(WHEEL_MODEL));
        need(tfc, WHEEL_MODEL, "main.xRot = -wheel.getRotationAngle(partialTick);");
        JsonObject geometry = wheel.bake(List.of("main"), EntityModelBaker.Affine.translate(8, -8, 8), SCALE, "#0");
        geometry.remove("textures");
        out.put(RP + "models/" + MODEL_DIR + "water_wheel.json", Json.write(geometry));
        for (String wood : woods) {
            String texture = MODEL_DIR + "water_wheel/" + wood;
            copy(tfc, out, TEXTURES + "entity/water_wheel/" + wood + ".png", RP + "textures/" + texture + ".png");
            JsonObject m = new JsonObject();
            m.addProperty("parent", Json.TO_NS + ":" + MODEL_DIR + "water_wheel");
            JsonObject textures = new JsonObject();
            textures.addProperty("0", Json.TO_NS + ":" + texture);
            textures.addProperty("particle", Json.TO_NS + ":" + texture);
            m.add("textures", textures);
            out.put(RP + "models/" + wheelModel(wood) + ".json", Json.write(m));
        }
        // WindmillBlockEntityRenderer, axis x: XN 90, then translate(0.5, -2, 0.5); the parts sit 24 px up. Blade 0 at rest
        // points down from the centre. Axis z adds YN 90 first (and translates -1): the same blade turned about y, which the
        // display does.
        EntityModelBaker.Affine frame = EntityModelBaker.Affine.rotX(-90).after(EntityModelBaker.Affine.translate(8, -32, 8));
        need(tfc, BLADE_MODEL, "main.xRot = -(windmill.getRotationAngle(partialTick) + offsetAngle);");
        need(tfc, BLADE_MODEL, "blade.xRot = -(windmill.getRotationAngle(partialTick) + offsetAngle);");
        bladeModel(tfc, out, BLADE_MODEL, "windmill_blade", frame);
        bladeModel(tfc, out, LATTICE_MODEL, "windmill_blade_lattice", frame);
    }

    private static void bladeModel(TfcCheckout tfc, OutputTree out, String file, String name, EntityModelBaker.Affine frame) {
        EntityModelBaker blade = EntityModelBaker.read(file, tfc.readString(file));
        String texture = Json.TO_NS + ":" + MODEL_DIR + name;
        copy(tfc, out, TEXTURES + "entity/misc/" + name + ".png", RP + "textures/" + MODEL_DIR + name + ".png");
        out.put(RP + "models/" + MODEL_DIR + name + ".json", Json.write(blade.bake(List.of("blade", "main"), frame, SCALE, texture)));
    }

    private static void need(TfcCheckout tfc, String file, String needle) {
        if (!tfc.readString(file).replaceAll("\\s+", " ").contains(needle)) {
            throw new DatagenException(file + " no longer contains \"" + needle + "\"");
        }
    }

    private static void copy(TfcCheckout tfc, OutputTree out, String src, String dst) {
        if (!tfc.exists(src)) {
            throw new DatagenException("TFC has no " + src);
        }
        out.put(dst, tfc.read(src));
        if (tfc.exists(src + ".mcmeta")) {
            out.put(dst + ".mcmeta", tfc.read(src + ".mcmeta"));
        }
    }

    /**
     * TFC paints {@code item/wood/water_wheel} per wood at atlas stitching (the {@code paletted_permutations} source of its
     * {@code minecraft:blocks} atlas: each colour of {@code color_palettes/wood/planks/palette} becomes the same index of
     * {@code plank_items/<wood>}); a vanilla client has no such source for our pack, so the sprite is painted here.
     */
    static byte[] paletted(TfcCheckout tfc, String rel, String wood) {
        if (!tfc.readString(ATLAS).contains("\"tfc:" + rel + "\"")) {
            throw new DatagenException(ATLAS + " no longer paints tfc:" + rel);
        }
        BufferedImage key = image(tfc, "color_palettes/wood/planks/palette");
        BufferedImage perm = image(tfc, "color_palettes/wood/plank_items/" + wood);
        if (key.getWidth() != perm.getWidth()) {
            throw new DatagenException("the plank palette and plank_items/" + wood + " differ in length");
        }
        Map<Integer, Integer> map = new java.util.HashMap<>();
        for (int i = 0; i < key.getWidth(); i++) {
            map.put(key.getRGB(i, 0), perm.getRGB(i, 0));
        }
        BufferedImage img = image(tfc, rel);
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                int argb = img.getRGB(x, y);
                if ((argb >>> 24) != 0 && map.containsKey(argb)) {
                    img.setRGB(x, y, map.get(argb));
                }
            }
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try {
            ImageIO.write(img, "png", bytes);
        } catch (IOException e) {
            throw new DatagenException("cannot encode the " + wood + " water wheel icon: " + e.getMessage());
        }
        return bytes.toByteArray();
    }

    private static BufferedImage image(TfcCheckout tfc, String rel) {
        String path = TEXTURES + rel + ".png";
        if (!tfc.exists(path)) {
            throw new DatagenException("TFC has no texture " + rel);
        }
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(tfc.read(path)));
            BufferedImage argb = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);
            argb.getGraphics().drawImage(img, 0, 0, null);
            return argb;
        } catch (IOException e) {
            throw new DatagenException("cannot read " + path + ": " + e.getMessage());
        }
    }

    /** TFC's item model {@code models/item/<path>} (an {@code item/generated} sprite) as {@code tfp:item/<path>} with its texture. */
    static void itemModel(TfcCheckout tfc, OutputTree out, String path) {
        String src = MODELS + "item/" + path + ".json";
        JsonObject model = Json.parse(tfc.readString(src), src).getAsJsonObject();
        if (!"item/generated".equals(model.get("parent").getAsString())) {
            throw new DatagenException(src + " is no longer an item/generated sprite");
        }
        for (Map.Entry<String, JsonElement> t : model.getAsJsonObject("textures").entrySet()) {
            String ref = t.getValue().getAsString();
            if (!ref.startsWith(Json.FROM_NS + ":")) {
                throw new DatagenException(src + ": texture " + ref + " is not TFC's");
            }
            String png = ref.substring(Json.FROM_NS.length() + 1);
            Matcher wheel = WHEEL_ICON.matcher(png);
            if (wheel.matches() && !tfc.exists(TEXTURES + png + ".png")) {
                out.put(RP + "textures/" + png + ".png", paletted(tfc, "item/wood/water_wheel", wheel.group(1)));
            } else {
                copy(tfc, out, TEXTURES + png + ".png", RP + "textures/" + png + ".png");
            }
        }
        out.put(RP + "models/item/" + path + ".json", Json.write(Json.rename(model)));
    }

    // ---- blocks -------------------------------------------------------------------------------------------------------

    static String langKey(String id) {
        return "block." + id.replace(':', '.').replace('/', '.');
    }

    /** The one {@code minecraft:mineable/<tool>} tag of TFC's generated data that holds {@code tfc:<block>}. */
    static String tool(TfcCheckout tfc, String block) {
        String found = null;
        for (String t : List.of("axe", "hoe", "pickaxe", "shovel")) {
            String src = MINEABLE + t + ".json";
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

    /** The wheel display: TFC's model at the block centre, turned to the axis ({@code YN 90} for z), drawn 4 times larger. */
    static DeviceAppearance.Renderer wheelRenderer(String wood, String axis) {
        return DeviceAppearance.Renderer.item(WHEEL_RENDER + wood).scaled(BigDecimal.valueOf(SCALE).stripTrailingZeros())
            .rotated(RotationBlocks.rotation(0, "z".equals(axis) ? 90 : 0));
    }

    static Entry wheel(TfcCheckout tfc, OutputTree out, String wood, String hardness) {
        String id = WHEEL + wood;
        DeviceBlockSpec.Settings settings = DeviceBlockSpec.Settings.of(hardness, hardness, DeviceSound.WOOD,
            tool(tfc, "wood/water_wheel/" + wood));
        DeviceBlockSpec.Builder b = DeviceBlockSpec.builder(id).settings(settings)
            .loot(DeviceLoot.fromTfc(tfc, "wood/water_wheel/" + wood)).deviceBehaviour().blockItem(langKey(id))
            .property(DeviceProperty.names("axis", AXES, "x"))
            .renderItem(WHEEL_RENDER + wood, Json.TO_NS + ":" + wheelModel(wood));
        for (String axis : List.of("x", "z")) {
            b.appearance(DeviceAppearance.rendered(axis, DeviceAppearance.Carrier.state(RenderedTemplates.CARRIER),
                List.of(wheelRenderer(wood, axis))));
        }
        // CraftEngine's axis type has y; the device turns a y placement to the player's horizontal axis at once
        b.everyState(s -> "z".equals(s.get("axis")) ? "z" : "x", null);
        itemModel(tfc, out, "wood/water_wheel/" + wood);
        DeviceBlockSpec spec = b.build();
        return finish(spec, true, settings);
    }

    /**
     * The display rotation of blade {@code i} of {@code count} about the windmill's axis: TFC turns blade i by
     * {@code -(2 pi / count) * i} about x in the blade frame, which for axis z is turned by {@code YN 90}; the display's own
     * half turn about y is undone last (as {@link RotationBlocks#rotation}).
     */
    public static String bladeRotation(String axis, int i, int count) {
        double[] q = mul(axis(1, 0, 0, -360.0 * i / count), axis(0, 1, 0, 180));
        if ("z".equals(axis)) {
            q = mul(axis(0, 1, 0, -90), q);
        }
        StringBuilder sb = new StringBuilder();
        for (int k = 0; k < 4; k++) {
            BigDecimal v = BigDecimal.valueOf(q[k]).setScale(6, RoundingMode.HALF_EVEN).stripTrailingZeros();
            if (v.signum() == 0) {
                v = BigDecimal.ZERO;
            }
            sb.append(k == 0 ? "" : ",").append(v.toPlainString());
        }
        return sb.toString();
    }

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

    static String appearanceName(String axis, int count) {
        return axis + "_" + count;
    }

    static Entry windmill(TfcCheckout tfc, String wood, String hardness) {
        String id = WINDMILL + wood;
        DeviceBlockSpec.Settings settings = DeviceBlockSpec.Settings.of(hardness, hardness, DeviceSound.WOOD,
            tool(tfc, "wood/windmill/" + wood));
        String axleItem = AXLE_RENDER + wood;
        DeviceBlockSpec.Builder b = DeviceBlockSpec.builder(id).settings(settings)
            .loot(DeviceLoot.fromTfc(tfc, "wood/windmill/" + wood)).deviceBehaviour()
            .property(DeviceProperty.names("axis", AXES, "x"))
            .property(DeviceProperty.range("count", 1, MAX_BLADES, 1))
            // the shaft model the axle block's spinning display draws (PAR-MECH-01), as our own render item
            .renderItem(axleItem, Json.TO_NS + ":block/rotation/wood/axle/" + wood)
            .renderItem(BLADE_RENDER, Json.TO_NS + ":" + MODEL_DIR + "windmill_blade")
            .renderItem(LATTICE_RENDER, Json.TO_NS + ":" + MODEL_DIR + "windmill_blade_lattice");
        for (String axis : List.of("x", "z")) {
            for (int count = 1; count <= MAX_BLADES; count++) {
                List<DeviceAppearance.Renderer> r = new ArrayList<>();
                // AxleBlockEntityRenderer.renderAxle: the shaft model along z, turned to x
                r.add(DeviceAppearance.Renderer.item(axleItem).rotated(RotationBlocks.rotation(0, "x".equals(axis) ? 90 : 0)));
                for (int i = 0; i < count; i++) {
                    r.add(DeviceAppearance.Renderer.item(BLADE_RENDER).scaled(BigDecimal.valueOf(SCALE).stripTrailingZeros())
                        .rotated(bladeRotation(axis, i, count)));
                }
                b.appearance(DeviceAppearance.rendered(appearanceName(axis, count),
                    DeviceAppearance.Carrier.state(RotationBlocks.axleCarrier(axis).state()), r));
            }
        }
        b.everyState(s -> appearanceName("z".equals(s.get("axis")) ? "z" : "x", Integer.parseInt(s.get("count"))), null);
        return finish(b.build(), false, settings);
    }

    /**
     * The block entry: the device spec's literal YAML; for the wheel's barrier cube the settings and events are the expanded
     * {@code tfp:block/rendered_cube} template (sounds with TFC's SoundType volume and pitch, cracks, break particles), as
     * {@code RotationBlocks} writes its cubes; every display gets the cube's view range (the models are blocks wide).
     */
    @SuppressWarnings("unchecked")
    static Entry finish(DeviceBlockSpec spec, boolean cube, DeviceBlockSpec.Settings settings) {
        Map<String, Object> root = DeviceYaml.literal(spec);
        Map<String, Object> blocks = (Map<String, Object>) root.get("blocks");
        Map<String, Object> block = (Map<String, Object>) blocks.get(spec.id());
        if (cube) {
            String render = spec.renderItems().keySet().iterator().next();
            RenderedCubeSpec cubeSpec = new RenderedCubeSpec(spec.id(), render, spec.renderItems().get(render), settings, false,
                render, spec.blockItemLangKey());
            Map<String, Object> templates = (Map<String, Object>) RenderedTemplates.yaml().get("templates#rendered_block");
            Map<String, Object> expanded = expand((Map<String, Object>) templates.get(RenderedTemplates.CUBE), cubeSpec.arguments());
            Map<String, Object> next = new LinkedHashMap<>();
            next.put("settings", expanded.get("settings"));
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
            for (Object r : (List<Object>) appearance.get("entity_renderer")) {
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

    // ---- items and lang -----------------------------------------------------------------------------------------------

    /** TFC's dye colours of {@code WINDMILL_BLADES}: the item models under {@code models/item/windmill_blade/}. */
    static List<String> colours(TfcCheckout tfc) {
        List<String> c = List.copyOf(names(tfc, MODELS + "item/windmill_blade/"));
        if (c.size() != 16 || !c.contains("white")) {
            throw new DatagenException("TFC's windmill blade colours are " + c + ", not the 16 dye colours");
        }
        return c;
    }

    static String itemLangKey(String id) {
        return "item." + id.replace(':', '.').replace('/', '.');
    }

    private static Map<String, Object> simpleItem(String id) {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("template", "tfp:item/simple");
        e.put("arguments", Map.of("item_lang_key", itemLangKey(id)));
        return e;
    }

    static Map<String, Object> bladeItems(TfcCheckout tfc, OutputTree out, List<String> colours) {
        Map<String, Object> items = new LinkedHashMap<>();
        for (String c : colours) {
            itemModel(tfc, out, "windmill_blade/" + c);
            items.put(COLOURED + c, simpleItem(COLOURED + c));
        }
        for (String id : List.of(LATTICE, RUSTIC)) {
            itemModel(tfc, out, id.substring(id.indexOf(':') + 1));
            items.put(id, simpleItem(id));
        }
        return items;
    }

    /** The block and item names from every TFC locale that names them. */
    static void writeLang(TfcCheckout tfc, OutputTree out, List<String> woods, List<String> colours) {
        List<String> keys = new ArrayList<>();
        for (String wood : woods) {
            keys.add(langKey(WHEEL + wood));
            keys.add(langKey(WINDMILL + wood));
        }
        for (String c : colours) {
            keys.add(itemLangKey(COLOURED + c));
        }
        keys.add(itemLangKey(LATTICE));
        keys.add(itemLangKey(RUSTIC));
        for (String f : tfc.list(LANG)) {
            if (!f.endsWith(".json")) {
                continue;
            }
            String locale = f.substring(LANG.length(), f.length() - ".json".length());
            JsonObject src = Json.parse(tfc.readString(f), f).getAsJsonObject();
            Map<String, String> found = new TreeMap<>();
            for (String key : keys) {
                String tfcKey = key.replaceFirst("^(block|item)\\." + Json.TO_NS + "\\.", "$1." + Json.FROM_NS + ".");
                if (src.has(tfcKey)) {
                    found.put(key, src.get(tfcKey).getAsString());
                } else if (locale.equals("en_us")) {
                    throw new DatagenException("TFC has no English name " + tfcKey);
                }
            }
            if (found.isEmpty()) {
                continue;
            }
            JsonObject o = new JsonObject();
            found.forEach(o::addProperty);
            out.put(LANG_RP + locale + ".json", Json.write(o));
        }
    }
}
