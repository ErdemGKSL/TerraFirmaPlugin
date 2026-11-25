/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/TFCBlocks.java @ c3b4d9536 (PLACED_ITEM: registerNoItem, instabreak,
 *       SoundType.STEM, noOcclusion),
 *       src/main/java/net/dries007/tfc/common/blocks/devices/PlacedItemBlock.java @ c3b4d9536 (ITEM_0..3, SHAPE_0..3, empty
 *       collision, InventoryRemoveBehavior.DROP),
 *       src/main/java/net/dries007/tfc/util/SelfTests.java @ c3b4d9536 (expectedNoLootTableBlocks holds TFCBlocks.PLACED_ITEM)
 */
package dev.tfp.datagen.placed;

import dev.tfp.datagen.DatagenException;
import dev.tfp.datagen.OutputTree;
import dev.tfp.datagen.TfcCheckout;
import dev.tfp.datagen.Yaml;
import dev.tfp.datagen.device.DeviceAppearance;
import dev.tfp.datagen.device.DeviceBlockSpec;
import dev.tfp.datagen.device.DeviceYaml;
import dev.tfp.datagen.hitbox.HitboxAllocation;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PAR-POT-01: the placed item block as the CraftEngine block {@code tfp:placed_item}
 * ({@code craftengine/tfp/configuration/placed/placed_item.yml}).
 *
 * <ul>
 *   <li>No state property: TFC's {@code ITEM_0..3} (which quadrants the block below supports) are checked by the plugin
 *       when an item is placed (QuadrantSupport) and the items, rotations and the large flag live in the device record,
 *       so one appearance serves all 15 occupancies and the large item.</li>
 *   <li>Hitbox (PAR-INFRA-13 owner rule, {@code hitbox/class-overrides.json} class {@code placed_item}): the heavy weighted
 *       pressure plate at power 0 (14 x 1 x 14 outline, no collision, as TFC's empty collision), drawn empty
 *       ({@code transparent}); the items are the plugin's item displays (PaperPlacedItemEffects). No per-item
 *       carrier.</li>
 *   <li>The {@code tfp:device} behaviour and TFC's settings: {@code instabreak} (hardness and resistance 0),
 *       {@code SoundType.STEM}, {@code noOcclusion}, in no {@code minecraft:mineable} tag; no block item
 *       ({@code registerNoItem}) and no loot: the device framework drops the four slots ({@code DROP}).</li>
 * </ul>
 * The carrier is taken from the selector ({@link HitboxAllocation#agreed}) by {@link #run}; the demand for
 * {@link #DEMAND_KEY}, the {@code Datagen} run line and the GeneratedCarrierTest pin are files outside PAR-POT-01's
 * ownership and come with follow-up PAR-POT-13 (until then {@link #run} is not called).
 */
public final class PlacedItemBlock {
    public static final String ID = "tfp:placed_item";
    public static final String APPEARANCE = "base";
    /** The TFC outline row the demand is served for: every quadrant supported (TFC's default state). */
    public static final String DEMAND_KEY = "tfc:placed_item[item_0=true,item_1=true,item_2=true,item_3=true]";
    public static final String OUTPUT = "craftengine/tfp/configuration/placed/placed_item.yml";
    static final String SOURCE = "src/main/java/net/dries007/tfc/common/blocks/devices/PlacedItemBlock.java";
    static final String BLOCKS = "src/main/java/net/dries007/tfc/common/blocks/TFCBlocks.java";
    static final String MINEABLE = TfcCheckout.GENERATED + "data/minecraft/tags/block/mineable/";
    static final String LOOT = TfcCheckout.MAIN + "data/tfc/loot_table/blocks/placed_item.json";
    /** Vanilla {@code SoundType.STEM}: {@code new SoundType(1.0F, 1.0F, STEM_BREAK, STEM_STEP, ...)}. */
    static final List<String> SOUND_KEYS = List.of("break", "step", "place", "hit", "fall");
    private static final Pattern REGISTER = Pattern.compile(
        "registerNoItem\\(\"placed_item\", \\(\\) -> new PlacedItemBlock\\(ExtendedProperties\\.of\\(\\)\\.instabreak\\(\\)"
            + "\\.sound\\(SoundType\\.(\\w+)\\)\\.noOcclusion\\(\\)\\.blockEntity\\(TFCBlockEntities\\.PLACED_ITEM\\)\\)\\);");

    private PlacedItemBlock() {
    }

    /** True when the checkout has the placed item block (the small test fixtures do not). */
    public static boolean available(TfcCheckout tfc) {
        return tfc.exists(SOURCE);
    }

    /** Writes the entry with the selector's carrier. Returns the number of server states (1). */
    public static int run(TfcCheckout tfc, OutputTree out) {
        checkSource(tfc);
        HitboxAllocation.Assignment a = HitboxAllocation.agreed(ID, APPEARANCE);
        if (a.mode() != HitboxAllocation.Mode.HITBOX_ONLY) {
            throw new DatagenException("the placed item carrier is " + a.mode() + ", PlacedItemBlock writes a transparent carrier");
        }
        DeviceBlockSpec spec = spec(a.state(), a.hideTarget());
        out.put(OUTPUT, Yaml.write(DeviceYaml.HEADER, yaml(spec)));
        return (int) spec.stateCount();
    }

    /** TFC's block: the four quadrant shapes, empty collision, the slots dropped on removal, no tool tag, no loot. */
    static void checkSource(TfcCheckout tfc) {
        String src = tfc.readString(SOURCE);
        for (String needle : List.of("private static final VoxelShape SHAPE_0 = box(8, 0, 8, 16, 1, 16);",
                "private static final VoxelShape SHAPE_1 = box(0, 0, 8, 8, 1, 16);",
                "private static final VoxelShape SHAPE_2 = box(8, 0, 0, 16, 1, 8);",
                "private static final VoxelShape SHAPE_3 = box(0, 0, 0, 8, 1, 8);",
                "super(properties, InventoryRemoveBehavior.DROP);",
                "builder.add(ITEM_0, ITEM_1, ITEM_2, ITEM_3);")) {
            if (!src.contains(needle)) {
                throw new DatagenException(SOURCE + " no longer contains \"" + needle + "\": re-check the placed item entry");
            }
        }
        for (String t : List.of("axe", "hoe", "pickaxe", "shovel")) {
            if (tfc.readString(MINEABLE + t + ".json").contains("\"tfc:placed_item\"")) {
                throw new DatagenException("tfc:placed_item is now in minecraft:mineable/" + t + ": add the tool to PlacedItemBlock");
            }
        }
        if (tfc.exists(LOOT)) {
            throw new DatagenException(LOOT + " now exists: PlacedItemBlock writes no loot");
        }
        Matcher m = REGISTER.matcher(tfc.readString(BLOCKS));
        if (!m.find()) {
            throw new DatagenException(BLOCKS + ": the placed_item registration no longer matches " + REGISTER.pattern());
        }
        if (!m.group(1).equals("STEM")) {
            throw new DatagenException(BLOCKS + ": the placed_item sound is SoundType." + m.group(1) + ", PlacedItemBlock writes stem");
        }
    }

    /** One hitbox-only appearance on {@code carrier}; {@code hideTarget} (or null) is where real carrier blocks are sent. */
    static DeviceBlockSpec spec(String carrier, String hideTarget) {
        DeviceBlockSpec.Builder b = DeviceBlockSpec.builder(ID)
            .appearance(DeviceAppearance.vanilla(APPEARANCE, DeviceAppearance.Carrier.state(carrier)))
            .everyState(s -> APPEARANCE, null)
            .deviceBehaviour();
        if (hideTarget != null) {
            b.stateMapping(carrier, hideTarget);
        }
        return b.build();
    }

    /** The literal entry with TFC's settings first and the appearance drawn empty. */
    @SuppressWarnings("unchecked")
    static Map<String, Object> yaml(DeviceBlockSpec spec) {
        Map<String, Object> root = DeviceYaml.literal(spec);
        Map<String, Object> blocks = (Map<String, Object>) root.get("blocks");
        Map<String, Object> literal = (Map<String, Object>) blocks.get(spec.id());
        Map<String, Object> states = (Map<String, Object>) literal.get("states");
        Map<String, Object> app = (Map<String, Object>) ((Map<String, Object>) states.get("appearances")).get(APPEARANCE);
        app.put("transparent", true);
        // A block without properties is single-state in CraftEngine: one appearance, no variant.
        states.remove("properties");
        states.remove("variants");
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("settings", settings());
        block.putAll(literal);
        blocks.put(spec.id(), block);
        return root;
    }

    /** TFC {@code instabreak()}: strength 0; {@code SoundType.STEM}; {@code noOcclusion}. */
    static Map<String, Object> settings() {
        Map<String, Object> sounds = new LinkedHashMap<>();
        SOUND_KEYS.forEach(k -> sounds.put(k, "minecraft:block.stem." + k));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("hardness", BigDecimal.ZERO);
        m.put("resistance", BigDecimal.ZERO);
        m.put("sounds", sounds);
        m.put("can_occlude", false);
        m.put("is_suffocating", false);
        m.put("is_view_blocking", false);
        return m;
    }
}
