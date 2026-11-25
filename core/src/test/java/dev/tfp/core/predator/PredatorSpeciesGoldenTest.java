/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.predator;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.animal.WildAnimalRoll;
import org.junit.jupiter.api.Test;

/**
 * Golden values for the predator registrations at c3b4d9536 ({@code TFCEntities} attribute suppliers, the
 * {@code create*} factories' diurnal, sound and pack flags).
 */
class PredatorSpeciesGoldenTest {

    @Test
    void twelvePredatorsAllWildAnimals() {
        assertThat(PredatorSpecies.ids()).hasSize(12);
        assertThat(WildAnimalRoll.SPECIES).containsAll(PredatorSpecies.ids());
    }

    @Test
    void bearsHuntByDayEveryoneElseByNight() {
        for (String id : PredatorSpecies.ids()) {
            final boolean bear = id.endsWith("_bear");
            assertThat(PredatorSpecies.profile(id).orElseThrow().diurnal()).as(id).isEqualTo(bear);
        }
    }

    @Test
    void attributes() {
        final PredatorSpecies.Attributes wolf = PredatorSpecies.profile("tfc:wolf").orElseThrow().attributes();
        assertThat(wolf).isEqualTo(new PredatorSpecies.Attributes(40, 0.30000001192092896, 1, 6, 0, 1));
        assertThat(PredatorSpecies.profile("tfc:grizzly_bear").orElseThrow().attributes().knockbackResistance()).isEqualTo(0.8);
        final PredatorSpecies.Attributes tiger = PredatorSpecies.profile("tfc:tiger").orElseThrow().attributes();
        assertThat(tiger.maxHealth()).isEqualTo(30);
        assertThat(tiger.attackDamage()).isEqualTo(7);
        assertThat(tiger.knockbackResistance()).isEqualTo(0.25);
        final PredatorSpecies.Attributes croc = PredatorSpecies.profile("tfc:crocodile").orElseThrow().attributes();
        assertThat(croc.attackKnockback()).isEqualTo(0);
        assertThat(croc.knockbackResistance()).isEqualTo(0.90);
    }

    @Test
    void packFlagsAndSounds() {
        final PredatorSpecies.Profile wolf = PredatorSpecies.profile("tfc:wolf").orElseThrow();
        assertThat(wolf.pack()).isTrue();
        assertThat(wolf.tamable()).isTrue();
        assertThat(wolf.howling()).isTrue();
        assertThat(wolf.sound()).isEqualTo("tfc_wolf");
        final PredatorSpecies.Profile direwolf = PredatorSpecies.profile("tfc:direwolf").orElseThrow();
        assertThat(direwolf.tamable()).isFalse();
        assertThat(direwolf.howling()).isTrue();
        assertThat(direwolf.sound()).isEqualTo("dog");
        final PredatorSpecies.Profile hyena = PredatorSpecies.profile("tfc:hyena").orElseThrow();
        assertThat(hyena.howling()).isFalse();
        assertThat(PredatorSpecies.profile("tfc:panther").orElseThrow().sound()).isEqualTo("cougar");
        assertThat(PredatorSpecies.profile("tfc:black_bear").orElseThrow().sound()).isEqualTo("bear");
        assertThat(PredatorSpecies.profile("tfc:deer")).isEmpty();
    }
}
