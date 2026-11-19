/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/TFCTiers.java @ c3b4d9536 (the metal tool tiers' levels),
 * file: src/main/java/net/dries007/tfc/util/Metal.java @ c3b4d9536 (BlockType.ANVIL: tier = metal.toolTier().level()),
 * file: src/main/java/net/dries007/tfc/common/blocks/rock/RockAnvilBlock.java @ c3b4d9536 (getTier: 0)
 */
package dev.tfp.core.anvil;

import java.util.Map;
import java.util.OptionalInt;

/**
 * The tier of an anvil block (TFC {@code Tiered.getTier}), by block id: a rock anvil is tier 0, a metal anvil its metal's
 * tool tier level (copper 1 ... red steel 6). Recipes need an anvil of at least their tier. Pure; any thread.
 */
public final class AnvilTiers {
    public static final String ROCK_PREFIX = "tfp:rock/anvil/";
    public static final String METAL_PREFIX = "tfp:metal/anvil/";
    /** The metals that have an anvil block, with TFC's tool tier level. */
    public static final Map<String, Integer> METAL_LEVELS = Map.of(
        "copper", 1,
        "bronze", 2,
        "bismuth_bronze", 2,
        "black_bronze", 2,
        "wrought_iron", 3,
        "steel", 4,
        "black_steel", 5,
        "blue_steel", 6,
        "red_steel", 6);

    private AnvilTiers() {
    }

    /** The tier of the anvil block {@code blockId}, empty when it is no anvil. */
    public static OptionalInt tier(String blockId) {
        if (blockId.startsWith(ROCK_PREFIX) && blockId.length() > ROCK_PREFIX.length()) {
            return OptionalInt.of(0);
        }
        if (blockId.startsWith(METAL_PREFIX)) {
            Integer level = METAL_LEVELS.get(blockId.substring(METAL_PREFIX.length()));
            return level == null ? OptionalInt.empty() : OptionalInt.of(level);
        }
        return OptionalInt.empty();
    }
}
