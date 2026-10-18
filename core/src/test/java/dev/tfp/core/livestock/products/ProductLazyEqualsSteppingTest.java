/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.livestock.products;

import dev.tfp.core.calendar.CalendarTicks;
import dev.tfp.core.livestock.AnimalState;
import dev.tfp.core.livestock.BreedingRules;
import dev.tfp.core.livestock.LivestockRules;
import dev.tfp.core.livestock.LivestockSpecies;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Random;
import java.util.function.LongUnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * M5-LIV-02 lazy-equals-stepping for the products. The stepped side is TFC's per-tick logic, run literally:
 * familiarity decays on every 20-tick check (the same literal step as {@code LazyEqualsSteppingTest}), and readiness is
 * recomputed on every tick from the TFC formulas ({@code ProducingMammal.isReadyForAnimalProduct},
 * {@code DairyAnimal.hasProduct}, {@code WoolyAnimal.hasProduct}, {@code OviparousAnimal.hasProduct} and
 * {@code ProducingAnimal.getProductsCooldown}). The nest is stepped on every game tick, acting only on the
 * {@code % 30 == 0} checks, in TFC's order: ready, roll, slot, lay.
 *
 * <p>The lazy side is {@link ProductRules} and {@link NestRules}, which skip straight to the decisions. Cases whose
 * familiarity sits within 1e-6 of the threshold are skipped, because a stepped double and a lazy float can round to
 * different sides there. The skip count is small and is not asserted away.
 */
class ProductLazyEqualsSteppingTest {
    private static final long DAY = 24_000L;
    private static final long PLAYER_DAY = 28_800L;
    private static final long CHECK = 20L;
    private static final long NEST_CHECK = 30L;

    /** Literal TFC {@code tickAnimalData} familiarity step at check tick {@code t} (as in {@code LazyEqualsSteppingTest}). */
    private static void stepFamiliarity(double[] f, long[] anchor, long t) {
        if (f[0] > 0 && anchor[0] != -1 && t > anchor[0] + DAY && f[0] < 0.3f) {
            f[0] = Math.max(0, Math.min(1, f[0] - 0.02 * (t - anchor[0] - DAY) / DAY));
            anchor[0] = t;
        }
    }

    /** Literal TFC readiness at tick {@code t}, from the stepped double familiarity. */
    private static boolean steppedReady(ProductSpec spec, LivestockSpecies species, double familiarity, boolean male,
                                        long producedTick, long birthTick, long oldTick, long t) {
        final boolean old = oldTick != -1L && t > oldTick;
        final boolean adult = !old && t > birthTick + (long) species.adulthoodDays() * PLAYER_DAY;
        final long cooldown = Math.max(0L, spec.produceTicks() + producedTick - t);
        final boolean hasProduct = switch (spec.kind()) {
            case MILK -> !male && adult && cooldown == 0L;
            case EGG -> !male && adult && (producedTick <= 0L || cooldown <= 0L);
            case SHEAR -> producedTick <= 0L || cooldown <= 0L;
        };
        return familiarity > spec.produceFamiliarity()
            && hasProduct
            && (spec.kind() != ProductKind.SHEAR || adult);
    }

    private static NestRules.NestRandom wrap(Random r) {
        return new NestRules.NestRandom() {
            @Override
            public int nextInt(int bound) {
                return r.nextInt(bound);
            }

            @Override
            public boolean nextBoolean() {
                return r.nextBoolean();
            }
        };
    }

    @Test
    void readinessMatchesSteppingForSeededStartStates() {
        final Random rnd = new Random(0x5EED_0B0CL);
        final LivestockSpecies[] producing = Arrays.stream(LivestockSpecies.values())
            .filter(s -> ProductSpec.defaults(s).isPresent())
            .toArray(LivestockSpecies[]::new);
        int compared = 0;
        for (int i = 0; i < 300; i++) {
            final LivestockSpecies species = producing[rnd.nextInt(producing.length)];
            final ProductSpec spec = ProductSpec.defaults(species).orElseThrow();
            final boolean male = spec.kind() == ProductKind.SHEAR && rnd.nextBoolean();
            final float start = rnd.nextInt(3) == 0 ? rnd.nextFloat() : 0.1f + rnd.nextFloat() * 0.4f;
            final long anchor = -2L - rnd.nextInt(23_998);
            // mostly adults, sometimes children (birth after the adult tick) and sometimes old
            final long birth = rnd.nextInt(4) == 0 ? rnd.nextInt(3_000_000) : -10_000_000L;
            final long old = rnd.nextInt(5) == 0 ? rnd.nextInt(3_000_000) : -1L;
            final long produced = rnd.nextInt(3) == 0 ? 0L : rnd.nextInt(2 * spec.produceTicks());
            final long now = rnd.nextInt(150_000);

            final AnimalState s = new AnimalState(male, start, anchor, birth, old,
                AnimalState.NEVER, AnimalState.NEVER, 0, false, AnimalState.NOT_PREGNANT);
            final ProductState p = new ProductState(produced);

            final double[] f = {start};
            final long[] a = {anchor};
            for (long t = 1; t <= now; t++) {
                if (t % CHECK == 0) {
                    stepFamiliarity(f, a, t);
                }
                if (t == now || t % 997 == 0) {
                    final double steppedFamiliarity = f[0];
                    assertThat(LivestockRules.familiarityAt(s, t))
                        .as("case %d familiarity at %d", i, t)
                        .isCloseTo((float) steppedFamiliarity, within(1e-6f));
                    if (Math.abs(steppedFamiliarity - spec.produceFamiliarity()) > 1e-6) {
                        final boolean stepped = steppedReady(spec, species, steppedFamiliarity, male, produced, birth, old, t);
                        assertThat(ProductRules.isReady(spec, species, s, p, t))
                            .as("case %d, %s, male %s, tick %d", i, species, male, t)
                            .isEqualTo(stepped);
                        compared++;
                    }
                }
            }
        }
        assertThat(compared).isGreaterThan(1_000);
    }

    @Test
    void nestLayMatchesSteppingForSeededSeatings() {
        final Random rnd = new Random(0x4E57_1A7DL);
        final LivestockSpecies[] birds = {LivestockSpecies.CHICKEN, LivestockSpecies.DUCK, LivestockSpecies.QUAIL};
        // calendar ticks run at 5/6 of player ticks here (24000 per day against 28800), offset to day 100
        final LongUnaryOperator calendar = t -> 100L * CalendarTicks.CALENDAR_TICKS_IN_DAY + t * 5L / 6L;
        int laid = 0;
        int left = 0;
        int stillSeated = 0;
        int skipped = 0;
        for (int i = 0; i < 300; i++) {
            final LivestockSpecies species = birds[rnd.nextInt(birds.length)];
            final ProductSpec spec = ProductSpec.defaults(species).orElseThrow();
            final boolean fertilized = rnd.nextBoolean();
            final float start = rnd.nextInt(4) == 0 ? rnd.nextFloat() : 0.1f + rnd.nextFloat() * 0.4f;
            final long anchor = -2L - rnd.nextInt(23_998);
            final long birth = -10_000_000L;
            final AnimalState hen = new AnimalState(false, start, anchor, birth, -1L,
                AnimalState.NEVER, AnimalState.NEVER, rnd.nextInt(20), fertilized, AnimalState.NOT_PREGNANT);
            final long produced = rnd.nextInt(3) == 0 ? 0L : rnd.nextInt(2 * spec.produceTicks());
            final ProductState p = new ProductState(produced);
            final long seated = 1L + rnd.nextInt(120_000);
            final long now = seated + rnd.nextInt(40_000);
            final boolean slotOpen = rnd.nextInt(5) != 0;
            final long seed = rnd.nextLong();

            // stepped: literal TFC per tick, with the nest acting on its own 30-tick grid, after the decay of the tick
            final double[] f = {start};
            final long[] a = {anchor};
            final Random stepRnd = new Random(seed);
            String kind = "still";
            long eventTick = -1L;
            boolean nearThreshold = false;
            boolean steppedMale = false;
            double steppedFamiliarity = Double.NaN;
            for (long t = 1; t <= now; t++) {
                if (t % CHECK == 0) {
                    stepFamiliarity(f, a, t);
                }
                if (t >= seated && t % NEST_CHECK == 0) {
                    if (Math.abs(f[0] - spec.produceFamiliarity()) <= 1e-6) {
                        nearThreshold = true;
                        break;
                    }
                    if (!steppedReady(spec, species, f[0], false, produced, birth, -1L, t)) {
                        kind = "left";
                        eventTick = t;
                        break;
                    }
                    if (stepRnd.nextInt(7) != 0) {
                        continue;
                    }
                    if (!slotOpen) {
                        kind = "left";
                        eventTick = t;
                        break;
                    }
                    steppedMale = fertilized && stepRnd.nextBoolean();
                    steppedFamiliarity = f[0];
                    kind = "laid";
                    eventTick = t;
                    break;
                }
            }
            if (nearThreshold) {
                skipped++;
                continue;
            }

            final NestRules.Outcome outcome = NestRules.advance(spec, species, hen, p, seated, now, slotOpen,
                wrap(new Random(seed)), calendar);
            switch (kind) {
                case "still" -> {
                    assertThat(outcome).as("case %d still", i).isInstanceOf(NestRules.StillSeated.class);
                    stillSeated++;
                }
                case "left" -> {
                    assertThat(outcome).as("case %d left", i).isEqualTo(new NestRules.Left(eventTick));
                    left++;
                }
                default -> {
                    assertThat(outcome).as("case %d laid", i).isInstanceOf(NestRules.Laid.class);
                    final NestRules.Laid lay = (NestRules.Laid) outcome;
                    assertThat(lay.tick()).as("case %d lay tick", i).isEqualTo(eventTick);
                    assertThat(lay.hen().fertilized()).isFalse();
                    assertThat(lay.hen().uses()).isEqualTo(hen.uses() + 1);
                    assertThat(lay.product().producedTick()).isEqualTo(eventTick);
                    if (fertilized) {
                        assertThat(lay.nestEgg()).as("case %d fertile egg", i).isPresent();
                        final EggRules.FertileEgg egg = lay.nestEgg().get();
                        final long steppedHatchDay = CalendarTicks.totalDays(calendar.applyAsLong(eventTick)) + species.hatchDays();
                        assertThat(egg.hatchDay()).as("case %d hatch day", i).isEqualTo(steppedHatchDay);
                        assertThat(egg.hatchling().male()).as("case %d gender", i).isEqualTo(steppedMale);
                        assertThat(egg.hatchling().familiarity())
                            .as("case %d offspring familiarity", i)
                            .isCloseTo((float) (steppedFamiliarity < 0.9 ? steppedFamiliarity / 2 : steppedFamiliarity * 0.9),
                                within(1e-6f));
                        assertThat(egg.hatchling().birthTick()).isEqualTo(eventTick);
                    } else {
                        assertThat(lay.nestEgg()).as("case %d plain egg", i).isEmpty();
                    }
                    laid++;
                }
            }
        }
        assertThat(laid).as("laid cases").isGreaterThan(20);
        assertThat(left).as("left cases").isGreaterThan(10);
        assertThat(stillSeated + laid + left + skipped).isEqualTo(300);
        // the eggs' hatch day is the same formula as BreedingRules.eggHatchDay
        assertThat(BreedingRules.eggHatchDay(calendar.applyAsLong(0L), LivestockSpecies.CHICKEN))
            .isEqualTo(CalendarTicks.totalDays(calendar.applyAsLong(0L)) + 8);
    }
}
