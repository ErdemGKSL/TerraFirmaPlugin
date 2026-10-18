/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/CrucibleBlockEntity.java @ c3b4d9536
 */
package dev.tfp.core.crucible;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.tfp.core.alloy.FluidAlloy;
import dev.tfp.core.cooking.FirepitRules;
import dev.tfp.core.heat.HeatConfig;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CrucibleRulesTest {
    private static final String COPPER = "tfc:metal/copper";
    private static final FirepitRules.DeviceConfig DEVICE = FirepitRules.DeviceConfig.DEFAULT;

    @Test
    void fillStopsAtCapacityAndRejectsFluidsWithoutMeltTemperature() {
        FluidAlloy.Fill first = CrucibleRules.fill(FluidAlloy.empty(), COPPER, 3000, 4000);
        assertThat(first.accepted()).isEqualTo(3000);
        FluidAlloy.Fill second = CrucibleRules.fill(first.alloy(), COPPER, 2000, 4000);
        assertThat(second.accepted()).isEqualTo(1000);
        assertThat(second.alloy().amount()).isEqualTo(4000);
        assertThat(CrucibleRules.fill(second.alloy(), COPPER, 1, 4000).accepted()).isZero();
        assertThat(CrucibleRules.fill(FluidAlloy.empty(), "minecraft:water", 100, 4000).accepted()).isZero();
    }

    @Test
    void meltTemperatureComesFromTheMetalsAndUnknownIsFour() {
        assertThat(CrucibleRules.meltTemperature(COPPER)).contains(1080f);
        assertThat(CrucibleRules.meltTemperature(dev.tfp.core.metal.Metal.UNKNOWN_ID)).contains(400f);
        assertThat(CrucibleRules.meltTemperature("minecraft:water")).isEmpty();
    }

    @Test
    void moltenIsStrictlyAboveMeltTemperatureOrAlwaysForUnknownFluids() {
        assertThat(CrucibleRules.isMolten(COPPER, 1080f)).isFalse();
        assertThat(CrucibleRules.isMolten(COPPER, 1080.5f)).isTrue();
        assertThat(CrucibleRules.isMolten("", 0f)).isTrue();
        assertThat(CrucibleRules.isMolten("minecraft:water", 0f)).isTrue();
    }

    @Test
    void meltConvertsAtTheRecipeTemperatureAndNotBelow() {
        CrucibleRecipe recipe = new CrucibleRecipe("item:tfc:anemometer", 930f, "tfc:metal/brass", 200, false, false);
        assertThat(CrucibleRules.melt(recipe, 929.9f, 0, 0).converts()).isFalse();
        CrucibleRules.Melt melt = CrucibleRules.melt(recipe, 930f, 0, 0);
        assertThat(melt).isEqualTo(new CrucibleRules.Melt(true, "tfc:metal/brass", 200));
        assertThat(CrucibleRules.melt(null, 2000f, 0, 0)).isEqualTo(new CrucibleRules.Melt(false, "", 0));
    }

    @Test
    void durableRecipesScaleTheFluidByWearAndRoundDown() {
        CrucibleRecipe recipe = new CrucibleRecipe("item:x", 100f, COPPER, 100, true, false);
        assertThat(recipe.fluidAmount(0, 3)).isEqualTo(100);
        // 100 x (1 - 1/3) = 66.66, which floors to 66
        assertThat(recipe.fluidAmount(1, 3)).isEqualTo(66);
        // a non-wearing item keeps the full amount
        assertThat(recipe.fluidAmount(1, 0)).isEqualTo(100);
        CrucibleRecipe fixed = new CrucibleRecipe("item:x", 100f, COPPER, 100, false, false);
        assertThat(fixed.fluidAmount(1, 3)).isEqualTo(100);
    }

    @Test
    void itemHeatStepFollowsTfcAddTemp() {
        // (0.8 - 1 + 3 x 1.0) / 10 = 0.28
        assertThat(CrucibleRules.heatItemStep(0f, 10f, 100f, 3f, HeatConfig.DEFAULT)).isCloseTo(0.28f, within(1e-5f));
        // capped at the crucible temperature
        assertThat(CrucibleRules.heatItemStep(99.9f, 10f, 100f, 3f, HeatConfig.DEFAULT)).isEqualTo(100f);
        // a modifier that does not beat cooling (0.8 - 1 + 0.2 = 0) leaves the item where it is
        assertThat(CrucibleRules.heatItemStep(50f, 10f, 100f, 0.2f, HeatConfig.DEFAULT)).isEqualTo(50f);
        // the crucible heat modifier breaks even with cooling at 400 degrees
        assertThat(CrucibleRules.itemHeatModifier(400f)).isCloseTo(3f, within(1e-5f));
    }

    @Test
    void pourWaitsAfterEachUnitAndSkipsTransfersWhileWaiting() {
        CrucibleRules.Pour state = new CrucibleRules.Pour(0, -1, 0);
        state = CrucibleRules.pourTick(state, List.of(0), 4, 1);
        assertThat(state.lastFillTicks()).isEqualTo(3);
        // still waiting: the transfer is not read, the wait counts down
        state = CrucibleRules.pourTick(state, List.of(0), 4, 1);
        assertThat(state.lastFillTicks()).isEqualTo(2);
        state = CrucibleRules.pourTick(state, List.of(), 4, 1);
        state = CrucibleRules.pourTick(state, List.of(), 4, 1);
        assertThat(state.lastFillTicks()).isZero();
        // a pour is allowed again on the next tick; the counter is spent, so the normal rate applies
        state = CrucibleRules.pourTick(state, List.of(0), 4, 1);
        assertThat(state.lastFillTicks()).isEqualTo(3);
    }

    @Test
    void idleCrucibleFastPoursSlotZeroOnce() {
        // TFC's constructor sets fastPourTicks and fastPourSlot to 0, so the first unit from slot 0 uses the fast rate
        CrucibleRules.Pour first = CrucibleRules.pourTick(CrucibleRules.Pour.IDLE, List.of(0), 4, 1);
        assertThat(first.lastFillTicks()).isZero();
        assertThat(first.fastPourTicks()).isEqualTo(-1);
    }

    @Test
    void fastPourUsesTheFastRateForTheFastSlotOnly() {
        CrucibleRules.Pour state = CrucibleRules.Pour.IDLE.setFastPouring(3);
        assertThat(state.fastPourTicks()).isEqualTo(CrucibleRules.FAST_POUR_TICKS);
        state = CrucibleRules.pourTick(state, List.of(3), 4, 1);
        assertThat(state.lastFillTicks()).isZero();
        assertThat(state.fastPourTicks()).isEqualTo(CrucibleRules.FAST_POUR_TICKS - 1);
        // another slot pours at the normal rate, and does not use up the fast units
        state = CrucibleRules.pourTick(state, List.of(5), 4, 1);
        assertThat(state.lastFillTicks()).isEqualTo(3);
        assertThat(state.fastPourTicks()).isEqualTo(CrucibleRules.FAST_POUR_TICKS - 1);
        // with the counter exhausted, the fast slot pours at the normal rate
        CrucibleRules.Pour spent = new CrucibleRules.Pour(0, -1, 3);
        assertThat(CrucibleRules.pourTick(spent, List.of(3), 4, 1).lastFillTicks()).isEqualTo(3);
    }

    @Test
    void outputMovesOneUnitOnlyWhenMoltenAndAccepted() {
        FluidAlloy alloy = FluidAlloy.empty().fill(COPPER, 10, 4000).alloy();
        assertThat(CrucibleRules.outputTick(alloy, COPPER, 1100f, true).amount()).isEqualTo(9);
        assertThat(CrucibleRules.outputTick(alloy, COPPER, 1000f, true).amount()).isEqualTo(10);
        assertThat(CrucibleRules.outputTick(alloy, COPPER, 1100f, false).amount()).isEqualTo(10);
        assertThat(CrucibleRules.outputTick(FluidAlloy.empty(), "", 1100f, true).amount()).isZero();
    }

    @Test
    void drawNeedsAnAlloyThatMatchesTheFlowAndIsMolten() {
        FluidAlloy alloy = FluidAlloy.empty().fill(COPPER, 10, 4000).alloy();
        assertThat(CrucibleRules.canDraw(FluidAlloy.empty(), "", 1100f, Optional.empty())).isFalse();
        assertThat(CrucibleRules.canDraw(alloy, COPPER, 1100f, Optional.empty())).isTrue();
        assertThat(CrucibleRules.canDraw(alloy, COPPER, 1100f, Optional.of(COPPER))).isTrue();
        assertThat(CrucibleRules.canDraw(alloy, COPPER, 1100f, Optional.of("tfc:metal/tin"))).isFalse();
        assertThat(CrucibleRules.canDraw(alloy, COPPER, 500f, Optional.empty())).isFalse();
    }

    @Test
    void setTemperatureOnlyRaisesAndHoldsTheTarget() {
        CrucibleTemperature cold = CrucibleTemperature.COLD;
        CrucibleTemperature hot = cold.setTemperature(500f);
        assertThat(hot.temperature()).isEqualTo(500f);
        assertThat(hot.stabilityTicks()).isEqualTo(CrucibleTemperature.STABILITY_TICKS);
        // a lower value is ignored and does not reset the window
        assertThat(hot.setTemperature(100f)).isEqualTo(hot);
    }

    @Test
    void tickCoolsTheTemperatureTowardTheTargetAndTheTargetAfterTheWindow() {
        // no target: the temperature falls one degree a tick, and the target stays at 0
        CrucibleTemperature next = new CrucibleTemperature(100f, 0f, 0).tick(DEVICE);
        assertThat(next.temperature()).isEqualTo(99f);
        assertThat(next.target()).isZero();
        // the target decays while the window is closed
        CrucibleTemperature decay = new CrucibleTemperature(50f, 50f, 0).tick(DEVICE);
        assertThat(decay.target()).isEqualTo(49f);
        // within the window, the target holds
        CrucibleTemperature held = new CrucibleTemperature(50f, 50f, 3).tick(DEVICE);
        assertThat(held.target()).isEqualTo(50f);
        assertThat(held.stabilityTicks()).isEqualTo(2);
    }

    @Test
    void catchUpMatchesTheCalendarUpdate() {
        // the target cools over ten ticks first, then the temperature follows it
        CrucibleTemperature caught = new CrucibleTemperature(100f, 50f, 0).catchUp(10, DEVICE);
        assertThat(caught.target()).isEqualTo(40f);
        assertThat(caught.temperature()).isEqualTo(90f);
        assertThat(new CrucibleTemperature(100f, 0f, 0).catchUp(10, DEVICE).temperature()).isEqualTo(90f);
        assertThat(new CrucibleTemperature(100f, 0f, 0).catchUp(0, DEVICE)).isEqualTo(new CrucibleTemperature(100f, 0f, 0));
    }
}
