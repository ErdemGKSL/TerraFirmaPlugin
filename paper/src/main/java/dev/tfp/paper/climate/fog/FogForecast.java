/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/climate/OverworldClimateModel.java @ c3b4d9536 (getFog),
 *       src/main/java/net/dries007/tfc/client/ClientForgeEventHandler.java @ c3b4d9536 (onFogRender)
 */
package dev.tfp.paper.climate.fog;

import dev.tfp.core.climate.ChunkClimate;
import dev.tfp.core.climate.OverworldClimate;
import dev.tfp.core.climate.fog.ClimateFog;
import dev.tfp.core.clock.ClockSnapshot;
import dev.tfp.paper.climate.ChunkClimateSource;

/**
 * PAR-CLIM-14: the fog of a place and a calendar instant, as the quantised level the seasonal biome remap would pick a
 * foggy variant by. Pure and thread-safe (any thread, no world access): the sky light at the player and the clock are
 * inputs. Nothing sends the level to a client yet; the vanilla-client spike (biome fog distance attributes per chunk) has
 * not run, so this feeds only {@code /tfp debug fog}.
 */
public final class FogForecast {
    /** The quantised levels a foggy variant set can express (1..LEVELS; 0 is no fog). */
    public static final int LEVELS = 3;
    /** The render distance, in blocks, {@link #at} reports TFC's planes for (12 chunks). */
    public static final float DEBUG_RENDER_DISTANCE = 12 * 16;

    private FogForecast() {
    }

    /**
     * What the fog is at one place.
     *
     * @param foggyDay    whether today is a foggy day (1 in {@link ClimateFog#FOGGY_DAY_RARITY})
     * @param dayStrength today's fog roll in [0, 1), 0 on a clear day
     * @param fog         TFC {@code getFog}: the fog scale in [0, 1)
     * @param level       {@link ClimateFog#level} of {@code fog} for {@link #LEVELS}
     * @param farPlane    TFC's terrain fog far plane at {@link #DEBUG_RENDER_DISTANCE}, blocks
     * @param nearPlane   the near plane that goes with it
     */
    public record Reading(boolean foggyDay, float dayStrength, float fog, int level, float farPlane, float nearPlane) {
    }

    /**
     * The fog at block column {@code x}, {@code z} of a TFC overworld.
     *
     * @param force    a debug override: today counts as a foggy day of full strength
     * @param skyLight the sky light at the player's eye, 0 to 15 (TFC takes it from the level at the camera)
     */
    public static Reading at(long worldSeed, int x, int z, ClockSnapshot clock, int skyLight, boolean force) {
        final OverworldClimate climate = OverworldClimate.forWorldSeed(worldSeed, OverworldClimate.DEFAULT_TEMPERATURE_SCALE);
        final ChunkClimate chunk = ChunkClimateSource.climateAt(worldSeed, x, z);
        final long ticks = clock.calendarTicks();
        final float rainfall = climate.instantRainfall(chunk, ticks, clock.daysInMonth());
        final long days = ticks / 24_000L;
        final long seed = climate.climateSeed();
        final float strength = force ? 1f - Math.ulp(1f) : ClimateFog.dayStrength(seed, days);
        final boolean foggy = force || ClimateFog.isFoggyDay(seed, days);
        final float fog = force ? ClimateFog.fogWithStrength(strength, ticks, rainfall, skyLight)
            : ClimateFog.fog(seed, ticks, rainfall, skyLight);
        return new Reading(foggy, strength, fog, ClimateFog.level(fog, LEVELS), ClimateFog.farPlane(DEBUG_RENDER_DISTANCE, fog),
            ClimateFog.nearPlane(DEBUG_RENDER_DISTANCE, fog));
    }
}
