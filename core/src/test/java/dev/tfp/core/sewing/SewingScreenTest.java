/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.sewing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.tfp.core.craft.SewingRecipe;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** PAR-PROC-07: the grid click mapping of the sewing screen. */
class SewingScreenTest {
    @Test
    void everySquareHasItsOwnSlotInColumnsZeroToSevenOfRowsZeroToThree() {
        Set<Integer> slots = new HashSet<>();
        for (int square = 0; square < SewingRecipe.SQUARES; square++) {
            int slot = SewingScreen.squareSlot(square);
            assertThat(slot % 9).isLessThan(8);
            assertThat(slot / 9).isLessThan(4);
            assertThat(SewingScreen.squareAt(slot)).isEqualTo(square);
            slots.add(slot);
        }
        assertThat(slots).hasSize(32);
    }

    @Test
    void squaresAreReadRowByRowLikeTheRecipeStrings() {
        assertThat(SewingScreen.squareAt(0)).isEqualTo(0);
        assertThat(SewingScreen.squareAt(7)).isEqualTo(7);
        assertThat(SewingScreen.squareAt(9)).isEqualTo(8);
        assertThat(SewingScreen.squareAt(27 + 7)).isEqualTo(31);
    }

    @Test
    void columnEightAndTheLowerRowsHoldNoSquare() {
        assertThat(SewingScreen.squareAt(8)).isEqualTo(-1);
        assertThat(SewingScreen.squareAt(35)).isEqualTo(-1);
        assertThat(SewingScreen.squareAt(36)).isEqualTo(-1);
        assertThat(SewingScreen.squareAt(-1)).isEqualTo(-1);
    }

    @Test
    void stitchPointsFillTheFirstFiveRowsOfTheChest() {
        for (int stitch = 0; stitch < SewingRecipe.STITCHES; stitch++) {
            assertThat(SewingScreen.stitchSlot(stitch)).isEqualTo(stitch);
            assertThat(SewingScreen.stitchAt(stitch)).isEqualTo(stitch);
        }
        assertThat(SewingScreen.stitchAt(45)).isEqualTo(-1);
        assertThat(SewingScreen.stitchAt(-1)).isEqualTo(-1);
    }

    @Test
    void theLastRowHoldsFiveItemSlotsAndFourButtonsOnTopOfNoGridSlot() {
        Set<Integer> row = new HashSet<>(Set.of(SewingScreen.SLOT_YARN, SewingScreen.SLOT_TOOL, SewingScreen.SLOT_INPUT_1,
            SewingScreen.SLOT_INPUT_2, SewingScreen.SLOT_RESULT, SewingScreen.BUTTON_BURLAP, SewingScreen.BUTTON_WOOL,
            SewingScreen.BUTTON_REMOVE, SewingScreen.BUTTON_NEEDLE));
        assertThat(row).hasSize(9).allMatch(s -> s >= 45 && s <= 53);
        assertThat(row).allMatch(s -> SewingScreen.stitchAt(s) == -1 && SewingScreen.squareAt(s) == -1);
        assertThat(SewingScreen.isInput(SewingScreen.SLOT_YARN)).isTrue();
        assertThat(SewingScreen.isInput(SewingScreen.SLOT_INPUT_2)).isTrue();
        assertThat(SewingScreen.isInput(SewingScreen.SLOT_RESULT)).isFalse();
        assertThat(SewingScreen.isInput(SewingScreen.BUTTON_BURLAP)).isFalse();
    }

    @Test
    void outOfRangeIndexesAreRejected() {
        assertThatThrownBy(() -> SewingScreen.squareSlot(32)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SewingScreen.stitchSlot(45)).isInstanceOf(IllegalArgumentException.class);
    }
}
