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

import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Invariants of the ingot pile rules and the pile value: bounds, the add and remove round trip, and the decisions over every
 * count. The TFC lines are cited in {@link IngotPileGoldenTest}.
 */
class IngotPileRulesTest {

    private static final String IRON = "tfc:metal/wrought_iron_ingot";

    @Test
    void pileRejectsEmptyAndOverfullContents() {
        // IngotPileBlockEntity.java: entries are one per ingot, and the state count is 1..64 (single) or 1..36 (double).
        assertThatThrownBy(() -> new IngotPile(PileKind.INGOT, List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new IngotPile(PileKind.DOUBLE_INGOT, Collections.nCopies(37, IRON)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new IngotPile(PileKind.INGOT, List.of("")))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void addOnFullPileThrows() {
        IngotPile full = new IngotPile(PileKind.DOUBLE_INGOT, Collections.nCopies(36, IRON));
        assertThat(full.full()).isTrue();
        assertThatThrownBy(() -> full.add(IRON)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void addThenRemoveRestoresThePile() {
        IngotPile pile = IngotPile.single(PileKind.INGOT, IRON);
        for (int count = 2; count <= PileKind.INGOT.capacity(); count++) {
            pile = pile.add("tfc:metal/copper_ingot");
        }
        assertThat(pile.count()).isEqualTo(64);
        for (int count = 63; count >= 1; count--) {
            IngotPile.Removal removal = pile.removeTop();
            assertThat(removal.item()).isEqualTo("tfc:metal/copper_ingot");
            pile = removal.remaining().orElseThrow();
            assertThat(pile.count()).isEqualTo(count);
        }
        assertThat(pile.items()).containsExactly(IRON);
    }

    @Test
    void everyCountHasAConsistentShapeAndLayer() {
        for (PileKind kind : PileKind.values()) {
            for (int count = 1; count <= kind.capacity(); count++) {
                int shape = kind.shapeIndex(count);
                assertThat(shape).isBetween(0, kind.layers() - 1);
                assertThat(kind.layerOf(count - 1)).isEqualTo(shape + 1);
                assertThat(kind.topSixteenths(count)).isGreaterThan(0.0).isLessThanOrEqualTo(16.0);
            }
        }
    }

    @Test
    void addDecisionAlwaysNamesAnInRangeCellOrAGuardedFailure() {
        // Over every full clicked count, a column of full piles then air: the placed cell is directly above the column.
        for (int height = 1; height <= 5; height++) {
            List<PileCell> above = new java.util.ArrayList<>();
            for (int i = 0; i < height; i++) {
                above.add(new PileCell.Filled(PileKind.INGOT, 64));
            }
            above.add(AIR);
            AddDecision decision = IngotPileRules.decideAdd(PileKind.INGOT, true, new PileCell.Filled(PileKind.INGOT, 64), above);
            assertThat(decision).isEqualTo(new AddDecision(AddDecision.Action.PLACE_ON_TOP, height + 1));
        }
        assertThat(IngotPileRules.decideAdd(PileKind.INGOT, true, OTHER, List.of(AIR)))
            .isEqualTo(new AddDecision(AddDecision.Action.PLACE_BY_ITEM, 0));
    }

    @Test
    void removeRequiresAPileOfTheKind() {
        assertThatThrownBy(() -> IngotPileRules.decideRemove(PileKind.INGOT, false, OTHER, List.of()))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> IngotPileRules.decideRemove(PileKind.INGOT, false, new PileCell.Filled(PileKind.DOUBLE_INGOT, 2), List.of()))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cellsRejectCountsOutsideTheirKind() {
        assertThatThrownBy(() -> new PileCell.Filled(PileKind.DOUBLE_INGOT, 37)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PileCell.Filled(PileKind.INGOT, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void asCellReflectsTheCount() {
        assertThat(IngotPile.single(PileKind.DOUBLE_INGOT, IRON).asCell()).isEqualTo(new PileCell.Filled(PileKind.DOUBLE_INGOT, 1));
    }
}
