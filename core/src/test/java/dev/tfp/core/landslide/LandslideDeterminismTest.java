/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.landslide;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.world.BlockPos;
import dev.tfp.core.world.Direction;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** M3-PHYS-02 determinism: a decision is a function of the snapshot and the draws it is given, and of nothing else. */
class LandslideDeterminismTest {
    private static final BlockPos ORIGIN = new BlockPos(0, 0, 0);

    /** One row of the golden box for the given centre and non-air cells (indexes as in {@link LandslideGoldenTest}). */
    static String box(char centre, Map<BlockPos, Character> cells) {
        char[] c = new char[27];
        Arrays.fill(c, '.');
        c[13] = centre;
        cells.forEach((p, code) -> c[(p.y() + 1) * 9 + (p.z() + 1) * 3 + (p.x() + 1)] = code);
        return new String(c);
    }

    /** Dirt at the origin, stone below, stone to the east. North, south and west are open: three open sides. */
    static String threeOpenSides() {
        return box('D', Map.of(new BlockPos(0, -1, 0), 'R', new BlockPos(1, 0, 0), 'R'));
    }

    /** Dirt at the origin, stone below and to the east. Only the west side is open: its own block below is open. */
    static String oneOpenSide() {
        return box('D', Map.of(
            new BlockPos(0, -1, 0), 'R',
            new BlockPos(1, 0, 0), 'R',
            new BlockPos(0, -1, -1), 'R',
            new BlockPos(0, -1, 1), 'R'));
    }

    /** A random source that scripts its results and records each bound it was asked for. */
    static final class Scripted implements LandslideRandom {
        private final Deque<Integer> values;
        final List<Integer> bounds = new ArrayList<>();

        Scripted(int... values) {
            this.values = new ArrayDeque<>();
            for (int v : values) {
                this.values.add(v);
            }
        }

        @Override
        public int nextInt(int bound) {
            bounds.add(bound);
            return values.poll();
        }
    }

    @Test
    void sameInputsGiveTheSameDecisionEveryTime() {
        LandslideRules rules = LandslideGoldenTest.rules();
        LandslideView view = LandslideGoldenTest.view(threeOpenSides());
        LandslideDecision first = rules.decide(view, ORIGIN, new LandslideGoldenTest.Lcg(7));
        for (int i = 0; i < 1000; i++) {
            assertThat(rules.decide(view, ORIGIN, new LandslideGoldenTest.Lcg(7))).isEqualTo(first);
        }
    }

    @Test
    void decisionDoesNotDependOnEarlierCalls() {
        LandslideRules rules = LandslideGoldenTest.rules();
        LandslideView a = LandslideGoldenTest.view(threeOpenSides());
        LandslideView b = LandslideGoldenTest.view(oneOpenSide());
        LandslideDecision before = rules.decide(a, ORIGIN, new LandslideGoldenTest.Lcg(5));
        rules.decide(b, ORIGIN, new LandslideGoldenTest.Lcg(9));
        rules.decide(a, ORIGIN, new LandslideGoldenTest.Lcg(0));
        assertThat(rules.decide(a, ORIGIN, new LandslideGoldenTest.Lcg(5))).isEqualTo(before);
    }

    @Test
    void theScriptedDrawPicksTheIndexedOpenSideInNorthEastSouthWestOrder() {
        LandslideRules rules = LandslideGoldenTest.rules();
        LandslideView view = LandslideGoldenTest.view(threeOpenSides());
        // open sides in order: north, south, west
        assertThat(rules.decide(view, ORIGIN, new Scripted(0))).isEqualTo(new LandslideDecision.Slide(Direction.NORTH));
        assertThat(rules.decide(view, ORIGIN, new Scripted(1))).isEqualTo(new LandslideDecision.Slide(Direction.SOUTH));
        assertThat(rules.decide(view, ORIGIN, new Scripted(2))).isEqualTo(new LandslideDecision.Slide(Direction.WEST));
    }

    @Test
    void theSideDrawIsAskedOnceWithTheNumberOfOpenSides() {
        LandslideRules rules = LandslideGoldenTest.rules();
        Scripted random = new Scripted(1);
        rules.decide(LandslideGoldenTest.view(threeOpenSides()), ORIGIN, random);
        assertThat(random.bounds).containsExactly(3);
    }

    @Test
    void aSingleOpenSideStillDrawsOnceAsTfcDoes() {
        LandslideRules rules = LandslideGoldenTest.rules();
        Scripted random = new Scripted(0);
        LandslideDecision decision = rules.decide(LandslideGoldenTest.view(oneOpenSide()), ORIGIN, random);
        assertThat(decision).isEqualTo(new LandslideDecision.Slide(Direction.WEST));
        assertThat(random.bounds).containsExactly(1);
    }

    @Test
    void aFallDrawsNothing() {
        LandslideRules rules = LandslideGoldenTest.rules();
        Scripted random = new Scripted();
        LandslideView view = LandslideGoldenTest.view(box('D', Map.of()));
        assertThat(rules.decide(view, ORIGIN, random)).isEqualTo(new LandslideDecision.Fall());
        assertThat(random.bounds).isEmpty();
    }
}
