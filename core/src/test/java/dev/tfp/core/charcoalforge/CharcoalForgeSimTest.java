/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/CharcoalForgeBlockEntity.java @ c3b4d9536 (serverTick,
 *       handleInputMelting)
 */
package dev.tfp.core.charcoalforge;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.cooking.FirepitRules;
import dev.tfp.core.fuel.Fuel;
import dev.tfp.core.fuel.FuelTable;
import dev.tfp.core.heat.HeatConfig;
import dev.tfp.core.heat.HeatState;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** PAR-METAL-08: the lazy forge replay equals stepping, heats items to the forge temperature and stops at conversions. */
class CharcoalForgeSimTest {
    private static final FirepitRules.DeviceConfig DEVICE = FirepitRules.DeviceConfig.DEFAULT;
    private static final HeatConfig HEAT = HeatConfig.DEFAULT;
    private static final Fuel CHARCOAL = FuelTable.byId("charcoal").orElseThrow();

    private static List<CharcoalForgeSim.Input> inputs(CharcoalForgeSim.Input... in) {
        List<CharcoalForgeSim.Input> list = new ArrayList<>(Arrays.asList(in));
        while (list.size() < CharcoalForgeSim.INPUTS) {
            list.add(null);
        }
        return list;
    }

    private static CharcoalForgeSim.Input ingot(long tick, float recipe) {
        return new CharcoalForgeSim.Input(HeatState.cold(tick), 0.5f, recipe);
    }

    private static CharcoalForgeData lit(float burnTemperature, int burn) {
        return new CharcoalForgeData(new CharcoalForgeState(true, burn, burnTemperature, 0f, 0), false);
    }

    @Test
    void anIngotLeftInTheForgeReachesTheForgeTemperatureWithoutClicks() {
        // a freshly lit charcoal forge, an ingot put in at tick 0, 60 s (1200 ticks) later
        CharcoalForgeData start = lit(1350f, 1800);
        CharcoalForgeSim.Result r = CharcoalForgeSim.advance(start, 0, 1200, List.of(CHARCOAL), inputs(ingot(0, Float.NaN)),
            DEVICE, HEAT, CharcoalForgeSim.HeatSink.NONE);
        assertThat(r.finished(1200)).isTrue();
        // the forge warms one degree per tick; the item keeps up with it
        assertThat(r.data().state().temperature()).isEqualTo(1200f);
        assertThat(r.heats().get(0).temperature()).isEqualTo(1200f);
        assertThat(r.heats().get(0).lastTick()).isEqualTo(1200);
        assertThat(r.heats().get(1)).isNull();
        CharcoalForgeSim.Result later = CharcoalForgeSim.advance(start, 0, 1700, List.of(CHARCOAL), inputs(ingot(0, Float.NaN)),
            DEVICE, HEAT, CharcoalForgeSim.HeatSink.NONE);
        assertThat(later.data().state().temperature()).isEqualTo(1350f);
        assertThat(later.heats().get(0).temperature()).isEqualTo(1350f);
    }

    @Test
    void theItemFollowsTheWarmUpOfTheForgeTickByTick() {
        CharcoalForgeData d = lit(1350f, 1800);
        CharcoalForgeState s = d.state();
        HeatState item = HeatState.cold(0);
        List<Fuel> queue = List.of(CHARCOAL);
        for (long t = 1; t <= 700; t++) {
            CharcoalForgeRules.Step step = CharcoalForgeRules.step(s, false, null, DEVICE);
            s = step.state();
            item = item.heat(s.temperature(), HeatState.DEFAULT_HEAT_MODIFIER, t, 0.5f, HEAT);
        }
        CharcoalForgeSim.Result r = CharcoalForgeSim.advance(d, 0, 700, queue, inputs(ingot(0, Float.NaN)), DEVICE, HEAT,
            CharcoalForgeSim.HeatSink.NONE);
        assertThat(r.data().state()).isEqualTo(s);
        assertThat(r.heats().get(0)).isEqualTo(item);
    }

    @Test
    void theReplayEqualsSteppingOverRandomForges() {
        Random random = new Random(8);
        List<Fuel> pool = List.of(FuelTable.byId("charcoal").orElseThrow(), FuelTable.byId("coal").orElseThrow(),
            FuelTable.byId("oak_logs").orElseThrow());
        for (int run = 0; run < 60; run++) {
            boolean raining = random.nextInt(3) == 0;
            int air = random.nextInt(4) == 0 ? random.nextInt(601) : 0;
            Fuel first = pool.get(random.nextInt(pool.size()));
            CharcoalForgeData start = new CharcoalForgeData(new CharcoalForgeState(true, 1 + random.nextInt(first.duration()),
                first.temperature(), random.nextInt(2) == 0 ? 0f : random.nextInt(1500), air), raining);
            List<Fuel> queue = new ArrayList<>();
            for (int i = random.nextInt(4); i > 0; i--) {
                queue.add(pool.get(random.nextInt(pool.size())));
            }
            List<CharcoalForgeSim.Input> in = inputs(
                new CharcoalForgeSim.Input(new HeatState(random.nextInt(300), 0), 0.3f + random.nextFloat(), Float.NaN),
                random.nextBoolean() ? null : new CharcoalForgeSim.Input(HeatState.cold(0), 1.5f, Float.NaN));
            long span = 1 + random.nextInt(9000);

            CharcoalForgeSim.Result lazy = CharcoalForgeSim.advance(start, 0, span, queue, in, DEVICE, HEAT,
                CharcoalForgeSim.HeatSink.NONE);

            CharcoalForgeData cur = start;
            List<CharcoalForgeSim.Input> items = in;
            int used = 0;
            for (long t = 0; t < span; t++) {
                CharcoalForgeSim.Result one = CharcoalForgeSim.advance(cur, t, t + 1,
                    queue.subList(Math.min(used, queue.size()), queue.size()), items, DEVICE, HEAT, CharcoalForgeSim.HeatSink.NONE);
                used += one.consumed();
                cur = one.data();
                List<CharcoalForgeSim.Input> next = new ArrayList<>();
                for (int i = 0; i < CharcoalForgeSim.INPUTS; i++) {
                    CharcoalForgeSim.Input old = items.get(i);
                    next.add(old == null ? null : new CharcoalForgeSim.Input(one.heats().get(i), old.capacity(),
                        old.recipeTemperature()));
                }
                items = next;
            }
            assertThat(lazy.data()).as("run " + run).isEqualTo(cur);
            assertThat(lazy.consumed()).as("run " + run).isEqualTo(used);
            for (int i = 0; i < CharcoalForgeSim.INPUTS; i++) {
                assertThat(lazy.heats().get(i)).as("run " + run + " item " + i)
                    .isEqualTo(items.get(i) == null ? null : items.get(i).heat());
            }
        }
    }

    @Test
    void aRecipeFiresOnTheTickTheItemReachesItsTemperatureAndTheReplayStopsThere() {
        CharcoalForgeData start = lit(1350f, 1800);
        CharcoalForgeSim.Result r = CharcoalForgeSim.advance(start, 0, 5000, List.of(CHARCOAL), inputs(ingot(0, 1080f)), DEVICE,
            HEAT, CharcoalForgeSim.HeatSink.NONE);
        assertThat(r.conversions()).containsExactly(0);
        assertThat(r.heats().get(0).temperature()).isGreaterThanOrEqualTo(1080f);
        assertThat(r.stoppedAt()).isBetween(100L, 1500L);
        assertThat(r.finished(5000)).isFalse();
        // the tick before, the item was below the recipe temperature
        CharcoalForgeSim.Result before = CharcoalForgeSim.advance(start, 0, r.stoppedAt() - 1, List.of(CHARCOAL),
            inputs(ingot(0, 1080f)), DEVICE, HEAT, CharcoalForgeSim.HeatSink.NONE);
        assertThat(before.conversions()).isEmpty();
        assertThat(before.heats().get(0).temperature()).isLessThan(1080f);
    }

    @Test
    void theHeatGivenToTheBlockAboveIsTheForgeTemperatureOfEachTick() {
        List<float[]> runs = new ArrayList<>();
        CharcoalForgeSim.advance(lit(1350f, 1800), 100, 140, List.of(), inputs(), DEVICE, HEAT,
            (from, to, temperature) -> runs.add(new float[] {from, to, temperature}));
        assertThat(runs).hasSize(40);
        assertThat(runs.get(0)[0]).isEqualTo(101f);
        assertThat(runs.get(39)[1]).isEqualTo(140f);
        assertThat(runs.get(0)[2]).isGreaterThan(0f);
        assertThat(runs.get(39)[2]).isGreaterThan(runs.get(0)[2]);
        // a settled forge hands over one run
        runs.clear();
        CharcoalForgeData settled = new CharcoalForgeData(new CharcoalForgeState(true, 1800, 1350f, 1350f, 0), false);
        CharcoalForgeSim.advance(settled, 0, 1000, List.of(), inputs(), DEVICE, HEAT,
            (from, to, temperature) -> runs.add(new float[] {from, to, temperature}));
        assertThat(runs).hasSize(1);
        assertThat(runs.get(0)).containsExactly(1f, 1000f, 1350f);
    }

    @Test
    void aBurnedOutForgeGoesOutAndKeepsHeatingTheItemWhileItCoolsThenStops() {
        CharcoalForgeData start = lit(1350f, 50);
        CharcoalForgeSim.Result r = CharcoalForgeSim.advance(start, 0, 100_000, List.of(), inputs(ingot(0, Float.NaN)), DEVICE,
            HEAT, CharcoalForgeSim.HeatSink.NONE);
        assertThat(r.data().state().lit()).isFalse();
        assertThat(r.data().state().temperature()).isZero();
        assertThat(r.data().heatLevel()).isZero();
        // the item was heated on the way and then only cools by its own record (lastTick is the last warm tick)
        assertThat(r.heats().get(0).lastTick()).isLessThan(100_000);
        assertThat(r.heats().get(0).temperature()).isGreaterThan(0f);
    }

    @Test
    void ticksUntilOutCountsTheFuelQueue() {
        CharcoalForgeData start = lit(1350f, 10);
        assertThat(CharcoalForgeSim.ticksUntilOut(start, List.of())).isEqualTo(10);
        assertThat(CharcoalForgeSim.ticksUntilOut(start, List.of(CHARCOAL))).isEqualTo(10 + CHARCOAL.duration());
        assertThat(CharcoalForgeSim.ticksUntilOut(CharcoalForgeData.FRESH, List.of(CHARCOAL))).isEqualTo(-1);
        CharcoalForgeData rain = start.withRaining(true);
        assertThat(CharcoalForgeSim.ticksUntilOut(rain, List.of())).isEqualTo(5);
    }

    @Test
    void theSteppedAndLazyOutOfTimeAgree() {
        // ticksUntilOut is the tick the replay shows the forge unlit
        CharcoalForgeData start = lit(1350f, 123);
        long out = CharcoalForgeSim.ticksUntilOut(start, List.of(CHARCOAL));
        CharcoalForgeSim.Result at = CharcoalForgeSim.advance(start, 0, out, List.of(CHARCOAL), inputs(), DEVICE, HEAT,
            CharcoalForgeSim.HeatSink.NONE);
        CharcoalForgeSim.Result before = CharcoalForgeSim.advance(start, 0, out - 1, List.of(CHARCOAL), inputs(), DEVICE, HEAT,
            CharcoalForgeSim.HeatSink.NONE);
        assertThat(at.data().state().lit()).isFalse();
        assertThat(before.data().state().lit()).isTrue();
    }

    @Test
    void theStructureNeedsTheTopClearAChimneyAndFourInsulatedSidesAndAFloor() {
        assertThat(CharcoalForgeStructure.isValid(world(true, true, 0, true))).isTrue();
        assertThat(CharcoalForgeStructure.isValid(world(false, true, 0, true))).as("top blocked").isFalse();
        assertThat(CharcoalForgeStructure.isValid(world(true, false, -1, true))).as("no sky").isFalse();
        assertThat(CharcoalForgeStructure.isValid(world(true, true, 0, false))).as("no floor").isFalse();
        for (int missing = 0; missing < 4; missing++) {
            int m = missing;
            boolean[] open = new boolean[4];
            open[m] = true;
            assertThat(CharcoalForgeStructure.isValid(new Fake(true, true, open, true))).as("side " + m).isFalse();
        }
        // sky seen only two steps out to the east still counts, one step to the south as well
        assertThat(CharcoalForgeStructure.isValid(new Fake(true, false, new boolean[4], true) {
            @Override
            public boolean canSeeSky(int dx, int dy, int dz) {
                return dx == 2 && dy == 1 && dz == 0;
            }
        })).isTrue();
        assertThat(CharcoalForgeStructure.isValid(new Fake(true, false, new boolean[4], true) {
            @Override
            public boolean canSeeSky(int dx, int dy, int dz) {
                return dx == 1 && dy == 1 && dz == 1; // a diagonal is not one of the nine
            }
        })).isFalse();
    }

    @Test
    void theDataKeepsItsFieldsThroughTheCodec() {
        CharcoalForgeData d = new CharcoalForgeData(new CharcoalForgeState(true, 77, 1350f, 910.5f, 40), true);
        assertThat(CharcoalForgeData.CODEC.decode(CharcoalForgeData.CODEC.encode(d))).isEqualTo(d);
        assertThat(CharcoalForgeData.CODEC.decode(CharcoalForgeData.CODEC.encode(CharcoalForgeData.FRESH)))
            .isEqualTo(CharcoalForgeData.FRESH);
        assertThat(d.heatLevel()).isEqualTo(CharcoalForgeRules.heatLevel(910.5f));
        assertThat(CharcoalForgeData.FRESH.heatLevel()).isZero();
    }

    private static CharcoalForgeStructure.World world(boolean top, boolean sky, int ignored, boolean floor) {
        return new Fake(top, sky, new boolean[4], floor);
    }

    /** A forge whose four sides are insulated unless {@code open} says otherwise (north, east, south, west). */
    private static class Fake implements CharcoalForgeStructure.World {
        private final boolean top;
        private final boolean sky;
        private final boolean[] open;
        private final boolean floor;

        Fake(boolean top, boolean sky, boolean[] open, boolean floor) {
            this.top = top;
            this.sky = sky;
            this.open = open;
            this.floor = floor;
        }

        @Override
        public boolean topClear() {
            return top;
        }

        @Override
        public boolean canSeeSky(int dx, int dy, int dz) {
            return sky && dx == 0 && dy == 1 && dz == 0;
        }

        @Override
        public boolean insulation(int dx, int dy, int dz) {
            if (dy == -1) {
                return floor;
            }
            int side = dz == -1 ? 0 : dx == 1 ? 1 : dz == 1 ? 2 : 3;
            return !open[side];
        }
    }
}
