/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/plant/Plant.java @ c3b4d9536 (the block types and their properties),
 *       src/main/java/net/dries007/tfc/common/blocks/plant/{WaterPlantBlock,TallWaterPlantBlock,TFCSeagrassBlock,
 *       CreepingWaterPlantBlock,RotatableWaterPlantBlock,TFCCactusBlock,TFCBambooSaplingBlock,TFCBambooStalkBlock}.java
 *       @ c3b4d9536 (sounds, strength, solidity),
 *       src/main/resources/assets/tfc/{blockstates,models,textures}/.../plant/** @ c3b4d9536 (copied, the looks per state),
 *       src/main/resources/data/tfc/loot_table/blocks/plant/*.json @ c3b4d9536 (delegated to the loot runtime),
 *       src/main/resources/assets/tfc/lang/*.json @ c3b4d9536 (block.tfc.plant.*)
 */
package dev.tfp.datagen.plant.shape;

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
import dev.tfp.datagen.hitbox.HitboxAllocation;
import dev.tfp.datagen.hitbox.HitboxDemands;
import dev.tfp.datagen.hitbox.PlantPools;
import dev.tfp.datagen.plant.PlantBlockGen;
import dev.tfp.datagen.plant.PlantBlockGen.Plant;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * PAR-WORLD-47: the 13 plant block types of TFC's {@code Plant} enum that neither {@link PlantBlockGen} (land and floating
 * plants) nor {@code GrowingPlantBlocks} (PAR-FARM-25: vines, columns, kelp, giant kelp, saguaro, creepers, epiphytes)
 * generate (30 plants: water, grass-water and tall-water plants, ocean creepers and rotatables, barrel cactus, saguaro fruit,
 * bamboo) as CraftEngine blocks {@code tfp:plant/<name>}, with the properties their TFC blockstates draw by ({@code part}
 * as {@code half}, the six face booleans, {@code facing}, {@code age}, {@code open}, {@code leaves}) and, for the tall water
 * plants, {@code waterlogged} (TFC's {@code fluid}, empty or a water).
 *
 * <p><b>Look budget</b> (owner decision 2026-11-25, PAR-WORLD-47's first plan line). The plant pools of PAR-WORLD-12 are
 * full, so plants that live under water draw on CraftEngine's {@code kelp} pool (25 states, waterlogged by nature): the
 * first {@link #POOL_MODELS} single-part looks in TFC's enum order are pool states with TFC's model; every other look
 * (multipart ocean creepers, rotated ocean plants, the overflow) shares one empty kelp state and is drawn by
 * {@code entity_renderer} item displays, one per model part. Every dry plant is a hitbox-only carrier picked by the selector
 * ({@link HitboxDemands#PLANT_SHAPE_ROWS}) with item displays: the berry bush row, the barrel row (collision) for the barrel
 * cactus and bamboo. Tinted faces are baked with TFC's default climate colour as in PAR-WORLD-12; lifecycle stages are
 * PAR-WORLD-48's.
 */
public final class PlantShapeGen {
    public static final String OUTPUT_DIR = "craftengine/tfp/configuration/plant_shape/";
    public static final String WATER_OUTPUT_DIR = "craftengine/tfp/configuration/plant_water/";
    static final String LANG_RP = "craftengine/tfp/resourcepack/assets/tfp_plant_shape/lang/";
    static final String BLOCKSTATES = TfcCheckout.ASSETS + "blockstates/plant/";
    static final String LOOT_SOURCE = TfcCheckout.MAIN + "data/" + Json.FROM_NS + "/loot_table/blocks/plant/";
    /** The types generated here: the block types of Plant.java that PlantBlockGen and GrowingPlantBlocks leave. */
    public static final Set<String> TYPES = Set.of("CACTUS_FLOWER", "CACTUS", "OCEAN_ROCK_CREEPING", "OCEAN_CREEPING",
        "OCEAN_ROTATABLE", "TALL_WATER", "TALL_WATER_FRESH", "WATER", "WATER_FRESH", "GRASS_WATER", "GRASS_WATER_FRESH",
        "BAMBOO_SAPLING", "BAMBOO");
    /** Always under water (TFC's {@code fluid} is never empty in the world): the kelp pool. */
    public static final Set<String> SUBMERGED = Set.of("WATER", "WATER_FRESH", "GRASS_WATER", "GRASS_WATER_FRESH",
        "OCEAN_CREEPING", "OCEAN_ROCK_CREEPING", "OCEAN_ROTATABLE");
    /** In water or emergent per half: a {@code waterlogged} property picks the kelp pool or the dry carrier. */
    public static final Set<String> TALL_WATER = Set.of("TALL_WATER", "TALL_WATER_FRESH");
    /** Solid in TFC (the barrel cactus's {@code solid()}, bamboo): the barrel's collision outline row. */
    public static final Set<String> SOLID = Set.of("CACTUS", "BAMBOO");
    /** PAR-WORLD-59: the selector's water plant pool ({@code hitbox/PlantPools}). */
    public static final String KELP_POOL = PlantPools.KELP;
    public static final int POOL_SIZE = PlantPools.capacity(KELP_POOL);
    /** Model looks per pool; the last state is the shared empty one under the item displays. */
    public static final int POOL_MODELS = PlantPools.waterModels();
    static final String EMPTY_MODEL = "tfp:block/plant_empty";
    static final String WATERLOGGED = "waterlogged";

    private PlantShapeGen() {
    }

    /** What {@link #run} wrote. */
    public record Result(List<Plant> plants, int states, Map<String, Integer> poolUse, int renderedAppearances,
            Set<String> renderItems) {
    }

    public static boolean available(TfcCheckout tfc) {
        return PlantBlockGen.available(tfc) && tfc.exists(BLOCKSTATES + "moss.json");
    }

    /** One property of a generated block: its CraftEngine type and values in TFC's order. */
    record Prop(String name, String type, List<String> values, String def) {
        Map<String, Object> yaml() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("type", type);
            switch (type) {
                case "int" -> m.put("range", values.getFirst() + "~" + values.getLast());
                case "boolean" -> { }
                default -> m.put("values", values);
            }
            m.put("default", type.equals("int") ? (Object) Integer.parseInt(def) : type.equals("boolean")
                ? (Object) Boolean.parseBoolean(def) : def);
            return m;
        }
    }

    /** A plant's TFC blockstate and the properties it draws by. */
    record Blockstate(String source, JsonObject json, List<Prop> props) {
        static Blockstate read(TfcCheckout tfc, Plant p) {
            String src = BLOCKSTATES + p.name() + ".json";
            if (!tfc.exists(src)) {
                throw new DatagenException("TFC has no blockstate for plant " + p.name());
            }
            JsonObject json = Json.parse(tfc.readString(src), src).getAsJsonObject();
            Map<String, Set<String>> seen = new LinkedHashMap<>();
            if (json.has("variants")) {
                for (String k : json.getAsJsonObject("variants").keySet()) {
                    parse(k).forEach((n, v) -> seen.computeIfAbsent(n, x -> new LinkedHashSet<>()).add(v));
                }
            } else {
                for (JsonElement part : json.getAsJsonArray("multipart")) {
                    JsonObject o = part.getAsJsonObject();
                    if (o.has("when")) {
                        for (Map<String, String> w : whens(o.getAsJsonObject("when"))) {
                            w.forEach((n, v) -> seen.computeIfAbsent(n, x -> new LinkedHashSet<>())
                                .addAll(List.of(v.split("\\|"))));
                        }
                    }
                }
            }
            List<Prop> props = new ArrayList<>();
            seen.forEach((n, vals) -> props.add(prop(src, n, vals)));
            if (TALL_WATER.contains(p.type())) {
                props.add(new Prop(WATERLOGGED, "boolean", List.of("false", "true"), "true"));
            }
            return new Blockstate(src, json, List.copyOf(props));
        }

        private static Prop prop(String src, String name, Set<String> vals) {
            if (name.equals("part")) {
                if (!Set.of("lower", "upper").containsAll(vals)) {
                    throw new DatagenException(src + ": part " + vals);
                }
                return new Prop("half", "double_block_half", List.of("lower", "upper"), "lower");
            }
            if (Set.of("true", "false").containsAll(vals)) {
                return new Prop(name, "boolean", List.of("false", "true"), "false");
            }
            if (vals.stream().allMatch(v -> v.matches("\\d+"))) {
                List<Integer> ints = vals.stream().map(Integer::parseInt).sorted().toList();
                List<String> range = new ArrayList<>();
                for (int i = ints.getFirst(); i <= ints.getLast(); i++) {
                    range.add(Integer.toString(i));
                }
                return new Prop(name, "int", range, range.getFirst());
            }
            if (name.equals("facing")) {
                List<String> six = List.of("down", "up", "north", "south", "west", "east");
                List<String> four = List.of("north", "east", "south", "west");
                if (vals.equals(Set.copyOf(six))) {
                    return new Prop(name, "direction", six, "up");
                }
                if (vals.equals(Set.copyOf(four))) {
                    return new Prop(name, "horizontal_direction", four, "north");
                }
            }
            List<String> v = new ArrayList<>(vals);
            return new Prop(name, "string", v, v.getFirst());
        }

        /** The parts drawn in {@code state} (TFC's property names; ours renamed back): each a weighted list of models. */
        List<List<ModelRef>> parts(Map<String, String> ours) {
            Map<String, String> state = new LinkedHashMap<>();
            ours.forEach((k, v) -> {
                if (!k.equals(WATERLOGGED)) {
                    state.put(k.equals("half") ? "part" : k, v);
                }
            });
            List<List<ModelRef>> out = new ArrayList<>();
            if (json.has("variants")) {
                for (Map.Entry<String, JsonElement> e : json.getAsJsonObject("variants").entrySet()) {
                    if (matches(parse(e.getKey()), state)) {
                        out.add(refs(e.getValue()));
                        return drawn(out);
                    }
                }
                throw new DatagenException(source + ": no variant for " + state);
            }
            for (JsonElement part : json.getAsJsonArray("multipart")) {
                JsonObject o = part.getAsJsonObject();
                boolean on = !o.has("when");
                if (!on) {
                    for (Map<String, String> w : whens(o.getAsJsonObject("when"))) {
                        on |= matches(w, state);
                    }
                }
                if (on) {
                    out.add(refs(o.get("apply")));
                }
            }
            return drawn(out);
        }

        private static List<List<ModelRef>> drawn(List<List<ModelRef>> parts) {
            List<List<ModelRef>> out = new ArrayList<>();
            for (List<ModelRef> p : parts) {
                if (!(p.size() == 1 && isEmpty(p.getFirst().path()))) {
                    out.add(p);
                }
            }
            return out;
        }

        private static List<Map<String, String>> whens(JsonObject when) {
            List<Map<String, String>> out = new ArrayList<>();
            if (when.has("OR")) {
                for (JsonElement e : when.getAsJsonArray("OR")) {
                    out.add(flat(e.getAsJsonObject()));
                }
            } else {
                out.add(flat(when));
            }
            return out;
        }

        private static Map<String, String> flat(JsonObject o) {
            Map<String, String> m = new LinkedHashMap<>();
            o.entrySet().forEach(e -> m.put(e.getKey(), e.getValue().getAsString()));
            return m;
        }

        private static boolean matches(Map<String, String> when, Map<String, String> state) {
            for (Map.Entry<String, String> w : when.entrySet()) {
                if (!List.of(w.getValue().split("\\|")).contains(state.get(w.getKey()))) {
                    return false;
                }
            }
            return true;
        }

        private static Map<String, String> parse(String key) {
            Map<String, String> m = new LinkedHashMap<>();
            if (!key.isEmpty()) {
                for (String kv : key.split(",")) {
                    String[] s = kv.split("=");
                    m.put(s[0], s[1]);
                }
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

    /** Every state of {@code props} as an ordered property map, the first property varying slowest. */
    static List<Map<String, String>> states(List<Prop> props) {
        List<Map<String, String>> out = new ArrayList<>();
        out.add(new LinkedHashMap<>());
        for (Prop p : props) {
            List<Map<String, String>> next = new ArrayList<>();
            for (Map<String, String> s : out) {
                for (String v : p.values()) {
                    Map<String, String> m = new LinkedHashMap<>(s);
                    m.put(p.name(), v);
                    next.add(m);
                }
            }
            out = next;
        }
        return out;
    }

    static String key(Map<String, String> state) {
        List<String> kv = new ArrayList<>();
        state.forEach((k, v) -> kv.add(k + "=" + v));
        return String.join(",", kv);
    }

    /** Where a state of {@code p} sits: the kelp pool or a dry hitbox-only carrier (null). */
    static String pool(Plant p, Map<String, String> state) {
        return SUBMERGED.contains(p.type()) || TALL_WATER.contains(p.type()) && "true".equals(state.get(WATERLOGGED))
            ? KELP_POOL : null;
    }

    /** The selector row of a plant's dry states. */
    public static String row(String type) {
        return SOLID.contains(type) ? HitboxDemands.PLANT_SOLID_ROW : HitboxDemands.PLANT_ROW;
    }

    /** Whether some state of a plant of {@code type} needs a dry carrier from the selector. */
    public static boolean needsCarrier(String type) {
        return !SUBMERGED.contains(type);
    }

    /** The item display of a block model ({@code tfp:render/plant_shape/plant/moss}). */
    static String renderItem(String model) {
        return Json.TO_NS + ":render/plant_shape/" + model.substring(model.indexOf(":block/") + ":block/".length());
    }

    /**
     * The display rotation of a blockstate part turned by {@code x} then {@code y}: vanilla turns the block model by
     * {@code rotateYXZ(-y, -x, 0)}, and an item display draws the model turned 180 degrees about y (CropBlocks.rotation).
     * CraftEngine reads {@code "x,y,z"} as {@code rotateZYX}, so this is {@code Ry(180 - y) Rx(-x)}.
     */
    static String rotation(int x, int y) {
        return Math.floorMod(-x, 360) + "," + Math.floorMod(180 - y, 360) + ",0";
    }

    /** Writes every block, its item, the models, textures, lang and loot. */
    @SuppressWarnings("unchecked")
    public static Result run(TfcCheckout tfc, OutputTree out) {
        List<Plant> plants = PlantBlockGen.plants(tfc, TYPES);
        Map<String, List<String>> rows = new LinkedHashMap<>();
        HitboxDemands.PLANT_SHAPE_ROWS.keySet().forEach(r -> rows.put(r, new ArrayList<>()));
        plants.stream().filter(p -> needsCarrier(p.type())).forEach(p -> rows.get(row(p.type())).add(p.name()));
        if (!rows.equals(HitboxDemands.PLANT_SHAPE_ROWS)) {
            throw new DatagenException("Plant.java's dry plant shapes are " + rows + " but HitboxDemands.PLANT_SHAPE_ROWS is "
                + HitboxDemands.PLANT_SHAPE_ROWS);
        }
        PlantBlockGen.Assets assets = new PlantBlockGen.Assets(tfc, out);
        assets.emptyModel();
        // first pass: every state's resolved look, so the pools fill in one deterministic order (TFC's enum order)
        Map<Plant, Blockstate> blockstates = new LinkedHashMap<>();
        Map<Plant, Map<String, List<List<ModelRef>>>> looks = new LinkedHashMap<>();
        Map<String, Map<String, String>> poolLooks = new LinkedHashMap<>(); // pool -> look key -> auto_state id
        poolLooks.put(KELP_POOL, new LinkedHashMap<>());
        int states = 0;
        for (Plant p : plants) {
            Blockstate bs = Blockstate.read(tfc, p);
            blockstates.put(p, bs);
            Map<String, List<List<ModelRef>>> byState = new LinkedHashMap<>();
            for (Map<String, String> s : states(bs.props())) {
                List<List<ModelRef>> parts = new ArrayList<>();
                for (List<ModelRef> part : bs.parts(s)) {
                    List<ModelRef> resolved = new ArrayList<>();
                    for (ModelRef r : part) {
                        // the water tint falls on vanilla's water_still, which datagen cannot bake: drawn untinted (ledger)
                        String tint = "water".equals(p.tint()) ? null : p.tint();
                        String model = r.path().startsWith("minecraft:") ? r.path() : assets.look(r.path(), tint);
                        resolved.add(new ModelRef(model, r.x(), r.y(), r.uvlock(), r.weight()));
                    }
                    parts.add(resolved);
                }
                byState.put(key(s), parts);
                states++;
                String pool = pool(p, s);
                if (pool != null && parts.size() == 1) {
                    Map<String, String> ids = poolLooks.get(pool);
                    String k = lookKey(parts.getFirst());
                    if (!ids.containsKey(k) && ids.size() < POOL_MODELS) {
                        ids.put(k, "tfp_" + pool + "_" + ids.size());
                    }
                }
            }
            looks.put(p, byState);
        }
        Map<String, Integer> use = new LinkedHashMap<>();
        Set<String> renderItems = new TreeSet<>();
        int rendered = 0;
        for (Plant p : plants) {
            Blockstate bs = blockstates.get(p);
            Map<String, Object> appearances = new LinkedHashMap<>();
            Map<String, String> appearanceByLook = new LinkedHashMap<>();
            Map<String, Object> variants = new LinkedHashMap<>();
            Map<String, Object> items = new LinkedHashMap<>();
            Map<String, Object> mappings = new TreeMap<>();
            for (Map<String, String> s : states(bs.props())) {
                List<List<ModelRef>> parts = looks.get(p).get(key(s));
                String pool = pool(p, s);
                String poolId = pool != null && parts.size() == 1 ? poolLooks.get(pool).get(lookKey(parts.getFirst())) : null;
                Map<String, Object> app = new LinkedHashMap<>();
                String lookKey;
                if (poolId != null) {
                    app = CeBlockStates.appearance(autoState(pool, poolId), parts.getFirst());
                    lookKey = "pool:" + poolId;
                } else {
                    if (pool != null) {
                        app.putAll(CeBlockStates.appearance(autoState(pool, "tfp_" + pool + "_empty"),
                            List.of(new ModelRef(EMPTY_MODEL, 0, 0, false, 1))));
                    } else {
                        HitboxAllocation.Assignment a = HitboxAllocation.agreed(p.id(), HitboxDemands.PLANT_PATTERN);
                        if (a.mode() != HitboxAllocation.Mode.HITBOX_ONLY) {
                            throw new DatagenException(p.id() + " got a " + a.mode() + " carrier; plants draw item displays");
                        }
                        app.put("state", a.state());
                        app.put("transparent", true);
                        if (a.hideTarget() != null && !a.pool()) {
                            mappings.put(a.state(), a.hideTarget());
                        }
                    }
                    List<Object> elements = new ArrayList<>();
                    StringBuilder lk = new StringBuilder(pool == null ? "dry:" : "empty:" + pool + ":");
                    for (List<ModelRef> part : parts) {
                        ModelRef r = part.getFirst(); // a display draws one model: the first of TFC's random picks
                        String item = renderItem(r.path());
                        if (renderItems.add(item)) { // each display item once over all files
                            items.put(item, renderItemYaml(r.path()));
                        }
                        Map<String, Object> el = new LinkedHashMap<>();
                        el.put("item", item);
                        el.put("rotation", rotation(r.x(), r.y()));
                        elements.add(el);
                        lk.append(item).append('@').append(r.x()).append('/').append(r.y()).append(';');
                    }
                    if (!elements.isEmpty()) {
                        app.put("entity_renderer", elements);
                    } else if (pool == null) {
                        lk.append("nothing"); // TFC draws nothing (the upper half of a two-block model): the carrier alone
                    }
                    lookKey = lk.toString();
                }
                String name = appearanceByLook.get(lookKey);
                if (name == null) {
                    name = "look_" + appearanceByLook.size();
                    appearanceByLook.put(lookKey, name);
                    appearances.put(name, app);
                    if (app.containsKey("entity_renderer")) {
                        rendered++;
                    }
                }
                variants.put(key(s), Map.of("appearance", name));
            }
            Map<String, Object> block = new LinkedHashMap<>();
            block.put("settings", settings(p, tfc));
            List<Object> behaviour = new ArrayList<>();
            if (bs.props().stream().anyMatch(x -> x.name().equals("half"))) {
                behaviour.add(Map.of("type", "double_high_block"));
            }
            if (!behaviour.isEmpty()) {
                block.put("behavior", behaviour);
            }
            if (bs.props().isEmpty()) {
                block.put("state", appearances.values().iterator().next());
            } else {
                Map<String, Object> props = new LinkedHashMap<>();
                bs.props().forEach(x -> props.put(x.name(), x.yaml()));
                Map<String, Object> st = new LinkedHashMap<>();
                st.put("properties", props);
                st.put("appearances", appearances);
                st.put("variants", variants);
                block.put("states", st);
            }
            Map<String, Object> blocks = new LinkedHashMap<>();
            blocks.put(p.id(), block);
            String itemModel = itemModel(tfc, assets, p);
            if (itemModel != null) {
                ((Map<String, Object>) block.get("settings")).put("item", p.id());
                items.put(p.id(), item(p, itemModel, bs));
            }
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("blocks", blocks);
            if (!items.isEmpty()) {
                root.put("items", items);
            }
            if (!mappings.isEmpty()) {
                root.put("block_state_mappings", mappings);
            }
            boolean water = SUBMERGED.contains(p.type()) || TALL_WATER.contains(p.type());
            out.put((water ? WATER_OUTPUT_DIR : OUTPUT_DIR) + p.name() + ".yml", Yaml.write(DeviceYaml.HEADER, root));
            String loot = LOOT_SOURCE + p.name() + ".json";
            if (tfc.exists(loot)) {
                LootTableExport.delegateBlock(out, "plant/" + p.name(), Json.rename(Json.parse(tfc.readString(loot), loot)), n -> { });
            }
        }
        for (Map.Entry<String, Map<String, String>> e : poolLooks.entrySet()) {
            use.put(e.getKey(), e.getValue().size() + 1);
        }
        writeLang(tfc, out, plants);
        return new Result(List.copyOf(plants), states, java.util.Collections.unmodifiableMap(use), rendered,
            java.util.Collections.unmodifiableSet(renderItems));
    }

    private static Map<String, Object> autoState(String pool, String id) {
        return PlantPools.appearance(pool, id);
    }

    static String lookKey(List<ModelRef> look) {
        StringBuilder sb = new StringBuilder();
        for (ModelRef r : look) {
            sb.append(r.path()).append('/').append(r.x()).append('/').append(r.y()).append('/').append(r.uvlock())
                .append('/').append(r.weight()).append(';');
        }
        return sb.toString();
    }

    private static Map<String, Object> renderItemYaml(String model) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("template", "tfp:item/render");
        m.put("arguments", Map.of("render_model", model));
        return m;
    }

    /** TFC's strength and sounds per type (Plant.BlockType's properties). */
    private static Map<String, Object> settings(Plant p, TfcCheckout tfc) {
        String t = p.type();
        boolean cactus = t.contains("CACTUS") && !t.equals("CACTUS_FLOWER");
        String sound = cactus ? "wool" : t.equals("OCEAN_ROTATABLE") ? "slime_block" : t.startsWith("OCEAN_") ? "basalt"
            : t.startsWith("BAMBOO") ? (t.equals("BAMBOO") ? "bamboo" : "bamboo_sapling")
            : t.equals("CACTUS_FLOWER") ? "crop" : SUBMERGED.contains(t) || TALL_WATER.contains(t) ? "wet_grass" : "grass";
        String strength = cactus ? "0.25" : t.equals("BAMBOO") ? "1.0" : "0.0";
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("hardness", new BigDecimal(strength));
        s.put("resistance", new BigDecimal(strength));
        s.put("sounds", sounds(sound));
        s.put("replaceable", !SOLID.contains(t));
        s.put("push_reaction", "destroy");
        s.put("can_occlude", false);
        s.put("is_suffocating", false);
        s.put("is_view_blocking", false);
        return s;
    }

    private static Map<String, String> sounds(String group) {
        Map<String, String> m = new LinkedHashMap<>();
        for (String e : List.of("break", "step", "place", "hit", "fall")) {
            m.put(e, "minecraft:block." + group + "." + e);
        }
        return m;
    }

    /** TFC's item model of the plant, or null when TFC registers no item (the body blocks of columns, the kelp tree). */
    private static String itemModel(TfcCheckout tfc, PlantBlockGen.Assets assets, Plant p) {
        if (!tfc.exists(TfcCheckout.ASSETS + "models/item/plant/" + p.name() + ".json")) {
            return null;
        }
        return assets.itemModel(p);
    }

    private static Map<String, Object> item(Plant p, String model, Blockstate bs) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("material", "paper");
        item.put("data", Map.of("item_name", "<!i><lang:block." + Json.TO_NS + ".plant." + p.name() + ">"));
        item.put("model", model);
        Map<String, Object> beh = new LinkedHashMap<>();
        boolean tall = bs.props().stream().anyMatch(x -> x.name().equals("half"));
        beh.put("type", tall ? "double_high_block_item" : "block_item");
        beh.put("block", p.id());
        item.put("behavior", beh);
        return item;
    }

    private static void writeLang(TfcCheckout tfc, OutputTree out, List<Plant> plants) {
        String dir = TfcCheckout.ASSETS + "lang/";
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
}
