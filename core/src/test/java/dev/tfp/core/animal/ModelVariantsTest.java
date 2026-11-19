/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.animal;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.livestock.LivestockAge;
import org.junit.jupiter.api.Test;

/** PAR-FAUNA-03: the shipped variant table picks TFC's texture by age and gender and the parts by gender. */
class ModelVariantsTest {
    private final ModelVariants v = ModelVariants.get();

    @Test
    void cowLooksFollowAgeAndGender() {
        assertThat(v.pick("tfc:cow", false, LivestockAge.ADULT)).contains("tfp_cow");
        assertThat(v.pick("tfc:cow", false, LivestockAge.OLD)).contains("tfp_cow_old");
        assertThat(v.pick("tfc:cow", true, LivestockAge.ADULT)).contains("tfp_cow_m");
        assertThat(v.pick("tfc:cow", true, LivestockAge.OLD)).contains("tfp_cow_old_m");
    }

    @Test
    void maleTexturesAndBabyTextures() {
        assertThat(v.pick("tfc:duck", true, LivestockAge.ADULT)).contains("tfp_duck_male");
        assertThat(v.pick("tfc:duck", false, LivestockAge.ADULT)).contains("tfp_duck");
        assertThat(v.pick("tfc:duck", true, LivestockAge.CHILD)).contains("tfp_duck_baby");
        assertThat(v.pick("tfc:chicken", true, LivestockAge.OLD)).contains("tfp_chicken_male_old");
        assertThat(v.pick("tfc:turkey", true, LivestockAge.ADULT)).contains("tfp_turkey_male");
    }

    @Test
    void speciesWithoutAModelAreDrawnAsTheirCarrier() {
        assertThat(v.pick("tfc:pig", false, LivestockAge.ADULT)).isEmpty();
        assertThat(v.pick("tfc:goat", true, LivestockAge.OLD)).isEmpty();
    }

    @Test
    void everyAnimalWithAModelFileHasEveryGenderAndStage() {
        for (String species : new String[] {"alpaca", "bactrian_camel", "bison", "chicken", "cow", "deer", "lion", "sheep", "wolf", "yak"}) {
            for (boolean male : new boolean[] {false, true}) {
                for (LivestockAge age : LivestockAge.values()) {
                    assertThat(v.pick("tfc:" + species, male, age)).as(species + " " + male + " " + age).isPresent();
                }
            }
        }
    }
}
