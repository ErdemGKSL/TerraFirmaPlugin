/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/tracker/WeatherHelpers.java @ c3b4d9536 (getPrecipitationAt with the
 * WorldTracker weather flag)
 * Also: src/main/java/net/dries007/tfc/client/ClimateRenderCache.java @ c3b4d9536 (the local rain decision per player)
 */
package dev.tfp.paper.weather.cycle;

import dev.tfp.core.climate.ChunkClimate;
import dev.tfp.core.climate.ClimateReading;
import dev.tfp.core.climate.OverworldClimate;
import dev.tfp.core.clock.ClockSnapshot;
import dev.tfp.core.weather.PrecipitationType;
import dev.tfp.core.world.BlockPos;
import dev.tfp.paper.climate.ChunkClimateSource;

/**
 * The climate's precipitation at one position of a TFC overworld, with the world's weather flag applied (TFC
 * {@code rainIntensity = isWeatherEnabled() ? getRain(t) : -1}). Pure and thread-safe: call it from a worker, with plain
 * values captured on the owning thread. It samples the climate noise, so it is never called from an event handler.
 */
public final class LocalRain {
    private LocalRain() {
    }

    /**
     * @param worldSeed      the world seed (the climate seed derives from it)
     * @param pos            the block position
     * @param clock          the published calendar reading
     * @param weatherEnabled the world's weather flag ({@link ClimateWeatherCycle#enabled})
     * @param rainReaches    vanilla's gate in front of TFC's call (sky light and the motion-blocking top); pass
     *                       {@code true} for the per-player sky decision, which TFC's client makes without it
     * @return NONE before the first calendar reading
     */
    public static PrecipitationType at(long worldSeed, BlockPos pos, ClockSnapshot clock, boolean weatherEnabled,
            boolean rainReaches) {
        if (!clock.isSet() || !rainReaches) {
            return PrecipitationType.NONE;
        }
        OverworldClimate climate = OverworldClimate.forWorldSeed(worldSeed, OverworldClimate.DEFAULT_TEMPERATURE_SCALE);
        float rainIntensity = weatherEnabled ? climate.rainIntensity(clock.calendarTicks()) : -1f;
        if (rainIntensity < 0) {
            return PrecipitationType.NONE; // no rain anywhere: skip the chunk climate noise
        }
        ChunkClimate chunk = ChunkClimateSource.climateAt(worldSeed, pos.x(), pos.z());
        ClimateReading reading = ClimateReading.of(climate, chunk, pos, clock);
        return PrecipitationType.decide(reading.temperature(), reading.rainfall(), rainIntensity, true);
    }
}
