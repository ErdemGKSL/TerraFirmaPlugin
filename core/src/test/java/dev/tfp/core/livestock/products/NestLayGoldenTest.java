/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.livestock.products;

import dev.tfp.core.livestock.AnimalState;
import dev.tfp.core.livestock.LivestockSpecies;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Deque;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Golden egg-lay timing, the plain-egg drop, the full-nest refusal and the hatch day, hand-traced from TFC at c3b4d9536.
 *
 * <p>Lines followed: {@code NestBoxBlockEntity.serverTick}: {@code getGameTime() % 30 == 0} (checks), then
 * {@code isReadyForAnimalProduct} (not ready means {@code stopRiding}), then {@code bird.getRandom().nextInt(7) == 0}
 * (the roll), then {@code Helpers.hasOpenSlot} (full means {@code stopRiding}), then {@code insertOne}, {@code setFertilized(false)},
 * {@code setProductsCooldown}. {@code OviparousAnimal.makeEgg}: the gender draw and {@code EggComponent.of} with
 * {@code getTotalCalendarDays() + hatchDays}, and {@code addUses(1)}. {@code EggComponent.canHatch}:
 * {@code fertilized && hatchDay <= getTotalCalendarDays() && !rotten}.
 *
 * <p>The random source is scripted, so every roll is fixed. A script that runs out, or a draw that the TFC order does not
 * make, fails the test.
 */
class NestLayGoldenTest {
    private static final long ADULT_BIRTH = -10_000_000L;
    /** Calendar tick of day 100 at player tick 0. The calendar moves 1:1 with player ticks in these cases. */
    private static final long DAY_100 = 100L * 24_000L;
    private static final NestRules.NestRandom UNUSED = new NestRules.NestRandom() {
        @Override
        public int nextInt(int bound) {
            throw new AssertionError("no roll expected");
        }

        @Override
        public boolean nextBoolean() {
            throw new AssertionError("no gender draw expected");
        }
    };

    /** Scripted: each {@code nextInt} call pops the next roll and checks the bound is 7. */
    private static final class Script implements NestRules.NestRandom {
        private final Deque<Integer> rolls = new ArrayDeque<>();
        private final Deque<Boolean> genders = new ArrayDeque<>();

        Script rolls(int... values) {
            for (int v : values) {
                rolls.add(v);
            }
            return this;
        }

        Script genders(boolean... values) {
            for (boolean v : values) {
                genders.add(v);
            }
            return this;
        }

        @Override
        public int nextInt(int bound) {
            assertThat(bound).isEqualTo(7);
            if (rolls.isEmpty()) {
                throw new AssertionError("roll script exhausted");
            }
            return rolls.poll();
        }

        @Override
        public boolean nextBoolean() {
            if (genders.isEmpty()) {
                throw new AssertionError("gender script exhausted");
            }
            return genders.poll();
        }

        void assertUsedUp() {
            assertThat(rolls).isEmpty();
            assertThat(genders).isEmpty();
        }
    }

    private static final ProductSpec CHICKEN = ProductSpec.defaults(LivestockSpecies.CHICKEN).orElseThrow();

    private static AnimalState hen(boolean fertilized, float familiarity, long familiarityTick, int uses) {
        return new AnimalState(false, familiarity, familiarityTick, ADULT_BIRTH, AnimalState.NO_OLD_TICK,
            AnimalState.NEVER, AnimalState.NEVER, uses, fertilized, AnimalState.NOT_PREGNANT);
    }

    /**
     * A fertilized adult chicken, seated at tick 100 with familiarity 0.5 (no decay). Checks are at 120, 150, 180.
     * Rolls: 3 at 120 (no lay), 0 at 150 (lay). Gender draw: true (a male chick).
     * Lay at 150: the egg is fertile, the hatch day is {@code 100 + 8 = 108}, and the chick is born at 150 with
     * familiarity {@code 0.5 / 2 = 0.25} (TFC {@code f < 0.9F ? f / 2}). The hen loses the fertilized flag,
     * gets one use (3 to 4) and starts her cooldown at 150.
     */
    @Test
    void fertilizedChickenLaysOnTheFirstSuccessfulRollAtACheck() {
        final Script script = new Script().rolls(3, 0).genders(true);
        final NestRules.Outcome outcome = NestRules.advance(CHICKEN, LivestockSpecies.CHICKEN,
            hen(true, 0.5f, 0L, 3), ProductState.fresh(), 100L, 200L, true, script, t -> DAY_100 + t);
        script.assertUsedUp();

        assertThat(outcome).isInstanceOf(NestRules.Laid.class);
        final NestRules.Laid laid = (NestRules.Laid) outcome;
        assertThat(laid.tick()).isEqualTo(150L);
        assertThat(laid.hen().fertilized()).isFalse();
        assertThat(laid.hen().uses()).isEqualTo(4);
        assertThat(laid.product().producedTick()).isEqualTo(150L);

        assertThat(laid.nestEgg()).isPresent();
        final EggRules.FertileEgg egg = laid.nestEgg().get();
        assertThat(egg.hatchDay()).isEqualTo(108L);
        assertThat(egg.species()).isEqualTo(LivestockSpecies.CHICKEN);
        assertThat(egg.hatchling().male()).isTrue();
        assertThat(egg.hatchling().familiarity()).isEqualTo(0.25f);
        assertThat(egg.hatchling().birthTick()).isEqualTo(150L);
        assertThat(egg.hatchling().familiarityTick()).isEqualTo(150L);
    }

    /**
     * A seated hen's first check is the first multiple of 30 at or after the seating tick. Seated at 121, the first check
     * is 150, not 120. Seated at exactly 120, the check at 120 counts.
     */
    @Test
    void firstCheckIsTheFirstNestTickAtOrAfterSeating() {
        assertThat(NestRules.firstCheckAtOrAfter(121L)).isEqualTo(150L);
        assertThat(NestRules.firstCheckAtOrAfter(120L)).isEqualTo(120L);
        assertThat(NestRules.firstCheckAtOrAfter(0L)).isEqualTo(0L);
        assertThat(NestRules.firstCheckAtOrAfter(-1L)).isEqualTo(0L);
    }

    /**
     * The hen laid at 150 with {@code produceTicks} 36000, so she is not ready at 120 of the next wait. Seated at 36120,
     * the check at 36120 has cooldown {@code 150 + 36000 - 36120 = 30}, so she is unseated there (a {@code Left}), and no
     * roll is drawn. Seated at 36121, the first check is 36150, which has cooldown 0, so she rolls, and a 0 lays
     * (a plain hen here, so a plain egg).
     */
    @Test
    void cooldownBlocksTheLayUntilTheCooldownEnds() {
        final AnimalState laidHen = hen(false, 0.5f, 0L, 4);
        final ProductState laid = new ProductState(150L);

        final NestRules.Outcome early = NestRules.advance(CHICKEN, LivestockSpecies.CHICKEN, laidHen, laid,
            36_120L, 36_200L, true, UNUSED, t -> DAY_100 + t);
        assertThat(early).isEqualTo(new NestRules.Left(36_120L));

        final Script script = new Script().rolls(0);
        final NestRules.Outcome ready = NestRules.advance(CHICKEN, LivestockSpecies.CHICKEN, laidHen, laid,
            36_121L, 36_200L, true, script, t -> DAY_100 + t);
        script.assertUsedUp();
        assertThat(ready).isInstanceOf(NestRules.Laid.class);
        assertThat(((NestRules.Laid) ready).tick()).isEqualTo(36_150L);
    }

    /**
     * {@code NestBoxBlockEntity}: a plain egg (no {@code EggComponent}) is rejected by {@code isItemValid}, so it is
     * dropped at the nest and no slot is used. The hen still gets one use and the cooldown. No gender draw happens for a
     * plain egg.
     */
    @Test
    void plainHenDropsAPlainEggWithoutAGenderDraw() {
        final Script script = new Script().rolls(0);
        final NestRules.Outcome outcome = NestRules.advance(CHICKEN, LivestockSpecies.CHICKEN,
            hen(false, 0.5f, 0L, 3), ProductState.fresh(), 100L, 200L, true, script, t -> DAY_100 + t);
        script.assertUsedUp();
        final NestRules.Laid laid = (NestRules.Laid) outcome;
        assertThat(laid.tick()).isEqualTo(120L);
        assertThat(laid.nestEgg()).isEmpty();
        assertThat(laid.hen().uses()).isEqualTo(4);
        assertThat(laid.product().producedTick()).isEqualTo(120L);
    }

    /**
     * {@code NestBoxBlockEntity}: a successful roll with no open slot unseats the hen ({@code stopRiding}) and lays nothing.
     * The roll at 120 is 0, and the slot is full, so the result is {@code Left(120)}, with no gender draw.
     */
    @Test
    void fullNestUnseatsAHenWhoRollsALay() {
        final Script script = new Script().rolls(0);
        final NestRules.Outcome outcome = NestRules.advance(CHICKEN, LivestockSpecies.CHICKEN,
            hen(true, 0.5f, 0L, 3), ProductState.fresh(), 100L, 200L, false, script, t -> DAY_100 + t);
        script.assertUsedUp();
        assertThat(outcome).isEqualTo(new NestRules.Left(120L));
    }

    /**
     * A hen below the 0.15 threshold is not ready, so she is unseated at the first check and draws no roll.
     * Familiarity 0.1 read at 120 does not decay (the anchor is 120, within a day).
     */
    @Test
    void lowFamiliarityHenIsUnseatedAtTheFirstCheck() {
        final NestRules.Outcome outcome = NestRules.advance(CHICKEN, LivestockSpecies.CHICKEN,
            hen(true, 0.1f, 120L, 3), ProductState.fresh(), 100L, 200L, true, UNUSED, t -> DAY_100 + t);
        assertThat(outcome).isEqualTo(new NestRules.Left(120L));
    }

    /**
     * Rolls that all miss through {@code now} leave the hen seated: checks 120, 150 and 180 roll 1, 2 and 3 (no lay), and the next check, 210, is after
     * {@code now = 190}, so the result is {@code StillSeated}.
     */
    @Test
    void missedRollsLeaveTheHenSeated() {
        final Script script = new Script().rolls(1, 2, 3);
        final NestRules.Outcome outcome = NestRules.advance(CHICKEN, LivestockSpecies.CHICKEN,
            hen(true, 0.5f, 0L, 3), ProductState.fresh(), 100L, 190L, true, script, t -> DAY_100 + t);
        script.assertUsedUp();
        assertThat(outcome).isEqualTo(new NestRules.StillSeated());
    }

    /**
     * {@code EggComponent.canHatch} with {@code hatchDay = 108}, hatched on calendar day 108 and later. A rotten egg
     * never hatches. {@code hatchCalendarTick} is the start of day 108.
     */
    @Test
    void chickHatchesOnItsHatchDayUnlessRotten() {
        final EggRules.FertileEgg egg = new EggRules.FertileEgg(108L, LivestockSpecies.CHICKEN,
            AnimalState.offspring(true, 0.25f, 150L));
        assertThat(EggRules.canHatch(egg, 107L * 24_000L + 23_999L, false)).isFalse();
        assertThat(EggRules.canHatch(egg, 108L * 24_000L, false)).isTrue();
        assertThat(EggRules.canHatch(egg, 200L * 24_000L, true)).isFalse();
        assertThat(EggRules.hatchCalendarTick(egg)).isEqualTo(108L * 24_000L);
    }

    /**
     * {@code OviparousAnimal.makeEgg}: {@code hatchDay = getTotalCalendarDays() + hatchDays}. Duck and quail have
     * {@code hatchDays} of 8 too. A lay on day 100 hatches on day 108.
     */
    @Test
    void hatchDayIsLayDayPlusHatchDaysForEveryOviparousSpecies() {
        for (LivestockSpecies species : new LivestockSpecies[] {LivestockSpecies.CHICKEN, LivestockSpecies.DUCK, LivestockSpecies.QUAIL}) {
            final EggRules.Laying laying = EggRules.makeEgg(species, hen(true, 0.5f, 0L, 0), 150L, DAY_100 + 150L, false);
            assertThat(laying.egg()).isPresent();
            assertThat(laying.egg().get().hatchDay()).as(species.name()).isEqualTo(108L);
        }
    }

    @Test
    void makeEggRejectsMammals() {
        assertThatThrownBy(() -> EggRules.makeEgg(LivestockSpecies.COW, hen(false, 0.5f, 0L, 0), 0L, 0L, false))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
