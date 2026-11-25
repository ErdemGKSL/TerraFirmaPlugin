/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/plant/Plant.java @ c3b4d9536 (the enum, block types, age rule, tint sets),
 *       src/main/java/net/dries007/tfc/common/blocks/plant/PlantBlock.java @ c3b4d9536 (AGE),
 *       src/main/java/net/dries007/tfc/common/blocks/plant/TFCTallGrassBlock.java @ c3b4d9536 (the two halves),
 *       src/main/java/net/dries007/tfc/client/TFCColors.java @ c3b4d9536 (the default climate colour: 10 C, 250 mm),
 *       src/main/resources/assets/tfc/{blockstates,models,textures}/.../plant/** and textures/colormap/*.png @ c3b4d9536 (copied),
 *       src/main/resources/data/tfc/loot_table/blocks/plant/*.json @ c3b4d9536 (delegated to the loot runtime),
 *       src/main/resources/assets/tfc/lang/*.json @ c3b4d9536 (block.tfc.plant.*)
 */
package dev.tfp.datagen.plant;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.tfp.datagen.CeBlockStates;
import dev.tfp.datagen.CeBlockStates.ModelRef;
import dev.tfp.datagen.DatagenException;
import dev.tfp.datagen.Json;
import dev.tfp.datagen.LootTableExport;
import dev.tfp.datagen.OutputTree;
import dev.tfp.datagen.TfcCheckout;
import dev.tfp.datagen.Yaml;
import dev.tfp.datagen.device.DeviceYaml;
import dev.tfp.datagen.hitbox.PlantPools;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;

/**
 * PAR-WORLD-12: TFC's plants of the block types worldgen places on land and on the water surface as CraftEngine blocks
 * ({@code craftengine/tfp/configuration/plant/<name>.yml}, {@code tfp:plant/<name>}), with TFC's properties: {@code age}
 * 0..3 where {@code Plant.getMaxAgeForType} gives 3, and the two halves of the tall types (TFC {@code part}, CraftEngine's
 * {@code double_high_block} {@code half}).
 *
 * <p><b>Look.</b> Each (age, half) draws TFC's blockstate models at the plant's first lifecycle stage (TFC's
 * {@code tfc:plant} model loader picks the stage from the client calendar and climate; the seasonal stages are a
 * follow-up). Ages 0 and 1 share the age-1 look, so the plants fit the plant pools: every distinct look takes one state of
 * CraftEngine's {@code sugar_cane} (15) or {@code sapling} (8) pool when untinted, else of the {@code tripwire} pool (126);
 * an identical look is one shared state ({@code auto_state} id). Tinted faces (TFC's block colour handlers) have their
 * texture baked with TFC's default climate colour ({@code TFCColors.getClimateColor(map, 10, 250)}) because none of the
 * three pools has TFC's colour handler. All are no-collision carriers, as TFC's plants (the cacti too).
 */
public final class PlantBlockGen {
    public static final String PLANT_JAVA = "src/main/java/net/dries007/tfc/common/blocks/plant/Plant.java";
    static final String ASSETS = TfcCheckout.ASSETS;
    public static final String OUTPUT_DIR = "craftengine/tfp/configuration/plant/";
    static final String RP = "craftengine/tfp/resourcepack/assets/tfp/";
    public static final String LANG_RP = "craftengine/tfp/resourcepack/assets/tfp_plant/lang/";
    static final String LOOT_SOURCE = TfcCheckout.MAIN + "data/" + Json.FROM_NS + "/loot_table/blocks/";
    /** The block types generated here: the ones worldgen places on land and floating on water. */
    public static final Set<String> TYPES = Set.of("STANDARD", "SHORT_SHRUB", "SHRUB", "FLOWERBED", "CACTUSBED", "PASSABLE_CACTUS",
        "DRY", "SHORT_GRASS", "BEACH_GRASS", "TALL_GRASS", "TALL_SHRUB", "FLOATING", "FLOATING_FRESH");
    /** The tall types (TFC {@code part}: TFCTallGrassBlock, TFCTallShrubBlock, TFCPassableCactusBlock). */
    public static final Set<String> TALL = Set.of("TALL_GRASS", "TALL_SHRUB", "PASSABLE_CACTUS");
    static final Set<String> FLOATING = Set.of("FLOATING", "FLOATING_FRESH");
    /** The cacti: TFC {@code strength(0.25F).sound(SoundType.WOOL)}; CACTUSBED is not replaceable ({@code solid()}). */
    static final Set<String> CACTI = Set.of("CACTUSBED", "PASSABLE_CACTUS");
    /** CraftEngine's land plant pools and their sizes (PAR-WORLD-59: modelled by the selector, {@code hitbox/PlantPools}). */
    public static final Map<String, Integer> POOLS = PlantPools.LAND;
    /** TFC's default climate for colours without a position: 10 C, 250 mm ({@code TFCColors}). */
    static final int TINT_X = 255 - Math.clamp((int) ((10f + 20f) * 255f / 50f), 0, 255);
    static final int TINT_Y = 255 - Math.clamp((int) (250f * 255f / 500f), 0, 255);
    static final String EMPTY_MODEL = "tfp:block/plant_empty";
    private static final Pattern ENTRY = Pattern.compile("^ {4}([A-Z_]+)\\(BlockType\\.([A-Z_]+)", Pattern.MULTILINE);
    private static final Pattern PLANT_SET = Pattern.compile("EnumSet<Plant> (\\w+) = EnumSet\\.of\\(([^)]*)\\)");
    private static final Pattern TYPE_SET = Pattern.compile("EnumSet<BlockType> FOLIAGE_TYPES = EnumSet\\.of\\(([^)]*)\\)");

    private PlantBlockGen() {
    }


    private static Map<String, Integer> landUse(PlantPools.Allocator allocator) {
        Map<String, Integer> m = new LinkedHashMap<>(allocator.use());
        m.keySet().retainAll(POOLS.keySet());
        return java.util.Collections.unmodifiableMap(m);
    }

    /** One TFC plant: its name ({@code allium}), block type and colour map ({@code grass}, {@code tall_grass}, {@code foliage}, or null). */
    public record Plant(String name, String type, boolean hasAge, String tint, boolean itemTinted) {
        public String id() {
            return Json.TO_NS + ":plant/" + name;
        }

        public boolean tall() {
            return TALL.contains(type);
        }
    }

    /**
     * What {@link #run} wrote: the plants, their state count, the pool use per pool, the pool states taken (blooming and
     * dormant looks) and the PAR-WORLD-61 dormant table (base CE state, e.g. {@code tfp:plant/allium}, to its dormant look).
     */
    public record Result(List<Plant> plants, int states, Map<String, Integer> poolUse, int looks, Map<String, Dormant> dormant) {
    }

    /** PAR-WORLD-61: the dormant look of one base state: the plant (its bloom fields) and the look's CE state. */
    public record Dormant(String plant, String state) {
    }

    /** PAR-WORLD-61: the block that only holds the dormant looks; never placed, only sent by the packet rewriter. */
    public static final String DORMANT_BLOCK = Json.TO_NS + ":plant/dormant_look";
    /** The TFC tags that name the flowers, which get the spare pool states first. */
    static final List<String> FLOWER_TAGS = List.of(TfcCheckout.GENERATED + "data/minecraft/tags/block/flowers.json",
        TfcCheckout.GENERATED + "data/minecraft/tags/block/small_flowers.json",
        TfcCheckout.GENERATED + "data/minecraft/tags/block/tall_flowers.json");

    public static boolean available(TfcCheckout tfc) {
        return tfc.exists(PLANT_JAVA) && tfc.exists(ASSETS + "blockstates/plant/allium.json");
    }

    /** The plants of {@link #TYPES} in TFC's enum order, with their age rule and colour map read from Plant.java. */
    public static List<Plant> plants(TfcCheckout tfc) {
        return plants(tfc, TYPES);
    }

    /** The plants of {@code types} (PAR-WORLD-47 reads the other block types through this). */
    public static List<Plant> plants(TfcCheckout tfc, Set<String> types) {
        String src = tfc.readString(PLANT_JAVA);
        Map<String, Set<String>> sets = new LinkedHashMap<>();
        Matcher s = PLANT_SET.matcher(src);
        while (s.find()) {
            sets.put(s.group(1), names(s.group(2)));
        }
        Matcher ft = TYPE_SET.matcher(src);
        if (!ft.find()) {
            throw new DatagenException(PLANT_JAVA + ": FOLIAGE_TYPES not found");
        }
        Set<String> foliageTypes = names(ft.group(1));
        int a = src.indexOf("private int getMaxAgeForType(BlockType type)");
        int b = src.indexOf("return 3;", a);
        if (a < 0 || b < 0) {
            throw new DatagenException(PLANT_JAVA + ": getMaxAgeForType not found");
        }
        Set<String> aged = new HashSet<>();
        Matcher at = Pattern.compile("type == BlockType\\.([A-Z_]+)").matcher(src.substring(a, b));
        while (at.find()) {
            aged.add(at.group(1));
        }
        for (String need : List.of("BLOCK_TINTED_PLANTS", "ITEM_TINTED_PLANTS", "FOLIAGE_COLOR_PLANTS", "WATER_COLOR_PLANTS")) {
            if (!sets.containsKey(need)) {
                throw new DatagenException(PLANT_JAVA + ": " + need + " not found");
            }
        }
        List<Plant> out = new ArrayList<>();
        Matcher m = ENTRY.matcher(src);
        while (m.find()) {
            String constant = m.group(1);
            String type = m.group(2);
            if (!types.contains(type)) {
                continue;
            }
            String tint = null;
            if (sets.get("BLOCK_TINTED_PLANTS").contains(constant)) {
                // ClientEventHandler: water, else tall grass, else (vines) seasonal, else foliage, else grass; the seasonal
                // foliage colour of the vines (PAR-WORLD-47) is drawn with the foliage map until PAR-WORLD-48
                boolean foliageColor = sets.get("FOLIAGE_COLOR_PLANTS").contains(constant);
                tint = sets.get("WATER_COLOR_PLANTS").contains(constant) ? "water"
                    : (type.equals("TALL_GRASS") || type.equals("SHORT_GRASS")) && !foliageColor ? "tall_grass"
                    : foliageTypes.contains(type) || foliageColor || type.equals("VINE") ? "foliage" : "grass";
            }
            out.add(new Plant(constant.toLowerCase(java.util.Locale.ROOT), type, aged.contains(type), tint,
                sets.get("ITEM_TINTED_PLANTS").contains(constant)));
        }
        if (out.isEmpty()) {
            throw new DatagenException(PLANT_JAVA + ": no plant entries found");
        }
        return out;
    }

    private static Set<String> names(String list) {
        Set<String> s = new LinkedHashSet<>();
        for (String p : list.split(",")) {
            if (!p.isBlank()) {
                s.add(p.trim().replace("BlockType.", ""));
            }
        }
        return s;
    }

    /** The (age, half) states of a plant, as CraftEngine variant keys ({@code ""} for a single-state plant). */
    static List<String> stateKeys(Plant p) {
        List<String> keys = new ArrayList<>();
        List<String> ages = p.hasAge() ? List.of("0", "1", "2", "3") : java.util.Collections.singletonList(null);
        List<String> halves = p.tall() ? List.of("lower", "upper") : java.util.Collections.singletonList(null);
        for (String age : ages) {
            for (String half : halves) {
                List<String> parts = new ArrayList<>();
                if (age != null) {
                    parts.add("age=" + age);
                }
                if (half != null) {
                    parts.add("half=" + half);
                }
                keys.add(String.join(",", parts));
            }
        }
        return keys;
    }

    /** Writes every plant block, its item, models, textures, lang and loot. */
    public static Result run(TfcCheckout tfc, OutputTree out) {
        List<Plant> plants = plants(tfc);
        Assets assets = new Assets(tfc, out);
        Map<String, String> lookIds = new LinkedHashMap<>(); // look key -> auto_state id
        Map<String, String> lookPool = new LinkedHashMap<>();
        PlantPools.Allocator allocator = new PlantPools.Allocator();
        int states = 0;
        // first pass: the looks of every plant, so the pools are assigned in one deterministic order
        Map<Plant, Map<String, List<ModelRef>>> looks = new LinkedHashMap<>();
        Map<Plant, Map<String, List<ModelRef>>> raw = new LinkedHashMap<>();
        for (Plant p : plants) {
            Map<String, List<ModelRef>> byKey = new LinkedHashMap<>();
            Map<String, List<ModelRef>> rawByKey = new LinkedHashMap<>();
            Blockstate bs = Blockstate.read(tfc, p);
            for (String key : stateKeys(p)) {
                String lookKey = key.replace("age=0", "age=1"); // ages 0 and 1 share the age-1 look
                List<ModelRef> group = bs.models(lookKey, p);
                byKey.put(key, resolve(assets, group, p, null));
                rawByKey.put(key, group);
                states++;
            }
            looks.put(p, byKey);
            raw.put(p, rawByKey);
        }
        for (boolean tinted : List.of(false, true)) {
            for (Map.Entry<Plant, Map<String, List<ModelRef>>> e : looks.entrySet()) {
                if ((e.getKey().tint() != null) != tinted) {
                    continue;
                }
                for (List<ModelRef> look : e.getValue().values()) {
                    String k = lookKey(look);
                    if (lookIds.containsKey(k)) {
                        continue;
                    }
                    String pool = allocator.land(tinted).orElse(null);
                    if (pool == null) {
                        throw new DatagenException("plant looks overflow the plant pools " + POOLS + " at " + e.getKey().id()
                            + " (" + lookIds.size() + " looks placed): merge more looks or move some to entity renderers");
                    }
                    lookIds.put(k, "tfp_plant_" + lookIds.size());
                    lookPool.put(k, pool);
                }
            }
        }
        Map<String, Dormant> dormant = new LinkedHashMap<>();
        int taken = dormantLooks(tfc, out, assets, plants, looks, raw, lookIds, lookPool, allocator, dormant);
        assets.emptyModel();
        for (Map.Entry<Plant, Map<String, List<ModelRef>>> e : looks.entrySet()) {
            Plant p = e.getKey();
            Map<String, Object> blocks = new LinkedHashMap<>();
            blocks.put(p.id(), block(p, e.getValue(), lookIds, lookPool));
            Map<String, Object> items = new LinkedHashMap<>();
            items.put(p.id(), item(p, assets));
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("blocks", blocks);
            root.put("items", items);
            out.put(OUTPUT_DIR + p.name() + ".yml", Yaml.write(DeviceYaml.HEADER, root));
            String loot = LOOT_SOURCE + "plant/" + p.name() + ".json";
            if (tfc.exists(loot)) {
                LootTableExport.delegateBlock(out, "plant/" + p.name(), Json.rename(Json.parse(tfc.readString(loot), loot)), n -> { });
            }
        }
        writeLang(tfc, out, plants);
        JsonObject table = new JsonObject();
        dormant.forEach((base, d) -> {
            JsonObject o = new JsonObject();
            o.addProperty("plant", d.plant());
            o.addProperty("state", d.state());
            table.add(base, o);
        });
        dev.tfp.datagen.plant.stage.PlantStageGen.mergeDormantStates(out, table);
        return new Result(List.copyOf(plants), states, landUse(allocator), lookIds.size() + taken,
            java.util.Collections.unmodifiableMap(dormant));
    }

    private static List<ModelRef> resolve(Assets assets, List<ModelRef> group, Plant p, String stageKey) {
        List<ModelRef> resolved = new ArrayList<>();
        for (ModelRef r : group) {
            String model = r.path().equals(EMPTY_MODEL) ? EMPTY_MODEL : assets.look(r.path(), p.tint(), stageKey);
            resolved.add(new ModelRef(model, r.x(), r.y(), r.uvlock(), r.weight()));
        }
        return resolved;
    }

    /** The CE state string of a plant state key: {@code tfp:plant/rose[age=1,half=lower]}, or the bare id. */
    static String stateString(String id, String key) {
        return key.isEmpty() ? id : id + "[" + key + "]";
    }

    /** The plants TFC tags as flowers ({@code minecraft:flowers}, {@code small_flowers}, {@code tall_flowers}). */
    static Set<String> flowers(TfcCheckout tfc) {
        Set<String> names = new LinkedHashSet<>();
        for (String tag : FLOWER_TAGS) {
            if (!tfc.exists(tag)) {
                continue;
            }
            for (JsonElement v : Json.parse(tfc.readString(tag), tag).getAsJsonObject().getAsJsonArray("values")) {
                String id = v.isJsonObject() ? v.getAsJsonObject().get("id").getAsString() : v.getAsString();
                if (id.startsWith(Json.FROM_NS + ":plant/")) {
                    names.add(id.substring((Json.FROM_NS + ":plant/").length()));
                }
            }
        }
        return names;
    }

    /**
     * PAR-WORLD-61: gives plants a dormant look on the plant pool states the blooming looks left over. A state's dormant
     * look draws TFC's {@code dormant} stage of each {@code tfc:plant} loader model (the tint baked as for the blooming
     * look); a state whose dormant look equals its blooming look needs none. Plants are served whole (every state of the
     * plant or none) in a fixed order: the flowers first, then the rest, each in TFC's enum order; a plant that does not
     * fit is skipped and the next one tried. A dormant look equal to an existing look reuses its pool state. The looks go
     * on {@link #DORMANT_BLOCK} ({@code look} 0..n-1), the table into {@code dormant}.
     *
     * @return the pool states taken
     */
    private static int dormantLooks(TfcCheckout tfc, OutputTree out, Assets assets, List<Plant> plants,
            Map<Plant, Map<String, List<ModelRef>>> looks, Map<Plant, Map<String, List<ModelRef>>> raw,
            Map<String, String> lookIds, Map<String, String> lookPool, PlantPools.Allocator allocator,
            Map<String, Dormant> dormant) {
        Set<String> flowers = flowers(tfc);
        List<Plant> order = new ArrayList<>();
        plants.stream().filter(p -> flowers.contains(p.name())).forEach(order::add);
        plants.stream().filter(p -> !flowers.contains(p.name())).forEach(order::add);
        Map<String, List<ModelRef>> targets = new LinkedHashMap<>(); // dormant look key -> look, in look-index order
        List<String> targetOrder = new ArrayList<>();
        Map<String, String> ids = new LinkedHashMap<>();
        Map<String, String> pools = new LinkedHashMap<>();
        Map<String, Integer> index = new LinkedHashMap<>();
        int taken = 0;
        for (Plant p : order) {
            Map<String, List<ModelRef>> wanted = new LinkedHashMap<>();
            for (Map.Entry<String, List<ModelRef>> e : raw.get(p).entrySet()) {
                if (e.getValue().stream().noneMatch(r -> r.path().startsWith(Json.FROM_NS + ":") && assets.staged(r.path()))) {
                    continue;
                }
                List<ModelRef> d = resolve(assets, e.getValue(), p, "dormant");
                if (!lookKey(d).equals(lookKey(looks.get(p).get(e.getKey())))) {
                    wanted.put(e.getKey(), d);
                }
            }
            if (wanted.isEmpty()) {
                continue;
            }
            Set<String> fresh = new LinkedHashSet<>();
            for (List<ModelRef> d : wanted.values()) {
                String k = lookKey(d);
                if (!lookIds.containsKey(k) && !ids.containsKey(k)) {
                    fresh.add(k);
                }
            }
            if (allocator.remaining(p.tint() != null) < fresh.size()) {
                continue;
            }
            for (String k : fresh) {
                pools.put(k, allocator.land(p.tint() != null).orElseThrow());
                ids.put(k, "tfp_plant_" + (lookIds.size() + taken));
                taken++;
            }
            for (Map.Entry<String, List<ModelRef>> e : wanted.entrySet()) {
                String k = lookKey(e.getValue());
                if (targets.putIfAbsent(k, e.getValue()) == null) {
                    index.put(k, targetOrder.size());
                    targetOrder.add(k);
                }
                dormant.put(stateString(p.id(), e.getKey()), new Dormant(p.name(), String.valueOf(index.get(k))));
            }
        }
        if (targets.isEmpty()) {
            return taken;
        }
        int n = targets.size();
        dormant.replaceAll((base, d) -> new Dormant(d.plant(), n == 1 ? DORMANT_BLOCK : DORMANT_BLOCK + "[look=" + d.state() + "]"));
        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("hardness", new java.math.BigDecimal("0.0"));
        settings.put("resistance", new java.math.BigDecimal("0.0"));
        settings.put("sounds", sounds("grass"));
        settings.put("replaceable", true);
        settings.put("push_reaction", "destroy");
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("settings", settings);
        java.util.function.Function<String, Map<String, Object>> visual = k -> {
            String id = lookIds.containsKey(k) ? lookIds.get(k) : ids.get(k);
            String pool = lookIds.containsKey(k) ? lookPool.get(k) : pools.get(k);
            return CeBlockStates.appearance(PlantPools.appearance(pool, id), targets.get(k));
        };
        if (n == 1) {
            b.put("state", visual.apply(targetOrder.get(0)));
        } else {
            Map<String, Object> prop = new LinkedHashMap<>();
            prop.put("type", "int");
            prop.put("range", "0~" + (n - 1));
            prop.put("default", 0);
            Map<String, Object> appearances = new LinkedHashMap<>();
            Map<String, Object> variants = new LinkedHashMap<>();
            for (int i = 0; i < n; i++) {
                appearances.put("look_" + i, visual.apply(targetOrder.get(i)));
                variants.put("look=" + i, Map.of("appearance", "look_" + i));
            }
            Map<String, Object> st = new LinkedHashMap<>();
            st.put("properties", Map.of("look", prop));
            st.put("appearances", appearances);
            st.put("variants", variants);
            b.put("states", st);
        }
        Map<String, Object> blocks = new LinkedHashMap<>();
        blocks.put(DORMANT_BLOCK, b);
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("blocks", blocks);
        out.put(OUTPUT_DIR + DORMANT_BLOCK.substring(DORMANT_BLOCK.lastIndexOf('/') + 1) + ".yml", Yaml.write(DeviceYaml.HEADER, root));
        return taken;
    }

    static String lookKey(List<ModelRef> look) {
        StringBuilder sb = new StringBuilder();
        for (ModelRef r : look) {
            sb.append(r.path()).append('/').append(r.x()).append('/').append(r.y()).append('/').append(r.uvlock())
                .append('/').append(r.weight()).append(';');
        }
        return sb.toString();
    }

    private static Map<String, Object> block(Plant p, Map<String, List<ModelRef>> byKey, Map<String, String> ids,
            Map<String, String> pools) {
        Map<String, Object> settings = new LinkedHashMap<>();
        boolean cactus = CACTI.contains(p.type());
        settings.put("hardness", new java.math.BigDecimal(cactus ? "0.25" : "0.0"));
        settings.put("resistance", new java.math.BigDecimal(cactus ? "0.25" : "0.0"));
        settings.put("sounds", sounds(cactus ? "wool" : "grass"));
        settings.put("replaceable", !p.type().equals("CACTUSBED"));
        settings.put("push_reaction", "destroy");
        settings.put("item", p.id());
        List<Object> behaviour = new ArrayList<>();
        if (FLOATING.contains(p.type())) {
            behaviour.add(Map.of("type", "on_liquid_block"));
        } else {
            Map<String, Object> bush = new LinkedHashMap<>();
            bush.put("type", "bush_block");
            bush.put("blacklist", true);
            bush.put("bottom_blocks", List.of("minecraft:air", "minecraft:cave_air", "minecraft:void_air", "minecraft:water",
                "minecraft:lava"));
            if (p.tall()) {
                bush.put("stackable", true);
                bush.put("max_height", 2);
            }
            behaviour.add(bush);
            if (p.tall()) {
                behaviour.add(Map.of("type", "double_high_block"));
            }
            behaviour.add(Map.of("type", "liquid_flowable_block"));
        }
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("settings", settings);
        b.put("behavior", behaviour);
        if (byKey.size() == 1 && byKey.containsKey("")) {
            b.put("state", visual(byKey.get(""), ids, pools));
            return b;
        }
        Map<String, Object> props = new LinkedHashMap<>();
        if (p.hasAge()) {
            Map<String, Object> age = new LinkedHashMap<>();
            age.put("type", "int");
            age.put("range", "0~3");
            age.put("default", 0);
            props.put("age", age);
        }
        if (p.tall()) {
            Map<String, Object> half = new LinkedHashMap<>();
            half.put("type", "double_block_half");
            half.put("values", List.of("lower", "upper")); // both: the state counters (Report.countCeStates) read values
            half.put("default", "lower");
            props.put("half", half);
        }
        Map<String, Object> appearances = new LinkedHashMap<>();
        Map<String, Object> variants = new LinkedHashMap<>();
        for (Map.Entry<String, List<ModelRef>> e : byKey.entrySet()) {
            String name = ids.get(lookKey(e.getValue())).substring("tfp_".length());
            appearances.putIfAbsent(name, visual(e.getValue(), ids, pools));
            variants.put(e.getKey(), Map.of("appearance", name));
        }
        Map<String, Object> st = new LinkedHashMap<>();
        st.put("properties", props);
        st.put("appearances", appearances);
        st.put("variants", variants);
        b.put("states", st);
        return b;
    }

    private static Map<String, Object> visual(List<ModelRef> look, Map<String, String> ids, Map<String, String> pools) {
        String k = lookKey(look);
        return CeBlockStates.appearance(PlantPools.appearance(pools.get(k), ids.get(k)), look);
    }

    private static Map<String, Object> item(Plant p, Assets assets) {
        String model = assets.itemModel(p);
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("material", "paper");
        item.put("data", Map.of("item_name", "<!i><lang:block." + Json.TO_NS + ".plant." + p.name() + ">"));
        item.put("model", model);
        Map<String, Object> beh = new LinkedHashMap<>();
        beh.put("type", FLOATING.contains(p.type()) ? "liquid_collision_block_item" : p.tall() ? "double_high_block_item" : "block_item");
        beh.put("block", p.id());
        item.put("behavior", beh);
        return item;
    }

    static Map<String, String> sounds(String group) {
        Map<String, String> m = new LinkedHashMap<>();
        for (String e : List.of("break", "step", "place", "hit", "fall")) {
            m.put(e, "minecraft:block." + group + "." + e);
        }
        return m;
    }

    private static void writeLang(TfcCheckout tfc, OutputTree out, List<Plant> plants) {
        String dir = ASSETS + "lang/";
        for (String f : tfc.list(dir)) {
            if (!f.endsWith(".json")) {
                continue;
            }
            JsonObject src = Json.parse(tfc.readString(f), f).getAsJsonObject();
            TreeMap<String, String> keys = new TreeMap<>();
            for (Plant p : plants) {
                JsonElement v = src.get("block." + Json.FROM_NS + ".plant." + p.name());
                if (v != null) {
                    keys.put("block." + Json.TO_NS + ".plant." + p.name(), v.getAsString());
                }
            }
            JsonObject o = new JsonObject();
            keys.forEach(o::addProperty);
            out.put(LANG_RP + f.substring(dir.length()), Json.write(o));
        }
    }

    /** A plant's TFC blockstate: the model group drawn in each state (the last non-empty multipart group). */
    record Blockstate(String source, JsonObject json) {
        static Blockstate read(TfcCheckout tfc, Plant p) {
            String src = ASSETS + "blockstates/plant/" + p.name() + ".json";
            if (!tfc.exists(src)) {
                throw new DatagenException("TFC has no blockstate for plant " + p.name());
            }
            return new Blockstate(src, Json.parse(tfc.readString(src), src).getAsJsonObject());
        }

        /** The models of state {@code key} (our keys: {@code half} is TFC's {@code part}); nothing drawn (an empty model, no multipart match) is {@link #EMPTY_MODEL}. */
        List<ModelRef> models(String key, Plant p) {
            Map<String, String> state = new LinkedHashMap<>();
            if (!key.isEmpty()) {
                for (String kv : key.split(",")) {
                    String[] s = kv.split("=");
                    state.put(s[0].equals("half") ? "part" : s[0], s[1]);
                }
            }
            List<JsonElement> groups = new ArrayList<>();
            if (json.has("variants")) {
                for (Map.Entry<String, JsonElement> e : json.getAsJsonObject("variants").entrySet()) {
                    if (matches(e.getKey().isEmpty() ? Map.of() : parseKey(e.getKey()), state)) {
                        groups.add(e.getValue());
                    }
                }
            } else {
                for (JsonElement part : json.getAsJsonArray("multipart")) {
                    JsonObject o = part.getAsJsonObject();
                    Map<String, String> when = new LinkedHashMap<>();
                    if (o.has("when")) {
                        o.getAsJsonObject("when").entrySet().forEach(w -> when.put(w.getKey(), w.getValue().getAsString()));
                    }
                    if (matches(when, state)) {
                        groups.add(o.get("apply"));
                    }
                }
            }
            List<ModelRef> chosen = null;
            for (JsonElement g : groups) {
                List<ModelRef> refs = refs(g);
                if (refs.stream().anyMatch(r -> !isEmpty(r.path()))) {
                    chosen = refs; // the last non-empty group: the plant itself (TFC draws a base under canna)
                }
            }
            if (groups.isEmpty() && json.has("variants")) {
                throw new DatagenException(source + ": no model for state " + key + " of " + p.id());
            }
            return chosen == null ? List.of(new ModelRef(EMPTY_MODEL, 0, 0, false, 1)) : chosen;
        }

        private static boolean matches(Map<String, String> when, Map<String, String> state) {
            for (Map.Entry<String, String> w : when.entrySet()) {
                if (!state.containsKey(w.getKey()) || !List.of(w.getValue().split("\\|")).contains(state.get(w.getKey()))) {
                    return false;
                }
            }
            return true;
        }

        private static Map<String, String> parseKey(String k) {
            Map<String, String> m = new LinkedHashMap<>();
            for (String kv : k.split(",")) {
                String[] s = kv.split("=");
                m.put(s[0], s[1]);
            }
            return m;
        }

        private static List<ModelRef> refs(JsonElement g) {
            JsonArray arr = new JsonArray();
            if (g.isJsonArray()) {
                arr = g.getAsJsonArray();
            } else {
                arr.add(g);
            }
            List<ModelRef> out = new ArrayList<>();
            for (JsonElement e : arr) {
                JsonObject o = e.getAsJsonObject();
                out.add(new ModelRef(o.get("model").getAsString(), o.has("x") ? o.get("x").getAsInt() : 0,
                    o.has("y") ? o.get("y").getAsInt() : 0, o.has("uvlock") && o.get("uvlock").getAsBoolean(),
                    o.has("weight") ? o.get("weight").getAsInt() : 1));
            }
            return out;
        }

        static boolean isEmpty(String model) {
            return model.endsWith("/empty") || model.endsWith("_empty");
        }
    }

    /**
     * Copies TFC models and textures (renamed) and makes the looks: the first lifecycle stage of a {@code tfc:plant} model,
     * and for a tinted plant a child model whose tinted textures are baked with the plant's colour map.
     */
    public static final class Assets {
        private final TfcCheckout tfc;
        private final OutputTree out;
        private final Set<String> done = new HashSet<>();
        private final Map<String, int[]> colormaps = new LinkedHashMap<>();

        public Assets(TfcCheckout tfc, OutputTree out) {
            this.tfc = tfc;
            this.out = out;
        }

        /** Our model of TFC model {@code model} for a block look: the stage model resolved, the tint baked. */
        public String look(String model, String tint) {
            return look(model, tint, null);
        }

        /**
         * PAR-WORLD-61: our model of TFC model {@code model} at lifecycle stage {@code stageKey} of a {@code tfc:plant}
         * loader model (null: the first stage, the blooming look), the tint baked.
         */
        public String look(String model, String tint, String stageKey) {
            String stage = stage(model, stageKey);
            copyModel(stage);
            String ours = Json.renameId(stage);
            if (tint == null) {
                return ours;
            }
            Map<String, String> textures = new LinkedHashMap<>();
            Set<String> tintedVars = new LinkedHashSet<>();
            chain(stage, textures, tintedVars, tint.equals("grass") ? Set.of(0, 1) : Set.of(0));
            if (tintedVars.isEmpty()) {
                return ours;
            }
            JsonObject child = new JsonObject();
            child.addProperty("parent", ours);
            JsonObject tex = new JsonObject();
            for (String var : tintedVars) {
                String texture = resolve(textures, var);
                tex.addProperty(var, bake(texture, tint));
            }
            child.add("textures", tex);
            String path = "block/plant_tinted/" + tint + "/" + stage.substring(stage.indexOf(":block/") + ":block/".length());
            out.put(RP + "models/" + path + ".json", Json.write(child));
            return Json.TO_NS + ":" + path;
        }

        /** The item model of a plant ({@code tfp:item/plant/<name>}), its layer0 baked when TFC tints the item. */
        public String itemModel(Plant p) {
            String model = Json.FROM_NS + ":item/plant/" + p.name();
            if (!tfc.exists(ASSETS + "models/item/plant/" + p.name() + ".json")) {
                throw new DatagenException("TFC has no item model for plant " + p.name());
            }
            copyModel(model);
            if (p.itemTinted() && p.tint() != null) {
                JsonObject src = read(model);
                if (src.has("textures") && src.getAsJsonObject("textures").has("layer0")) {
                    JsonObject child = src.deepCopy();
                    child.getAsJsonObject("textures").addProperty("layer0",
                        bake(src.getAsJsonObject("textures").get("layer0").getAsString(), p.tint()));
                    out.put(RP + "models/item/plant_tinted/" + p.name() + ".json", Json.write(Json.rename(child)));
                    return Json.TO_NS + ":item/plant_tinted/" + p.name();
                }
            }
            return Json.TO_NS + ":item/plant/" + p.name();
        }

        public void emptyModel() {
            JsonObject empty = new JsonObject();
            empty.add("textures", new JsonObject());
            empty.add("elements", new JsonArray());
            out.put(RP + "models/block/plant_empty.json", Json.write(empty));
        }

        /** The model a {@code tfc:plant} loader model draws at its first stage, else the model itself. */
        String stage(String model) {
            return stage(model, null);
        }

        /** The model a {@code tfc:plant} loader model draws at stage {@code key} (null: its first stage), else the model itself. */
        String stage(String model, String key) {
            JsonObject o = read(model);
            if (!o.has("loader")) {
                return model;
            }
            if (!o.get("loader").getAsString().equals(Json.FROM_NS + ":plant")) {
                throw new DatagenException(model + ": unsupported model loader " + o.get("loader"));
            }
            for (Map.Entry<String, JsonElement> e : o.entrySet()) {
                if (!e.getKey().equals("loader") && !e.getKey().startsWith("__") && (key == null || key.equals(e.getKey()))) {
                    JsonObject stage = e.getValue().getAsJsonObject();
                    if (stage.size() != 1 || !stage.has("parent")) {
                        throw new DatagenException(model + ": stage " + e.getKey() + " is not a plain parent");
                    }
                    return stage(stage.get("parent").getAsString(), key);
                }
            }
            throw new DatagenException(model + ": no stage " + (key == null ? "" : key));
        }

        /** Whether TFC model {@code model} is a {@code tfc:plant} loader model. */
        boolean staged(String model) {
            return read(model).has("loader");
        }

        /** Collects the texture variables along the parent chain (child first) and the variables of tinted faces. */
        private void chain(String model, Map<String, String> textures, Set<String> tintedVars, Set<Integer> tintIndices) {
            String m = model;
            boolean elementsSeen = false;
            while (m != null) {
                if (!m.startsWith(Json.FROM_NS + ":")) {
                    String vanilla = m.contains(":") ? m.substring(m.indexOf(':') + 1) : m;
                    if (!elementsSeen && vanilla.equals("block/tinted_cross") && tintIndices.contains(0)) {
                        tintedVars.add("cross");
                    }
                    return;
                }
                JsonObject o = read(m);
                if (o.has("textures")) {
                    for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("textures").entrySet()) {
                        textures.putIfAbsent(e.getKey(), e.getValue().getAsString());
                    }
                }
                if (!elementsSeen && o.has("elements")) {
                    elementsSeen = true;
                    for (JsonElement el : o.getAsJsonArray("elements")) {
                        JsonObject faces = el.getAsJsonObject().getAsJsonObject("faces");
                        if (faces == null) {
                            continue;
                        }
                        for (Map.Entry<String, JsonElement> f : faces.entrySet()) {
                            JsonObject face = f.getValue().getAsJsonObject();
                            if (face.has("tintindex") && tintIndices.contains(face.get("tintindex").getAsInt())) {
                                tintedVars.add(face.get("texture").getAsString().substring(1));
                            }
                        }
                    }
                }
                m = o.has("parent") ? o.get("parent").getAsString() : null;
            }
        }

        private static String resolve(Map<String, String> textures, String var) {
            String v = textures.get(var);
            for (int i = 0; v != null && v.startsWith("#") && i < 16; i++) {
                v = textures.get(v.substring(1));
            }
            if (v == null || v.startsWith("#")) {
                throw new DatagenException("texture variable " + var + " does not resolve: " + textures);
            }
            return v;
        }

        /** Bakes TFC texture {@code texture} with the default colour of {@code map}; returns our texture id. */
        String bake(String texture, String map) {
            String tfcTex = texture.contains(":") ? texture : "minecraft:" + texture;
            String rel = tfcTex.substring(tfcTex.indexOf(':') + 1);
            String path = "block/plant_tinted/" + map + "/" + rel.substring(rel.indexOf('/') + 1);
            String target = RP + "textures/" + path + ".png";
            if (!out.contains(target)) {
                if (!tfcTex.startsWith(Json.FROM_NS + ":")) {
                    throw new DatagenException("tinted texture " + texture + " is not TFC's: it cannot be baked");
                }
                String src = ASSETS + "textures/" + rel + ".png";
                int colour = colour(map);
                out.put(target, tint(tfc.read(src), colour, src));
                if (tfc.exists(src + ".mcmeta")) {
                    out.put(target + ".mcmeta", tfc.read(src + ".mcmeta"));
                }
            }
            return Json.TO_NS + ":" + path;
        }

        /** {@code TFCColors.getClimateColor(map, 10, 250)}: the colour map pixel at (102, 128). */
        int colour(String map) {
            int[] c = colormaps.computeIfAbsent(map, k -> {
                String src = ASSETS + "textures/colormap/" + k + ".png";
                BufferedImage img = image(tfc.read(src), src);
                return new int[] {img.getRGB(TINT_X, TINT_Y)};
            });
            return c[0] & 0xFFFFFF;
        }

        static byte[] tint(byte[] png, int colour, String src) {
            BufferedImage img = image(png, src);
            BufferedImage outImg = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);
            int cr = colour >> 16 & 0xFF;
            int cg = colour >> 8 & 0xFF;
            int cb = colour & 0xFF;
            for (int y = 0; y < img.getHeight(); y++) {
                for (int x = 0; x < img.getWidth(); x++) {
                    int argb = img.getRGB(x, y);
                    int r = (argb >> 16 & 0xFF) * cr / 255;
                    int g = (argb >> 8 & 0xFF) * cg / 255;
                    int b = (argb & 0xFF) * cb / 255;
                    outImg.setRGB(x, y, argb & 0xFF000000 | r << 16 | g << 8 | b);
                }
            }
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try {
                ImageIO.write(outImg, "png", bytes);
            } catch (IOException e) {
                throw new DatagenException("cannot write the tinted copy of " + src + ": " + e);
            }
            return bytes.toByteArray();
        }

        private static BufferedImage image(byte[] png, String src) {
            try {
                BufferedImage img = ImageIO.read(new ByteArrayInputStream(png));
                if (img == null) {
                    throw new DatagenException(src + " is not a PNG");
                }
                return img;
            } catch (IOException e) {
                throw new DatagenException("cannot read " + src + ": " + e);
            }
        }

        private JsonObject read(String model) {
            if (!model.startsWith(Json.FROM_NS + ":")) {
                throw new DatagenException("not a TFC model: " + model);
            }
            String src = ASSETS + "models/" + model.substring(model.indexOf(':') + 1) + ".json";
            if (!tfc.exists(src)) {
                throw new DatagenException("TFC has no model " + model);
            }
            return Json.parse(tfc.readString(src), src).getAsJsonObject();
        }

        /** Copies TFC model {@code model}, its TFC parents and TFC textures, renamed. */
        void copyModel(String model) {
            if (!model.startsWith(Json.FROM_NS + ":") || !done.add(model)) {
                return;
            }
            JsonObject o = read(model);
            o.remove("__comment__");
            if (o.has("parent")) {
                copyModel(o.get("parent").getAsString());
            }
            if (o.has("textures")) {
                for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("textures").entrySet()) {
                    String t = e.getValue().getAsString();
                    if (t.startsWith(Json.FROM_NS + ":")) {
                        String rel = t.substring(t.indexOf(':') + 1);
                        String src = ASSETS + "textures/" + rel + ".png";
                        if (!tfc.exists(src)) {
                            throw new DatagenException(model + ": TFC has no texture " + t);
                        }
                        out.put(RP + "textures/" + rel + ".png", tfc.read(src));
                        if (tfc.exists(src + ".mcmeta")) {
                            out.put(RP + "textures/" + rel + ".png.mcmeta", tfc.read(src + ".mcmeta"));
                        }
                    }
                }
            }
            out.put(RP + "models/" + model.substring(model.indexOf(':') + 1) + ".json", Json.write(Json.rename(o)));
        }
    }
}
