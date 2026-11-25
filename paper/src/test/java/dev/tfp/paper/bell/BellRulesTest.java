/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.bell;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.tfp.core.world.Direction;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** PAR-METAL-18: TFC's pitch and vanilla's placement rule, pure. */
class BellRulesTest {
    @Test
    void pitchIsTfcsAndAHammerLowersItByATenth() {
        assertThat(BellRules.pitch("bronze", false)).isEqualTo(0.8f);
        assertThat(BellRules.pitch("brass", false)).isEqualTo(0.6f);
        assertThat(BellRules.pitch("bronze", true)).isEqualTo(0.8f - 0.1f);
        assertThat(BellRules.pitch("brass", true)).isEqualTo(0.6f - 0.1f);
        assertThat(BellRules.VOLUME).isEqualTo(2.0f);
        assertThatThrownBy(() -> BellRules.pitch("gold", false)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void idsRoundTrip() {
        assertThat(BellRules.metal("tfp:brass_bell")).isEqualTo("brass");
        assertThat(BellRules.metal("tfp:bronze_bell")).isEqualTo("bronze");
        assertThat(BellRules.metal("tfp:gold_bell")).isNull();
        assertThat(BellRules.id("brass")).isEqualTo("tfp:brass_bell");
    }

    @Test
    void floorAndCeilingFaceThePlayer() {
        assertThat(BellRules.place(Direction.UP, Direction.EAST, d -> false)).isEqualTo(new BellRules.Placement("floor", "east"));
        assertThat(BellRules.place(Direction.DOWN, Direction.NORTH, d -> false))
            .isEqualTo(new BellRules.Placement("ceiling", "north"));
    }

    @Test
    void aSideClickHangsFromOneWallFacingAwayFromTheClickedFace() {
        // clicked the north face of a block to the south: the bell is on its north side and its arm points to the wall, so it faces south
        assertThat(BellRules.place(Direction.NORTH, Direction.SOUTH, d -> false))
            .isEqualTo(new BellRules.Placement("single_wall", "south"));
        assertThat(BellRules.place(Direction.EAST, Direction.WEST, d -> false))
            .isEqualTo(new BellRules.Placement("single_wall", "west"));
    }

    @Test
    void sturdyBlocksOnBothSidesHoldItBetweenWalls() {
        Set<Direction> sturdy = Set.of(Direction.WEST, Direction.EAST);
        assertThat(BellRules.place(Direction.EAST, Direction.NORTH, sturdy::contains))
            .isEqualTo(new BellRules.Placement("double_wall", "west"));
        // along z the same two blocks do not count
        assertThat(BellRules.place(Direction.NORTH, Direction.NORTH, sturdy::contains).attachment()).isEqualTo("single_wall");
        assertThat(BellRules.place(Direction.SOUTH, Direction.NORTH, Set.of(Direction.NORTH, Direction.SOUTH)::contains))
            .isEqualTo(new BellRules.Placement("double_wall", "north"));
        // only one side sturdy is not enough
        assertThat(BellRules.place(Direction.WEST, Direction.NORTH, Set.of(Direction.WEST)::contains).attachment())
            .isEqualTo("single_wall");
    }
}
