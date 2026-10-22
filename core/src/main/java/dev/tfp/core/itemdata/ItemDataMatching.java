/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/component/fluid/FluidComponent.java @ c3b4d9536 (equals),
 *       src/main/java/net/dries007/tfc/common/component/item/ItemListComponent.java @ c3b4d9536 (equals),
 *       src/main/java/net/dries007/tfc/common/component/item/ItemComponent.java @ c3b4d9536 (equals of lists),
 *       src/main/java/net/dries007/tfc/common/items/FluidContainerItem.java @ c3b4d9536 (getMaxStackSize),
 *       src/main/java/net/dries007/tfc/common/items/MoldItem.java @ c3b4d9536 (getMaxStackSize)
 */
package dev.tfp.core.itemdata;

import dev.tfp.core.vessel.FluidAmount;
import java.util.List;
import java.util.function.BiPredicate;

/**
 * The stack-merge rules of the fluid and item-list item data. In TFC both are data components, and vanilla merges two
 * stacks only when every component is equal. On top of that TFC never stacks a filled fluid item:
 * {@code FluidContainerItem.getMaxStackSize} (buckets, bottles) and {@code MoldItem.getMaxStackSize} return 1 whenever
 * the item holds fluid, the jug is {@code stacksTo(1)} and {@code VesselItem.getMaxStackSize} is always 1. So empty fluid
 * items stack, filled ones never do ({@link #fluidsAllowMerge}); item lists ({@code tfc:contents}, on the sealed large
 * vessel's and the powderkeg's block items) stack only when equal slot by slot ({@link #listsMatch}). Any thread.
 */
public final class ItemDataMatching {

    private ItemDataMatching() {
    }

    /**
     * TFC {@code FluidComponent.equals} ({@code FluidStack.matches}): same fluid and same amount; every empty content is
     * equal. A missing component reads as empty.
     */
    public static boolean fluidsMatch(FluidAmount a, FluidAmount b) {
        FluidAmount x = a == null ? FluidAmount.EMPTY : a;
        FluidAmount y = b == null ? FluidAmount.EMPTY : b;
        return x.equals(y);
    }

    /**
     * Whether two fluid items may share a stack by their fluid: only when both are empty (TFC returns a max stack size of
     * 1 for every filled fluid item, so two equal filled buckets still never stack). A missing component reads as empty.
     */
    public static boolean fluidsAllowMerge(FluidAmount a, FluidAmount b) {
        return (a == null || a.isEmpty()) && (b == null || b.isEmpty());
    }

    /**
     * TFC {@code ItemListComponent.equals} ({@code ItemStack.listMatches}): the same number of slots, and each pair of slots
     * matches by {@code matches} (TFC {@code ItemStack.matches}: same item, data and count; two empty slots match). A
     * missing component reads as the empty list, which differs from a list of empty slots of another size.
     */
    public static <T> boolean listsMatch(List<T> a, List<T> b, BiPredicate<? super T, ? super T> matches) {
        List<T> x = a == null ? List.of() : a;
        List<T> y = b == null ? List.of() : b;
        if (x.size() != y.size()) {
            return false;
        }
        for (int i = 0; i < x.size(); i++) {
            if (!matches.test(x.get(i), y.get(i))) {
                return false;
            }
        }
        return true;
    }
}
