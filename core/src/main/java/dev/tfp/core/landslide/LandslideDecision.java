/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.landslide;

import dev.tfp.core.world.Direction;

/** What a block does when it is checked for a landslide. */
public sealed interface LandslideDecision permits LandslideDecision.None, LandslideDecision.Fall, LandslideDecision.Slide {
    /** The block stays where it is. */
    record None() implements LandslideDecision {}

    /** The block falls straight down. TFC keeps it at its own position and starts a falling block there. */
    record Fall() implements LandslideDecision {}

    /** The block moves one block sideways, to the same height, into the open side {@code side}. Its offset is {@code side}. */
    record Slide(Direction side) implements LandslideDecision {
        public Slide {
            if (side == null || side == Direction.UP || side == Direction.DOWN) {
                throw new IllegalArgumentException("A slide goes sideways, not " + side);
            }
        }
    }
}
