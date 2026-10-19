/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.predator;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Golden values for the prey acceptance of land predators at c3b4d9536.
 *
 * <p>Sources: {@code generated/data/tfc/tags/entity_type/hunted_by_land_predators.json} (values {@code #tfc:bird_prey},
 * {@code #tfc:large_prey}, {@code tfc:rat}, {@code tfc:jerboa}, {@code tfc:lemming}, {@code tfc:mongoose},
 * {@code tfc:armadillo}), {@code bird_prey.json} (7 types) and {@code large_prey.json} (14 types), and the datagen
 * {@code BuiltinEntityTags} lines that build them. The predators themselves (wolf, cougar, bear, crocodile) and the
 * boar are not in the tag.
 */
class HuntedPreyGoldenTest {

    @Test
    void expandedTagHasTheTfcCounts() {
        // bird_prey 7 + large_prey 14 + five single types = 26 entity types
        assertThat(HuntedPrey.BIRD_PREY).hasSize(7);
        assertThat(HuntedPrey.LARGE_PREY).hasSize(14);
        assertThat(HuntedPrey.SMALL_PREY).hasSize(5);
    }

    @Test
    void everyBirdPreyTypeIsAccepted() {
        // bird_prey.json
        for (String type : new String[] {"tfc:turkey", "tfc:pheasant", "tfc:grouse", "tfc:chicken", "tfc:penguin", "tfc:quail", "tfc:duck"}) {
            assertThat(HuntedPrey.isHuntedByLandPredator(type)).as(type).isTrue();
        }
    }

    @Test
    void everyLargePreyTypeIsAccepted() {
        // large_prey.json, including the two camels and the musk ox
        for (String type : new String[] {"tfc:deer", "tfc:caribou", "tfc:bongo", "tfc:pig", "tfc:cow", "tfc:sheep", "tfc:yak",
            "tfc:musk_ox", "tfc:horse", "tfc:mule", "tfc:donkey", "tfc:goat", "tfc:dromedary_camel", "tfc:bactrian_camel"}) {
            assertThat(HuntedPrey.isHuntedByLandPredator(type)).as(type).isTrue();
        }
    }

    @Test
    void theFiveSingleTypesAreAccepted() {
        // hunted_by_land_predators.json single values
        for (String type : new String[] {"tfc:rat", "tfc:jerboa", "tfc:lemming", "tfc:mongoose", "tfc:armadillo"}) {
            assertThat(HuntedPrey.isHuntedByLandPredator(type)).as(type).isTrue();
        }
    }

    @Test
    void predatorsAndOtherAnimalsAreRefused() {
        // predators are in land_predators, not in the hunted tag; the boar and the fox are not listed
        for (String type : new String[] {"tfc:wolf", "tfc:grizzly_bear", "tfc:cougar", "tfc:crocodile", "tfc:boar", "tfc:fox",
            "tfc:rabbit", "tfc:panda", "minecraft:player", "tfc:cod"}) {
            assertThat(HuntedPrey.isHuntedByLandPredator(type)).as(type).isFalse();
        }
    }

    @Test
    void acceptanceIsCaseSensitiveAndNamespaced() {
        // entity ids are lowercase keys with the namespace; a bare name is not a tag member
        assertThat(HuntedPrey.isHuntedByLandPredator("deer")).isFalse();
        assertThat(HuntedPrey.isHuntedByLandPredator("TFC:DEER")).isFalse();
    }
}
