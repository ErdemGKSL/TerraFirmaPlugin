/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/rotation/FluidPipeBlock.java @ c3b4d9536 (updateConnectedSides: the
 *       openSides == 0, == 1 and the straight-through cases; connectsToPipeInDirection)
 */
package dev.tfp.core.machine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.tfp.core.world.Direction;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class FluidPipeRulesTest {

    /** A straight pipe between a neighbour on each side keeps both faces: TFC's {@code openSides >= 2} branch. */
    @Test
    void aStraightRunKeepsTheConnectingFaces() {
        assertThat(FluidPipeRules.connections(EnumSet.of(Direction.WEST, Direction.EAST), null, Set.of()))
            .containsExactlyInAnyOrder(Direction.WEST, Direction.EAST);
    }

    /**
     * TFC {@code updateConnectedSides} with {@code openSides == 1}: the pipe connects to its one neighbour and straight out through
     * the opposite face, so the end of a pipe run reaches into the open space beyond it.
     */
    @Test
    void aPipeEndConnectsStraightOut() {
        assertThat(FluidPipeRules.connections(EnumSet.of(Direction.WEST), null, Set.of()))
            .containsExactlyInAnyOrder(Direction.WEST, Direction.EAST);
    }

    /** A pump on the top face is a connecting neighbour, so a pipe under a pump connects up and straight down. */
    @Test
    void aPipeUnderAPumpConnectsUpAndDown() {
        assertThat(FluidPipeRules.connections(EnumSet.of(Direction.UP), null, Set.of()))
            .containsExactlyInAnyOrder(Direction.UP, Direction.DOWN);
    }

    /** Two neighbours that are not opposite make a corner: exactly the two faces. */
    @Test
    void aCornerConnectsItsTwoFaces() {
        assertThat(FluidPipeRules.connections(EnumSet.of(Direction.NORTH, Direction.EAST), null, Set.of()))
            .containsExactlyInAnyOrder(Direction.NORTH, Direction.EAST);
    }

    /**
     * TFC {@code updateConnectedSides} with {@code openSides == 0}: the placement look direction (the default) and its opposite,
     * so an isolated pipe is a straight piece along the direction the player faced.
     */
    @Test
    void anIsolatedPipeUsesThePreferredFace() {
        assertThat(FluidPipeRules.connections(Set.of(), Direction.NORTH, Set.of()))
            .containsExactlyInAnyOrder(Direction.NORTH, Direction.SOUTH);
    }

    /**
     * TFC {@code updateShape} passes no default: the first face of the current state that is connected is the default, in
     * {@code Direction.values()} order.
     */
    @Test
    void anIsolatedPipeWithNoPreferenceKeepsItsCurrentFace() {
        assertThat(FluidPipeRules.connections(Set.of(), null, Set.of(Direction.EAST)))
            .containsExactlyInAnyOrder(Direction.EAST, Direction.WEST);
        assertThat(FluidPipeRules.connections(Set.of(), null, Set.of(Direction.UP, Direction.NORTH)))
            .containsExactlyInAnyOrder(Direction.UP, Direction.DOWN);
    }

    /** TFC asserts a default exists for an isolated pipe; without one there is no face to connect. */
    @Test
    void anIsolatedPipeWithNoFaceToUseIsRefused() {
        assertThatThrownBy(() -> FluidPipeRules.connections(Set.of(), null, Set.of()))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
