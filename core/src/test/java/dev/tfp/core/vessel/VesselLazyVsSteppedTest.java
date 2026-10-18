/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/component/heat/HeatCapability.java @ c3b4d9536 (adjustTemp),
 *       src/main/java/net/dries007/tfc/common/component/mold/Vessel.java @ c3b4d9536 (updateWith, updateHeatCapacity),
 *       src/main/java/net/dries007/tfc/common/component/heat/HeatComponent.java @ c3b4d9536 (withHeatCapacity)
 */
package dev.tfp.core.vessel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.tfp.core.heat.HeatConfig;
import dev.tfp.core.heat.HeatState;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Molds and vessels are never ticked. A jump over a gap must equal a step-by-step simulation (one calendar tick at a time)
 * under the same capacity, and a capacity change must settle the temperature at the change tick first.
 *
 * <p><b>Tolerance.</b> The rules are exact in real arithmetic. Each float step rounds its subtraction, so a stepped run
 * can drift by at most half an ulp of the largest temperature per step. The bound used is {@code steps x ulp(1600)}, a
 * proof bound over the temperatures used here, not a fitted value.
 */
class VesselLazyVsSteppedTest {
    private static final HeatConfig CFG = HeatConfig.DEFAULT;
    private static final float ULP_BOUND = Math.ulp(1600f);

    /** One calendar tick at a time, under one capacity. */
    private static float stepped(float start, float capacity, long ticks) {
        float t = start;
        for (long i = 0; i < ticks; i++) {
            t = HeatState.adjust(t, capacity, 1, CFG);
        }
        return t;
    }

    private static VesselSlot copper(int count) {
        return new VesselSlot("copper_ingot", count, 2.0f, new Melt(1000f, new FluidAmount("copper", 100), 0.5f, 1085f));
    }

    @Test
    void steppingOneTickAtATimeMatchesTheJump() {
        List<VesselSlot> contents = List.of(copper(3), VesselSlot.EMPTY, VesselSlot.EMPTY, VesselSlot.EMPTY);
        float capacity = VesselRules.contentHeatCapacity(contents, 0, 0f);
        for (float start : new float[] {1400f, 1000f, 500f, 3.5f}) {
            VesselState vessel = new VesselState(contents, Alloy.EMPTY, capacity, new HeatState(start, 0L));
            for (long ticks : new long[] {1L, 7L, 120L, 300L}) {
                float jump = vessel.temperatureAt(ticks, CFG);
                assertThat(stepped(start, capacity, ticks)).as("start %s, %s ticks", start, ticks)
                    .isCloseTo(jump, within(ticks * ULP_BOUND));
            }
        }
    }

    @Test
    void insertThenHeatCoolsWithTheContentCapacity() {
        // Insert while cold (only allowed in inventory mode), then heat: the cooling uses the content capacity.
        VesselState cold = VesselState.empty(0L);
        VesselState.InsertOutcome inserted = cold.insert(0L, 0, "copper_ingot", 4, 64, 2.0f, null, true, CFG);
        assertThat(inserted.remainder()).isZero();
        VesselState hot = inserted.state().heatTo(0L, 1000f, CFG);
        float capacity = VesselRules.contentHeatCapacity(hot.slots(), 0, 0f);
        assertThat(hot.heatCapacity()).isEqualTo(capacity);
        assertThat(hot.temperatureAt(200L, CFG)).isCloseTo(stepped(1000f, capacity, 200L), within(200 * ULP_BOUND));
    }

    @Test
    void moldFillSettlesAtTheFillTickThenCoolsUnderTheNewCapacity() {
        MoldState mold = MoldState.empty(MoldKind.INGOT, 100, 0L).heatTo(0L, 900f);
        float emptyCapacity = mold.heatCapacity();
        MoldState.FillOutcome filled = mold.fill(25L, new FluidAmount("copper", 100), true, 0.5f, CFG);
        assertThat(filled.filled()).isEqualTo(100);
        float before = stepped(900f, emptyCapacity, 25L);
        float after = stepped(before, filled.state().heatCapacity(), 75L);
        assertThat(filled.state().temperatureAt(100L, CFG)).isCloseTo(after, within(100 * ULP_BOUND));
    }

    @Test
    void splittingAGapAtAnyTickGivesTheSameTemperature() {
        VesselState vessel = VesselState.empty(0L).heatTo(0L, 1200f, CFG);
        float direct = vessel.temperatureAt(900L, CFG);
        for (long split : new long[] {1L, 50L, 333L, 899L}) {
            float mid = vessel.temperatureAt(split, CFG);
            VesselState resumed = new VesselState(vessel.slots(), vessel.alloy(), vessel.heatCapacity(),
                new HeatState(mid, split));
            assertThat(resumed.temperatureAt(900L, CFG)).isCloseTo(direct, within(900 * ULP_BOUND));
        }
    }
}
