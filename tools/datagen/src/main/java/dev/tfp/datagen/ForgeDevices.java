/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/CharcoalForgeBlock.java @ c3b4d9536 (the block state),
 *       src/main/java/net/dries007/tfc/common/blocks/devices/BellowsBlock.java @ c3b4d9536 (the block state),
 *       src/main/resources/assets/tfc/blockstates/charcoal_forge.json and bellows.json @ c3b4d9536 (the variants),
 *       src/main/java/net/dries007/tfc/common/blocks/CharcoalPileBlock.java @ c3b4d9536 (SHAPE_BY_LAYER, the forge shape),
 *       src/main/resources/assets/tfc/models/block/charcoal_forge/*.json @ c3b4d9536 (the models, copied by AssetPipeline)
 */
package dev.tfp.datagen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * M4-FORGE-01 (block data slice): the charcoal forge and the bellows as CraftEngine block entries,
 * {@code tfp:charcoal_forge} and {@code tfp:bellows}.
 *
 * <ul>
 *   <li><b>Charcoal forge:</b> one state property, {@code heat_level} (TFC {@code HEAT_LEVEL}, an integer 0 to 7, default 0),
 *       8 server states. TFC's blockstate has one variant per level, each with a model of its own
 *       ({@code charcoal_forge/heat_0} to {@code heat_7}).</li>
 *   <li><b>Bellows:</b> one state property, {@code facing} (the four horizontal directions, default north), 4 server
 *       states, one model rotated by the facing.</li>
 * </ul>
 * Both entries are read from TFC's block classes (the property, its range and its default) and checked against the TFC
 * blockstates; a class or blockstate that no longer matches is an error.
 *
 * <p><b>Forge look.</b> TFC draws every heat level with its own model ({@code charcoal_forge/heat_N}, parent
 * {@code template_forge}): one 16×14×16 box, the sides {@code charcoal_pile}, the top the per-level texture
 * {@code devices/charcoal_forge/N} (level 0 a still charcoal top, 1 to 7 animated glowing strips). The shape is
 * {@code CharcoalPileBlock.SHAPE_BY_LAYER[7]} (14 px), used for selection and collision. Here every level is drawn on
 * the vanilla state {@link #FORGE_VISUAL} ({@code snow[layers=7]}: a 14 px outline like TFC, but vanilla snow collides
 * one layer lower, 12 px), whose block model is TFC's cold model {@code heat_0}: one vanilla state carries one model
 * only, so the eight levels cannot each have a block model. Levels 1 to 7 add an {@code entity_renderer} item display
 * of TFC's model for that level (an item {@code tfp:render/charcoal_forge/heat_N}), scaled by {@link #OVERLAY_SCALE}
 * over the block model, lit with TFC's light level ({@code heat * 2}) as block light. Each level also emits TFC's
 * light ({@code luminance: heat * 2}). Real vanilla {@code snow[layers=7]} is sent as {@link #SNOW_HIDE_TARGET}
 * ({@code block_state_mappings}), so player-stacked snow never looks like a forge. The bellows keeps a placeholder
 * appearance (a vanilla smoker).
 *
 * <p>The outputs are {@code craftengine/tfp/configuration/charcoal_forge.yml} and {@code bellows.yml}, so CraftEngine
 * loads them: each has the block with its settings and its loot (the block itself); the bellows also has the item that
 * places it (with the TFC item model), the forge none (TFC makes it by lighting a charcoal pile; its only items are
 * the display-only render items of its lit levels). The behaviour (the fuel, the multiblock check, the temperature, the block entity) is not in these
 * entries: it is Bukkit event glue in the plugin.
 */
final class ForgeDevices {
    static final String FORGE_SOURCE = "src/main/java/net/dries007/tfc/common/blocks/devices/CharcoalForgeBlock.java";
    static final String BELLOWS_SOURCE = "src/main/java/net/dries007/tfc/common/blocks/devices/BellowsBlock.java";
    static final String PROPERTIES = "src/main/java/net/dries007/tfc/common/blocks/TFCBlockStateProperties.java";
    static final String FORGE_BLOCKSTATE = TfcCheckout.ASSETS + "blockstates/charcoal_forge.json";
    static final String BELLOWS_BLOCKSTATE = TfcCheckout.ASSETS + "blockstates/bellows.json";
    static final String FORGE_OUTPUT = AssetPipeline.CONFIG + "charcoal_forge.yml";
    static final String BELLOWS_OUTPUT = AssetPipeline.CONFIG + "bellows.yml";
    /**
     * The device block items whose TFC item models {@link AssetPipeline} copies. The charcoal forge has none: in TFC it is
     * made by lighting a charcoal pile, it is never placed from an item.
     */
    static final List<String> DEVICE_ITEMS = List.of("bellows");
    static final String FORGE_ID = Json.TO_NS + ":charcoal_forge";
    static final String BELLOWS_ID = Json.TO_NS + ":bellows";

    static final List<String> FACINGS = List.of("north", "east", "south", "west");
    /** TFC: {@code CharcoalForgeBlock.getShape} returns {@code CharcoalPileBlock.SHAPE_BY_LAYER[7]}. */
    static final String BLOCKS_SOURCE = "src/main/java/net/dries007/tfc/common/blocks/TFCBlocks.java";
    static final String PILE_SOURCE = "src/main/java/net/dries007/tfc/common/blocks/CharcoalPileBlock.java";
    /** The visual (hitbox) state of every forge level: vanilla snow's outline is 2 px per layer, 7 layers = 14 px. */
    static final String FORGE_VISUAL = "minecraft:snow[layers=7]";
    /** Where real vanilla snow with 7 layers is sent (a pure vanilla state no custom block uses; one hop). */
    static final String SNOW_HIDE_TARGET = "minecraft:snow[layers=8]";
    /** The lit overlay is drawn this much larger than the block model it covers, so the two never z-fight. */
    static final java.math.BigDecimal OVERLAY_SCALE = new java.math.BigDecimal("1.002");
    /** The prefix of the display-only items an entity_renderer draws for the lit levels. */
    static final String RENDER_ITEM_PREFIX = Json.TO_NS + ":render/charcoal_forge/";
    private static final Pattern MODEL_REF = Pattern.compile(Json.FROM_NS + ":(block/charcoal_forge/[a-z0-9_]+)");
    private static final String HEADER = "Generated by tools/datagen from TerraFirmaCraft @ "
        + TfcCheckout.PINNED_COMMIT.substring(0, 9) + ". Do not edit: fix the generator and regenerate.";
    private static final Pattern HEAT_LEVEL =
        Pattern.compile("HEAT_LEVEL = IntegerProperty\\.create\\(\"(\\w+)\", (\\d+), (\\d+)\\)");

    /** The heat levels of the forge and the lowest lit one (level 0 is cold). */
    record HeatRange(String name, int min, int max) {
    }

    private ForgeDevices() {
    }

    /** Writes both entries. Returns the number of server states (forge plus bellows). Fails when TFC's sources differ. */
    static int run(TfcCheckout tfc, OutputTree out) {
        HeatRange heat = heatRange(tfc);
        checkShape(tfc);
        Map<Integer, String> models = forgeModels(tfc);
        for (String model : models.values()) {
            if (!out.contains(AssetPipeline.RP + "models/" + model + ".json")) {
                throw new DatagenException("forge model tfp:" + model + " was not copied by AssetPipeline");
            }
        }
        out.put(FORGE_OUTPUT, Yaml.write(HEADER, forge(heat, models)));
        checkBellows(tfc);
        requireItemModel(out, "bellows");
        out.put(BELLOWS_OUTPUT, Yaml.write(HEADER, bellows()));
        return (heat.max() - heat.min() + 1) + FACINGS.size();
    }

    /** The forge's heat level property and the variants of its blockstate, checked against the class. */
    static HeatRange heatRange(TfcCheckout tfc) {
        String src = require(tfc, FORGE_SOURCE);
        if (!src.contains("IntegerProperty HEAT = TFCBlockStateProperties.HEAT_LEVEL;")
            || !src.contains("builder.add(HEAT)") || !src.contains("setValue(HEAT, 0)")) {
            throw new DatagenException(FORGE_SOURCE + ": the block state is not HEAT = HEAT_LEVEL, default 0, added once");
        }
        Matcher m = HEAT_LEVEL.matcher(require(tfc, PROPERTIES));
        if (!m.find()) {
            throw new DatagenException(PROPERTIES + ": no HEAT_LEVEL = IntegerProperty.create(name, min, max)");
        }
        HeatRange range = new HeatRange(m.group(1), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
        if (range.min() != 0) {
            throw new DatagenException(PROPERTIES + ": HEAT_LEVEL starts at " + range.min() + ", the default 0 would be outside it");
        }
        Set<String> expected = new TreeSet<>();
        for (int level = range.min(); level <= range.max(); level++) {
            expected.add(range.name() + "=" + level);
        }
        checkVariants(tfc, FORGE_BLOCKSTATE, expected);
        return range;
    }

    /** TFC's forge shape must still be the 7-layer (14 px) charcoal pile shape that {@link #FORGE_VISUAL} matches. */
    static void checkShape(TfcCheckout tfc) {
        String src = require(tfc, FORGE_SOURCE);
        if (!src.contains("return CharcoalPileBlock.SHAPE_BY_LAYER[7];") || src.contains("getCollisionShape")) {
            throw new DatagenException(FORGE_SOURCE + ": the shape is no longer CharcoalPileBlock.SHAPE_BY_LAYER[7] "
                + "for selection and collision; re-pick FORGE_VISUAL");
        }
        if (!require(tfc, BLOCKS_SOURCE).contains("lightLevel(state -> state.getValue(CharcoalForgeBlock.HEAT) * 2)")) {
            throw new DatagenException(BLOCKS_SOURCE + ": the charcoal forge light is no longer heat * 2 (update lightLevel)");
        }
        if (!require(tfc, PILE_SOURCE).contains("box(0, 0, 0, 16, 14, 16),")) {
            throw new DatagenException(PILE_SOURCE + ": SHAPE_BY_LAYER[7] is no longer 16x14x16");
        }
    }

    /**
     * The model of each heat level from TFC's blockstate ({@code block/charcoal_forge/heat_N}, no namespace), in level
     * order. AssetPipeline copies them (with their parent and textures); ForgeDevices draws them.
     */
    static Map<Integer, String> forgeModels(TfcCheckout tfc) {
        var variants = Json.parse(require(tfc, FORGE_BLOCKSTATE), FORGE_BLOCKSTATE).getAsJsonObject()
            .getAsJsonObject("variants");
        Map<Integer, String> models = new java.util.TreeMap<>();
        for (String key : variants.keySet()) {
            var v = variants.get(key);
            if (!v.isJsonObject() || !v.getAsJsonObject().has("model") || v.getAsJsonObject().size() != 1) {
                throw new DatagenException(FORGE_BLOCKSTATE + ": variant " + key + " is not one unrotated model");
            }
            Matcher m = MODEL_REF.matcher(v.getAsJsonObject().get("model").getAsString());
            if (!key.startsWith("heat_level=") || !m.matches()) {
                throw new DatagenException(FORGE_BLOCKSTATE + ": variant " + key + " is not heat_level=N -> tfc:block/charcoal_forge/...");
            }
            models.put(Integer.parseInt(key.substring("heat_level=".length())), m.group(1));
        }
        return models;
    }

    private static void checkBellows(TfcCheckout tfc) {
        String src = require(tfc, BELLOWS_SOURCE);
        if (!src.contains("DirectionProperty FACING = HorizontalDirectionalBlock.FACING;")
            || !src.contains("builder.add(FACING)") || !src.contains("setValue(FACING, Direction.NORTH)")) {
            throw new DatagenException(BELLOWS_SOURCE + ": the block state is not the horizontal FACING, default north, added once");
        }
        Set<String> expected = new TreeSet<>();
        FACINGS.forEach(f -> expected.add("facing=" + f));
        checkVariants(tfc, BELLOWS_BLOCKSTATE, expected);
    }

    private static void checkVariants(TfcCheckout tfc, String blockstate, Set<String> expected) {
        String src = blockstate;
        var json = Json.parse(require(tfc, blockstate), src).getAsJsonObject();
        if (!json.has("variants")) {
            throw new DatagenException(blockstate + " has no variants");
        }
        Set<String> actual = new TreeSet<>(json.getAsJsonObject("variants").keySet());
        if (!actual.equals(expected)) {
            throw new DatagenException(blockstate + ": variants " + actual + " differ from the block state " + expected);
        }
    }

    private static void requireItemModel(OutputTree out, String id) {
        if (!out.contains(AssetPipeline.RP + "models/item/" + id + ".json")) {
            throw new DatagenException("CE item tfp:" + id + " has no item model tfp:item/" + id);
        }
    }

    private static String require(TfcCheckout tfc, String path) {
        if (!tfc.exists(path)) {
            throw new DatagenException("TFC checkout has no " + path);
        }
        return tfc.readString(path);
    }

    static Map<String, Object> forge(HeatRange heat, Map<Integer, String> models) {
        Map<String, Object> property = new LinkedHashMap<>();
        property.put("type", "int");
        property.put("range", heat.min() + "~" + heat.max());
        property.put("default", heat.min());
        String cold = Json.TO_NS + ":" + models.get(heat.min());
        Map<String, Object> variants = new LinkedHashMap<>();
        Map<String, Object> appearances = new LinkedHashMap<>();
        Map<String, Object> items = new LinkedHashMap<>();
        for (int level = heat.min(); level <= heat.max(); level++) {
            String model = models.get(level);
            if (model == null) {
                throw new DatagenException(FORGE_BLOCKSTATE + " has no model for heat_level=" + level);
            }
            String name = "heat_" + level;
            Map<String, Object> appearance = new LinkedHashMap<>();
            appearance.put("state", FORGE_VISUAL);
            appearance.put("model", cold); // the same model on every appearance: one vanilla state, one model
            if (level > heat.min()) {
                String item = RENDER_ITEM_PREFIX + name;
                Map<String, Object> brightness = new LinkedHashMap<>();
                brightness.put("block_light", Math.min(15, lightLevel(level)));
                brightness.put("sky_light", 15);
                Map<String, Object> element = new LinkedHashMap<>();
                element.put("item", item);
                element.put("scale", OVERLAY_SCALE);
                element.put("brightness", brightness);
                appearance.put("entity_renderer", List.of(element));
                items.put(item, CeBlockStates.call("tfp:item/render", Map.of("render_model", Json.TO_NS + ":" + model)));
            }
            appearances.put(name, appearance);
            Map<String, Object> variant = new LinkedHashMap<>();
            variant.put("appearance", name);
            variant.put("settings", Map.of("luminance", lightLevel(level)));
            variants.put(heat.name() + "=" + level, variant);
        }
        Map<String, Object> root = entry(FORGE_ID, null, Map.of(heat.name(), property), appearances, variants);
        root.put("items", items);
        root.put("block_state_mappings", Map.of(FORGE_VISUAL, SNOW_HIDE_TARGET));
        return root;
    }

    /** TFC: {@code lightLevel(state -> state.getValue(CharcoalForgeBlock.HEAT) * 2)}. */
    static int lightLevel(int heat) {
        return heat * 2;
    }

    static Map<String, Object> bellows() {
        Map<String, Object> property = new LinkedHashMap<>();
        property.put("type", "string");
        property.put("values", FACINGS);
        property.put("default", "north");
        Map<String, Object> appearances = new LinkedHashMap<>();
        Map<String, Object> variants = new LinkedHashMap<>();
        for (String f : FACINGS) {
            appearances.put(f, Map.of("state", "minecraft:smoker[facing=" + f + ",lit=false]"));
            variants.put("facing=" + f, Map.of("appearance", f));
        }
        return entry(BELLOWS_ID, "bellows", Map.of("facing", property), appearances, variants);
    }

    /** @param itemName the name of the item that places the block, or null when there is none */
    private static Map<String, Object> entry(String id, String itemName, Map<String, Object> properties,
            Map<String, Object> appearances, Map<String, Object> variants) {
        Map<String, Object> states = new LinkedHashMap<>();
        states.put("properties", new LinkedHashMap<>(properties));
        states.put("appearances", appearances);
        states.put("variants", variants);
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("settings", CeBlockStates.call("tfp:settings/device",
            Map.of("device_hardness", new java.math.BigDecimal("1"), "device_resistance", new java.math.BigDecimal("1"))));
        block.put("loot", Map.of("template", "tfp:loot_table/self"));
        block.put("states", states);
        Map<String, Object> blocks = new LinkedHashMap<>();
        blocks.put(id, block);
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("blocks", blocks);
        if (itemName != null) {
            Map<String, Object> items = new LinkedHashMap<>();
            items.put(id, CeBlockStates.call("tfp:item/block_item", Map.of("item_lang_key", "block.tfp." + itemName)));
            root.put("items", items);
        }
        return root;
    }

    /** The ids of both entries, for the report. */
    static List<String> ids() {
        return new ArrayList<>(List.of(FORGE_ID, BELLOWS_ID));
    }
}
