/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.livestock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

/**
 * M5-LIV-01 golden familiarity, hunger and feeding values. Expected values are hand-traced from TFC at c3b4d9536:
 * {@code TFCAnimalProperties.tickAnimalData} (the decay condition and the {@code 0.02f * (now - anchor - TICKS_IN_DAY)
 * / TICKS_IN_DAY} step, which resets the anchor through {@code setFamiliarity}), {@code CommonAnimalBehavior.setLastFedNow}
 * and {@code setFamiliarity}, {@code TFCAnimalProperties.eatFood} (+0.06, adult cap, child uncapped) and
 * {@code TFCAnimalProperties.isHungry}. One decay check period is 20 ticks, and the decay for a day's overshoot of
 * {@code o} ticks is {@code 0.02 * o / 24000}.
 */
class FamiliarityGoldenTest {
    private static final float TOL = 1e-6f;

    /** A state with only the familiarity fields relevant; no feed, mate, pregnancy or old tick. */
    private static AnimalState state(float familiarity, long anchor) {
        return new AnimalState(false, familiarity, anchor, -10_000_000L, -1L,
            AnimalState.NEVER, AnimalState.NEVER, 0, false, AnimalState.NOT_PREGNANT);
    }

    /** An adult pig (80 adult days = 2 304 000 ticks) that was born long ago, with the given familiarity. */
    private static AnimalState adultPig(float familiarity, long anchor, long lastFed) {
        return new AnimalState(false, familiarity, anchor, -10_000_000L, -1L,
            lastFed, AnimalState.NEVER, 0, false, AnimalState.NOT_PREGNANT);
    }

    @Test
    void decayFirstCheckAfterOneDayOfOvershootTwentyTicks() {
        // TFC: anchor 0, check at 24020 (first gameTime%20 tick > 0 + 24000). overshoot 20, decay 0.02*20/24000 = 1/60000
        final AnimalState s = state(0.2f, 0L);
        final AnimalState at = LivestockRules.catchUp(s, 24_020L);
        assertThat(at.familiarity()).isCloseTo(0.2f - 1f / 60_000f, within(TOL));
        assertThat(at.familiarityTick()).isEqualTo(24_020L);
    }

    @Test
    void noDecayBeforeTheFirstCheckAfterTheDay() {
        // TFC: now 24019 is not > the next check tick 24020, so no check has fired yet
        final AnimalState s = state(0.2f, 0L);
        assertThat(LivestockRules.catchUp(s, 24_019L)).isEqualTo(s);
        assertThat(LivestockRules.familiarityAt(s, 24_000L)).isEqualTo(0.2f);
    }

    @Test
    void secondDayDecaysAgainWithAFullPeriodOfOvershoot() {
        // TFC: checks at 24020 (overshoot 20) then 48040 (anchor 24020, overshoot 20). Two decays of 1/60000
        final AnimalState at = LivestockRules.catchUp(state(0.2f, 0L), 48_040L);
        assertThat(at.familiarity()).isCloseTo(0.2f - 2f / 60_000f, within(TOL));
        assertThat(at.familiarityTick()).isEqualTo(48_040L);
    }

    @Test
    void overshootOfFifteenTicksFromAnOffGridFeed() {
        // TFC: feed (anchor) at 1005. next check > 1005 + 24000 = 25005 is 25020, overshoot 15, decay 0.02*15/24000 = 1.25e-5
        final AnimalState at = LivestockRules.catchUp(state(0.2f, 1_005L), 25_020L);
        assertThat(at.familiarity()).isCloseTo(0.2f - 0.0000125f, within(TOL));
        assertThat(at.familiarityTick()).isEqualTo(25_020L);
    }

    @Test
    void familiarityAtTheDecayLimitOrAboveDoesNotDecay() {
        // TFC: condition familiarity < familiarityDecayLimit (0.3). At 0.35 nothing ever decays
        final AnimalState above = state(0.35f, 0L);
        assertThat(LivestockRules.catchUp(above, 1_000_000_000L)).isEqualTo(above);
        final AnimalState atLimit = state(0.3f, 0L);
        assertThat(LivestockRules.catchUp(atLimit, 1_000_000_000L)).isEqualTo(atLimit);
    }

    @Test
    void zeroFamiliarityNeverDecays() {
        // TFC: condition familiarity > 0
        final AnimalState zero = state(0f, 0L);
        assertThat(LivestockRules.catchUp(zero, 500_000L)).isEqualTo(zero);
    }

    @Test
    void decayClampsAtZeroAndAnchorsAtThatCheck() {
        // TFC: f 0.00001, check 24020 takes it to 1e-5 - 1.6667e-5 < 0, clamped to 0 and anchored at 24020 (the check).
        final AnimalState at = LivestockRules.catchUp(state(0.00001f, 0L), 48_040L);
        assertThat(at.familiarity()).isZero();
        assertThat(at.familiarityTick()).isEqualTo(24_020L);
    }

    @Test
    void manyCyclesInOneCatchUpMatchTfcStepping() {
        // TFC: f 0.05001, anchor 0. Checks at 24020 * k. Decays 1/60000 each. Number of decays before reaching 0:
        //  f - 3000/60000 = 0.00001 > 0 after 3000 decays (last at 24020*3000 = 72 060 000), then the 3001st clamps to 0
        //  at 24020*3001 = 72 084 020.
        final AnimalState before = LivestockRules.catchUp(state(0.05001f, 0L), 72_084_019L);
        assertThat(before.familiarity()).isCloseTo(0.00001f, within(TOL));
        assertThat(before.familiarityTick()).isEqualTo(72_060_000L);
        final AnimalState after = LivestockRules.catchUp(state(0.05001f, 0L), 72_084_020L);
        assertThat(after.familiarity()).isZero();
        assertThat(after.familiarityTick()).isEqualTo(72_084_020L);
    }

    @Test
    void catchUpIsIdempotentAtTheSameTick() {
        // Lazy: reading again at the same tick gives the same state, not a second decay
        final AnimalState once = LivestockRules.catchUp(state(0.2f, 0L), 48_040L);
        assertThat(LivestockRules.catchUp(once, 48_040L)).isEqualTo(once);
    }

    @Test
    void feedingAnAdultBelowTheCapGainsSixHundredthsAndResetsTheAnchor() {
        // TFC eatFood: setLastFedNow (fed = 3 000 000, anchor = 3 000 000), then f + 0.06f; adult min(f+0.06, cap 0.35)
        // Adult born long ago. Last familiarity write 2 999 000, so the decay check (3 023 020) is not yet due
        final AnimalState s = adultPig(0.2f, 2_999_000L, AnimalState.NEVER);
        final LivestockRules.FeedOutcome out = LivestockRules.feed(s, LivestockSpecies.PIG, 3_000_000L);
        assertThat(out.accepted()).isTrue();
        assertThat(out.state().familiarity()).isCloseTo(0.26f, within(TOL));
        assertThat(out.state().lastFedTick()).isEqualTo(3_000_000L);
        assertThat(out.state().familiarityTick()).isEqualTo(3_000_000L);
    }

    @Test
    void feedingAnAdultIsCappedAtTheSpeciesCap() {
        // TFC: min(0.30 + 0.06, 0.35) = 0.35
        final LivestockRules.FeedOutcome out = feedPig(0.30f, 3_000_000L);
        assertThat(out.state().familiarity()).isCloseTo(0.35f, within(TOL));
    }

    @Test
    void feedingAnAdultAtTheCapStillResetsTheFeedButGainsNothing() {
        // TFC: getFamiliarity() < cap is false, so no gain, but setLastFedNow ran
        final LivestockRules.FeedOutcome out = feedPig(0.35f, 3_000_000L);
        assertThat(out.accepted()).isTrue();
        assertThat(out.state().familiarity()).isEqualTo(0.35f);
        assertThat(out.state().lastFedTick()).isEqualTo(3_000_000L);
    }

    @Test
    void childGainsSixHundredthsEvenAboveTheAdultCap() {
        // TFC: getAgeType() == CHILD short-circuits the cap, no min() applied. Child born at 2 999 000 is young at 3 000 000
        final AnimalState child = new AnimalState(false, 0.5f, 2_999_000L, 2_999_000L, -1L,
            AnimalState.NEVER, AnimalState.NEVER, 0, false, AnimalState.NOT_PREGNANT);
        final LivestockRules.FeedOutcome out = LivestockRules.feed(child, LivestockSpecies.PIG, 3_000_000L);
        assertThat(out.state().familiarity()).isCloseTo(0.56f, within(TOL));
    }

    @Test
    void childGainClampsAtOne() {
        // TFC setFamiliarity clamps to [0, 1]: 0.97 + 0.06 = 1.03 -> 1
        final AnimalState child = new AnimalState(false, 0.97f, 2_999_000L, 2_999_000L, -1L,
            AnimalState.NEVER, AnimalState.NEVER, 0, false, AnimalState.NOT_PREGNANT);
        assertThat(LivestockRules.feed(child, LivestockSpecies.PIG, 3_000_000L).state().familiarity()).isEqualTo(1f);
    }

    @Test
    void feedingCatchesUpTheDecayFirst() {
        // TFC decays on checks, so the value fed on is the decayed one: 0.2 - 2/60000, plus 0.06
        final AnimalState s = state(0.2f, 0L);
        final AnimalState fedAt = LivestockRules.feed(
            new AnimalState(false, 0.2f, 0L, -10_000_000L, -1L, AnimalState.NEVER, AnimalState.NEVER, 0, false, -1L),
            LivestockSpecies.PIG, 48_040L).state();
        assertThat(fedAt.familiarity()).isCloseTo(0.26f - 2f / 60_000f, within(TOL));
        assertThat(s.familiarity()).isEqualTo(0.2f);
    }

    @Test
    void hungerIsStrictlyMoreThanOneDayAfterTheLastFeed() {
        // TFC isHungry: calendar().getTicks() > lastFed + TICKS_IN_DAY
        final AnimalState fedAtZero = adultPig(0.2f, 0L, 0L);
        assertThat(LivestockRules.isHungry(fedAtZero, 24_000L)).isFalse();
        assertThat(LivestockRules.isHungry(fedAtZero, 24_001L)).isTrue();
    }

    @Test
    void neverFedIsHungry() {
        assertThat(LivestockRules.isHungry(adultPig(0f, 0L, AnimalState.NEVER), 0L)).isTrue();
    }

    @Test
    void rejectedFeedLeavesTheStateUntouched() {
        // TFC mobInteract: not hungry -> showExtraClickInfo and FAIL, no eatFood
        final AnimalState s = adultPig(0.2f, 2_999_000L, 2_980_000L);
        final LivestockRules.FeedOutcome out = LivestockRules.feed(s, LivestockSpecies.PIG, 3_000_000L);
        assertThat(out.accepted()).isFalse();
        assertThat(out.state()).isEqualTo(s);
    }

    @Test
    void rottenFoodIsRefusedUnlessTheSpeciesEatsIt() {
        // TFC isFood: (eatsRottenFood || !rotten) && item in food tag
        assertThat(LivestockSpecies.PIG.canEat(true)).isTrue();
        assertThat(LivestockSpecies.DONKEY.canEat(true)).isFalse();
        assertThat(LivestockSpecies.DONKEY.canEat(false)).isTrue();
    }

    private static LivestockRules.FeedOutcome feedPig(float familiarity, long now) {
        return LivestockRules.feed(adultPig(familiarity, now - 1_000L, AnimalState.NEVER), LivestockSpecies.PIG, now);
    }
}
