/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/items/TFCItems.java @ c3b4d9536 (the fluid containers and their capacity and
 *       whitelist arguments), src/main/java/net/dries007/tfc/common/items/FluidContainerItem.java @ c3b4d9536,
 *       src/main/java/net/dries007/tfc/common/items/GlassBottleItem.java @ c3b4d9536,
 *       src/main/java/net/dries007/tfc/config/ServerConfig.java @ c3b4d9536 (the capacity defaults)
 */
package dev.tfp.core.fluid;

import dev.tfp.core.barrel.BarrelRules;
import dev.tfp.core.crucible.CrucibleRules;
import dev.tfp.core.vessel.JugRules;
import dev.tfp.core.vessel.VesselRules;
import java.util.Optional;

/**
 * The fluid containers of TFC that hold a single fluid tank, with the default capacity of each in mB and the fluid tag that
 * decides what it may hold. Molds are not here: {@code core/vessel/MoldKind} owns their capacities.
 *
 * <p>A container whose predicate is code in TFC (the small vessel, the crucible and the lamp) has no tag, so its
 * {@link #usableTag()} is empty and the caller supplies the predicate as a fact.
 */
public enum FluidContainerKind {
    /** TFC {@code woodenBucketCapacity}, default {@code FluidHelpers.BUCKET_VOLUME}. */
    WOODEN_BUCKET(1000, "tfc:usable_in_wooden_bucket"),
    /** TFC {@code metalBucketCapacity}, default {@code FluidHelpers.BUCKET_VOLUME}. */
    RED_STEEL_BUCKET(1000, "tfc:usable_in_red_steel_bucket"),
    /** TFC {@code metalBucketCapacity}, default {@code FluidHelpers.BUCKET_VOLUME}. */
    BLUE_STEEL_BUCKET(1000, "tfc:usable_in_blue_steel_bucket"),
    /** TFC {@code jugCapacity}, the ceramic jug. */
    JUG(JugRules.CAPACITY, "tfc:usable_in_jug"),
    /** TFC {@code silicaGlassBottleCapacity}, a glass bottle that is whitelisted with the jug tag. */
    SILICA_GLASS_BOTTLE(500, "tfc:usable_in_jug"),
    /** TFC {@code hematiticGlassBottleCapacity}, a glass bottle that is whitelisted with the jug tag. */
    HEMATITIC_GLASS_BOTTLE(400, "tfc:usable_in_jug"),
    /** TFC {@code volcanicGlassBottleCapacity}, a glass bottle that is whitelisted with the jug tag. */
    VOLCANIC_GLASS_BOTTLE(400, "tfc:usable_in_jug"),
    /** TFC {@code olivineGlassBottleCapacity}, a glass bottle that is whitelisted with the jug tag. */
    OLIVINE_GLASS_BOTTLE(400, "tfc:usable_in_jug"),
    /** TFC {@code barrelCapacity}, the barrel's tank. */
    BARREL(BarrelRules.DEFAULT_CAPACITY, "tfc:usable_in_barrel"),
    /** TFC {@code smallVesselCapacity}; its predicate is {@code FluidHeat.get(fluid) != null}. */
    SMALL_VESSEL(VesselRules.SMALL_VESSEL_CAPACITY, null),
    /** TFC {@code crucibleCapacity}; its predicate is {@code FluidHeat.get(fluid) != null}. */
    CRUCIBLE(CrucibleRules.DEFAULT_CAPACITY, null),
    /** TFC {@code lampCapacity}; its predicate is {@code LampFuel.get(fluid, state) != null}. */
    LAMP(250, null);

    private final int defaultCapacity;
    private final String usableTag;

    FluidContainerKind(int defaultCapacity, String usableTag) {
        this.defaultCapacity = defaultCapacity;
        this.usableTag = usableTag;
    }

    /** @return the TFC default tank capacity in mB */
    public int defaultCapacity() {
        return defaultCapacity;
    }

    /**
     * @return the id of the {@code tfc:usable_in_*} fluid tag that decides the contents, or empty when the predicate is code
     */
    public Optional<String> usableTag() {
        return Optional.ofNullable(usableTag);
    }
}
