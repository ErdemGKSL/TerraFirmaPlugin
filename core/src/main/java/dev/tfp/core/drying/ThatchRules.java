/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/ThatchBlock.java @ c3b4d9536 (getLightBlock, slowEntityFactor),
 *       src/main/java/net/dries007/tfc/common/blocks/ISlowEntities.java @ c3b4d9536 (NO_SLOW),
 *       src/main/java/net/dries007/tfc/config/ServerConfig.java @ c3b4d9536 (thatchMovementMultiplier)
 */
package dev.tfp.core.drying;

/**
 * The rules of thatch. Thatch has no drying time in TFC: it is a light-blocking block with no collision, and it slows movement
 * unless it holds a fluid. Nothing here is lazy, because nothing changes over time.
 */
public final class ThatchRules {
    /** {@code level.getMaxLightLevel()}: thatch blocks all light. */
    public static final int LIGHT_BLOCK = 15;
    /** {@code ISlowEntities.NO_SLOW}: no slowdown. */
    public static final float NO_SLOW = 1f;
    /** {@code thatchMovementMultiplier} default. */
    public static final double DEFAULT_MOVEMENT_MULTIPLIER = 0.6;

    private ThatchRules() {
    }

    /**
     * The movement factor of a thatch block: {@code thatchMovementMultiplier} when it holds no fluid, {@link #NO_SLOW} when
     * it does, so a waterlogged thatch does not slow a player twice.
     *
     * @param fluidPresent whether the block holds any fluid (TFC tests for {@code Fluids.EMPTY})
     * @param multiplier   {@code thatchMovementMultiplier}, in [0, 1]
     */
    public static float slowFactor(boolean fluidPresent, double multiplier) {
        if (!(multiplier >= 0 && multiplier <= 1)) {
            throw new IllegalArgumentException("multiplier must be in [0, 1]: " + multiplier);
        }
        return fluidPresent ? NO_SLOW : (float) multiplier;
    }
}
