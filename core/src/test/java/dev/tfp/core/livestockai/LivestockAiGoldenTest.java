/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.livestockai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.animal.CarrierTable;
import dev.tfp.core.livestock.LivestockSpecies;
import dev.tfp.core.livestockai.LivestockRoster.Brain;
import dev.tfp.core.prey.Avoidance;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * PAR-FAUNA-08 golden values: the literals of TFC's {@code LivestockAi}, {@code OviparousAi}, {@code TFCCamelAi},
 * {@code TFCHorse.registerGoals}, {@code DelegatingTemptingSensor} and {@code BreedBehavior} @ c3b4d9536, and the
 * 18-species roster of {@code TFCEntities}.
 */
class LivestockAiGoldenTest {

    @Test
    void rosterIsTheEighteenDomesticSpeciesOfLivestockSpecies() {
        List<String> ids = LivestockRoster.species();
        assertThat(ids).hasSize(18).doesNotHaveDuplicates();
        assertThat(ids).containsExactlyInAnyOrderElementsOf(Arrays.stream(LivestockSpecies.values())
                .map(s -> "tfc:" + s.name().toLowerCase(Locale.ROOT)).toList());
        // every species has a carrier row with livestock rules
        for (String id : ids) {
            assertThat(CarrierTable.get().find(id)).as(id).isPresent();
            assertThat(CarrierTable.get().find(id).orElseThrow().livestock()).as(id).isPresent();
        }
    }

    @Test
    void brainsFollowTfcClasses() {
        assertThat(LivestockRoster.species(Brain.LIVESTOCK))
                .containsExactly("tfc:pig", "tfc:cow", "tfc:goat", "tfc:yak", "tfc:alpaca", "tfc:sheep", "tfc:musk_ox");
        assertThat(LivestockRoster.species(Brain.OVIPAROUS)).containsExactly("tfc:chicken", "tfc:duck", "tfc:quail");
        assertThat(LivestockRoster.species(Brain.CAMEL)).containsExactly("tfc:dromedary_camel", "tfc:bactrian_camel");
        assertThat(LivestockRoster.species(Brain.HORSE)).containsExactly("tfc:donkey", "tfc:mule", "tfc:horse");
        assertThat(LivestockRoster.species(Brain.PET)).containsExactly("tfc:cat", "tfc:dog");
        assertThat(LivestockRoster.species(Brain.SMALL_PREY)).containsExactly("tfc:rabbit");
        assertThat(Arrays.stream(Brain.values()).filter(Brain::tfcGoals))
                .containsExactly(Brain.LIVESTOCK, Brain.OVIPAROUS, Brain.CAMEL);
        assertThat(LivestockRoster.profile("tfc:horse")).isEmpty();
        assertThat(LivestockRoster.profile("tfc:wolf")).isEmpty();
    }

    @Test
    void livestockLiterals() {
        LivestockRoster.Profile p = LivestockRoster.profile("tfc:cow").orElseThrow();
        assertThat(p).isSameAs(LivestockRoster.profile("tfc:quail").orElseThrow());
        assertThat(p.swimChance()).isEqualTo(0.8f);          // new Swim(0.8F)
        assertThat(p.panicSpeed()).isEqualTo(2.0f);          // new AnimalPanic<>(2.0F)
        assertThat(p.tempt(false)).isEqualTo(1.25f);         // e.isBaby() ? 1.5F : 1.25F
        assertThat(p.tempt(true)).isEqualTo(1.5f);
        assertThat(p.followAdult()).isEqualTo(1.25f);        // BabyFollowAdult(5..16, 1.25F)
        assertThat(p.stroll()).isEqualTo(1.0f);              // RandomStroll.stroll(1.0F)
        assertThat(p.away()).isEqualTo(1.3f);                // SetWalkTargetAwayFrom(AVOID_TARGET, 1.3F, 15)
        assertThat(p.breed()).isEqualTo(1.0f);               // new BreedBehavior<>(1.0F)
        assertThat(p.avoidance()).isEqualTo(Avoidance.PREDATORS_AND_RAMMERS); // create(true)
    }

    @Test
    void camelLiterals() {
        LivestockRoster.Profile p = LivestockRoster.profile("tfc:bactrian_camel").orElseThrow();
        assertThat(p.panicSpeed()).isEqualTo(4.0f);          // CamelPanic(4.0F)
        assertThat(p.tempt(false)).isEqualTo(3.5f);          // e.isBaby() ? 2.5F : 3.5F
        assertThat(p.tempt(true)).isEqualTo(2.5f);
        assertThat(p.followAdult()).isEqualTo(2.5f);
        assertThat(p.stroll()).isEqualTo(2.0f);
        assertThat(p.away()).isEqualTo(3.2f);
        assertThat(p.breed()).isEqualTo(2.0f);
        assertThat(LivestockRoster.HORSE_TEMPT_SPEED).isEqualTo(1.25f);
        assertThat(LivestockRoster.HORSE_TEMPT_PRIORITY).isEqualTo(3);
    }

    @Test
    void temptationSensorPicksNearestFoodHolderStrictlyInsideTen() {
        List<Temptation.Candidate> c = List.of(
                new Temptation.Candidate(4 * 4, false, true, false),      // no food
                new Temptation.Candidate(9 * 9, false, true, true),       // tempting
                new Temptation.Candidate(3 * 3, true, true, true),        // spectator
                new Temptation.Candidate(2 * 2, false, false, true),      // dead
                new Temptation.Candidate(6 * 6, false, true, true));      // nearest food holder
        assertThat(Temptation.temptingPlayer(c)).hasValue(4);
        // closerThan(p, 10) is exclusive: exactly 10 blocks is out
        assertThat(Temptation.temptingPlayer(List.of(new Temptation.Candidate(100, false, true, true)))).isEmpty();
        assertThat(Temptation.temptingPlayer(List.of(new Temptation.Candidate(99.99, false, true, true)))).hasValue(0);
        assertThat(Temptation.temptingPlayer(List.of())).isEmpty();
    }

    @Test
    void followTemptationStandsWithinTwoAndAHalfAndCoolsDownAHundredTicks() {
        assertThat(Temptation.walks(2.5 * 2.5)).isTrue();
        assertThat(Temptation.walks(2.49 * 2.49)).isFalse();
        assertThat(Temptation.cooldownUntil(1000)).isEqualTo(1100);
        assertThat(Temptation.follows(true, 1099, 1100, false, false)).isFalse();
        assertThat(Temptation.follows(true, 1100, 1100, false, false)).isTrue();
        assertThat(Temptation.follows(true, 1100, 0, true, false)).as("breeding").isFalse();
        assertThat(Temptation.follows(true, 1100, 0, false, true)).as("panicking").isFalse();
        assertThat(Temptation.follows(false, 1100, 0, false, false)).isFalse();
    }

    @Test
    void breedBehaviourLiterals() {
        assertThat(BreedApproach.attempts(201, 200, true)).isTrue();
        assertThat(BreedApproach.attempts(200, 200, true)).as("strictly after").isFalse();
        assertThat(BreedApproach.attempts(500, 200, false)).as("females never start").isFalse();
        assertThat(BreedApproach.partner(List.of(
                new BreedApproach.Partner(25, true, false),
                new BreedApproach.Partner(64, false, true),
                new BreedApproach.Partner(49, true, true),
                new BreedApproach.Partner(36, true, true)))).hasValue(3);
        assertThat(BreedApproach.partner(List.of(new BreedApproach.Partner(1, true, false)))).isEmpty();
        // spawnChildAtTime = time + 60 + nextInt(50), with java.util.Random(7) as the draw source
        int draw = new Random(7).nextInt(50);
        assertThat(BreedApproach.pairAt(1000, new Random(7))).isEqualTo(1060 + draw);
        for (int seed = 0; seed < 200; seed++) {
            assertThat(BreedApproach.pairAt(0, new Random(seed))).isBetween(60L, 109L);
        }
        assertThat(BreedApproach.pairs(2.9 * 2.9, 1100, 1100)).isTrue();
        assertThat(BreedApproach.pairs(3.0 * 3.0, 1100, 1100)).as("closerThan is strict").isFalse();
        assertThat(BreedApproach.pairs(1, 1099, 1100)).isFalse();
        assertThat(BreedApproach.continues(true, 1100, 1100)).isTrue();
        assertThat(BreedApproach.continues(true, 1101, 1100)).isFalse();
        assertThat(BreedApproach.continues(false, 0, 1100)).isFalse();
    }

    @Test
    void showcaseHasAMaleAndAFemaleOfEverySpeciesOnDistinctSpots() {
        List<LivestockShowcase.Slot> slots = LivestockShowcase.layout(LivestockRoster.species());
        assertThat(slots).hasSize(36);
        assertThat(slots.stream().map(s -> s.dx() + "," + s.dz()).distinct()).hasSize(36);
        assertThat(slots.stream().filter(LivestockShowcase.Slot::male).map(LivestockShowcase.Slot::species))
                .containsExactlyElementsOf(LivestockRoster.species());
        assertThat(LivestockShowcase.size(18)).containsExactly(21, 18);
        assertThat(slots.get(0)).isEqualTo(new LivestockShowcase.Slot("tfc:pig", false, 0, 0));
        assertThat(slots.get(11)).isEqualTo(new LivestockShowcase.Slot("tfc:sheep", true, 20, 3));
        assertThat(slots.get(13)).isEqualTo(new LivestockShowcase.Slot("tfc:musk_ox", true, 0, 10));
    }
}
