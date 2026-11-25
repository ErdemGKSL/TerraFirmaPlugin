/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.rack;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.chest.ChestSide;
import java.util.List;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

/** PAR-DECO-05: tool rack slots and clicks, bookshelf slots and clicks, the looking order and the placement facing. */
class RackRulesTest {
    @Test
    void toolRackSlotFollowsTfcGetSlotFromPos() {
        // north and south facings read x, east and west read z; the lower half adds 2, the upper half of a column is 0 or 1
        assertThat(ToolRackRules.slot(ChestSide.NORTH, 0.2, 0.8, 0.9)).isEqualTo(0);
        assertThat(ToolRackRules.slot(ChestSide.SOUTH, 0.7, 0.8, 0.1)).isEqualTo(1);
        assertThat(ToolRackRules.slot(ChestSide.NORTH, 0.2, 0.2, 0.9)).isEqualTo(2);
        assertThat(ToolRackRules.slot(ChestSide.SOUTH, 0.7, 0.2, 0.1)).isEqualTo(3);
        assertThat(ToolRackRules.slot(ChestSide.EAST, 0.9, 0.8, 0.2)).isEqualTo(0);
        assertThat(ToolRackRules.slot(ChestSide.WEST, 0.1, 0.8, 0.7)).isEqualTo(1);
        assertThat(ToolRackRules.slot(ChestSide.EAST, 0.9, 0.2, 0.7)).isEqualTo(3);
        // the boundaries are exclusive upwards: exactly 0.5 is the low column and the lower row only below 0.5
        assertThat(ToolRackRules.slot(ChestSide.NORTH, 0.5, 0.5, 0.5)).isEqualTo(0);
        assertThat(ToolRackRules.slot(ChestSide.NORTH, 0.5001, 0.4999, 0.5)).isEqualTo(3);
    }

    @Test
    void toolRackClickTable() {
        assertThat(ToolRackRules.click(true, false)).isEqualTo(ToolRackRules.Click.PASS);
        assertThat(ToolRackRules.click(false, false)).isEqualTo(ToolRackRules.Click.TAKE);
        assertThat(ToolRackRules.click(true, true)).isEqualTo(ToolRackRules.Click.INSERT);
        assertThat(ToolRackRules.click(false, true)).isEqualTo(ToolRackRules.Click.SWAP);
    }

    @Test
    void attachedSides() {
        for (ChestSide s : ChestSide.values()) {
            assertThat(ToolRackRules.rackAttached(s)).isEqualTo(s.opposite());
            assertThat(ToolRackRules.shelfAttached(s)).isEqualTo(s);
        }
    }

    @Test
    void lookingOrderMatchesVanilla() {
        // yaw 0 looks south, 90 west, 180 north, 270 east; level pitch
        assertThat(LookingOrder.horizontal(0f, 0f).getFirst()).isEqualTo(ChestSide.SOUTH);
        assertThat(LookingOrder.horizontal(90f, 0f).getFirst()).isEqualTo(ChestSide.WEST);
        assertThat(LookingOrder.horizontal(180f, 0f).getFirst()).isEqualTo(ChestSide.NORTH);
        assertThat(LookingOrder.horizontal(270f, 0f).getFirst()).isEqualTo(ChestSide.EAST);
        // looking south-west: south is nearest to yaw 30, then west, then east, then north (opposites in reverse)
        assertThat(LookingOrder.horizontal(30f, 0f)).containsExactly(ChestSide.SOUTH, ChestSide.WEST, ChestSide.EAST, ChestSide.NORTH);
        assertThat(LookingOrder.horizontal(60f, 0f)).containsExactly(ChestSide.WEST, ChestSide.SOUTH, ChestSide.NORTH, ChestSide.EAST);
        // looking straight down still orders the four by the yaw
        assertThat(LookingOrder.horizontal(0f, 89f)).hasSize(4).startsWith(ChestSide.SOUTH);
        assertThat(LookingOrder.horizontal(180f, -89f)).hasSize(4).startsWith(ChestSide.NORTH);
    }

    @Test
    void rackFacesBackFromTheWallInFrontOfThePlayer() {
        List<ChestSide> looking = LookingOrder.horizontal(0f, 0f); // looking south
        // a wall to the south: the rack faces north (towards the player)
        assertThat(ToolRackRules.rackFacing(looking, wall(ChestSide.SOUTH))).isEqualTo(ChestSide.NORTH);
        // no wall to the south, one to the west: the next nearest looking direction is tried
        assertThat(ToolRackRules.rackFacing(LookingOrder.horizontal(30f, 0f), wall(ChestSide.WEST))).isEqualTo(ChestSide.EAST);
        // no wall at all: nothing is placed
        assertThat(ToolRackRules.rackFacing(looking, side -> false)).isNull();
    }

    @Test
    void shelfFollowsTfcsHelperOrder() {
        List<ChestSide> looking = LookingOrder.horizontal(0f, 0f); // looking south
        // the facing is the opposite of the look, and the block on the facing side carries it
        assertThat(ToolRackRules.shelfFacing(looking, wall(ChestSide.NORTH))).isEqualTo(ChestSide.NORTH);
        assertThat(ToolRackRules.shelfFacing(looking, side -> false)).isNull();
    }

    @Test
    void rackDoesNotStackOnARackFace() {
        assertThat(ToolRackRules.stacksOnRack(ChestSide.NORTH, ChestSide.NORTH)).isTrue();
        assertThat(ToolRackRules.stacksOnRack(ChestSide.NORTH, ChestSide.SOUTH)).isFalse();
        assertThat(ToolRackRules.stacksOnRack(null, ChestSide.SOUTH)).isFalse();
    }

    @Test
    void bookshelfSlotsOfTheFrontFace() {
        // facing south: x runs left to right; top row 0,1,2, bottom row 3,4,5
        assertThat(slot(ChestSide.SOUTH, ChestSide.SOUTH, 0.1, 0.9)).isEqualTo(0);
        assertThat(slot(ChestSide.SOUTH, ChestSide.SOUTH, 0.5, 0.9)).isEqualTo(1);
        assertThat(slot(ChestSide.SOUTH, ChestSide.SOUTH, 0.9, 0.9)).isEqualTo(2);
        assertThat(slot(ChestSide.SOUTH, ChestSide.SOUTH, 0.1, 0.1)).isEqualTo(3);
        assertThat(slot(ChestSide.SOUTH, ChestSide.SOUTH, 0.5, 0.1)).isEqualTo(4);
        assertThat(slot(ChestSide.SOUTH, ChestSide.SOUTH, 0.9, 0.1)).isEqualTo(5);
        // the column edges
        assertThat(slot(ChestSide.SOUTH, ChestSide.SOUTH, 0.374, 0.9)).isEqualTo(0);
        assertThat(slot(ChestSide.SOUTH, ChestSide.SOUTH, 0.375, 0.9)).isEqualTo(1);
        assertThat(slot(ChestSide.SOUTH, ChestSide.SOUTH, 0.6874, 0.9)).isEqualTo(1);
        assertThat(slot(ChestSide.SOUTH, ChestSide.SOUTH, 0.6875, 0.9)).isEqualTo(2);
        // the row edge: 0.5 is the upper row
        assertThat(slot(ChestSide.SOUTH, ChestSide.SOUTH, 0.1, 0.5)).isEqualTo(0);
        assertThat(slot(ChestSide.SOUTH, ChestSide.SOUTH, 0.1, 0.4999)).isEqualTo(3);
        // the other facings read the axis that runs left to right as seen from the front
        assertThat(slot(ChestSide.NORTH, ChestSide.NORTH, 0.9, 0.9)).isEqualTo(0);
        assertThat(OptionalInt.of(BookshelfRules.slot(ChestSide.WEST, ChestSide.WEST, 0.0, 0.9, 0.9).getAsInt())).hasValue(2);
        assertThat(BookshelfRules.slot(ChestSide.EAST, ChestSide.EAST, 1.0, 0.9, 0.9).getAsInt()).isEqualTo(0);
    }

    private static int slot(ChestSide facing, ChestSide face, double across, double y) {
        // across is the position along the south face (x); for the other facings the tests pass the matching coordinate
        return BookshelfRules.slot(facing, face, across, y, 0.5).getAsInt();
    }

    @Test
    void bookshelfOnlyTheFrontFaceHasSlots() {
        assertThat(BookshelfRules.slot(ChestSide.SOUTH, ChestSide.NORTH, 0.5, 0.5, 0.5)).isEmpty();
        assertThat(BookshelfRules.slot(ChestSide.SOUTH, ChestSide.EAST, 0.5, 0.5, 0.5)).isEmpty();
    }

    @Test
    void bookshelfClickTable() {
        assertThat(BookshelfRules.click(true, true, false)).isEqualTo(BookshelfRules.Click.ADD);
        assertThat(BookshelfRules.click(true, true, true)).isEqualTo(BookshelfRules.Click.REMOVE);
        assertThat(BookshelfRules.click(true, false, true)).isEqualTo(BookshelfRules.Click.REMOVE);
        assertThat(BookshelfRules.click(true, false, false)).isEqualTo(BookshelfRules.Click.CONSUME);
        assertThat(BookshelfRules.click(false, false, false)).isEqualTo(BookshelfRules.Click.PASS);
        assertThat(BookshelfRules.click(false, true, false)).isEqualTo(BookshelfRules.Click.CONSUME);
    }

    @Test
    void enchantPowerIsHalfAPointPerBook() {
        assertThat(BookshelfRules.enchantPower(0)).isEqualTo(0.0);
        assertThat(BookshelfRules.enchantPower(6)).isEqualTo(3.0);
    }

    private static java.util.function.Predicate<ChestSide> wall(ChestSide at) {
        return side -> side == at;
    }
}
