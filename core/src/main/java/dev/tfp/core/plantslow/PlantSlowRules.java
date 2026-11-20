/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/plant/PlantBlock.java @ c3b4d9536 (slowEntityFactor),
 *       src/main/java/net/dries007/tfc/common/blocks/plant/fruit/SeasonalPlantBlock.java @ c3b4d9536 (slowEntityFactor),
 *       src/main/java/net/dries007/tfc/common/blocks/ThatchBlock.java @ c3b4d9536 (slowEntityFactor),
 *       src/main/java/net/dries007/tfc/config/ServerConfig.java @ c3b4d9536 (thatchMovementMultiplier 0.6)
 */
package dev.tfp.core.plantslow;

import dev.tfp.core.leaves.LeafRules;

/**
 * The movement slowdown of plants, berry bushes and thatch (PAR-GATHER-10), pure. The per-plant speed factors are data
 * ({@code Plant.speedFactor}, exported by datagen); {@link LeafRules#plantFactor} already holds the plant lerp.
 */
public final class PlantSlowRules {
    /** TFC {@code thatchMovementMultiplier} default. */
    public static final float THATCH_MULTIPLIER = 0.6f;
    /** Berry bush stages: {@code STAGE_2} is 0 to 2. */
    public static final int BUSH_STAGES = 3;

    private PlantSlowRules() {}

    /** {@code PlantBlock}, {@code TopPlantBlock}, {@code BodyPlantBlock}, {@code KelpTreeBlock}: {@code lerp(plantsModifier, speed, 1)}. */
    public static float plant(float speedFactor) {
        return LeafRules.plantFactor(speedFactor, LeafRules.PLANTS_MOVEMENT_MODIFIER);
    }

    /**
     * {@code SeasonalPlantBlock.slowEntityFactor} of a berry bush (it has a {@code stage} and no {@code age}): the leaves modifier
     * lerped from no slowdown by {@code (1 + stage) / 3}.
     */
    public static float seasonalBush(int stage, float leavesModifier) {
        return LeafRules.lerp((1 + stage) / (float) BUSH_STAGES, LeafRules.NO_SLOW, leavesModifier);
    }

    /** {@code ThatchBlock.slowEntityFactor}: the multiplier unless the block holds a fluid. */
    public static float thatch(boolean fluidLogged, float multiplier) {
        return fluidLogged ? LeafRules.NO_SLOW : multiplier;
    }
}
