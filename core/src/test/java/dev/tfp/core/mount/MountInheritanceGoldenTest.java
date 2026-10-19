/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.mount;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import dev.tfp.core.livestock.LivestockSpecies;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Hand-traced foal inheritance at c3b4d9536. Each expected value is derived from TFC's {@code EntityHelpers.createOffspringAttribute}
 * (spread = |v1 - v2| + 2 * 0.15 * (max - min), centre = average, offset = (r1 + r2 + r3) / 3 - 0.5, reflected once past a bound),
 * the {@code TFCHorse.applyGenes} colour draws (variant {@code nextInt(9)}: below 4 father, below 8 mother, else {@code nextInt(7)};
 * markings {@code nextInt(5)}: below 2 father, below 4 mother, else {@code nextInt(5)}), and the {@code createGenes} species rule.
 *
 * <p>Parents used throughout: father horse (max health 30, jump 1.0, speed 0.3375, colour 5/1); mother horse (20, 0.5, 0.2,
 * colour 2/3). Each attribute's midpoint draw (0.5, 0.5, 0.5) gives offset 0, so the foal sits on the parents' average.
 */
class MountInheritanceGoldenTest {

    private static final MountStats FATHER_STATS = new MountStats(30.0, 1.0, 0.3375);
    private static final MountStats MOTHER_STATS = new MountStats(20.0, 0.5, 0.2);
    private static final HorseColour FATHER_COLOUR = new HorseColour(5, 1);
    private static final HorseColour MOTHER_COLOUR = new HorseColour(2, 3);

    private static MountGenes.Parent horse(MountStats stats, HorseColour colour) {
        return new MountGenes.Parent(LivestockSpecies.HORSE, stats, Optional.of(colour));
    }

    private static MountGenes.Parent donkey(MountStats stats) {
        return new MountGenes.Parent(LivestockSpecies.DONKEY, stats, Optional.empty());
    }

    private static final double MID = 0.5;

    @Test
    void horseFoalAveragesTheParentsAtTheMidpointDraws() {
        // health: spread 10 + 2*2.25 = 14.5, offset 0, so 25.0. jump: spread 0.5 + 2*0.09 = 0.68, centre 0.75.
        // speed: centre (0.3375 + 0.2) / 2 = 0.26875, spread irrelevant at offset 0.
        // colour: i = 3 (below 4) takes the father's variant 5; j = 3 (below 4) takes the mother's markings 3.
        final MountGenes genes = new MountGenes(horse(MOTHER_STATS, MOTHER_COLOUR), horse(FATHER_STATS, FATHER_COLOUR));
        final ScriptedMountRandom random = new ScriptedMountRandom(
            MID, MID, MID, MID, MID, MID, MID, MID, MID, 3, 3);
        final MountGenes.Foal foal = genes.inherit(random);
        random.assertConsumed();
        assertThat(foal.species()).isEqualTo(LivestockSpecies.HORSE);
        assertThat(foal.stats().maxHealth()).isCloseTo(25.0, within(1e-9));
        assertThat(foal.stats().jumpStrength()).isCloseTo(0.75, within(1e-6));
        assertThat(foal.stats().movementSpeed()).isCloseTo(0.26875, within(1e-6));
        assertThat(foal.colour()).contains(new HorseColour(5, 3));
    }

    @Test
    void reflectionAboveTheMaximumIsOnceAndNotClampedAgain() {
        // health draws (1, 1, 1): offset +0.5, so 25 + 14.5 * 0.5 = 32.25 > 30, reflected to 30 - 2.25 = 27.75.
        // A mule (donkey mother, horse father) draws no colour, so nine draws are taken in all.
        final MountGenes genes = new MountGenes(donkey(MOTHER_STATS), horse(FATHER_STATS, FATHER_COLOUR));
        final ScriptedMountRandom random = new ScriptedMountRandom(
            1.0, 1.0, 1.0, MID, MID, MID, MID, MID, MID);
        final MountGenes.Foal foal = genes.inherit(random);
        random.assertConsumed();
        assertThat(foal.stats().maxHealth()).isCloseTo(27.75, within(1e-9));
        assertThat(foal.colour()).isEmpty();
    }

    @Test
    void muleFoalDrawsNoColourAndTakesTheLowOffset() {
        // jump draws (0, 0, 0): offset -0.5, so 0.75 + 0.68 * -0.5 = 0.41, above the minimum 0.4 (no reflection).
        // mule foal: species from the donkey mother with a horse father.
        final MountGenes genes = new MountGenes(donkey(MOTHER_STATS), horse(FATHER_STATS, FATHER_COLOUR));
        final ScriptedMountRandom random = new ScriptedMountRandom(
            MID, MID, MID, 0.0, 0.0, 0.0, MID, MID, MID);
        final MountGenes.Foal foal = genes.inherit(random);
        random.assertConsumed();
        assertThat(foal.species()).isEqualTo(LivestockSpecies.MULE);
        assertThat(foal.stats().jumpStrength()).isCloseTo(0.41, within(1e-6));
        assertThat(foal.colour()).isEmpty();
    }

    @Test
    void reflectionBelowTheMinimumMirrorsBackUp() {
        // Both parents at the jump minimum (0.4): spread 0.18, centre 0.4, offset -0.5 gives 0.31, below the minimum,
        // so it reflects to min + (min - 0.31) = 0.49.
        final MountGenes genes = new MountGenes(donkey(new MountStats(15.0, 0.4, 0.1125)), donkey(new MountStats(15.0, 0.4, 0.1125)));
        final ScriptedMountRandom random = new ScriptedMountRandom(
            0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
        final MountGenes.Foal foal = genes.inherit(random);
        random.assertConsumed();
        assertThat(foal.species()).isEqualTo(LivestockSpecies.DONKEY);
        assertThat(foal.stats().jumpStrength()).isCloseTo(0.49, within(1e-6));
    }

    @Test
    void colourRulesTakeTheDrawnBranch() {
        // i below 4: father variant. j below 2: father markings. -> (5, 1)
        final HorseColour a = HorseColour.inherit(MOTHER_COLOUR, FATHER_COLOUR, new ScriptedMountRandom(0, 0));
        assertThat(a).isEqualTo(new HorseColour(5, 1));

        // i = 4 (below 8, not below 4): mother variant 2. j = 2 (below 4, not below 2): mother markings 3. -> (2, 3)
        final HorseColour b = HorseColour.inherit(MOTHER_COLOUR, FATHER_COLOUR, new ScriptedMountRandom(4, 2));
        assertThat(b).isEqualTo(new HorseColour(2, 3));

        // i = 7 (the last mother branch): mother variant 2. j = 3 (the last mother markings draw): mother markings 3.
        final HorseColour c = HorseColour.inherit(MOTHER_COLOUR, FATHER_COLOUR, new ScriptedMountRandom(7, 3));
        assertThat(c).isEqualTo(new HorseColour(2, 3));

        // i = 8: a random variant, nextInt(7) = 6. j = 4: a random marking, nextInt(5) = 4. -> (6, 4)
        final ScriptedMountRandom random = new ScriptedMountRandom(8, 6, 4, 4);
        final HorseColour d = HorseColour.inherit(MOTHER_COLOUR, FATHER_COLOUR, random);
        random.assertConsumed();
        assertThat(d).isEqualTo(new HorseColour(6, 4));
    }

    @Test
    void colourIndicesOutOfRangeAreRejected() {
        assertThatThrownBy(() -> new HorseColour(7, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new HorseColour(0, 5)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void attributeRangesMustBeOrdered() {
        assertThatThrownBy(() -> MountStats.inheritAttribute(1, 2, 5, 5, new ScriptedMountRandom(MID, MID, MID)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void offspringAttributeIsSymmetricInItsParents() {
        final double a = MountStats.inheritAttribute(20.0, 26.0, 15.0, 30.0, new ScriptedMountRandom(0.2, 0.9, 0.4));
        final double b = MountStats.inheritAttribute(26.0, 20.0, 15.0, 30.0, new ScriptedMountRandom(0.2, 0.9, 0.4));
        assertThat(a).isEqualTo(b);
    }
}
