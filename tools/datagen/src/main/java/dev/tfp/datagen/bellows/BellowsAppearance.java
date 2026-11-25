/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/client/render/blockentity/BellowsBlockEntityRenderer.java @ c3b4d9536 (the middle:
 *       planeCount 4, headWidth, the indent; the back plate 16x16x2),
 *       src/main/java/net/dries007/tfc/common/blockentities/BellowsBlockEntity.java @ c3b4d9536 (MIN/MAX_EXTENSION),
 *       src/main/resources/assets/tfc/models/block/bellows.json and blockstates/bellows.json @ c3b4d9536 (the front plate, the
 *       turns), src/main/resources/assets/tfc/sounds.json (block.bellows.blow) and sounds/block/bellows/blow1.ogg @ c3b4d9536
 *       (copied), src/main/resources/assets/tfc/lang/*.json @ c3b4d9536 (subtitles.block.tfc.bellows.blow)
 */
package dev.tfp.datagen.bellows;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.tfp.core.bellows.BellowsMotion;
import dev.tfp.datagen.DatagenException;
import dev.tfp.datagen.Json;
import dev.tfp.datagen.OutputTree;
import dev.tfp.datagen.TfcCheckout;
import dev.tfp.datagen.crop.CropBlocks;
import dev.tfp.datagen.device.DeviceAppearance;
import dev.tfp.datagen.device.DeviceBlockSpec;
import dev.tfp.datagen.device.DeviceLoot;
import dev.tfp.datagen.device.DeviceProperty;
import dev.tfp.datagen.hitbox.HitboxAllocation;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * PAR-METAL-26: how the bellows looks and sounds. The block {@code tfp:bellows} has the TFC {@code facing} and a {@code pressed}
 * level 0 to {@value #LEVELS_MAX}: level {@code n} draws the bellows at the extension {@code MIN + n * (MAX - MIN) / 4} of
 * {@link BellowsMotion} (0.125, 0.25 ... 0.625). One item display per appearance draws TFC's front plate, the moving back
 * plate (16x16x2) and the accordion middle (four planes of 12x12, alternately indented like TFC's renderer) as one model,
 * turned by TFC's blockstate {@code y}; the carrier is transparent (hitbox only). The plugin morphs the block through the
 * levels after a push ({@code BellowsVisuals}); no ticking, packets only on a push.
 *
 * <p>TFC's {@code block.bellows.blow} is copied as {@code tfp_bellows:block.bellows.blow} with its subtitle.
 */
public final class BellowsAppearance {
    public static final String ID = "tfp:bellows";
    public static final String PROPERTY = "pressed";
    public static final int LEVELS = 5;
    public static final int LEVELS_MAX = LEVELS - 1;
    public static final List<String> FACINGS = List.of("north", "east", "south", "west");
    static final String SOUND_NS = "tfp_bellows";
    public static final String SOUND_EVENT = "block.bellows.blow";
    static final String SUBTITLE_TFC = "subtitles.block.tfc.bellows.blow";
    static final String SUBTITLE = "subtitles.block.tfp.bellows.blow";
    static final String PACK = "craftengine/tfp/";
    static final String RP = PACK + "resourcepack/assets/tfp/";
    static final String SOUND_RP = PACK + "resourcepack/assets/" + SOUND_NS + "/";
    static final String SOUNDS = TfcCheckout.ASSETS + "sounds.json";
    static final String LANG = TfcCheckout.ASSETS + "lang/";
    static final String TEXTURES = RP + "textures/block/devices/bellows/";
    /** TFC {@code BellowsBlockEntityRenderer}: planes of the middle. */
    static final int PLANES = 4;
    /** TFC: {@code indentBase * indentFactor} in blocks. */
    static final double INDENT = 0.0125 * 1.8;

    private BellowsAppearance() {
    }

    /** The extension (blocks, 0.125 to 0.625) a pressed level draws. */
    public static double extension(int level) {
        return BellowsMotion.MIN_EXTENSION + level * (BellowsMotion.MAX_EXTENSION - BellowsMotion.MIN_EXTENSION) / LEVELS_MAX;
    }

    public static String model(int level) {
        return Json.TO_NS + ":block/bellows/level_" + level;
    }

    static String renderItem(int level) {
        return Json.TO_NS + ":render/bellows/level_" + level;
    }

    static String appearanceName(String facing, int level) {
        return facing + "_" + level;
    }

    /** The spec: facing x pressed level, each on the transparent carrier of its facing (the outline does not change). */
    public static DeviceBlockSpec spec(TfcCheckout tfc, Map<String, Integer> turns) {
        DeviceBlockSpec.Builder b = DeviceBlockSpec.builder(ID)
            .property(DeviceProperty.names("facing", FACINGS, "north"))
            .property(DeviceProperty.range(PROPERTY, 0, LEVELS_MAX, 0));
        for (int level = 0; level < LEVELS; level++) {
            b.renderItem(renderItem(level), model(level));
        }
        java.util.Set<String> hidden = new java.util.TreeSet<>();
        for (String facing : FACINGS) {
            HitboxAllocation.Assignment a = HitboxAllocation.agreed(ID, facing);
            if (a.mode() != HitboxAllocation.Mode.HITBOX_ONLY) {
                throw new DatagenException(ID + "#" + facing + " got a " + a.mode() + " carrier; the bellows draws item displays");
            }
            if (a.hideTarget() != null && !a.pool() && hidden.add(a.state())) {
                b.stateMapping(a.state(), a.hideTarget());
            }
            for (int level = 0; level < LEVELS; level++) {
                List<DeviceAppearance.Renderer> r = new ArrayList<>();
                r.add(DeviceAppearance.Renderer.item(renderItem(level)).rotated(CropBlocks.rotation(turns.get(facing))));
                b.appearance(DeviceAppearance.rendered(appearanceName(facing, level), DeviceAppearance.Carrier.state(a.state()), r));
            }
        }
        return b.everyState(s -> appearanceName(s.get("facing"), Integer.parseInt(s.get(PROPERTY))), null)
            .deviceBehaviour()
            .settings(DeviceBlockSpec.Settings.fromTfc(tfc, "bellows"))
            .loot(DeviceLoot.fromTfc(tfc, "bellows"))
            .blockItem("block.tfp.bellows")
            .build();
    }

    /** TFC's blockstate turns ({@code y}) of each facing, read from {@code blockstates/bellows.json}. */
    public static Map<String, Integer> turns(TfcCheckout tfc) {
        String src = TfcCheckout.ASSETS + "blockstates/bellows.json";
        JsonObject variants = Json.parse(tfc.readString(src), src).getAsJsonObject().getAsJsonObject("variants");
        Map<String, Integer> turns = new TreeMap<>();
        for (String f : FACINGS) {
            JsonObject v = variants.getAsJsonObject("facing=" + f);
            turns.put(f, v.has("y") ? v.get("y").getAsInt() : 0);
        }
        return turns;
    }

    /** Writes the level models and the sound; fails when TFC's model, textures or sound differ from what is drawn here. */
    public static void writeAssets(TfcCheckout tfc, OutputTree out) {
        String modelSrc = TfcCheckout.ASSETS + "models/block/bellows.json";
        JsonObject front = Json.parse(tfc.readString(modelSrc), modelSrc).getAsJsonObject();
        JsonObject el = front.getAsJsonArray("elements").get(0).getAsJsonObject();
        if (front.getAsJsonArray("elements").size() != 1 || !el.getAsJsonArray("from").toString().equals("[0,0,14]")
            || !el.getAsJsonArray("to").toString().equals("[16,16,16]")) {
            throw new DatagenException(modelSrc + ": the front plate is no longer the single 16x16x2 box at z 14 to 16");
        }
        for (String tex : List.of("front", "back", "side")) {
            if (!out.contains(TEXTURES + tex + ".png")) {
                throw new DatagenException("the bellows texture " + tex + ".png was not copied by AssetPipeline");
            }
        }
        for (int level = 0; level < LEVELS; level++) {
            out.put(RP + "models/block/bellows/level_" + level + ".json", Json.write(levelModel(level)));
        }
        writeSound(tfc, out);
        writeLang(tfc, out);
    }

    // ---- the model ---------------------------------------------------------------------------------------

    /** TFC's {@code bellows.json} faces of a 16x16x2 plate (the same uv layout for both plates). */
    private static JsonObject plate(String name, double z1, double z2, String texture, boolean mirrored) {
        JsonObject e = new JsonObject();
        e.addProperty("name", name);
        e.add("from", array(0, 0, z1));
        e.add("to", array(16, 16, z2));
        JsonObject faces = new JsonObject();
        faces.add("north", face(0, 0, 16, 16, texture, 0));
        faces.add("east", mirrored ? face(14, 0, 16, 16, texture, 0) : face(0, 0, 2, 16, texture, 0));
        faces.add("south", face(0, 0, 16, 16, texture, 0));
        faces.add("west", mirrored ? face(0, 0, 2, 16, texture, 0) : face(14, 0, 16, 16, texture, 0));
        faces.add("up", mirrored ? face(0, 0, 16, 2, texture, 0) : face(0, 14, 16, 16, texture, 0));
        faces.add("down", mirrored ? face(0, 14, 16, 16, texture, 0) : face(0, 0, 16, 2, texture, 0));
        e.add("faces", faces);
        return e;
    }

    /** The model of one pressed level, drawn for a bellows facing south (TFC's blockstate without a turn). */
    static JsonObject levelModel(int level) {
        double extension = extension(level) * 16; // px from the far end to the end of the moving plate
        JsonObject m = new JsonObject();
        m.addProperty("credit", "Generated by tools/datagen (PAR-METAL-26)");
        m.addProperty("ambientocclusion", false);
        JsonObject textures = new JsonObject();
        textures.addProperty("0", Json.TO_NS + ":block/devices/bellows/back");
        textures.addProperty("1", Json.TO_NS + ":block/devices/bellows/front");
        textures.addProperty("2", Json.TO_NS + ":block/devices/bellows/side");
        textures.addProperty("particle", Json.TO_NS + ":block/devices/bellows/back");
        m.add("textures", textures);
        JsonArray elements = new JsonArray();
        elements.add(plate("front", 14, 16, "#1", false));
        elements.add(plate("back", extension - 2, extension, "#0", true));
        // the middle runs from the moving plate to the front plate (TFC: local z headWidth to width, mirrored by the 180 turn)
        double width = 1 - extension / 16; // TFC's `width`
        double change = INDENT / width * 16; // px
        double length = 14 - extension;
        for (int i = 0; i < PLANES; i++) {
            double inset = i % 2 == 0 ? change : 0;
            double z1 = extension + i * length / PLANES;
            double z2 = extension + (i + 1) * length / PLANES;
            elements.add(plane(i, 2 + inset, 14 - inset, z1, z2));
        }
        m.add("elements", elements);
        return m;
    }

    private static JsonObject plane(int i, double min, double max, double z1, double z2) {
        JsonObject e = new JsonObject();
        e.addProperty("name", "middle_" + i);
        e.add("from", array(min, min, z1));
        e.add("to", array(max, max, z2));
        JsonObject faces = new JsonObject();
        int u1 = i * 4;
        faces.add("east", face(u1, 0, u1 + 4, 16, "#2", 0));
        faces.add("west", face(u1, 0, u1 + 4, 16, "#2", 0));
        faces.add("up", face(u1, 0, u1 + 4, 16, "#2", 90));
        faces.add("down", face(u1, 0, u1 + 4, 16, "#2", 90));
        e.add("faces", faces);
        return e;
    }

    private static JsonObject face(double u1, double v1, double u2, double v2, String texture, int rotation) {
        JsonObject f = new JsonObject();
        f.add("uv", array(u1, v1, u2, v2));
        if (rotation != 0) {
            f.addProperty("rotation", rotation);
        }
        f.addProperty("texture", texture);
        return f;
    }

    private static JsonArray array(double... v) {
        JsonArray a = new JsonArray();
        for (double d : v) {
            a.add(d);
        }
        return a;
    }

    // ---- the sound ---------------------------------------------------------------------------------------

    /** TFC's {@code block.bellows.blow} and its oggs, under {@code tfp_bellows}. */
    static void writeSound(TfcCheckout tfc, OutputTree out) {
        JsonObject sounds = Json.parse(tfc.readString(SOUNDS), SOUNDS).getAsJsonObject();
        if (!sounds.has(SOUND_EVENT)) {
            throw new DatagenException(SOUNDS + " has no " + SOUND_EVENT);
        }
        JsonArray list = new JsonArray();
        String prefix = Json.FROM_NS + ":";
        for (var el : sounds.getAsJsonObject(SOUND_EVENT).getAsJsonArray("sounds")) {
            String name = el.getAsString();
            if (!name.startsWith(prefix)) {
                throw new DatagenException(SOUNDS + ": " + SOUND_EVENT + " sound outside tfc: " + name);
            }
            String path = name.substring(prefix.length());
            String ogg = TfcCheckout.ASSETS + "sounds/" + path + ".ogg";
            if (!tfc.exists(ogg)) {
                throw new DatagenException("missing sound " + ogg);
            }
            out.put(SOUND_RP + "sounds/" + path + ".ogg", tfc.read(ogg));
            list.add(SOUND_NS + ":" + path);
        }
        JsonObject event = new JsonObject();
        event.addProperty("subtitle", SUBTITLE);
        event.add("sounds", list);
        JsonObject root = new JsonObject();
        root.add(SOUND_EVENT, event);
        out.put(SOUND_RP + "sounds.json", Json.write(root));
    }

    /** The sound subtitle in every TFC locale that has it. */
    static void writeLang(TfcCheckout tfc, OutputTree out) {
        for (String f : tfc.list(LANG)) {
            if (!f.endsWith(".json")) {
                continue;
            }
            String locale = f.substring(LANG.length(), f.length() - ".json".length());
            JsonObject src = Json.parse(tfc.readString(f), f).getAsJsonObject();
            if (src.has(SUBTITLE_TFC)) {
                JsonObject o = new JsonObject();
                o.addProperty(SUBTITLE, src.get(SUBTITLE_TFC).getAsString());
                out.put(SOUND_RP + "lang/" + locale + ".json", Json.write(o));
            }
        }
    }

    /** The id the plugin plays. */
    public static String soundId() {
        return SOUND_NS + ":" + SOUND_EVENT;
    }
}
