/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.neighbour;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.tfp.core.world.Direction;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ShapeRulesTest {
    /** "beam" connects (state "beam:<n>") to the number of horizontal beam neighbours, like TFC's support beams. */
    static final ShapeRule<String> BEAM = new ShapeRule<>() {
        @Override
        public String id() {
            return "beam";
        }

        @Override
        public Set<String> blockIds() {
            return Set.of("beam");
        }

        @Override
        public boolean appliesTo(String state) {
            return state.startsWith("beam");
        }

        @Override
        public String decide(Neighbourhood<String> n) {
            int c = 0;
            for (Direction d : new Direction[] {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
                String s = n.at(d);
                if (s == null) {
                    return null;
                }
                if (appliesTo(s)) {
                    c++;
                }
            }
            return "beam:" + c;
        }

        @Override
        public String provisional(String placed) {
            return "beam:0";
        }
    };

    static Neighbourhood<String> around(String center, String east, String west, String others) {
        return Neighbourhood.of((dx, dy, dz) -> {
            if (dx == 0 && dy == 0 && dz == 0) {
                return center;
            }
            if (dx == 1 && dy == 0 && dz == 0) {
                return east;
            }
            if (dx == -1 && dy == 0 && dz == 0) {
                return west;
            }
            return others;
        });
    }

    @Test
    void neighbourhoodIndexesAllTwentySevenOffsets() {
        Neighbourhood<String> n = Neighbourhood.of((dx, dy, dz) -> dx + "," + dy + "," + dz);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    assertThat(n.at(dx, dy, dz)).isEqualTo(dx + "," + dy + "," + dz);
                }
            }
        }
        assertThat(n.at(Direction.UP)).isEqualTo("0,1,0");
        assertThat(n.at(Direction.NORTH)).isEqualTo("0,0,-1");
        assertThat(n.center()).isEqualTo("0,0,0");
        assertThat(n.facesKnown()).isTrue();
        assertThatThrownBy(() -> n.at(2, 0, 0)).isInstanceOf(IndexOutOfBoundsException.class);
    }

    @Test
    void unknownNeighboursAreNullNotAir() {
        Neighbourhood<String> n = around("beam:0", null, "air", "air");
        assertThat(n.known(Direction.EAST)).isFalse();
        assertThat(n.facesKnown()).isFalse();
        ShapeRules<String> rules = ShapeRules.<String>empty().with(BEAM);
        assertThat(rules.decide(n)).as("an unknown neighbour keeps the state").isEmpty();
    }

    @Test
    void decideReturnsOnlyRealChanges() {
        ShapeRules<String> rules = ShapeRules.<String>empty().with(BEAM);
        assertThat(rules.decide(around("beam:0", "beam:0", "air", "air"))).contains("beam:1");
        assertThat(rules.decide(around("beam:2", "beam:1", "beam:5", "air"))).isEmpty();
        assertThat(rules.decide(around("stone", "beam:1", "beam:1", "air"))).as("no rule applies").isEmpty();
        assertThat(rules.decide(around(null, "beam:1", "beam:1", "air"))).as("unknown centre").isEmpty();
    }

    @Test
    void registryIsImmutableFirstMatchAndFiltersById() {
        ShapeRules<String> empty = ShapeRules.empty();
        ShapeRules<String> one = empty.with(BEAM);
        assertThat(empty.isEmpty()).isTrue();
        assertThat(one.rules()).hasSize(1);
        assertThat(one.mayApply("beam")).isTrue();
        assertThat(one.mayApply("stone")).isFalse();
        assertThat(one.ruleFor("beam:3")).contains(BEAM);
        assertThatThrownBy(() -> one.with(BEAM)).isInstanceOf(IllegalArgumentException.class);
        assertThat(one.without("beam").isEmpty()).isTrue();
        assertThat(one.without("missing")).isSameAs(one);
        assertThat(BEAM.provisional("beam")).isEqualTo("beam:0");
    }

    @Test
    void ruleWithoutIdsMakesEveryBlockACandidate() {
        ShapeRule<String> any = new ShapeRule<>() {
            @Override
            public String id() {
                return "any";
            }

            @Override
            public Set<String> blockIds() {
                return Set.of();
            }

            @Override
            public boolean appliesTo(String state) {
                return false;
            }

            @Override
            public String decide(Neighbourhood<String> n) {
                return null;
            }
        };
        assertThat(ShapeRules.<String>empty().with(any).mayApply("whatever")).isTrue();
        assertThat(any.provisional("x")).isEqualTo("x");
    }
}
