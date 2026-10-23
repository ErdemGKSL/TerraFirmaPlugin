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
 *       src/main/resources/assets/tfc/models/block/charcoal_forge/*.json @ c3b4d9536 (the models, copied by AssetPipeline),
 *       src/main/java/net/dries007/tfc/common/blocks/TFCBlocks.java @ c3b4d9536 (strength, sound, light of both),
 *       src/main/resources/data/tfc/loot_table/blocks/charcoal_forge.json and bellows.json @ c3b4d9536 (the drops)
 */
package dev.tfp.datagen;

import dev.tfp.datagen.device.DeviceAppearance;
import dev.tfp.datagen.device.DeviceBlockSpec;
import dev.tfp.datagen.device.DeviceLoot;
import dev.tfp.datagen.device.DeviceProperty;
import dev.tfp.datagen.device.DeviceSpecs;
import dev.tfp.datagen.device.DeviceYaml;
import dev.tfp.datagen.hitbox.HitboxAllocation;
import dev.tfp.datagen.hitbox.HitboxDemands;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
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
 * the hitbox carrier the selector picks ({@link #forgeCarrier()}, PAR-INFRA-13: {@code snow[layers=7]}, a 14 px outline
 * like TFC, but vanilla snow collides one layer lower, 12 px), whose block model is TFC's cold model {@code heat_0}: one vanilla state carries one model
 * only, so the eight levels cannot each have a block model. Levels 1 to 7 add an {@code entity_renderer} item display
 * of TFC's model for that level (an item {@code tfp:render/charcoal_forge/heat_N}), scaled by {@link #OVERLAY_SCALE}
 * over the block model, lit with TFC's light level ({@code heat * 2}) as block light. Each level also emits TFC's
 * light ({@code luminance: heat * 2}). Real vanilla instances of the carrier are sent as the selector's hide target
 * ({@code snow[layers=8]}, {@code block_state_mappings}), so natural snow never looks like a forge. The bellows keeps a
 * placeholder appearance: the vanilla look of its selected carrier, one per facing.
 *
 * <p><b>Spec.</b> Both entries are {@link DeviceBlockSpec}s ({@link DeviceSpecs#charcoalForge}, {@link DeviceSpecs#facing})
 * written by {@link DeviceYaml#writeLiteral} (PAR-INFRA-19), with TFC's settings ({@link DeviceBlockSpec.Settings#fromTfc}:
 * the forge 0.2/0.2, {@code tfp:block.charcoal.*} sounds, shovel; the bellows 3.0/3.0, wood sounds, axe) and TFC's loot
 * ({@link DeviceLoot#fromTfc}: the forge 7 {@code minecraft:charcoal}, the bellows itself).
 *
 * <p>The outputs are {@code craftengine/tfp/configuration/charcoal_forge.yml} and {@code bellows.yml}, so CraftEngine
 * loads them: each has the block with its settings and its loot; the bellows also has the item that
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
    /** The TFC block names, whose settings ({@code TFCBlocks}, mineable tags) and loot tables the specs read. */
    static final String FORGE_BLOCK = "charcoal_forge";
    static final String BELLOWS_BLOCK = "bellows";
    static final String FORGE_ID = Json.TO_NS + ":" + FORGE_BLOCK;
    static final String BELLOWS_ID = Json.TO_NS + ":" + BELLOWS_BLOCK;

    static final List<String> FACINGS = List.of("north", "east", "south", "west");
    /** TFC: {@code CharcoalForgeBlock.getShape} returns {@code CharcoalPileBlock.SHAPE_BY_LAYER[7]}. */
    static final String BLOCKS_SOURCE = "src/main/java/net/dries007/tfc/common/blocks/TFCBlocks.java";
    static final String PILE_SOURCE = "src/main/java/net/dries007/tfc/common/blocks/CharcoalPileBlock.java";
    /** The lit overlay is drawn this much larger than the block model it covers, so the two never z-fight. */
    static final java.math.BigDecimal OVERLAY_SCALE = new java.math.BigDecimal("1.002");
    /** The prefix of the display-only items an entity_renderer draws for the lit levels. */
    static final String RENDER_ITEM_PREFIX = Json.TO_NS + ":render/charcoal_forge/";
    private static final Pattern MODEL_REF = Pattern.compile(Json.FROM_NS + ":(block/charcoal_forge/[a-z0-9_]+)");
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
        if (!HitboxDemands.FORGE_MODEL.equals(Json.TO_NS + ":" + models.get(heat.min()))) {
            throw new DatagenException("the forge's cold model is tfp:" + models.get(heat.min()) + ", the hitbox demand says "
                + HitboxDemands.FORGE_MODEL);
        }
        for (String model : models.values()) {
            if (!out.contains(AssetPipeline.RP + "models/" + model + ".json")) {
                throw new DatagenException("forge model tfp:" + model + " was not copied by AssetPipeline");
            }
        }
        out.put(FORGE_OUTPUT, DeviceYaml.writeLiteral(forgeSpec(tfc, heat, models)));
        checkBellows(tfc);
        requireItemModel(out, BELLOWS_BLOCK);
        out.put(BELLOWS_OUTPUT, DeviceYaml.writeLiteral(bellowsSpec(tfc)));
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

    /** TFC's forge shape must still be the 7-layer (14 px) charcoal pile shape the hitbox shape table was read with. */
    static void checkShape(TfcCheckout tfc) {
        String src = require(tfc, FORGE_SOURCE);
        if (!src.contains("return CharcoalPileBlock.SHAPE_BY_LAYER[7];") || src.contains("getCollisionShape")) {
            throw new DatagenException(FORGE_SOURCE + ": the shape is no longer CharcoalPileBlock.SHAPE_BY_LAYER[7] "
                + "for selection and collision; regenerate hitbox/tfc-shapes.json");
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

    /**
     * The forge's spec ({@link DeviceSpecs#charcoalForge}): one appearance per heat level on the selector's carrier with the
     * cold model, lit levels with an item display of their own TFC model, light {@link #lightLevel}, the carrier's hide
     * mapping, and TFC's settings and loot ({@code strength(0.2F)}, {@code TFCSounds.CHARCOAL}, shovel; 7 charcoal).
     * {@code models}: level to model path without namespace ({@link #forgeModels}).
     */
    static DeviceBlockSpec forgeSpec(TfcCheckout tfc, HeatRange heat, Map<Integer, String> models) {
        Map<Integer, String> ids = new TreeMap<>();
        for (int level = heat.min(); level <= heat.max(); level++) {
            String model = models.get(level);
            if (model == null) {
                throw new DatagenException(FORGE_BLOCKSTATE + " has no model for heat_level=" + level);
            }
            ids.put(level, Json.TO_NS + ":" + model);
        }
        HitboxAllocation.Assignment carrier = forgeCarrier();
        Map<String, String> mappings = carrier.hideTarget() == null ? Map.of() : Map.of(carrier.state(), carrier.hideTarget());
        return DeviceSpecs.charcoalForge(FORGE_ID, DeviceProperty.range(heat.name(), heat.min(), heat.max(), heat.min()), ids,
            DeviceAppearance.Carrier.state(carrier.state()), OVERLAY_SCALE, RENDER_ITEM_PREFIX, ForgeDevices::lightLevel,
            DeviceBlockSpec.Settings.fromTfc(tfc, FORGE_BLOCK), DeviceLoot.fromTfc(tfc, FORGE_BLOCK), mappings);
    }

    /** TFC: {@code lightLevel(state -> state.getValue(CharcoalForgeBlock.HEAT) * 2)}. */
    static int lightLevel(int heat) {
        return heat * 2;
    }

    /**
     * The bellows' spec ({@link DeviceSpecs#facing}): one appearance per facing on the selector's carrier (vanilla look,
     * placeholder), its block item, and TFC's settings and loot ({@code strength(3.0f)}, {@code SoundType.WOOD}, axe;
     * itself).
     */
    static DeviceBlockSpec bellowsSpec(TfcCheckout tfc) {
        return DeviceSpecs.facing(BELLOWS_ID, DeviceProperty.names("facing", FACINGS, "north"),
            f -> bellowsCarrier(f).state(), "block.tfp." + BELLOWS_BLOCK,
            DeviceBlockSpec.Settings.fromTfc(tfc, BELLOWS_BLOCK), DeviceLoot.fromTfc(tfc, BELLOWS_BLOCK));
    }

    /** The forge's carrier (every heat level): explicit state with our model, and its hide target. */
    static HitboxAllocation.Assignment forgeCarrier() {
        HitboxAllocation.Assignment a = HitboxAllocation.agreed(FORGE_ID, HitboxDemands.FORGE_APPEARANCE);
        if (a.mode() != HitboxAllocation.Mode.MODEL) {
            throw new DatagenException("the charcoal forge carrier is " + a.mode() + ", ForgeDevices writes an explicit model");
        }
        return a;
    }

    /** The bellows' carrier for one facing (its vanilla look; placeholder). */
    static HitboxAllocation.Assignment bellowsCarrier(String facing) {
        HitboxAllocation.Assignment a = HitboxAllocation.agreed(BELLOWS_ID, facing);
        if (a.mode() != HitboxAllocation.Mode.VANILLA || a.hideTarget() != null) {
            throw new DatagenException("the bellows carrier is " + a.mode() + " with hide " + a.hideTarget()
                + ", ForgeDevices writes a plain vanilla look");
        }
        return a;
    }

    /** The ids of both entries, for the report. */
    static List<String> ids() {
        return new ArrayList<>(List.of(FORGE_ID, BELLOWS_ID));
    }
}
