/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/ForgeEventHandler.java @ c3b4d9536 (onPlayerTick, drinking in the rain),
 * and src/main/java/net/dries007/tfc/util/tracker/WeatherHelpers.java @ c3b4d9536 (getPrecipitationAt)
 */
package dev.tfp.core.thirst;

/**
 * TFC's rule for drinking in the rain: a player whose pitch is at most {@value #MAX_PITCH} degrees (looking up,
 * {@code Mth.wrapDegrees(getXRot())}, the F3 value) while it rains at the block above the player's feet gains
 * {@code thirstGainedFromDrinkingInTheRain} thirst per tick. Pure.
 *
 * <p>"Raining at" is vanilla {@code Level.isRainingAt} with TFC's climate in place of the biome: the level is raining,
 * the position sees the sky, no motion-blocking block is above it, and the climate says rain (instant temperature above
 * 0 degrees C; at or below it snows).
 */
public final class RainDrinking {
    /** TFC's pitch limit: {@code angle <= -80}. */
    public static final float MAX_PITCH = -80f;

    private RainDrinking() {
    }

    /** {@code Mth.wrapDegrees} for a float, as in Minecraft 1.21.1. */
    static float wrapDegrees(float degrees) {
        float wrapped = degrees % 360f;
        if (wrapped >= 180f) {
            wrapped -= 360f;
        }
        if (wrapped < -180f) {
            wrapped += 360f;
        }
        return wrapped;
    }

    /** Whether a pitch (vanilla {@code xRot}, negative up) is looking up enough to drink. */
    public static boolean looksUp(float pitch) {
        return wrapDegrees(pitch) <= MAX_PITCH;
    }

    /**
     * Whether it rains at a position, given what the world says there.
     *
     * @param raining     the level is raining (vanilla weather)
     * @param openToSky   the position sees the sky and no motion-blocking block is above it
     * @param temperature the instant temperature at the position in degrees C (NaN: unknown, no rain)
     */
    public static boolean rainingAt(boolean raining, boolean openToSky, float temperature) {
        return raining && openToSky && temperature > 0f;
    }

    /** Whether the player drinks this tick: looking up while it rains at the position above the feet. */
    public static boolean drinks(float pitch, boolean raining, boolean openToSky, float temperature) {
        return looksUp(pitch) && rainingAt(raining, openToSky, temperature);
    }
}
