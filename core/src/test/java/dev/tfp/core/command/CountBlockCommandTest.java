/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.tfp.core.command.BlockSelector.Kind;
import dev.tfp.core.command.CommandArgs.Error;
import dev.tfp.core.command.CommandArgs.Parsed;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * {@code /tfp count} from TFC {@code CountBlockCommand.java}: the radius is {@code integer(1, 250)} (line 33), and the
 * loops visit x, then z, then y over the build height (lines 52 to 61). The grid is a small in-memory world.
 */
class CountBlockCommandTest {
    static final BlockSelector STONE = new BlockSelector(Kind.BLOCK, "minecraft:stone", Map.of());

    /** A 3 by 3 column square over heights 0 to 2, with stone at a few cells, one of them outside the square. */
    static final class Grid {
        private final Set<String> stone = Set.of("0,0,0", "1,2,-1", "-1,1,1", "2,0,0", "0,3,0", "0,-1,0");

        boolean matches(int dx, int y, int dz) {
            return stone.contains(dx + "," + y + "," + dz);
        }
    }

    @Test
    void parsesRadiusAndBlock() {
        assertThat(CountBlockCommand.parse(List.of("3", "stone")))
            .isEqualTo(new Parsed.Ok<>(new CountBlockCommand.Request(3, STONE)));
    }

    /** CountBlockCommand.java line 33: radius 0 and radius 251 are refused. */
    @Test
    void radiusOutsideOneToTwoFiftyIsRefused() {
        assertThat(CountBlockCommand.parse(List.of("0", "stone")))
            .isEqualTo(new Parsed.Refused<>(Error.OUT_OF_RANGE, "0"));
        assertThat(CountBlockCommand.parse(List.of("251", "stone")))
            .isEqualTo(new Parsed.Refused<>(Error.OUT_OF_RANGE, "251"));
    }

    @Test
    void missingBlockIsRefused() {
        assertThat(CountBlockCommand.parse(List.of("3"))).isEqualTo(new Parsed.Refused<>(Error.MISSING_ARGUMENT, ""));
    }

    /**
     * CountBlockCommand.java lines 52-61 with radius 1: the square is 3 by 3 (dx and dz from -1 to 1). Stone at (2,0,0)
     * is outside it, and stone at (0,3,0) and (0,-1,0) is outside the height range 0 to 2. Three cells are counted.
     */
    @Test
    void countsOnlyTheSquareAndTheHeights() {
        var grid = new Grid();
        assertThat(CountBlockCommand.count(1, 0, 3, grid::matches)).isEqualTo(3);
    }

    /** CountBlockCommand.java line 56: a height range that is empty counts nothing. */
    @Test
    void emptyHeightRangeCountsNothing() {
        assertThat(CountBlockCommand.count(1, 3, 3, new Grid()::matches)).isZero();
    }

    /** CountBlockCommand.java lines 52-61 with the grid widened to radius 2: the cell at (2,0,0) now counts. */
    @Test
    void widerRadiusReachesTheOuterColumns() {
        assertThat(CountBlockCommand.count(2, 0, 3, new Grid()::matches)).isEqualTo(4);
    }

    @Test
    void radiusOutsideTheRangeIsAProgrammingError() {
        assertThatThrownBy(() -> CountBlockCommand.count(0, 0, 3, (dx, y, dz) -> true))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theSelectorDecidesWhatIsCounted() {
        var grid = new Grid();
        var ore = new BlockSelector(Kind.TAG, "tfc:ore_bearing", Map.of());
        assertThat(CountBlockCommand.count(1, 0, 3, (dx, y, dz) -> ore.matches("x", Set.of(), Map.of()))).isZero();
        assertThat(CountBlockCommand.count(1, 0, 3, (dx, y, dz) -> grid.matches(dx, y, dz) && STONE.matches(
            "minecraft:stone", Set.of(), Map.of()))).isEqualTo(3);
    }
}
