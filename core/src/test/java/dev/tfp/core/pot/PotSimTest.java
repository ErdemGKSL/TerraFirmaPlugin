/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.pot;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.cooking.FirepitRules;
import dev.tfp.core.cooking.FirepitState;
import dev.tfp.core.cooking.PotRules;
import dev.tfp.core.firepit.FirepitData;
import dev.tfp.core.firepit.FirepitSim;
import dev.tfp.core.fuel.Fuel;
import dev.tfp.core.heat.HeatConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * PAR-HEAT-09: the lazy pot catch-up against a literal transcription of TFC's live tick ({@code AbstractFirepitBlockEntity
 * .serverTick}: the firepit part, then {@code PotBlockEntity.handleCooking} with that tick's temperature), stepped one
 * tick at a time, over random fuels, recipes and spans; and the span split anywhere gives the same pot.
 */
class PotSimTest {
    static final Fuel OAK = new Fuel("oak", 2250, 798f, 0.95f);
    static final Fuel PINE = new Fuel("pine", 1500, 600f, 0.7f);
    static final Fuel STICK = new Fuel("stick", 400, 150f, 0.6f);
    static final List<Fuel> FUELS = List.of(OAK, PINE, STICK);

    /** The live result: the pot, the queue entries taken and the tick the recipe finished. */
    record Live(PotData data, int consumed, long finishedAt) {
    }

    /** TFC's serverTick + handleCooking, one tick at a time, stopping at the tick the output is made. */
    static Live live(PotData data, long from, long to, List<Optional<Fuel>> queue, PotSim.Boil recipe, Random random) {
        FirepitData f = data.firepit();
        int pre = data.counters().preBoilingTicks();
        int boil = data.counters().boilingTicks();
        int used = 0;
        for (long t = from; t < to; ) {
            FirepitSim.Result r = FirepitSim.advance(f, t, t + 1, queue.subList(Math.min(used, queue.size()), queue.size()),
                null, random, FirepitRules.DeviceConfig.DEFAULT, HeatConfig.DEFAULT);
            used += r.consumed();
            f = r.data();
            t++;
            // isBoiling: cachedRecipe != null && output == null && cachedRecipe.isHotEnough(temperature)
            boolean boiling = recipe != null && !data.hasOutput() && f.temperature() > recipe.temperature();
            if (boiling) {
                if (pre < PotRules.PRE_BOIL_TIME) {
                    pre++;
                    continue;
                }
                if (boil < recipe.duration()) {
                    boil++;
                } else {
                    return new Live(data.withFirepit(f).withCounters(PotRules.PotState.IDLE), used, t);
                }
            } else if (boil > 0) {
                boil = 0;
                pre = 0;
            }
        }
        return new Live(data.withFirepit(f).withCounters(new PotRules.PotState(pre, boil)), used, PotSim.NOT_FINISHED);
    }

    static PotData litPot(Random r, List<Optional<Fuel>> queue) {
        Fuel first = FUELS.get(r.nextInt(FUELS.size()));
        float temperature = r.nextInt(4) == 0 ? 0f : r.nextFloat() * 900f;
        FirepitData f = new FirepitData(new FirepitState(r.nextInt(first.duration()) + 1, first.temperature(), temperature,
            r.nextInt(3) == 0 ? r.nextInt(600) : 0), r.nextInt(5) != 0, 0, r.nextFloat() * 0.5f, 0, first.duration(),
            r.nextInt(4) == 0);
        if (!f.lit()) {
            f = new FirepitData(new FirepitState(0, 0f, temperature, 0), false, 0, 0f, 0, first.duration(), f.raining());
        }
        int n = r.nextInt(4);
        for (int i = 0; i < n; i++) {
            queue.add(r.nextInt(6) == 0 ? Optional.empty() : Optional.of(FUELS.get(r.nextInt(FUELS.size()))));
        }
        PotRules.PotState c = r.nextBoolean() ? PotRules.PotState.IDLE
            : new PotRules.PotState(r.nextInt(PotRules.PRE_BOIL_TIME + 1), r.nextInt(50));
        return new PotData(f, c, PotOutput.NONE, PotData.FRESH.fluid());
    }

    static PotSim.Boil recipe(Random r) {
        return switch (r.nextInt(4)) {
            case 0 -> null;
            case 1 -> new PotSim.Boil(300f, 1200);
            case 2 -> new PotSim.Boil(600f, 2400);
            default -> new PotSim.Boil(r.nextInt(800), r.nextInt(500));
        };
    }

    @Test
    void lazyEqualsTheSteppedLiveTickOverRandomCases() {
        Random cases = new Random(9);
        int finished = 0;
        for (int i = 0; i < 400; i++) {
            List<Optional<Fuel>> queue = new ArrayList<>();
            PotData pot = litPot(cases, queue);
            PotSim.Boil recipe = recipe(cases);
            long from = cases.nextInt(100_000);
            long to = from + 1 + cases.nextInt(i % 4 == 0 ? 60_000 : 4_000);
            long seed = cases.nextLong();

            Live expected = live(pot, from, to, queue, recipe, new Random(seed));
            PotSim.Result lazy = PotSim.advance(pot, from, to, queue, recipe, new Random(seed),
                FirepitRules.DeviceConfig.DEFAULT, HeatConfig.DEFAULT);

            assertThat(lazy.finishedAt()).as("case %d", i).isEqualTo(expected.finishedAt());
            assertThat(lazy.consumed()).as("case %d", i).isEqualTo(expected.consumed());
            assertThat(lazy.data()).as("case %d", i).isEqualTo(expected.data());
            if (lazy.finished()) {
                finished++;
            }
        }
        assertThat(finished).as("cases where a recipe finished").isGreaterThan(20);
    }

    @Test
    void splittingTheSpanGivesTheSamePot() {
        Random cases = new Random(17);
        for (int i = 0; i < 200; i++) {
            List<Optional<Fuel>> queue = new ArrayList<>();
            PotData pot = litPot(cases, queue);
            PotSim.Boil recipe = recipe(cases);
            long from = cases.nextInt(10_000);
            long to = from + 2 + cases.nextInt(8_000);
            long mid = from + 1 + cases.nextInt((int) (to - from - 1));
            long seed = cases.nextLong();

            PotSim.Result whole = PotSim.advance(pot, from, to, queue, recipe, new Random(seed),
                FirepitRules.DeviceConfig.DEFAULT, HeatConfig.DEFAULT);
            Random rnd = new Random(seed);
            PotSim.Result a = PotSim.advance(pot, from, mid, queue, recipe, rnd, FirepitRules.DeviceConfig.DEFAULT,
                HeatConfig.DEFAULT);
            if (a.finished()) {
                assertThat(whole.finishedAt()).as("case %d", i).isEqualTo(a.finishedAt());
                assertThat(whole.data()).as("case %d", i).isEqualTo(a.data());
                continue;
            }
            PotSim.Result b = PotSim.advance(a.data(), mid, to, queue.subList(a.consumed(), queue.size()), recipe, rnd,
                FirepitRules.DeviceConfig.DEFAULT, HeatConfig.DEFAULT);
            assertThat(b.finishedAt()).as("case %d", i).isEqualTo(whole.finishedAt());
            assertThat(a.consumed() + b.consumed()).as("case %d", i).isEqualTo(whole.consumed());
            assertThat(b.data()).as("case %d", i).isEqualTo(whole.data());
        }
    }

    @Test
    void aHotPotBoilsThePreBoilThenTheDurationThenFinishesOnTheNextTick() {
        FirepitData hot = new FirepitData(new FirepitState(100_000, 800f, 800f, 0), true, 0, 0f, 0, 100_000, false);
        PotData pot = PotData.FRESH.withFirepit(hot);
        PotSim.Boil soup = new PotSim.Boil(300f, 1200);

        PotSim.Result r = PotSim.advance(pot, 0, 10_000, List.of(), soup, new Random(1), FirepitRules.DeviceConfig.DEFAULT,
            HeatConfig.DEFAULT);
        assertThat(r.finishedAt()).isEqualTo(PotRules.PRE_BOIL_TIME + 1200 + 1);
        assertThat(r.data().counters()).isEqualTo(PotRules.PotState.IDLE);

        PotSim.Result partway = PotSim.advance(pot, 0, 150, List.of(), soup, new Random(1),
            FirepitRules.DeviceConfig.DEFAULT, HeatConfig.DEFAULT);
        assertThat(partway.finished()).isFalse();
        assertThat(partway.data().counters()).isEqualTo(new PotRules.PotState(100, 50));
        assertThat(PotSim.isBoiling(partway.data(), soup)).isTrue();
        assertThat(PotSim.hasRecipeStarted(partway.data(), soup)).isTrue();
        assertThat(partway.data().rendersAsBoiling()).isTrue();
    }

    @Test
    void anOutputOrNoRecipeNeverBoilsAndResetsStartedCounters() {
        FirepitData hot = new FirepitData(new FirepitState(100_000, 800f, 800f, 0), true, 0, 0f, 0, 100_000, false);
        PotData boiling = PotData.FRESH.withFirepit(hot).withCounters(new PotRules.PotState(100, 30));
        PotData withOutput = boiling.withOutput(new PotOutput.Soup(dev.tfp.core.device.ItemBlob.of(new byte[] {1})));

        PotSim.Result none = PotSim.advance(boiling, 0, 500, List.of(), null, new Random(1),
            FirepitRules.DeviceConfig.DEFAULT, HeatConfig.DEFAULT);
        PotSim.Result output = PotSim.advance(withOutput, 0, 500, List.of(), new PotSim.Boil(300f, 10), new Random(1),
            FirepitRules.DeviceConfig.DEFAULT, HeatConfig.DEFAULT);
        assertThat(none.data().counters()).isEqualTo(PotRules.PotState.IDLE);
        assertThat(output.data().counters()).isEqualTo(PotRules.PotState.IDLE);
        assertThat(output.finished()).isFalse();
        // a pre-boil that never reached the boil is kept, as TFC (it only resets once boilingTicks > 0)
        PotData pre = boiling.withCounters(new PotRules.PotState(40, 0));
        assertThat(PotSim.advance(pre, 0, 10, List.of(), null, new Random(1), FirepitRules.DeviceConfig.DEFAULT,
            HeatConfig.DEFAULT).data().counters()).isEqualTo(new PotRules.PotState(40, 0));
    }

    @Test
    void aColdPotWithARecipeStopsSteppingAndStillMatchesTheLiveTick() {
        PotData cold = PotData.FRESH.withCounters(new PotRules.PotState(100, 5));
        PotSim.Boil soup = new PotSim.Boil(300f, 1200);
        long to = 50_000_000L; // a long absence: the replay must not step it tick by tick
        PotSim.Result r = PotSim.advance(cold, 0, to, List.of(), soup, new Random(1), FirepitRules.DeviceConfig.DEFAULT,
            HeatConfig.DEFAULT);
        assertThat(r.data().counters()).isEqualTo(PotRules.PotState.IDLE);
        assertThat(r.data().firepit()).isEqualTo(PotData.FRESH.firepit());
    }
}
