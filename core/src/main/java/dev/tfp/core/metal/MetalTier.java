/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/TFCTiers.java and TFCArmorMaterials.java @ c3b4d9536
 */
package dev.tfp.core.metal;

/**
 * The tool and armor tiers of the metals, the numbers TFC gives them in {@code TFCTiers} and {@code TFCArmorMaterials}.
 * Only the metals with a tool tier are here; {@link Metal#tier()} is the tier level, which is 0 for every other metal.
 *
 * <p>Field names follow the Minecraft tool and armor terms: {@code durability} is TFC's {@code uses}, and the armor
 * {@code reduction} values are TFC's armor reductions. The tool's {@code attackDamage} is TFC's {@code damage} bonus. The
 * {@code incorrectForTag} is the block tag of blocks the tier cannot mine. All values are as TFC defines them.
 */
public enum MetalTier {
    COPPER(1, 600, 5.25f, 3.25f, 8, "minecraft:incorrect_for_stone_tool",
        new Armor(160, 200, 215, 150,
            1, 3, 4, 1,
            9, 0.0f, 0.0f)),
    BISMUTH_BRONZE(2, 1200, 6.65f, 4.0f, 10, "minecraft:incorrect_for_iron_tool",
        new Armor(250, 288, 311, 240,
            1, 4, 4, 1,
            9, 0.0f, 0.0f)),
    BLACK_BRONZE(2, 1460, 6.0f, 4.25f, 10, "minecraft:incorrect_for_iron_tool",
        new Armor(285, 340, 336, 262,
            1, 4, 4, 1,
            9, 0.0f, 0.0f)),
    BRONZE(2, 1300, 7.3f, 4.0f, 13, "minecraft:incorrect_for_iron_tool",
        new Armor(270, 315, 323, 251,
            1, 4, 4, 1,
            9, 0.0f, 0.0f)),
    WROUGHT_IRON(3, 2200, 8.0f, 4.75f, 12, "minecraft:incorrect_for_iron_tool",
        new Armor(429, 495, 528, 370,
            1, 4, 5, 2,
            12, 0.0f, 0.0f)),
    STEEL(4, 3300, 9.5f, 5.75f, 12, "minecraft:incorrect_for_diamond_tool",
        new Armor(520, 600, 640, 440,
            2, 5, 6, 2,
            12, 1.0f, 0.0f)),
    BLACK_STEEL(5, 4200, 11.0f, 7.0f, 17, "minecraft:incorrect_for_netherite_tool",
        new Armor(650, 750, 800, 550,
            2, 5, 6, 2,
            17, 2.0f, 0.05f)),
    BLUE_STEEL(6, 6500, 12.0f, 9.0f, 22, "minecraft:incorrect_for_netherite_tool",
        new Armor(860, 960, 1088, 748,
            3, 6, 8, 3,
            23, 3.0f, 0.1f)),
    RED_STEEL(6, 6500, 12.0f, 9.0f, 22, "minecraft:incorrect_for_netherite_tool",
        new Armor(884, 1020, 1010, 715,
            3, 6, 8, 3,
            23, 3.0f, 0.1f)),
    ;

    /** The tier level, the 0-based order of the tiers. */
    private final int level;
    /** The number of uses of a tool of this tier (TFC {@code uses}). */
    private final int durability;
    /** The mining speed of a tool of this tier. */
    private final float speed;
    /** The attack damage bonus of a tool of this tier (TFC {@code damage}). */
    private final float attackDamage;
    /** The enchantability of the tool of this tier. */
    private final int enchantmentValue;
    /** The block tag of the blocks a tool of this tier cannot mine, as a Minecraft tag id. */
    private final String incorrectForTag;
    /** The armor of this tier. */
    private final Armor armor;

    MetalTier(int level, int durability, float speed, float attackDamage, int enchantmentValue, String incorrectForTag, Armor armor) {
        this.level = level;
        this.durability = durability;
        this.speed = speed;
        this.attackDamage = attackDamage;
        this.enchantmentValue = enchantmentValue;
        this.incorrectForTag = incorrectForTag;
        this.armor = armor;
    }

    /** The tier level, used as the {@code break_power} of the tiers. */
    public int level() {
        return level;
    }

    /** The durability (uses) of a tool of this tier. */
    public int durability() {
        return durability;
    }

    /** The mining speed of a tool of this tier. */
    public float speed() {
        return speed;
    }

    /** The attack damage bonus of a tool of this tier. */
    public float attackDamage() {
        return attackDamage;
    }

    /** The enchantability of a tool of this tier. */
    public int enchantmentValue() {
        return enchantmentValue;
    }

    /** The Minecraft block tag id of the blocks this tier cannot mine. */
    public String incorrectForTag() {
        return incorrectForTag;
    }

    /** The armor of this tier. */
    public Armor armor() {
        return armor;
    }

    /**
     * The armor of a tier, with TFC's {@code ArmorMaterial} values. Durability and reduction are per slot, in the order
     * feet, legs, chest, head.
     *
     * @param feetDurability      the durability of the boots
     * @param legsDurability      the durability of the greaves
     * @param chestDurability     the durability of the chestplate
     * @param headDurability      the durability of the helmet
     * @param feetReduction       the armor reduction of the boots
     * @param legsReduction       the armor reduction of the greaves
     * @param chestReduction      the armor reduction of the chestplate
     * @param headReduction       the armor reduction of the helmet
     * @param enchantmentValue    the enchantability of the armor
     * @param toughness           the armor toughness
     * @param knockbackResistance the knockback resistance
     */
    public record Armor(
        int feetDurability,
        int legsDurability,
        int chestDurability,
        int headDurability,
        int feetReduction,
        int legsReduction,
        int chestReduction,
        int headReduction,
        int enchantmentValue,
        float toughness,
        float knockbackResistance
    ) {}
}
