/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/plant/Plant.java @ c3b4d9536 (the enum and BlockType factories: the
 *       growing shapes and their sets),
 *       src/main/java/net/dries007/tfc/common/blocks/plant/{TopPlantBlock,BodyPlantBlock,TFCVineBlock,CreepingPlantBlock,
 *       EpiphytePlantBlock,PipePlantBlock,KelpTreeFlowerBlock,GrowingBranchingCactusBlock}.java @ c3b4d9536 (the state
 *       properties),
 *       src/main/resources/assets/tfc/{blockstates,models}/**plant/** and textures @ c3b4d9536 (copied),
 *       src/main/resources/data/tfc/loot_table/blocks/plant/*.json @ c3b4d9536 (delegated to the loot runtime),
 *       src/main/resources/assets/tfc/lang/*.json @ c3b4d9536 (block.tfc.plant.*)
 */
package dev.tfp.datagen.plant;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.tfp.datagen.DatagenException;
import dev.tfp.datagen.Json;
import dev.tfp.datagen.LootTableExport;
import dev.tfp.datagen.OutputTree;
import dev.tfp.datagen.TfcCheckout;
import dev.tfp.datagen.Yaml;
import dev.tfp.datagen.device.DeviceAppearance;
import dev.tfp.datagen.device.DeviceBlockSpec;
import dev.tfp.datagen.device.DeviceProperty;
import dev.tfp.datagen.device.DeviceYaml;
import dev.tfp.datagen.hitbox.HitboxAllocation;
import dev.tfp.datagen.hitbox.HitboxDemands;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PAR-FARM-25: TFC's growing plant shapes as CraftEngine blocks ({@code tfp:plant/<name>}, files
 * {@code craftengine/tfp/configuration/plant/growing/<name>.yml}): the jungle vines, the weeping and twisting columns (a body
 * and a head per plant), the kelp (body and head), the giant kelp (tree and flower), the saguaro (body and head), the creeping
 * plants, the epiphytes and the perched epiphytes. {@code PlantBlockGen} leaves these block types out (its pools could not hold
 * them), so worldgen placed vanilla placeholders.
 *
 * <p><b>States</b> are TFC's: a head has vanilla's {@code age} 0..25 (the column grows from it), kelp has the
 * {@code fluid} property (empty or salt water), vines the five faces, creeping plants and the saguaro the six, the kelp tree
 * six faces and the fluid, the kelp flower {@code age} 0..5, the six-way {@code facing} and the fluid, the saguaro head
 * {@code grows_branches}, an epiphyte the four horizontal facings.
 *
 * <p><b>Look and hitbox.</b> Every block is hitbox-only on the one shared outline row ({@link HitboxDemands#GROWING_PLANT_KEY},
 * no collision) with TFC's blockstate models as item displays (one per applying multipart model; tinted ones baked with the
 * default climate colour as {@code PlantBlockGen}). The outline is one full-height box where TFC has a different one per
 * shape (ledger divergence). Settings are the plants' (instant break, grass sounds; cacti 0.25 and wool), loot is TFC's
 * table, the items TFC's item models.
 */
public final class GrowingPlantBlocks {
    public static final String OUTPUT_DIR = "craftengine/tfp/configuration/plant/growing/";
    static final String LANG_RP = "craftengine/tfp/resourcepack/assets/tfp_plant_growing/lang/";
    static final Set<String> TYPES = Set.of("VINE", "WEEPING", "WEEPING_TOP", "TWISTING", "TWISTING_TOP", "TWISTING_TOP_SHORT",
        "TWISTING_SOLID", "TWISTING_SOLID_TOP", "KELP", "KELP_TOP", "KELP_TREE", "KELP_TREE_FLOWER", "BRANCHING_CACTUS",
        "BRANCHING_CACTUS_TOP", "CREEPING", "CREEPING_STONE", "EPIPHYTE", "PERCHED_EPIPHYTE");
    /** TFC's {@code NO_ITEM_TYPES} among {@link #TYPES}: the bodies have no block item. */
    static final Set<String> NO_ITEM = Set.of("WEEPING", "TWISTING_SOLID", "KELP", "KELP_TREE", "TWISTING", "BRANCHING_CACTUS");
    static final Set<String> FOLIAGE = Set.of("WEEPING", "WEEPING_TOP", "TWISTING", "TWISTING_TOP", "TWISTING_SOLID",
        "TWISTING_SOLID_TOP");
    static final Set<String> CACTI = Set.of("BRANCHING_CACTUS", "BRANCHING_CACTUS_TOP");
    static final List<String> FACES6 = List.of("north", "east", "south", "west", "up", "down");
    static final List<String> FACES5 = List.of("north", "east", "south", "west", "up");
    static final List<String> HORIZONTAL = List.of("north", "east", "south", "west");
    static final List<String> FLUIDS = List.of("empty", "salt_water");
    private static final Pattern ENTRY = Pattern.compile("^ {4}([A-Z_]+)\\(BlockType\\.([A-Z_]+)", Pattern.MULTILINE);
    private static final Pattern SET = Pattern.compile("EnumSet<Plant> (\\w+) = EnumSet\\.of\\(([^)]*)\\)");

    private GrowingPlantBlocks() {
    }

    /** One growing plant: its name and TFC block type. */
    public record Growing(String name, String type) {
        public String id() {
            return HitboxDemands.growingPlantId(name);
        }
    }

    /** One part of a state's look: TFC's model and its blockstate rotation. */
    record Part(String model, int x, int y) {
    }

    public static boolean available(TfcCheckout tfc) {
        return tfc.exists(PlantBlockGen.PLANT_JAVA) && tfc.exists(TfcCheckout.ASSETS + "blockstates/plant/hanging_vines.json");
    }

    /** The plants of {@link #TYPES} in TFC's enum order. */
    public static List<Growing> plants(TfcCheckout tfc) {
        List<Growing> out = new ArrayList<>();
        Matcher m = ENTRY.matcher(tfc.readString(PlantBlockGen.PLANT_JAVA));
        while (m.find()) {
            if (TYPES.contains(m.group(2))) {
                out.add(new Growing(m.group(1).toLowerCase(Locale.ROOT), m.group(2)));
            }
        }
        return List.copyOf(out);
    }

    /** The state properties of a block type (TFC's {@code createBlockStateDefinition}). */
    static List<DeviceProperty> properties(String type) {
        List<DeviceProperty> p = new ArrayList<>();
        switch (type) {
            case "WEEPING", "TWISTING", "TWISTING_SOLID", "PERCHED_EPIPHYTE" -> { }
            case "WEEPING_TOP", "TWISTING_TOP", "TWISTING_TOP_SHORT", "TWISTING_SOLID_TOP" -> p.add(DeviceProperty.range("age", 0, 25, 0));
            case "KELP" -> p.add(DeviceProperty.names("fluid", FLUIDS, "empty"));
            case "KELP_TOP" -> {
                p.add(DeviceProperty.range("age", 0, 25, 0));
                p.add(DeviceProperty.names("fluid", FLUIDS, "empty"));
            }
            case "VINE" -> FACES5.forEach(f -> p.add(DeviceProperty.bool(f, false)));
            case "CREEPING", "CREEPING_STONE", "BRANCHING_CACTUS" -> FACES6.forEach(f -> p.add(DeviceProperty.bool(f, false)));
            case "BRANCHING_CACTUS_TOP" -> {
                FACES6.forEach(f -> p.add(DeviceProperty.bool(f, false)));
                p.add(DeviceProperty.bool("grows_branches", false));
            }
            case "KELP_TREE" -> {
                FACES6.forEach(f -> p.add(DeviceProperty.bool(f, false)));
                p.add(DeviceProperty.names("fluid", FLUIDS, "empty"));
            }
            case "KELP_TREE_FLOWER" -> {
                p.add(DeviceProperty.range("age", 0, 5, 0));
                p.add(DeviceProperty.names("facing", List.of("down", "up", "north", "south", "west", "east"), "up"));
                p.add(DeviceProperty.names("fluid", FLUIDS, "empty"));
            }
            case "EPIPHYTE" -> p.add(DeviceProperty.names("facing", HORIZONTAL, "north"));
            default -> throw new DatagenException("no state properties for " + type);
        }
        return p;
    }

    /** The tint colour map of a plant ({@code PlantBlockGen.plants}' rule), or null. */
    static String tint(String src, String constant, String type) {
        Map<String, Set<String>> sets = new LinkedHashMap<>();
        Matcher s = SET.matcher(src);
        while (s.find()) {
            Set<String> names = new LinkedHashSet<>();
            for (String n : s.group(2).split(",")) {
                if (!n.isBlank()) {
                    names.add(n.trim());
                }
            }
            sets.put(s.group(1), names);
        }
        for (String need : List.of("BLOCK_TINTED_PLANTS", "FOLIAGE_COLOR_PLANTS", "WATER_COLOR_PLANTS")) {
            if (!sets.containsKey(need)) {
                throw new DatagenException(PlantBlockGen.PLANT_JAVA + ": " + need + " not found");
            }
        }
        if (!sets.get("BLOCK_TINTED_PLANTS").contains(constant)) {
            return null;
        }
        if (sets.get("WATER_COLOR_PLANTS").contains(constant)) {
            return null; // the tank bromeliad's water is vanilla's water texture, which cannot be baked (ledger divergence)
        }
        return FOLIAGE.contains(type) || sets.get("FOLIAGE_COLOR_PLANTS").contains(constant) ? "foliage" : "grass";
    }

    static boolean itemTinted(String src, String constant) {
        Matcher s = SET.matcher(src);
        while (s.find()) {
            if (s.group(1).equals("ITEM_TINTED_PLANTS")) {
                return List.of(s.group(2).split("\\s*,\\s*")).contains(constant);
            }
        }
        throw new DatagenException(PlantBlockGen.PLANT_JAVA + ": ITEM_TINTED_PLANTS not found");
    }

    /** The models TFC's blockstate draws for {@code state}: a variant, or every applying multipart model. */
    static List<Part> look(JsonObject blockstate, String source, Map<String, String> state) {
        List<Part> parts = new ArrayList<>();
        if (blockstate.has("variants")) {
            JsonObject variants = blockstate.getAsJsonObject("variants");
            for (Map.Entry<String, JsonElement> e : variants.entrySet()) {
                if (matches(key(e.getKey()), state)) {
                    parts.add(part(e.getValue(), source));
                    return parts;
                }
            }
            throw new DatagenException(source + ": no variant for " + state);
        }
        for (JsonElement el : blockstate.getAsJsonArray("multipart")) {
            JsonObject o = el.getAsJsonObject();
            Map<String, String> when = new LinkedHashMap<>();
            if (o.has("when")) {
                JsonObject w = o.getAsJsonObject("when");
                if (w.has("OR") || w.has("AND")) {
                    throw new DatagenException(source + ": OR/AND conditions are not handled");
                }
                w.entrySet().forEach(x -> when.put(x.getKey(), x.getValue().getAsString()));
            }
            if (matches(when, state)) {
                parts.add(part(o.get("apply"), source));
            }
        }
        return parts;
    }

    private static Part part(JsonElement apply, String source) {
        JsonObject o = apply.isJsonArray() ? apply.getAsJsonArray().get(0).getAsJsonObject() : apply.getAsJsonObject();
        if (!o.has("model")) {
            throw new DatagenException(source + ": a model reference without a model");
        }
        return new Part(o.get("model").getAsString(), o.has("x") ? o.get("x").getAsInt() : 0, o.has("y") ? o.get("y").getAsInt() : 0);
    }

    private static Map<String, String> key(String k) {
        Map<String, String> m = new LinkedHashMap<>();
        if (!k.isEmpty()) {
            for (String kv : k.split(",")) {
                String[] s = kv.split("=");
                m.put(s[0], s[1]);
            }
        }
        return m;
    }

    private static boolean matches(Map<String, String> when, Map<String, String> state) {
        for (Map.Entry<String, String> w : when.entrySet()) {
            String have = state.get(w.getKey());
            if (have == null || !List.of(w.getValue().split("\\|")).contains(have)) {
                return false;
            }
        }
        return true;
    }

    /** The item display rotation of a blockstate turn: {@code CropBlocks.rotation} with TFC's x first. */
    static String rotation(int x, int y) {
        return x + "," + Math.floorMod(180 - y, 360) + ",0";
    }

    /** Our render item of a model (one per model across all growing plants). */
    static String renderItem(String model) {
        return Json.TO_NS + ":render/growing/" + model.substring(model.indexOf(":block/") + ":block/".length());
    }

    /** Writes the blocks, items, models, textures, loot and names. Returns the number of server states. */
    public static int run(TfcCheckout tfc, OutputTree out) {
        List<Growing> plants = plants(tfc);
        List<String> names = plants.stream().map(Growing::name).toList();
        if (!names.equals(HitboxDemands.GROWING_PLANTS)) {
            throw new DatagenException("TFC's growing plants " + names + " differ from HitboxDemands.GROWING_PLANTS "
                + HitboxDemands.GROWING_PLANTS);
        }
        String src = tfc.readString(PlantBlockGen.PLANT_JAVA);
        PlantBlockGen.Assets assets = new PlantBlockGen.Assets(tfc, out);
        Map<String, Object> renderItems = new LinkedHashMap<>();
        int states = 0;
        for (Growing p : plants) {
            states += block(tfc, out, assets, src, p, renderItems);
        }
        out.put(OUTPUT_DIR + "render_items.yml", Yaml.write(DeviceYaml.HEADER, Map.of("items", renderItems)));
        assets.emptyModel();
        writeLang(tfc, out, plants);
        return states;
    }

    @SuppressWarnings("unchecked")
    private static int block(TfcCheckout tfc, OutputTree out, PlantBlockGen.Assets assets, String javaSrc, Growing p,
            Map<String, Object> renderItems) {
        String bsPath = TfcCheckout.ASSETS + "blockstates/plant/" + p.name() + ".json";
        if (!tfc.exists(bsPath)) {
            throw new DatagenException("TFC has no blockstate for plant " + p.name());
        }
        JsonObject bs = Json.parse(tfc.readString(bsPath), bsPath).getAsJsonObject();
        String tint = tint(javaSrc, p.name().toUpperCase(Locale.ROOT), p.type());
        List<DeviceProperty> props = properties(p.type());
        DeviceBlockSpec.Builder b = DeviceBlockSpec.builder(p.id()).properties(props);
        HitboxAllocation.Assignment a = HitboxAllocation.agreed(p.id(), HitboxDemands.GROWING_PLANT_PATTERN);
        if (a.mode() != HitboxAllocation.Mode.HITBOX_ONLY) {
            throw new DatagenException(p.id() + " got a " + a.mode() + " carrier; GrowingPlantBlocks draws item displays");
        }
        DeviceAppearance.Carrier carrier = DeviceAppearance.Carrier.state(a.state());
        if (a.hideTarget() != null && !a.pool()) {
            b.stateMapping(a.state(), a.hideTarget());
        }
        Map<String, String> byLook = new LinkedHashMap<>();
        Map<String, String> byState = new LinkedHashMap<>();
        Set<String> declared = new LinkedHashSet<>();
        for (Map<String, String> state : DeviceBlockSpec.combinations(props)) {
            List<Part> parts = look(bs, bsPath, state);
            StringBuilder lookKey = new StringBuilder();
            for (Part part : parts) {
                lookKey.append(part.model()).append('/').append(part.x()).append('/').append(part.y()).append(';');
            }
            String name = byLook.get(lookKey.toString());
            if (name == null) {
                name = "plant_" + byLook.size();
                byLook.put(lookKey.toString(), name);
                List<DeviceAppearance.Renderer> renderers = new ArrayList<>();
                for (Part part : parts) {
                    // the leafy kelp draws vanilla's kelp models (no TFC copy or tint)
                    String model = part.model().startsWith(Json.FROM_NS + ":") ? assets.look(part.model(), tint) : part.model();
                    String item = renderItem(model);
                    Map<String, Object> call = new LinkedHashMap<>();
                    call.put("template", "tfp:item/render");
                    call.put("arguments", Map.of("render_model", model));
                    renderItems.putIfAbsent(item, call);
                    if (declared.add(item)) {
                        b.renderItem(item, model);
                    }
                    renderers.add(DeviceAppearance.Renderer.item(item).rotated(rotation(part.x(), part.y())));
                }
                if (renderers.isEmpty()) {
                    String empty = emptyItem(renderItems);
                    if (declared.add(empty)) {
                        b.renderItem(empty, PlantBlockGen.EMPTY_MODEL);
                    }
                    renderers.add(DeviceAppearance.Renderer.item(empty));
                }
                b.appearance(DeviceAppearance.rendered(name, carrier, renderers));
            }
            byState.put(state.toString(), name);
        }
        b.everyState(s -> byState.get(s.toString()), null);
        DeviceBlockSpec spec = b.build();
        Map<String, Object> one = DeviceYaml.literal(spec);
        Map<String, Object> entry = (Map<String, Object>) ((Map<String, Object>) one.get("blocks")).get(p.id());
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("settings", settings(p));
        String lootSrc = TfcCheckout.MAIN + "data/" + Json.FROM_NS + "/loot_table/blocks/plant/" + p.name() + ".json";
        if (tfc.exists(lootSrc)) {
            LootTableExport.delegateBlock(out, "plant/" + p.name(), Json.rename(Json.parse(tfc.readString(lootSrc), lootSrc)), n -> { });
        }
        block.put("states", entry.get("states"));
        Map<String, Object> root = new LinkedHashMap<>();
        Map<String, Object> blocks = new LinkedHashMap<>();
        blocks.put(p.id(), block);
        root.put("blocks", blocks);
        if (!NO_ITEM.contains(p.type())) {
            PlantBlockGen.Plant plant = new PlantBlockGen.Plant(p.name(), p.type(), false, tint, itemTinted(javaSrc, p.name().toUpperCase(Locale.ROOT)));
            Map<String, Object> items = new LinkedHashMap<>();
            items.put(p.id(), item(p, assets.itemModel(plant)));
            root.put("items", items);
        }
        Object mappings = one.get("block_state_mappings");
        if (mappings != null) {
            root.put("block_state_mappings", mappings);
        }
        out.put(OUTPUT_DIR + p.name() + ".yml", Yaml.write(DeviceYaml.HEADER, root));
        return (int) spec.stateCount();
    }

    private static String emptyItem(Map<String, Object> renderItems) {
        String item = Json.TO_NS + ":render/growing/plant_empty";
        Map<String, Object> call = new LinkedHashMap<>();
        call.put("template", "tfp:item/render");
        call.put("arguments", Map.of("render_model", PlantBlockGen.EMPTY_MODEL));
        renderItems.putIfAbsent(item, call);
        return item;
    }

    /** TFC's settings: instant break (cacti 0.25), grass (cacti wool) sounds, destroyed by pistons, not occluding. */
    static Map<String, Object> settings(Growing p) {
        boolean cactus = CACTI.contains(p.type());
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("hardness", new BigDecimal(cactus ? "0.25" : "0.0"));
        s.put("resistance", new BigDecimal(cactus ? "0.25" : "0.0"));
        s.put("sounds", PlantBlockGen.sounds(cactus ? "wool" : "grass"));
        s.put("replaceable", false);
        s.put("push_reaction", "destroy");
        s.put("can_occlude", false);
        s.put("is_suffocating", false);
        s.put("is_view_blocking", false);
        if (!NO_ITEM.contains(p.type())) {
            s.put("item", p.id());
        }
        return s;
    }

    private static Map<String, Object> item(Growing p, String model) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("material", "paper");
        item.put("data", Map.of("item_name", "<!i><lang:block." + Json.TO_NS + ".plant." + p.name() + ">"));
        item.put("model", model);
        item.put("behavior", Map.of("type", "block_item", "block", p.id()));
        return item;
    }

    private static void writeLang(TfcCheckout tfc, OutputTree out, List<Growing> plants) {
        String dir = TfcCheckout.ASSETS + "lang/";
        for (String f : tfc.list(dir)) {
            if (!f.endsWith(".json")) {
                continue;
            }
            JsonObject src = Json.parse(tfc.readString(f), f).getAsJsonObject();
            TreeMap<String, String> keys = new TreeMap<>();
            for (Growing p : plants) {
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
