/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/TFCBlocks.java @ c3b4d9536 (SCRAPING: registerNoItem,
 *       strength(0.2F), SoundType.STEM, noOcclusion),
 *       src/main/java/net/dries007/tfc/common/blocks/devices/ScrapingBlock.java @ c3b4d9536 (SHAPE, WAXED, InventoryRemoveBehavior.DROP),
 *       src/main/java/net/dries007/tfc/util/SelfTests.java @ c3b4d9536 (expectedNoLootTableBlocks holds TFCBlocks.SCRAPING)
 */
package dev.tfp.datagen.scraping;

import dev.tfp.datagen.DatagenException;
import dev.tfp.datagen.OutputTree;
import dev.tfp.datagen.TfcCheckout;
import dev.tfp.datagen.Yaml;
import dev.tfp.datagen.device.DeviceAppearance;
import dev.tfp.datagen.device.DeviceBlockSpec;
import dev.tfp.datagen.device.DeviceYaml;
import dev.tfp.datagen.hitbox.HitboxAllocation;
import dev.tfp.datagen.hitbox.HitboxDemands;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PAR-PROC-29: the scraping surface as the CraftEngine block {@code tfp:scraping}
 * ({@code craftengine/tfp/configuration/scraping/scraping.yml}).
 *
 * <ul>
 *   <li>No state property: TFC's {@code WAXED} is kept in the plugin's block entity record (PAR-PROC-05, it changes
 *       neither the shape nor the block model), so one appearance serves the block.</li>
 *   <li>The appearance is the hitbox carrier the selector picks for TFC's 16 x 1 x 16 plate ({@code tfc:scraping}),
 *       drawn empty ({@code transparent}): the surface is the item display of {@code PaperScrapingEffects} (TFC's
 *       {@code ScrapingBlockModel}), drawn per cell from {@link ScrapingArt}'s tiles.</li>
 *   <li>The {@code tfp:device} behaviour (the plugin's {@code ScrapingDevice}) and TFC's settings: strength 0.2,
 *       {@code SoundType.STEM} (a (1.0, 1.0) vanilla type, so plain ids), {@code noOcclusion} (not occluding,
 *       suffocating or view-blocking), in no {@code minecraft:mineable} tag.</li>
 *   <li>No block item ({@code registerNoItem}) and no loot ({@code SelfTests.expectedNoLootTableBlocks}): the device
 *       framework drops the slot's item when the surface goes.</li>
 * </ul>
 * The entry is written through {@link DeviceBlockSpec} and {@link DeviceYaml#literal}, not the device template: the
 * template always names one {@code minecraft:mineable/<tool>} tag, which TFC's scraping block does not have, and
 * {@link DeviceAppearance} refuses a transparent appearance without {@code entity_renderer} elements, so the
 * {@code transparent} flag and the settings are added to the literal entry here.
 * <p>
 * Not run by {@code Datagen} yet: the selector's carrier today is {@code minecraft:lily_pad} (carrier-policy says
 * {@code absent}), but our worldgen places real lily pads as the floating-plant placeholder ({@code PlantBlocks}), and a
 * transparent appearance draws every one of them empty. PAR-INFRA-42 corrects the policy; then PAR-PROC-29 adds the run
 * line back on the new carrier.
 */
public final class ScrapingBlock {
    public static final String ID = HitboxDemands.SCRAPING_ID;
    public static final String APPEARANCE = HitboxDemands.SCRAPING_APPEARANCE;
    public static final String OUTPUT = "craftengine/tfp/configuration/scraping/scraping.yml";
    static final String SOURCE = "src/main/java/net/dries007/tfc/common/blocks/devices/ScrapingBlock.java";
    static final String BLOCKS = "src/main/java/net/dries007/tfc/common/blocks/TFCBlocks.java";
    static final String MINEABLE = TfcCheckout.GENERATED + "data/minecraft/tags/block/mineable/";
    static final String LOOT = TfcCheckout.MAIN + "data/tfc/loot_table/blocks/scraping.json";
    /** Vanilla {@code SoundType.STEM}: {@code new SoundType(1.0F, 1.0F, STEM_BREAK, STEM_STEP, ...)}. */
    static final List<String> SOUND_KEYS = List.of("break", "step", "place", "hit", "fall");
    private static final Pattern REGISTER = Pattern.compile(
        "registerNoItem\\(\"scraping\", \\(\\) -> new ScrapingBlock\\(ExtendedProperties\\.of\\(\\)\\.strength\\(([0-9.]+)F\\)"
            + "\\.sound\\(SoundType\\.(\\w+)\\)\\.noOcclusion\\(\\)\\.blockEntity\\(TFCBlockEntities\\.SCRAPING\\)\\)\\);");

    private ScrapingBlock() {
    }

    /** True when the checkout has the scraping block (the small test fixtures do not). */
    public static boolean available(TfcCheckout tfc) {
        return tfc.exists(SOURCE);
    }

    /** Writes the entry. Returns the number of server states (1). */
    public static int run(TfcCheckout tfc, OutputTree out) {
        checkSource(tfc);
        String strength = strength(tfc);
        DeviceBlockSpec spec = spec();
        out.put(OUTPUT, Yaml.write(DeviceYaml.HEADER, yaml(spec, strength)));
        return (int) spec.stateCount();
    }

    /** TFC's block: the 1 px shape, the only property WAXED, the slot dropped on removal, no tool tag, no loot table. */
    static void checkSource(TfcCheckout tfc) {
        String src = tfc.readString(SOURCE);
        for (String needle : List.of("private static final VoxelShape SHAPE = box(0.0D, 0.0D, 0.0D, 16.0D, 1.0D, 16.0D);",
                "super(properties, InventoryRemoveBehavior.DROP);",
                "super.createBlockStateDefinition(builder.add(WAXED));")) {
            if (!src.contains(needle)) {
                throw new DatagenException(SOURCE + " no longer contains \"" + needle + "\": re-check the scraping entry");
            }
        }
        for (String t : List.of("axe", "hoe", "pickaxe", "shovel")) {
            if (tfc.readString(MINEABLE + t + ".json").contains("\"tfc:scraping\"")) {
                throw new DatagenException("tfc:scraping is now in minecraft:mineable/" + t + ": add the tool to ScrapingBlock");
            }
        }
        if (tfc.exists(LOOT)) {
            throw new DatagenException(LOOT + " now exists: ScrapingBlock writes no loot");
        }
    }

    /** TFC's {@code strength(h)} (hardness = resistance); the sound must still be STEM. */
    static String strength(TfcCheckout tfc) {
        Matcher m = REGISTER.matcher(tfc.readString(BLOCKS));
        if (!m.find()) {
            throw new DatagenException(BLOCKS + ": the scraping registration no longer matches " + REGISTER.pattern());
        }
        if (!m.group(2).equals("STEM")) {
            throw new DatagenException(BLOCKS + ": the scraping sound is SoundType." + m.group(2) + ", ScrapingBlock writes stem");
        }
        return m.group(1);
    }

    /** The carrier the selector agreed on; it must be drawn empty (the look is the plugin's display). */
    static HitboxAllocation.Assignment carrier() {
        HitboxAllocation.Assignment a = HitboxAllocation.agreed(ID, APPEARANCE);
        if (a.mode() != HitboxAllocation.Mode.HITBOX_ONLY) {
            throw new DatagenException("the scraping carrier is " + a.mode() + ", ScrapingBlock writes a transparent carrier");
        }
        return a;
    }

    static DeviceBlockSpec spec() {
        HitboxAllocation.Assignment a = carrier();
        DeviceBlockSpec.Builder b = DeviceBlockSpec.builder(ID)
            .appearance(DeviceAppearance.vanilla(APPEARANCE, DeviceAppearance.Carrier.state(a.state())))
            .everyState(s -> APPEARANCE, null)
            .deviceBehaviour();
        if (a.hideTarget() != null) {
            b.stateMapping(a.state(), a.hideTarget());
        }
        return b.build();
    }

    /** The literal entry with TFC's settings first and the appearance drawn empty. */
    @SuppressWarnings("unchecked")
    static Map<String, Object> yaml(DeviceBlockSpec spec, String strength) {
        Map<String, Object> root = DeviceYaml.literal(spec);
        Map<String, Object> blocks = (Map<String, Object>) root.get("blocks");
        Map<String, Object> literal = (Map<String, Object>) blocks.get(spec.id());
        Map<String, Object> states = (Map<String, Object>) literal.get("states");
        Map<String, Object> app = (Map<String, Object>) ((Map<String, Object>) states.get("appearances")).get(APPEARANCE);
        app.put("transparent", true);
        // A block without properties is single-state in CraftEngine: one appearance, no variant (an empty variant key
        // would not parse as a state).
        states.remove("properties");
        states.remove("variants");
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("settings", settings(strength));
        block.putAll(literal);
        blocks.put(spec.id(), block);
        return root;
    }

    static Map<String, Object> settings(String strength) {
        Map<String, Object> sounds = new LinkedHashMap<>();
        SOUND_KEYS.forEach(k -> sounds.put(k, "minecraft:block.stem." + k));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("hardness", new BigDecimal(strength));
        m.put("resistance", new BigDecimal(strength));
        m.put("sounds", sounds);
        m.put("can_occlude", false);
        m.put("is_suffocating", false);
        m.put("is_view_blocking", false);
        return m;
    }
}
