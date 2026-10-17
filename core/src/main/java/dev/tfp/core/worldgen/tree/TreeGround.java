/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/generated/resources/data/{tfc,minecraft,c}/tags/block/*.json @ c3b4d9536 (the members of the block tags the tree
 * features test: minecraft:dirt (tfc:tree_grows_on), tfc:bush_plantable_on, tfc:sea_bush_plantable_on, minecraft:sand,
 * c:stones, c:gravels, minecraft:leaves), with src/main/java/net/dries007/tfc/util/EnvironmentHelpers.java
 * (isWorldgenReplaceable) and src/main/java/net/dries007/tfc/common/fluids/FluidHelpers.java (isAirOrEmptyFluid)
 */
package dev.tfp.core.worldgen.tree;

/**
 * What the tree features see at a position: the terrain's block classes and the blocks trees place. Each constant answers the
 * tag tests of TFC's tree code. The soil variant of TFC's dirt, grass and mud is not modelled (the terrain has none yet).
 */
public enum TreeGround {
    AIR,
    /** Fresh water ({@code minecraft:water}, a source block). */
    WATER,
    /** TFC's salt water ({@code tfc:salt_water}, a source block). */
    SALT_WATER,
    GRASS,
    DIRT,
    COARSE_DIRT,
    MUD,
    SAND,
    GRAVEL,
    /** Raw rock and other stone ({@code c:stones}). */
    STONE,
    /** TFC's raw sandstone ({@code tfc:raw_sandstone/<colour>}): solid for the trees, plantable for dry plants (M2-GEN-13). */
    SANDSTONE,
    /** Any other solid block (snow, ice). */
    SOLID,
    /** A log or wood block a tree placed. */
    LOG,
    LEAVES,
    ROOTED_DIRT,
    MUDDY_ROOTS,
    KRUMMHOLZ;

    public boolean isAir() {
        return this == AIR;
    }

    /** {@code FluidHelpers.isAirOrEmptyFluid}: air or a fluid's own source block. */
    public boolean isAirOrEmptyFluid() {
        return this == AIR || this == WATER || this == SALT_WATER;
    }

    /** {@code EnvironmentHelpers.isWorldgenReplaceable}: air, a fluid block, or a single-block plant (none in the terrain). */
    public boolean isWorldgenReplaceable() {
        return isAirOrEmptyFluid();
    }

    /** Whether the fluid here is {@code Fluids.WATER} (fresh water; TFC's salt and river water are other fluids). */
    public boolean isFreshWater() {
        return this == WATER;
    }

    /** Whether the position holds any fluid. */
    public boolean hasFluid() {
        return this == WATER || this == SALT_WATER;
    }

    /** {@code minecraft:leaves}. */
    public boolean isLeaves() {
        return this == LEAVES;
    }

    /** {@code tfc:tree_grows_on} = {@code minecraft:dirt}: TFC's grass, dirt (rooted dirt and duff included), coarse dirt and mud. */
    public boolean treeGrowsOn() {
        return this == GRASS || this == DIRT || this == COARSE_DIRT || this == MUD || this == ROOTED_DIRT;
    }

    /** {@code minecraft:sand}. */
    public boolean isSand() {
        return this == SAND;
    }

    /** {@code c:gravels}. */
    public boolean isGravel() {
        return this == GRAVEL;
    }

    /** {@code c:stones}. */
    public boolean isStone() {
        return this == STONE;
    }

    /** {@code tfc:sea_bush_plantable_on}: {@code minecraft:dirt}, {@code c:gravels}, {@code c:sands}. */
    public boolean seaBushPlantableOn() {
        return treeGrowsOn() || this == GRAVEL || this == SAND;
    }

    /** {@code tfc:bush_plantable_on}: {@code minecraft:dirt}, farmland and clay (neither is in the terrain). */
    public boolean bushPlantableOn() {
        return treeGrowsOn();
    }

    /** Whether the top face is sturdy (a full solid block), for {@code BottomSupportedDeviceBlock.canSurvive}. */
    public boolean sturdyTop() {
        return switch (this) {
            case AIR, WATER, SALT_WATER, LEAVES, KRUMMHOLZ -> false;
            default -> true;
        };
    }
}
