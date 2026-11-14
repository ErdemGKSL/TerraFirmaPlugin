/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/BarrelRackBlock.java @ c3b4d9536 (RACK_SHAPE, canSurvive: the
 *       bottom support), src/main/java/net/dries007/tfc/common/blocks/TFCBlocks.java @ c3b4d9536 (BARREL_RACK: wood sound,
 *       flammableLikePlanks = flammable(20, 5), strength(4f)),
 *       src/main/java/net/dries007/tfc/common/blocks/ExtendedProperties.java @ c3b4d9536 (flammableLikePlanks),
 *       src/main/resources/assets/tfc/blockstates/barrel_rack.json, models/block/barrel_rack.json,
 *       models/item/barrel_rack.json and textures/block/barrel_rack.png @ c3b4d9536 (copied),
 *       src/main/resources/data/tfc/loot_table/blocks/barrel_rack.json @ c3b4d9536 (the drop),
 *       src/main/resources/assets/tfc/lang/*.json @ c3b4d9536 (block.tfc.barrel_rack)
 */
package dev.tfp.datagen.barrelrack;

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
import dev.tfp.datagen.device.DeviceYaml;
import dev.tfp.datagen.hitbox.HitboxAllocation;
import dev.tfp.datagen.hitbox.HitboxDemands;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PAR-PROC-38: TFC's barrel rack as the CraftEngine block {@code tfp:barrel_rack}, in
 * {@code craftengine/tfp/configuration/barrel/barrel_rack.yml}. A plain block (no device): the plugin's
 * {@code BarrelRackListener} lays a barrel on it, and the barrel's {@code rack} state holds it afterwards.
 *
 * <ul>
 *   <li>One state. The carrier comes from the hitbox selector ({@code tfc:barrel_rack}, TFC's {@code RACK_SHAPE}); it is
 *       hitbox-only (no free carrier scores high enough for TFC's model), so the look is an item display of TFC's model
 *       {@code tfp:block/barrel_rack} ({@code tfp:render/barrel_rack}).</li>
 *   <li>TFC's settings (strength 4, wood sounds, the axe tag), {@code flammableLikePlanks} as CraftEngine burn settings,
 *       the loot (the rack itself, under survives_explosion as TFC's table), the block item with TFC's item model.</li>
 *   <li>TFC {@code canSurvive} / {@code updateShape}: CraftEngine's {@code sturdy_base_block} with a full top face below
 *       (TFC also takes {@code tfc:solid_top_face}; ledger).</li>
 * </ul>
 */
public final class BarrelRackBlock {
    public static final String ID = HitboxDemands.BARREL_RACK_ID;
    public static final String APPEARANCE = HitboxDemands.BARREL_RACK_APPEARANCE;
    public static final String MODEL = HitboxDemands.BARREL_RACK_MODEL;
    public static final String RENDER_ITEM = Json.TO_NS + ":render/barrel_rack";
    public static final String OUTPUT = "craftengine/tfp/configuration/barrel/barrel_rack.yml";
    public static final String LANG_KEY = "block." + Json.TO_NS + ".barrel_rack";
    static final String SOURCE = "src/main/java/net/dries007/tfc/common/blocks/devices/BarrelRackBlock.java";
    static final String BLOCKS = "src/main/java/net/dries007/tfc/common/blocks/TFCBlocks.java";
    static final String PROPERTIES = "src/main/java/net/dries007/tfc/common/blocks/ExtendedProperties.java";
    static final String BLOCKSTATE = TfcCheckout.ASSETS + "blockstates/barrel_rack.json";
    static final String MODELS = TfcCheckout.ASSETS + "models/";
    static final String TEXTURES = TfcCheckout.ASSETS + "textures/";
    static final String LOOT = TfcCheckout.MAIN + "data/" + Json.FROM_NS + "/loot_table/blocks/barrel_rack.json";
    static final String LANG = TfcCheckout.ASSETS + "lang/";
    static final String PACK = "craftengine/tfp/";
    static final String RP = PACK + "resourcepack/assets/tfp/";
    static final String LANG_RP = PACK + "resourcepack/assets/tfp_barrel_rack/lang/";
    /** The half turn every block-model item display needs to match the block (see ForgeDevices.OVERLAY_ROTATION). */
    static final String RENDER_ROTATION = "0,180,0";
    private static final Pattern REGISTER = Pattern.compile("register\\(\"barrel_rack\", \\(\\) -> new BarrelRackBlock\\("
        + "ExtendedProperties\\.of\\(MapColor\\.WOOD\\)\\.sound\\(SoundType\\.WOOD\\)\\.flammableLikePlanks\\(\\)\\.strength\\(([0-9.]+)f\\)\\)\\);");
    private static final Pattern PLANKS = Pattern.compile(
        "public ExtendedProperties flammableLikePlanks\\(\\) \\{ return flammable\\((\\d+), (\\d+)\\)");

    private BarrelRackBlock() {
    }

    /** True when the checkout has the rack (the small test fixtures do not). */
    public static boolean available(TfcCheckout tfc) {
        return tfc.exists(SOURCE) && tfc.exists(MODELS + "block/barrel_rack.json");
    }

    /** Writes the entry, its models, texture and names. Returns the number of server states (1). */
    public static int run(TfcCheckout tfc, OutputTree out) {
        checkSource(tfc);
        checkLoot(tfc);
        checkBlockstate(tfc);
        int[] fire = flammable(tfc);
        DeviceBlockSpec spec = spec(tfc);
        out.put(OUTPUT, Yaml.write(DeviceYaml.HEADER, yaml(spec, fire)));
        copyModels(tfc, out);
        writeLang(tfc, out);
        return (int) spec.stateCount();
    }

    /** TFC's rack: the RACK_SHAPE outline, the bottom support, nothing else of its own (no state, no block entity). */
    static void checkSource(TfcCheckout tfc) {
        String src = tfc.readString(SOURCE).replaceAll("\\s+", " ");
        for (String needle : List.of("return BarrelBlock.RACK_SHAPE;", "return BottomSupportedDeviceBlock.canSurvive(level, pos);",
                "public class BarrelRackBlock extends ExtendedBlock")) {
            if (!src.contains(needle)) {
                throw new DatagenException(SOURCE + " no longer contains \"" + needle + "\": re-check the barrel rack entry");
            }
        }
        if (src.contains("createBlockStateDefinition")) {
            throw new DatagenException(SOURCE + " now has states: re-check the barrel rack entry");
        }
    }

    /** TFC's hardness (strength(h) sets both). */
    static String strength(TfcCheckout tfc) {
        Matcher m = REGISTER.matcher(tfc.readString(BLOCKS));
        if (!m.find()) {
            throw new DatagenException(BLOCKS + ": the barrel rack registration no longer matches " + REGISTER.pattern());
        }
        return m.group(1);
    }

    /** TFC's {@code flammableLikePlanks}: {@code flammable(flammability, fireSpreadSpeed)}. */
    static int[] flammable(TfcCheckout tfc) {
        Matcher m = PLANKS.matcher(tfc.readString(PROPERTIES));
        if (!m.find()) {
            throw new DatagenException(PROPERTIES + ": flammableLikePlanks no longer matches " + PLANKS.pattern());
        }
        return new int[] {Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))};
    }

    /** TFC's table: one pool under survives_explosion dropping the rack itself. */
    static void checkLoot(TfcCheckout tfc) {
        JsonObject root = Json.parse(tfc.readString(LOOT), LOOT).getAsJsonObject();
        JsonArray pools = root.getAsJsonArray("pools");
        if (pools == null || pools.size() != 1) {
            throw new DatagenException(LOOT + ": not one pool");
        }
        JsonObject pool = pools.get(0).getAsJsonObject();
        JsonArray entries = pool.getAsJsonArray("entries");
        JsonArray conditions = pool.getAsJsonArray("conditions");
        if (entries == null || entries.size() != 1
            || !(Json.FROM_NS + ":barrel_rack").equals(entries.get(0).getAsJsonObject().get("name").getAsString())
            || conditions == null || conditions.size() != 1
            || !"minecraft:survives_explosion".equals(conditions.get(0).getAsJsonObject().get("condition").getAsString())) {
            throw new DatagenException(LOOT + ": no longer the rack itself under survives_explosion");
        }
    }

    /** One variant drawing {@code tfc:block/barrel_rack} unrotated. */
    static void checkBlockstate(TfcCheckout tfc) {
        JsonObject variants = Json.parse(tfc.readString(BLOCKSTATE), BLOCKSTATE).getAsJsonObject().getAsJsonObject("variants");
        JsonObject v = variants == null || variants.size() != 1 ? null : variants.getAsJsonObject("");
        if (v == null || !(Json.FROM_NS + ":block/barrel_rack").equals(v.get("model").getAsString()) || v.has("y") || v.has("x")) {
            throw new DatagenException(BLOCKSTATE + " no longer draws tfc:block/barrel_rack in one unrotated variant");
        }
    }

    /** The carrier the selector agreed on; hitbox-only (the look is the item display). */
    static HitboxAllocation.Assignment carrier() {
        HitboxAllocation.Assignment a = HitboxAllocation.agreed(ID, APPEARANCE);
        if (a.mode() != HitboxAllocation.Mode.HITBOX_ONLY) {
            throw new DatagenException("the barrel rack carrier is " + a.mode() + ", BarrelRackBlock draws a transparent carrier");
        }
        return a;
    }

    static DeviceBlockSpec spec(TfcCheckout tfc) {
        HitboxAllocation.Assignment a = carrier();
        String strength = strength(tfc);
        DeviceBlockSpec.Builder b = DeviceBlockSpec.builder(ID)
            .appearance(DeviceAppearance.rendered(APPEARANCE, DeviceAppearance.Carrier.state(a.state()),
                List.of(DeviceAppearance.Renderer.item(RENDER_ITEM).rotated(RENDER_ROTATION))))
            .everyState(s -> APPEARANCE, null)
            .settings(DeviceBlockSpec.Settings.fromTfc(tfc, "barrel_rack"))
            .loot(DeviceLoot.self())
            .blockItem(LANG_KEY)
            .renderItem(RENDER_ITEM, MODEL);
        if (!b.build().settings().hardness().toPlainString().equals(strength)) {
            throw new DatagenException(ID + ": the settings' hardness differs from the registration " + strength);
        }
        if (a.hideTarget() != null) {
            b.stateMapping(a.state(), a.hideTarget());
        }
        return b.build();
    }

    /**
     * The literal entry: TFC's settings (not occluding: the look is a display), the burn settings, CraftEngine's
     * {@code sturdy_base_block} and the one appearance (a block without properties is single-state).
     */
    @SuppressWarnings("unchecked")
    static Map<String, Object> yaml(DeviceBlockSpec spec, int[] fire) {
        Map<String, Object> root = DeviceYaml.literal(spec);
        Map<String, Object> blocks = (Map<String, Object>) root.get("blocks");
        Map<String, Object> block = (Map<String, Object>) blocks.get(spec.id());
        Map<String, Object> settings = (Map<String, Object>) block.get("settings");
        settings.put("can_occlude", false);
        settings.put("is_suffocating", false);
        settings.put("is_view_blocking", false);
        // NeoForge: getFireSpreadSpeed is the vanilla ignite odds (CE burn_chance), getFlammability the burn odds
        // (CE fire_spread_chance); see ComposterBlock.
        settings.put("burnable", true);
        settings.put("burn_chance", fire[1]);
        settings.put("fire_spread_chance", fire[0]);
        Map<String, Object> sturdy = new LinkedHashMap<>();
        sturdy.put("type", "sturdy_base_block");
        sturdy.put("direction", "down");
        sturdy.put("support_types", List.of("full"));
        Map<String, Object> states = (Map<String, Object>) block.get("states");
        states.remove("properties");
        states.remove("variants");
        Map<String, Object> ordered = new LinkedHashMap<>();
        ordered.put("settings", settings);
        ordered.put("loot", block.get("loot"));
        ordered.put("behavior", sturdy);
        ordered.put("states", states);
        blocks.put(spec.id(), ordered);
        return root;
    }

    /** TFC's block and item models (renamed) and the rack texture. */
    static void copyModels(TfcCheckout tfc, OutputTree out) {
        String block = MODELS + "block/barrel_rack.json";
        JsonObject m = Json.parse(tfc.readString(block), block).getAsJsonObject();
        out.put(RP + "models/block/barrel_rack.json", Json.write(Json.rename(m)));
        JsonObject textures = m.getAsJsonObject("textures");
        for (Map.Entry<String, JsonElement> e : textures.entrySet()) {
            String t = e.getValue().getAsString();
            if (!t.startsWith(Json.FROM_NS + ":")) {
                continue;
            }
            String png = TEXTURES + t.substring(t.indexOf(':') + 1) + ".png";
            if (!tfc.exists(png)) {
                throw new DatagenException(block + " names " + t + ", which TFC has no texture for");
            }
            out.put(RP + "textures/" + t.substring(t.indexOf(':') + 1) + ".png", tfc.read(png));
        }
        String item = MODELS + "item/barrel_rack.json";
        JsonObject im = Json.parse(tfc.readString(item), item).getAsJsonObject();
        if (!(Json.FROM_NS + ":block/barrel_rack").equals(im.get("parent").getAsString())) {
            throw new DatagenException(item + " is no longer the block model as parent");
        }
        out.put(RP + "models/item/barrel_rack.json", Json.write(Json.rename(im)));
    }

    /** {@code block.tfp.barrel_rack} in every TFC locale that names the rack (English must). */
    static void writeLang(TfcCheckout tfc, OutputTree out) {
        String tfcKey = "block." + Json.FROM_NS + ".barrel_rack";
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
            Map<String, String> keys = new TreeMap<>();
            keys.put(LANG_KEY, src.get(tfcKey).getAsString());
            JsonObject o = new JsonObject();
            keys.forEach(o::addProperty);
            out.put(LANG_RP + locale + ".json", Json.write(o));
        }
    }
}
