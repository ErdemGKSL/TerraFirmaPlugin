/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.crucible;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.cooking.FirepitRules;
import dev.tfp.core.heat.HeatConfig;
import dev.tfp.core.heat.HeatState;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** PAR-METAL-12: the lazy replay of TFC's crucible serverTick. */
class CrucibleSimTest {
    private static final FirepitRules.DeviceConfig DEVICE = FirepitRules.DeviceConfig.DEFAULT;
    private static final HeatConfig HEAT = HeatConfig.DEFAULT;
    private static final CrucibleRecipe COPPER = new CrucibleRecipe("tfc:ore/small_native_copper", 1080f,
        "tfc:metal/copper", 10, false, false);

    private static List<CrucibleSim.Input> inputs(CrucibleSim.Input first) {
        List<CrucibleSim.Input> out = new ArrayList<>();
        out.add(first);
        for (int i = 1; i < CrucibleRules.INPUT_SLOTS; i++) {
            out.add(null);
        }
        return out;
    }

    private static CrucibleSim.Input copper(long tick) {
        return new CrucibleSim.Input(HeatState.cold(tick), 1f, COPPER, 0, 0);
    }

    @Test
    void emptyCrucibleCoolsByTheClosedFormWithoutStepping() {
        CrucibleData hot = CrucibleData.FRESH.withTemperature(new CrucibleTemperature(900f, 900f, 0));
        CrucibleSim.Result r = CrucibleSim.advance(hot, 0, 5000, List.of(), inputs(null), 4000, DEVICE, HEAT);
        CrucibleTemperature expected = hot.temperature().catchUp(5000, DEVICE);
        assertThat(r.data().temperature()).isEqualTo(expected);
        assertThat(r.finished()).isTrue();
        assertThat(r.stoppedAt()).isEqualTo(5000);
    }

    @Test
    void aHeatRunSteppedTickByTickEqualsTheWholeSpan() {
        List<CrucibleSim.Run> runs = List.of(new CrucibleSim.Run(11, 400, 1300f));
        CrucibleSim.Result whole = CrucibleSim.advance(CrucibleData.FRESH, 10, 900, runs, inputs(null), 4000, DEVICE, HEAT);
        CrucibleData stepped = CrucibleData.FRESH;
        for (long t = 10; t < 900; t++) {
            stepped = CrucibleSim.advance(stepped, t, t + 1, runs, inputs(null), 4000, DEVICE, HEAT).data();
        }
        assertThat(whole.data().temperature().temperature()).isEqualTo(stepped.temperature().temperature());
        assertThat(whole.data().temperature().target()).isEqualTo(stepped.temperature().target());
    }

    @Test
    void anItemMeltsOnTheTickTfcsAddTempFormulaReachesTheRecipeTemperature() {
        // TFC: every tick temperature += (cooling - 1 + (2 + crucible x 0.0025) x heating) / capacity, capped at the crucible
        // (the item first cools by cooling / capacity; HeatState.heat does both)
        List<CrucibleSim.Run> runs = List.of(new CrucibleSim.Run(1, 100_000, 1500f));
        CrucibleSim.Result r = CrucibleSim.advance(CrucibleData.FRESH, 0, 100_000, runs, inputs(copper(0)), 4000, DEVICE, HEAT);
        assertThat(r.conversions()).hasSize(1);
        CrucibleSim.Conversion c = r.conversions().get(0);
        assertThat(c.slot()).isZero();
        assertThat(c.fluid()).isEqualTo("tfc:metal/copper");
        assertThat(c.made()).isEqualTo(10);
        assertThat(c.accepted()).isEqualTo(10);
        assertThat(r.data().alloy().amount()).isEqualTo(10);
        // the independent loop: the crucible warms toward the source by FirepitRules, the item follows
        CrucibleTemperature crucible = CrucibleTemperature.COLD;
        float item = 0f;
        long expected = -1;
        for (long t = 1; t <= 100_000 && expected < 0; t++) {
            crucible = crucible.setTemperature(1500f).tick(DEVICE);
            float c0 = crucible.temperature();
            item = Math.max(0f, item - (float) HEAT.coolingModifier() / 1f);
            float step = (float) HEAT.coolingModifier() - 1 + CrucibleRules.itemHeatModifier(c0) * (float) HEAT.heatingModifier();
            float next = Math.min(c0, item + step / 1f);
            if (next > item) {
                item = next;
            }
            if (item >= 1080f) {
                expected = t;
            }
        }
        assertThat(r.stoppedAt()).isEqualTo(expected);
    }

    @Test
    void theAlloyFillIsCappedAtTheCapacityAndTheOverflowIsLost() {
        CrucibleRecipe big = new CrucibleRecipe("x", 100f, "tfc:metal/copper", 500, false, false);
        CrucibleSim.Input in = new CrucibleSim.Input(new HeatState(2000f, 0), 1f, big, 0, 0);
        CrucibleData full = CrucibleData.FRESH.withAlloy(dev.tfp.core.alloy.FluidAlloy.empty()
            .fill("tfc:metal/copper", 400, 450).alloy());
        List<CrucibleSim.Run> runs = List.of(new CrucibleSim.Run(1, 50, 1500f));
        CrucibleSim.Result r = CrucibleSim.advance(full, 0, 50, runs, inputs(in), 450, DEVICE, HEAT);
        assertThat(r.conversions()).hasSize(1);
        assertThat(r.conversions().get(0).accepted()).isEqualTo(50);
        assertThat(r.data().alloy().amount()).isEqualTo(450);
    }

    @Test
    void aColdCrucibleWithNoRunLeavesTheItemsAlone() {
        CrucibleSim.Result r = CrucibleSim.advance(CrucibleData.FRESH, 0, 100_000, List.of(), inputs(copper(0)), 4000, DEVICE,
            HEAT);
        assertThat(r.finished()).isTrue();
        assertThat(r.stoppedAt()).isEqualTo(100_000);
        assertThat(r.heats().get(0)).isEqualTo(HeatState.cold(0));
    }

    @Test
    void theDataCodecRoundTrips() {
        CrucibleData d = new CrucibleData(new CrucibleTemperature(812.5f, 900f, 3),
            dev.tfp.core.alloy.FluidAlloy.empty().fill("tfc:metal/copper", 90, 4000).alloy()
                .fill("tfc:metal/tin", 10, 4000).alloy());
        assertThat(CrucibleData.CODEC.decode(CrucibleData.CODEC.encode(d)).alloy().contents()).isEqualTo(d.alloy().contents());
        assertThat(CrucibleData.CODEC.decode(CrucibleData.CODEC.encode(d)).temperature()).isEqualTo(d.temperature());
        assertThat(CrucibleData.CODEC.decode(CrucibleData.CODEC.encode(CrucibleData.FRESH))).isEqualTo(CrucibleData.FRESH);
    }

    @Test
    void thePluginsNamespaceOfAMetalMeltsLikeTfcs() {
        assertThat(CrucibleRules.meltTemperature("tfp:metal/copper")).isEqualTo(CrucibleRules.meltTemperature("tfc:metal/copper"));
        assertThat(CrucibleRules.fill(dev.tfp.core.alloy.FluidAlloy.empty(), "tfp:metal/copper", 10, 4000).accepted()).isEqualTo(10);
    }
}
