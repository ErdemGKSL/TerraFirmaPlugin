/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/items/JavelinItem.java @ c3b4d9536 (getThrownDamage),
 *       src/main/java/net/dries007/tfc/common/entities/misc/ThrownJavelin.java @ c3b4d9536 (getItemAttackDamage, onHitEntity),
 *       src/main/java/net/dries007/tfc/util/Metal.java @ c3b4d9536 (JAVELIN factor 0.7), RockCategory.java (JAVELIN factor 0.7)
 */
package dev.tfp.core.javelin;

import dev.tfp.core.hunt.HuntJavelin;

/**
 * The thrown damage of a javelin item from its generated attack modifier. Every TFC javelin (rock, obsidian and metal) is
 * {@code ToolItem.productAttributes(tier, 0.7, speed)}, so the {@code attack_damage} modifier of the item is
 * {@code 0.7 x tier attack bonus}; the thrown damage is {@code 1.5 x tier attack bonus} ({@link HuntJavelin#thrownDamage}).
 * The tier bonuses are multiples of 0.25, so the bonus is recovered exactly by rounding to a quarter.
 */
public final class JavelinDamage {
    /** TFC's attack damage factor of every javelin. */
    public static final double ATTACK_FACTOR = 0.7;

    private JavelinDamage() {
    }

    /** The tier attack bonus behind an {@code attack_damage} modifier amount of a javelin. */
    public static float tierBonus(double attackModifierAmount) {
        return (float) (Math.round(attackModifierAmount / ATTACK_FACTOR * 4.0) / 4.0);
    }

    /** The damage a thrown javelin whose item has this {@code attack_damage} modifier amount deals. */
    public static float thrownDamage(double attackModifierAmount) {
        return HuntJavelin.thrownDamage(tierBonus(attackModifierAmount));
    }
}
