/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/items/ToolItem.java @ c3b4d9536
 */
package dev.tfp.core.tool;

/**
 * The stats of one metal tool, derived by {@link ToolRules}. Immutable.
 *
 * @param durability            the uses of the tool, 0 when it is not damageable (TFC {@code uses} of the tier)
 * @param miningSpeed           the mining speed on the blocks the tool mines (the tier speed for a digger, 1 otherwise)
 * @param attackDamageModifier  the attack damage modifier of the item, {@code attackDamageFactor * tier damage}
 * @param attackSpeedModifier   the attack speed modifier of the item (TFC {@code attackSpeed})
 * @param breakPower            the tier level, used as the {@code break_power} of the tool
 */
public record ToolStats(
    int durability,
    float miningSpeed,
    float attackDamageModifier,
    float attackSpeedModifier,
    int breakPower
) {}
