/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/LargeVesselBlock.java @ c3b4d9536 (FACING default north, SEALED),
 *       src/main/java/net/dries007/tfc/common/blocks/devices/SealableDeviceBlock.java @ c3b4d9536 (SEALED, POWERED),
 *       src/main/java/net/dries007/tfc/common/blocks/TFCBlocks.java @ c3b4d9536 (LARGE_VESSEL, GLAZED_LARGE_VESSELS:
 *       strength(2.5F), the default stone sound),
 *       src/main/resources/assets/tfc/blockstates/ceramic/large_vessel{,/*}.json, models/block/ceramic/*large_vessel_*.json,
 *       models/item/ceramic/large_vessel{,/*}.json and textures/block/ceramic/large_vessel/** @ c3b4d9536 (copied),
 *       src/main/resources/data/tfc/loot_table/blocks/ceramic/large_vessel{,/*}.json @ c3b4d9536 (the drop),
 *       src/generated/resources/data/minecraft/tags/block/mineable/pickaxe.json @ c3b4d9536 (the tool),
 *       src/main/resources/assets/tfc/lang/*.json @ c3b4d9536 (block.tfc.ceramic.large_vessel.*)
 */
package dev.tfp.datagen.largevessel;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PAR-POT-06: the large vessel and its sixteen glazed colours as CraftEngine blocks {@code tfp:ceramic/large_vessel} and
 * {@code tfp:ceramic/large_vessel/<colour>} ({@code craftengine/tfp/configuration/largevessel/large_vessel.yml}). The
 * plugin's {@code LargeVesselDevice} runs them.
 *
 * <ul>
 *   <li>The state properties {@code facing} (the four horizontal directions, default north) and {@code sealed} (TFC
 *       {@code SealableDeviceBlock.SEALED}, default false); TFC's {@code powered} is no state (the device keeps it in its
 *       record and reads the redstone signal itself). 8 states per block.</li>
 *   <li>Carriers: the hitbox selector serves TFC's two outlines hitbox-only ({@link HitboxDemands}; the owner decision of
 *       PAR-INFRA-13 puts both on the one heavy_core state); the look is TFC's {@code opened} or {@code sealed} block
 *       model of the colour as an {@code entity_renderer} item display turned by the blockstate's {@code y}.</li>
 *   <li>The {@code tfp:device} behaviour through {@code tfp:block/device_rendered}, TFC's settings (strength 2.5, the stone
 *       sounds, the pickaxe tag), the loot (the vessel itself; the device puts its contents on the dropped item) and the
 *       block item with TFC's item model (without its {@code tfc:sealed} override).</li>
 * </ul>
 */
public final class LargeVesselBlocks {
    public static final String KIND = HitboxDemands.LARGE_VESSEL_ID;
    static final String BLOCK = "ceramic/large_vessel";
    static final String SOURCE = "src/main/java/net/dries007/tfc/common/blocks/LargeVesselBlock.java";
    static final String SEALABLE = "src/main/java/net/dries007/tfc/common/blocks/devices/SealableDeviceBlock.java";
    static final String TFC_BLOCKS = "src/main/java/net/dries007/tfc/common/blocks/TFCBlocks.java";
    static final String BLOCKSTATES = TfcCheckout.ASSETS + "blockstates/";
    static final String MODELS = TfcCheckout.ASSETS + "models/";
    static final String TEXTURES = TfcCheckout.ASSETS + "textures/";
    static final String LOOT = TfcCheckout.MAIN + "data/" + Json.FROM_NS + "/loot_table/blocks/";
    static final String MINEABLE = TfcCheckout.GENERATED + "data/minecraft/tags/block/mineable/";
    static final String LANG = TfcCheckout.ASSETS + "lang/";
    static final String PACK = "craftengine/tfp/";
    public static final String OUTPUT = PACK + "configuration/largevessel/large_vessel.yml";
    static final String RP = PACK + "resourcepack/assets/tfp/";
    static final String LANG_RP = PACK + "resourcepack/assets/tfp_largevessel/lang/";
    public static final String FACING = "facing";
    public static final String SEALED = "sealed";
    public static final List<String> FACINGS = HitboxDemands.FACINGS;
    /** TFC {@code Helpers.mapOf(DyeColor.class, ...)}: the plain vessel has no colour ({@code null}). */
    public static final List<String> COLORS = HitboxDemands.CANDLE_COLORS;
    private static final Pattern STRENGTH = Pattern.compile("\\.strength\\(([0-9.]+)F\\)");
    private static final Pattern TEXTURE = Pattern.compile(Json.FROM_NS + ":(block/[a-z0-9_/]+)");

    private LargeVesselBlocks() {
    }

    /** True when the checkout has the large vessel (the small test fixtures do not). */
    public static boolean available(TfcCheckout tfc) {
        return tfc.exists(SOURCE) && tfc.exists(BLOCKSTATES + BLOCK + ".json") && tfc.exists(SEALABLE);
    }

    /** The block id of a colour ({@code null} for the plain vessel). */
    public static String id(String color) {
        return color == null ? KIND : KIND + "/" + color;
    }

    /** TFC's block path of a colour ({@code ceramic/large_vessel}, {@code ceramic/large_vessel/red}). */
    static String tfcPath(String color) {
        return color == null ? BLOCK : BLOCK + "/" + color;
    }

    public static String langKey(String color) {
        return "block." + Json.TO_NS + "." + tfcPath(color).replace('/', '.');
    }

    /** TFC's model of a colour: {@code ceramic/large_vessel_opened}, {@code ceramic/red_large_vessel_sealed}. */
    static String modelPath(String color, boolean sealed) {
        return "block/ceramic/" + (color == null ? "" : color + "_") + "large_vessel_" + (sealed ? "sealed" : "opened");
    }

    public static String model(String color, boolean sealed) {
        return Json.TO_NS + ":" + modelPath(color, sealed);
    }

    /** The display-only item that draws {@link #model}. */
    public static String renderItem(String color, boolean sealed) {
        return Json.TO_NS + ":render/" + tfcPath(color) + "/" + (sealed ? "sealed" : "opened");
    }

    /** The display turn of TFC's blockstate {@code y} (see LoomBlock.rotation). */
    static String rotation(int y) {
        return "0," + Math.floorMod(180 - y, 360) + ",0";
    }

    /** Writes the entries, their models, textures and names. Returns the number of server states (8 per block). */
    public static int run(TfcCheckout tfc, OutputTree out) {
        checkSource(tfc);
        String strength = strength(tfc);
        Map<String, Object> blocks = new LinkedHashMap<>();
        Map<String, Object> items = new LinkedHashMap<>();
        Map<String, Object> mappings = new TreeMap<>();
        TreeSet<String> textures = new TreeSet<>();
        TreeSet<String> models = new TreeSet<>();
        int states = 0;
        for (String color : colors()) {
            checkLoot(tfc, color);
            Map<String, Integer> turns = checkBlockstate(tfc, color);
            copyModels(tfc, out, color, models, textures);
            DeviceBlockSpec spec = spec(color, strength, tool(tfc, color), turns);
            states += (int) spec.stateCount();
            merge(DeviceYaml.templated(spec), blocks, items, mappings);
        }
        copyTextures(tfc, out, textures);
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

    /** The plain vessel and then every dye colour. */
    static List<String> colors() {
        List<String> all = new java.util.ArrayList<>();
        all.add(null);
        all.addAll(COLORS);
        return all;
    }

    @SuppressWarnings("unchecked")
    private static void merge(Map<String, Object> one, Map<String, Object> blocks, Map<String, Object> items,
            Map<String, Object> mappings) {
        blocks.putAll((Map<String, Object>) one.get("blocks"));
        items.putAll((Map<String, Object>) one.getOrDefault("items", Map.of()));
        for (Map.Entry<String, Object> e : ((Map<String, Object>) one.getOrDefault("block_state_mappings", Map.of())).entrySet()) {
            Object old = mappings.putIfAbsent(e.getKey(), e.getValue());
            if (old != null && !old.equals(e.getValue())) {
                throw new DatagenException("large vessel carrier " + e.getKey() + " hidden as " + old + " and " + e.getValue());
            }
        }
    }

    /** TFC's states: FACING default north (the placing direction), SEALED false; POWERED is not modelled. */
    static void checkSource(TfcCheckout tfc) {
        String sealable = tfc.readString(SEALABLE).replaceAll("\\s+", " ");
        if (!sealable.contains("public static final BooleanProperty SEALED = TFCBlockStateProperties.SEALED;")
            || !sealable.contains("super(properties, InventoryRemoveBehavior.DROP);")) {
            throw new DatagenException(SEALABLE + " no longer declares SEALED or the DROP remove behaviour: re-check the large vessel entry");
        }
        String src = tfc.readString(SOURCE).replaceAll("\\s+", " ");
        for (String needle : List.of("public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;",
                "registerDefaultState(getStateDefinition().any().setValue(FACING, Direction.NORTH).setValue(SEALED, false).setValue(POWERED, false));",
                "state.setValue(FACING, context.getHorizontalDirection())",
                "super.createBlockStateDefinition(builder.add(FACING));")) {
            if (!src.contains(needle)) {
                throw new DatagenException(SOURCE + " no longer contains \"" + needle + "\": re-check the large vessel entry");
            }
        }
    }

    /** TFC's one hardness: both registrations are {@code strength(2.5F)} with no {@code sound(...)} (the stone default). */
    static String strength(TfcCheckout tfc) {
        String found = null;
        int lines = 0;
        for (String l : tfc.readString(TFC_BLOCKS).split("\n")) {
            if (!l.contains("\"ceramic/large_vessel") || !l.contains("new LargeVesselBlock(")) {
                continue;
            }
            lines++;
            Matcher m = STRENGTH.matcher(l);
            if (!m.find() || l.contains(".sound(")) {
                throw new DatagenException(TFC_BLOCKS + ": a large vessel registration no longer has strength(..) and the default sound");
            }
            if (found != null && !found.equals(m.group(1))) {
                throw new DatagenException(TFC_BLOCKS + ": the large vessels have different strengths");
            }
            found = m.group(1);
        }
        if (lines != 2) {
            throw new DatagenException(TFC_BLOCKS + ": expected the LARGE_VESSEL and GLAZED_LARGE_VESSELS registrations, found " + lines);
        }
        return found;
    }

    /** TFC's mineable tag of the vessel of {@code color} (exactly one). */
    static String tool(TfcCheckout tfc, String color) {
        String block = Json.FROM_NS + ":" + tfcPath(color);
        String found = null;
        for (String t : List.of("axe", "hoe", "pickaxe", "shovel")) {
            String src = MINEABLE + t + ".json";
            for (JsonElement v : Json.parse(tfc.readString(src), src).getAsJsonObject().getAsJsonArray("values")) {
                if (v.isJsonPrimitive() && v.getAsString().equals(block)) {
                    if (found != null) {
                        throw new DatagenException(block + " is in two mineable tags: " + found + ", " + t);
                    }
                    found = t;
                }
            }
        }
        if (found == null) {
            throw new DatagenException(block + " is in no minecraft:mineable tag");
        }
        return found;
    }

    /** TFC's variants: every sealed state and facing draws the model of that state, turned about y only. */
    static Map<String, Integer> checkBlockstate(TfcCheckout tfc, String color) {
        String src = BLOCKSTATES + tfcPath(color) + ".json";
        JsonObject variants = Json.parse(tfc.readString(src), src).getAsJsonObject().getAsJsonObject("variants");
        if (variants == null || variants.size() != 2 * FACINGS.size()) {
            throw new DatagenException(src + ": not one variant per sealed state and facing");
        }
        Map<String, Integer> turns = new LinkedHashMap<>();
        for (boolean sealed : new boolean[] {false, true}) {
            for (String facing : FACINGS) {
                String key = "sealed=" + sealed + ",facing=" + facing;
                JsonObject v = variants.getAsJsonObject(key);
                String want = Json.FROM_NS + ":" + modelPath(color, sealed);
                if (v == null || !want.equals(v.get("model").getAsString()) || v.has("x")) {
                    throw new DatagenException(src + ": " + key + " no longer draws " + want + " turned about y only");
                }
                turns.put(sealed + "," + facing, v.has("y") ? v.get("y").getAsInt() : 0);
            }
        }
        return turns;
    }

    /** TFC's table: the vessel itself (a sealed one also copies its contents, which the device puts on the item instead). */
    static void checkLoot(TfcCheckout tfc, String color) {
        String self = Json.FROM_NS + ":" + tfcPath(color);
        String src = LOOT + tfcPath(color) + ".json";
        JsonObject root = Json.parse(tfc.readString(src), src).getAsJsonObject();
        JsonArray pools = root.getAsJsonArray("pools");
        if (pools == null || pools.size() != 1) {
            throw new DatagenException(src + ": not one pool");
        }
        JsonObject pool = pools.get(0).getAsJsonObject();
        JsonArray conditions = pool.getAsJsonArray("conditions");
        if (pool.get("rolls").getAsDouble() != 1 || conditions == null || conditions.size() != 1
            || !"minecraft:survives_explosion".equals(conditions.get(0).getAsJsonObject().get("condition").getAsString())) {
            throw new DatagenException(src + ": the pool is not one roll under survives_explosion");
        }
        JsonArray entries = pool.getAsJsonArray("entries");
        if (entries.size() != 1 || !"minecraft:alternatives".equals(entries.get(0).getAsJsonObject().get("type").getAsString())) {
            throw new DatagenException(src + ": the entry is not one alternatives entry");
        }
        JsonArray children = entries.get(0).getAsJsonObject().getAsJsonArray("children");
        if (children.size() != 2) {
            throw new DatagenException(src + ": the alternatives no longer have two children");
        }
        for (JsonElement ch : children) {
            JsonObject o = ch.getAsJsonObject();
            if (!"minecraft:item".equals(o.get("type").getAsString()) || !self.equals(o.get("name").getAsString())) {
                throw new DatagenException(src + ": a child does not drop " + self);
            }
        }
    }

    static DeviceBlockSpec spec(String color, String strength, String tool, Map<String, Integer> turns) {
        String id = id(color);
        DeviceBlockSpec.Builder b = DeviceBlockSpec.builder(id)
            .property(DeviceProperty.names(FACING, FACINGS, "north"))
            .property(DeviceProperty.bool(SEALED, false));
        Map<String, String> hidden = new LinkedHashMap<>();
        for (boolean sealed : new boolean[] {false, true}) {
            for (String facing : FACINGS) {
                String name = HitboxDemands.largeVesselAppearance(sealed, facing);
                HitboxAllocation.Assignment a = carrier(id, name);
                b.appearance(DeviceAppearance.rendered(name, DeviceAppearance.Carrier.state(a.state()),
                    List.of(DeviceAppearance.Renderer.item(renderItem(color, sealed)).rotated(rotation(turns.get(sealed + "," + facing))))));
                if (a.hideTarget() != null) {
                    hidden.putIfAbsent(a.state(), a.hideTarget());
                }
            }
            b.renderItem(renderItem(color, sealed), model(color, sealed));
        }
        b.everyState(s -> HitboxDemands.largeVesselAppearance(Boolean.parseBoolean(s.get(SEALED)), s.get(FACING)), null)
            .settings(DeviceBlockSpec.Settings.of(strength, strength, DeviceSound.STONE, tool))
            .loot(DeviceLoot.self())
            .deviceBehaviour()
            .blockItem(langKey(color));
        hidden.forEach(b::stateMapping);
        return b.build();
    }

    /** The carrier of one appearance; the vessel's is hitbox-only (the look is the item display). */
    static HitboxAllocation.Assignment carrier(String id, String appearance) {
        HitboxAllocation.Assignment a = HitboxAllocation.agreed(id, appearance);
        if (a.mode() != HitboxAllocation.Mode.HITBOX_ONLY) {
            throw new DatagenException("the large vessel carrier of " + id + "#" + appearance + " is " + a.mode()
                + ", LargeVesselBlocks draws a transparent carrier");
        }
        return a;
    }

    /** TFC's two block models of {@code color} with their parent chains, and the item model (no sealed override). */
    static void copyModels(TfcCheckout tfc, OutputTree out, String color, TreeSet<String> done, TreeSet<String> textures) {
        for (boolean sealed : new boolean[] {false, true}) {
            copyChain(tfc, out, Json.FROM_NS + ":" + modelPath(color, sealed), done, textures);
        }
        String path = "item/" + tfcPath(color);
        String src = MODELS + path + ".json";
        JsonObject item = Json.parse(tfc.readString(src), src).getAsJsonObject();
        if (!(Json.FROM_NS + ":" + modelPath(color, false)).equals(item.get("parent").getAsString())) {
            throw new DatagenException(src + " is no longer the opened block model as parent");
        }
        item.remove("overrides");
        item.remove("__comment__");
        out.put(RP + "models/" + path + ".json", Json.write(Json.rename(item)));
    }

    private static void copyChain(TfcCheckout tfc, OutputTree out, String model, TreeSet<String> done, TreeSet<String> textures) {
        if (!model.startsWith(Json.FROM_NS + ":") || !done.add(model)) {
            return;
        }
        String path = model.substring(Json.FROM_NS.length() + 1);
        String src = MODELS + path + ".json";
        if (!tfc.exists(src)) {
            throw new DatagenException("a large vessel model has parent " + model + ", which TFC has no model for");
        }
        JsonObject m = Json.parse(tfc.readString(src), src).getAsJsonObject();
        m.remove("__comment__");
        JsonObject t = m.getAsJsonObject("textures");
        if (t != null) {
            for (Map.Entry<String, JsonElement> e : t.entrySet()) {
                Matcher x = TEXTURE.matcher(e.getValue().getAsString());
                if (x.matches()) {
                    textures.add(x.group(1));
                }
            }
        }
        out.put(RP + "models/" + path + ".json", Json.write(Json.rename(m)));
        if (m.has("parent")) {
            copyChain(tfc, out, m.get("parent").getAsString(), done, textures);
        }
    }

    private static void copyTextures(TfcCheckout tfc, OutputTree out, TreeSet<String> textures) {
        for (String t : textures) {
            String png = TEXTURES + t + ".png";
            if (!tfc.exists(png)) {
                throw new DatagenException("a large vessel model names " + t + ", which TFC has no texture for");
            }
            out.put(RP + "textures/" + t + ".png", tfc.read(png));
            if (tfc.exists(png + ".mcmeta")) {
                out.put(RP + "textures/" + t + ".png.mcmeta", tfc.read(png + ".mcmeta"));
            }
        }
    }

    /** {@code block.tfp.ceramic.large_vessel[.colour]} in every TFC locale that names them (English must name all). */
    static void writeLang(TfcCheckout tfc, OutputTree out) {
        for (String f : tfc.list(LANG)) {
            if (!f.endsWith(".json")) {
                continue;
            }
            String locale = f.substring(LANG.length(), f.length() - ".json".length());
            JsonObject src = Json.parse(tfc.readString(f), f).getAsJsonObject();
            Map<String, String> keys = new TreeMap<>();
            for (String color : colors()) {
                String tfcKey = "block." + Json.FROM_NS + "." + tfcPath(color).replace('/', '.');
                if (src.has(tfcKey)) {
                    keys.put(langKey(color), src.get(tfcKey).getAsString());
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
