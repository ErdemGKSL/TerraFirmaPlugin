/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/wood/Wood.java @ c3b4d9536 (PAR-DECO-02: the DOOR, TRAPDOOR, FENCE_GATE,
 *       BUTTON and PRESSURE_PLATE strengths and properties),
 *       src/main/resources/assets/tfc/blockstates/wood/{door,trapdoor,fence_gate,button,pressure_plate}/*.json,
 *       models/block/wood/{door,trapdoor,fence_gate,button,pressure_plate}/*.json, models/item/wood/{...}/*.json and their
 *       textures @ c3b4d9536 (copied),
 *       src/main/resources/data/tfc/loot_table/blocks/wood/{...}/*.json @ c3b4d9536 (the drops),
 *       src/generated/resources/data/minecraft/tags/block/*.json @ c3b4d9536 (the block tags),
 *       src/main/resources/assets/tfc/lang/*.json @ c3b4d9536 (the names)
 */
package dev.tfp.datagen;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.tfp.datagen.hitbox.HitboxAllocation;
import dev.tfp.datagen.hitbox.HitboxDemands;
import dev.tfp.datagen.hitbox.TfcShapes;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PAR-DECO-02: the wood doors, trapdoors, fence gates, buttons and pressure plates of the 20 woods as CraftEngine blocks,
 * written into each wood's {@code configuration/woodfamily/<wood>.yml} beside the PAR-DECO-01 blocks:
 * <ul>
 *   <li>CraftEngine's built-in {@code door_block}, {@code trapdoor_block}, {@code fence_gate_block}, {@code button_block}
 *       (with {@code face_attached_horizontal_directional_block}) and {@code pressure_plate_block} behaviours with TFC's
 *       properties, the wooden {@code BlockSetType} sounds (TFC's {@code new BlockSetType(name)} defaults), TFC's strengths,
 *       tags, drops and names.</li>
 *   <li>Every block is hitbox-only on one shared vanilla twin ({@link HitboxDemands#WOOD_LOGIC_TWINS}, drawn empty) and
 *       draws TFC's blockstate model of the state as one item display, turned by TFC's {@code x} and {@code y}.</li>
 * </ul>
 */
final class WoodLogicBlocks {
    /** The kinds of {@link HitboxDemands#WOOD_LOGIC_KINDS}, in that order, that this class writes. */
    static final List<String> KINDS = HitboxDemands.WOOD_LOGIC_KINDS;
    /** The block states of one kind of one wood. */
    static final Map<String, Integer> STATES = Map.of("fence_gate", 32, "door", 64, "trapdoor", 64, "button", 24,
        "pressure_plate", 2);

    private static final Map<String, String> KIND_TYPE = Map.of("fence_gate", "FENCE_GATE", "door", "DOOR", "trapdoor",
        "TRAPDOOR", "button", "BUTTON", "pressure_plate", "PRESSURE_PLATE");
    private static final Map<String, String> KIND_CLASS = Map.of("fence_gate", "TFCFenceGateBlock", "door", "TFCDoorBlock",
        "trapdoor", "TFCTrapDoorBlock", "button", "TFCWoodButtonBlock", "pressure_plate", "TFCPressurePlateBlock");
    /** {@code strength(h)} or {@code strength(h, r)} on the registration line of the kind. */
    private static final Pattern STRENGTH = Pattern.compile(
        "^ {8}([A-Z_]+)\\(wood -> new (TFC[A-Za-z]+)\\([^\\n]*?\\.strength\\(([0-9.]+)[fF](?:, ([0-9.]+)[fF])?\\)",
        Pattern.MULTILINE);
    /** TFC's {@code new BlockSetType(name)}: the wooden sounds (vanilla's constructor defaults). */
    private static final Map<String, Map<String, String>> SOUNDS = Map.of(
        "door", sounds("open", "minecraft:block.wooden_door.open", "close", "minecraft:block.wooden_door.close"),
        "trapdoor", sounds("open", "minecraft:block.wooden_trapdoor.open", "close", "minecraft:block.wooden_trapdoor.close"),
        "fence_gate", sounds("open", "minecraft:block.fence_gate.open", "close", "minecraft:block.fence_gate.close"),
        "button", sounds("on", "minecraft:block.wooden_button.click_on", "off", "minecraft:block.wooden_button.click_off"),
        "pressure_plate", sounds("on", "minecraft:block.wooden_pressure_plate.click_on", "off",
            "minecraft:block.wooden_pressure_plate.click_off"));
    /** CraftEngine's {@code button_block} ticks_to_stay_pressed: TFCWoodButtonBlock passes 30 to ButtonBlock. */
    private static final int BUTTON_TICKS = 30;
    /** TFC's door drops only the lower half (mcresources' door loot, renamed). */
    private static final String DOOR_LOOT = "{\"type\":\"minecraft:block\",\"pools\":[{\"name\":\"loot_pool\",\"rolls\":1,"
        + "\"entries\":[{\"type\":\"minecraft:item\",\"name\":\"%1$s\",\"conditions\":[{\"condition\":"
        + "\"minecraft:block_state_property\",\"block\":\"%1$s\",\"properties\":{\"half\":\"lower\"}}]}],\"conditions\":["
        + "{\"condition\":\"minecraft:survives_explosion\"}]}]}";

    private WoodLogicBlocks() {
    }

    private static Map<String, String> sounds(String a, String av, String b, String bv) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put(a, av);
        m.put(b, bv);
        return m;
    }

    /** TFC's strength of each kind: hardness and resistance (one argument means both). */
    static Map<String, WoodFamilies.Strength> strengths(TfcCheckout tfc) {
        String src = tfc.readString(WoodFamilies.SOURCE);
        Map<String, WoodFamilies.Strength> byType = new LinkedHashMap<>();
        Matcher m = STRENGTH.matcher(src);
        while (m.find()) {
            BigDecimal h = new BigDecimal(m.group(3));
            byType.put(m.group(1) + "/" + m.group(2), new WoodFamilies.Strength(h, m.group(4) == null ? h
                : new BigDecimal(m.group(4))));
        }
        Map<String, WoodFamilies.Strength> out = new LinkedHashMap<>();
        for (String kind : KINDS) {
            WoodFamilies.Strength st = byType.get(KIND_TYPE.get(kind) + "/" + KIND_CLASS.get(kind));
            if (st == null) {
                throw new DatagenException(WoodFamilies.SOURCE + ": Wood.BlockType." + KIND_TYPE.get(kind) + " is no longer a "
                    + KIND_CLASS.get(kind) + " with .strength(h[, r])");
            }
            out.put(kind, st);
        }
        return out;
    }

    /** One block of one wood: {@code states} adds its entry to {@code blocks} and its items to {@code items}. */
    static int write(TfcCheckout tfc, OutputTree out, String wood, String kind, WoodFamilies.Strength st,
            Map<String, List<String>> tags, Map<String, Object> blocks, Map<String, Object> items) {
        String path = WoodFamilies.blockPath(kind, wood);
        String id = Json.TO_NS + ":" + path;
        Map<String, Object> settings = WoodFamilies.settings(st, tags.getOrDefault(Json.FROM_NS + ":" + path, List.of()), path);
        if (kind.equals("button")) {
            settings.remove("instrument"); // TFC's button properties are ExtendedProperties.of(): no instrument, no map colour
            settings.remove("map_color");
        }
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("settings", settings);
        entry.put("behavior", behavior(kind));
        entry.put("loot", kind.equals("door") ? doorLoot(tfc, path) : WoodFamilies.loot(tfc, kind, path));
        entry.put("states", states(tfc, out, wood, kind, path, items));
        blocks.put(id, entry);
        WoodFamilies.copyModel(tfc, out, "item/" + path);
        items.put(id, item(kind, id, path));
        return STATES.get(kind);
    }

    private static Map<String, Object> item(String kind, String id, String path) {
        if (!kind.equals("door")) {
            return CeBlockStates.call("tfp:item/block_item", Map.of("item_lang_key", WoodFamilies.langKey(path)));
        }
        // a door is two blocks: CraftEngine's double_high_block_item places both halves (tfp:item/block_item is a block_item)
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("material", "paper");
        item.put("data", Map.of("item_name", "<!i><lang:" + WoodFamilies.langKey(path) + ">"));
        item.put("model", Json.TO_NS + ":item/" + path);
        Map<String, Object> behavior = new LinkedHashMap<>();
        behavior.put("type", "double_high_block_item");
        behavior.put("block", id);
        item.put("behavior", behavior);
        return item;
    }

    private static List<Object> behavior(String kind) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("type", switch (kind) {
            case "door" -> "door_block";
            case "trapdoor" -> "trapdoor_block";
            case "fence_gate" -> "fence_gate_block";
            case "button" -> "button_block";
            default -> "pressure_plate_block";
        });
        if (kind.equals("button")) {
            b.put("ticks_to_stay_pressed", BUTTON_TICKS);
        }
        b.put("sounds", SOUNDS.get(kind));
        if (kind.equals("button")) {
            return List.of(Map.of("type", "face_attached_horizontal_directional_block"), b);
        }
        return List.of(b);
    }

    /** TFC's door table: the plain self drop of the lower half only. */
    private static Map<String, Object> doorLoot(TfcCheckout tfc, String path) {
        String src = WoodFamilies.LOOT + path + ".json";
        String self = Json.TO_NS + ":" + path;
        JsonElement table = Json.rename(Json.parse(tfc.readString(src), src));
        if (!table.equals(Json.parse(String.format(DOOR_LOOT, self), "WoodLogicBlocks.DOOR_LOOT"))) {
            throw new DatagenException(src + " is no longer the self drop of the lower half");
        }
        Map<String, Object> lower = new LinkedHashMap<>();
        lower.put("type", "match_block_property");
        lower.put("properties", Map.of("half", "lower"));
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("type", "item");
        entry.put("item", self);
        Map<String, Object> pool = new LinkedHashMap<>();
        pool.put("rolls", 1);
        pool.put("conditions", List.of(Map.of("type", "survives_explosion"), lower));
        pool.put("entries", List.of(entry));
        return Map.of("pools", List.of(pool));
    }

    // ---- states ---------------------------------------------------------------------------------------------------------

    private static Map<String, Object> prop(String type, List<String> values, String dflt) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("type", type);
        if (values != null) {
            p.put("values", values);
        }
        p.put("default", dflt);
        return p;
    }

    private static Map<String, Object> bool() {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("type", "boolean");
        p.put("default", false);
        return p;
    }

    private static Map<String, Object> properties(String kind) {
        Map<String, Object> props = new LinkedHashMap<>();
        switch (kind) {
            case "door" -> {
                props.put("facing", prop("horizontal_direction", List.of("north", "east", "south", "west"), "north"));
                props.put("half", prop("double_block_half", List.of("upper", "lower"), "lower"));
                props.put("hinge", prop("hinge", List.of("left", "right"), "left"));
                props.put("open", bool());
                props.put("powered", bool());
            }
            case "trapdoor" -> {
                props.put("facing", prop("horizontal_direction", List.of("north", "east", "south", "west"), "north"));
                props.put("half", prop("single_block_half", List.of("bottom", "top"), "bottom"));
                props.put("open", bool());
                props.put("powered", bool());
                props.put("waterlogged", bool());
            }
            case "fence_gate" -> {
                props.put("facing", prop("horizontal_direction", List.of("north", "east", "south", "west"), "north"));
                props.put("in_wall", bool());
                props.put("open", bool());
                props.put("powered", bool());
            }
            case "button" -> {
                props.put("face", prop("anchor_type", List.of("floor", "wall", "ceiling"), "floor"));
                props.put("facing", prop("horizontal_direction", List.of("north", "east", "south", "west"), "north"));
                props.put("powered", bool());
            }
            default -> props.put("powered", bool());
        }
        return props;
    }

    /** One TFC blockstate variant: its model (without the namespace folder) and the turns. */
    record Variant(String model, int x, int y) {
    }

    /** TFC's variants by their (partial) property conditions. */
    static Map<Map<String, String>, Variant> variants(TfcCheckout tfc, String path) {
        String src = WoodFamilies.BLOCKSTATES + path + ".json";
        JsonObject bs = Json.rename(Json.parse(tfc.readString(src), src)).getAsJsonObject();
        JsonObject vs = bs.getAsJsonObject("variants");
        if (vs == null) {
            throw new DatagenException(src + " has no variants");
        }
        Map<Map<String, String>, Variant> out = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> e : vs.entrySet()) {
            if (!e.getValue().isJsonObject()) {
                throw new DatagenException(src + ": " + e.getKey() + " is not one model");
            }
            JsonObject v = e.getValue().getAsJsonObject();
            Map<String, String> cond = new LinkedHashMap<>();
            for (String kv : e.getKey().split(",")) {
                String[] p = kv.split("=");
                cond.put(p[0], p[1]);
            }
            String model = v.get("model").getAsString();
            if (!model.startsWith(Json.TO_NS + ":block/")) {
                throw new DatagenException(src + ": " + e.getKey() + " names a model outside our namespace: " + model);
            }
            out.put(cond, new Variant(model, v.has("x") ? v.get("x").getAsInt() : 0, v.has("y") ? v.get("y").getAsInt() : 0));
        }
        return out;
    }

    /** The one variant whose conditions all hold in {@code state}; fails on none or several. */
    static Variant match(Map<Map<String, String>, Variant> variants, Map<String, String> state, String path) {
        Variant found = null;
        for (Map.Entry<Map<String, String>, Variant> e : variants.entrySet()) {
            if (state.entrySet().containsAll(e.getKey().entrySet())) {
                if (found != null) {
                    throw new DatagenException(path + ": " + state + " matches several variants");
                }
                found = e.getValue();
            }
        }
        if (found == null) {
            throw new DatagenException(path + ": " + state + " matches no variant");
        }
        return found;
    }

    private static Map<String, Object> states(TfcCheckout tfc, OutputTree out, String wood, String kind, String path,
            Map<String, Object> items) {
        String id = Json.TO_NS + ":" + path;
        Map<Map<String, String>, Variant> variants = variants(tfc, path);
        Map<String, String> partOf = new LinkedHashMap<>(); // TFC model id -> display item id
        for (Variant v : variants.values()) {
            if (!partOf.containsKey(v.model())) {
                String name = v.model().substring(v.model().lastIndexOf('/') + 1);
                String part = name.equals(wood) ? "base" : name.startsWith(wood + "_") ? name.substring(wood.length() + 1) : null;
                if (part == null) {
                    throw new DatagenException(path + ": model " + v.model() + " is not of wood " + wood);
                }
                WoodFamilies.copyModel(tfc, out, v.model().substring(Json.TO_NS.length() + 1));
                String item = WoodFamilies.renderItem(kind, wood, part);
                items.put(item, CeBlockStates.call("tfp:item/render", Map.of("render_model", v.model())));
                partOf.put(v.model(), item);
            }
        }
        Map<String, Object> appearances = new LinkedHashMap<>();
        Map<String, Object> variantsOut = new LinkedHashMap<>();
        TreeSet<String> usedModels = new TreeSet<>();
        for (String[] s : HitboxDemands.woodLogicStates(kind)) {
            String name = s[0];
            Map<String, String> state = new LinkedHashMap<>();
            for (String kv : name.split(",")) {
                String[] p = kv.split("=");
                state.put(p[0], p[1]);
            }
            Variant v = match(variants, state, path);
            usedModels.add(v.model());
            HitboxAllocation.Assignment a = HitboxAllocation.agreed(id, name);
            String want = HitboxDemands.twin(id) + "[" + name + "]";
            if (a.mode() != HitboxAllocation.Mode.HITBOX_ONLY || !a.state().equals(want) || a.hideTarget() != null) {
                throw new DatagenException(id + "#" + name + ": carrier " + a.state() + " (" + a.mode() + "), expected " + want
                    + " drawn empty");
            }
            Map<String, Object> renderer = new LinkedHashMap<>();
            renderer.put("item", partOf.get(v.model()));
            renderer.put("rotation", rotation(v.x(), v.y()));
            Map<String, Object> app = new LinkedHashMap<>();
            app.put("state", a.state());
            app.put("transparent", true);
            app.put("entity_renderer", List.of(renderer));
            appearances.put(name, app);
            Map<String, Object> variant = new LinkedHashMap<>();
            variant.put("appearance", name);
            if ("true".equals(state.get("waterlogged"))) {
                variant.put("settings", Map.of("fluid_state", "water"));
            }
            variantsOut.put(name, variant);
        }
        if (!usedModels.equals(new TreeSet<>(partOf.keySet()))) {
            throw new DatagenException(path + ": the blockstate names models no state draws");
        }
        Map<String, Object> states = new LinkedHashMap<>();
        states.put("properties", properties(kind));
        states.put("appearances", appearances);
        states.put("variants", variantsOut);
        return states;
    }

    // ---- the display rotation -------------------------------------------------------------------------------------------

    /**
     * The display turn of TFC's blockstate {@code x} and {@code y}. Vanilla turns a model by {@code Ry(-y) * Rx(-x)} (x
     * first); the display adds the half turn every block-model item display needs ({@code Ry(180)}, WoodFamilies POST_ROTATION,
     * so a model with no turn is {@code 0,180,0} and y becomes {@code 180 - y}). With an x turn the Euler string is
     * ambiguous, so the quaternion {@code x,y,z,w} is written.
     */
    static String rotation(int x, int y) {
        int yaw = Math.floorMod(180 - y, 360);
        if (x == 0) {
            return "0," + yaw + ",0";
        }
        double ay = Math.toRadians(yaw) / 2;
        double ax = Math.toRadians(-x) / 2;
        // q = qy * qx (Hamilton product, JOML order: qx applied first)
        double qxx = Math.sin(ax);
        double qxw = Math.cos(ax);
        double qyy = Math.sin(ay);
        double qyw = Math.cos(ay);
        double rx = qyw * qxx;
        double ry = qyy * qxw;
        double rz = -qyy * qxx;
        double rw = qyw * qxw;
        return num(rx) + "," + num(ry) + "," + num(rz) + "," + num(rw);
    }

    private static String num(double v) {
        BigDecimal b = BigDecimal.valueOf(v).setScale(6, java.math.RoundingMode.HALF_UP).stripTrailingZeros();
        return b.signum() == 0 ? "0" : b.toPlainString();
    }

    /** The properties' names of a kind, for tests. */
    static List<String> propertyNames(String kind) {
        return new ArrayList<>(properties(kind).keySet());
    }
}
