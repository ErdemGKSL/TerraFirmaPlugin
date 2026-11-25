/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.bowl;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.bowl.BowlRules.Action;
import dev.tfp.core.bowl.BowlRules.Situation;
import org.junit.jupiter.api.Test;

/** PAR-POT-09: the order of TFC's {@code BowlBlock.useItemOn} and the renderer's mapping, against values read from the source. */
class BowlRulesTest {
    private static Situation s(boolean mainHand, boolean sneaking, boolean heldEmpty, boolean glass, boolean powder,
            boolean saltable, boolean contentEmpty, boolean contentSalt, boolean glassOp) {
        return new Situation(mainHand, sneaking, heldEmpty, glass, powder, saltable, contentEmpty, contentSalt, glassOp);
    }

    @Test
    void emptyMainHandTakesOneOrSixteenWhenSneaking() {
        assertThat(BowlRules.decide(s(true, false, true, false, false, false, false, true, false))).isEqualTo(new Action.Take(1));
        assertThat(BowlRules.decide(s(true, true, true, false, false, false, false, true, false))).isEqualTo(new Action.Take(16));
    }

    @Test
    void emptyOffHandDoesNothing() {
        assertThat(BowlRules.decide(s(false, false, true, false, false, false, false, true, false))).isEqualTo(new Action.Pass());
    }

    @Test
    void heldPowderGoesInEvenWhenTheBowlIsFull() {
        assertThat(BowlRules.decide(s(true, false, false, false, true, false, false, false, false))).isEqualTo(new Action.Insert());
    }

    @Test
    void glassWorkComesFirstAndPassesWithoutAMatchingPowder() {
        assertThat(BowlRules.decide(s(true, false, false, true, true, false, false, false, true))).isEqualTo(new Action.ApplyGlass());
        assertThat(BowlRules.decide(s(true, false, false, true, true, false, false, false, false))).isEqualTo(new Action.Pass());
        assertThat(BowlRules.decide(s(true, false, true, true, false, false, false, false, false))).as("glass before empty hand")
            .isEqualTo(new Action.Pass());
    }

    @Test
    void saltableFoodNeedsSaltInTheBowl() {
        assertThat(BowlRules.decide(s(true, false, false, false, false, true, false, true, false))).isEqualTo(new Action.Salt());
        assertThat(BowlRules.decide(s(true, false, false, false, false, true, false, false, false))).isEqualTo(new Action.Pass());
        assertThat(BowlRules.decide(s(true, false, false, false, false, true, true, true, false))).isEqualTo(new Action.Pass());
    }

    @Test
    void anythingElsePasses() {
        assertThat(BowlRules.decide(s(true, false, false, false, false, false, false, true, false))).isEqualTo(new Action.Pass());
    }

    @Test
    void insertionStopsAtSixteenAndRefusesAnotherPowder() {
        assertThat(BowlRules.accepted(0, false, 64)).isEqualTo(16);
        assertThat(BowlRules.accepted(10, true, 64)).isEqualTo(6);
        assertThat(BowlRules.accepted(10, true, 3)).isEqualTo(3);
        assertThat(BowlRules.accepted(16, true, 5)).isZero();
        assertThat(BowlRules.accepted(4, false, 5)).isZero();
        assertThat(BowlRules.accepted(0, true, 0)).isZero();
    }

    @Test
    void saltingUsesTheSmallerCount() {
        assertThat(BowlRules.toSalt(5, 16)).isEqualTo(5);
        assertThat(BowlRules.toSalt(20, 7)).isEqualTo(7);
    }

    @Test
    void textureIsThePowdersNameWithTheVanillaOnesMappedAndSaltAsFallback() {
        assertThat(BowlRules.texture("tfp:powder/hematite")).isEqualTo("hematite");
        assertThat(BowlRules.texture("tfc:powder/wood_ash")).isEqualTo("wood_ash");
        assertThat(BowlRules.texture("minecraft:redstone")).isEqualTo("redstone");
        assertThat(BowlRules.texture("minecraft:glowstone_dust")).isEqualTo("glowstone");
        assertThat(BowlRules.texture("minecraft:blaze_powder")).isEqualTo("blaze_powder");
        assertThat(BowlRules.texture("minecraft:gunpowder")).isEqualTo("gunpowder");
        assertThat(BowlRules.texture("minecraft:sugar")).isEqualTo("salt");
        assertThat(BowlRules.texture("tfp:powder/")).isEqualTo("salt");
    }

    @Test
    void layerHeightIsMthMapOfTheCount() {
        assertThat(BowlRules.layerHeight(0)).isEqualTo(0.5);
        assertThat(BowlRules.layerHeight(16)).isEqualTo(2.0);
        assertThat(BowlRules.layerHeight(8)).isEqualTo(1.25);
        assertThat(BowlRules.layerHeight(99)).isEqualTo(2.0);
    }
}
