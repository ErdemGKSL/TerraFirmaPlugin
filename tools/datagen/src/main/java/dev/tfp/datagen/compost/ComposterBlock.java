/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/TFCComposterBlock.java @ c3b4d9536 (STAGE, TYPE, CompostType,
 *       the default state stage 0 / normal),
 *       src/main/java/net/dries007/tfc/common/blocks/TFCBlockStateProperties.java @ c3b4d9536 (STAGE_8 0..8),
 *       src/main/java/net/dries007/tfc/common/blocks/TFCBlocks.java @ c3b4d9536 (COMPOSTER: strength(0.6F), SoundType.WOOD,
 *       flammable(60, 90)),
 *       src/main/resources/assets/tfc/blockstates/composter.json, models/block/composter/*.json, models/item/composter.json
 *       and textures/block/devices/composter/*.png @ c3b4d9536 (copied),
 *       src/main/resources/data/tfc/loot_table/blocks/composter.json @ c3b4d9536 (the drop),
 *       src/generated/resources/data/minecraft/tags/block/mineable/axe.json @ c3b4d9536 (the tool),
 *       src/main/resources/assets/tfc/lang/*.json @ c3b4d9536 (block.tfc.composter)
 */
package dev.tfp.datagen.compost;

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
import dev.tfp.datagen.device.DeviceYaml;
import dev.tfp.datagen.hitbox.HitboxAllocation;
import dev.tfp.datagen.hitbox.HitboxDemands;
import dev.tfp.datagen.hitbox.TfcShapes;
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
 * PAR-PROC-34: the composter as the CraftEngine block {@code tfp:composter}
 * ({@code craftengine/tfp/configuration/compost/composter.yml}). The plugin's {@code ComposterDevice} (PAR-PROC-11) runs
 * it and writes both state properties.
 *
 * <ul>
 *   <li>Properties: {@code stage} 0..8 (TFC {@code STAGE_8}, default 0) and {@code type} {@code normal|ready|rotten}
 *       (TFC {@code CompostType}, default normal): 27 server states.</li>
 *   <li>Looks: TFC's blockstate is a multipart, the bin ({@code composter/composter}) always plus the compost model
 *       {@code <type>_<stage>} from stage 1 up, 25 looks. The outline of stages 0..7 (TFC {@code SHAPES[stage]}, the well
 *       from {@code max(2, stage * 2)}) has one hitbox demand per stage with the bin model ({@link HitboxDemands}), so
 *       stages may share a carrier and the bin takes at most 8 states; the compost is an {@code entity_renderer} item
 *       display of TFC's compost model ({@code tfp:render/composter/<type>_<stage>}), and a hitbox-only carrier would draw
 *       the bin as a display too. Stage 8 is TFC's full cube: drawn from CE's solid pool
 *       ({@link HitboxDemands#COMPOSTER_POOL_STAGE}; no light reaches a display inside an opaque block), each type as one
 *       block model of the bin and the compost ({@code tfp:block/composter/filled/<type>_8}, merged from TFC's two
 *       models).</li>
 *   <li>The {@code tfp:device} behaviour through the device template, TFC's settings (strength 0.6, wood sounds, the axe
 *       tag), TFC's {@code flammable(60, 90)} as CraftEngine's burn settings, the loot (the composter itself) and the block
 *       item with TFC's item model.</li>
 * </ul>
 * TFC's {@code BottomSupportedDeviceBlock} support rule is not reproduced (ledger divergence).
 */
public final class ComposterBlock {
    public static final String ID = HitboxDemands.COMPOSTER_ID;
    static final String BLOCK = "composter";
    static final String SOURCE = "src/main/java/net/dries007/tfc/common/blocks/devices/TFCComposterBlock.java";
    static final String PROPERTIES = "src/main/java/net/dries007/tfc/common/blocks/TFCBlockStateProperties.java";
    static final String BLOCKS = "src/main/java/net/dries007/tfc/common/blocks/TFCBlocks.java";
    static final String BLOCKSTATE = TfcCheckout.ASSETS + "blockstates/composter.json";
    static final String MODELS = TfcCheckout.ASSETS + "models/";
    static final String BIN_MODEL = MODELS + "block/composter/composter.json";
    static final String ITEM_MODEL = MODELS + "item/composter.json";
    static final String TEXTURES = TfcCheckout.ASSETS + "textures/";
    static final String LANG = TfcCheckout.ASSETS + "lang/";
    static final String PACK = "craftengine/tfp/";
    public static final String OUTPUT = PACK + "configuration/compost/composter.yml";
    static final String RP = PACK + "resourcepack/assets/tfp/";
    static final String LANG_RP = PACK + "resourcepack/assets/tfp_composter/lang/";
    /** ComposterDevice.STAGE / TYPE (the plugin writes them). */
    public static final String STAGE = "stage";
    public static final String TYPE = "type";
    public static final String LANG_KEY = "block." + Json.TO_NS + "." + BLOCK;
    /** The half turn every block-model item display needs to match the block (see ForgeDevices.OVERLAY_ROTATION). */
    static final String RENDER_ROTATION = "0,180,0";
    /** The compost texture key of a merged model (TFC's bin uses {@code 0} and {@code 1}, the compost models {@code 0}). */
    static final String COMPOST_TEXTURE_KEY = "compost";
    private static final Pattern FLAMMABLE = Pattern.compile(
        "register\\(\"composter\", \\(\\) -> new TFCComposterBlock\\(ExtendedProperties\\.of\\(MapColor\\.WOOD\\)[^;]*"
            + "\\.flammable\\((\\d+), (\\d+)\\)");

    /** TFC's {@code flammable(flammability, fireSpreadSpeed)}: vanilla burn odds and ignite odds. */
    record Flammable(int flammability, int fireSpreadSpeed) {
    }

    private ComposterBlock() {
    }

    /** True when the checkout has the composter (the small test fixtures do not). */
    public static boolean available(TfcCheckout tfc) {
        return tfc.exists(SOURCE) && tfc.exists(BIN_MODEL);
    }

    /** The render item of a compost look. */
    public static String renderItem(String appearance) {
        return Json.TO_NS + ":render/composter/" + appearance;
    }

    /** TFC's compost model of a look ({@code tfp:block/composter/<type>_<stage>}). */
    public static String compostModel(String appearance) {
        return Json.TO_NS + ":block/composter/" + appearance;
    }

    /** The merged bin + compost model of a look drawn on a pool state. */
    public static String filledModel(String appearance) {
        return Json.TO_NS + ":block/composter/filled/" + appearance;
    }

    /**
     * The stage drawn from the CE pool must be TFC's full cube, and the last stage (the highest stage of {@code STAGE_8}).
     */
    static void checkPoolStage() {
        if (HitboxDemands.COMPOSTER_POOL_STAGE != TfcShapes.COMPOSTER_MAX_STAGE) {
            throw new DatagenException("the composter pool stage is not TFC's last stage");
        }
        if (!TfcShapes.load().get(HitboxDemands.composterKey(HitboxDemands.COMPOSTER_POOL_STAGE)).selection()
            .equals(dev.tfp.datagen.hitbox.Shape.FULL)) {
            throw new DatagenException("the composter pool stage is no longer TFC's full cube");
        }
    }

    /** Writes the entry, its models, textures and names. Returns the number of server states (27). */
    public static int run(TfcCheckout tfc, OutputTree out) {
        checkSource(tfc);
        checkBlockstate(tfc);
        checkPoolStage();
        Flammable fire = flammable(tfc);
        Set<String> filled = new TreeSet<>();
        DeviceBlockSpec spec = spec(tfc, filled);
        copyModels(tfc, out, filled);
        out.put(OUTPUT, Yaml.write(DeviceYaml.HEADER, yaml(spec, fire)));
        writeLang(tfc, out);
        return (int) spec.stateCount();
    }

    /** TFC's states: STAGE_8 (0..8) and COMPOST_TYPE (normal, ready, rotten), default stage 0 / normal. */
    static void checkSource(TfcCheckout tfc) {
        String src = tfc.readString(SOURCE).replaceAll("\\s+", " ");
        for (String needle : List.of("public static final IntegerProperty STAGE = TFCBlockStateProperties.STAGE_8;",
                "public static final EnumProperty<CompostType> TYPE = TFCBlockStateProperties.COMPOST_TYPE;",
                "registerDefaultState(getStateDefinition().any().setValue(STAGE, 0).setValue(TYPE, CompostType.NORMAL));",
                "super.createBlockStateDefinition(builder.add(STAGE, TYPE));",
                "public enum CompostType implements StringRepresentable { NORMAL, READY, ROTTEN;",
                "extends BottomSupportedDeviceBlock")) {
            if (!src.contains(needle)) {
                throw new DatagenException(SOURCE + " no longer contains \"" + needle + "\": re-check the composter entry");
            }
        }
        String props = tfc.readString(PROPERTIES);
        String stage = "STAGE_8 = IntegerProperty.create(\"stage\", 0, " + TfcShapes.COMPOSTER_MAX_STAGE + ");";
        if (!props.contains(stage)) {
            throw new DatagenException(PROPERTIES + " no longer contains \"" + stage + "\"");
        }
    }

    /**
     * TFC's multipart: the unconditional bin, then for every stage 1..8 and type one part {@code when {type, stage}}
     * drawing {@code tfc:block/composter/<type>_<stage>}, unrotated. Anything else fails.
     */
    static void checkBlockstate(TfcCheckout tfc) {
        JsonArray parts = Json.parse(tfc.readString(BLOCKSTATE), BLOCKSTATE).getAsJsonObject().getAsJsonArray("multipart");
        Map<String, String> got = new TreeMap<>();
        boolean bin = false;
        for (JsonElement e : parts) {
            JsonObject part = e.getAsJsonObject();
            JsonObject apply = part.getAsJsonObject("apply");
            if (apply.size() != 1 || !apply.has("model")) {
                throw new DatagenException(BLOCKSTATE + ": a part is not one unrotated model");
            }
            String model = apply.get("model").getAsString();
            if (!part.has("when")) {
                if (bin || !model.equals(Json.FROM_NS + ":block/composter/composter")) {
                    throw new DatagenException(BLOCKSTATE + ": the unconditional part is not the one bin model");
                }
                bin = true;
                continue;
            }
            JsonObject when = part.getAsJsonObject("when");
            if (when.size() != 2 || !when.has(TYPE) || !when.has(STAGE)) {
                throw new DatagenException(BLOCKSTATE + ": a part's condition is not {type, stage}");
            }
            String key = when.get(TYPE).getAsString() + "_" + when.get(STAGE).getAsString();
            if (got.put(key, model) != null) {
                throw new DatagenException(BLOCKSTATE + ": two parts for " + key);
            }
        }
        Map<String, String> want = new TreeMap<>();
        for (int s = 1; s <= TfcShapes.COMPOSTER_MAX_STAGE; s++) {
            for (String t : HitboxDemands.COMPOSTER_TYPES) {
                want.put(t + "_" + s, Json.FROM_NS + ":block/composter/" + t + "_" + s);
            }
        }
        if (!bin || !got.equals(want)) {
            throw new DatagenException(BLOCKSTATE + " is no longer the bin plus <type>_<stage> for stages 1.."
                + TfcShapes.COMPOSTER_MAX_STAGE);
        }
    }

    /** TFC's {@code flammable(60, 90)} on the composter registration. */
    static Flammable flammable(TfcCheckout tfc) {
        Matcher m = FLAMMABLE.matcher(tfc.readString(BLOCKS));
        if (!m.find()) {
            throw new DatagenException(BLOCKS + ": the composter registration no longer matches " + FLAMMABLE.pattern());
        }
        return new Flammable(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)));
    }

    /** The appearance of {@code type} at {@code stage}, filling {@code filled} with the looks drawn as merged models. */
    static DeviceAppearance appearance(String type, int stage, DeviceBlockSpec.Builder b, Set<String> filled,
            Set<String> renderItems) {
        String name = HitboxDemands.composterAppearance(type, stage);
        boolean compost = stage > 0;
        if (stage == HitboxDemands.COMPOSTER_POOL_STAGE) {
            filled.add(name);
            return DeviceAppearance.modelled(name, DeviceAppearance.Carrier.pool(HitboxDemands.COMPOSTER_POOL), filledModel(name));
        }
        HitboxAllocation.Assignment a = HitboxAllocation.agreed(ID, HitboxDemands.composterDemand(stage));
        switch (a.mode()) {
            case MODEL -> {
                DeviceAppearance app = DeviceAppearance.modelled(name, DeviceAppearance.Carrier.state(a.state()),
                    HitboxDemands.COMPOSTER_MODEL);
                if (compost) {
                    app = app.withOverlay(List.of(renderer(name, b, renderItems)));
                }
                return app;
            }
            case HITBOX_ONLY -> {
                List<DeviceAppearance.Renderer> rs = new ArrayList<>();
                rs.add(renderer(BLOCK, b, renderItems, HitboxDemands.COMPOSTER_MODEL));
                if (compost) {
                    rs.add(renderer(name, b, renderItems));
                }
                return DeviceAppearance.rendered(name, DeviceAppearance.Carrier.state(a.state()), rs);
            }
            default -> throw new DatagenException("the composter carrier of stage " + stage + " is " + a.mode()
                + ", ComposterBlock draws TFC's models");
        }
    }

    private static DeviceAppearance.Renderer renderer(String look, DeviceBlockSpec.Builder b, Set<String> renderItems) {
        return renderer(look, b, renderItems, compostModel(look));
    }

    private static DeviceAppearance.Renderer renderer(String look, DeviceBlockSpec.Builder b, Set<String> renderItems,
            String model) {
        String item = renderItem(look);
        if (renderItems.add(item)) {
            b.renderItem(item, model);
        }
        return DeviceAppearance.Renderer.item(item).rotated(RENDER_ROTATION);
    }

    static DeviceBlockSpec spec(TfcCheckout tfc, Set<String> filled) {
        DeviceBlockSpec.Builder b = DeviceBlockSpec.builder(ID)
            .property(DeviceProperty.range(STAGE, 0, TfcShapes.COMPOSTER_MAX_STAGE, 0))
            .property(DeviceProperty.names(TYPE, HitboxDemands.COMPOSTER_TYPES, HitboxDemands.COMPOSTER_TYPES.getFirst()));
        Set<String> renderItems = new TreeSet<>();
        b.appearance(appearance(HitboxDemands.COMPOSTER_TYPES.getFirst(), 0, b, filled, renderItems));
        for (int s = 1; s <= TfcShapes.COMPOSTER_MAX_STAGE; s++) {
            for (String t : HitboxDemands.COMPOSTER_TYPES) {
                b.appearance(appearance(t, s, b, filled, renderItems));
            }
        }
        b.everyState(st -> HitboxDemands.composterAppearance(st.get(TYPE), Integer.parseInt(st.get(STAGE))), null)
            .settings(DeviceBlockSpec.Settings.fromTfc(tfc, BLOCK))
            .loot(DeviceLoot.fromTfc(tfc, BLOCK))
            .deviceBehaviour()
            .blockItem(LANG_KEY);
        Map<String, String> hidden = new TreeMap<>();
        for (int s = 0; s < HitboxDemands.COMPOSTER_POOL_STAGE; s++) {
            HitboxAllocation.Assignment a = HitboxAllocation.agreed(ID, HitboxDemands.composterDemand(s));
            if (a.hideTarget() != null && !a.pool()) {
                String old = hidden.putIfAbsent(a.state(), a.hideTarget());
                if (old == null) {
                    b.stateMapping(a.state(), a.hideTarget());
                }
            }
        }
        return b.build();
    }

    /** The templated entry plus TFC's {@code flammable} as CraftEngine burn settings (deep-merged with the template's). */
    @SuppressWarnings("unchecked")
    static Map<String, Object> yaml(DeviceBlockSpec spec, Flammable fire) {
        Map<String, Object> root = DeviceYaml.templated(spec);
        Map<String, Object> block = (Map<String, Object>) ((Map<String, Object>) root.get("blocks")).get(spec.id());
        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("burnable", true);
        // NeoForge: getFireSpreadSpeed is the vanilla ignite odds (CE burn_chance), getFlammability the burn odds
        // (CE fire_spread_chance).
        settings.put("burn_chance", fire.fireSpreadSpeed());
        settings.put("fire_spread_chance", fire.flammability());
        Map<String, Object> ordered = new LinkedHashMap<>();
        block.forEach((k, v) -> {
            ordered.put(k, v);
            if (k.equals("arguments")) {
                ordered.put("settings", settings);
            }
        });
        ((Map<String, Object>) root.get("blocks")).put(spec.id(), ordered);
        return root;
    }

    /**
     * TFC's bin, compost and per-type models and the item model (renamed), their textures, and the merged bin + compost
     * models of the {@code filled} looks.
     */
    static void copyModels(TfcCheckout tfc, OutputTree out, Set<String> filled) {
        JsonObject bin = model(tfc, "block/composter/composter");
        out.put(RP + "models/block/composter/composter.json", Json.write(Json.rename(bin)));
        copyTextures(tfc, out, bin, BIN_MODEL);
        for (int s = 1; s <= TfcShapes.COMPOSTER_MAX_STAGE; s++) {
            JsonObject base = model(tfc, "block/composter/compost_" + s);
            out.put(RP + "models/block/composter/compost_" + s + ".json", Json.write(Json.rename(base)));
            for (String t : HitboxDemands.COMPOSTER_TYPES) {
                String look = t + "_" + s;
                JsonObject m = model(tfc, "block/composter/" + look);
                if (!m.has("parent") || !(Json.FROM_NS + ":block/composter/compost_" + s).equals(m.get("parent").getAsString())
                    || m.has("elements")) {
                    throw new DatagenException(MODELS + "block/composter/" + look + ".json no longer only retextures compost_" + s);
                }
                out.put(RP + "models/block/composter/" + look + ".json", Json.write(Json.rename(m)));
                copyTextures(tfc, out, m, look);
                if (filled.contains(look)) {
                    out.put(RP + "models/block/composter/filled/" + look + ".json", Json.write(Json.rename(merged(bin, base, m))));
                }
            }
        }
        JsonObject item = Json.parse(tfc.readString(ITEM_MODEL), ITEM_MODEL).getAsJsonObject();
        if (!item.has("parent") || !(Json.FROM_NS + ":block/composter/composter").equals(item.get("parent").getAsString())) {
            throw new DatagenException(ITEM_MODEL + " is no longer the bin model as parent");
        }
        out.put(RP + "models/item/composter.json", Json.write(Json.rename(item)));
    }

    /**
     * TFC's multipart drawn as one model: the bin's parent, textures and elements, plus the compost elements with their
     * texture {@code #0} renamed to {@code #compost} (the per-type texture).
     */
    static JsonObject merged(JsonObject bin, JsonObject compostBase, JsonObject look) {
        JsonObject m = new JsonObject();
        m.add("parent", bin.get("parent").deepCopy());
        JsonObject textures = bin.getAsJsonObject("textures").deepCopy();
        JsonObject lookTextures = look.getAsJsonObject("textures");
        if (lookTextures == null || lookTextures.size() != 1 || !lookTextures.has("0")) {
            throw new DatagenException("a composter compost model no longer sets exactly texture 0");
        }
        textures.add(COMPOST_TEXTURE_KEY, lookTextures.get("0").deepCopy());
        m.add("textures", textures);
        JsonArray elements = bin.getAsJsonArray("elements").deepCopy();
        for (JsonElement e : compostBase.getAsJsonArray("elements")) {
            JsonObject el = e.getAsJsonObject().deepCopy();
            for (Map.Entry<String, JsonElement> face : el.getAsJsonObject("faces").entrySet()) {
                JsonObject f = face.getValue().getAsJsonObject();
                if (!"#0".equals(f.get("texture").getAsString())) {
                    throw new DatagenException("a composter compost element face no longer draws #0");
                }
                f.addProperty("texture", "#" + COMPOST_TEXTURE_KEY);
            }
            elements.add(el);
        }
        m.add("elements", elements);
        return m;
    }

    private static JsonObject model(TfcCheckout tfc, String path) {
        String src = MODELS + path + ".json";
        return Json.parse(tfc.readString(src), src).getAsJsonObject();
    }

    /** The {@code tfc:block/...} textures of a model. */
    private static void copyTextures(TfcCheckout tfc, OutputTree out, JsonObject model, String source) {
        JsonObject textures = model.getAsJsonObject("textures");
        if (textures == null) {
            return;
        }
        for (Map.Entry<String, JsonElement> e : textures.entrySet()) {
            String v = e.getValue().getAsString();
            if (!v.startsWith(Json.FROM_NS + ":block/")) {
                continue;
            }
            String path = v.substring(v.indexOf(':') + 1);
            String png = TEXTURES + path + ".png";
            if (!tfc.exists(png)) {
                throw new DatagenException(source + " names " + v + ", which TFC has no texture for");
            }
            out.put(RP + "textures/" + path + ".png", tfc.read(png));
        }
    }

    /** {@code block.tfp.composter} in every TFC locale that names the composter (English must). */
    static void writeLang(TfcCheckout tfc, OutputTree out) {
        String tfcKey = "block." + Json.FROM_NS + "." + BLOCK;
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
            o.addProperty(LANG_KEY, src.get(tfcKey).getAsString());
            out.put(LANG_RP + locale + ".json", Json.write(o));
        }
    }
}
