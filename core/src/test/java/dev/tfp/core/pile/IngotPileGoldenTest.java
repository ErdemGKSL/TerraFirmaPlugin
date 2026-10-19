/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.pile;

import static dev.tfp.core.pile.PileCell.AIR;
import static dev.tfp.core.pile.PileCell.OTHER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.tfp.core.pile.AddDecision.Action;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Golden values of TFC's ingot pile constants and hand-traced add and remove cases, at TFC c3b4d9536.
 *
 * <p>Sources: {@code IngotPileBlock.COUNT = COUNT_1_64} (IngotPileBlock.java:41, TFCBlockStateProperties.java:53),
 * {@code DoubleIngotPileBlock.DOUBLE_COUNT = COUNT_1_36} (DoubleIngotPileBlock.java:21, TFCBlockStateProperties.java:52),
 * the eight single shapes (IngotPileBlock.java:43-52) and six double shapes (DoubleIngotPileBlock.java:23-30), the shape lookups
 * {@code (count - 1) / 8} (IngotPileBlock.java:170) and {@code (count - 1) / 6} (DoubleIngotPileBlock.java:40), the layer
 * {@code (i + 8) / 8} and {@code (i + 6) / 6} (client IngotPileBlockModel.java:40, DoubleIngotPileBlockModel.java:40), the
 * item tags (InteractionManager.java:351-352, TFCTags.java:616), the add branches (InteractionManager.java:430-506), the remove
 * climb (IngotPileBlock.java:82-109) and the picked item (IngotPileBlockEntity.java:152).
 */
class IngotPileGoldenTest {

    private static final String IRON = "tfc:metal/wrought_iron_ingot";
    private static final String COPPER = "tfc:metal/copper_ingot";
    private static final String STONE = "tfc:rock/raw/granite";

    @Test
    void singleKindConstantsMatchTfc() {
        // IngotPileBlock.java:41 and TFCBlockStateProperties.java:53: COUNT_1_64; 8 shapes at IngotPileBlock.java:43-52.
        assertThat(PileKind.INGOT.capacity()).isEqualTo(64);
        assertThat(PileKind.INGOT.perLayer()).isEqualTo(8);
        assertThat(PileKind.INGOT.layers()).isEqualTo(8);
        assertThat(PileKind.INGOT.itemTag()).isEqualTo("c:ingots");
    }

    @Test
    void doubleKindConstantsMatchTfc() {
        // DoubleIngotPileBlock.java:21 and TFCBlockStateProperties.java:52: COUNT_1_36; 6 shapes at DoubleIngotPileBlock.java:24-29.
        assertThat(PileKind.DOUBLE_INGOT.capacity()).isEqualTo(36);
        assertThat(PileKind.DOUBLE_INGOT.perLayer()).isEqualTo(6);
        assertThat(PileKind.DOUBLE_INGOT.layers()).isEqualTo(6);
        assertThat(PileKind.DOUBLE_INGOT.itemTag()).isEqualTo("c:double_ingots");
    }

    @Test
    void shapeIndexAtLayerBoundariesMatchTfcLookup() {
        // IngotPileBlock.java:170 SHAPES[(count - 1) / 8]: counts 1-8 use shape 0, 9 uses 1, 64 uses 7.
        assertThat(PileKind.INGOT.shapeIndex(1)).isZero();
        assertThat(PileKind.INGOT.shapeIndex(8)).isZero();
        assertThat(PileKind.INGOT.shapeIndex(9)).isEqualTo(1);
        assertThat(PileKind.INGOT.shapeIndex(16)).isEqualTo(1);
        assertThat(PileKind.INGOT.shapeIndex(17)).isEqualTo(2);
        assertThat(PileKind.INGOT.shapeIndex(64)).isEqualTo(7);
        // DoubleIngotPileBlock.java:40 SHAPES[(count - 1) / 6]: counts 1-6 use shape 0, 7 uses 1, 36 uses 5.
        assertThat(PileKind.DOUBLE_INGOT.shapeIndex(1)).isZero();
        assertThat(PileKind.DOUBLE_INGOT.shapeIndex(6)).isZero();
        assertThat(PileKind.DOUBLE_INGOT.shapeIndex(7)).isEqualTo(1);
        assertThat(PileKind.DOUBLE_INGOT.shapeIndex(36)).isEqualTo(5);
    }

    @Test
    void shapeIndexRejectsCountsOutsideTheBlockState() {
        // COUNT_1_64 and COUNT_1_36 bound the states: 0 and 65 (single), 0 and 37 (double) do not exist.
        assertThatThrownBy(() -> PileKind.INGOT.shapeIndex(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PileKind.INGOT.shapeIndex(65)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PileKind.DOUBLE_INGOT.shapeIndex(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PileKind.DOUBLE_INGOT.shapeIndex(37)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void singleCollisionTopsMatchTfcShapes() {
        // IngotPileBlock.java:44-51: box tops 2, 4, 6 .. 16 for shapes 0-7.
        for (int shape = 0; shape < 8; shape++) {
            assertThat(PileKind.INGOT.topSixteenths(shape * 8 + 1)).isEqualTo(2.0 * (shape + 1));
        }
        assertThat(PileKind.INGOT.topSixteenths(64)).isEqualTo(16.0);
    }

    @Test
    void doubleCollisionTopsMatchTfcShapes() {
        // DoubleIngotPileBlock.java:24-29: box tops 1/6*16, 2/6*16 .. 5/6*16, then 16.
        double[] tfcTops = {1f / 6 * 16, 2f / 6 * 16, 3f / 6 * 16, 4f / 6 * 16, 5f / 6 * 16, 16};
        for (int shape = 0; shape < 6; shape++) {
            assertThat(PileKind.DOUBLE_INGOT.topSixteenths(shape * 6 + 1)).isCloseTo(tfcTops[shape], org.assertj.core.data.Offset.offset(1e-4));
        }
        assertThat(PileKind.DOUBLE_INGOT.topSixteenths(36)).isEqualTo(16.0);
    }

    @Test
    void layerOfIngotMatchesTfcModelLayers() {
        // IngotPileBlockModel.java:40 layer = (i + 8) / 8, with i 0-based: ingots 0-7 are layer 1, 8-15 layer 2, 63 layer 8.
        assertThat(PileKind.INGOT.layerOf(0)).isEqualTo(1);
        assertThat(PileKind.INGOT.layerOf(7)).isEqualTo(1);
        assertThat(PileKind.INGOT.layerOf(8)).isEqualTo(2);
        assertThat(PileKind.INGOT.layerOf(63)).isEqualTo(8);
        // DoubleIngotPileBlockModel.java:40 layer = (i + 6) / 6: ingots 0-5 are layer 1, 35 is layer 6.
        assertThat(PileKind.DOUBLE_INGOT.layerOf(0)).isEqualTo(1);
        assertThat(PileKind.DOUBLE_INGOT.layerOf(5)).isEqualTo(1);
        assertThat(PileKind.DOUBLE_INGOT.layerOf(6)).isEqualTo(2);
        assertThat(PileKind.DOUBLE_INGOT.layerOf(35)).isEqualTo(6);
    }

    @Test
    void addWithSneakOnNonFullPileAddsToIt() {
        // InteractionManager.java:443-449: a clicked pile below capacity takes one ingot in place.
        AddDecision decision = IngotPileRules.decideAdd(PileKind.INGOT, true, new PileCell.Filled(PileKind.INGOT, 5), List.of());
        assertThat(decision).isEqualTo(new AddDecision(Action.ADD_TO_PILE, 0));
        IngotPile pile = new IngotPile(PileKind.INGOT, List.of(IRON, IRON, IRON, IRON, COPPER));
        assertThat(pile.add(IRON).count()).isEqualTo(6);
        assertThat(pile.add(IRON).items().get(5)).isEqualTo(IRON);
    }

    @Test
    void addOnFullPileWithAirAbovePlacesOnTop() {
        // InteractionManager.java:458-474: full at the clicked pile, then air above: a new pile at depth 1.
        List<PileCell> above = List.of(AIR);
        AddDecision decision = IngotPileRules.decideAdd(PileKind.INGOT, true, new PileCell.Filled(PileKind.INGOT, 64), above);
        assertThat(decision).isEqualTo(new AddDecision(Action.PLACE_ON_TOP, 1));
    }

    @Test
    void addClimbsFullPilesToTheFirstNonFullPile() {
        // InteractionManager.java:458-466: full, full, then 30: the ingot goes on the pile at depth 2 (count 31 after).
        List<PileCell> above = List.of(new PileCell.Filled(PileKind.INGOT, 64), new PileCell.Filled(PileKind.INGOT, 30));
        AddDecision decision = IngotPileRules.decideAdd(PileKind.INGOT, true, new PileCell.Filled(PileKind.INGOT, 64), above);
        assertThat(decision).isEqualTo(new AddDecision(Action.ADD_TO_PILE, 2));
        IngotPile target = new IngotPile(PileKind.INGOT, java.util.Collections.nCopies(30, IRON));
        assertThat(target.add(IRON).count()).isEqualTo(31);
    }

    @Test
    void addOnFullPileStopsAtAnyOtherBlock() {
        // InteractionManager.java:476-487: a non-air, non-pile block above a full pile fails (the `return InteractionResult.FAIL`).
        List<PileCell> above = List.of(OTHER);
        assertThat(IngotPileRules.decideAdd(PileKind.INGOT, true, new PileCell.Filled(PileKind.INGOT, 64), above))
            .isEqualTo(new AddDecision(Action.FAIL, 0));
        // A pile of the other kind is another block to the rules (Helpers.isBlock compares the exact block).
        List<PileCell> doubleAbove = List.of(new PileCell.Filled(PileKind.DOUBLE_INGOT, 3));
        assertThat(IngotPileRules.decideAdd(PileKind.INGOT, true, new PileCell.Filled(PileKind.INGOT, 64), doubleAbove))
            .isEqualTo(new AddDecision(Action.FAIL, 0));
    }

    @Test
    void addOnFullColumnThatRunsOutFails() {
        // The loop of InteractionManager.java:458-461 reads cells until a non-full pile or air; a column with no cell left fails.
        assertThat(IngotPileRules.decideAdd(PileKind.INGOT, true, new PileCell.Filled(PileKind.INGOT, 64), List.of()))
            .isEqualTo(new AddDecision(Action.FAIL, 0));
    }

    @Test
    void addOnDoublePileUsesThirtySixAsCapacity() {
        // InteractionManager.java:352 and 443: the double ingot pile is full at 36, not 64.
        assertThat(IngotPileRules.decideAdd(PileKind.DOUBLE_INGOT, true, new PileCell.Filled(PileKind.DOUBLE_INGOT, 35), List.of()))
            .isEqualTo(new AddDecision(Action.ADD_TO_PILE, 0));
        assertThat(IngotPileRules.decideAdd(PileKind.DOUBLE_INGOT, true, new PileCell.Filled(PileKind.DOUBLE_INGOT, 36), List.of(AIR)))
            .isEqualTo(new AddDecision(Action.PLACE_ON_TOP, 1));
        assertThat(IngotPileRules.decideAdd(PileKind.DOUBLE_INGOT, true, new PileCell.Filled(PileKind.INGOT, 64), List.of(AIR)))
            .isEqualTo(new AddDecision(Action.PLACE_BY_ITEM, 0));
    }

    @Test
    void addOnOtherBlockPlacesByItem() {
        // InteractionManager.java:489-506: a non-pile clicked block places a new pile with the item.
        assertThat(IngotPileRules.decideAdd(PileKind.INGOT, true, OTHER, List.of()))
            .isEqualTo(new AddDecision(Action.PLACE_BY_ITEM, 0));
    }

    @Test
    void addWithoutSneakOrPermissionPasses() {
        // InteractionManager.java:430-432: the use passes without the sneak and the build permission.
        assertThat(IngotPileRules.decideAdd(PileKind.INGOT, false, new PileCell.Filled(PileKind.INGOT, 5), List.of()))
            .isEqualTo(AddDecision.PASS);
    }

    @Test
    void removeTakesFromTheClickedSingleIngotPileAndRemovesTheBlock() {
        // IngotPileBlock.java:82-109: no pile above, count 1: the block is removed.
        RemoveDecision decision = IngotPileRules.decideRemove(PileKind.INGOT, false, new PileCell.Filled(PileKind.INGOT, 1), List.of());
        assertThat(decision).isEqualTo(new RemoveDecision(RemoveDecision.Action.REMOVE, 0, true));
    }

    @Test
    void removeClimbsSameKindPilesToTheTop() {
        // IngotPileBlock.java:82-86: climb while the cell above is this block. Clicked 5, above 64 then 3: the top (3) loses one.
        List<PileCell> above = List.of(new PileCell.Filled(PileKind.INGOT, 64), new PileCell.Filled(PileKind.INGOT, 3));
        RemoveDecision decision = IngotPileRules.decideRemove(PileKind.INGOT, false, new PileCell.Filled(PileKind.INGOT, 5), above);
        assertThat(decision).isEqualTo(new RemoveDecision(RemoveDecision.Action.REMOVE, 2, false));
    }

    @Test
    void removeStopsTheClimbAtAnyOtherBlock() {
        // Clicked 5, above 2 then other then 9: the climb stops at the other block, so the pile at depth 1 (count 2) is the top.
        List<PileCell> above = List.of(new PileCell.Filled(PileKind.INGOT, 2), OTHER, new PileCell.Filled(PileKind.INGOT, 9));
        assertThat(IngotPileRules.decideRemove(PileKind.INGOT, false, new PileCell.Filled(PileKind.INGOT, 5), above))
            .isEqualTo(new RemoveDecision(RemoveDecision.Action.REMOVE, 1, false));
    }

    @Test
    void removeDoesNotClimbIntoTheOtherKind() {
        // A double pile above is another block: the clicked single pile (count 1) is the top, and its block is removed.
        List<PileCell> above = List.of(new PileCell.Filled(PileKind.DOUBLE_INGOT, 4));
        assertThat(IngotPileRules.decideRemove(PileKind.INGOT, false, new PileCell.Filled(PileKind.INGOT, 1), above))
            .isEqualTo(new RemoveDecision(RemoveDecision.Action.REMOVE, 0, true));
    }

    @Test
    void removeWhileSneakingPassesToTheDefaultUse() {
        // IngotPileBlock.java:112: a sneaking player gets PASS_TO_DEFAULT_BLOCK_INTERACTION.
        assertThat(IngotPileRules.decideRemove(PileKind.INGOT, true, new PileCell.Filled(PileKind.INGOT, 5), List.of()))
            .isEqualTo(new RemoveDecision(RemoveDecision.Action.PASS_TO_DEFAULT, 0, false));
    }

    @Test
    void removeTakesTheLastAddedIngotFirst() {
        // IngotPileBlockEntity.java:57-63: removeIngot takes the last entry; the pick gives the bottom one (line 152).
        IngotPile pile = new IngotPile(PileKind.INGOT, List.of(IRON, COPPER, STONE));
        IngotPile.Removal removal = pile.removeTop();
        assertThat(removal.item()).isEqualTo(STONE);
        assertThat(removal.remaining()).contains(new IngotPile(PileKind.INGOT, List.of(IRON, COPPER)));
        assertThat(pile.pickedItem()).isEqualTo(IRON);
    }

    @Test
    void removeOfTheLastIngotEmptiesThePile() {
        IngotPile.Removal removal = IngotPile.single(PileKind.DOUBLE_INGOT, IRON).removeTop();
        assertThat(removal.item()).isEqualTo(IRON);
        assertThat(removal.remaining()).isEmpty();
    }
}
