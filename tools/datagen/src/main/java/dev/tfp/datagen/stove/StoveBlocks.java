/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/StoveBlock.java @ c3b4d9536 (FACING, BASE_SHAPE, the states),
 *       src/main/java/net/dries007/tfc/common/blocks/devices/StovePotBlock.java @ c3b4d9536 (POT_SHAPE, the states),
 *       src/main/java/net/dries007/tfc/common/blocks/TFCBlocks.java @ c3b4d9536 (STOVE, STOVE_POT: strength 3, METAL,
 *       lightLevel(litBlockEmission(15))),
 *       src/main/resources/assets/tfc/blockstates/stove.json, stove_pot.json and models/block/stove_{lit,unlit}.json,
 *       pot_stove.json, models/item/stove.json and the stove textures @ c3b4d9536 (copied, the pot parts merged),
 *       src/main/resources/data/tfc/loot_table/blocks/stove.json, stove_pot.json @ c3b4d9536 (the drops),
 *       src/main/resources/assets/tfc/lang/*.json @ c3b4d9536 (block.tfc.stove, block.tfc.stove_pot)
 */
package dev.tfp.datagen.stove;

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
import dev.tfp.datagen.device.DeviceLoot;
import dev.tfp.datagen.device.DeviceProperty;
import dev.tfp.datagen.device.DeviceSound;
import dev.tfp.datagen.device.DeviceYaml;
import dev.tfp.datagen.hitbox.HitboxAllocation;
import dev.tfp.datagen.hitbox.HitboxDemands;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PAR-HEAT-31: the stove and the stove pot as the CraftEngine blocks {@code tfp:stove} and {@code tfp:stove_pot}
 * ({@code craftengine/tfp/configuration/stove/stove.yml} and {@code stove_pot.yml}), run by the firepit and pot device kinds
 * ({@code paper/firepit}, {@code paper/pot}).
 *
 * <p>TFC's {@code StoveBlock extends FirepitBlock} and {@code StovePotBlock extends PotBlock}; each adds the horizontal
 * {@code facing} to the firepit's {@code lit}, {@code smoke_level} (0 to 4) and {@code axis}: 4 x 2 x 5 x 2 = 80 server
 * states. The blockstates draw only {@code lit} and {@code facing} (a model turned by y per facing, the pot as a multipart
 * with the stove's own lit part), so every (lit, facing) pair is one appearance ({@link HitboxDemands#stoveAppearance}).
 *
 * <p>Look: as the firepit and the grill, every appearance is hitbox-only on the carrier the selector agreed for TFC's own
 * outline ({@code BASE_SHAPE}, and with the pot box {@code POT_SHAPE}), drawn empty, and TFC's model is an
 * {@code entity_renderer} item display turned per facing. The stove pot's multipart ({@code pot_stove} over {@code stove_lit}
 * or {@code stove_unlit}) is merged into one model per lit value, as the grill's.
 */
public final class StoveBlocks {
    public static final String STOVE = HitboxDemands.STOVE_ID;
    public static final String STOVE_POT = HitboxDemands.STOVE_POT_ID;
    static final String D = "src/main/java/net/dries007/tfc/common/blocks/devices/";
    static final String STOVE_SOURCE = D + "StoveBlock.java";
    static final String STOVE_POT_SOURCE = D + "StovePotBlock.java";
    static final String TFC_BLOCKS = "src/main/java/net/dries007/tfc/common/blocks/TFCBlocks.java";
    static final String STOVE_STATE = TfcCheckout.ASSETS + "blockstates/stove.json";
    static final String STOVE_POT_STATE = TfcCheckout.ASSETS + "blockstates/stove_pot.json";
    static final String MODELS = TfcCheckout.ASSETS + "models/";
    static final String TEXTURES = TfcCheckout.ASSETS + "textures/";
    static final String LANG = TfcCheckout.ASSETS + "lang/";
    static final String PACK = "craftengine/tfp/";
    public static final String STOVE_OUTPUT = PACK + "configuration/stove/stove.yml";
    public static final String STOVE_POT_OUTPUT = PACK + "configuration/stove/stove_pot.yml";
    static final String RP = PACK + "resourcepack/assets/tfp/";
    static final String LANG_RP = PACK + "resourcepack/assets/tfp_stove/lang/";
    /** TFC {@code litBlockEmission(15)}. */
    static final int LIT_LIGHT = HitboxDemands.FIREPIT_LIT_LIGHT;
    public static final String STOVE_LANG_KEY = "block.tfp.stove";
    public static final String STOVE_POT_LANG_KEY = "block.tfp.stove_pot";
    private static final Pattern TEXTURE = Pattern.compile(Json.FROM_NS + ":(block/[a-z0-9_/]+|item/[a-z0-9_/]+)");

    private StoveBlocks() {
    }

    /** True when the checkout has the stove (the small test fixtures do not). */
    public static boolean available(TfcCheckout tfc) {
        return tfc.exists(STOVE_SOURCE) && tfc.exists(STOVE_STATE) && tfc.exists(MODELS + "block/stove_lit.json")
            && tfc.exists(MODELS + "block/pot_stove.json");
    }

    /** Writes both entries, their models, textures and names. Returns the number of server states of one block (80). */
    public static int run(TfcCheckout tfc, OutputTree out) {
        checkSources(tfc);
        DeviceBlockSpec stove = stoveSpec(tfc);
        DeviceBlockSpec pot = stovePotSpec(tfc);
        out.put(STOVE_OUTPUT, Yaml.write(DeviceYaml.HEADER, DeviceYaml.templated(stove)));
        out.put(STOVE_POT_OUTPUT, Yaml.write(DeviceYaml.HEADER, DeviceYaml.templated(pot)));
        writeModels(tfc, out);
        writeLang(tfc, out);
        return (int) stove.stateCount();
    }

    /** The block state properties, in a stable order: facing, then the firepit's lit, smoke_level, axis. */
    static List<DeviceProperty> props() {
        return List.of(DeviceProperty.names("facing", HitboxDemands.FACINGS, "north"), DeviceProperty.bool("lit", false),
            DeviceProperty.range("smoke_level", 0, 4, 0), DeviceProperty.names("axis", List.of("x", "z"), "x"));
    }

    /**
     * TFC's classes: both extend the firepit family, add {@code FACING} (horizontal facing, default north) to the state, take
     * the shapes datagen drew its outlines from, and light up to 15.
     */
    static void checkSources(TfcCheckout tfc) {
        String stove = tfc.readString(STOVE_SOURCE).replaceAll("\\s+", " ");
        String pot = tfc.readString(STOVE_POT_SOURCE).replaceAll("\\s+", " ");
        for (String needle : List.of("class StoveBlock extends FirepitBlock",
                "EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;",
                "BASE_SHAPE = box(1, 0, 1, 15, 12, 15);", "setValue(FACING, Direction.NORTH)", "builder.add(FACING);",
                "stack.getItem() == TFCItems.WROUGHT_IRON_GRILL.get()", "TFCBlocks.STOVE_POT.get()")) {
            require(stove, needle, STOVE_SOURCE);
        }
        for (String needle : List.of("class StovePotBlock extends PotBlock",
                "EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;",
                "Shapes.or(StoveBlock.BASE_SHAPE, box(2, 12, 2, 14, 16, 14))", "setValue(FACING, Direction.NORTH)",
                "builder.add(FACING);", "TFCBlocks.STOVE.get()")) {
            require(pot, needle, STOVE_POT_SOURCE);
        }
        String blocks = tfc.readString(TFC_BLOCKS).replaceAll("\\s+", " ");
        for (String block : List.of("stove", "stove_pot")) {
            if (!Pattern.compile("register\\(\"" + block + "\", \\(\\) -> new \\w+\\(ExtendedProperties\\.of\\(MapColor\\.METAL\\)"
                + "\\.strength\\(3F?\\)\\.sound\\(SoundType\\.METAL\\)\\.randomTicks\\(\\)\\.noOcclusion\\(\\)"
                + "\\.lightLevel\\(litBlockEmission\\(" + LIT_LIGHT + "\\)\\)").matcher(blocks).find()) {
                throw new DatagenException(TFC_BLOCKS + ": the " + block + " registration changed");
            }
        }
    }

    private static void require(String text, String needle, String source) {
        if (!text.contains(needle)) {
            throw new DatagenException(source + " no longer contains \"" + needle + "\": re-check the stove entries");
        }
    }

    /** The carrier of an appearance: must be hitbox-only (the look is the display). */
    static HitboxAllocation.Assignment carrier(String id, boolean lit, String facing) {
        String app = HitboxDemands.stoveAppearance(lit, facing);
        HitboxAllocation.Assignment a = HitboxAllocation.agreed(id, app);
        if (a.mode() != HitboxAllocation.Mode.HITBOX_ONLY) {
            throw new DatagenException(id + "#" + app + ": the carrier is " + a.mode()
                + ", the stove draws TFC's model as an item display over an empty carrier");
        }
        return a;
    }

    /** The display-only item of a model ({@code tfp:render/stove_lit}). */
    static String renderItem(String model) {
        return Json.TO_NS + ":render/" + model.substring(model.indexOf(":block/") + ":block/".length());
    }

    /** The display turn of a facing: an item display draws a model a half turn about y against a block, so {@code 180 - y}. */
    static String rotation(String facing) {
        return "0," + (180 - HitboxDemands.stoveRotation(facing)) + ",0";
    }

    static DeviceBlockSpec stoveSpec(TfcCheckout tfc) {
        return spec(tfc, STOVE, "stove", HitboxDemands::stoveModel, STOVE_LANG_KEY);
    }

    static DeviceBlockSpec stovePotSpec(TfcCheckout tfc) {
        return spec(tfc, STOVE_POT, "stove_pot", HitboxDemands::stovePotModel, null);
    }

    /** One entry: an appearance per (lit, facing) on its hitbox-only carrier with the display of the lit value's model. */
    static DeviceBlockSpec spec(TfcCheckout tfc, String id, String block, java.util.function.Function<Boolean, String> model,
            String itemLangKey) {
        Map<String, String> hides = new TreeMap<>();
        DeviceBlockSpec.Builder b = DeviceBlockSpec.builder(id).properties(props());
        for (boolean lit : new boolean[] {true, false}) {
            b.renderItem(renderItem(model.apply(lit)), model.apply(lit));
        }
        for (boolean lit : new boolean[] {true, false}) {
            for (String facing : HitboxDemands.FACINGS) {
                HitboxAllocation.Assignment a = carrier(id, lit, facing);
                if (a.hideTarget() != null) {
                    hides.put(a.state(), a.hideTarget());
                }
                b.appearance(DeviceAppearance.rendered(HitboxDemands.stoveAppearance(lit, facing),
                    DeviceAppearance.Carrier.state(a.state()),
                    List.of(DeviceAppearance.Renderer.item(renderItem(model.apply(lit))).rotated(rotation(facing)))));
            }
        }
        b.everyState(s -> HitboxDemands.stoveAppearance("true".equals(s.get("lit")), s.get("facing")),
                s -> "true".equals(s.get("lit")) ? LIT_LIGHT : 0)
            .settings(settings(tfc, block))
            .loot(DeviceLoot.fromTfc(tfc, block))
            .deviceBehaviour();
        hides.forEach(b::stateMapping);
        if (itemLangKey != null) {
            b.blockItem(itemLangKey);
        }
        return b.build();
    }

    /**
     * TFC's settings of {@code tfc:<block>}: strength 3 and {@code SoundType.METAL} (checked in {@link #checkSources}), the
     * pickaxe tag. Not {@code Settings.fromTfc}: TFC's generated {@code mineable/pickaxe.json} lists the stove twice, which
     * that reader refuses as "two tags".
     */
    static DeviceBlockSpec.Settings settings(TfcCheckout tfc, String block) {
        String src = TfcCheckout.GENERATED + "data/minecraft/tags/block/mineable/pickaxe.json";
        boolean pickaxe = false;
        for (JsonElement v : Json.parse(tfc.readString(src), src).getAsJsonObject().getAsJsonArray("values")) {
            pickaxe |= v.isJsonPrimitive() && v.getAsString().equals(Json.FROM_NS + ":" + block);
        }
        if (!pickaxe) {
            throw new DatagenException(src + " no longer holds tfc:" + block);
        }
        return DeviceBlockSpec.Settings.of("3", "3", DeviceSound.METAL, "pickaxe");
    }

    // ---- models ---------------------------------------------------------------------------------------

    /** The facing-east parts (no y turn) of a multipart blockstate that apply to a lit value, as TFC model ids. */
    static List<String> parts(TfcCheckout tfc, boolean lit) {
        JsonObject root = Json.parse(tfc.readString(STOVE_POT_STATE), STOVE_POT_STATE).getAsJsonObject();
        List<String> out = new ArrayList<>();
        for (JsonElement part : root.getAsJsonArray("multipart")) {
            JsonObject p = part.getAsJsonObject();
            JsonObject when = p.has("when") ? p.getAsJsonObject("when") : new JsonObject();
            if (when.has("facing") && !when.get("facing").getAsString().equals("east")) {
                continue;
            }
            if (when.has("lit") && when.get("lit").getAsBoolean() != lit) {
                continue;
            }
            JsonObject apply = p.getAsJsonObject("apply");
            if (apply.has("y") && apply.get("y").getAsInt() != 0) {
                throw new DatagenException(STOVE_POT_STATE + ": a facing-east part is turned, the merged model cannot be");
            }
            String model = apply.get("model").getAsString();
            if (!model.startsWith(Json.FROM_NS + ":block/")) {
                throw new DatagenException(STOVE_POT_STATE + ": part model " + model + " is not a TFC block model");
            }
            out.add(model.substring(Json.FROM_NS.length() + 1));
        }
        return out;
    }

    /** TFC's stove model of a lit value, checked against the stove blockstate (facing east: no turn). */
    static String stoveModelName(TfcCheckout tfc, boolean lit) {
        JsonObject variants = Json.parse(tfc.readString(STOVE_STATE), STOVE_STATE).getAsJsonObject().getAsJsonObject("variants");
        JsonObject v = variants.getAsJsonObject("lit=" + lit + ",facing=east");
        if (v == null || v.has("y")) {
            throw new DatagenException(STOVE_STATE + ": no unturned variant lit=" + lit + ",facing=east");
        }
        String want = Json.FROM_NS + ":block/stove_" + (lit ? "lit" : "unlit");
        if (!want.equals(v.get("model").getAsString())) {
            throw new DatagenException(STOVE_STATE + ": lit=" + lit + " no longer draws " + want);
        }
        return "block/stove_" + (lit ? "lit" : "unlit");
    }

    /**
     * The merged model of a lit value of the pot: the elements of every part in part order, the textures of every part
     * (the first part's {@code particle} wins), the render type and the parent of the first part that has one; renamed.
     */
    static JsonObject merged(TfcCheckout tfc, boolean lit) {
        JsonObject textures = new JsonObject();
        JsonArray elements = new JsonArray();
        String parent = null;
        String renderType = null;
        for (String part : parts(tfc, lit)) {
            String src = MODELS + part + ".json";
            JsonObject json = Json.parse(tfc.readString(src), src).getAsJsonObject();
            if (json.has("loader")) {
                throw new DatagenException(src + ": model loader " + json.get("loader") + " has no converter");
            }
            if (parent == null && json.has("parent")) {
                parent = json.get("parent").getAsString();
            }
            if (renderType == null && json.has("render_type")) {
                renderType = json.get("render_type").getAsString();
            }
            if (json.has("textures")) {
                for (Map.Entry<String, JsonElement> t : json.getAsJsonObject("textures").entrySet()) {
                    JsonElement prev = textures.get(t.getKey());
                    if (prev == null) {
                        textures.add(t.getKey(), t.getValue());
                    } else if (!t.getKey().equals("particle") && !prev.equals(t.getValue())) {
                        throw new DatagenException(src + ": texture #" + t.getKey() + " differs between the stove pot's parts");
                    }
                }
            }
            if (json.has("elements")) {
                json.getAsJsonArray("elements").forEach(elements::add);
            }
        }
        JsonObject model = new JsonObject();
        if (parent != null) {
            model.addProperty("parent", parent);
        }
        if (renderType != null) {
            model.addProperty("render_type", renderType);
        }
        model.add("textures", textures);
        model.add("elements", elements);
        return Json.rename(model).getAsJsonObject();
    }

    /** The pack models by output path: the stove's two, the stove pot's two merged ones, and the item model. */
    static Map<String, JsonObject> models(TfcCheckout tfc) {
        Map<String, JsonObject> out = new LinkedHashMap<>();
        for (boolean lit : new boolean[] {true, false}) {
            String name = stoveModelName(tfc, lit);
            String src = MODELS + name + ".json";
            JsonObject m = Json.parse(tfc.readString(src), src).getAsJsonObject();
            m.remove("__comment__");
            out.put(RP + "models/" + name + ".json", Json.rename(m).getAsJsonObject());
            String id = HitboxDemands.stovePotModel(lit);
            out.put(RP + "models/" + id.substring(id.indexOf(':') + 1) + ".json", merged(tfc, lit));
        }
        String item = MODELS + "item/stove.json";
        JsonObject m = Json.parse(tfc.readString(item), item).getAsJsonObject();
        m.remove("__comment__");
        out.put(RP + "models/item/stove.json", Json.rename(m).getAsJsonObject());
        return out;
    }

    /** Writes the models and the TFC textures they name (with their {@code .mcmeta}); vanilla textures stay vanilla. */
    static void writeModels(TfcCheckout tfc, OutputTree out) {
        TreeSet<String> textures = new TreeSet<>();
        for (Map.Entry<String, JsonObject> e : models(tfc).entrySet()) {
            out.put(e.getKey(), Json.write(e.getValue()));
            JsonObject t = e.getValue().getAsJsonObject("textures");
            if (t == null) {
                continue;
            }
            for (Map.Entry<String, JsonElement> x : t.entrySet()) {
                String ref = x.getValue().getAsString();
                if (ref.startsWith(Json.TO_NS + ":")) {
                    Matcher m = TEXTURE.matcher(Json.FROM_NS + ref.substring(Json.TO_NS.length()));
                    if (!m.matches()) {
                        throw new DatagenException("stove model texture " + ref + " is not a block or item texture");
                    }
                    textures.add(m.group(1));
                }
            }
        }
        for (String t : textures) {
            String png = TEXTURES + t + ".png";
            if (!tfc.exists(png)) {
                throw new DatagenException("TFC has no texture " + t);
            }
            out.put(RP + "textures/" + t + ".png", tfc.read(png));
            if (tfc.exists(png + ".mcmeta")) {
                out.put(RP + "textures/" + t + ".png.mcmeta", tfc.read(png + ".mcmeta"));
            }
        }
    }

    /** {@code block.tfp.stove} and {@code block.tfp.stove_pot} in every TFC locale that names them (English must). */
    static void writeLang(TfcCheckout tfc, OutputTree out) {
        for (String f : tfc.list(LANG)) {
            if (!f.endsWith(".json")) {
                continue;
            }
            String locale = f.substring(LANG.length(), f.length() - ".json".length());
            JsonObject src = Json.parse(tfc.readString(f), f).getAsJsonObject();
            Map<String, String> keys = new TreeMap<>();
            for (String[] k : new String[][] {{"block.tfc.stove", STOVE_LANG_KEY}, {"block.tfc.stove_pot", STOVE_POT_LANG_KEY}}) {
                if (src.has(k[0])) {
                    keys.put(k[1], src.get(k[0]).getAsString());
                } else if (locale.equals("en_us")) {
                    throw new DatagenException("TFC has no English name " + k[0]);
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
