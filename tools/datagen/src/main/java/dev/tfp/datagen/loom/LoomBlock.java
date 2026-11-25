/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/wood/TFCLoomBlock.java @ c3b4d9536 (FACING default north, SHAPE_*),
 *       src/main/java/net/dries007/tfc/common/blocks/wood/Wood.java @ c3b4d9536 (LOOM: strength(2.5F), the wood sound),
 *       src/main/resources/assets/tfc/blockstates/wood/loom/*.json, models/block/loom.json, models/block/wood/loom/*.json,
 *       models/item/wood/loom/*.json and textures/block/wood/planks/*.png @ c3b4d9536 (copied),
 *       src/main/resources/data/tfc/loot_table/blocks/wood/loom/*.json @ c3b4d9536 (the drop),
 *       src/generated/resources/data/minecraft/tags/block/mineable/axe.json @ c3b4d9536 (the tool),
 *       src/main/resources/assets/tfc/lang/*.json @ c3b4d9536 (block.tfc.wood.loom.*)
 */
package dev.tfp.datagen.loom;

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
 * PAR-PROC-06: the looms as CraftEngine blocks {@code tfp:wood/loom/<wood>}, one per TFC wood, in
 * {@code craftengine/tfp/configuration/loom/loom.yml}. The plugin's {@code LoomDevice} runs them.
 *
 * <ul>
 *   <li>TFC's {@code facing} (the four horizontal directions, default north); TFC's water-logging property is not a state
 *       (ledger). 4 states per wood.</li>
 *   <li>Carriers: the hitbox selector serves TFC's four outlines hitbox-only ({@link HitboxDemands}, shared by every wood);
 *       the look is TFC's block model of that wood as an {@code entity_renderer} item display turned by the blockstate's
 *       {@code y}.</li>
 *   <li>The {@code tfp:device} behaviour through {@code tfp:block/device_rendered}, TFC's settings (strength 2.5, wood sounds,
 *       the axe tag), the loot (the loom itself) and the block item with TFC's item model.</li>
 * </ul>
 */
public final class LoomBlock {
    public static final String KIND = Json.TO_NS + ":wood/loom";
    static final String SOURCE = "src/main/java/net/dries007/tfc/common/blocks/wood/TFCLoomBlock.java";
    static final String WOOD = "src/main/java/net/dries007/tfc/common/blocks/wood/Wood.java";
    static final String BLOCKSTATES = TfcCheckout.ASSETS + "blockstates/wood/loom/";
    static final String MODELS = TfcCheckout.ASSETS + "models/";
    static final String TEXTURES = TfcCheckout.ASSETS + "textures/";
    static final String LOOT = TfcCheckout.MAIN + "data/" + Json.FROM_NS + "/loot_table/blocks/wood/loom/";
    static final String MINEABLE = TfcCheckout.GENERATED + "data/minecraft/tags/block/mineable/";
    static final String LANG = TfcCheckout.ASSETS + "lang/";
    static final String PACK = "craftengine/tfp/";
    public static final String OUTPUT = PACK + "configuration/loom/loom.yml";
    static final String RP = PACK + "resourcepack/assets/tfp/";
    static final String LANG_RP = PACK + "resourcepack/assets/tfp_loom/lang/";
    public static final String FACING = "facing";
    public static final List<String> FACINGS = HitboxDemands.FACINGS;
    private static final Pattern LOOM_LINE = Pattern.compile(
        "LOOM\\(\\(self, wood\\) -> new TFCLoomBlock\\(properties\\(wood\\)\\.strength\\(([0-9.]+)F\\)");
    private static final Pattern WOOD_SOUND = Pattern.compile(
        "private static ExtendedProperties properties\\(RegistryWood wood\\)\\s*\\{\\s*return ExtendedProperties\\.of\\(wood\\.woodColor\\(\\)\\)\\.sound\\(SoundType\\.(\\w+)\\)");
    private static final Pattern TEXTURE = Pattern.compile("\"" + Json.FROM_NS + ":(block/[a-z0-9_/]+)\"");

    private LoomBlock() {
    }

    /** True when the checkout has the looms (the small test fixtures do not). */
    public static boolean available(TfcCheckout tfc) {
        return tfc.exists(SOURCE) && tfc.exists(MODELS + "block/loom.json") && tfc.exists(WOOD);
    }

    public static String id(String wood) {
        return HitboxDemands.loomId(wood);
    }

    public static String langKey(String wood) {
        return "block." + Json.TO_NS + ".wood.loom." + wood;
    }

    public static String model(String wood) {
        return Json.TO_NS + ":block/wood/loom/" + wood;
    }

    /** The display-only item that draws {@link #model}. */
    public static String renderItem(String wood) {
        return Json.TO_NS + ":render/wood/loom/" + wood;
    }

    /** The display turn of TFC's blockstate {@code y} (see SluiceBlock.rotation). */
    static String rotation(int y) {
        return "0," + Math.floorMod(180 - y, 360) + ",0";
    }

    /** Writes the entries, their models, textures and names. Returns the number of server states (4 per wood). */
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
            Map<String, Integer> turns = checkBlockstate(tfc, wood);
            copyModels(tfc, out, wood);
            DeviceBlockSpec spec = spec(wood, strength, tool, turns);
            states += (int) spec.stateCount();
            merge(DeviceYaml.templated(spec), blocks, items, mappings);
        }
        String baseSrc = MODELS + "block/loom.json";
        String base = tfc.readString(baseSrc);
        out.put(RP + "models/block/loom.json", Json.write(Json.rename(Json.parse(base, baseSrc))));
        copyTextures(tfc, out, base, baseSrc);
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
                throw new DatagenException("loom carrier " + e.getKey() + " hidden as " + old + " and " + e.getValue());
            }
        }
    }

    /** TFC's state: FACING default north (and the water property the plugin does not model). */
    static void checkSource(TfcCheckout tfc) {
        String src = tfc.readString(SOURCE).replaceAll("\\s+", " ");
        for (String needle : List.of("public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;",
                "registerDefaultState(getStateDefinition().any().setValue(FACING, Direction.NORTH)",
                "builder.add(FACING, FLUID);",
                "super(properties, InventoryRemoveBehavior.DROP);",
                "state.setValue(FACING, context.getHorizontalDirection().getOpposite())")) {
            if (!src.contains(needle)) {
                throw new DatagenException(SOURCE + " no longer contains \"" + needle + "\": re-check the loom entry");
            }
        }
    }

    /** TFC's woods: {@link HitboxDemands#BARREL_WOODS} and the blockstates. */
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

    /** TFC's {@code LOOM} hardness (one value); its sound must be wood. */
    static String strength(TfcCheckout tfc) {
        String src = tfc.readString(WOOD);
        Matcher m = LOOM_LINE.matcher(src);
        if (!m.find()) {
            throw new DatagenException(WOOD + ": the loom registration no longer matches " + LOOM_LINE.pattern());
        }
        Matcher s = WOOD_SOUND.matcher(src);
        if (!s.find() || !s.group(1).equals("WOOD")) {
            throw new DatagenException(WOOD + ": the wood block properties no longer use SoundType.WOOD");
        }
        return m.group(1);
    }

    /** TFC's mineable tag of the loom of {@code wood} (exactly one). */
    static String tool(TfcCheckout tfc, String wood) {
        String block = Json.FROM_NS + ":wood/loom/" + wood;
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

    /** TFC's variants: every facing draws the one model of the wood, turned about y. */
    static Map<String, Integer> checkBlockstate(TfcCheckout tfc, String wood) {
        String src = BLOCKSTATES + wood + ".json";
        JsonObject variants = Json.parse(tfc.readString(src), src).getAsJsonObject().getAsJsonObject("variants");
        if (variants == null || variants.size() != FACINGS.size()) {
            throw new DatagenException(src + ": not one variant per facing");
        }
        Map<String, Integer> turns = new LinkedHashMap<>();
        for (String facing : FACINGS) {
            JsonObject v = variants.getAsJsonObject("facing=" + facing);
            String want = Json.FROM_NS + ":block/wood/loom/" + wood;
            if (v == null || !want.equals(v.get("model").getAsString()) || v.has("x")) {
                throw new DatagenException(src + ": facing=" + facing + " no longer draws " + want + " turned about y only");
            }
            turns.put(facing, v.has("y") ? v.get("y").getAsInt() : 0);
        }
        return turns;
    }

    static DeviceBlockSpec spec(String wood, String strength, String tool, Map<String, Integer> turns) {
        String id = id(wood);
        DeviceBlockSpec.Builder b = DeviceBlockSpec.builder(id).property(DeviceProperty.names(FACING, FACINGS, "north"));
        Map<String, String> hidden = new LinkedHashMap<>();
        for (String facing : FACINGS) {
            HitboxAllocation.Assignment a = carrier(id, facing);
            b.appearance(DeviceAppearance.rendered(facing, DeviceAppearance.Carrier.state(a.state()),
                List.of(DeviceAppearance.Renderer.item(renderItem(wood)).rotated(rotation(turns.get(facing))))));
            if (a.hideTarget() != null) {
                hidden.putIfAbsent(a.state(), a.hideTarget());
            }
        }
        b.everyState(s -> s.get(FACING), null)
            .settings(DeviceBlockSpec.Settings.of(strength, strength, DeviceSound.WOOD, tool))
            .loot(DeviceLoot.self())
            .deviceBehaviour()
            .blockItem(langKey(wood))
            .renderItem(renderItem(wood), model(wood));
        hidden.forEach(b::stateMapping);
        return b.build();
    }

    /** The carrier of one appearance; the loom's is hitbox-only (the look is the item display). */
    static HitboxAllocation.Assignment carrier(String id, String appearance) {
        HitboxAllocation.Assignment a = HitboxAllocation.agreed(id, appearance);
        if (a.mode() != HitboxAllocation.Mode.HITBOX_ONLY) {
            throw new DatagenException("the loom carrier of " + id + "#" + appearance + " is " + a.mode()
                + ", LoomBlock draws a transparent carrier");
        }
        return a;
    }

    /** TFC's per-wood block model and item model, renamed; and the textures they name. */
    static void copyModels(TfcCheckout tfc, OutputTree out, String wood) {
        String path = "block/wood/loom/" + wood;
        String src = MODELS + path + ".json";
        String text = tfc.readString(src);
        JsonObject m = Json.parse(text, src).getAsJsonObject();
        if (!(Json.FROM_NS + ":block/loom").equals(m.get("parent").getAsString())) {
            throw new DatagenException(src + ": parent is not tfc:block/loom");
        }
        out.put(RP + "models/" + path + ".json", Json.write(Json.rename(m)));
        copyTextures(tfc, out, text, src);
        String isrc = MODELS + "item/wood/loom/" + wood + ".json";
        JsonObject item = Json.parse(tfc.readString(isrc), isrc).getAsJsonObject();
        if (!(Json.FROM_NS + ":block/wood/loom/" + wood).equals(item.get("parent").getAsString())) {
            throw new DatagenException(isrc + " is no longer the block model as parent");
        }
        out.put(RP + "models/item/wood/loom/" + wood + ".json", Json.write(Json.rename(item)));
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

    /** {@code block.tfp.wood.loom.<wood>} in every TFC locale that names the looms (English must name all). */
    static void writeLang(TfcCheckout tfc, OutputTree out, List<String> woods) {
        for (String f : tfc.list(LANG)) {
            if (!f.endsWith(".json")) {
                continue;
            }
            String locale = f.substring(LANG.length(), f.length() - ".json".length());
            JsonObject src = Json.parse(tfc.readString(f), f).getAsJsonObject();
            Map<String, String> keys = new TreeMap<>();
            for (String wood : woods) {
                String tfcKey = "block." + Json.FROM_NS + ".wood.loom." + wood;
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
