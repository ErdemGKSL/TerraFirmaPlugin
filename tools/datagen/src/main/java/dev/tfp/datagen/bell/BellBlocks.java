/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/TFCBellBlock.java @ c3b4d9536 (pitch, the bell texture),
 *       src/main/java/net/dries007/tfc/common/blocks/TFCBlocks.java @ c3b4d9536 (BRONZE_BELL pitch 0.8, BRASS_BELL pitch 0.6:
 *       strength(5.0F), SoundType.ANVIL, requiresCorrectToolForDrops),
 *       src/main/java/net/dries007/tfc/client/render/blockentity/TFCBellBlockEntityRenderer.java @ c3b4d9536 (the bell body),
 *       src/main/resources/assets/tfc/blockstates/{bronze,brass}_bell.json, models/block/bell/*.json, models/item/*_bell.json,
 *       textures/entity/bell/{bronze,brass}.png, textures/item/*_bell.png @ c3b4d9536 (copied),
 *       src/main/resources/data/tfc/loot_table/blocks/{bronze,brass}_bell.json @ c3b4d9536 (the block drops itself),
 *       src/main/resources/assets/tfc/lang/*.json @ c3b4d9536 (block.tfc.bronze_bell, block.tfc.brass_bell)
 */
package dev.tfp.datagen.bell;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.tfp.datagen.DatagenException;
import dev.tfp.datagen.Json;
import dev.tfp.datagen.OutputTree;
import dev.tfp.datagen.TfcCheckout;
import dev.tfp.datagen.Yaml;
import dev.tfp.datagen.crop.CropBlocks;
import dev.tfp.datagen.device.DeviceAppearance;
import dev.tfp.datagen.device.DeviceBlockSpec;
import dev.tfp.datagen.device.DeviceLoot;
import dev.tfp.datagen.device.DeviceProperty;
import dev.tfp.datagen.device.DeviceSound;
import dev.tfp.datagen.device.DeviceYaml;
import dev.tfp.datagen.hitbox.HitboxAllocation;
import dev.tfp.datagen.hitbox.HitboxDemands;
import dev.tfp.datagen.hitbox.TfcShapes;
import dev.tfp.datagen.rendered.RenderedSound;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PAR-METAL-18: the bronze and brass bells as the CraftEngine blocks {@code tfp:bronze_bell} and {@code tfp:brass_bell}
 * ({@code configuration/bell/bell.yml}).
 *
 * <ul>
 *   <li>States: vanilla {@code BellBlock}'s {@code attachment} (floor, ceiling, single_wall, double_wall) and {@code facing}
 *       (16 states per bell; {@code powered} is left out, see the ledger). The placement turn is the plugin's.</li>
 *   <li>Hitbox: vanilla's {@code getVoxelShape} on a transparent carrier (nine distinct outlines, see
 *       {@link TfcShapes#bellShape}). Look: TFC's blockstate as item displays: the frame model (vanilla {@code bell_floor},
 *       {@code bell_ceiling}, {@code bell_single_wall}, {@code bell_between_walls} with TFC's bar and post textures, turned by
 *       the blockstate's {@code y}) and the bell body, which TFC's block entity renderer draws from vanilla's {@code bell_body}
 *       model part (a 6x7x6 box and an 8x2x8 lip on the 32x32 metal texture): here a model of two boxes.</li>
 *   <li>TFC's settings (strength 5, {@code SoundType.ANVIL}, pickaxe, correct tool for drops), loot (themselves) and a block
 *       item with TFC's flat item model.</li>
 * </ul>
 */
public final class BellBlocks {
    static final String BLOCK_SRC = "src/main/java/net/dries007/tfc/common/blocks/TFCBellBlock.java";
    static final String TFC_BLOCKS = "src/main/java/net/dries007/tfc/common/blocks/TFCBlocks.java";
    static final String ASSETS = TfcCheckout.ASSETS;
    static final String LANG = ASSETS + "lang/";
    static final String PACK = "craftengine/tfp/";
    public static final String OUTPUT = PACK + "configuration/bell/bell.yml";
    static final String RP = PACK + "resourcepack/assets/tfp/";
    static final String LANG_RP = PACK + "resourcepack/assets/tfp_bell/lang/";
    public static final List<String> ATTACHMENTS = List.of("floor", "ceiling", "single_wall", "double_wall");
    public static final List<String> FACINGS = List.of("north", "east", "south", "west");
    /** TFC's constructor pitch of each bell ({@code TFCBellBlock(properties, pitch, texture)}). */
    public static final Map<String, String> PITCH = Map.of("bronze", "0.8", "brass", "0.6");
    private static final List<String> SOUND_EVENTS = List.of("break", "step", "place", "hit", "fall");
    /** Vanilla {@code SoundType.ANVIL}: volume 0.3, pitch 1.0. */
    private static final RenderedSound ANVIL_SOUND = new RenderedSound(new BigDecimal("0.3"), BigDecimal.ONE);
    private static final Pattern REGISTER = Pattern.compile("register\\(\"(bronze|brass)_bell\", \\(\\) -> new TFCBellBlock\\("
        + "ExtendedProperties\\.of\\(MapColor\\.GOLD\\)\\.requiresCorrectToolForDrops\\(\\)\\.strength\\(5\\.0F\\)"
        + "\\.sound\\(SoundType\\.ANVIL\\)[^;]*?, ([0-9.]+)f, \"(bronze|brass)\"\\)\\);");

    /** Our frame model of an attachment, a copy of TFC's {@code block/bell/<metal>_<model>}. */
    static final Map<String, String> FRAME_MODEL = Map.of("floor", "floor", "ceiling", "ceiling", "single_wall", "single_wall",
        "double_wall", "double_wall");

    private BellBlocks() {
    }

    public static String id(String metal) {
        return HitboxDemands.bellId(metal);
    }

    public static String langKey(String metal) {
        return "block." + Json.TO_NS + "." + metal + "_bell";
    }

    static String frameModel(String metal, String attachment) {
        return Json.TO_NS + ":block/bell/" + metal + "_" + FRAME_MODEL.get(attachment);
    }

    static String bodyModel(String metal) {
        return Json.TO_NS + ":block/bell/" + metal + "_body";
    }

    static String renderItem(String model) {
        return Json.TO_NS + ":render/" + model.substring(model.indexOf(":block/") + ":block/".length());
    }

    /** True when the checkout has the bells (the small test fixtures do not). */
    public static boolean available(TfcCheckout tfc) {
        return tfc.exists(BLOCK_SRC) && tfc.exists(ASSETS + "blockstates/brass_bell.json");
    }

    /** Writes the entries, models, textures and names. Returns the number of server states (2 bells x 16). */
    public static int run(TfcCheckout tfc, OutputTree out) {
        checkSource(tfc);
        Map<String, Object> blocks = new LinkedHashMap<>();
        Map<String, Object> items = new LinkedHashMap<>();
        Map<String, Object> mappings = new TreeMap<>();
        Set<String> copied = new TreeSet<>();
        int states = 0;
        for (String metal : TfcShapes.BELL_METALS) {
            Map<String, Integer> turns = checkBlockstate(tfc, metal);
            copyModels(tfc, out, metal, copied);
            DeviceBlockSpec spec = spec(tfc, metal, turns);
            states += (int) spec.stateCount();
            merge(yaml(spec), blocks, items, mappings);
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("blocks", blocks);
        root.put("items", items);
        if (!mappings.isEmpty()) {
            root.put("block_state_mappings", mappings);
        }
        out.put(OUTPUT, Yaml.write(DeviceYaml.HEADER, root));
        writeLang(tfc, out);
        return states;
    }

    // ---- TFC's source ---------------------------------------------------------------------------------

    /** TFC's registrations (pitch, strength, sound) and the bell block's class. */
    static void checkSource(TfcCheckout tfc) {
        String src = tfc.readString(BLOCK_SRC).replaceAll("\\s+", " ");
        for (String needle : List.of("public class TFCBellBlock extends BellBlock",
                "level.playSound(null, pos, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 2.0F, hard ? pitch - 0.1f : pitch);",
                "Helpers.isItem(player.getMainHandItem(), TFCTags.Items.TOOLS_HAMMER)")) {
            if (!src.contains(needle.replaceAll("\\s+", " "))) {
                throw new DatagenException(BLOCK_SRC + " no longer contains \"" + needle + "\": re-check the bell entry");
            }
        }
        Map<String, String> found = new LinkedHashMap<>();
        Matcher m = REGISTER.matcher(tfc.readString(TFC_BLOCKS).replaceAll("\\s+", " "));
        while (m.find()) {
            if (!m.group(1).equals(m.group(3))) {
                throw new DatagenException(TFC_BLOCKS + ": " + m.group(1) + "_bell names the texture " + m.group(3));
            }
            found.put(m.group(1), m.group(2));
        }
        if (!found.equals(PITCH)) {
            throw new DatagenException(TFC_BLOCKS + " registers the bells " + found + ", BellBlocks expects " + PITCH);
        }
    }

    /**
     * TFC's blockstate of a bell: every (attachment, facing) names the model of the attachment and the {@code y} of the facing
     * (north 0, east 90, south 180, west 270 for the floor, the walls and the ceiling; the models are vanilla's, drawn for
     * facing north). Returns the turn of each {@code attachment/facing}.
     */
    static Map<String, Integer> checkBlockstate(TfcCheckout tfc, String metal) {
        String src = ASSETS + "blockstates/" + metal + "_bell.json";
        JsonObject variants = Json.parse(tfc.readString(src), src).getAsJsonObject().getAsJsonObject("variants");
        Map<String, Integer> turns = new LinkedHashMap<>();
        for (String attachment : ATTACHMENTS) {
            for (String facing : FACINGS) {
                String key = "attachment=" + attachment + ",facing=" + facing;
                if (variants == null || !variants.has(key)) {
                    throw new DatagenException(src + " has no variant " + key);
                }
                JsonObject v = variants.getAsJsonObject(key);
                String want = Json.FROM_NS + ":block/bell/" + metal + "_" + attachment;
                if (!v.get("model").getAsString().equals(want)) {
                    throw new DatagenException(src + " " + key + " names " + v.get("model") + ", expected " + want);
                }
                turns.put(attachment + "/" + facing, v.has("y") ? v.get("y").getAsInt() : 0);
            }
        }
        if (variants.size() != ATTACHMENTS.size() * FACINGS.size()) {
            throw new DatagenException(src + " has " + variants.size() + " variants, expected 16");
        }
        return turns;
    }

    // ---- the specs -----------------------------------------------------------------------------------------

    static String appearanceName(String attachment, String facing) {
        return attachment + "_" + facing;
    }

    static DeviceBlockSpec spec(TfcCheckout tfc, String metal, Map<String, Integer> turns) {
        String id = id(metal);
        DeviceBlockSpec.Builder b = DeviceBlockSpec.builder(id)
            .property(DeviceProperty.names("attachment", ATTACHMENTS, "floor"))
            .property(DeviceProperty.names("facing", FACINGS, "north"));
        Set<String> renderItems = new LinkedHashSet<>();
        Map<String, String> hidden = new TreeMap<>();
        for (String attachment : ATTACHMENTS) {
            for (String facing : FACINGS) {
                String cls = HitboxDemands.bellOutline(attachment, facing);
                HitboxAllocation.Assignment a = HitboxAllocation.agreed(id, cls);
                if (a.mode() != HitboxAllocation.Mode.HITBOX_ONLY) {
                    throw new DatagenException(id + "#" + cls + " got a " + a.mode() + " carrier; BellBlocks draws item displays");
                }
                if (a.hideTarget() != null && !a.pool() && hidden.putIfAbsent(a.state(), a.hideTarget()) == null) {
                    b.stateMapping(a.state(), a.hideTarget());
                }
                List<DeviceAppearance.Renderer> r = new ArrayList<>();
                r.add(part(b, renderItems, frameModel(metal, attachment), turns.get(attachment + "/" + facing)));
                r.add(part(b, renderItems, bodyModel(metal), 0));
                b.appearance(DeviceAppearance.rendered(appearanceName(attachment, facing),
                    DeviceAppearance.Carrier.state(a.state()), r));
            }
        }
        return b.everyState(s -> appearanceName(s.get("attachment"), s.get("facing")), null)
            .settings(DeviceBlockSpec.Settings.of("5.0", "5.0", DeviceSound.METAL, "pickaxe"))
            .loot(DeviceLoot.self())
            .deviceBehaviour()
            .blockItem(langKey(metal))
            .build();
    }

    private static DeviceAppearance.Renderer part(DeviceBlockSpec.Builder b, Set<String> items, String model, int y) {
        String item = renderItem(model);
        if (items.add(item)) {
            b.renderItem(item, model);
        }
        return DeviceAppearance.Renderer.item(item).rotated(CropBlocks.rotation(y));
    }

    // ---- the yaml -------------------------------------------------------------------------------------

    /** The templated entry with {@code SoundType.ANVIL}'s events and the pickaxes as the correct tools. */
    @SuppressWarnings("unchecked")
    static Map<String, Object> yaml(DeviceBlockSpec spec) {
        Map<String, Object> root = DeviceYaml.templated(spec);
        Map<String, Object> block = (Map<String, Object>) ((Map<String, Object>) root.get("blocks")).get(spec.id());
        Map<String, Object> args = (Map<String, Object>) block.get("arguments");
        for (String event : SOUND_EVENTS) {
            String key = "device_sound_" + event;
            if (!args.containsKey(key)) {
                throw new DatagenException("the device template has no argument " + key + ": re-check BellBlocks' sounds");
            }
            Map<String, Object> sound = new LinkedHashMap<>();
            sound.put("id", "minecraft:block.anvil." + event);
            sound.put("volume", ANVIL_SOUND.volume(event));
            sound.put("pitch", ANVIL_SOUND.pitch(event));
            args.put(key, sound);
        }
        block.put("settings", Map.of("correct_tools", List.of("#minecraft:pickaxes", "#tfp:tools/pickaxe")));
        return root;
    }

    @SuppressWarnings("unchecked")
    private static void merge(Map<String, Object> one, Map<String, Object> blocks, Map<String, Object> items,
            Map<String, Object> mappings) {
        blocks.putAll((Map<String, Object>) one.get("blocks"));
        items.putAll((Map<String, Object>) one.getOrDefault("items", Map.of()));
        for (Map.Entry<String, Object> e : ((Map<String, Object>) one.getOrDefault("block_state_mappings", Map.of())).entrySet()) {
            Object old = mappings.putIfAbsent(e.getKey(), e.getValue());
            if (old != null && !old.equals(e.getValue())) {
                throw new DatagenException("bell carrier " + e.getKey() + " hidden as " + old + " and " + e.getValue());
            }
        }
    }

    // ---- models, textures, names ----------------------------------------------------------------------

    static void copyModels(TfcCheckout tfc, OutputTree out, String metal, Set<String> copied) {
        for (String attachment : ATTACHMENTS) {
            CropBlocks.copyModel(tfc, out, Json.FROM_NS + ":block/bell/" + metal + "_" + attachment, copied);
        }
        // the flat item model: TFC's own (item/generated over textures/item/<metal>_bell)
        String itemSrc = ASSETS + "models/item/" + metal + "_bell.json";
        JsonObject item = Json.parse(tfc.readString(itemSrc), itemSrc).getAsJsonObject();
        String tex = item.getAsJsonObject("textures").get("layer0").getAsString();
        String png = ASSETS + "textures/" + tex.substring(Json.FROM_NS.length() + 1) + ".png";
        if (!tfc.exists(png)) {
            throw new DatagenException(itemSrc + " uses missing texture " + tex);
        }
        out.put(RP + "textures/" + tex.substring(Json.FROM_NS.length() + 1) + ".png", tfc.read(png));
        out.put(RP + "models/item/" + metal + "_bell.json", Json.write(Json.rename(item)));
        // the bell body: the entity texture goes to the block atlas path our model names
        String body = ASSETS + "textures/entity/bell/" + metal + ".png";
        if (!tfc.exists(body)) {
            throw new DatagenException("TFC has no bell texture " + body);
        }
        out.put(RP + "textures/block/bell/" + metal + "_body.png", tfc.read(body));
        out.put(RP + "models/block/bell/" + metal + "_body.json", Json.write(bodyJson(metal)));
    }

    /**
     * Vanilla {@code BellModel}'s body part as a block model: the box (5,6,5)-(11,13,11) with texOffs (0,0) and the lip
     * (4,4,4)-(12,6,12) with texOffs (0,13), on the 32x32 texture ({@code uv} is in 16ths of the texture).
     */
    static JsonObject bodyJson(String metal) {
        String tex = Json.TO_NS + ":block/bell/" + metal + "_body";
        JsonObject m = new JsonObject();
        m.addProperty("ambientocclusion", false);
        JsonObject textures = new JsonObject();
        textures.addProperty("0", tex);
        textures.addProperty("particle", tex);
        m.add("textures", textures);
        JsonArray elements = new JsonArray();
        elements.add(box(5, 6, 5, 11, 13, 11, 0, 0));
        elements.add(box(4, 4, 4, 12, 6, 12, 0, 13));
        m.add("elements", elements);
        return m;
    }

    /** A cuboid with the box UV layout of {@code ModelPart.Cube} (top row: top, bottom; then west, north, east, south). */
    static JsonObject box(int x1, int y1, int z1, int x2, int y2, int z2, int u0, int v0) {
        int w = x2 - x1;
        int h = y2 - y1;
        int d = z2 - z1;
        JsonObject e = new JsonObject();
        e.add("from", array(x1, y1, z1));
        e.add("to", array(x2, y2, z2));
        JsonObject faces = new JsonObject();
        faces.add("up", face(u0 + d, v0, u0 + d + w, v0 + d));
        faces.add("down", face(u0 + d + w, v0 + d, u0 + d + 2 * w, v0));
        faces.add("west", face(u0, v0 + d, u0 + d, v0 + d + h));
        faces.add("north", face(u0 + d, v0 + d, u0 + d + w, v0 + d + h));
        faces.add("east", face(u0 + d + w, v0 + d, u0 + 2 * d + w, v0 + d + h));
        faces.add("south", face(u0 + 2 * d + w, v0 + d, u0 + 2 * d + 2 * w, v0 + d + h));
        e.add("faces", faces);
        return e;
    }

    private static JsonObject face(int u1, int v1, int u2, int v2) {
        JsonObject f = new JsonObject();
        f.add("uv", array(u1 / 2.0, v1 / 2.0, u2 / 2.0, v2 / 2.0));
        f.addProperty("texture", "#0");
        return f;
    }

    private static JsonArray array(double... v) {
        JsonArray a = new JsonArray();
        for (double d : v) {
            a.add(d);
        }
        return a;
    }

    /** {@code block.tfp.bronze_bell} and {@code block.tfp.brass_bell} in every TFC locale that names them (English must). */
    static void writeLang(TfcCheckout tfc, OutputTree out) {
        for (String f : tfc.list(LANG)) {
            if (!f.endsWith(".json")) {
                continue;
            }
            String locale = f.substring(LANG.length(), f.length() - ".json".length());
            JsonObject src = Json.parse(tfc.readString(f), f).getAsJsonObject();
            Map<String, String> keys = new TreeMap<>();
            for (String metal : TfcShapes.BELL_METALS) {
                String tfcKey = "block." + Json.FROM_NS + "." + metal + "_bell";
                if (src.has(tfcKey)) {
                    keys.put(langKey(metal), src.get(tfcKey).getAsString());
                } else if (locale.equals("en_us")) {
                    throw new DatagenException("TFC has no English name " + tfcKey);
                }
            }
            if (!keys.isEmpty()) {
                JsonObject o = new JsonObject();
                keys.forEach(o::addProperty);
                out.put(LANG_RP + locale + ".json", Json.write(o));
            }
        }
    }
}
