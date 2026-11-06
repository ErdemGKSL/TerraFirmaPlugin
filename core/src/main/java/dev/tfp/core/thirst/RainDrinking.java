/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/ForgeEventHandler.java @ c3b4d9536 (onPlayerTick, drinking in the rain)
 */
package dev.tfp.core.thirst;

/**
 * TFC's rule for drinking in the rain: a player whose pitch is at most {@value #MAX_PITCH} degrees (looking up,
 * {@code Mth.wrapDegrees(getXRot())}, the F3 value) while it rains at the block above the player's feet gains
 * {@code thirstGainedFromDrinkingInTheRain} thirst per tick. Pure.
 *
 * <p>"Raining at" is vanilla {@code Level.isRainingAt}: the level is raining, the position sees the sky, no
 * motion-blocking block is above it, and the biome the client renders there has precipitation and is warm enough to rain
 * ({@code Biome.warmEnoughToRain}: the height-adjusted biome temperature is at least {@value #RAIN_MIN_TEMPERATURE}).
 * TFC replaces the biome by its climate there because TFC also drives the client's rain from that climate; this plugin
 * does not, so the gate follows the rain the vanilla client draws (a ledger divergence, PAR-SURV-04).
 */
public final class RainDrinking {
    /** TFC's pitch limit: {@code angle <= -80}. */
    public static final float MAX_PITCH = -80f;
    /** Vanilla {@code Biome.warmEnoughToRain}: rain at or above this biome temperature, snow below. */
    public static final float RAIN_MIN_TEMPERATURE = 0.15f;
    /**
     * The biome temperature of every TFC biome ({@code data/tfc/worldgen/biome/*.json}: all 125 at 0.5, with
     * precipitation), which the seasonal biome variants copy and so the client renders in a TFC world.
     */
    public static final float TFC_BIOME_TEMPERATURE = 0.5f;
    /** Vanilla {@code Biome.getHeightAdjustedTemperature}: no adjustment up to {@code seaLevel + 17}. */
    static final int HEIGHT_ADJUST_ABOVE_SEA = 17;
    /** Vanilla's height lapse: {@code 0.05F / 40.0F} per block. */
    static final float LAPSE_PER_BLOCK = 0.05f / 40.0f;
    /** Bound of vanilla's {@code TEMPERATURE_NOISE.getValue(x / 8, z / 8, false) * 8} (coldest case used). */
    static final float NOISE_BOUND = 8f;

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
     * Vanilla {@code Biome.getHeightAdjustedTemperature} for a biome with no temperature modifier, with the coldest value
     * of its temperature noise ({@value #NOISE_BOUND}): above {@code seaLevel + 17} the temperature drops 0.05 per 40
     * blocks. Used for a biome the server does not hold (the seasonal variant the client sees); for a server biome the
     * exact value comes from the world.
     */
    public static float heightAdjustedTemperature(float base, int y, int seaLevel) {
        int over = seaLevel + HEIGHT_ADJUST_ABOVE_SEA;
        return y > over ? base - (NOISE_BOUND + y - over) * LAPSE_PER_BLOCK : base;
    }

    /**
     * Whether it rains at a position as the vanilla client draws it.
     *
     * @param raining          the level is raining (vanilla weather)
     * @param openToSky        the position sees the sky and no motion-blocking block is above it
     * @param hasPrecipitation the client's biome there has precipitation
     * @param biomeTemperature the client's biome temperature there, height-adjusted (NaN: unknown, no rain)
     */
    public static boolean rainingAt(boolean raining, boolean openToSky, boolean hasPrecipitation,
            float biomeTemperature) {
        return raining && openToSky && hasPrecipitation && biomeTemperature >= RAIN_MIN_TEMPERATURE;
    }

    /** Whether the player drinks this tick: looking up while it rains at the position above the feet. */
    public static boolean drinks(float pitch, boolean raining, boolean openToSky, boolean hasPrecipitation,
            float biomeTemperature) {
        return looksUp(pitch) && rainingAt(raining, openToSky, hasPrecipitation, biomeTemperature);
    }
}
