/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.itemdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.tfp.core.vessel.FluidAmount;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;

/**
 * PAR-INFRA-04: hand-traced rows of TFC's {@code ItemComponent.insert/extract} and the component equality that decides
 * stack merges (src/main/java/net/dries007/tfc/common/component/** @ c3b4d9536).
 */
class ItemDataRulesTest {

    @Test
    void insertGoldenRows() {
        // contentCount, stackCount, same, canContain, maxStack, slotCap -> content, remainder
        int[][] rows = {
            {0, 10, 0, 1, 64, 16, 10, 0},   // empty slot takes all under the slot capacity
            {0, 40, 0, 1, 64, 16, 16, 24},  // empty slot capped by slot capacity
            {0, 40, 0, 1, 1, 16, 1, 39},    // capped by the item's max stack size
            {10, 10, 1, 1, 64, 16, 16, 4},  // same item tops up
            {10, 10, 0, 1, 64, 16, 10, 10}, // different item: nothing moves
            {0, 10, 1, 0, 64, 16, 0, 10},   // container may not hold it
            {5, 0, 1, 1, 64, 16, 5, 0},     // empty insert
            {20, 3, 1, 1, 64, 16, 16, 7},   // over capacity (config lowered): content shrinks to the capacity like TFC
        };
        for (int[] r : rows) {
            ItemSlotRules.Insert in = ItemSlotRules.insert(r[0], r[1], r[2] == 1, r[3] == 1, r[4], r[5]);
            assertThat(new int[] {in.contentCount(), in.remainderCount()}).as(Arrays.toString(r)).containsExactly(r[6], r[7]);
        }
        assertThat(ItemSlotRules.insert(10, 10, true, true, 64, 16).inserted(10)).isEqualTo(6);
        assertThatThrownBy(() -> ItemSlotRules.insert(-1, 1, true, true, 64, 16)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void extractGoldenRows() {
        assertThat(ItemSlotRules.extract(0, 5)).isEqualTo(new ItemSlotRules.Extract(0, 0));
        assertThat(ItemSlotRules.extract(10, 4)).isEqualTo(new ItemSlotRules.Extract(6, 4));
        assertThat(ItemSlotRules.extract(4, 4)).isEqualTo(new ItemSlotRules.Extract(0, 4));
        assertThat(ItemSlotRules.extract(3, 10)).isEqualTo(new ItemSlotRules.Extract(0, 3));
    }

    /** No merge when fluid contents differ: TFC compares the fluid component with {@code FluidStack.matches}. */
    @Test
    void fluidItemsMergeOnlyWithEqualContents() {
        FluidAmount water500 = new FluidAmount("minecraft:water", 500);
        assertThat(ItemDataMatching.fluidsMatch(water500, new FluidAmount("minecraft:water", 500))).isTrue();
        assertThat(ItemDataMatching.fluidsMatch(water500, new FluidAmount("minecraft:water", 499))).isFalse();
        assertThat(ItemDataMatching.fluidsMatch(water500, new FluidAmount("tfc:salt_water", 500))).isFalse();
        assertThat(ItemDataMatching.fluidsMatch(water500, null)).isFalse();
        assertThat(ItemDataMatching.fluidsMatch(null, FluidAmount.EMPTY)).isTrue();
        assertThat(ItemDataMatching.fluidsMatch(new FluidAmount("a", 0), new FluidAmount("b", 0))).isTrue();
    }

    /** TFC: a filled fluid item has a max stack size of 1, so only empty ones share a stack. */
    @Test
    void filledFluidItemsNeverMerge() {
        FluidAmount water = new FluidAmount("minecraft:water", 1000);
        assertThat(ItemDataMatching.fluidsAllowMerge(water, new FluidAmount("minecraft:water", 1000))).isFalse();
        assertThat(ItemDataMatching.fluidsAllowMerge(water, FluidAmount.EMPTY)).isFalse();
        assertThat(ItemDataMatching.fluidsAllowMerge(null, water)).isFalse();
        assertThat(ItemDataMatching.fluidsAllowMerge(null, FluidAmount.EMPTY)).isTrue();
    }

    @Test
    void itemListsMatchSlotBySlot() {
        List<String> a = Arrays.asList("x", null, "y");
        assertThat(ItemDataMatching.listsMatch(a, Arrays.asList("x", null, "y"), Objects::equals)).isTrue();
        assertThat(ItemDataMatching.listsMatch(a, Arrays.asList("x", "y", null), Objects::equals)).isFalse();
        assertThat(ItemDataMatching.listsMatch(a, Arrays.asList("x", null), Objects::equals)).isFalse();
        assertThat(ItemDataMatching.listsMatch(null, List.of(), Objects::equals)).isTrue();
    }

    @Test
    void limits() {
        assertThat(ItemListLimits.withinBounds(4, 1000)).isTrue();
        assertThat(ItemListLimits.withinBounds(ItemListLimits.MAX_SLOTS + 1, 0)).isFalse();
        assertThat(ItemListLimits.withinBounds(1, ItemListLimits.MAX_TOTAL_BYTES + 1L)).isFalse();
    }
}
