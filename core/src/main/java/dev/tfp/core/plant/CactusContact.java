/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/plant/TFCCactusBlock.java @ c3b4d9536 (entityInside),
 *       src/main/java/net/dries007/tfc/common/blocks/plant/TFCPassableCactusBlock.java @ c3b4d9536 (entityInside),
 *       src/main/java/net/dries007/tfc/common/blocks/plant/TFCCactusBedBlock.java @ c3b4d9536 (entityInside),
 *       src/main/java/net/dries007/tfc/common/blocks/plant/BranchingCactusBlock.java @ c3b4d9536 (entityInside)
 */
package dev.tfp.core.plant;

import dev.tfp.core.worldgen.plant.PlantType;

/** Which plants hurt on contact and how much (vanilla's cactus damage type, 1.0 per hit), pure. */
public final class CactusContact {
    /** {@code entity.hurt(damageSources().cactus(), 1f)}. */
    public static final float DAMAGE = 1.0f;
    /** Vanilla's invulnerability after a hit is 10 ticks: {@code entityInside} runs every tick but only one hit in ten lands. */
    public static final int PERIOD_TICKS = 10;

    private CactusContact() {
    }

    public static boolean hurts(PlantType type) {
        return switch (type) {
            case CACTUS, PASSABLE_CACTUS, CACTUSBED, BRANCHING_CACTUS, BRANCHING_CACTUS_TOP -> true;
            default -> false;
        };
    }
}
