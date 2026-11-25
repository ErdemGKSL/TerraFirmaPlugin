/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.fishing;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** PAR-FAUNA-18: which animals run GetHookedGoal, and the lazy hook exhaustion. */
class HookedSpeciesTest {
    @Test
    void sixteenFreshwaterFishCodTropicalPufferfishDolphinAndOrcaGoForHooks() {
        assertThat(HookedSpecies.ids()).hasSize(21)
                .contains("tfc:largemouth_bass", "tfc:red_piranha", "tfc:cod", "tfc:tropical_fish", "tfc:pufferfish", "tfc:dolphin", "tfc:orca");
        assertThat(HookedSpecies.goesForHook("tfc:squid")).isFalse();
        assertThat(HookedSpecies.goesForHook("tfc:jellyfish")).isFalse();
        assertThat(HookedSpecies.goesForHook("tfc:manatee")).isFalse();
        assertThat(HookedSpecies.goesForHook("tfc:lobster")).isFalse();
    }

    @Test
    void lazyExhaustionEqualsSteppedTicks() {
        for (int start = 0; start <= 120; start += 7) {
            for (int elapsed = 0; elapsed <= 130; elapsed += 5) {
                Hook stepped = new Hook(start, 40L);
                for (int i = 0; i < elapsed; i++) {
                    stepped = stepped.tick();
                }
                assertThat(new Hook(start, 40L).after(elapsed)).as("%d after %d", start, elapsed).isEqualTo(stepped);
            }
        }
    }

    @Test
    void aNegativeElapsedTimeChangesNothing() {
        assertThat(new Hook(10, 3L).after(-5)).isEqualTo(new Hook(10, 3L));
    }
}
