/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.livestock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.tfp.core.calendar.CalendarTicks;
import java.util.Arrays;
import java.util.Optional;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * M5-LIV-01 lazy-equals-stepping. The stepped side is TFC's per-tick logic, run literally on every 20-tick check from
 * the start tick to {@code now}: {@code TFCAnimalProperties.tickAnimalData} for the familiarity decay (condition,
 * {@code 0.02 * (t - anchor - TICKS_IN_DAY) / TICKS_IN_DAY}, anchor reset to {@code t}), and
 * {@code MammalProperties.tickAnimalData} for the birth (decay first, then the birth check, litter, uses).
 * The lazy side is {@link LivestockRules} and {@link BreedingRules}, which compute the same result in closed form.
 *
 * <p>Start states are caught up at tick 0. The anchor is drawn in [-23 999, -2], which keeps every pending check after 0
 * and never equals the TFC sentinel -1. Birth tests use grid-aligned {@code now} with calendar ticks equal to
 * player ticks, so the birth check instant and the grid agree exactly.
 */
class LazyEqualsSteppingTest {
    private static final long DAY = 24_000L;
    private static final long CHECK = 20L;

    /** Literal TFC {@code tickAnimalData} familiarity step at check tick {@code t}; double precision, clamped to [0, 1]. */
    private static void stepFamiliarity(double[] f, long[] anchor, long t) {
        if (f[0] > 0 && anchor[0] != -1 && t > anchor[0] + DAY && f[0] < 0.3f) {
            f[0] = Math.max(0, Math.min(1, f[0] - 0.02 * (t - anchor[0] - DAY) / DAY));
            anchor[0] = t;
        }
    }

    @Test
    void familiarityDecayMatchesSteppingForSeededStartStates() {
        final Random rnd = new Random(0x5EEDF00DL);
        for (int i = 0; i < 400; i++) {
            final float start = rnd.nextInt(5) == 0 ? rnd.nextFloat() : rnd.nextFloat() * 0.3f;
            final long anchor = -2L - rnd.nextInt(23_998);
            final long now = 1L + rnd.nextInt(5 * (int) DAY);
            final AnimalState s = new AnimalState(false, start, anchor, -10_000_000L, -1L,
                AnimalState.NEVER, AnimalState.NEVER, 0, false, AnimalState.NOT_PREGNANT);

            final double[] f = {start};
            final long[] a = {anchor};
            for (long t = CHECK; t <= now; t += CHECK) {
                stepFamiliarity(f, a, t);
            }

            final AnimalState lazy = LivestockRules.catchUp(s, now);
            assertThat(lazy.familiarity()).as("case %d, start %s, anchor %d, now %d", i, start, anchor, now)
                .isCloseTo((float) f[0], within(1e-6f));
            assertThat(lazy.familiarityTick()).as("case %d anchor", i).isEqualTo(a[0]);
            assertThat(LivestockRules.familiarityAt(s, now)).isCloseTo(lazy.familiarity(), within(1e-9f));
            // reading the same state again at the same tick does not decay twice
            assertThat(LivestockRules.catchUp(lazy, now)).isEqualTo(lazy);
            // familiarity never goes up from decay
            assertThat(lazy.familiarity()).isLessThanOrEqualTo(start);
        }
    }

    @Test
    void birthMatchesSteppingForSeededPregnanciesWithDecayBeforeIt() {
        final Random rnd = new Random(0xB17DB17DL);
        final LivestockSpecies[] mammals = Arrays.stream(LivestockSpecies.values())
            .filter(sp -> sp.kind() == LivestockSpecies.Kind.MAMMAL)
            .toArray(LivestockSpecies[]::new);
        for (int i = 0; i < 300; i++) {
            final LivestockSpecies species = mammals[rnd.nextInt(mammals.length)];
            final long pregnantDay = 1 + rnd.nextInt(60);
            final float start = rnd.nextFloat();
            final long anchor = -2L - rnd.nextInt(23_998);
            // grid-aligned now, up to about 150 days; calendar ticks equal player ticks in this test
            final long now = CHECK * rnd.nextInt(150 * (int) (DAY / CHECK));
            final int childDraw = rnd.nextInt(species.childCount());
            final AnimalState mother = new AnimalState(false, start, anchor, -10_000_000L, -1L,
                AnimalState.NEVER, AnimalState.NEVER, rnd.nextInt(30), true, pregnantDay);

            // stepped: decay, then the birth check, on each 20-tick check
            final double[] f = {mother.familiarity()};
            final long[] a = {mother.familiarityTick()};
            boolean born = false;
            long bornAt = -1L;
            double steppedOffspring = Double.NaN;
            for (long t = CHECK; t <= now && !born; t += CHECK) {
                stepFamiliarity(f, a, t);
                final long days = CalendarTicks.totalDays(t);
                if (pregnantDay > 0 && days >= pregnantDay + species.gestationDays()) {
                    born = true;
                    bornAt = t;
                    steppedOffspring = f[0] < 0.9 ? f[0] / 2 : f[0] * 0.9;
                }
            }
            if (born) {
                // identity calendar mapping: the first due check is the due calendar tick itself (grid-aligned)
                assertThat(bornAt).isEqualTo(BreedingRules.dueCalendarTick(mother, species));
            }

            final Optional<BreedingRules.Birth> lazy = BreedingRules.birth(mother, species, now, now,
                Math.max(bornAt, 0L), childDraw);
            assertThat(lazy.isPresent()).as("case %d, %s, day %d, gestation %d, now %d", i, species, pregnantDay,
                species.gestationDays(), now).isEqualTo(born);
            if (!born) {
                // not due: the lazy birth must not run at all, and the stored mother is only caught up
                assertThat(lazy).isEmpty();
            }
            if (born) {
                final BreedingRules.Birth birth = lazy.get();
                assertThat(birth.kids()).isEqualTo(1 + childDraw);
                assertThat(birth.offspringFamiliarity()).isCloseTo((float) steppedOffspring, within(1e-6f));
                assertThat(birth.mother().fertilized()).isFalse();
                assertThat(birth.mother().pregnantDay()).isEqualTo(AnimalState.NOT_PREGNANT);
                assertThat(birth.mother().uses()).isEqualTo(mother.uses() + 10);
            }
        }
    }
}
