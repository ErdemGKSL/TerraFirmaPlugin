/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.livestock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * M5-LIV-01 golden breeding values, hand-traced from TFC at c3b4d9536:
 * {@code TFCAnimalProperties.onFertilized} (fertilized, last fed yesterday, male addUses(5), male last fed yesterday),
 * {@code MammalProperties.onFertilized} (pregnancy day = total calendar days), {@code MammalProperties.tickAnimalData}
 * (birth when {@code pregnantTime > 0 && totalCalendarDays >= pregnantTime + gestationDays && fertilized}; litter
 * {@code Mth.nextInt(1, childCount)}, inclusive; mother addUses(10)), {@code setBabyTraits}/{@code birthChildren}
 * (offspring familiarity {@code f < 0.9F ? f / 2 : f * 0.9F}), {@code TFCAnimal.canMate},
 * {@code TFCAnimalProperties.isReadyToMate} and {@code OviparousAnimal.makeEgg} (hatch day = total calendar days + hatchDays).
 */
class BreedingGoldenTest {
    private static final long DAY = 24_000L;

    /** An adult (birth long ago), a given familiarity and last feed, never mated, not fertilized. */
    private static AnimalState adult(boolean male, float familiarity, long familiarityTick, long lastFed) {
        return new AnimalState(male, familiarity, familiarityTick, -10_000_000L, -1L, lastFed, AnimalState.NEVER, 0,
            false, AnimalState.NOT_PREGNANT);
    }

    @Test
    void fertilizationMarksTheFemaleFertilizedHungryAndRecordsTheDay() {
        // TFC: calendar 121 000 is day 5 (121000 / 24000). female fertilized; setLastFedYesterday -> lastFed = now - 24 000
        final AnimalState female = adult(false, 0.5f, 0L, 900_000L);
        final AnimalState male = adult(true, 0.5f, 0L, 900_000L);
        final BreedingRules.Pairing pair = BreedingRules.fertilize(female, male, LivestockSpecies.PIG, 1_000_000L, 121_000L);
        assertThat(pair.female().fertilized()).isTrue();
        assertThat(pair.female().pregnantDay()).isEqualTo(5L);
        assertThat(pair.female().lastFedTick()).isEqualTo(976_000L);
        // isHungry is strict, so the female becomes hungry one tick after the pairing
        assertThat(LivestockRules.isHungry(pair.female(), 1_000_000L)).isFalse();
        assertThat(LivestockRules.isHungry(pair.female(), 1_000_001L)).isTrue();
        // the male loses 5 uses and is also set hungry
        assertThat(pair.male().uses()).isEqualTo(5);
        assertThat(pair.male().lastFedTick()).isEqualTo(976_000L);
        assertThat(pair.male().fertilized()).isFalse();
    }

    @Test
    void fertilizingAFertilizedFemaleDoesNothing() {
        // TFC getBreedOffspring: only when !isFertilized()
        final AnimalState female = adult(false, 0.5f, 0L, 900_000L).withFertilized(true).withPregnantDay(3L);
        final AnimalState male = adult(true, 0.5f, 0L, 900_000L);
        final BreedingRules.Pairing pair = BreedingRules.fertilize(female, male, LivestockSpecies.PIG, 1_000_000L, 121_000L);
        assertThat(pair.female()).isEqualTo(female);
        assertThat(pair.male()).isEqualTo(male);
    }

    @Test
    void pigBirthIsDueOnTheDayGestationEnds() {
        // TFC pig gestation 19. Fertilized on day 5, so due when totalDays >= 5 + 19 = 24, i.e. calendar 576 000.
        final AnimalState pregnant = adult(false, 0.5f, 0L, 900_000L).withFertilized(true).withPregnantDay(5L);
        assertThat(BreedingRules.gestationDue(pregnant, LivestockSpecies.PIG, 575_999L)).isFalse();
        assertThat(BreedingRules.gestationDue(pregnant, LivestockSpecies.PIG, 576_000L)).isTrue();
    }

    @Test
    void cowGestationIsFiftyEightCalendarDays() {
        // TFC ServerConfig 742: cow gestation 58. Fertilized day 2 -> due on day 60
        final AnimalState pregnant = adult(false, 0.5f, 0L, 900_000L).withFertilized(true).withPregnantDay(2L);
        assertThat(BreedingRules.gestationDue(pregnant, LivestockSpecies.COW, 60L * DAY - 1)).isFalse();
        assertThat(BreedingRules.gestationDue(pregnant, LivestockSpecies.COW, 60L * DAY)).isTrue();
    }

    @Test
    void femaleFertilizedOnDayZeroNeverGivesBirth() {
        // TFC MammalProperties: condition is getPregnantTime() > 0. A day-0 pregnancy stores 0, so the check never passes.
        // Kept as a TFC quirk, not fixed.
        final AnimalState female = adult(false, 0.5f, 0L, 900_000L);
        final BreedingRules.Pairing pair = BreedingRules.fertilize(female, adult(true, 0.5f, 0L, 900_000L),
            LivestockSpecies.PIG, 1_000_000L, 0L);
        assertThat(pair.female().pregnantDay()).isZero();
        assertThat(BreedingRules.gestationDue(pair.female(), LivestockSpecies.PIG, 19L * DAY)).isFalse();
        assertThat(BreedingRules.gestationDue(pair.female(), LivestockSpecies.PIG, 100L * DAY)).isFalse();
        assertThat(BreedingRules.birth(pair.female(), LivestockSpecies.PIG, 100L * DAY, 100L * DAY, 100L * DAY, 0)).isEqualTo(Optional.empty());
    }

    @Test
    void birthClearsThePregnancyAddsTenUsesAndGivesOffspringHalfTheFamiliarity() {
        // TFC: birthChildren (familiarity 0.5 < 0.9 so child gets 0.25), then fertilized=false, pregnantTime=-1, addUses(10)
        final AnimalState pregnant = adult(false, 0.5f, 2_000_000L, 1_990_000L).withFertilized(true).withPregnantDay(5L);
        final BreedingRules.Birth birth = BreedingRules.birth(pregnant, LivestockSpecies.PIG, 2_000_000L, 576_000L, 576_000L, 0)
            .orElseThrow();
        assertThat(birth.kids()).isEqualTo(1);
        assertThat(birth.offspringFamiliarity()).isCloseTo(0.25f, within(1e-6f));
        assertThat(birth.mother().fertilized()).isFalse();
        assertThat(birth.mother().pregnantDay()).isEqualTo(AnimalState.NOT_PREGNANT);
        assertThat(birth.mother().uses()).isEqualTo(10);
    }

    @Test
    void litterSizeIsOneToChildCountInclusive() {
        // TFC Mth.nextInt(random, 1, childCount): pig childCount 10 gives 1..10, draw in [0,10) -> kids = 1 + draw
        final AnimalState pregnant = adult(false, 0.5f, 2_000_000L, 1_990_000L).withFertilized(true).withPregnantDay(5L);
        assertThat(BreedingRules.birth(pregnant, LivestockSpecies.PIG, 2_000_000L, 576_000L, 576_000L, 9).orElseThrow().kids())
            .isEqualTo(10);
        assertThatThrownBy(() -> BreedingRules.birth(pregnant, LivestockSpecies.PIG, 2_000_000L, 576_000L, 576_000L, 10))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void offspringFamiliarityFollowsTheTfcThresholdInFloat() {
        // TFC: f < 0.9F ? f / 2 : f * 0.9F
        assertThat(BreedingRules.offspringFamiliarity(0f)).isZero();
        assertThat(BreedingRules.offspringFamiliarity(0.5f)).isCloseTo(0.25f, within(1e-6f));
        assertThat(BreedingRules.offspringFamiliarity(0.89f)).isCloseTo(0.445f, within(1e-6f));
        // 0.9F is not less than 0.9F, so 0.9 takes the multiply branch
        assertThat(BreedingRules.offspringFamiliarity(0.9f)).isCloseTo(0.81f, within(1e-6f));
        assertThat(BreedingRules.offspringFamiliarity(0.95f)).isCloseTo(0.855f, within(1e-6f));
        assertThat(BreedingRules.offspringFamiliarity(1f)).isCloseTo(0.9f, within(1e-6f));
    }

    @Test
    void birthIsDecayedFirstSoOffspringInheritTheDecayedFamiliarity() {
        // TFC tickAnimalData: decay runs before the birth check. Mother 0.2 anchored at 0, birth check first passes at
        // 48 040: decayed to 0.2 - 2/60000 before halving -> 0.1 - 1/60000
        final AnimalState pregnant = adult(false, 0.2f, 0L, 900_000L).withFertilized(true).withPregnantDay(5L);
        final BreedingRules.Birth birth = BreedingRules.birth(pregnant, LivestockSpecies.PIG, 48_040L, 576_000L, 48_040L, 0).orElseThrow();
        assertThat(birth.offspringFamiliarity()).isCloseTo((0.2f - 2f / 60_000f) / 2f, within(1e-6f));
    }

    @Test
    void birthIsDueOnTheCalendarTickOfTheDay() {
        // dueCalendarTick: (pregnantDay 5 + gestation 19) * 24 000 = 576 000 for a pig
        final AnimalState pregnant = adult(false, 0.5f, 0L, 900_000L).withFertilized(true).withPregnantDay(5L);
        assertThat(BreedingRules.dueCalendarTick(pregnant, LivestockSpecies.PIG)).isEqualTo(576_000L);
    }

    @Test
    void notYetDueBirthReturnsEmpty() {
        final AnimalState pregnant = adult(false, 0.5f, 0L, 900_000L).withFertilized(true).withPregnantDay(5L);
        assertThat(BreedingRules.birth(pregnant, LivestockSpecies.PIG, 2_000_000L, 575_999L, 575_999L, 0)).isEqualTo(Optional.empty());
    }

    @Test
    void readyToMateNeedsFamiliarityAndAFreshFeedAndTheMatingCooldown() {
        // TFC isReadyToMate: adult, familiarity >= 0.3, not fertilized, not hungry, lastMate + 24 000 <= now, not pregnant
        final long now = 24_000L; // not hungry: 24 000 > 0 + 24 000 is false
        assertThat(BreedingRules.isReadyToMate(adult(false, 0.3f, now, 0L), LivestockSpecies.PIG, now)).isTrue();
        assertThat(BreedingRules.isReadyToMate(adult(false, 0.29f, now, 0L), LivestockSpecies.PIG, now)).isFalse();
        assertThat(BreedingRules.isReadyToMate(adult(false, 0.3f, now, AnimalState.NEVER), LivestockSpecies.PIG, now)).isFalse();
        // lastMate = 1 gives 1 + 24 000 <= 24 000 false: still cooling down
        assertThat(BreedingRules.isReadyToMate(adult(false, 0.3f, now, 0L).withLastMateTick(1L), LivestockSpecies.PIG, now)).isFalse();
        assertThat(BreedingRules.isReadyToMate(adult(false, 0.3f, now, 0L).withLastMateTick(0L), LivestockSpecies.PIG, now)).isTrue();
        assertThat(BreedingRules.isReadyToMate(adult(false, 0.3f, now, 0L).withFertilized(true), LivestockSpecies.PIG, now)).isFalse();
        assertThat(BreedingRules.isReadyToMate(adult(false, 0.3f, now, 0L).withPregnantDay(5L), LivestockSpecies.PIG, now)).isFalse();
    }

    @Test
    void canMateNeedsOppositeGenders() {
        final AnimalState female = adult(false, 0.5f, 24_000L, 0L);
        final AnimalState male = adult(true, 0.5f, 24_000L, 0L);
        assertThat(BreedingRules.canMate(female, male, LivestockSpecies.PIG, 24_000L)).isTrue();
        assertThat(BreedingRules.canMate(female, female, LivestockSpecies.PIG, 24_000L)).isFalse();
    }

    @Test
    void eggHatchesHatchDaysAfterItIsLaid() {
        // TFC OviparousAnimal.makeEgg: EggComponent.of(baby, totalCalendarDays + hatchDays), chicken hatchDays 8.
        // Laid on day 10 (calendar 240 000), hatch day 18
        assertThat(BreedingRules.eggHatchDay(10L * DAY, LivestockSpecies.CHICKEN)).isEqualTo(18L);
    }

    @Test
    void oviparousFertilizationDoesNotStartAPregnancy() {
        // TFC: pregnancy is only for MammalProperties; chicken keeps pregnantTime -1
        final BreedingRules.Pairing pair = BreedingRules.fertilize(adult(false, 0.5f, 0L, 900_000L),
            adult(true, 0.5f, 0L, 900_000L), LivestockSpecies.CHICKEN, 1_000_000L, 121_000L);
        assertThat(pair.female().fertilized()).isTrue();
        assertThat(pair.female().pregnantDay()).isEqualTo(AnimalState.NOT_PREGNANT);
    }
}
