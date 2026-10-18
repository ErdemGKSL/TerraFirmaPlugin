/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/items/ToolItem.java @ c3b4d9536 (productAttributes, willConsumeDurability, mineBlock),
 * file: src/main/java/net/dries007/tfc/common/items/JavelinItem.java @ c3b4d9536 (getThrownDamage),
 * file: src/main/java/net/dries007/tfc/common/items/HammerItem.java and TFCHoeItem.java @ c3b4d9536 (which durability rule they use)
 */
package dev.tfp.core.tool;

import java.util.Objects;

/**
 * The stone tool rules as pure functions. None of them takes a random input: TFC's durability loss is 1 or 0, and vanilla's
 * unbreaking is applied by the Paper item hook, not here. The rules are stateless, so calls can run on any thread.
 *
 * <p>Mining follows vanilla's tool model, as TFC uses it. A block that is not mineable by the tool type gives speed 1. Drops need the
 * tool type to dig the block and the tier to be correct for it, but only when the block sets {@code requiresCorrectTool}.
 */
public final class ToolRules {
    private ToolRules() {}

    /** Whether the digging type mines the block. Throws for a type that does not dig, since TFC's javelin is not a mining tool. */
    public static boolean isMineable(ToolType tool, MinedBlock block) {
        requireDigs(tool);
        return block.mineableWith().contains(tool);
    }

    /** The mining speed: the tier speed on mineable blocks and 1 elsewhere. */
    public static float miningSpeed(ToolType tool, ToolTier tier, MinedBlock block) {
        Objects.requireNonNull(tier, "tier");
        return isMineable(tool, block) ? tier.speed() : 1.0f;
    }

    /**
     * Whether mining the block with this tool and tier drops its item. A block that does not require the correct tool always drops.
     * Otherwise the type must dig it and the tier must not be incorrect for it.
     */
    public static boolean correctForDrops(ToolType tool, ToolTier tier, MinedBlock block) {
        Objects.requireNonNull(tier, "tier");
        if (!block.requiresCorrectTool()) return true;
        return isMineable(tool, block) && !block.incorrectFor().contains(tier.incorrectTag());
    }

    /**
     * The durability damage for one block mined. TFC's {@code ToolItem.mineBlock} damages 1 when {@code willConsumeDurability}:
     * the consumes tag or a non-zero destroy speed. The stone axe and shovel use vanilla's rule, a non-zero destroy speed only.
     * Returns 0 or 1.
     */
    public static int durabilityLoss(ToolType tool, MinedBlock block) {
        requireDigs(tool);
        boolean consumes = tool.consumesDurabilityTag()
            ? block.consumesDurabilityTag() || block.destroySpeed() != 0.0f
            : block.destroySpeed() != 0.0f;
        return consumes ? 1 : 0;
    }

    /** The damage after one mined block. */
    public static int damageAfterMining(int damage, ToolType tool, MinedBlock block) {
        return damage + durabilityLoss(tool, block);
    }

    /** Whether the tool breaks at this damage: it breaks once the damage reaches the tier uses. */
    public static boolean isBroken(int damage, ToolTier tier) {
        return damage >= tier.uses();
    }

    /** TFC {@code ToolItem.productAttributes}: the factor times the tier attack bonus. */
    public static float attackDamage(ToolType tool, ToolTier tier) {
        return tool.attackDamageFactor() * tier.attackDamageBonus();
    }

    /** TFC {@code JavelinItem.getThrownDamage}: 1.5 times the tier attack bonus. */
    public static float javelinThrownDamage(ToolTier tier) {
        return 1.5f * tier.attackDamageBonus();
    }

    private static void requireDigs(ToolType tool) {
        if (!tool.digs()) throw new IllegalArgumentException(tool + " does not dig blocks");
    }
}
