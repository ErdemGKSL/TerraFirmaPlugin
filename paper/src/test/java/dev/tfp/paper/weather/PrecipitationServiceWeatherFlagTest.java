/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.weather;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.climate.OverworldClimate;
import dev.tfp.core.clock.ClockSnapshot;
import dev.tfp.core.clock.GlobalClockHolder;
import dev.tfp.core.weather.PrecipitationType;
import dev.tfp.core.weather.cycle.WeatherCycle;
import dev.tfp.core.world.BlockPos;
import dev.tfp.paper.climate.ChunkClimateSource;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.testing.MockServerTest;
import dev.tfp.paper.weather.cycle.ClimateWeatherCycle;
import dev.tfp.paper.weather.cycle.LocalRain;
import java.lang.reflect.Proxy;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import org.bukkit.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.world.WorldMock;

/**
 * PAR-CLIM-20: {@link PrecipitationService} honours the climate weather cycle. With the world's weather flag off a wet
 * site in a climate rain window gets NONE (TFC {@code rainIntensity = isWeatherEnabled ? getRain : -1}); with it on the
 * decision is TFC's {@code getPrecipitationAt} behind vanilla's {@code level.isRaining()} gate, which holds the first
 * 21 ticks of a rain window back (the lazy rain level is not above 0.2 yet); a world the cycle does not drive keeps
 * the climate with the flag on.
 */
class PrecipitationServiceWeatherFlagTest extends MockServerTest {
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000020");

    private WorldMock tfc;
    private WorldMock vanilla;
    private GlobalClockHolder clock;
    private ClimateWeatherCycle cycle;
    private PrecipitationService service;
    /** A very wet column of the TFC world's seed (any rain intensity above zero precipitates there). */
    private BlockPos wet;
    /** The first tick of a climate rain window, and a tick well inside it where the wet site gets RAIN. */
    private long windowStart;
    private long rainTick;

    /** Global and worker tasks run inline; nothing else is used. */
    private static Scheduler inlineScheduler() {
        return (Scheduler) Proxy.newProxyInstance(Scheduler.class.getClassLoader(), new Class<?>[] {Scheduler.class},
                (p, m, a) -> switch (m.getName()) {
                    case "global" -> {
                        ((Runnable) a[0]).run();
                        yield null;
                    }
                    case "cpuExecutor" -> (Executor) Runnable::run;
                    case "hashCode" -> System.identityHashCode(p);
                    case "equals" -> p == a[0];
                    case "toString" -> "inline scheduler";
                    default -> throw new UnsupportedOperationException(m.getName());
                });
    }

    @BeforeEach
    void setUp() {
        tfc = server.addSimpleWorld("tfc");
        vanilla = server.addSimpleWorld("vanilla");
        clock = new GlobalClockHolder();
        Scheduler scheduler = inlineScheduler();
        cycle = new ClimateWeatherCycle(clock, scheduler, w -> w.getName().equals("tfc"), () -> server.getWorlds(),
                () -> server.getOnlinePlayers());
        service = new PrecipitationService(clock, scheduler, null, null, () -> Optional.of(cycle));

        long seed = tfc.getSeed();
        assertThat(vanilla.getSeed()).isEqualTo(seed);
        wet = null;
        for (int i = 0; i < 400 && wet == null; i++) {
            int x = (i % 20 - 10) * 2_000;
            int z = (i / 20 - 10) * 2_000;
            if (ChunkClimateSource.climateAt(seed, x, z).averageRainfall() > 450f) {
                wet = new BlockPos(x, 70, z);
            }
        }
        assertThat(wet).as("a column with more than 450 mm of rain").isNotNull();
        OverworldClimate climate = OverworldClimate.forWorldSeed(seed, OverworldClimate.DEFAULT_TEMPERATURE_SCALE);
        windowStart = -1;
        rainTick = -1;
        for (long t = 1_000; t < 50_000_000L && rainTick < 0; t++) {
            if (climate.rainIntensity(t) >= 0 && climate.rainIntensity(t - 1) < 0) {
                long inside = t + 2_000;
                if (climate.rainIntensity(inside) >= 0
                        && PrecipitationService.decide(seed, true, wet, true, true, at(inside)) == PrecipitationType.RAIN
                        && LocalRain.at(seed, wet, at(t + 1), true, true) == PrecipitationType.RAIN) {
                    windowStart = t;
                    rainTick = inside;
                }
                t += 12_000; // windows are at least 12,000 ticks long
            }
        }
        assertThat(rainTick).as("a warm rain window at the wet column").isPositive();
    }

    private static ClockSnapshot at(long calendarTicks) {
        return new ClockSnapshot(1, calendarTicks, calendarTicks, ClockSnapshot.DEFAULT_DAYS_IN_MONTH, true);
    }

    private void publish(long calendarTicks) {
        clock.publish(calendarTicks, calendarTicks, ClockSnapshot.DEFAULT_DAYS_IN_MONTH, true);
    }

    @Test
    void theWeatherFlagOffGivesNoneAtAWetSiteInARainWindow() {
        publish(rainTick);
        assertThat(service.precipitationAt(tfc, wet, true)).isEqualTo(PrecipitationType.RAIN);
        assertThat(service.isRainingAt(tfc, wet, true)).isTrue();

        cycle.setEnabled(tfc, false);
        assertThat(service.weatherEnabled(tfc)).isFalse();
        assertThat(service.precipitationAt(tfc, wet, true)).isEqualTo(PrecipitationType.NONE);
        assertThat(service.isRainingAt(tfc, wet, true)).isFalse();
        // The player path captures the same flag
        var capture = new PrecipitationService.Capture(PLAYER, tfc.getSeed(), true, wet, true, service.weatherEnabled(tfc));
        assertThat(PrecipitationService.compute(capture, clock.get()).type()).isEqualTo(PrecipitationType.NONE);

        cycle.setEnabled(tfc, true);
        assertThat(service.precipitationAt(tfc, wet, true)).isEqualTo(PrecipitationType.RAIN);
    }

    @Test
    void theFlagOffGivesNoneAtEveryTimeAndThePureDecisionAgrees() {
        long seed = tfc.getSeed();
        for (long t = 0; t < 2_304_000L; t += 3_000) {
            assertThat(PrecipitationService.decide(seed, true, wet, true, false, at(t))).isEqualTo(PrecipitationType.NONE);
            assertThat(PrecipitationService.climateRaining(seed, t, false)).isFalse();
        }
    }

    @Test
    void vanillaIsRainingGateHoldsTheFirstTwentyOneTicksOfARainWindowBack() {
        long seed = tfc.getSeed();
        // The climate alone already rains there five ticks into the window
        assertThat(LocalRain.at(seed, wet, at(windowStart + 5), true, true)).isEqualTo(PrecipitationType.RAIN);
        // Rain level steps 0.01 per tick from 0: above 0.2 from the 21st raining tick (vanilla Level.isRaining)
        int firstRaining = -1;
        for (int d = 0; d < 40; d++) {
            if (PrecipitationService.climateRaining(seed, windowStart + d, true)) {
                firstRaining = d;
                break;
            }
        }
        // About twenty 0.01 steps from 0 (float sums decide the exact tick, as in vanilla)
        assertThat(firstRaining).isBetween(19, 21);
        OverworldClimate climate = OverworldClimate.forWorldSeed(seed, OverworldClimate.DEFAULT_TEMPERATURE_SCALE);
        assertThat(WeatherCycle.levelsAt(climate, windowStart - 1, true).rain()).isZero();
        assertThat(WeatherCycle.levelsAt(climate, windowStart + firstRaining, true).isRaining()).isTrue();

        publish(windowStart + 5);
        assertThat(service.precipitationAt(tfc, wet, true)).isEqualTo(PrecipitationType.NONE);
        publish(windowStart + 30);
        assertThat(service.precipitationAt(tfc, wet, true)).isEqualTo(PrecipitationType.RAIN);
    }

    @Test
    void aWorldTheCycleDoesNotDriveKeepsTheClimateWithTheFlagOn() {
        publish(rainTick);
        cycle.setEnabled(tfc, false);
        // The weather flag of the TFC world does not reach a vanilla world; its storm flag is not read
        assertThat(service.weatherEnabled(vanilla)).isTrue();
        vanilla.setStorm(false);
        assertThat(service.precipitationAt(vanilla, wet, true)).isEqualTo(PrecipitationType.RAIN);
        assertThat(service.precipitationAt(tfc, wet, true)).isEqualTo(PrecipitationType.NONE);
        // Rain does not reach a covered position, whatever the weather
        assertThat(service.precipitationAt(vanilla, wet, false)).isEqualTo(PrecipitationType.NONE);
    }

    @Test
    void withoutTheCycleTheFlagIsOn() {
        PrecipitationService alone = new PrecipitationService(clock, inlineScheduler(), null, null, Optional::empty);
        assertThat(alone.weatherEnabled(tfc)).isTrue();
        publish(rainTick);
        assertThat(alone.precipitationAt(tfc, wet, true)).isEqualTo(PrecipitationType.RAIN);
        World nether = server.addSimpleWorld("nether_like");
        assertThat(alone.precipitationAt(nether, wet, false)).isEqualTo(PrecipitationType.NONE);
    }
}
