/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/TFCTiers.java @ c3b4d9536 (attackDamageBonus of every tier),
 *       src/main/java/net/dries007/tfc/common/items/TFCItems.java @ c3b4d9536 (OBSIDIAN_JAVELIN, ROCK_TOOLS JAVELIN),
 *       src/main/java/net/dries007/tfc/util/Metal.java @ c3b4d9536 (ItemType.JAVELIN: metal.toolTier())
 */
package dev.tfp.core.item.feature;

import dev.tfp.core.tool.ToolTier;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The tool tier attack damage bonus of each TFC javelin, by our item id (PAR-INFRA-50): {@code tfp:stone/javelin/<rock
 * category>} (the four stone tiers of {@link ToolTier}), {@code tfp:obsidian_javelin} (TFC {@code OBSIDIAN}) and
 * {@code tfp:metal/javelin/<metal>} (the metal's tool tier). Immutable, any thread.
 */
public final class JavelinTiers {
    /** TFC {@code TFCTiers}: the attack damage bonus of the obsidian and metal tiers. */
    private static final Map<String, Float> METAL_BONUS = Map.of(
            "copper", 3.25f,
            "bronze", 4.0f,
            "bismuth_bronze", 4.0f,
            "black_bronze", 4.25f,
            "wrought_iron", 4.75f,
            "steel", 5.75f,
            "black_steel", 7.0f,
            "blue_steel", 9.0f,
            "red_steel", 9.0f);
    private static final float OBSIDIAN_BONUS = 2.5f;

    private static final String METAL_PREFIX = "tfp:metal/javelin/";
    private static final String STONE_PREFIX = "tfp:stone/javelin/";
    public static final String OBSIDIAN = "tfp:obsidian_javelin";

    private JavelinTiers() {
    }

    /** The attack damage bonus of the javelin {@code itemId}, or empty when it is not a javelin. */
    public static Optional<Float> attackDamageBonus(String itemId) {
        if (itemId == null) {
            return Optional.empty();
        }
        if (itemId.equals(OBSIDIAN)) {
            return Optional.of(OBSIDIAN_BONUS);
        }
        if (itemId.startsWith(METAL_PREFIX)) {
            return Optional.ofNullable(METAL_BONUS.get(itemId.substring(METAL_PREFIX.length())));
        }
        if (itemId.startsWith(STONE_PREFIX)) {
            String category = itemId.substring(STONE_PREFIX.length());
            for (ToolTier tier : ToolTier.values()) {
                if (tier.name().toLowerCase(Locale.ROOT).equals(category)) {
                    return Optional.of(tier.attackDamageBonus());
                }
            }
        }
        return Optional.empty();
    }

    /** The metals with a javelin. */
    public static java.util.Set<String> metals() {
        return METAL_BONUS.keySet();
    }
}
