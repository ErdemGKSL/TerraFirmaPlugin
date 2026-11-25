/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/BlastFurnaceBlockEntity.java @ c3b4d9536 (serverTick)
 */
package dev.tfp.core.blastfurnace;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.blastfurnace.BlastFurnaceRules.Charge;
import dev.tfp.core.blastfurnace.BlastFurnaceRules.FluidAmount;
import dev.tfp.core.blastfurnace.BlastFurnaceRules.FuelHead;
import dev.tfp.core.blastfurnace.BlastFurnaceRules.Tank;
import dev.tfp.core.cooking.FirepitRules;
import dev.tfp.core.crucible.CrucibleRecipe;
import dev.tfp.core.fuel.Fuel;
import dev.tfp.core.heat.HeatConfig;
import dev.tfp.core.heat.HeatState;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The lazy replay equals the stepped TFC tick, and splits of a span give the same result. */
class BlastFurnaceSimTest {
    private static final FirepitRules.DeviceConfig DEVICE = FirepitRules.DeviceConfig.DEFAULT;
    private static final HeatConfig HEAT = HeatConfig.DEFAULT;
    private static final BlastFurnaceRules.Config CONFIG = BlastFurnaceRules.Config.DEFAULT;
    private static final String CAST_IRON = "tfc:metal/cast_iron";
    private static final String PIG_IRON = "tfc:metal/pig_iron";
    private static final List<BlastFurnaceRecipe> RECIPES =
        List.of(new BlastFurnaceRecipe(CAST_IRON, 1, "tfc:powder/flux", PIG_IRON, 1));
    private static final Fuel CHARCOAL = new Fuel("minecraft:charcoal", 1800, 1350f, 1f);
    private static final CrucibleRecipe IRON_MELT = new CrucibleRecipe("tfc:metal/ingot/cast_iron", 1000f, CAST_IRON, 100, false, false);

    private static Charge charge() {
        return new Charge(HeatState.cold(0), 0.5f, IRON_MELT, 0, 0);
    }

    private static BlastFurnaceData lit() {
        return BlastFurnaceData.FRESH.withState(new BlastFurnaceState(true, 0, 0f, 0, 0f));
    }

    /** TFC's loop, tick by tick, with the same rules (the reference). */
    private static BlastFurnaceSim.Result stepped(BlastFurnaceData data, long from, long to, List<Fuel> queue, boolean tuyere,
            List<Charge> charges) {
        BlastFurnaceState state = data.state();
        Tank input = data.input();
        Tank output = data.output();
        long dripTick = data.dripTick();
        List<Charge> current = new ArrayList<>(charges);
        List<Integer> alive = new ArrayList<>();
        for (int i = 0; i < charges.size(); i++) {
            alive.add(i);
        }
        List<Integer> melted = new ArrayList<>();
        int used = 0;
        int damage = 0;
        for (long t = from + 1; t <= to; t++) {
            BlastFurnaceRules.TickResult r = BlastFurnaceRules.tick(state, used < queue.size() ? FuelHead.of(queue.get(used)) : FuelHead.EMPTY,
                CONFIG, DEVICE);
            used += r.consumedFuel() ? 1 : 0;
            damage += r.tuyereDamaged() && tuyere ? 1 : 0;
            state = r.state();
            if (!r.hot()) {
                continue;
            }
            List<FluidAmount> produced = new ArrayList<>();
            for (int k = 0; k < current.size(); ) {
                BlastFurnaceRules.MeltResult m = BlastFurnaceRules.melt(List.of(current.get(k)), state.temperature(), t, HEAT);
                if (m.remaining().isEmpty()) {
                    produced.addAll(m.produced());
                    melted.add(alive.remove(k));
                    current.remove(k);
                } else {
                    current.set(k, m.remaining().getFirst());
                    k++;
                }
            }
            boolean wasEmpty = output.isEmpty();
            BlastFurnaceRules.Converted c = BlastFurnaceRules.convert(input, produced, RECIPES, output, CONFIG.fluidCapacity());
            input = c.input();
            output = c.output();
            if (wasEmpty && !output.isEmpty()) {
                dripTick = t;
            }
        }
        return new BlastFurnaceSim.Result(new BlastFurnaceData(state, input, output, dripTick), used, damage, current, melted);
    }

    private static BlastFurnaceSim.Result run(BlastFurnaceData data, long from, long to, List<Fuel> queue, boolean tuyere,
            List<Charge> charges) {
        return BlastFurnaceSim.advance(data, from, to, queue, tuyere, charges, RECIPES, CONFIG, DEVICE, HEAT);
    }

    @Test
    void theReplayEqualsTheSteppedTick() {
        List<Charge> charges = List.of(charge(), charge(), charge());
        List<Fuel> queue = List.of(CHARCOAL, CHARCOAL);
        for (long to : new long[] {1, 50, 400, 1500, 4000, 20_000}) {
            BlastFurnaceSim.Result a = run(lit().withState(new BlastFurnaceState(true, 0, 0f, 300, 0f)), 0, to, queue, true, charges);
            BlastFurnaceSim.Result b = stepped(lit().withState(new BlastFurnaceState(true, 0, 0f, 300, 0f)), 0, to, queue, true, charges);
            assertThat(a).as("to=%d", to).isEqualTo(b);
        }
    }

    @Test
    void aSpanSplitInTwoGivesTheSameResult() {
        List<Charge> charges = List.of(charge(), charge());
        List<Fuel> queue = List.of(CHARCOAL, CHARCOAL, CHARCOAL);
        BlastFurnaceData start = lit().withState(new BlastFurnaceState(true, 0, 0f, 600, 0f));
        BlastFurnaceSim.Result whole = run(start, 0, 3000, queue, true, charges);
        for (long mid : new long[] {1, 17, 600, 1234, 2999}) {
            BlastFurnaceSim.Result first = run(start, 0, mid, queue, true, charges);
            BlastFurnaceSim.Result second = run(first.data(), mid, 3000, queue.subList(first.fuelUsed(), queue.size()), true,
                first.charges());
            assertThat(second.data()).as("mid=%d", mid).isEqualTo(whole.data());
            assertThat(first.fuelUsed() + second.fuelUsed()).isEqualTo(whole.fuelUsed());
            assertThat(first.tuyereDamage() + second.tuyereDamage()).isEqualTo(whole.tuyereDamage());
            assertThat(second.charges()).isEqualTo(whole.charges());
            assertThat(first.melted().size() + second.melted().size()).isEqualTo(whole.melted().size());
        }
    }

    @Test
    void aBurningFurnaceMeltsItsChargesIntoPigIron() {
        BlastFurnaceSim.Result r = run(lit(), 0, 6000, List.of(CHARCOAL, CHARCOAL, CHARCOAL, CHARCOAL), false,
            List.of(charge(), charge()));
        assertThat(r.melted()).containsExactly(0, 1);
        assertThat(r.charges()).isEmpty();
        // 2 x 100 mB of cast iron, one flux per mB: pig iron 1:1 (TFC recipe amounts 1 : 1)
        assertThat(r.data().output()).isEqualTo(new Tank(PIG_IRON, 200));
        assertThat(r.data().input().amount()).isZero();
    }

    @Test
    void theOutputRemembersTheTickItStartedToDrip() {
        BlastFurnaceSim.Result r = run(lit(), 0, 6000, List.of(CHARCOAL, CHARCOAL, CHARCOAL, CHARCOAL), false, List.of(charge()));
        assertThat(r.data().output().isEmpty()).isFalse();
        assertThat(r.data().dripTick()).isBetween(1L, 6000L);
        // a second charge melting into a tank that already holds output leaves the drip start alone
        BlastFurnaceSim.Result again = run(r.data(), 6000, 9000, List.of(), false, List.of(charge()));
        assertThat(again.data().dripTick()).isEqualTo(r.data().dripTick());
    }

    @Test
    void theFuelRunsOutAndTheFurnaceCoolsToCold() {
        BlastFurnaceSim.Result r = run(lit(), 0, 20_000, List.of(CHARCOAL), false, List.of());
        assertThat(r.fuelUsed()).isEqualTo(1);
        assertThat(r.data().state()).isEqualTo(BlastFurnaceState.COLD);
    }

    @Test
    void anUnlitColdFurnaceOnlyLosesAir() {
        BlastFurnaceData cold = BlastFurnaceData.FRESH.withState(new BlastFurnaceState(false, 0, 0f, 400, 0f));
        assertThat(run(cold, 100, 150, List.of(), true, List.of(charge())).data().state().airTicks()).isEqualTo(350);
        assertThat(run(cold, 100, 100_000, List.of(), true, List.of(charge())).data().state().airTicks()).isZero();
        assertThat(run(cold, 100, 100_000, List.of(), true, List.of(charge())).tuyereDamage()).isZero();
    }

    @Test
    void theTuyereTakesDamageOnlyWhileHotAndBlowing() {
        BlastFurnaceData hot = lit().withState(new BlastFurnaceState(true, 5000, 1350f, 600, 900f));
        BlastFurnaceSim.Result r = run(hot, 0, 600, List.of(), true, List.of());
        assertThat(r.tuyereDamage()).isEqualTo(30); // every 20th air tick of 600
        assertThat(run(hot, 0, 600, List.of(), false, List.of()).tuyereDamage()).isZero();
    }

    @Test
    void aFullOutputTankVoidsTheExcess() {
        BlastFurnaceData full = lit().withOutput(new Tank(PIG_IRON, 9990));
        BlastFurnaceSim.Result r = run(full, 0, 6000, List.of(CHARCOAL, CHARCOAL, CHARCOAL, CHARCOAL), false,
            List.of(charge(), charge()));
        assertThat(r.data().output()).isEqualTo(new Tank(PIG_IRON, CONFIG.fluidCapacity()));
    }
}
