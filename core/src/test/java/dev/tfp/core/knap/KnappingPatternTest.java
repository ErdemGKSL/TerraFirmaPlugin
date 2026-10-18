/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/test/java/net/dries007/tfc/test/util/KnappingPatternTest.java @ c3b4d9536 (the five grid tests), with the shape and
 * mirror tests of the pattern parser added here
 */
package dev.tfp.core.knap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class KnappingPatternTest {

    @Test
    void trueByDefault() {
        KnappingPattern p = new KnappingPattern();
        for (int i = 0; i < 25; i++) {
            assertThat(p.get(i)).as("index=%d", i).isTrue();
        }
    }

    @Test
    void setAll() {
        KnappingPattern p = new KnappingPattern();
        p.setAll(true);
        for (int i = 0; i < 25; i++) assertThat(p.get(i)).as("index=%d", i).isTrue();
        p.setAll(false);
        for (int i = 0; i < 25; i++) assertThat(p.get(i)).as("index=%d", i).isFalse();
    }

    @Test
    void getAndSetIndex() {
        for (int i = 0; i < 25; i++) {
            KnappingPattern p = new KnappingPattern();
            p.set(i, false);
            assertThat(p.get(i)).isFalse();
            p.set(i, true);
            assertThat(p.get(i)).isTrue();
        }
    }

    @Test
    void getAndSetCoordinates() {
        for (int x = 0; x < 5; x++) {
            for (int y = 0; y < 5; y++) {
                KnappingPattern p = new KnappingPattern();
                p.set(x, y, false);
                assertThat(p.get(x, y)).isFalse();
                p.set(x, y, true);
                assertThat(p.get(x, y)).isTrue();
            }
        }
    }

    @Test
    void setFalseLeavesOthersUnchanged() {
        for (int x = 0; x < 5; x++) {
            for (int y = 0; y < 5; y++) {
                KnappingPattern p = new KnappingPattern();
                p.set(x, y, false);
                assertThat(p.get(x, y)).isFalse();
                for (int x0 = 0; x0 < 5; x0++) {
                    for (int y0 = 0; y0 < 5; y0++) {
                        if (x0 != x || y0 != y) assertThat(p.get(x0, y0)).as("(%d, %d)", x0, y0).isTrue();
                    }
                }
            }
        }
    }

    @Test
    void clickIsIdempotentAndOnlyTouchesItsCell() {
        KnappingPattern p = new KnappingPattern();
        p.click(KnappingRules.buttonIndex(2, 3));
        p.click(KnappingRules.buttonIndex(2, 3));
        assertThat(p.get(2, 3)).isFalse();
        assertThat(IntStream.range(0, 25).filter(p::get).count()).isEqualTo(24);
    }

    @Test
    void buttonIndexIsRowMajor() {
        assertThat(KnappingRules.buttonIndex(0, 0)).isZero();
        assertThat(KnappingRules.buttonIndex(4, 0)).isEqualTo(4);
        assertThat(KnappingRules.buttonIndex(0, 4)).isEqualTo(20);
        assertThatThrownBy(() -> KnappingRules.buttonIndex(5, 0)).isInstanceOf(IndexOutOfBoundsException.class);
    }

    @Test
    void parsesRowsWhereAnyNonSpaceIsOn() {
        KnappingPattern p = KnappingPattern.of(List.of("X   X", "XXXXX", " XXX "), Optional.of(false));
        assertThat(p.getWidth()).isEqualTo(5);
        assertThat(p.getHeight()).isEqualTo(3);
        assertThat(p.get(0, 0)).isTrue();
        assertThat(p.get(1, 0)).isFalse();
        assertThat(p.get(0, 2)).isFalse();
        assertThat(p.get(1, 2)).isTrue();
        assertThat(p.rows()).containsExactly("#   #", "#####", " ### ");
    }

    @Test
    void rejectsBadShapes() {
        assertThatThrownBy(() -> KnappingPattern.of(List.of(), Optional.of(false))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> KnappingPattern.of(List.of("#".repeat(5), "#".repeat(5), "#".repeat(5), "#".repeat(5), "#".repeat(5), "#".repeat(5)), Optional.of(false)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> KnappingPattern.of(List.of("######"), Optional.of(false))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> KnappingPattern.of(List.of("##", "###"), Optional.of(false))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> KnappingPattern.of(List.of("##"), Optional.empty())).isInstanceOf(IllegalArgumentException.class);
        assertThat(KnappingPattern.of(List.of("#####", "#####", "#####", "#####", "#####"), Optional.empty()).getWidth()).isEqualTo(5);
    }

    @Test
    void matchesAMirroredPattern() {
        // A pattern with one side only: the grid that is its mirror image matches only through the mirror rule
        KnappingPattern pattern = KnappingPattern.of(List.of("#    ", "#    ", "#    ", "#    ", "#    "), Optional.empty());
        KnappingPattern mirrored = KnappingPattern.of(List.of("    #", "    #", "    #", "    #", "    #"), Optional.empty());
        KnappingPattern grid = new KnappingPattern();
        grid.setAll(false);
        for (int y = 0; y < 5; y++) grid.set(4, y, true);
        assertThat(grid.matches(pattern)).isTrue();
        assertThat(grid.matches(mirrored)).isTrue();
        assertThat(pattern.matches(mirrored)).isTrue();
    }

    @Test
    void matchesAtAnOffsetForSmallerPatterns() {
        // A 2x1 pattern "on, off" (a space is off) with default off: the grid matches with its on cell at (0, 0)
        KnappingPattern pattern = KnappingPattern.of(List.of("# "), Optional.of(false));
        assertThat(KnappingPattern.grid("#...." + ".....".repeat(4)).matches(pattern)).isTrue();
        // An on cell that is outside the window at every offset breaks the default, so the pattern does not match
        assertThat(KnappingPattern.grid("##..." + ".....".repeat(4)).matches(pattern)).isFalse();
        // Off cells everywhere match nothing that needs an on cell
        assertThat(KnappingPattern.grid(".".repeat(25)).matches(pattern)).isFalse();
    }

    @Test
    void equalityIgnoresBitsOutsideTheSize() {
        KnappingPattern a = KnappingPattern.of(List.of("##", "##"), Optional.of(false));
        KnappingPattern b = KnappingPattern.of(List.of("##", "##"), Optional.of(false));
        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
        assertThat(a).isNotEqualTo(KnappingPattern.of(List.of("##", "##"), Optional.of(true)));
    }
}
