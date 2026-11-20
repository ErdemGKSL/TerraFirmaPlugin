/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.farm;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.climate.ChunkClimate;
import dev.tfp.core.clock.ClockSnapshot;
import dev.tfp.core.device.CatchUp;
import dev.tfp.core.device.DeviceRecord;
import dev.tfp.core.farm.FarmlandRules;
import dev.tfp.core.farm.FarmlandState;
import dev.tfp.core.farm.Nutrients;
import dev.tfp.core.persist.DataMap;
import dev.tfp.paper.device.DeviceKind;
import java.util.List;
import org.junit.jupiter.api.Test;

/** PAR-FARM-01: the farmland record, its lazy water reserve, the water snapshot decision and the readout numbers. */
class FarmlandDeviceTest {
    private static final ClockSnapshot CLOCK = new ClockSnapshot(1, 1_000_000L, 2_000_000L, 8, true);

    private static boolean[][][] grid() {
        return new boolean[9][2][9];
    }

    @Test
    void codecRoundTripsNutrientsReserveAndWaterTick() {
        FarmlandState s = new FarmlandState(new Nutrients(0.25f, 0.5f, 0.75f), 3.5f, 12345L);
        DataMap out = FarmlandDevice.CODEC.encode(s);
        assertThat(FarmlandDevice.CODEC.decode(out)).isEqualTo(s);
    }

    @Test
    void catchUpGrowsTheReserveInOneStepAndClampsAtFifteen() {
        DeviceKind<FarmlandState> kind = FarmlandDevice.kind(() -> CLOCK);
        DeviceRecord<FarmlandState> record = new DeviceRecord<>(FarmlandState.fresh(0L), List.of(), 0L);
        long gap = 24_000L; // player ticks; the calendar runs 20/24 of that
        DeviceRecord<FarmlandState> next = CatchUp.apply(record, gap, kind.advance()).record();
        long calendarGap = (long) (20f / 24f * gap);
        assertThat(next.state().additionalWater()).isEqualTo(FarmlandRules.additionalWater(0f, 0, calendarGap));
        assertThat(next.lastUpdateTick()).isEqualTo(gap);
        DeviceRecord<FarmlandState> huge = CatchUp.apply(record, 1_000_000L, kind.advance()).record();
        assertThat(huge.state().additionalWater()).isEqualTo(FarmlandRules.MAX_ADDITIONAL_WATER);
    }

    @Test
    void kindCoversThePerSoilFarmlandBlocks() {
        assertThat(FarmlandDevice.kindCovers("tfp:farmland/podzol")).isTrue();
        assertThat(FarmlandDevice.kindCovers("tfp:dirt/podzol")).isFalse();
        assertThat(FarmlandDevice.kind(() -> CLOCK).covers("tfp:farmland/podzol")).isTrue();
    }

    @Test
    void snapshotFindsTheCheapestWaterLikeFindMinCostWater() {
        boolean[][][] g = grid();
        assertThat(new HydrationSnapshot(g, false, FarmlandRules.SoilHydration.NORMAL).minCostWater())
            .isEqualTo(FarmlandRules.NO_SOURCE_COST);
        // water 2 blocks east on the farmland's own level: cost 2
        boolean[][][] near = grid();
        near[4 + 2][1][4] = true;
        assertThat(new HydrationSnapshot(near, false, FarmlandRules.SoilHydration.NORMAL).minCostWater()).isEqualTo(2);
        // water one block below and next to it: cost 1 + 2 = 3
        boolean[][][] below = grid();
        below[4 + 1][0][4] = true;
        assertThat(new HydrationSnapshot(below, false, FarmlandRules.SoilHydration.NORMAL).minCostWater()).isEqualTo(3);
    }

    @Test
    void readoutShowsHydrationNextToWaterAndNutrientPercents() {
        boolean[][][] near = grid();
        near[4 + 1][1][4] = true; // cost 1: a source in reach
        HydrationSnapshot water = new HydrationSnapshot(near, false, FarmlandRules.SoilHydration.NORMAL);
        FarmlandState state = new FarmlandState(new Nutrients(0.1f, 0.55f, 1.0f), 0f, 0L);
        FarmlandReadout.Lines lines = FarmlandReadout.compute(state, water, ChunkClimate.NEUTRAL, CLOCK, 42L);
        assertThat(lines.hydration().now()).isGreaterThanOrEqualTo(FarmlandRules.SOURCE_BOOST);
        assertThat(lines.nitrogenPercent()).isEqualTo(10);
        assertThat(lines.phosphorusPercent()).isEqualTo(55);
        assertThat(lines.potassiumPercent()).isEqualTo(100);
        HydrationSnapshot dry = new HydrationSnapshot(grid(), false, FarmlandRules.SoilHydration.NORMAL);
        assertThat(FarmlandReadout.compute(state, dry, ChunkClimate.NEUTRAL, CLOCK, 42L).hydration().now())
            .isLessThan(lines.hydration().now());
    }
}
