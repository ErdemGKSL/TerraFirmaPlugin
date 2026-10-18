/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/items/ToolItem.java, src/main/java/net/dries007/tfc/util/Metal.java,
 * and src/main/java/net/dries007/tfc/common/recipes/HeatingRecipe.java @ c3b4d9536
 */
package dev.tfp.core.tool;

import dev.tfp.core.metal.Metal;
import dev.tfp.core.metal.MetalTier;
import java.util.Optional;

/**
 * Derives the stats of a metal tool from its tier and kind, as TFC's {@code ToolItem.productAttributes}, the tool classes and
 * the tier's uses. Pure functions, no state. The stone tool rules are in {@link ToolRules}.
 */
public final class MetalToolRules {
    /** The mining speed of an item that is not a digger, the vanilla default. */
    public static final float DEFAULT_MINING_SPEED = 1.0f;

    private MetalToolRules() {}

    /**
     * The stats of a tool of {@code kind} made from {@code tier}.
     *
     * @param tier the tool tier of the metal
     * @param kind the tool kind
     * @return the derived stats
     */
    public static ToolStats stats(MetalTier tier, ToolKind kind) {
        int durability = kind.damageable() ? tier.durability() : 0;
        float miningSpeed = kind.digger() ? tier.speed() : DEFAULT_MINING_SPEED;
        float attackDamage = kind.hasAttributeModifiers() ? kind.attackDamageFactor() * tier.attackDamage() : 0f;
        float attackSpeed = kind.hasAttributeModifiers() ? kind.attackSpeed() : 0f;
        return new ToolStats(durability, miningSpeed, attackDamage, attackSpeed, tier.level());
    }

    /**
     * The stats of a tool of {@code kind} made from {@code metal}, or empty when the metal has no tool tier.
     *
     * @param metal the metal
     * @param kind  the tool kind
     */
    public static Optional<ToolStats> stats(Metal metal, ToolKind kind) {
        return metal.toolTier().map(tier -> stats(tier, kind));
    }

    /**
     * The fluid amount a heating recipe yields from a damaged item, when the recipe uses durability: the output scales with
     * the remaining durability, {@code floor(amount * (1 - damage / maxDamage))}. Returns {@code amount} unchanged when the
     * item has no max damage (TFC {@code HeatingRecipe.assembleFluid}).
     *
     * @param amount    the output fluid amount of the recipe
     * @param damage    the damage value of the input item
     * @param maxDamage the max damage of the input item
     */
    public static int fluidAfterDamage(int amount, int damage, int maxDamage) {
        if (maxDamage <= 0) {
            return amount;
        }
        float remaining = 1 - (float) damage / maxDamage;
        return (int) Math.floor(amount * remaining);
    }
}
