/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.craft;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

/** PAR-PROC-03: the crafting grid rules (vanilla trimming, shrinking, shaped/shapeless matching, TFC's input slot). */
class AdvancedCraftGridTest {
    private static final Predicate<String> EMPTY = Objects::isNull;
    /** Ingredients are item names; "*" accepts anything. */
    private static final BiPredicate<String, String> TEST = (i, s) -> i.equals("*") || i.equals(s);

    private static List<String> grid(String... slots) {
        return Arrays.asList(slots);
    }

    @Test
    void theGridIsTrimmedToItsNonEmptyBox() {
        AdvancedCraftGrid.Input<String> in = AdvancedCraftGrid.positioned(3, grid(
                null, null, null,
                null, "a", null,
                null, "b", "c"), EMPTY);
        assertThat(in.width()).isEqualTo(2);
        assertThat(in.height()).isEqualTo(2);
        assertThat(in.items()).containsExactly("a", null, "b", "c");
        assertThat(in.fullIndex(0)).isEqualTo(4);
        assertThat(in.fullIndex(3)).isEqualTo(8);
        assertThat(AdvancedCraftGrid.positioned(2, grid(null, null, null, null), EMPTY).width()).isZero();
    }

    @Test
    void shrinkRemovesBlankRowsAndColumnsLikeVanilla() {
        assertThat(AdvancedCraftGrid.shrink(List.of("KB ", "S  ", " B "))).containsExactly("KB", "S ", " B");
        assertThat(AdvancedCraftGrid.shrink(List.of("   ", " X ", "   "))).containsExactly("X");
        assertThat(AdvancedCraftGrid.shrink(List.of("ABC", "DEF", "GHI"))).containsExactly("ABC", "DEF", "GHI");
        assertThat(AdvancedCraftGrid.shrink(List.of("   "))).isEmpty();
    }

    @Test
    void shapedMatchesPlainAndMirroredAnywhereInTheGrid() {
        List<String> cells = AdvancedCraftGrid.cells(List.of("ab", "c "), Map.of('a', "A", 'b', "B", 'c', "C")::get);
        assertThat(cells).containsExactly("A", "B", "C", null);
        AdvancedCraftGrid.Input<String> plain = AdvancedCraftGrid.positioned(3, grid(
                null, null, null,
                null, "A", "B",
                null, "C", null), EMPTY);
        assertThat(AdvancedCraftGrid.shapedMatch(cells, 2, 2, plain, TEST, EMPTY, 0)).isEqualTo(0);
        AdvancedCraftGrid.Input<String> mirrored = AdvancedCraftGrid.positioned(3, grid(
                "B", "A", null,
                null, "C", null,
                null, null, null), EMPTY);
        // TFC translateMatch: mirrored layout, pattern cell 0 (A) sits at trimmed column width - 1 - 0
        assertThat(AdvancedCraftGrid.shapedMatch(cells, 2, 2, mirrored, TEST, EMPTY, 0)).isEqualTo(1);
        assertThat(mirrored.fullIndex(1)).isEqualTo(1);
        // A blank cell needs an empty slot; an extra item breaks the match; the size must be the pattern's
        AdvancedCraftGrid.Input<String> extra = AdvancedCraftGrid.positioned(2, grid("A", "B", "C", "D"), EMPTY);
        assertThat(AdvancedCraftGrid.shapedMatch(cells, 2, 2, extra, TEST, EMPTY, 0)).isEqualTo(-1);
        AdvancedCraftGrid.Input<String> small = AdvancedCraftGrid.positioned(2, grid("A", "B", null, null), EMPTY);
        assertThat(AdvancedCraftGrid.shapedMatch(cells, 2, 2, small, TEST, EMPTY, 0)).isEqualTo(-1);
    }

    @Test
    void tfcTestsTheMirroredLayoutFirstForTheInputSlot() {
        // A symmetric pattern of two wildcards: both layouts match, TFC takes the mirrored one, so the input cell
        // (row 0, column 1) is found in trimmed column 0.
        List<String> cells = List.of("*", "*");
        AdvancedCraftGrid.Input<String> in = AdvancedCraftGrid.positioned(3, grid(
                "x", "y", null,
                null, null, null,
                null, null, null), EMPTY);
        int target = AdvancedCraftGrid.dissolveRowColumn(0, 1, 2);
        assertThat(target).isEqualTo(1);
        assertThat(AdvancedCraftGrid.shapedMatch(cells, 2, 1, in, TEST, EMPTY, target)).isEqualTo(0);
    }

    @Test
    void shapelessNeedsAPerfectAssignment() {
        List<String> ingredients = List.of("*", "A");
        assertThat(AdvancedCraftGrid.shapelessMatches(ingredients, grid("A", null, "B", null), TEST, EMPTY)).isTrue();
        // "*" first would take A greedily; the matching must move it to B
        assertThat(AdvancedCraftGrid.shapelessMatches(ingredients, grid(null, "A", "B"), TEST, EMPTY)).isTrue();
        assertThat(AdvancedCraftGrid.shapelessMatches(ingredients, grid("B", "C"), TEST, EMPTY)).isFalse();
        assertThat(AdvancedCraftGrid.shapelessMatches(ingredients, grid("A", "B", "C"), TEST, EMPTY)).isFalse();
        assertThat(AdvancedCraftGrid.shapelessMatches(ingredients, grid("A"), TEST, EMPTY)).isFalse();
    }
}
