/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/DryingBricksBlock.java @ c3b4d9536 (COUNT, DRIED, SHAPE_1..4),
 *       src/main/java/net/dries007/tfc/common/blocks/soil/SoilBlockType.java @ c3b4d9536 (DRYING_BRICKS: noCollission,
 *       noOcclusion, instabreak, STEM sound; the shovel tag),
 *       src/main/java/net/dries007/tfc/common/blocks/ThatchBlock.java @ c3b4d9536 (full cube, no collision),
 *       src/main/java/net/dries007/tfc/common/blocks/TFCBlocks.java @ c3b4d9536 (THATCH),
 *       src/main/java/net/dries007/tfc/common/items/TFCItems.java @ c3b4d9536 (mud_brick/*),
 *       src/main/resources/assets/tfc/blockstates/drying_bricks/*.json, blockstates/thatch.json,
 *       models/block/mud_bricks/*.json, models/block/thatch.json, models/item/drying_bricks/*.json,
 *       models/item/mud_brick/*.json, models/item/thatch.json, textures/block/mud/*.png, textures/block/mud_bricks/*.png,
 *       textures/block/thatch.png, textures/item/mud_brick/*.png and data/tfc/loot_table/blocks/{drying_bricks/*,thatch}.json
 *       @ c3b4d9536 (copied or checked),
 *       src/main/resources/assets/tfc/lang/*.json @ c3b4d9536 (names)
 */
package dev.tfp.datagen.drying;

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
import java.util.regex.Pattern;

/**
 * PAR-POT-08: the drying bricks, the mud brick items and the thatch block as CraftEngine content in
 * {@code craftengine/tfp/configuration/drying/}.
 *
 * <ul>
 *   <li>{@code tfp:drying_bricks/<soil>} for each of TFC's eight soils: TFC's two properties {@code count} (1..4) and
 *       {@code dried}, so 8 server states. TFC's outline per count ({@code SHAPE_1..4}) is hitbox-only on a transparent
 *       carrier shared by every soil (no collision: {@code noCollission}); TFC's model {@code wet_N_<soil>} or
 *       {@code dry_N_<soil>} is an item display ({@code entity_renderer}). Settings are TFC's (instabreak, the shovel tag);
 *       no loot table: the plugin drops TFC's loot (count of the wet block item, or of the dry mud brick) when the pile is removed. The block item is the wet brick.</li>
 *   <li>{@code tfp:mud_brick/<soil>}: the dried brick, a plain item with TFC's model, texture and names. The plugin's
 *       {@code DryingBricks} (PAR-POT-08) runs the drying.</li>
 *   <li>{@code tfp:thatch}: TFC's full cube ({@code block/powder_snow} with the thatch texture) on the
 *       selector's carrier; the movement slowdown is PAR-GATHER-10's {@code PlantSlowProvider}. TFC's thatch holds water; ours
 *       does not (ledger).</li>
 * </ul>
 */
public final class DryingBlocks {
    static final String SOURCE = "src/main/java/net/dries007/tfc/common/blocks/devices/DryingBricksBlock.java";
    static final String SOIL_SOURCE = "src/main/java/net/dries007/tfc/common/blocks/soil/SoilBlockType.java";
    static final String THATCH_SOURCE = "src/main/java/net/dries007/tfc/common/blocks/ThatchBlock.java";
    static final String BLOCKSTATES = TfcCheckout.ASSETS + "blockstates/";
    static final String MODELS = TfcCheckout.ASSETS + "models/";
    static final String TEXTURES = TfcCheckout.ASSETS + "textures/";
    static final String LOOT = TfcCheckout.MAIN + "data/" + Json.FROM_NS + "/loot_table/blocks/";
    static final String LANG = TfcCheckout.ASSETS + "lang/";
    static final String PACK = "craftengine/tfp/";
    public static final String OUTPUT = PACK + "configuration/drying/drying.yml";
    static final String RP = PACK + "resourcepack/assets/tfp/";
    static final String LANG_RP = PACK + "resourcepack/assets/tfp_drying/lang/";
    public static final String COUNT = "count";
    public static final String DRIED = "dried";
    public static final String BRICK_PREFIX = "mud_brick/";
    /** TFC's count range ({@code COUNT_1_4}). */
    public static final int MAX_COUNT = 4;
    /** The half turn every block-model item display needs to match the block (see ForgeDevices.OVERLAY_ROTATION). */
    static final String RENDER_ROTATION = "0,180,0";
    static final String HEADER = DeviceYaml.HEADER;
    private static final Pattern TEXTURE = Pattern.compile(Json.FROM_NS + ":((?:block|item)/[a-z0-9_/]+)");

    private DryingBlocks() {
    }

    /** True when the checkout has the blocks and the soil list (the small test fixtures do not). */
    public static boolean available(TfcCheckout tfc) {
        return tfc.exists(SOURCE) && tfc.exists(THATCH_SOURCE) && tfc.exists(BLOCKSTATES + "thatch.json")
            && tfc.isDirectory(BLOCKSTATES + "drying_bricks/");
    }

    /** The soils that have a drying bricks blockstate, sorted. */
    public static List<String> soils(TfcCheckout tfc) {
        TreeSet<String> out = new TreeSet<>();
        for (String f : tfc.list(BLOCKSTATES + "drying_bricks/")) {
            if (f.endsWith(".json")) {
                out.add(f.substring(f.lastIndexOf('/') + 1, f.length() - ".json".length()));
            }
        }
        return List.copyOf(out);
    }

    /** Writes the entries, models, textures and names. Returns the number of server states (8 per soil, 1 for thatch). */
    public static int run(TfcCheckout tfc, OutputTree out) {
        List<String> soils = soils(tfc);
        if (!soils.equals(HitboxDemands.DRYING_SOILS)) {
            throw new DatagenException("TFC's drying bricks soils are " + soils + ", HitboxDemands.DRYING_SOILS lists "
                + HitboxDemands.DRYING_SOILS);
        }
        checkSources(tfc);
        Map<String, Object> blocks = new LinkedHashMap<>();
        Map<String, Object> items = new LinkedHashMap<>();
        Map<String, Object> mappings = new LinkedHashMap<>();
        TreeSet<String> textures = new TreeSet<>();
        TreeSet<String> done = new TreeSet<>();
        int states = 0;
        for (String soil : soils) {
            checkBlockstate(tfc, soil);
            checkLoot(tfc, soil);
            DeviceBlockSpec spec = spec(tfc, soil);
            merge(DeviceYaml.templated(spec), blocks, items, mappings);
            states += (int) spec.stateCount();
            for (int count = 1; count <= MAX_COUNT; count++) {
                for (boolean dried : new boolean[] {false, true}) {
                    copyModel(tfc, out, HitboxDemands.dryingModel(soil, count, dried), done, textures);
                }
            }
            copyItemModel(tfc, out, "drying_bricks/" + soil, done, textures);
            copyItemModel(tfc, out, BRICK_PREFIX + soil, done, textures);
            Map<String, Object> brick = new LinkedHashMap<>();
            brick.put("template", "tfp:item/simple");
            brick.put("arguments", Map.of("item_lang_key", brickLangKey(soil)));
            items.put(Json.TO_NS + ":" + BRICK_PREFIX + soil, brick);
        }
        checkThatch(tfc);
        DeviceBlockSpec thatch = thatchSpec(tfc);
        merge(thatchEntry(thatch), blocks, items, mappings);
        states += (int) thatch.stateCount();
        copyModel(tfc, out, HitboxDemands.THATCH_MODEL, done, textures);
        copyItemModel(tfc, out, "thatch", done, textures);
        copyTextures(tfc, out, textures);
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("blocks", blocks);
        root.put("items", items);
        if (!mappings.isEmpty()) {
            root.put("block_state_mappings", mappings);
        }
        out.put(OUTPUT, Yaml.write(HEADER, root));
        writeLang(tfc, out, soils);
        return states;
    }

    @SuppressWarnings("unchecked")
    private static void merge(Map<String, Object> entry, Map<String, Object> blocks, Map<String, Object> items,
            Map<String, Object> mappings) {
        blocks.putAll((Map<String, Object>) entry.get("blocks"));
        Object i = entry.get("items");
        if (i != null) {
            items.putAll((Map<String, Object>) i);
        }
        Object m = entry.get("block_state_mappings");
        if (m != null) {
            ((Map<String, Object>) m).forEach((from, to) -> {
                Object old = mappings.putIfAbsent(from, to);
                if (old != null && !old.equals(to)) {
                    throw new DatagenException(from + " is hidden as both " + old + " and " + to);
                }
            });
        }
    }

    /** The thatch entry written literally (no device behaviour: the block has no record) without empty properties or variants. */
    @SuppressWarnings("unchecked")
    static Map<String, Object> thatchEntry(DeviceBlockSpec spec) {
        Map<String, Object> root = DeviceYaml.literal(spec);
        Map<String, Object> block = (Map<String, Object>) ((Map<String, Object>) root.get("blocks")).get(spec.id());
        Map<String, Object> states = (Map<String, Object>) block.get("states");
        states.remove("properties");
        states.remove("variants");
        return root;
    }

    public static String brickLangKey(String soil) {
        return "item." + Json.TO_NS + ".mud_brick." + soil;
    }

    public static String blockLangKey(String soil) {
        return "block." + Json.TO_NS + ".drying_bricks." + soil;
    }

    public static final String THATCH_LANG_KEY = "block." + Json.TO_NS + ".thatch";

    /** The render item of a hitbox-only look ({@code tfp:render/mud_bricks/wet_1_alfisol}). */
    public static String renderItem(String model) {
        return Json.TO_NS + ":render/" + model.substring(model.indexOf(":block/") + ":block/".length());
    }

    // ---- sources ---------------------------------------------------------------------------------------------------

    /** TFC's properties, shapes and settings the entries depend on. */
    static void checkSources(TfcCheckout tfc) {
        String d = tfc.readString(SOURCE).replaceAll("\\s+", "");
        for (String needle : List.of("COUNT=TFCBlockStateProperties.COUNT_1_4;", "DRIED=TFCBlockStateProperties.DRIED;",
            "setValue(COUNT,1).setValue(DRIED,false)", "box(2,0,1,6,3,7)", "box(10,0,1,14,3,7)", "box(2,0,9,6,3,15)",
            "box(10,0,9,14,3,15)")) {
            if (!d.contains(needle.replaceAll("\\s+", ""))) {
                throw new DatagenException(SOURCE + " no longer contains \"" + needle + "\": re-check the drying bricks entry");
            }
        }
        String soil = tfc.readString(SOIL_SOURCE).replaceAll("\\s+", "");
        String reg = "DRYING_BRICKS((self,variant)->newDryingBricksBlock(ExtendedProperties.of(MapColor.DIRT).noCollission()"
            + ".noOcclusion().instabreak().sound(SoundType.STEM)";
        if (!soil.contains(reg)) {
            throw new DatagenException(SOIL_SOURCE + " no longer registers " + reg);
        }
    }

    static void checkThatch(TfcCheckout tfc) {
        String t = tfc.readString(THATCH_SOURCE).replaceAll("\\s+", "");
        if (!t.contains("getCollisionShape(BlockStatestate,BlockGetterlevel,BlockPospos,CollisionContextcontext){returnShapes.empty();")) {
            throw new DatagenException(THATCH_SOURCE + " no longer has an empty collision shape");
        }
        String reg = "register(\"thatch\",()->newThatchBlock(ExtendedProperties.of(MapColor.SAND).strength(0.6F,0.4F).noOcclusion()"
            + ".isViewBlocking(TFCBlocks::never).sound(TFCSounds.THATCH).flammable(50,100)))";
        if (!tfc.readString("src/main/java/net/dries007/tfc/common/blocks/TFCBlocks.java").replaceAll("\\s+", "")
            .contains(reg.replaceAll("\\s+", ""))) {
            throw new DatagenException("TFCBlocks no longer registers " + reg);
        }
        JsonObject bs = Json.parse(tfc.readString(BLOCKSTATES + "thatch.json"), "thatch.json").getAsJsonObject();
        JsonObject v = bs.getAsJsonObject("variants");
        if (v == null || v.size() != 1 || !v.has("")
            || !(Json.FROM_NS + ":block/thatch").equals(v.getAsJsonObject("").get("model").getAsString())) {
            throw new DatagenException("thatch blockstate is no longer one unrotated variant of tfc:block/thatch");
        }
        checkSelfLoot(tfc, "thatch", "thatch");
    }

    /** TFC's blockstate of a soil: eight variants {@code count=N,dried=B} drawing {@code mud_bricks/<wet|dry>_N_<soil>}. */
    static void checkBlockstate(TfcCheckout tfc, String soil) {
        String src = BLOCKSTATES + "drying_bricks/" + soil + ".json";
        JsonObject variants = Json.parse(tfc.readString(src), src).getAsJsonObject().getAsJsonObject("variants");
        Map<String, String> got = new TreeMap<>();
        if (variants != null) {
            variants.entrySet().forEach(e -> got.put(e.getKey(), e.getValue().getAsJsonObject().get("model").getAsString()));
        }
        Map<String, String> want = new TreeMap<>();
        for (int count = 1; count <= MAX_COUNT; count++) {
            for (boolean dried : new boolean[] {false, true}) {
                want.put("count=" + count + ",dried=" + dried, Json.FROM_NS + ":block/mud_bricks/" + (dried ? "dry" : "wet") + "_"
                    + count + "_" + soil);
            }
        }
        if (!got.equals(want)) {
            throw new DatagenException(src + " no longer draws the models DryingBlocks writes: " + got);
        }
    }

    /** TFC's table: per count and dried flag one pool dropping count of the wet block item or the dry mud brick. */
    static void checkLoot(TfcCheckout tfc, String soil) {
        String src = LOOT + "drying_bricks/" + soil + ".json";
        JsonArray pools = Json.parse(tfc.readString(src), src).getAsJsonObject().getAsJsonArray("pools");
        if (pools == null || pools.size() != 2 * MAX_COUNT) {
            throw new DatagenException(src + ": not " + 2 * MAX_COUNT + " pools");
        }
        Map<String, String> got = new TreeMap<>();
        for (JsonElement p : pools) {
            JsonObject entry = p.getAsJsonObject().getAsJsonArray("entries").get(0).getAsJsonObject();
            JsonObject props = entry.getAsJsonArray("conditions").get(0).getAsJsonObject().getAsJsonObject("properties");
            int n = entry.getAsJsonArray("functions").get(0).getAsJsonObject().get("count").getAsInt();
            if (n != Integer.parseInt(props.get(COUNT).getAsString())) {
                throw new DatagenException(src + ": a pool drops " + n + " for count " + props.get(COUNT));
            }
            got.put("count=" + props.get(COUNT).getAsString() + ",dried=" + props.get(DRIED).getAsString(),
                entry.get("name").getAsString());
        }
        Map<String, String> want = new TreeMap<>();
        for (int count = 1; count <= MAX_COUNT; count++) {
            want.put("count=" + count + ",dried=false", Json.FROM_NS + ":drying_bricks/" + soil);
            want.put("count=" + count + ",dried=true", Json.FROM_NS + ":" + BRICK_PREFIX + soil);
        }
        if (!got.equals(want)) {
            throw new DatagenException(src + " no longer drops what DryingBlocks writes: " + got);
        }
    }

    static void checkSelfLoot(TfcCheckout tfc, String block, String item) {
        String src = LOOT + block + ".json";
        JsonArray pools = Json.parse(tfc.readString(src), src).getAsJsonObject().getAsJsonArray("pools");
        if (pools == null || pools.size() != 1) {
            throw new DatagenException(src + ": not one pool");
        }
        JsonObject pool = pools.get(0).getAsJsonObject();
        JsonArray entries = pool.getAsJsonArray("entries");
        JsonObject conditions = pool.getAsJsonArray("conditions").get(0).getAsJsonObject();
        JsonObject entry = entries.size() == 1 ? entries.get(0).getAsJsonObject() : null;
        if (!"minecraft:survives_explosion".equals(conditions.get("condition").getAsString()) || entry == null
            || !(Json.FROM_NS + ":" + item).equals(entry.get("name").getAsString()) || entry.size() != 2) {
            throw new DatagenException(src + ": not one survives_explosion pool dropping the block itself");
        }
    }

    // ---- specs -----------------------------------------------------------------------------------------------------

    /** The 8 states of one soil: appearance {@code <wet|dry><count>}, its carrier shared by every soil. */
    static DeviceBlockSpec spec(TfcCheckout tfc, String soil) {
        String id = HitboxDemands.dryingId(soil);
        DeviceBlockSpec.Builder b = DeviceBlockSpec.builder(id)
            .property(DeviceProperty.range(COUNT, 1, MAX_COUNT, 1))
            .property(DeviceProperty.bool(DRIED, false));
        Map<String, String> hidden = new LinkedHashMap<>();
        for (int count = 1; count <= MAX_COUNT; count++) {
            for (boolean dried : new boolean[] {false, true}) {
                String name = HitboxDemands.dryingAppearance(count, dried);
                HitboxAllocation.Assignment a = HitboxAllocation.agreed(id, name);
                if (a.mode() != HitboxAllocation.Mode.HITBOX_ONLY) {
                    throw new DatagenException(id + "#" + name + " got a " + a.mode() + " carrier; drying bricks draw item displays");
                }
                String model = HitboxDemands.dryingModel(soil, count, dried);
                String item = renderItem(model);
                b.renderItem(item, model);
                b.appearance(DeviceAppearance.rendered(name, DeviceAppearance.Carrier.state(a.state()),
                    List.of(DeviceAppearance.Renderer.item(item).rotated(RENDER_ROTATION))));
                if (a.hideTarget() != null && !a.pool() && hidden.putIfAbsent(a.state(), a.hideTarget()) == null) {
                    b.stateMapping(a.state(), a.hideTarget());
                }
            }
        }
        b.everyState(s -> HitboxDemands.dryingAppearance(Integer.parseInt(s.get(COUNT)), Boolean.parseBoolean(s.get(DRIED))), null)
            .settings(DeviceBlockSpec.Settings.of("0", "0", DeviceSound.WOOD, "shovel"))
            .deviceBehaviour()
            .blockItem(blockLangKey(soil));
        return b.build();
    }

    /** Thatch: one state, TFC's cube drawn on the selector's carrier or as a display on a hitbox-only one. */
    static DeviceBlockSpec thatchSpec(TfcCheckout tfc) {
        HitboxAllocation.Assignment a = HitboxAllocation.agreed(HitboxDemands.THATCH_ID, HitboxDemands.THATCH_APPEARANCE);
        DeviceBlockSpec.Builder b = DeviceBlockSpec.builder(HitboxDemands.THATCH_ID);
        switch (a.mode()) {
            case MODEL -> b.appearance(DeviceAppearance.modelled(HitboxDemands.THATCH_APPEARANCE,
                DeviceAppearance.Carrier.state(a.state()), HitboxDemands.THATCH_MODEL));
            case POOL -> b.appearance(DeviceAppearance.modelled(HitboxDemands.THATCH_APPEARANCE,
                DeviceAppearance.Carrier.pool(dev.tfp.datagen.hitbox.VanillaCandidates.poolName(a.state())),
                HitboxDemands.THATCH_MODEL));
            case HITBOX_ONLY -> {
                String item = renderItem(HitboxDemands.THATCH_MODEL);
                b.appearance(DeviceAppearance.rendered(HitboxDemands.THATCH_APPEARANCE, DeviceAppearance.Carrier.state(a.state()),
                    List.of(DeviceAppearance.Renderer.item(item).rotated(RENDER_ROTATION))));
                b.renderItem(item, HitboxDemands.THATCH_MODEL);
            }
            default -> throw new DatagenException("the thatch carrier is " + a.mode() + ", DryingBlocks writes a model or a transparent carrier");
        }
        b.everyState(s -> HitboxDemands.THATCH_APPEARANCE, null)
            .settings(DeviceBlockSpec.Settings.fromTfc(tfc, "thatch"))
            .loot(DeviceLoot.self())
            .blockItem(THATCH_LANG_KEY);
        if (a.hideTarget() != null && !a.pool()) {
            b.stateMapping(a.state(), a.hideTarget());
        }
        return b.build();
    }

    // ---- models ----------------------------------------------------------------------------------------------------

    /** The block model {@code tfp:block/<path>} (TFC's, namespace renamed) with its parents; names its textures. */
    static void copyModel(TfcCheckout tfc, OutputTree out, String ourModel, TreeSet<String> done, TreeSet<String> textures) {
        copyModelFile(tfc, out, ourModel.substring(Json.TO_NS.length() + 1), done, textures);
    }

    static void copyItemModel(TfcCheckout tfc, OutputTree out, String path, TreeSet<String> done, TreeSet<String> textures) {
        copyModelFile(tfc, out, "item/" + path, done, textures);
    }

    private static void copyModelFile(TfcCheckout tfc, OutputTree out, String path, TreeSet<String> done, TreeSet<String> textures) {
        if (!done.add(path)) {
            return;
        }
        String src = MODELS + path + ".json";
        if (!tfc.exists(src)) {
            throw new DatagenException("TFC has no model " + path);
        }
        JsonObject model = Json.parse(tfc.readString(src), src).getAsJsonObject();
        model.remove("__comment__");
        if (model.has("textures")) {
            for (Map.Entry<String, JsonElement> e : model.getAsJsonObject("textures").entrySet()) {
                String v = e.getValue().getAsString();
                if (v.startsWith(Json.FROM_NS + ":")) {
                    textures.add(v.substring(Json.FROM_NS.length() + 1));
                }
            }
        }
        out.put(RP + "models/" + path + ".json", Json.write(Json.rename(model)));
        if (model.has("parent") && model.get("parent").getAsString().startsWith(Json.FROM_NS + ":")) {
            copyModelFile(tfc, out, model.get("parent").getAsString().substring(Json.FROM_NS.length() + 1), done, textures);
        }
    }

    static void copyTextures(TfcCheckout tfc, OutputTree out, TreeSet<String> textures) {
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

    // ---- names -----------------------------------------------------------------------------------------------------

    /** The names of the blocks, block items and mud bricks in every TFC locale that has them (English must). */
    static void writeLang(TfcCheckout tfc, OutputTree out, List<String> soils) {
        List<String[]> keys = new ArrayList<>();
        for (String soil : soils) {
            keys.add(new String[] {"block." + Json.FROM_NS + ".drying_bricks." + soil, blockLangKey(soil)});
            keys.add(new String[] {"item." + Json.FROM_NS + ".mud_brick." + soil, brickLangKey(soil)});
        }
        keys.add(new String[] {"block." + Json.FROM_NS + ".thatch", THATCH_LANG_KEY});
        for (String f : tfc.list(LANG)) {
            if (!f.endsWith(".json")) {
                continue;
            }
            String locale = f.substring(LANG.length(), f.length() - ".json".length());
            JsonObject src = Json.parse(tfc.readString(f), f).getAsJsonObject();
            Map<String, String> names = new TreeMap<>();
            for (String[] k : keys) {
                if (src.has(k[0])) {
                    names.put(k[1], src.get(k[0]).getAsString());
                } else if (locale.equals("en_us")) {
                    throw new DatagenException("TFC has no English name " + k[0]);
                }
            }
            if (!names.isEmpty()) {
                JsonObject o = new JsonObject();
                names.forEach(o::addProperty);
                out.put(LANG_RP + locale + ".json", Json.write(o));
            }
        }
    }
}
