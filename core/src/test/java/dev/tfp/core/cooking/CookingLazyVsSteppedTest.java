/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/AbstractFirepitBlockEntity.java @ c3b4d9536 (serverTick)
 */
package dev.tfp.core.cooking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.tfp.core.cooking.PotRules.PotState;
import dev.tfp.core.fuel.Fuel;
import dev.tfp.core.fuel.FuelTable;
import dev.tfp.core.heat.HeatConfig;
import dev.tfp.core.heat.HeatState;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * The lazy forms against the live rules stepped one tick at a time, over seeded random states. The stepped side is the
 * per-tick rule ({@link FirepitRules#step}, {@link HeatState#heat}, the pot counter loop), so a mismatch means the closed
 * form is wrong. The closed forms also have to compose: a window split in two gives the same state as one window.
 */
class CookingLazyVsSteppedTest {
    private static final List<Fuel> FUELS = List.of(
        FuelTable.byId("oak_logs").orElseThrow(),
        FuelTable.byId("coal").orElseThrow(),
        FuelTable.byId("groundcover/pinecone").orElseThrow(),
        FuelTable.byId("leaves").orElseThrow());

    @Test
    void firepitLazyEqualsStepping() {
        Random random = new Random(0x7F9A);
        for (int trial = 0; trial < 3000; trial++) {
            FirepitState start = randomFirepit(random);
            List<Fuel> queue = randomQueue(random);
            boolean raining = random.nextBoolean();
            long ticks = random.nextInt(3001);

            FirepitRules.Advance lazy = FirepitRules.advance(start, ticks, raining, queue, FirepitRules.DeviceConfig.DEFAULT);
            Stepped stepped = stepFirepit(start, ticks, raining, queue);

            String what = start + " queue=" + queue.size() + " raining=" + raining + " ticks=" + ticks;
            assertThat(lazy.state().burnTicks()).as(what).isEqualTo(stepped.state.burnTicks());
            assertThat(lazy.state().burnTemperature()).as(what).isEqualTo(stepped.state.burnTemperature());
            assertThat(lazy.state().airTicks()).as(what).isEqualTo(stepped.state.airTicks());
            assertThat(lazy.consumed()).as(what).isEqualTo(stepped.consumed);
            assertThat(lazy.state().temperature()).as(what).isCloseTo(stepped.state.temperature(), within(0.05f));
        }
    }

    @Test
    void firepitWindowsCompose() {
        Random random = new Random(0x51DE);
        for (int trial = 0; trial < 1500; trial++) {
            FirepitState start = randomFirepit(random);
            List<Fuel> queue = randomQueue(random);
            boolean raining = random.nextBoolean();
            long a = random.nextInt(1500);
            long b = random.nextInt(1500);

            FirepitRules.Advance whole = FirepitRules.advance(start, a + b, raining, queue, FirepitRules.DeviceConfig.DEFAULT);
            FirepitRules.Advance first = FirepitRules.advance(start, a, raining, queue, FirepitRules.DeviceConfig.DEFAULT);
            FirepitRules.Advance second = FirepitRules.advance(first.state(), b, raining,
                queue.subList(first.consumed(), queue.size()), FirepitRules.DeviceConfig.DEFAULT);

            String what = start + " a=" + a + " b=" + b;
            assertThat(second.state().burnTicks()).as(what).isEqualTo(whole.state().burnTicks());
            assertThat(first.consumed() + second.consumed()).as(what).isEqualTo(whole.consumed());
            assertThat(second.state().temperature()).as(what).isCloseTo(whole.state().temperature(), within(0.05f));
        }
    }

    @Test
    void grillLazyEqualsHeatStateStepping() {
        Random random = new Random(0x6A11);
        float[] capacities = {1f, 2.5f, 4f, 25f, Float.POSITIVE_INFINITY};
        for (int trial = 0; trial < 1500; trial++) {
            float start = random.nextInt(1600);
            float capacity = capacities[random.nextInt(capacities.length)];
            float device = random.nextInt(1601);
            int ticks = random.nextInt(2001);

            HeatState item = new HeatState(start, 0);
            for (int t = 1; t <= ticks; t++) {
                item = item.heat(device, t, capacity, HeatConfig.DEFAULT);
            }
            float lazy = GrillRules.advance(start, capacity, device, ticks, HeatConfig.DEFAULT);
            String what = "start=" + start + " cap=" + capacity + " device=" + device + " ticks=" + ticks;
            float stepped = item.temperatureAt(ticks, capacity, HeatConfig.DEFAULT);
            assertThat(lazy).as(what).isCloseTo(stepped, within(GrillGoldenTest.tolerance(stepped, ticks)));
        }
    }

    @Test
    void cookTimeIsTheFirstSteppedTickAtRecipeTemperature() {
        Random random = new Random(0xC00C);
        for (int trial = 0; trial < 200; trial++) {
            float start = random.nextInt(900);
            float capacity = new float[] {1f, 2.5f, 4f}[random.nextInt(3)];
            float device = 300 + random.nextInt(1201);
            float recipe = random.nextInt((int) device + 1);

            long expected = -1;
            HeatState item = new HeatState(start, 0);
            for (int t = 1; t <= 20000 && expected < 0; t++) {
                item = item.heat(device, t, capacity, HeatConfig.DEFAULT);
                if (item.temperatureAt(t, capacity, HeatConfig.DEFAULT) >= recipe) {
                    expected = t;
                }
            }
            long lazy = GrillRules.ticksToCook(start, capacity, device, recipe, HeatConfig.DEFAULT);
            // the closed form is exact in real numbers; float steps may land one tick later on an exact tie
            assertThat(Math.abs(lazy - expected)).as("start=" + start + " cap=" + capacity + " device=" + device + " recipe=" + recipe).isLessThanOrEqualTo(1);
        }
    }

    @Test
    void potLazyEqualsCounterStepping() {
        Random random = new Random(0x907);
        for (int trial = 0; trial < 3000; trial++) {
            int duration = random.nextInt(300);
            int pre = random.nextInt(PotRules.PRE_BOIL_TIME + 1);
            int boil = random.nextInt(duration + 1);
            boolean boiling = random.nextBoolean();
            long ticks = random.nextInt(600);

            PotRules.Advance lazy = PotRules.advance(new PotState(pre, boil), ticks, boiling, duration);

            int p = pre;
            int b = boil;
            long finished = -1;
            for (long t = 1; t <= ticks; t++) {
                if (boiling) {
                    if (p < PotRules.PRE_BOIL_TIME) {
                        p++;
                        continue;
                    }
                    if (b < duration) {
                        b++;
                        continue;
                    }
                    finished = t;
                    p = 0;
                    b = 0;
                    break;
                } else if (b > 0) {
                    b = 0;
                    p = 0;
                }
            }
            String what = "pre=" + pre + " boil=" + boil + " dur=" + duration + " boiling=" + boiling + " ticks=" + ticks;
            assertThat(lazy.finishedAtTick()).as(what).isEqualTo(finished);
            assertThat(lazy.state()).as(what).isEqualTo(new PotState(p, b));
        }
    }

    private record Stepped(FirepitState state, int consumed) {
    }

    private static Stepped stepFirepit(FirepitState start, long ticks, boolean raining, List<Fuel> queue) {
        FirepitState cur = start;
        int used = 0;
        for (long t = 0; t < ticks; t++) {
            FirepitRules.Step step = FirepitRules.step(cur, raining, used < queue.size() ? queue.get(used) : null,
                FirepitRules.DeviceConfig.DEFAULT);
            if (step.consumedFuel()) {
                used++;
            }
            cur = step.state();
        }
        return new Stepped(cur, used);
    }

    private static FirepitState randomFirepit(Random random) {
        int burn = random.nextInt(4) == 0 ? 0 : 1 + random.nextInt(2500);
        float burnTemperature = burn == 0 ? 0f : FUELS.get(random.nextInt(FUELS.size())).temperature();
        float temperature = random.nextInt(3) == 0 ? random.nextInt(1601) : random.nextInt(1601) + random.nextFloat();
        int air = random.nextInt(3) == 0 ? 0 : random.nextInt(FirepitState.MAX_AIR_TICKS + 1);
        return new FirepitState(burn, burnTemperature, temperature, air);
    }

    private static List<Fuel> randomQueue(Random random) {
        List<Fuel> queue = new ArrayList<>();
        int size = random.nextInt(4);
        for (int i = 0; i < size; i++) {
            queue.add(FUELS.get(random.nextInt(FUELS.size())));
        }
        return queue;
    }
}
