/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/IngotPileBlock.java @ c3b4d9536 (COUNT, SHAPES),
 *       src/main/java/net/dries007/tfc/common/blocks/devices/DoubleIngotPileBlock.java @ c3b4d9536 (DOUBLE_COUNT, SHAPES),
 *       src/main/java/net/dries007/tfc/common/blocks/TFCBlocks.java @ c3b4d9536 (INGOT_PILE, DOUBLE_INGOT_PILE: registerNoItem,
 *       strength(4, 60), SoundType.METAL, noOcclusion),
 *       src/main/java/net/dries007/tfc/client/model/IngotPileBlockModel.java @ c3b4d9536 (the trapezoid of an ingot and its place),
 *       src/main/java/net/dries007/tfc/client/model/DoubleIngotPileBlockModel.java @ c3b4d9536,
 *       src/main/resources/assets/tfc/textures/block/metal/smooth/*.png @ c3b4d9536 (copied: MetalItem.softTextureId)
 */
package dev.tfp.datagen.pile;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.tfp.core.pile.PileKind;
import dev.tfp.core.pile.PileLayout;
import dev.tfp.datagen.DatagenException;
import dev.tfp.datagen.Json;
import dev.tfp.datagen.OutputTree;
import dev.tfp.datagen.TfcCheckout;
import dev.tfp.datagen.Yaml;
import dev.tfp.datagen.device.DeviceAppearance;
import dev.tfp.datagen.device.DeviceBlockSpec;
import dev.tfp.datagen.device.DeviceProperty;
import dev.tfp.datagen.device.DeviceTemplates;
import dev.tfp.datagen.device.DeviceYaml;
import dev.tfp.datagen.hitbox.HitboxAllocation;
import dev.tfp.datagen.hitbox.HitboxDemands;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * PAR-METAL-16: the ingot pile and the double ingot pile as the CraftEngine blocks {@code tfp:ingot_pile}
 * ({@code configuration/pile/ingot_pile.yml}) and {@code tfp:double_ingot_pile} ({@code double_ingot_pile.yml}).
 *
 * <ul>
 *   <li>One property, TFC's {@code count} ({@code COUNT_1_64}: 64 states; {@code COUNT_1_36}: 36 states). The hitbox is TFC's
 *       {@code SHAPES[(count - 1) / 8]} (6 for the double pile) on a transparent carrier, one appearance per layer
 *       ({@code h0}..). Settings are TFC's (strength 4 / 60, metal sound, pickaxe, not occluding), no loot table (TFC has none;
 *       the ingots drop from the block entity) and no block item ({@code registerNoItem}).</li>
 *   <li>The ingots are item displays of the plugin (one per ingot, one per full layer of one metal), because their metals are
 *       per-block data that CraftEngine's constant {@code entity_renderer} cannot draw. The pack carries, per kind, the item
 *       model definitions {@code render/pile/<kind>_single} and {@code _layer}, which select the model by the metal name in
 *       the stack's custom model data string, and one block model per metal and variant with TFC's
 *       {@code textures/block/metal/smooth/<metal>.png}. TFC's trapezoid is drawn as two stacked boxes.</li>
 * </ul>
 */
public final class PileBlocks {
    static final String P = "src/main/java/net/dries007/tfc/common/blocks/devices/";
    static final String TFC_BLOCKS = "src/main/java/net/dries007/tfc/common/blocks/TFCBlocks.java";
    static final String ASSETS = TfcCheckout.ASSETS;
    static final String SMOOTH = ASSETS + "textures/block/metal/smooth/";
    static final String PACK = "craftengine/tfp/";
    public static final String INGOT_OUTPUT = PACK + "configuration/pile/ingot_pile.yml";
    public static final String DOUBLE_OUTPUT = PACK + "configuration/pile/double_ingot_pile.yml";
    static final String RP = PACK + "resourcepack/assets/tfp/";
    public static final String INGOT_ID = HitboxDemands.INGOT_PILE_ID;
    public static final String DOUBLE_ID = HitboxDemands.DOUBLE_INGOT_PILE_ID;
    /** TFC's {@code IngotPileBlock.COUNT} and the double pile's, both named {@code count} here. */
    public static final String COUNT = "count";
    /** The item model id of the display of {@code kind} and {@code variant} (the plugin's {@code PileDisplays}). */
    public static final String ITEM_MODEL_PREFIX = "tfp:render/pile/";
    static final String UNKNOWN = "unknown";

    private PileBlocks() {
    }

    /** True when the checkout has both piles and the metal textures (the small test fixtures do not). */
    public static boolean available(TfcCheckout tfc) {
        return tfc.exists(P + "IngotPileBlock.java") && tfc.exists(P + "DoubleIngotPileBlock.java")
            && tfc.exists(SMOOTH + UNKNOWN + ".png");
    }

    /** The metals that have a soft texture (and the unknown one), sorted. */
    public static List<String> metals(TfcCheckout tfc) {
        TreeSet<String> out = new TreeSet<>();
        for (String f : tfc.list(SMOOTH)) {
            if (f.endsWith(".png")) {
                out.add(f.substring(f.lastIndexOf('/') + 1, f.length() - ".png".length()));
            }
        }
        if (!out.contains(UNKNOWN)) {
            throw new DatagenException(SMOOTH + " has no unknown.png (MetalItem.unknown)");
        }
        return List.copyOf(out);
    }

    /** Writes the entries, the display item definitions, models and textures. Returns the number of server states (64 + 36). */
    public static int run(TfcCheckout tfc, OutputTree out) {
        checkSources(tfc);
        DeviceBlockSpec single = spec(tfc, PileKind.INGOT);
        DeviceBlockSpec dbl = spec(tfc, PileKind.DOUBLE_INGOT);
        out.put(INGOT_OUTPUT, Yaml.write(DeviceYaml.HEADER, entry(single)));
        out.put(DOUBLE_OUTPUT, Yaml.write(DeviceYaml.HEADER, entry(dbl)));
        List<String> metals = metals(tfc);
        for (String metal : metals) {
            out.put(RP + "textures/block/pile/" + metal + ".png", tfc.read(SMOOTH + metal + ".png"));
        }
        for (PileKind kind : PileKind.values()) {
            for (Variant v : Variant.values()) {
                out.put(RP + "items/render/pile/" + name(kind) + "_" + v.id + ".json", Json.write(definition(kind, v, metals)));
                for (String metal : metals) {
                    out.put(RP + "models/item/render/pile/" + name(kind) + "_" + v.id + "/" + metal + ".json",
                        Json.write(model(kind, v, metal)));
                }
            }
        }
        return (int) (single.stateCount() + dbl.stateCount());
    }

    /** The model variants of a display: one ingot, or a full layer. */
    enum Variant {
        SINGLE("single"), LAYER("layer");

        final String id;

        Variant(String id) {
            this.id = id;
        }
    }

    /** {@code ingot} and {@code double_ingot}: the file prefix of a kind. */
    public static String name(PileKind kind) {
        return switch (kind) {
            case INGOT -> "ingot";
            case DOUBLE_INGOT -> "double_ingot";
        };
    }

    /** TFC's blocks and settings the generator depends on. */
    static void checkSources(TfcCheckout tfc) {
        String i = tfc.readString(P + "IngotPileBlock.java").replaceAll("\\s+", "");
        String d = tfc.readString(P + "DoubleIngotPileBlock.java").replaceAll("\\s+", "");
        String blocks = tfc.readString(TFC_BLOCKS).replaceAll("\\s+", "");
        for (String needle : List.of("COUNT=TFCBlockStateProperties.COUNT_1_64;", "SHAPES[(state.getValue(getCountProperty())-1)/8]",
            "setValue(countProperty,1)")) {
            if (!i.contains(needle.replaceAll("\\s+", ""))) {
                throw new DatagenException(P + "IngotPileBlock.java no longer contains \"" + needle + "\": re-check the pile entry");
            }
        }
        for (String needle : List.of("DOUBLE_COUNT=TFCBlockStateProperties.COUNT_1_36;", "SHAPES[(state.getValue(getCountProperty())-1)/6]")) {
            if (!d.contains(needle.replaceAll("\\s+", ""))) {
                throw new DatagenException(P + "DoubleIngotPileBlock.java no longer contains \"" + needle + "\"");
            }
        }
        for (String needle : List.of("registerNoItem(\"ingot_pile\",()->newIngotPileBlock(ExtendedProperties.of(MapColor.METAL)"
                + ".strength(4,60).sound(SoundType.METAL).noOcclusion()",
            "registerNoItem(\"double_ingot_pile\",()->newDoubleIngotPileBlock(ExtendedProperties.of(MapColor.METAL)"
                + ".strength(4,60).sound(SoundType.METAL).noOcclusion()")) {
            if (!blocks.contains(needle.replaceAll("\\s+", ""))) {
                throw new DatagenException(TFC_BLOCKS + " no longer registers " + needle);
            }
        }
    }

    static String id(PileKind kind) {
        return kind == PileKind.INGOT ? INGOT_ID : DOUBLE_ID;
    }

    /** The server state of the property {@code count} {@code 1..capacity} and its hitbox layer {@code (count - 1) / perLayer}. */
    static DeviceBlockSpec spec(TfcCheckout tfc, PileKind kind) {
        DeviceBlockSpec.Builder b = DeviceBlockSpec.builder(id(kind));
        b.property(DeviceProperty.range(COUNT, 1, kind.capacity(), 1));
        Map<String, String> hidden = new LinkedHashMap<>();
        for (int layer = 0; layer < kind.layers(); layer++) {
            HitboxAllocation.Assignment a = HitboxAllocation.agreed(id(kind), HitboxDemands.ingotPileAppearance(layer));
            if (a.mode() != HitboxAllocation.Mode.HITBOX_ONLY) {
                throw new DatagenException(id(kind) + "#h" + layer + " got a " + a.mode() + " carrier; the piles draw item displays");
            }
            if (a.hideTarget() != null && !a.pool() && hidden.putIfAbsent(a.state(), a.hideTarget()) == null) {
                b.stateMapping(a.state(), a.hideTarget());
            }
            b.appearance(DeviceAppearance.vanilla(HitboxDemands.ingotPileAppearance(layer),
                DeviceAppearance.Carrier.state(a.state())));
        }
        b.everyState(s -> HitboxDemands.ingotPileAppearance(kind.shapeIndex(Integer.parseInt(s.get(COUNT)))), null)
            .settings(DeviceBlockSpec.Settings.fromTfc(tfc, kind == PileKind.INGOT ? "ingot_pile" : "double_ingot_pile"))
            .deviceBehaviour();
        return b.build();
    }

    /**
     * The entry. The carriers are drawn transparent (the ingots are displays of the plugin, so the spec has no renderer, which
     * the device spec does not allow for a transparent appearance: the flag is set here) and the pile does not occlude
     * (TFC {@code noOcclusion}), so it uses the rendered template.
     */
    @SuppressWarnings("unchecked")
    static Map<String, Object> entry(DeviceBlockSpec spec) {
        Map<String, Object> root = DeviceYaml.templated(spec);
        Map<String, Object> block = (Map<String, Object>) ((Map<String, Object>) root.get("blocks")).get(spec.id());
        block.put("template", DeviceTemplates.DEVICE_RENDERED);
        Map<String, Object> appearances = (Map<String, Object>) ((Map<String, Object>) block.get("states")).get("appearances");
        for (Object appearance : appearances.values()) {
            ((Map<String, Object>) appearance).put("transparent", true);
        }
        return root;
    }

    // ---- the display models ----------------------------------------------------------------------------

    /**
     * {@code items/render/pile/<kind>_<variant>.json}: the model of the metal in the stack's custom model data string 0, the
     * unknown metal's for anything else.
     */
    static JsonObject definition(PileKind kind, Variant v, List<String> metals) {
        JsonArray cases = new JsonArray();
        for (String metal : metals) {
            JsonObject c = new JsonObject();
            c.addProperty("when", metal);
            c.add("model", modelRef(kind, v, metal));
            cases.add(c);
        }
        JsonObject select = new JsonObject();
        select.addProperty("type", "minecraft:select");
        select.addProperty("property", "minecraft:custom_model_data");
        select.addProperty("index", 0);
        select.add("cases", cases);
        select.add("fallback", modelRef(kind, v, UNKNOWN));
        JsonObject root = new JsonObject();
        root.add("model", select);
        return root;
    }

    private static JsonObject modelRef(PileKind kind, Variant v, String metal) {
        JsonObject m = new JsonObject();
        m.addProperty("type", "minecraft:model");
        m.addProperty("model", "tfp:item/render/pile/" + name(kind) + "_" + v.id + "/" + metal);
        return m;
    }

    /** TFC's trapezoid, as pixel boxes {@code {x1, y1, z1, x2, y2, z2}}: its lower half and its narrower upper half. */
    static List<double[]> ingotBoxes(PileKind kind, double ox, double oz) {
        double scale = 0.0625 / 2; // TFC: 1/32 of a block, half a pixel
        double minX = scale * 0.5;
        double minZ = scale * 0.5;
        double maxX = scale * (minX + (kind == PileKind.INGOT ? 7 : 10));
        double maxZ = scale * (minZ + 15);
        double height = scale * (kind == PileKind.INGOT ? 4 : 5);
        double mid = height / 2;
        List<double[]> boxes = new ArrayList<>();
        boxes.add(px(minX, 0, minZ, maxX, mid, maxZ, ox, oz));
        boxes.add(px(minX + scale, mid, minZ + scale, maxX - scale, height, maxZ - scale, ox, oz));
        return boxes;
    }

    /** Blocks to pixels, moved by the row offsets (blocks), rounded to 1/1000 px. */
    private static double[] px(double x1, double y1, double z1, double x2, double y2, double z2, double ox, double oz) {
        double[] v = {(x1 + ox) * 16, y1 * 16, (z1 + oz) * 16, (x2 + ox) * 16, y2 * 16, (z2 + oz) * 16};
        for (int i = 0; i < v.length; i++) {
            v[i] = Math.round(v[i] * 1000) / 1000.0;
        }
        return v;
    }

    /** One display model: the first ingot of the layer at the corner, or all of the layer's ingots, in the even-layer frame. */
    static JsonObject model(PileKind kind, Variant v, String metal) {
        String texture = "tfp:block/pile/" + metal;
        JsonArray elements = new JsonArray();
        int ingots = v == Variant.SINGLE ? 1 : kind.perLayer();
        for (int i = 0; i < ingots; i++) {
            PileLayout.Placement at = PileLayout.of(kind, i);
            for (double[] box : ingotBoxes(kind, at.x(), at.z())) {
                elements.add(element(box));
            }
        }
        JsonObject textures = new JsonObject();
        textures.addProperty("0", texture);
        textures.addProperty("particle", texture);
        JsonObject fixed = new JsonObject();
        fixed.add("rotation", nums(0, 0, 0));
        fixed.add("translation", nums(0, 0, 0));
        fixed.add("scale", nums(1, 1, 1));
        JsonObject display = new JsonObject();
        display.add("fixed", fixed);
        JsonObject m = new JsonObject();
        m.add("textures", textures);
        m.add("elements", elements);
        m.add("display", display);
        return m;
    }

    private static JsonObject element(double[] b) {
        JsonObject faces = new JsonObject();
        faces.add("north", face(b[0], 16 - b[4], b[3], 16 - b[1]));
        faces.add("south", face(b[0], 16 - b[4], b[3], 16 - b[1]));
        faces.add("east", face(b[2], 16 - b[4], b[5], 16 - b[1]));
        faces.add("west", face(b[2], 16 - b[4], b[5], 16 - b[1]));
        faces.add("up", face(b[0], b[2], b[3], b[5]));
        faces.add("down", face(b[0], b[2], b[3], b[5]));
        JsonObject e = new JsonObject();
        e.add("from", nums(b[0], b[1], b[2]));
        e.add("to", nums(b[3], b[4], b[5]));
        e.add("faces", faces);
        return e;
    }

    private static JsonObject face(double u1, double v1, double u2, double v2) {
        JsonObject f = new JsonObject();
        f.add("uv", nums(u1, v1, u2, v2));
        f.addProperty("texture", "#0");
        return f;
    }

    private static JsonArray nums(double... v) {
        JsonArray a = new JsonArray();
        for (double d : v) {
            a.add(d);
        }
        return a;
    }
}
