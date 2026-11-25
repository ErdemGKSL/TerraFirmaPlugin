/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/SluiceBlock.java @ c3b4d9536 (FACING, UPPER, default state),
 *       src/main/java/net/dries007/tfc/common/blocks/wood/Wood.java @ c3b4d9536 (SLUICE: strength(3F), the wood sound),
 *       src/main/resources/assets/tfc/blockstates/wood/sluice/*.json, models/block/sluice_{upper,lower}.json,
 *       models/block/wood/sluice/*.json, models/item/wood/sluice/*.json and textures/block/wood/sheet/*.png @ c3b4d9536
 *       (copied),
 *       src/main/resources/data/tfc/loot_table/blocks/wood/sluice/*.json @ c3b4d9536 (the drop: the upper half only),
 *       src/generated/resources/data/minecraft/tags/block/mineable/axe.json @ c3b4d9536 (the tool),
 *       src/main/resources/assets/tfc/lang/*.json @ c3b4d9536 (block.tfc.wood.sluice.*)
 */
package dev.tfp.datagen.sluice;

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
 * PAR-PROC-13: the sluices as CraftEngine blocks {@code tfp:wood/sluice/<wood>}, one per TFC wood, in
 * {@code craftengine/tfp/configuration/sluice/sluice.yml}. The plugin's {@code SluiceDevice} runs them.
 *
 * <ul>
 *   <li>TFC's two states: {@code facing} (the four horizontal directions, default north) and {@code upper} (default true).
 *       The plugin sets the facing and places the lower half at placement ({@code SluiceListener}); 8 states per wood.</li>
 *   <li>Carriers: the hitbox selector serves TFC's sixteen outlines hitbox-only ({@link HitboxDemands}, one per facing and
 *       half, shared by every wood); the look is TFC's block model of that wood and half as an {@code entity_renderer}
 *       item display, turned by the blockstate's {@code y}.</li>
 *   <li>The {@code tfp:device} behaviour through {@code tfp:block/device_rendered}, TFC's settings (strength 3, wood sounds,
 *       the axe tag), the loot (the block itself, from the upper half only) and the block item with TFC's item model.</li>
 * </ul>
 */
public final class SluiceBlock {
    public static final String KIND = Json.TO_NS + ":wood/sluice";
    static final String SOURCE = "src/main/java/net/dries007/tfc/common/blocks/devices/SluiceBlock.java";
    static final String WOOD = "src/main/java/net/dries007/tfc/common/blocks/wood/Wood.java";
    static final String BLOCKSTATES = TfcCheckout.ASSETS + "blockstates/wood/sluice/";
    static final String MODELS = TfcCheckout.ASSETS + "models/";
    static final String TEXTURES = TfcCheckout.ASSETS + "textures/";
    static final String LOOT = TfcCheckout.MAIN + "data/" + Json.FROM_NS + "/loot_table/blocks/wood/sluice/";
    static final String MINEABLE = TfcCheckout.GENERATED + "data/minecraft/tags/block/mineable/";
    static final String LANG = TfcCheckout.ASSETS + "lang/";
    static final String PACK = "craftengine/tfp/";
    public static final String OUTPUT = PACK + "configuration/sluice/sluice.yml";
    static final String RP = PACK + "resourcepack/assets/tfp/";
    static final String LANG_RP = PACK + "resourcepack/assets/tfp_sluice/lang/";
    public static final String FACING = "facing";
    public static final String UPPER = "upper";
    public static final List<String> FACINGS = HitboxDemands.FACINGS;
    static final List<String> BASES = List.of("block/sluice_upper", "block/sluice_lower");
    private static final Pattern SLUICE_LINE = Pattern.compile(
        "SLUICE\\(wood -> new SluiceBlock\\(properties\\(wood\\)\\.strength\\(([0-9.]+)F\\)");
    private static final Pattern WOOD_SOUND = Pattern.compile(
        "private static ExtendedProperties properties\\(RegistryWood wood\\)\\s*\\{\\s*return ExtendedProperties\\.of\\(wood\\.woodColor\\(\\)\\)\\.sound\\(SoundType\\.(\\w+)\\)");
    private static final Pattern TEXTURE = Pattern.compile("\"" + Json.FROM_NS + ":(block/[a-z0-9_/]+)\"");

    private SluiceBlock() {
    }

    /** True when the checkout has the sluices (the small test fixtures do not). */
    public static boolean available(TfcCheckout tfc) {
        return tfc.exists(SOURCE) && tfc.exists(MODELS + "block/sluice_upper.json") && tfc.exists(WOOD);
    }

    public static String id(String wood) {
        return HitboxDemands.sluiceId(wood);
    }

    public static String langKey(String wood) {
        return "block." + Json.TO_NS + ".wood.sluice." + wood;
    }

    /** The block model of {@code wood} and one half (TFC's, copied). */
    public static String model(String wood, boolean upper) {
        return Json.TO_NS + ":block/wood/sluice/" + wood + (upper ? "_upper" : "_lower");
    }

    /** The display-only item that draws {@link #model}. */
    public static String renderItem(String wood, boolean upper) {
        return Json.TO_NS + ":render/wood/sluice/" + wood + (upper ? "_upper" : "_lower");
    }

    /** The display turn of TFC's blockstate {@code y} (see BarrelBlock.sideRotation). */
    static String rotation(int y) {
        return "0," + Math.floorMod(180 - y, 360) + ",0";
    }

    /** Writes the entries, their models, textures and names. Returns the number of server states (8 per wood). */
    public static int run(TfcCheckout tfc, OutputTree out) {
        checkSource(tfc);
        List<String> woods = woods(tfc);
        String strength = strength(tfc);
        Map<String, Object> blocks = new LinkedHashMap<>();
        Map<String, Object> items = new LinkedHashMap<>();
        Map<String, Object> mappings = new TreeMap<>();
        int states = 0;
        for (String wood : woods) {
            String tool = tool(tfc, wood);
            checkLoot(tfc, wood);
            Map<String, Integer> turns = checkBlockstate(tfc, wood);
            copyModels(tfc, out, wood);
            DeviceBlockSpec spec = spec(wood, strength, tool, turns);
            states += (int) spec.stateCount();
            merge(yaml(spec), blocks, items, mappings);
        }
        copyParents(tfc, out);
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("blocks", blocks);
        root.put("items", items);
        if (!mappings.isEmpty()) {
            root.put("block_state_mappings", mappings);
        }
        out.put(OUTPUT, Yaml.write(DeviceYaml.HEADER, root));
        writeLang(tfc, out, woods);
        return states;
    }

    @SuppressWarnings("unchecked")
    private static void merge(Map<String, Object> one, Map<String, Object> blocks, Map<String, Object> items,
            Map<String, Object> mappings) {
        blocks.putAll((Map<String, Object>) one.get("blocks"));
        items.putAll((Map<String, Object>) one.getOrDefault("items", Map.of()));
        for (Map.Entry<String, Object> e : ((Map<String, Object>) one.getOrDefault("block_state_mappings", Map.of())).entrySet()) {
            Object old = mappings.putIfAbsent(e.getKey(), e.getValue());
            if (old != null && !old.equals(e.getValue())) {
                throw new DatagenException("sluice carrier " + e.getKey() + " hidden as " + old + " and " + e.getValue());
            }
        }
    }

    /** The templated entry whose loot drops only from the upper half (TFC's {@code block_state_property upper=true}). */
    @SuppressWarnings("unchecked")
    static Map<String, Object> yaml(DeviceBlockSpec spec) {
        Map<String, Object> root = DeviceYaml.templated(spec);
        Map<String, Object> block = (Map<String, Object>) ((Map<String, Object>) root.get("blocks")).get(spec.id());
        Map<String, Object> only = new LinkedHashMap<>();
        only.put("type", "match_block_property");
        only.put("properties", new LinkedHashMap<>(Map.of(UPPER, "true")));
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("type", "item");
        entry.put("item", spec.id());
        entry.put("conditions", List.of(only));
        Map<String, Object> pool = new LinkedHashMap<>();
        pool.put("rolls", 1);
        pool.put("conditions", List.of(Map.of("type", "survives_explosion")));
        pool.put("entries", List.of(entry));
        block.put("loot", new LinkedHashMap<>(Map.of("pools", List.of(pool))));
        return root;
    }

    /** TFC's states: FACING default north, UPPER default true. */
    static void checkSource(TfcCheckout tfc) {
        String src = tfc.readString(SOURCE).replaceAll("\\s+", " ");
        for (String needle : List.of("public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;",
                "public static final BooleanProperty UPPER = TFCBlockStateProperties.UPPER;",
                "registerDefaultState(getStateDefinition().any().setValue(UPPER, true).setValue(FACING, Direction.NORTH));",
                "super.createBlockStateDefinition(builder.add(UPPER, FACING));",
                "super(properties, InventoryRemoveBehavior.DROP);")) {
            if (!src.contains(needle)) {
                throw new DatagenException(SOURCE + " no longer contains \"" + needle + "\": re-check the sluice entry");
            }
        }
    }

    /** TFC's woods: {@link HitboxDemands#BARREL_WOODS} (checked against {@code Wood.java} by BarrelBlock) and the blockstates. */
    static List<String> woods(TfcCheckout tfc) {
        List<String> woods = HitboxDemands.BARREL_WOODS;
        TreeSet<String> states = new TreeSet<>();
        for (String f : tfc.list(BLOCKSTATES)) {
            if (f.endsWith(".json")) {
                states.add(f.substring(BLOCKSTATES.length(), f.length() - ".json".length()));
            }
        }
        if (!states.equals(new TreeSet<>(woods))) {
            throw new DatagenException(BLOCKSTATES + " holds " + states + ", not the woods " + woods);
        }
        return woods;
    }

    /** TFC's {@code SLUICE} hardness (one value: strength(h) sets both); its sound must be wood. */
    static String strength(TfcCheckout tfc) {
        String src = tfc.readString(WOOD);
        Matcher m = SLUICE_LINE.matcher(src);
        if (!m.find()) {
            throw new DatagenException(WOOD + ": the sluice registration no longer matches " + SLUICE_LINE.pattern());
        }
        Matcher s = WOOD_SOUND.matcher(src);
        if (!s.find() || !s.group(1).equals("WOOD")) {
            throw new DatagenException(WOOD + ": the wood block properties no longer use SoundType.WOOD");
        }
        return m.group(1);
    }

    /** TFC's mineable tag of the sluice of {@code wood} (exactly one). */
    static String tool(TfcCheckout tfc, String wood) {
        String block = Json.FROM_NS + ":wood/sluice/" + wood;
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

    /** TFC's table: one pool, survives_explosion, the sluice itself under {@code upper=true}. */
    static void checkLoot(TfcCheckout tfc, String wood) {
        String src = LOOT + wood + ".json";
        String self = Json.FROM_NS + ":wood/sluice/" + wood;
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
        if (entries.size() != 1) {
            throw new DatagenException(src + ": not one entry");
        }
        JsonObject entry = entries.get(0).getAsJsonObject();
        JsonArray own = entry.getAsJsonArray("conditions");
        if (!"minecraft:item".equals(entry.get("type").getAsString()) || !self.equals(entry.get("name").getAsString())
            || own == null || own.size() != 1) {
            throw new DatagenException(src + ": the entry is not " + self + " under one condition");
        }
        JsonObject c = own.get(0).getAsJsonObject();
        JsonObject props = c.getAsJsonObject("properties");
        if (!"minecraft:block_state_property".equals(c.get("condition").getAsString()) || !self.equals(c.get("block").getAsString())
            || props == null || props.size() != 1 || !"true".equals(props.get("upper").getAsString())) {
            throw new DatagenException(src + ": the condition is not upper=true");
        }
    }

    /** TFC's variants: both halves draw {@code <wood>_upper|_lower}, turned about y by one angle per facing. */
    static Map<String, Integer> checkBlockstate(TfcCheckout tfc, String wood) {
        String src = BLOCKSTATES + wood + ".json";
        JsonObject variants = Json.parse(tfc.readString(src), src).getAsJsonObject().getAsJsonObject("variants");
        if (variants == null || variants.size() != FACINGS.size() * 2) {
            throw new DatagenException(src + ": not one variant per facing and half");
        }
        Map<String, Integer> turns = new LinkedHashMap<>();
        for (String facing : FACINGS) {
            for (boolean upper : new boolean[] {true, false}) {
                String key = "facing=" + facing + ",upper=" + upper;
                JsonObject v = variants.getAsJsonObject(key);
                String want = Json.FROM_NS + ":block/wood/sluice/" + wood + (upper ? "_upper" : "_lower");
                if (v == null || !want.equals(v.get("model").getAsString()) || v.has("x")) {
                    throw new DatagenException(src + ": " + key + " no longer draws " + want + " turned about y only");
                }
                int y = v.has("y") ? v.get("y").getAsInt() : 0;
                Integer old = turns.putIfAbsent(facing, y);
                if (old != null && old != y) {
                    throw new DatagenException(src + ": facing=" + facing + " turns " + old + " and " + y);
                }
            }
        }
        return turns;
    }

    static DeviceBlockSpec spec(String wood, String strength, String tool, Map<String, Integer> turns) {
        String id = id(wood);
        DeviceBlockSpec.Builder b = DeviceBlockSpec.builder(id)
            .property(DeviceProperty.names(FACING, FACINGS, "north"))
            .property(DeviceProperty.bool(UPPER, true));
        Map<String, String> hidden = new LinkedHashMap<>();
        for (String facing : FACINGS) {
            for (boolean upper : new boolean[] {true, false}) {
                HitboxAllocation.Assignment a = carrier(id, HitboxDemands.sluiceAppearance(facing, upper));
                b.appearance(DeviceAppearance.rendered(HitboxDemands.sluiceAppearance(facing, upper),
                    DeviceAppearance.Carrier.state(a.state()), List.of(DeviceAppearance.Renderer
                        .item(renderItem(wood, upper)).rotated(rotation(turns.get(facing))))));
                if (a.hideTarget() != null) {
                    hidden.putIfAbsent(a.state(), a.hideTarget());
                }
            }
        }
        b.everyState(s -> HitboxDemands.sluiceAppearance(s.get(FACING), Boolean.parseBoolean(s.get(UPPER))), null)
            .settings(DeviceBlockSpec.Settings.of(strength, strength, DeviceSound.WOOD, tool))
            .loot(DeviceLoot.self())
            .deviceBehaviour()
            .blockItem(langKey(wood))
            .renderItem(renderItem(wood, true), model(wood, true))
            .renderItem(renderItem(wood, false), model(wood, false));
        hidden.forEach(b::stateMapping);
        return b.build();
    }

    /** The carrier of one appearance; the sluice's is hitbox-only (the look is the item display). */
    static HitboxAllocation.Assignment carrier(String id, String appearance) {
        HitboxAllocation.Assignment a = HitboxAllocation.agreed(id, appearance);
        if (a.mode() != HitboxAllocation.Mode.HITBOX_ONLY) {
            throw new DatagenException("the sluice carrier of " + id + "#" + appearance + " is " + a.mode()
                + ", SluiceBlock draws a transparent carrier");
        }
        return a;
    }

    /** TFC's per-wood block models of both halves and the item model, renamed; and the textures they name. */
    static void copyModels(TfcCheckout tfc, OutputTree out, String wood) {
        for (String half : List.of("_upper", "_lower")) {
            String path = "block/wood/sluice/" + wood + half;
            String src = MODELS + path + ".json";
            String text = tfc.readString(src);
            JsonObject m = Json.parse(text, src).getAsJsonObject();
            String parent = m.get("parent").getAsString();
            if (!(Json.FROM_NS + ":block/sluice" + half).equals(parent)) {
                throw new DatagenException(src + ": parent " + parent + " is not tfc:block/sluice" + half);
            }
            out.put(RP + "models/" + path + ".json", Json.write(Json.rename(m)));
            copyTextures(tfc, out, text, src);
        }
        String src = MODELS + "item/wood/sluice/" + wood + ".json";
        JsonObject item = Json.parse(tfc.readString(src), src).getAsJsonObject();
        if (!(Json.FROM_NS + ":block/wood/sluice/" + wood + "_lower").equals(item.get("parent").getAsString())) {
            throw new DatagenException(src + " is no longer the lower half's block model as parent");
        }
        out.put(RP + "models/item/wood/sluice/" + wood + ".json", Json.write(Json.rename(item)));
    }

    /** The two base models and their textures. */
    static void copyParents(TfcCheckout tfc, OutputTree out) {
        for (String path : BASES) {
            String src = MODELS + path + ".json";
            String text = tfc.readString(src);
            out.put(RP + "models/" + path + ".json", Json.write(Json.rename(Json.parse(text, src))));
            copyTextures(tfc, out, text, src);
        }
    }

    private static void copyTextures(TfcCheckout tfc, OutputTree out, String modelText, String source) {
        JsonObject textures = Json.parse(modelText, source).getAsJsonObject().getAsJsonObject("textures");
        if (textures == null) {
            return;
        }
        for (Map.Entry<String, JsonElement> e : textures.entrySet()) {
            Matcher t = TEXTURE.matcher("\"" + e.getValue().getAsString() + "\"");
            if (!t.matches()) {
                continue;
            }
            String png = TEXTURES + t.group(1) + ".png";
            if (!tfc.exists(png)) {
                throw new DatagenException(source + " names " + t.group(1) + ", which TFC has no texture for");
            }
            out.put(RP + "textures/" + t.group(1) + ".png", tfc.read(png));
        }
    }

    /** {@code block.tfp.wood.sluice.<wood>} in every TFC locale that names the sluices (English must name all). */
    static void writeLang(TfcCheckout tfc, OutputTree out, List<String> woods) {
        for (String f : tfc.list(LANG)) {
            if (!f.endsWith(".json")) {
                continue;
            }
            String locale = f.substring(LANG.length(), f.length() - ".json".length());
            JsonObject src = Json.parse(tfc.readString(f), f).getAsJsonObject();
            Map<String, String> keys = new TreeMap<>();
            for (String wood : woods) {
                String tfcKey = "block." + Json.FROM_NS + ".wood.sluice." + wood;
                if (src.has(tfcKey)) {
                    keys.put(langKey(wood), src.get(tfcKey).getAsString());
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
