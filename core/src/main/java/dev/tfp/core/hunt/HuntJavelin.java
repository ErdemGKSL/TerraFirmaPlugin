/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/items/JavelinItem.java @ c3b4d9536, src/main/java/net/dries007/tfc/common/entities/misc/ThrownJavelin.java
 */
package dev.tfp.core.hunt;

import dev.tfp.core.tool.ToolTier;

/**
 * The javelin rules of TFC: when a javelin can be thrown, what a throw costs and what the thrown javelin deals.
 *
 * <p>TFC has no break chance for javelins. A throw costs one point of durability (vanilla {@code hurtAndBreak(1)}), and a javelin
 * whose damage is one short of its maximum refuses to start a throw, so a throw never breaks the stack. The thrown damage is
 * {@code 1.5 * tier attack damage bonus}, or 8 when the thrown stack is not a javelin.
 */
public final class HuntJavelin {

    /** The held ticks a throw needs before the javelin is released (TFC {@code JavelinItem.releaseUsing}, {@code i >= 10}). */
    public static final int MIN_CHARGE_TICKS = 10;

    /** The damage of a thrown stack that is not a javelin (TFC {@code ThrownJavelin.getItemAttackDamage}, the trident value). */
    public static final float NOT_A_JAVELIN_DAMAGE = 8f;

    /** The durability one throw costs. */
    public static final int DURABILITY_PER_THROW = 1;

    private HuntJavelin() {
    }

    /**
     * Whether a javelin can start a throw, TFC {@code JavelinItem.use}: the stack must not be at {@code maxDamage - 1} or above.
     *
     * @param damage    the stack's damage
     * @param maxDamage the stack's durability
     */
    public static boolean canStartThrow(int damage, int maxDamage) {
        return damage < maxDamage - 1;
    }

    /**
     * Whether a held javelin is released as a throw, given the ticks it was held: TFC needs at least {@link #MIN_CHARGE_TICKS}.
     */
    public static boolean releasesThrow(int ticksHeld) {
        return ticksHeld >= MIN_CHARGE_TICKS;
    }

    /**
     * Whether a throw breaks the stack. Always false for a throw that {@link #canStartThrow} allows.
     *
     * @param damage    the stack's damage before the throw
     * @param maxDamage the stack's durability
     */
    public static boolean breaksOnThrow(int damage, int maxDamage) {
        return damage + DURABILITY_PER_THROW >= maxDamage;
    }

    /**
     * The damage a thrown javelin deals (TFC {@code JavelinItem.getThrownDamage}).
     *
     * @param attackDamageBonus the tier's attack damage bonus, {@link ToolTier#attackDamageBonus()} for a stone javelin
     */
    public static float thrownDamage(float attackDamageBonus) {
        return 1.5f * attackDamageBonus;
    }

    /** The damage a javelin of a stone tier deals when thrown. */
    public static float thrownDamage(ToolTier tier) {
        return thrownDamage(tier.attackDamageBonus());
    }
}
