/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/fluids/TFCFluids.java @ c3b4d9536 (salt, spring and river water as world fluids)
 */
package dev.tfp.core.worldwater;

/**
 * Which of TFC's world waters a vanilla water block stands for (PAR-FLUID-03, NP-FLUID-01). The world holds vanilla water
 * only; the kind is decided from the TFC biome and river of the column, never stored in the block.
 */
public enum WaterKind {
    /** Plain fresh water ({@code minecraft:water}): lakes, aquifers, still river water below the flowing layer. */
    FRESH("minecraft:water"),
    /** TFC's salt water: the water of every {@code salty()} biome. */
    SALT("tfp:salt_water"),
    /**
     * TFC's river water: fresh water with a flow. TFC's {@code RiverWaterBlock.pickupBlock} always picks it up as plain water,
     * so its container fluid is {@code minecraft:water}.
     */
    RIVER("minecraft:water"),
    /** TFC's spring water, the fluid of the hot spring pools. */
    SPRING("tfp:spring_water");

    private final String containerFluid;

    WaterKind(String containerFluid) {
        this.containerFluid = containerFluid;
    }

    /** The fluid id a bucket or a drink takes from this water (TFC's pickup result). */
    public String containerFluid() {
        return containerFluid;
    }

    /** Whether this water is salt (not drinkable as fresh water). */
    public boolean salty() {
        return this == SALT;
    }
}
