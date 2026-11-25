/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.scribing;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.scribing.ScribingRules.Kind;
import org.junit.jupiter.api.Test;

class ScribingRulesTest {
    @Test
    void blankTextClearsACustomNameOnly() {
        assertThat(ScribingRules.rename(true, "Old", "").kind()).isEqualTo(Kind.CLEAR);
        assertThat(ScribingRules.rename(true, "Old", "   ").kind()).isEqualTo(Kind.CLEAR);
        assertThat(ScribingRules.rename(true, "Old", null).kind()).isEqualTo(Kind.CLEAR);
        assertThat(ScribingRules.rename(false, "Iron Ingot", "").kind()).isEqualTo(Kind.NONE);
    }

    @Test
    void aDifferentTextSetsTheNameAndTheSameTextDoesNot() {
        assertThat(ScribingRules.rename(false, "Iron Ingot", "Ingot of Doom"))
            .isEqualTo(ScribingRules.Rename.set("Ingot of Doom"));
        assertThat(ScribingRules.rename(false, "Iron Ingot", "Iron Ingot").kind()).isEqualTo(Kind.NONE);
        assertThat(ScribingRules.rename(true, "Doom", "Doom").kind()).isEqualTo(Kind.NONE);
    }

    @Test
    void takingNeedsInkOrCreative() {
        assertThat(ScribingRules.mayPickup(false, false)).isFalse();
        assertThat(ScribingRules.mayPickup(false, true)).isTrue();
        assertThat(ScribingRules.mayPickup(true, false)).isTrue();
    }
}
