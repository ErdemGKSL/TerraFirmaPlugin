/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/aquatic/FreshwaterFish.java @ c3b4d9536 (canSpawnIn),
 * src/main/java/net/dries007/tfc/common/entities/aquatic/TFCSquid.java (canSpawnIn),
 * src/main/java/net/dries007/tfc/common/entities/aquatic/TFCCod.java (canSpawnIn)
 */
package dev.tfp.core.aquatic;

/**
 * The fluid predicate a fluid-gated TFC aquatic species uses in {@code canSpawnIn}. Three predicates appear in TFC:
 * vanilla source water only, salt water only, and the {@code tfc:any_infinite_water} tag.
 */
public enum Habitat {
    /** {@code fluid.isSame(Fluids.WATER)}: vanilla water only, so not river or spring water. */
    FRESH_WATER_ONLY,
    /** {@code fluid.isSame(TFCFluids.SALT_WATER.getSource())}: salt water only. */
    SALT_WATER_ONLY,
    /** {@code Helpers.isFluid(fluid, TFCTags.Fluids.ANY_INFINITE_WATER)}: vanilla, river, salt and spring water. */
    ANY_INFINITE_WATER;

    /**
     * Whether a species with this habitat may spawn in the fluid.
     *
     * @param fluid the fluid at the spawn position
     * @return true if the fluid is one this habitat accepts
     */
    public boolean contains(AquaticFluid fluid) {
        return switch (this) {
            case FRESH_WATER_ONLY -> fluid == AquaticFluid.VANILLA_WATER;
            case SALT_WATER_ONLY -> fluid == AquaticFluid.SALT_WATER;
            case ANY_INFINITE_WATER -> fluid != AquaticFluid.NOT_WATER;
        };
    }
}
