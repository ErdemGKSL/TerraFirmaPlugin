/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.mount;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.tfp.core.livestock.AnimalState;
import dev.tfp.core.livestock.LivestockSpecies;
import org.junit.jupiter.api.Test;

/**
 * Hand-traced mating and foal-species rules at c3b4d9536.
 *
 * <p>Sources: {@code TFCHorse.canMate} (opposite gender, both ready, then {@code checkExtraBreedConditions}: the other is a
 * {@code TFCDonkey} or a {@code Horse}, and both pass {@code vanillaParentingCheck}, which is not a vehicle or passenger,
 * tamed, and not a baby), {@code TFCDonkey.canMate}, {@code TFCMule.canMate} and {@code TFCMule.isReadyToMate} (both false),
 * {@code DromedaryCamel.canMate} and {@code BactrianCamel.canMate} (the same class, opposite gender, both ready, both
 * {@code vanillaParentingCheck} with no tamed test). The readiness test is {@code TFCAnimalProperties.isReadyToMate} via
 * {@link dev.tfp.core.livestock.BreedingRules}: adult, familiarity at least 0.3, not fertilized, not hungry, past the cooldown,
 * not pregnant.
 *
 * <p>Adult animals here are born at tick 0 and the test time is {@code NOW}, well past 80 player days. They were fed at
 * {@code NOW} (not hungry), and they never mated. Familiarity is 0.5 unless a case says otherwise.
 */
class MountBreedingGoldenTest {

    private static final long NOW = 10_000_000L;

    private static AnimalState animal(boolean male, float familiarity, long birthTick, long pregnantDay) {
        return new AnimalState(male, familiarity, -1L, birthTick, AnimalState.NO_OLD_TICK, NOW, AnimalState.NEVER, 0,
            false, pregnantDay);
    }

    private static AnimalState adult(boolean male) {
        return animal(male, 0.5f, 0L, AnimalState.NOT_PREGNANT);
    }

    private static MountBreeding.Mate mate(AnimalState state, LivestockSpecies species) {
        return new MountBreeding.Mate(state, species, false);
    }

    @Test
    void horseFamilyPairsMateAcrossHorseAndDonkey() {
        // TFCHorse.checkExtraBreedConditions: other is TFCDonkey or Horse. TFCDonkey: other is TFCDonkey or Horse.
        assertThat(MountBreeding.canMate(mate(adult(false), LivestockSpecies.HORSE), mate(adult(true), LivestockSpecies.HORSE), NOW)).isTrue();
        assertThat(MountBreeding.canMate(mate(adult(false), LivestockSpecies.HORSE), mate(adult(true), LivestockSpecies.DONKEY), NOW)).isTrue();
        assertThat(MountBreeding.canMate(mate(adult(false), LivestockSpecies.DONKEY), mate(adult(true), LivestockSpecies.HORSE), NOW)).isTrue();
        assertThat(MountBreeding.canMate(mate(adult(false), LivestockSpecies.DONKEY), mate(adult(true), LivestockSpecies.DONKEY), NOW)).isTrue();
    }

    @Test
    void muleNeverMatesAndCannotBeReadyEvenAtFullFamiliarity() {
        // TFCMule.canMate is false, and TFCMule.isReadyToMate is false, so no pair with a mule is allowed.
        final AnimalState fullMule = animal(false, 1.0f, 0L, AnimalState.NOT_PREGNANT);
        assertThat(MountBreeding.isReadyToMate(fullMule, LivestockSpecies.MULE, NOW)).isFalse();
        assertThat(MountBreeding.isReadyToMate(fullMule, LivestockSpecies.HORSE, NOW)).isTrue();
        assertThat(MountBreeding.canMate(mate(fullMule, LivestockSpecies.MULE), mate(adult(true), LivestockSpecies.HORSE), NOW)).isFalse();
        assertThat(MountBreeding.canMate(mate(adult(false), LivestockSpecies.HORSE), mate(adult(true), LivestockSpecies.MULE), NOW)).isFalse();
    }

    @Test
    void sameGenderNeverMates() {
        assertThat(MountBreeding.canMate(mate(adult(false), LivestockSpecies.HORSE), mate(adult(false), LivestockSpecies.DONKEY), NOW)).isFalse();
    }

    @Test
    void readinessNeedsThirtyPercentFamiliarityAndNoPregnancy() {
        // TFC readiness: familiarity >= 0.3. Below that, even two adult horses do not mate.
        final AnimalState low = animal(false, 0.29f, 0L, AnimalState.NOT_PREGNANT);
        assertThat(MountBreeding.canMate(mate(low, LivestockSpecies.HORSE), mate(adult(true), LivestockSpecies.HORSE), NOW)).isFalse();
        final AnimalState exactly = animal(false, 0.3f, 0L, AnimalState.NOT_PREGNANT);
        assertThat(MountBreeding.isReadyToMate(exactly, LivestockSpecies.HORSE, NOW)).isTrue();
        // TFC readiness: pregnantTime <= 0. A mare with a pregnancy day (day 5) is not ready.
        final AnimalState pregnant = animal(false, 0.5f, 0L, 5L);
        assertThat(MountBreeding.isReadyToMate(pregnant, LivestockSpecies.HORSE, NOW)).isFalse();
    }

    @Test
    void childrenDoNotMate() {
        // A horse born at NOW - 1000 is a child (adult only after 80 * 28 800 ticks).
        final AnimalState child = animal(false, 0.5f, NOW - 1000L, AnimalState.NOT_PREGNANT);
        assertThat(MountBreeding.canMate(mate(child, LivestockSpecies.HORSE), mate(adult(true), LivestockSpecies.HORSE), NOW)).isFalse();
    }

    @Test
    void rideTheHorseFamilyRefusesAndTheCamelRefusesToo() {
        // TFC vanillaParentingCheck: a vehicle or a passenger is not a parent (horses and camels).
        final MountBreeding.Mate ridden = new MountBreeding.Mate(adult(false), LivestockSpecies.HORSE, true);
        assertThat(MountBreeding.canMate(ridden, mate(adult(true), LivestockSpecies.DONKEY), NOW)).isFalse();
        final MountBreeding.Mate riddenCamel = new MountBreeding.Mate(adult(false), LivestockSpecies.DROMEDARY_CAMEL, true);
        assertThat(MountBreeding.canMate(riddenCamel, mate(adult(true), LivestockSpecies.DROMEDARY_CAMEL), NOW)).isFalse();
    }

    @Test
    void camelsMateOnlyWithTheirOwnSpecies() {
        // DromedaryCamel.canMate: same class. BactrianCamel.canMate: same class.
        assertThat(MountBreeding.canMate(mate(adult(false), LivestockSpecies.DROMEDARY_CAMEL), mate(adult(true), LivestockSpecies.DROMEDARY_CAMEL), NOW)).isTrue();
        assertThat(MountBreeding.canMate(mate(adult(false), LivestockSpecies.BACTRIAN_CAMEL), mate(adult(true), LivestockSpecies.BACTRIAN_CAMEL), NOW)).isTrue();
        assertThat(MountBreeding.canMate(mate(adult(false), LivestockSpecies.DROMEDARY_CAMEL), mate(adult(true), LivestockSpecies.BACTRIAN_CAMEL), NOW)).isFalse();
        assertThat(MountBreeding.canMate(mate(adult(false), LivestockSpecies.DROMEDARY_CAMEL), mate(adult(true), LivestockSpecies.HORSE), NOW)).isFalse();
    }

    @Test
    void foalSpeciesFollowsTheParentPairs() {
        // TFCHorse.createGenes: isMule = male instanceof TFCDonkey. TFCDonkey.createGenes: isMule = male instanceof TFCHorse.
        assertThat(MountGenes.foalSpecies(LivestockSpecies.HORSE, LivestockSpecies.HORSE)).isEqualTo(LivestockSpecies.HORSE);
        assertThat(MountGenes.foalSpecies(LivestockSpecies.HORSE, LivestockSpecies.DONKEY)).isEqualTo(LivestockSpecies.MULE);
        assertThat(MountGenes.foalSpecies(LivestockSpecies.DONKEY, LivestockSpecies.HORSE)).isEqualTo(LivestockSpecies.MULE);
        assertThat(MountGenes.foalSpecies(LivestockSpecies.DONKEY, LivestockSpecies.DONKEY)).isEqualTo(LivestockSpecies.DONKEY);
    }

    @Test
    void foalSpeciesRejectsMulesAndCamels() {
        assertThatThrownBy(() -> MountGenes.foalSpecies(LivestockSpecies.HORSE, LivestockSpecies.MULE))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MountGenes.foalSpecies(LivestockSpecies.DROMEDARY_CAMEL, LivestockSpecies.HORSE))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void horseFoalNeedsBothColours() {
        final MountStats stats = new MountStats(20.0, 0.5, 0.2);
        assertThatThrownBy(() -> new MountGenes(
            new MountGenes.Parent(LivestockSpecies.HORSE, stats, java.util.Optional.empty()),
            new MountGenes.Parent(LivestockSpecies.HORSE, stats, java.util.Optional.of(new HorseColour(0, 0)))))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
