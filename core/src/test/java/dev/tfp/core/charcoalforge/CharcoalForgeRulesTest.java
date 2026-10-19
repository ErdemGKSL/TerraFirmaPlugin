/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/CharcoalForgeBlockEntity.java @ c3b4d9536 (serverTick, light,
 *       consumeFuel, extinguish, intakeAir, onCalendarUpdate)
 */
package dev.tfp.core.charcoalforge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import dev.tfp.core.cooking.FirepitRules;
import dev.tfp.core.fuel.Fuel;
import dev.tfp.core.fuel.FuelTable;
import dev.tfp.core.heat.HeatConfig;
import dev.tfp.core.heat.HeatState;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * The charcoal forge rules: validation, the live step, the lazy form against the live step over seeded random states,
 * light, the bellows intake and the item heat step.
 */
class CharcoalForgeRulesTest {
    private static final FirepitRules.DeviceConfig DEVICE = FirepitRules.DeviceConfig.DEFAULT;
    private static final List<String> FUEL_IDS = List.of("charcoal", "coal", "oak_logs", "leaves");

    @Test
    void levelTemperatureMapsBackToTheSameHeatLevel() {
        assertThat(CharcoalForgeRules.levelTemperature(0)).isZero();
        for (int level = 1; level <= CharcoalForgeRules.MAX_HEAT_LEVEL; level++) {
            assertThat(CharcoalForgeRules.heatLevel(CharcoalForgeRules.levelTemperature(level))).as("level " + level)
                .isEqualTo(level);
        }
        assertThat(CharcoalForgeRules.levelTemperature(7)).as("the top level is the visible maximum").isEqualTo(1600f);
        assertThat(CharcoalForgeRules.levelTemperature(99)).isEqualTo(1600f);
        assertThat(CharcoalForgeRules.levelTemperature(-1)).isZero();
    }

    @Test
    void rejectsImpossibleStates() {
        assertThatThrownBy(() -> new CharcoalForgeState(true, -1, 0f, 0f, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CharcoalForgeState(false, 5, 0f, 0f, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CharcoalForgeState(true, 5, -1f, 0f, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CharcoalForgeState(true, 5, 0f, Float.NaN, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CharcoalForgeState(true, 5, 0f, 0f, 601)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CharcoalForgeState(true, 5, 0f, 0f, -1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void burnRateDoublesWithAirOrRain() {
        assertThat(CharcoalForgeRules.burnRate(0, false)).isEqualTo(1);
        assertThat(CharcoalForgeRules.burnRate(0, true)).isEqualTo(2);
        assertThat(CharcoalForgeRules.burnRate(1, false)).isEqualTo(2);
        assertThat(CharcoalForgeRules.burnRate(600, true)).isEqualTo(2);
    }

    @Test
    void unlitForgeWithBurnTemperatureIsExtinguishedOnTheNextTick() {
        CharcoalForgeState state = new CharcoalForgeState(false, 0, 500f, 400f, 0);
        CharcoalForgeRules.Step step = CharcoalForgeRules.step(state, false, null, DEVICE);
        assertThat(step.state()).isEqualTo(new CharcoalForgeState(false, 0, 0f, 399f, 0));
        assertThat(step.heatLevel()).isZero();
    }

    @Test
    void heatLevelIsTakenFromTheTemperatureBeforeItMoves() {
        // Starts at 1600 C (level 7), lit with burn to spare: the level shown is 7 even though the temperature then moves.
        CharcoalForgeState state = new CharcoalForgeState(true, 50, 1600f, 1600f, 0);
        CharcoalForgeRules.Step step = CharcoalForgeRules.step(state, false, null, DEVICE);
        assertThat(step.heatLevel()).isEqualTo(7);
        assertThat(step.state().temperature()).isEqualTo(1600f);
    }

    @Test
    void lazyFormEqualsSteppingOverRandomStates() {
        Random random = new Random(0xC4A7C0A1L);
        for (int trial = 0; trial < 3000; trial++) {
            CharcoalForgeState start = randomState(random);
            List<Fuel> queue = randomQueue(random);
            boolean raining = random.nextBoolean();
            long ticks = random.nextInt(3001);

            CharcoalForgeRules.Advance lazy = CharcoalForgeRules.advance(start, ticks, raining, queue, DEVICE);
            Stepped stepped = stepped(start, ticks, raining, queue);

            String what = start + " queue=" + queue.size() + " raining=" + raining + " ticks=" + ticks;
            assertThat(lazy.state().lit()).as(what).isEqualTo(stepped.state.lit());
            assertThat(lazy.state().burnTicks()).as(what).isEqualTo(stepped.state.burnTicks());
            assertThat(lazy.state().burnTemperature()).as(what).isEqualTo(stepped.state.burnTemperature());
            assertThat(lazy.state().temperature()).as(what).isCloseTo(stepped.state.temperature(), within(1e-3f));
            assertThat(lazy.state().airTicks()).as(what).isEqualTo(stepped.state.airTicks());
            assertThat(lazy.consumed()).as(what).isEqualTo(stepped.consumed);
        }
    }

    @Test
    void lazyFormComposes() {
        // Advancing t1 then t2 gives the same state as advancing t1 + t2 at once.
        Random random = new Random(0x5EED7L);
        for (int trial = 0; trial < 1000; trial++) {
            CharcoalForgeState start = randomState(random);
            List<Fuel> queue = randomQueue(random);
            boolean raining = random.nextBoolean();
            long t1 = random.nextInt(1500);
            long t2 = random.nextInt(1500);

            CharcoalForgeRules.Advance whole = CharcoalForgeRules.advance(start, t1 + t2, raining, queue, DEVICE);
            CharcoalForgeRules.Advance first = CharcoalForgeRules.advance(start, t1, raining, queue, DEVICE);
            CharcoalForgeRules.Advance second = CharcoalForgeRules.advance(first.state(), t2, raining,
                queue.subList(first.consumed(), queue.size()), DEVICE);

            String what = start + " t1=" + t1 + " t2=" + t2;
            assertThat(second.state().lit()).as(what).isEqualTo(whole.state().lit());
            assertThat(second.state().burnTicks()).as(what).isEqualTo(whole.state().burnTicks());
            assertThat(second.state().burnTemperature()).as(what).isEqualTo(whole.state().burnTemperature());
            assertThat(second.state().temperature()).as(what).isCloseTo(whole.state().temperature(), within(1e-3f));
            assertThat(second.state().airTicks()).as(what).isEqualTo(whole.state().airTicks());
            assertThat(first.consumed() + second.consumed()).as(what).isEqualTo(whole.consumed());
        }
    }

    @Test
    void lightTakesTheNextFuelAndIsANoOpWhenAlreadyBurning() {
        Fuel charcoal = FuelTable.byId("charcoal").orElseThrow();
        CharcoalForgeRules.Light lit = CharcoalForgeRules.light(CharcoalForgeState.COLD, charcoal);
        assertThat(lit.lit()).isTrue();
        assertThat(lit.consumedFuel()).isTrue();
        assertThat(lit.state()).isEqualTo(new CharcoalForgeState(true, 1800, 1350f, 0f, 0));

        CharcoalForgeRules.Light again = CharcoalForgeRules.light(lit.state(), charcoal);
        assertThat(again.lit()).isTrue();
        assertThat(again.consumedFuel()).isFalse();
        assertThat(again.state()).isEqualTo(lit.state());
    }

    @Test
    void lightWithoutFuelDoesNothing() {
        CharcoalForgeRules.Light out = CharcoalForgeRules.light(CharcoalForgeState.COLD, null);
        assertThat(out.lit()).isFalse();
        assertThat(out.consumedFuel()).isFalse();
        assertThat(out.state()).isEqualTo(CharcoalForgeState.COLD);
    }

    @Test
    void bellowsAirIsCappedAtTheDeviceMaximum() {
        CharcoalForgeState state = CharcoalForgeRules.intakeAir(CharcoalForgeState.COLD, BellowsRules.BELLOWS_AIR);
        assertThat(state.airTicks()).isEqualTo(200);
        state = CharcoalForgeRules.intakeAir(new CharcoalForgeState(false, 0, 0f, 0f, 500), BellowsRules.BELLOWS_AIR);
        assertThat(state.airTicks()).isEqualTo(600);
    }

    @Test
    void itemHeatStepUsesTheDefaultModifierAndTheDeviceTarget() {
        // addTemp(item, 500) with the default modifier 3: (0.8 - 1 + 3 x 1) / capacity 1 = 2.8 per step, below the target.
        HeatState item = HeatState.cold(10L);
        HeatState out = CharcoalForgeRules.heatInput(item, 500f, 1f, 11L, HeatConfig.DEFAULT);
        assertThat(out.temperature()).isCloseTo(2.8f, within(1e-4f));
        assertThat(out.lastTick()).isEqualTo(11L);
    }

    @Test
    void itemHeatStepStopsAtTheDeviceTarget() {
        HeatState item = new HeatState(499f, 10L);
        HeatState out = CharcoalForgeRules.heatInput(item, 500f, 1f, 10L, HeatConfig.DEFAULT);
        assertThat(out.temperature()).isEqualTo(500f);
    }

    private static CharcoalForgeState randomState(Random random) {
        boolean lit = random.nextInt(3) != 0;
        int burn = lit && random.nextInt(4) != 0 ? random.nextInt(400) : 0;
        float[] temps = {0f, 350f, 500f, 1350f};
        float burnTemperature = lit || random.nextBoolean() ? temps[random.nextInt(temps.length)] : 0f;
        float temperature = random.nextInt(4) == 0 ? 0f : random.nextFloat() * 1600f;
        int air = random.nextInt(3) == 0 ? 0 : random.nextInt(BellowsRules.MAX_DEVICE_AIR_TICKS + 1);
        return new CharcoalForgeState(lit, burn, burnTemperature, temperature, air);
    }

    private static List<Fuel> randomQueue(Random random) {
        List<Fuel> queue = new ArrayList<>();
        int size = random.nextInt(5);
        for (int i = 0; i < size; i++) {
            queue.add(FuelTable.byId(FUEL_IDS.get(random.nextInt(FUEL_IDS.size()))).orElseThrow());
        }
        return queue;
    }

    private static Stepped stepped(CharcoalForgeState start, long ticks, boolean raining, List<Fuel> queue) {
        CharcoalForgeState cur = start;
        int used = 0;
        for (long t = 0; t < ticks; t++) {
            Fuel next = used < queue.size() ? queue.get(used) : null;
            CharcoalForgeRules.Step step = CharcoalForgeRules.step(cur, raining, next, DEVICE);
            if (step.consumedFuel()) {
                used++;
            }
            cur = step.state();
        }
        return new Stepped(cur, used);
    }

    private record Stepped(CharcoalForgeState state, int consumed) {
    }
}
