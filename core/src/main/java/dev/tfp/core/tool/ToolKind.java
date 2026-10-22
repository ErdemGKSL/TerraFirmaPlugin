/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/Metal.java @ c3b4d9536 (ItemType)
 */
package dev.tfp.core.tool;

import java.util.Optional;

/**
 * The metal tool kinds of TFC, the tool entries of {@code Metal.ItemType} with the numbers their item classes are built with.
 * Each kind says how its stats derive from the metal's {@link dev.tfp.core.metal.MetalTier}: whether it has attribute
 * modifiers (attack damage and attack speed), whether its class mines blocks at the tier's speed (a vanilla {@code DiggerItem}),
 * and whether it is damageable.
 *
 * <p>The {@code headPart} is the metal part the tool is assembled from (TFC's {@code *_HEAD} or {@code *_BLADE} item, a mold
 * item), or empty when the kind has no metal head: the shears, fishing rod, shield and tuyere are made from other parts.
 *
 * <p>The attack damage factor and attack speed are the arguments of TFC's {@code tool(metal, factor, speed)}. The
 * {@code SHEARS}, {@code FISHING_ROD}, {@code SHIELD} and {@code TUYERE} kinds have no attribute modifiers.
 */
public enum ToolKind {
    PICKAXE("PICKAXE_HEAD", 0.75f, -2.8f, true, true, true),
    PROPICK("PROPICK_HEAD", 0.5f, -2.8f, true, true, true),
    AXE("AXE_HEAD", 1.5f, -3.1f, true, true, true),
    SHOVEL("SHOVEL_HEAD", 0.875f, -3.0f, true, true, true),
    HOE("HOE_HEAD", 0.5f, -2.0f, true, true, true),
    CHISEL("CHISEL_HEAD", 0.27f, 1.5f, true, true, true),
    HAMMER("HAMMER_HEAD", 1f, -3f, true, true, true),
    SAW("SAW_BLADE", 0.5f, -3f, true, true, true),
    JAVELIN("JAVELIN_HEAD", 0.7f, -2.6f, true, false, true),
    SWORD("SWORD_BLADE", 1f, -2.4f, true, false, true),
    MACE("MACE_HEAD", 1.3f, -3.4f, true, false, true),
    KNIFE("KNIFE_BLADE", 0.6f, -2.0f, true, true, true),
    SCYTHE("SCYTHE_BLADE", 0.7f, -3.2f, true, true, true),
    SHEARS(null, 0f, 0f, false, false, true),
    FISHING_ROD(null, 0f, 0f, false, false, true),
    SHIELD(null, 0f, 0f, false, false, true),
    TUYERE(null, 0f, 0f, false, false, false),
    ;

    private final String headPart;
    private final float attackDamageFactor;
    private final float attackSpeed;
    private final boolean attributes;
    private final boolean digger;
    private final boolean damageable;

    ToolKind(String headPart, float attackDamageFactor, float attackSpeed, boolean attributes, boolean digger, boolean damageable) {
        this.headPart = headPart;
        this.attackDamageFactor = attackDamageFactor;
        this.attackSpeed = attackSpeed;
        this.attributes = attributes;
        this.digger = digger;
        this.damageable = damageable;
    }

    /** The metal head or blade item this tool is built from, such as {@code PICKAXE_HEAD}, or empty for the kinds with none. */
    public Optional<String> headPart() {
        return Optional.ofNullable(headPart);
    }

    /** The factor of the tier's attack damage bonus (TFC {@code attackDamageFactor}). Zero when there are no attributes. */
    public float attackDamageFactor() {
        return attackDamageFactor;
    }

    /** The attack speed modifier (TFC {@code attackSpeed}). Zero when there are no attributes. */
    public float attackSpeed() {
        return attackSpeed;
    }

    /** Whether this kind has attribute modifiers (attack damage and attack speed) on its item. */
    public boolean hasAttributeModifiers() {
        return attributes;
    }

    /** Whether this kind mines blocks at the tier's speed, as a vanilla {@code DiggerItem} does. Others mine at speed 1. */
    public boolean digger() {
        return digger;
    }

    /** Whether this kind takes durability damage. The tuyere is a tiered item with no durability. */
    public boolean damageable() {
        return damageable;
    }
}
