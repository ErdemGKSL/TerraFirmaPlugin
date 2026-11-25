/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/plant/fruit/FruitBlocks.java @ c3b4d9536 (Tree: createSapling, createLeaves,
 *       createBranch, createGrowingBranch),
 *       src/main/java/net/dries007/tfc/common/blocks/plant/fruit/{FruitTreeSaplingBlock,FruitTreeBranchBlock,GrowingFruitTreeBranchBlock,
 *       FruitTreeLeavesBlock}.java @ c3b4d9536 (properties, getShape),
 *       src/main/java/net/dries007/tfc/common/blocks/TFCBlocks.java @ c3b4d9536 (the registrations),
 *       src/main/resources/assets/tfc/blockstates/plant/<tree>_{sapling,leaves,branch,growing_branch}.json, models/block/plant/*,
 *       models/item/plant/*, textures/block/fruit_tree/*.png @ c3b4d9536 (copied),
 *       src/main/resources/data/tfc/loot_table/blocks/plant/<tree>_*.json @ c3b4d9536 (the drops)
 */
package dev.tfp.datagen.fruittree;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.tfp.datagen.DatagenException;
import dev.tfp.datagen.Json;
import dev.tfp.datagen.LootTableExport;
import dev.tfp.datagen.OutputTree;
import dev.tfp.datagen.TfcCheckout;
import dev.tfp.datagen.Yaml;
import dev.tfp.datagen.crop.CropBlocks;
import dev.tfp.datagen.device.DeviceAppearance;
import dev.tfp.datagen.device.DeviceBlockSpec;
import dev.tfp.datagen.device.DeviceProperty;
import dev.tfp.datagen.device.DeviceSound;
import dev.tfp.datagen.device.DeviceYaml;
import dev.tfp.datagen.hitbox.HitboxAllocation;
import dev.tfp.datagen.hitbox.HitboxDemands;
import dev.tfp.datagen.hitbox.TfcShapes;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;

/**
 * PAR-FARM-16: TFC's eight fruit trees as CraftEngine blocks ({@code craftengine/tfp/configuration/fruittree/<tree>.yml}):
 * {@code tfp:plant/<tree>_sapling} ({@code saplings} 1..4), {@code _growing_branch} ({@code stage} 0..3, {@code saplings} 0..4,
 * {@code natural}, the six connection flags), {@code _branch} ({@code stage}, the flags) and {@code _leaves} ({@code lifecycle},
 * {@code persistent}). The device kind {@code tfp:fruit_tree} of PAR-FARM-08 runs them.
 *
 * <p>The sapling and the branches draw TFC's models as item displays on hitbox-only carriers (the sapling on the bush outline,
 * every connection pattern of a branch on the carrier the selector picks for TFC's PipeBlock outline); the leaves are full
 * cubes with TFC's four lifecycle models on CraftEngine's leaves pool. Settings are TFC's: sapling strength 0, leaves 0.5,
 * branches 1.0 (scaffolding sound); the drops are TFC's tables.
 */
public final class FruitTreeBlocks {
    static final String P = "src/main/java/net/dries007/tfc/common/blocks/plant/fruit/";
    public static final String FRUIT_BLOCKS = P + "FruitBlocks.java";
    public static final String BLOCKSTATES = TfcCheckout.ASSETS + "blockstates/plant/";
    public static final String MODELS_ITEM = TfcCheckout.ASSETS + "models/item/plant/";
    public static final String LOOT = TfcCheckout.MAIN + "data/" + Json.FROM_NS + "/loot_table/blocks/plant/";
    public static final String LANG = TfcCheckout.ASSETS + "lang/";
    static final String PACK = "craftengine/tfp/";
    public static final String OUTPUT_DIR = PACK + "configuration/fruittree/";
    public static final String RP = PACK + "resourcepack/assets/" + Json.TO_NS + "/";
    public static final String LANG_RP = PACK + "resourcepack/assets/tfp_fruit_tree/lang/";
    /** Vanilla {@code SoundType.GRASS} and {@code SoundType.SCAFFOLDING}. */
    public static final Map<String, String> GRASS = sounds("grass");
    public static final Map<String, String> SCAFFOLDING = sounds("scaffolding");
    /** Vanilla {@code SoundType.SWEET_BERRY_BUSH}: break and place their own events, step, hit and fall are grass. */
    public static final Map<String, String> SWEET_BERRY_BUSH = sweet();
    public static final List<String> LIFECYCLES = List.of("dormant", "healthy", "flowering", "fruiting");
    /** TFC {@code flammableLikeLeaves()} and {@code flammableLikeLogs()}: flammable(flammability, spread). */
    public static final int[] LEAVES_FIRE = {60, 30};
    public static final int[] LOGS_FIRE = {5, 5};

    private FruitTreeBlocks() {
    }

    private static Map<String, String> sweet() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("break", "minecraft:block.sweet_berry_bush.break");
        m.put("step", "minecraft:block.grass.step");
        m.put("place", "minecraft:block.sweet_berry_bush.place");
        m.put("hit", "minecraft:block.grass.hit");
        m.put("fall", "minecraft:block.grass.fall");
        return java.util.Collections.unmodifiableMap(m);
    }

    private static Map<String, String> sounds(String type) {
        Map<String, String> m = new LinkedHashMap<>();
        for (String k : List.of("break", "step", "place", "hit", "fall")) {
            m.put(k, "minecraft:block." + type + "." + k);
        }
        return java.util.Collections.unmodifiableMap(m);
    }

    /** True when the checkout has the fruit trees (the small test fixtures do not). */
    public static boolean available(TfcCheckout tfc) {
        return tfc.exists(FRUIT_BLOCKS) && tfc.exists(BLOCKSTATES + "cherry_leaves.json");
    }

    /** TFC's {@code FruitBlocks.Tree} constants, in order, lower case. */
    public static List<String> trees(TfcCheckout tfc) {
        String src = tfc.readString(FRUIT_BLOCKS);
        int start = src.indexOf("public enum Tree");
        if (start < 0) {
            throw new DatagenException(FRUIT_BLOCKS + " has no Tree enum any more");
        }
        List<String> out = new ArrayList<>();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^\\s+([A-Z_]+)\\(Food\\.", java.util.regex.Pattern.MULTILINE)
            .matcher(src.substring(start));
        while (m.find()) {
            out.add(m.group(1).toLowerCase(java.util.Locale.ROOT));
        }
        return out;
    }

    /** The appearance of a state and the demand appearance (pattern) its carrier is served for. */
    public record Look(String name, String pattern) {
    }

    /** One block: its spec and the models it draws. */
    public record Built(DeviceBlockSpec spec, Set<String> models) {
    }

    /**
     * One block: each state draws TFC's {@code blockstate} model(s) ({@link CropBlocks#look}); {@code look} names the appearance
     * and the demand pattern of the state. A state whose appearance the hitbox pools serve ({@link HitboxDemands#POOLS}) is
     * a full cube on the pool carrier; every other one a set of item displays on its hitbox-only carrier.
     */
    public static Built block(TfcCheckout tfc, String id, String blockstate, List<DeviceProperty> props,
            Function<Map<String, String>, Look> look, DeviceBlockSpec.Settings settings, String itemLangKey) {
        String src = BLOCKSTATES + blockstate + ".json";
        JsonObject bs = Json.parse(tfc.readString(src), src).getAsJsonObject();
        DeviceBlockSpec.Builder b = DeviceBlockSpec.builder(id).properties(props).settings(settings).deviceBehaviour();
        if (itemLangKey != null) {
            b.blockItem(itemLangKey);
        }
        Map<String, DeviceAppearance> apps = new LinkedHashMap<>();
        Map<String, String> byState = new LinkedHashMap<>();
        Set<String> models = new TreeSet<>();
        Set<String> renderItems = new LinkedHashSet<>();
        Map<String, String> hidden = new TreeMap<>();
        for (Map<String, String> state : DeviceBlockSpec.combinations(props)) {
            Look l = look.apply(state);
            byState.put(state.toString(), l.name());
            if (apps.containsKey(l.name())) {
                continue;
            }
            List<CropBlocks.Part> parts = CropBlocks.look(bs, src, state);
            parts.forEach(p -> models.add(p.model()));
            String pool = HitboxDemands.charcoalPitPool(id, l.name());
            if (pool != null) {
                if (parts.size() != 1) {
                    throw new DatagenException(id + "#" + l.name() + " is a pool block with several models");
                }
                apps.put(l.name(), DeviceAppearance.modelled(l.name(), DeviceAppearance.Carrier.pool(pool), parts.getFirst().model()));
                continue;
            }
            HitboxAllocation.Assignment a = HitboxAllocation.agreed(demandOwner(id, l.pattern()), l.pattern());
            if (a.mode() != HitboxAllocation.Mode.HITBOX_ONLY) {
                throw new DatagenException(id + "#" + l.name() + " got a " + a.mode() + " carrier; FruitTreeBlocks draws item displays");
            }
            List<DeviceAppearance.Renderer> renderers = new ArrayList<>();
            for (CropBlocks.Part p : parts) {
                String item = Json.TO_NS + ":render/" + p.model().substring(p.model().indexOf(":block/") + ":block/".length());
                if (renderItems.add(item)) {
                    b.renderItem(item, p.model());
                }
                renderers.add(DeviceAppearance.Renderer.item(item).rotated(CropBlocks.rotation(p.y())));
            }
            if (a.hideTarget() != null && !a.pool() && hidden.putIfAbsent(a.state(), a.hideTarget()) == null) {
                b.stateMapping(a.state(), a.hideTarget());
            }
            apps.put(l.name(), DeviceAppearance.rendered(l.name(), DeviceAppearance.Carrier.state(a.state()), renderers));
        }
        apps.values().forEach(b::appearance);
        b.everyState(s -> byState.get(s.toString()), null);
        return new Built(b.build(), models);
    }

    /** The block id the demand of {@code id} is filed under: the shared fruit tree set (HitboxDemands.fruitDemandOwner), else the block. */
    static String demandOwner(String id, String pattern) {
        for (String t : HitboxDemands.FRUIT_TREES) {
            for (String part : List.of("sapling", "branch", "growing_branch")) {
                if (id.equals(HitboxDemands.fruitId(t, part))) {
                    return HitboxDemands.fruitDemandOwner(part);
                }
            }
        }
        return id;
    }

    public static DeviceBlockSpec.Settings settings(String strength, String tool) {
        return DeviceBlockSpec.Settings.of(strength, strength, DeviceSound.STONE, tool);
    }

    /** The templated entry with the block's sounds, push reaction, fire odds and the not-occluding look of a hitbox-only block. */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> yaml(DeviceBlockSpec spec, Map<String, String> sounds, int[] fire, String tool, boolean occludes) {
        Map<String, Object> root = DeviceYaml.templated(spec);
        Map<String, Object> block = (Map<String, Object>) ((Map<String, Object>) root.get("blocks")).get(spec.id());
        Map<String, Object> args = (Map<String, Object>) block.get("arguments");
        sounds.forEach((k, v) -> args.put("device_sound_" + k, v));
        args.put("device_tool_tag", "minecraft:mineable/" + tool);
        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("push_reaction", "destroy");
        if (!occludes) {
            settings.put("can_occlude", false);
            settings.put("is_suffocating", false);
            settings.put("is_view_blocking", false);
        }
        settings.put("burnable", true);
        // NeoForge: getFireSpreadSpeed is the vanilla ignite odds (CE burn_chance), getFlammability the burn odds.
        settings.put("burn_chance", fire[1]);
        settings.put("fire_spread_chance", fire[0]);
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

    /** Merges the entries of several blocks into one file. */
    @SuppressWarnings("unchecked")
    public static int write(OutputTree out, String path, List<Map<String, Object>> roots, List<Built> blocks) {
        Map<String, Object> allBlocks = new LinkedHashMap<>();
        Map<String, Object> items = new LinkedHashMap<>();
        Map<String, Object> mappings = new TreeMap<>();
        int n = 0;
        for (int i = 0; i < roots.size(); i++) {
            n += (int) blocks.get(i).spec().stateCount();
            Map<String, Object> root = roots.get(i);
            allBlocks.putAll((Map<String, Object>) root.get("blocks"));
            if (root.get("items") != null) {
                ((Map<String, Object>) root.get("items")).forEach(items::putIfAbsent);
            }
            if (root.get("block_state_mappings") != null) {
                mappings.putAll((Map<String, Object>) root.get("block_state_mappings"));
            }
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("blocks", allBlocks);
        if (!items.isEmpty()) {
            root.put("items", items);
        }
        if (!mappings.isEmpty()) {
            root.put("block_state_mappings", mappings);
        }
        out.put(path, Yaml.write(DeviceYaml.HEADER, root));
        return n;
    }

    /** TFC's table of {@code plant/<name>} as ours of the same path (the ids are the block ids). */
    public static void loot(TfcCheckout tfc, OutputTree out, String name) {
        String src = LOOT + name + ".json";
        JsonElement table = Json.rename(Json.parse(tfc.readString(src), src));
        LootTableExport.delegateBlock(out, "plant/" + name, table, n -> { });
    }

    /** Copies the item model of {@code plant/<name>}: a generated item (its texture too) or a block model parent. */
    public static void itemModel(TfcCheckout tfc, OutputTree out, String name, Set<String> models) {
        String src = MODELS_ITEM + name + ".json";
        JsonObject m = Json.parse(tfc.readString(src), src).getAsJsonObject();
        String parent = m.get("parent").getAsString();
        if (parent.equals("item/generated")) {
            String ref = m.getAsJsonObject("textures").get("layer0").getAsString();
            String tex = ref.substring(Json.FROM_NS.length() + 1);
            String png = TfcCheckout.ASSETS + "textures/" + tex + ".png";
            if (!ref.startsWith(Json.FROM_NS + ":") || !tfc.exists(png)) {
                throw new DatagenException(src + " uses missing texture " + ref);
            }
            out.put(RP + "textures/" + tex + ".png", tfc.read(png));
        } else {
            models.add(parent);
        }
        out.put(RP + "models/item/plant/" + name + ".json", Json.write(Json.rename(m)));
    }

    /** The block item names {@code item.tfp.fruit_tree.<name>}, from TFC's {@code block.tfc.plant.<name>}, in every locale that has them. */
    public static void lang(TfcCheckout tfc, OutputTree out, List<String> names) {
        lang(tfc, out, names, LANG_RP);
    }

    /** As above, into the lang folder {@code langRp} (one resource pack namespace per generator). */
    public static void lang(TfcCheckout tfc, OutputTree out, List<String> names, String langRp) {
        for (String f : tfc.list(LANG)) {
            if (!f.endsWith(".json")) {
                continue;
            }
            String locale = f.substring(LANG.length(), f.length() - ".json".length());
            JsonObject src = Json.parse(tfc.readString(f), f).getAsJsonObject();
            TreeMap<String, String> keys = new TreeMap<>();
            for (String n : names) {
                String tfcKey = "block." + Json.FROM_NS + ".plant." + n;
                if (src.has(tfcKey)) {
                    keys.put(langKey(n), src.get(tfcKey).getAsString());
                } else if (locale.equals("en_us")) {
                    throw new DatagenException("TFC has no English name " + tfcKey);
                }
            }
            if (!keys.isEmpty()) {
                JsonObject o = new JsonObject();
                keys.forEach(o::addProperty);
                out.put(langRp + locale + ".json", Json.write(o));
            }
        }
    }

    public static String langKey(String name) {
        return "item." + Json.TO_NS + ".fruit_tree." + name;
    }

    private static List<DeviceProperty> flags(boolean down) {
        List<DeviceProperty> p = new ArrayList<>();
        for (String f : TfcShapes.FRUIT_FLAGS) {
            p.add(DeviceProperty.bool(f, f.equals("down") && down));
        }
        return p;
    }

    private static Function<Map<String, String>, Look> pipeLook() {
        return s -> {
            int bits = 0;
            for (int i = 0; i < 6; i++) {
                if (s.get(TfcShapes.FRUIT_FLAGS.get(i)).equals("true")) {
                    bits |= 1 << i;
                }
            }
            String n = TfcShapes.fruitBranchAppearance(bits);
            return new Look(n, n);
        };
    }

    /** The four blocks of one tree. */
    static List<Built> tree(TfcCheckout tfc, String t) {
        List<Built> out = new ArrayList<>();
        List<DeviceProperty> sap = List.of(DeviceProperty.range("saplings", 1, 4, 1));
        out.add(block(tfc, HitboxDemands.fruitId(t, "sapling"), t + "_sapling", sap,
            s -> new Look("s_" + s.get("saplings"), HitboxDemands.FRUIT_SAPLING_PATTERN), settings("0", "hoe"),
            langKey(t + "_sapling")));
        List<DeviceProperty> growing = new ArrayList<>(List.of(DeviceProperty.range("stage", 0, 3, 0),
            DeviceProperty.range("saplings", 0, 4, 0), DeviceProperty.bool("natural", false)));
        growing.addAll(flags(true));
        out.add(block(tfc, HitboxDemands.fruitId(t, "growing_branch"), t + "_growing_branch", growing, pipeLook(),
            settings("1.0", "axe"), null));
        List<DeviceProperty> branch = new ArrayList<>(List.of(DeviceProperty.range("stage", 0, 3, 0)));
        branch.addAll(flags(false));
        out.add(block(tfc, HitboxDemands.fruitId(t, "branch"), t + "_branch", branch, pipeLook(), settings("1.0", "axe"), null));
        List<DeviceProperty> leaves = List.of(DeviceProperty.names("lifecycle", LIFECYCLES, "healthy"),
            DeviceProperty.bool("persistent", false));
        out.add(block(tfc, HitboxDemands.fruitId(t, "leaves"), t + "_leaves", leaves,
            s -> new Look(s.get("lifecycle"), HitboxDemands.FRUIT_LEAVES_PATTERN), settings("0.5", "hoe"),
            langKey(t + "_leaves")));
        return out;
    }

    /** Writes the entries, models, textures, loot and names. Returns the number of server states. */
    public static int run(TfcCheckout tfc, OutputTree out) {
        List<String> trees = trees(tfc);
        if (!trees.equals(HitboxDemands.FRUIT_TREES)) {
            throw new DatagenException(FRUIT_BLOCKS + " lists " + trees + " but HitboxDemands.FRUIT_TREES is " + HitboxDemands.FRUIT_TREES);
        }
        checkSources(tfc);
        int states = 0;
        Set<String> models = new TreeSet<>();
        List<String> named = new ArrayList<>();
        for (String t : trees) {
            List<Built> built = tree(tfc, t);
            List<Map<String, Object>> roots = new ArrayList<>();
            roots.add(yaml(built.get(0).spec(), GRASS, LEAVES_FIRE, "hoe", false));
            roots.add(yaml(built.get(1).spec(), SCAFFOLDING, LOGS_FIRE, "axe", false));
            roots.add(yaml(built.get(2).spec(), SCAFFOLDING, LOGS_FIRE, "axe", false));
            roots.add(yaml(built.get(3).spec(), GRASS, LEAVES_FIRE, "hoe", true));
            built.forEach(b -> models.addAll(b.models()));
            states += write(out, OUTPUT_DIR + t + ".yml", roots, built);
            for (String part : List.of("sapling", "growing_branch", "branch", "leaves")) {
                loot(tfc, out, t + "_" + part);
            }
            itemModel(tfc, out, t + "_sapling", models);
            itemModel(tfc, out, t + "_leaves", models);
            named.add(t + "_sapling");
            named.add(t + "_leaves");
        }
        Set<String> copied = new TreeSet<>();
        for (String m : models) {
            CropBlocks.copyModel(tfc, out, m, copied);
        }
        lang(tfc, out, named);
        return states;
    }

    /** TFC's registrations the generator depends on. */
    static void checkSources(TfcCheckout tfc) {
        String fruit = tfc.readString(FRUIT_BLOCKS).replaceAll("\\s+", " ");
        for (String needle : List.of(
            "return new FruitTreeBranchBlock(ExtendedProperties.of(MapColor.WOOD).sound(SoundType.SCAFFOLDING).randomTicks().strength(1.0f)"
                + ".pushReaction(PushReaction.DESTROY).flammableLikeLogs(),",
            "return new GrowingFruitTreeBranchBlock(ExtendedProperties.of(MapColor.WOOD).sound(SoundType.SCAFFOLDING).randomTicks().strength(1.0f)"
                + ".pushReaction(PushReaction.DESTROY).blockEntity(TFCBlockEntities.TICK_COUNTING_PLANT).flammableLikeLogs().cloneEmpty(),",
            "new FruitTreeLeavesBlock(ExtendedProperties.of().mapColor(FruitTreeLeavesBlock::getMapColor).strength(0.5F).sound(SoundType.GRASS)"
                + ".randomTicks().noOcclusion().blockEntity(TFCBlockEntities.BERRY_BUSH).flammableLikeLeaves(),",
            "ExtendedProperties.of(MapColor.PLANT) .noCollission() .randomTicks() .strength(0) .sound(SoundType.GRASS)")) {
            if (!fruit.contains(needle.replaceAll("\\s+", " "))) {
                throw new DatagenException(FRUIT_BLOCKS + " no longer contains " + needle);
            }
        }
    }
}
