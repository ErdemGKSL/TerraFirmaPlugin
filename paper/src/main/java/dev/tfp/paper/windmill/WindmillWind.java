/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/rotation/WindmillBlockEntity.java @ c3b4d9536 (clientTick line 75:
 *       Climate.get(level).getWind(level, pos).length()), src/main/java/net/dries007/tfc/util/climate/OverworldClimateModel.java
 *       @ c3b4d9536 (getWind and its WeatherHelpers.isPrecipitating test), src/main/java/net/dries007/tfc/util/climate/ClimateModel.java
 *       @ c3b4d9536 (getWind default: no wind outside the overworld model)
 */
package dev.tfp.paper.windmill;

import dev.tfp.core.calendar.CalendarTicks;
import dev.tfp.core.climate.ChunkClimate;
import dev.tfp.core.climate.OverworldClimate;
import dev.tfp.core.clock.ClockSnapshot;
import dev.tfp.core.instrument.WindField;
import dev.tfp.core.world.BlockPos;
import dev.tfp.core.worldgen.Mth;
import dev.tfp.paper.climate.ChunkClimateSource;
import dev.tfp.paper.instrument.InstrumentReadout;
import dev.tfp.paper.worldgen.WorldSettingsLookup;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * The wind a windmill reads: the length of TFC's overworld wind vector at its block ({@link WindField}), the value the
 * anemometer shows (PAR-CLIM-09). Taken as a {@link Capture} on the region thread, computed on the CPU pool.
 */
public final class WindmillWind {
    private WindmillWind() {
    }

    /**
     * What the wind computation reads, taken on the windmill's region thread.
     *
     * @param worldSeed the world's seed
     * @param overworld whether the world has TFC's overworld climate
     * @param pos       the windmill's block
     * @param ocean     whether the biome has predictable winds ({@code #tfc:has_predictable_winds})
     * @param daytime   whether {@code dayTime % 24000 < 12000}
     * @param clock     the published clock
     */
    public record Capture(long worldSeed, boolean overworld, BlockPos pos, boolean ocean, boolean daytime, ClockSnapshot clock) {
    }

    /** Region thread of {@code block}. */
    @SuppressWarnings("deprecation")
    public static Capture capture(Block block, ClockSnapshot clock) {
        World world = block.getWorld();
        String biome = block.getBiome().getKey().getKey();
        return new Capture(world.getSeed(), world.getEnvironment() == World.Environment.NORMAL,
            new BlockPos(block.getX(), block.getY(), block.getZ()), InstrumentReadout.OCEAN_BIOMES.contains(biome),
            world.getTime() % 24_000 < 12_000, clock);
    }

    /** The wind's length, 0 outside the overworld or before the calendar runs. Pure; any thread. */
    public static float wind(Capture c) {
        if (!c.overworld() || !c.clock().isSet()) {
            return 0f;
        }
        OverworldClimate climate = WorldSettingsLookup.climate(c.worldSeed());
        ChunkClimate chunk = ChunkClimateSource.climateAt(c.worldSeed(), c.pos().x(), c.pos().z());
        long ticks = c.clock().calendarTicks();
        int dim = c.clock().daysInMonth();
        float rain = climate.rainIntensity(ticks);
        float rainfall = climate.instantRainfall(chunk, ticks, dim);
        // TFC WeatherHelpers.isPrecipitating, as InstrumentReadout.precipitating
        boolean raining = rain - Mth.clampedMap(rainfall, OverworldClimate.MIN_AVERAGE_RAINFALL,
            OverworldClimate.MAX_AVERAGE_RAINFALL, 1f, 0f) > 0;
        return WindField.at(climate.climateSeed(), CalendarTicks.totalDays(ticks), c.pos().y(), c.pos().z(), raining, c.ocean(),
            c.daytime(), WindField.DEFAULT_OCEAN_WIND_SCALE).length();
    }
}
