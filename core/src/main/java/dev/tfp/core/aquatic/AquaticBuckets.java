/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/EntityHelpers.java @ c3b4d9536 (bucketMobPickup)
 */
package dev.tfp.core.aquatic;

/**
 * The bucket rule of a TFC aquatic mob: a mob can be picked up into a bucket only when the held bucket holds the same fluid as the
 * mob's bucket, and the mob is alive. The item and sound glue is on Paper.
 */
public final class AquaticBuckets {

    private AquaticBuckets() {
    }

    /**
     * Whether a player may pick the species up with the held bucket. TFC's order: the held item is a bucket, its fluid matches the
     * mob bucket's fluid, and the entity is alive. A species with no mob bucket is never picked up.
     *
     * @param species      the aquatic species
     * @param heldBucket   the fluid the held bucket contains, {@link AquaticFluid#NOT_WATER} for an empty or other item
     * @param alive        whether the entity is alive
     * @return true if the pickup goes ahead
     */
    public static boolean canPickUp(AquaticSpecies species, AquaticFluid heldBucket, boolean alive) {
        return alive && species.bucketFluid().map(fluid -> fluid == heldBucket).orElse(false);
    }
}
