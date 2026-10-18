/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/TFCTiers.java @ c3b4d9536 (the IGNEOUS_EXTRUSIVE, IGNEOUS_INTRUSIVE, SEDIMENTARY and
 * METAMORPHIC rows)
 */
package dev.tfp.core.tool;

/**
 * The four stone tool tiers, one per rock category. Stone tools are level 0 and ported as TFC's "Stone ~ Vanilla Wood" tier, so every
 * one is incorrect for {@link IncorrectTag#WOODEN}. The metal tiers are not here: they belong to M4 and reuse the same rules.
 */
public enum ToolTier {
    IGNEOUS_EXTRUSIVE(0, 70, 4.7f, 2.0f, 5, IncorrectTag.WOODEN),
    IGNEOUS_INTRUSIVE(0, 60, 4.7f, 2.0f, 5, IncorrectTag.WOODEN),
    SEDIMENTARY(0, 50, 4.0f, 2.0f, 5, IncorrectTag.WOODEN),
    METAMORPHIC(0, 55, 4.35f, 2.0f, 5, IncorrectTag.WOODEN);

    private final int level;
    private final int uses;
    private final float speed;
    private final float attackDamageBonus;
    private final int enchantmentValue;
    private final IncorrectTag incorrectTag;

    ToolTier(int level, int uses, float speed, float attackDamageBonus, int enchantmentValue, IncorrectTag incorrectTag) {
        this.level = level;
        this.uses = uses;
        this.speed = speed;
        this.attackDamageBonus = attackDamageBonus;
        this.enchantmentValue = enchantmentValue;
        this.incorrectTag = incorrectTag;
    }

    /** TFC's {@code LevelTier.level()}. */
    public int level() {
        return level;
    }

    /** The durability: the damage at which the tool breaks. */
    public int uses() {
        return uses;
    }

    /** The mining speed on blocks the tool type digs. */
    public float speed() {
        return speed;
    }

    /** The {@code getAttackDamageBonus()} value that {@link ToolRules#attackDamage} multiplies. */
    public float attackDamageBonus() {
        return attackDamageBonus;
    }

    public int enchantmentValue() {
        return enchantmentValue;
    }

    /** The incorrect-for tag this tier is listed under. */
    public IncorrectTag incorrectTag() {
        return incorrectTag;
    }
}
