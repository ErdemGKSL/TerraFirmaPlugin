/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/plant/coral/Coral.java @ c3b4d9536 (the 5 colours x 6 types, sounds,
 *       instabreak, noCollission, requiresCorrectToolForDrops, needsItem),
 *       src/main/java/net/dries007/tfc/common/blocks/TFCBlocks.java @ c3b4d9536 (CORAL ids, SEA_PICKLE: light, slime sound),
 *       src/main/resources/assets/tfc/blockstates/coral/*.json and blockstates/sea_pickle.json @ c3b4d9536 (vanilla models),
 *       src/main/resources/assets/tfc/models/item/coral/*.json and models/item/sea_pickle.json @ c3b4d9536 (copied),
 *       src/main/resources/data/tfc/loot_table/blocks/coral/*.json and loot_table/blocks/sea_pickle.json @ c3b4d9536
 *       (shipped for the loot runtime),
 *       src/generated/resources/data/minecraft/tags/block/mineable/{pickaxe,hoe}.json @ c3b4d9536 (tool tags),
 *       src/main/resources/assets/tfc/lang/*.json @ c3b4d9536 (block.tfc.coral.*, block.tfc.sea_pickle)
 */
package dev.tfp.datagen.coral;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.tfp.datagen.DatagenException;
import dev.tfp.datagen.Json;
import dev.tfp.datagen.LootTableExport;
import dev.tfp.datagen.OutputTree;
import dev.tfp.datagen.TfcCheckout;
import dev.tfp.datagen.Yaml;
import dev.tfp.datagen.device.DeviceYaml;
import dev.tfp.datagen.hitbox.HitboxAllocation;
import dev.tfp.datagen.hitbox.HitboxDemands;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * PAR-WORLD-50: TFC's 30 corals ({@code tfp:coral/<colour>_<type>}) and its sea pickle ({@code tfp:sea_pickle}) as
 * CraftEngine blocks ({@value #OUTPUT}); the plugin's {@code paper/coral} runs their rules (dying, support loss).
 *
 * <ul>
 *   <li>Properties: {@code waterlogged} (TFC's {@code fluid} empty / salt water; CraftEngine's hardcoded waterlogging,
 *       {@code fluid_state: water} on the wet variants), {@code facing} for the wall fans, {@code pickles} 1-4 for the sea
 *       pickle ({@code stackable_block} with its own item, vanilla {@code SeaPickleBlock.canBeReplaced}).</li>
 *   <li>Settings: instabreak (hardness 0), no collision (the twin's), wet grass sounds on the living corals, stone on the
 *       dead ones, which only drop with a pickaxe ({@code requiresCorrectToolForDrops}); slime sounds and light
 *       {@code 3 + 3 * pickles} when wet for the sea pickle. Tool tags from TFC's generated mineable tags.</li>
 *   <li>Looks: TFC draws vanilla's models, so every appearance takes the hitbox selector's carrier: the vanilla twin's state
 *       of the same properties ({@code minecraft:dead_tube_coral_wall_fan[facing=east,waterlogged=true]}) with its vanilla
 *       look ({@link #selected}).</li>
 *   <li>Items only for coral and dead coral ({@code Coral.BlockType.needsItem}) and the sea pickle. Loot: TFC's tables
 *       shipped for the loot runtime, no CraftEngine {@code loot:}.</li>
 * </ul>
 */
public final class CoralBlockGen {
    static final String PACK = "craftengine/tfp/";
    public static final String OUTPUT = PACK + "configuration/coral/coral.yml";
    static final String RP = PACK + "resourcepack/assets/tfp/";
    static final String LANG_RP = PACK + "resourcepack/assets/tfp_coral/lang/";
    static final String ASSETS = TfcCheckout.ASSETS;
    static final String LANG = ASSETS + "lang/";
    static final String CORAL_SRC = "src/main/java/net/dries007/tfc/common/blocks/plant/coral/Coral.java";
    static final String BLOCKS_SRC = "src/main/java/net/dries007/tfc/common/blocks/TFCBlocks.java";
    static final String PICKAXE_TAG = "src/generated/resources/data/minecraft/tags/block/mineable/pickaxe.json";
    static final String HOE_TAG = "src/generated/resources/data/minecraft/tags/block/mineable/hoe.json";
    public static final String SEA_PICKLE = HitboxDemands.SEA_PICKLE_ID;

    private CoralBlockGen() {
    }

    /** Every block id: the corals in TFC's order, then the sea pickle. */
    public static List<String> blocks() {
        List<String> ids = new ArrayList<>(HitboxDemands.coralIds());
        ids.add(SEA_PICKLE);
        return List.copyOf(ids);
    }

    /** The ids TFC registers an item for: coral and dead coral ({@code needsItem}) and the sea pickle. */
    public static List<String> itemIds() {
        List<String> ids = new ArrayList<>();
        for (String id : HitboxDemands.coralIds()) {
            String type = HitboxDemands.coralType(id);
            if (type.equals("coral") || type.equals("dead_coral")) {
                ids.add(id);
            }
        }
        ids.add(SEA_PICKLE);
        return List.copyOf(ids);
    }

    public static boolean available(TfcCheckout tfc) {
        return tfc.exists(CORAL_SRC) && tfc.exists(ASSETS + "blockstates/sea_pickle.json");
    }

    /** Writes the blocks, items, item models, delegated loot and names; returns the number of appearances. */
    public static int run(TfcCheckout tfc, OutputTree out) {
        checkSource(tfc);
        Map<String, Object> blocks = new LinkedHashMap<>();
        Map<String, Object> items = new LinkedHashMap<>();
        int appearances = 0;
        List<String> pickaxe = tag(tfc, PICKAXE_TAG);
        List<String> hoe = tag(tfc, HOE_TAG);
        for (String id : HitboxDemands.coralIds()) {
            Map<String, Object> b = coral(tfc, id, pickaxe);
            appearances += ((Map<?, ?>) ((Map<?, ?>) b.get("states")).get("appearances")).size();
            blocks.put(id, b);
        }
        Map<String, Object> pickle = seaPickle(tfc, hoe);
        appearances += 8;
        blocks.put(SEA_PICKLE, pickle);
        for (String id : itemIds()) {
            items.put(id, item(id));
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("blocks", blocks);
        root.put("items", items);
        out.put(OUTPUT, Yaml.write(DeviceYaml.HEADER, root));
        for (String id : itemIds()) {
            String src = ASSETS + "models/item/" + path(id) + ".json";
            out.put(RP + "models/item/" + path(id) + ".json", Json.write(Json.rename(Json.parse(tfc.readString(src), src))));
        }
        for (String id : blocks()) {
            String src = TfcCheckout.MAIN + "data/" + Json.FROM_NS + "/loot_table/blocks/" + path(id) + ".json";
            LootTableExport.delegateBlock(out, path(id), Json.rename(Json.parse(tfc.readString(src), src)), named -> { });
        }
        writeLang(tfc, out);
        return appearances;
    }

    static void checkSource(TfcCheckout tfc) {
        String coral = tfc.readString(CORAL_SRC).replaceAll("\\s+", " ");
        for (String needle : List.of(
                "DEAD_CORAL((color, type) -> new TFCCoralPlantBlock(TFCCoralPlantBlock.BIG_SHAPE, BlockBehaviour.Properties.of()"
                    + ".mapColor(MapColor.COLOR_GRAY).requiresCorrectToolForDrops().noCollission().instabreak()))",
                "TFCBlocks.CORAL.get(color).get(DEAD_CORAL), BlockBehaviour.Properties.of().mapColor(color.color).noCollission()"
                    + ".instabreak().sound(SoundType.WET_GRASS)",
                "return this == DEAD_CORAL || this == CORAL;")) {
            if (!coral.contains(needle)) {
                throw new DatagenException(CORAL_SRC + " no longer contains \"" + needle + "\"");
            }
        }
        String blocks = tfc.readString(BLOCKS_SRC).replaceAll("\\s+", " ");
        for (String needle : List.of("lightLevel((state) -> TFCSeaPickleBlock.isDead(state) ? 0 : 3 + 3 * state.getValue("
                + "SeaPickleBlock.PICKLES)).sound(SoundType.SLIME_BLOCK)", "register(\"coral/\" + color.toString() + \"_\" + "
                + "type.toString(), type.create(color), type.createBlockItem(new Item.Properties()))")) {
            if (!blocks.contains(needle)) {
                throw new DatagenException(BLOCKS_SRC + " no longer contains \"" + needle + "\"");
            }
        }
    }

    /** The {@code tfp:} ids of a TFC block tag (plain entries only). */
    static List<String> tag(TfcCheckout tfc, String src) {
        List<String> ids = new ArrayList<>();
        for (JsonElement e : Json.parse(tfc.readString(src), src).getAsJsonObject().getAsJsonArray("values")) {
            if (e.isJsonPrimitive()) {
                ids.add(Json.renameId(e.getAsString()));
            }
        }
        return ids;
    }

    static String path(String id) {
        return id.substring(id.indexOf(':') + 1);
    }

    /**
     * The selector's carrier of {@code appearance}: the vanilla twin's state of the same properties, vanilla look. Anything
     * else fails.
     */
    public static String selected(String blockId, String appearance) {
        HitboxAllocation.Assignment a = HitboxAllocation.agreed(blockId, appearance);
        String expected = HitboxDemands.twin(blockId) + "[" + appearance + "]";
        if (a.mode() != HitboxAllocation.Mode.VANILLA || a.hideTarget() != null || a.pool() || !a.state().equals(expected)) {
            throw new DatagenException("the carrier of " + blockId + "#" + appearance + " is " + a.state() + " (" + a.mode()
                + "); CoralBlockGen draws " + expected + " with its vanilla look");
        }
        return a.state();
    }

    /** Vanilla's sound set {@code minecraft:block.<name>.*} as plain ids (WET_GRASS, STONE, SLIME_BLOCK are 1.0 / 1.0). */
    static Map<String, Object> sounds(String name) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (String e : List.of("break", "step", "place", "hit", "fall")) {
            m.put(e, "minecraft:block." + name + "." + e);
        }
        return m;
    }

    private static Map<String, Object> settings(String sound, List<String> tags, String item) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("hardness", 0);
        s.put("resistance", 0);
        s.put("sounds", sounds(sound));
        s.put("push_reaction", "destroy");
        if (!tags.isEmpty()) {
            s.put("tags", tags);
        }
        if (item != null) {
            s.put("item", item);
        }
        return s;
    }

    private static Map<String, Object> bool() {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("type", "boolean");
        p.put("default", false);
        return p;
    }

    private static Map<String, Object> coral(TfcCheckout tfc, String id, List<String> pickaxe) {
        String type = HitboxDemands.coralType(id);
        boolean dead = type.startsWith("dead_");
        boolean wall = type.endsWith("wall_fan");
        checkModels(tfc, id, wall);
        List<String> tags = pickaxe.contains(id) ? List.of("minecraft:mineable/pickaxe") : List.of();
        Map<String, Object> s = settings(dead ? "stone" : "wet_grass", tags, itemIds().contains(id) ? id : null);
        if (dead) {
            s.put("correct_tools", List.of("#minecraft:pickaxes", "#tfp:tools/pickaxe")); // requiresCorrectToolForDrops
        }
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("settings", s);
        Map<String, Object> props = new LinkedHashMap<>();
        if (wall) {
            Map<String, Object> facing = new LinkedHashMap<>();
            facing.put("type", "horizontal_direction");
            facing.put("values", HitboxDemands.FACINGS);
            facing.put("default", "north");
            props.put("facing", facing);
        }
        props.put("waterlogged", bool());
        Map<String, Object> appearances = new LinkedHashMap<>();
        Map<String, Object> variants = new LinkedHashMap<>();
        for (String f : wall ? HitboxDemands.FACINGS : java.util.Collections.<String>singletonList(null)) {
            for (boolean wet : new boolean[] {false, true}) {
                String key = HitboxDemands.coralAppearance(f, wet);
                appearances.put(key, Map.of("state", selected(id, key)));
                Map<String, Object> variant = new LinkedHashMap<>();
                variant.put("appearance", key);
                if (wet) {
                    variant.put("settings", Map.of("fluid_state", "water"));
                }
                variants.put(key, variant);
            }
        }
        block.put("states", states(props, appearances, variants));
        return block;
    }

    /** TFC's blockstate of a coral draws vanilla's model of its twin (a sign that the twin's look is TFC's). */
    static void checkModels(TfcCheckout tfc, String id, boolean wall) {
        String src = ASSETS + "blockstates/" + path(id) + ".json";
        JsonObject v = Json.parse(tfc.readString(src), src).getAsJsonObject().getAsJsonObject("variants");
        String model = "minecraft:block/" + HitboxDemands.twin(id).substring("minecraft:".length());
        for (String key : wall ? HitboxDemands.FACINGS.stream().map(f -> "facing=" + f).toList() : List.of("")) {
            JsonElement e = v.get(key);
            if (e == null || !e.getAsJsonObject().get("model").getAsString().equals(model)) {
                throw new DatagenException(src + " " + key + " no longer draws " + model);
            }
        }
    }

    private static Map<String, Object> seaPickle(TfcCheckout tfc, List<String> hoe) {
        String src = ASSETS + "blockstates/sea_pickle.json";
        JsonObject v = Json.parse(tfc.readString(src), src).getAsJsonObject().getAsJsonObject("variants");
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("settings", settings("slime_block", hoe.contains(SEA_PICKLE) ? List.of("minecraft:mineable/hoe") : List.of(),
            SEA_PICKLE));
        Map<String, Object> stack = new LinkedHashMap<>();
        stack.put("type", "stackable_block");
        stack.put("property", "pickles");
        stack.put("items", List.of(SEA_PICKLE));
        block.put("behavior", List.of(stack));
        Map<String, Object> props = new LinkedHashMap<>();
        Map<String, Object> pickles = new LinkedHashMap<>();
        pickles.put("type", "int");
        pickles.put("range", "1~4");
        pickles.put("default", 1);
        props.put("pickles", pickles);
        props.put("waterlogged", bool());
        Map<String, Object> appearances = new LinkedHashMap<>();
        Map<String, Object> variants = new LinkedHashMap<>();
        for (int n = 1; n <= 4; n++) {
            for (boolean wet : new boolean[] {false, true}) {
                if (v.get("pickles=" + n + ",fluid=" + (wet ? "salt_water" : "empty")) == null) {
                    throw new DatagenException(src + " has no pickles=" + n + " for " + (wet ? "salt water" : "no fluid"));
                }
                String key = HitboxDemands.seaPickleAppearance(n, wet);
                appearances.put(key, Map.of("state", selected(SEA_PICKLE, key)));
                Map<String, Object> s = new LinkedHashMap<>();
                s.put("luminance", HitboxDemands.seaPickleLight(n, wet));
                if (wet) {
                    s.put("fluid_state", "water");
                }
                Map<String, Object> variant = new LinkedHashMap<>();
                variant.put("appearance", key);
                variant.put("settings", s);
                variants.put(key, variant);
            }
        }
        block.put("states", states(props, appearances, variants));
        return block;
    }

    private static Map<String, Object> states(Map<String, Object> props, Map<String, Object> appearances,
                                              Map<String, Object> variants) {
        Map<String, Object> states = new LinkedHashMap<>();
        states.put("properties", props);
        states.put("appearances", appearances);
        states.put("variants", variants);
        return states;
    }

    private static Map<String, Object> item(String id) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("material", "paper");
        item.put("data", Map.of("item_name", "<!i><lang:block." + Json.TO_NS + "." + path(id).replace('/', '.') + ">"));
        item.put("model", Json.TO_NS + ":item/" + path(id));
        item.put("behavior", List.of(Map.of("type", "block_item", "block", id)));
        return item;
    }

    static void writeLang(TfcCheckout tfc, OutputTree out) {
        for (String f : tfc.list(LANG)) {
            if (!f.endsWith(".json")) {
                continue;
            }
            String locale = f.substring(LANG.length(), f.length() - ".json".length());
            JsonObject src = Json.parse(tfc.readString(f), f).getAsJsonObject();
            JsonObject o = new JsonObject();
            for (String block : blocks()) {
                String key = path(block).replace('/', '.');
                if (src.has("block." + Json.FROM_NS + "." + key)) {
                    o.addProperty("block." + Json.TO_NS + "." + key, src.get("block." + Json.FROM_NS + "." + key).getAsString());
                } else if (locale.equals("en_us")) {
                    throw new DatagenException("TFC has no English name block.tfc." + key);
                }
            }
            if (o.size() > 0) {
                out.put(LANG_RP + locale + ".json", Json.write(o));
            }
        }
    }
}
