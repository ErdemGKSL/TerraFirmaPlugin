/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.crop;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.calendar.CalendarTicks;
import dev.tfp.core.climate.ChunkClimate;
import dev.tfp.core.clock.ClockSnapshot;
import dev.tfp.core.crop.CropGrowth;
import dev.tfp.core.crop.CropPlant;
import dev.tfp.core.crop.CropState;
import dev.tfp.core.crop.CropType;
import dev.tfp.core.farm.FarmlandRules;
import dev.tfp.core.farm.NutrientGrowth;
import dev.tfp.core.farm.Nutrients;
import dev.tfp.core.farm.SoilTier;
import dev.tfp.core.world.BlockPos;
import dev.tfp.paper.farm.HydrationSnapshot;
import dev.tfp.paper.integration.BlockStateRef;
import org.junit.jupiter.api.Test;

/** PAR-FARM-04: the crop record, the block ids and states, and the catch-up decision over a synthetic environment. */
class CropDeviceTest {
    private static final long DAY = CalendarTicks.CALENDAR_TICKS_IN_DAY;
    private static final ChunkClimate TEMPERATE = new ChunkClimate(10f, 300f, 0f, 0f);

    private static CropSettle.Environment env(boolean farmland, boolean light, boolean aboveOk) {
        boolean[][][] grid = new boolean[9][2][9];
        grid[4 + 1][1][4] = true; // a water source next to the farmland
        HydrationSnapshot water = new HydrationSnapshot(grid, false, FarmlandRules.SoilHydration.NORMAL);
        return new CropSettle.Environment(farmland ? water : null, farmland, light, false, aboveOk, false, SoilTier.NORMAL, TEMPERATE,
            42L, new BlockPos(0, 64, 0));
    }

    private static ClockSnapshot clockAt(long calendarTick) {
        return new ClockSnapshot(1, 1_000_000L, calendarTick, 8, true);
    }

    @Test
    void theRecordRoundTrips() {
        CropPlant plant = new CropPlant(new CropState(0.5f, 0.25f, 0.75f, 123_456L), new NutrientGrowth.Demand(0.1f, 0.2f, 0.3f));
        assertThat(CropPlantCodec.CODEC.decode(CropPlantCodec.CODEC.encode(plant))).isEqualTo(plant);
    }

    @Test
    void blockIdsMapToCrops() {
        assertThat(CropIds.type("tfp:crop/wheat")).contains(CropType.WHEAT);
        assertThat(CropIds.type("tfp:crop/red_bell_pepper")).contains(CropType.RED_BELL_PEPPER);
        assertThat(CropIds.type("tfp:dead_crop/wheat")).isEmpty();
        assertThat(CropIds.type("tfp:crop/nothing")).isEmpty();
        assertThat(CropIds.deadId(CropType.GREEN_BEAN)).isEqualTo("tfp:dead_crop/green_bean");
    }

    @Test
    void deadStatesCarryTheShapeProperties() {
        BlockStateRef wheat = BlockStateRef.of("tfp:crop/wheat").with("age", "7");
        assertThat(CropIds.dead(CropType.WHEAT, wheat, true, false).properties()).containsOnlyKeys("mature").containsEntry("mature", "true");
        BlockStateRef tomato = BlockStateRef.of("tfp:crop/tomato").with("age", "7").with("part", "bottom").with("stick", "true");
        BlockStateRef dead = CropIds.dead(CropType.TOMATO, tomato, false, true);
        assertThat(dead.id()).isEqualTo("tfp:dead_crop/tomato");
        assertThat(dead.properties()).containsEntry("part", "top").containsEntry("stick", "true").containsEntry("mature", "false");
        BlockStateRef jute = BlockStateRef.of("tfp:crop/jute").with("age", "5").with("part", "bottom");
        assertThat(CropIds.dead(CropType.JUTE, jute, true, false).properties()).containsEntry("part", "bottom").doesNotContainKey("stick");
        assertThat(CropIds.top(jute, 4).properties()).containsEntry("part", "top").containsEntry("age", "4");
    }

    @Test
    void aWheatCropCaughtUpAfterAMonthShowsAHigherAgeAndTheSoilIsUsed() {
        CropPlant planted = CropPlant.planted(0L);
        Nutrients soil = new Nutrients(1f, 1f, 1f);
        CropGrowth.Result r = CropSettle.settle(CropType.WHEAT, env(true, true, true), planted, soil, clockAt(20 * DAY));
        assertThat(r.dead()).isFalse();
        assertThat(r.plant().state().growth()).isGreaterThan(0.5f);
        assertThat(r.plant().state().lastTick()).isEqualTo(20 * DAY);
        assertThat(CropType.WHEAT.stages().stage(r.plant().state().growth()).age()).isGreaterThan(3);
        assertThat(r.soil().nitrogen()).isLessThan(1f);
        // a second settle at the same tick changes nothing (the glue may visit a crop any number of times)
        CropGrowth.Result again = CropSettle.settle(CropType.WHEAT, env(true, true, true), r.plant(), r.soil(), clockAt(20 * DAY));
        assertThat(again.plant()).isEqualTo(r.plant());
        assertThat(again.soil()).isEqualTo(r.soil());
    }

    @Test
    void noLightMeansNoGrowthAndEventuallyDeath() {
        CropGrowth.Result r = CropSettle.settle(CropType.WHEAT, env(true, false, true), CropPlant.planted(0L), Nutrients.EMPTY,
            clockAt(100 * DAY));
        assertThat(r.dead()).isTrue();
        assertThat(r.fullyGrown()).isFalse();
    }

    @Test
    void aTwoBlockCropStopsAtItsBottomWhenTheCellAboveIsBlocked() {
        Nutrients soil = new Nutrients(1f, 1f, 1f);
        CropGrowth.Result free = CropSettle.settle(CropType.JUTE, env(true, true, true), CropPlant.planted(0L), soil, clockAt(40 * DAY));
        CropGrowth.Result blocked = CropSettle.settle(CropType.JUTE, env(true, true, false), CropPlant.planted(0L), soil, clockAt(40 * DAY));
        if (!blocked.dead()) {
            assertThat(blocked.plant().state().growth()).isLessThanOrEqualTo(2f / 5f);
        }
        if (!free.dead()) {
            assertThat(free.plant().state().growth()).isGreaterThan(blocked.dead() ? 0f : blocked.plant().state().growth() - 1e-6f);
        }
    }
}
