/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/rock/RockCategory.java @ c3b4d9536 (the stone ItemType factors),
 * file: src/main/java/net/dries007/tfc/common/items/ToolItem.java @ c3b4d9536 (mineBlock durability rule)
 */
package dev.tfp.core.tool;

/**
 * The stone tool types TFC makes from rock heads. Each type carries the TFC attack factors and whether it digs. A digging type mines
 * the blocks in its {@link #mineableTag() mineable tag}. Javelins are swords, so they do not dig.
 *
 * <p>{@link #consumesDurabilityTag()} separates two durability rules. TFC's own tool classes (hammer, hoe, knife) call
 * {@code ToolItem.willConsumeDurability}, which also honours the {@code consumes_tool_durability} block tag. The stone axe and shovel
 * are vanilla {@code AxeItem} and {@code ShovelItem}, so they only lose durability when the block's destroy speed is non-zero.
 */
public enum ToolType {
    AXE("minecraft:mineable/axe", 1.5f, -3.2f, true, false),
    HAMMER("tfc:mineable/hammer", 1.0f, -3.0f, true, true),
    HOE("tfc:mineable/hoe", 0.5f, -3.0f, true, true),
    JAVELIN(null, 0.7f, -2.2f, false, false),
    KNIFE("tfc:mineable/knife", 0.6f, -2.0f, true, true),
    SHOVEL("minecraft:mineable/shovel", 0.875f, -3.0f, true, false);

    private final String mineableTag;
    private final float attackDamageFactor;
    private final float attackSpeed;
    private final boolean digs;
    private final boolean consumesDurabilityTag;

    ToolType(String mineableTag, float attackDamageFactor, float attackSpeed, boolean digs, boolean consumesDurabilityTag) {
        this.mineableTag = mineableTag;
        this.attackDamageFactor = attackDamageFactor;
        this.attackSpeed = attackSpeed;
        this.digs = digs;
        this.consumesDurabilityTag = consumesDurabilityTag;
    }

    /** The block tag this type mines, or {@code null} for a type that does not dig. */
    public String mineableTag() {
        return mineableTag;
    }

    /** The factor TFC multiplies by the tier attack bonus ({@code ToolItem.productAttributes}). */
    public float attackDamageFactor() {
        return attackDamageFactor;
    }

    /** The attack speed modifier TFC passes to {@code ToolItem.productAttributes}. */
    public float attackSpeed() {
        return attackSpeed;
    }

    /** Whether this type mines blocks (speed rule and mining durability). */
    public boolean digs() {
        return digs;
    }

    /** Whether mining durability also honours {@code consumes_tool_durability} (TFC {@code ToolItem} subclasses). */
    public boolean consumesDurabilityTag() {
        return consumesDurabilityTag;
    }
}
