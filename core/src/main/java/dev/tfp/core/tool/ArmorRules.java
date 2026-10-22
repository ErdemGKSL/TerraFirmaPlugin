/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/TFCArmorMaterials.java @ c3b4d9536 and src/main/java/net/dries007/tfc/util/Metal.java
 */
package dev.tfp.core.tool;

import dev.tfp.core.metal.MetalTier;

/**
 * The per-piece durability and defense of a metal armor set. Pure functions over {@link MetalTier.Armor}.
 */
public final class ArmorRules {
    private ArmorRules() {}

    /**
     * The durability of one piece (TFC {@code Metal.armorDurability}).
     *
     * @param armor the armor of the tier
     * @param slot  the piece
     */
    public static int durability(MetalTier.Armor armor, ArmorSlot slot) {
        return switch (slot) {
            case HEAD -> armor.headDurability();
            case CHEST, BODY -> armor.chestDurability();
            case LEGS -> armor.legsDurability();
            case FEET -> armor.feetDurability();
        };
    }

    /**
     * The defense (armor points) of one piece, the reduction TFC gives its {@code ArmorItem.Type}.
     *
     * @param armor the armor of the tier
     * @param slot  the piece
     */
    public static int defense(MetalTier.Armor armor, ArmorSlot slot) {
        return switch (slot) {
            case HEAD -> armor.headReduction();
            case CHEST, BODY -> armor.chestReduction();
            case LEGS -> armor.legsReduction();
            case FEET -> armor.feetReduction();
        };
    }

    /**
     * The defense of a full set of the four worn pieces (head, chest, legs, feet). The horse armor is not worn.
     *
     * @param armor the armor of the tier
     */
    public static int totalDefense(MetalTier.Armor armor) {
        return defense(armor, ArmorSlot.HEAD) + defense(armor, ArmorSlot.CHEST)
            + defense(armor, ArmorSlot.LEGS) + defense(armor, ArmorSlot.FEET);
    }
}
