/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/component/item/ItemContainer.java @ c3b4d9536 (slots, slot limit, validity),
 *       src/main/java/net/dries007/tfc/common/component/item/ItemComponent.java @ c3b4d9536 (insert, extract)
 */
package dev.tfp.paper.itemdata;

import dev.tfp.core.itemdata.ItemDataMatching;
import dev.tfp.core.itemdata.ItemSlotRules;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import org.bukkit.inventory.ItemStack;

/**
 * The slots of an item that carries items (TFC {@code tfc:contents}: the sealed large vessel and the powderkeg, and their
 * block items), backed by {@link ContentsPdc} and TFC's slot rules
 * in {@link ItemSlotRules}. The caller supplies the container's slot count, slot capacity and {@code canContain}
 * predicate (TFC {@code ItemContainerInfo}). A stack whose entry has a newer version is never changed.
 *
 * <p>Threads: call on the thread that owns the stack.
 */
public final class ItemContents {

    private ItemContents() {
    }

    /** The container's slots, padded or cut to {@code slots}, with {@link ItemStack#empty()} for empty slots. */
    public static List<ItemStack> slots(ItemStack container, int slots) {
        List<ItemStack> stored = ContentsPdc.contents(container);
        List<ItemStack> out = new ArrayList<>(slots);
        for (int i = 0; i < slots; i++) {
            out.add(i < stored.size() ? stored.get(i).clone() : ItemStack.empty());
        }
        return out;
    }

    /**
     * Inserts {@code stack} into slot {@code slot} and writes the result.
     *
     * @return the remainder of {@code stack} (a new stack; {@code stack} itself is not modified)
     */
    public static ItemStack insert(ItemStack container, int slots, int slot, ItemStack stack, int slotCapacity,
                                   Predicate<ItemStack> canContain) {
        if (stack == null || ContentsPdc.isEmpty(stack)) {
            return ItemStack.empty();
        }
        if (ContentsPdc.isNewer(container)) {
            return stack.clone();
        }
        List<ItemStack> list = slots(container, slots);
        ItemStack inSlot = list.get(slot);
        boolean slotEmpty = ContentsPdc.isEmpty(inSlot);
        // Bukkit isSimilar is TFC's ItemStack.isSameItemSameComponents on Paper: same item and same components, count
        // ignored (CraftItemStack compares the item and the component patch).
        ItemSlotRules.Insert in = ItemSlotRules.insert(slotEmpty ? 0 : inSlot.getAmount(), stack.getAmount(),
                !slotEmpty && inSlot.isSimilar(stack), canContain.test(stack), stack.getMaxStackSize(), slotCapacity);
        if (in.remainderCount() == stack.getAmount()) {
            return stack.clone();
        }
        list.set(slot, in.contentCount() == 0 ? ItemStack.empty() : stack.asQuantity(in.contentCount()));
        if (!ContentsPdc.write(container, list)) {
            return stack.clone();
        }
        return in.remainderCount() == 0 ? ItemStack.empty() : stack.asQuantity(in.remainderCount());
    }

    /**
     * Extracts up to {@code amount} from slot {@code slot} and writes the result.
     *
     * @return the extracted stack, {@link ItemStack#empty()} when nothing
     */
    public static ItemStack extract(ItemStack container, int slots, int slot, int amount) {
        if (ContentsPdc.isNewer(container)) {
            return ItemStack.empty();
        }
        List<ItemStack> list = slots(container, slots);
        ItemStack inSlot = list.get(slot);
        if (ContentsPdc.isEmpty(inSlot)) {
            return ItemStack.empty();
        }
        ItemSlotRules.Extract ex = ItemSlotRules.extract(inSlot.getAmount(), amount);
        if (ex.extractedCount() == 0) {
            return ItemStack.empty();
        }
        list.set(slot, ex.contentCount() == 0 ? ItemStack.empty() : inSlot.asQuantity(ex.contentCount()));
        if (!ContentsPdc.write(container, list)) {
            return ItemStack.empty();
        }
        return inSlot.asQuantity(ex.extractedCount());
    }

    /** TFC {@code ItemStack.matches}: both empty, or the same item, data and count. */
    public static boolean stacksMatch(ItemStack a, ItemStack b) {
        boolean ea = ContentsPdc.isEmpty(a);
        boolean eb = ContentsPdc.isEmpty(b);
        if (ea || eb) {
            return ea && eb;
        }
        // Count + isSimilar is TFC's ItemStack.matches on Paper (isSimilar compares the item and the components).
        return a.getAmount() == b.getAmount() && a.isSimilar(b);
    }

    /** Whether two stacks' item lists allow a merge (slot by slot, see {@link ItemDataMatching}). */
    public static boolean contentsMatch(ItemStack a, ItemStack b) {
        return ItemDataMatching.listsMatch(ContentsPdc.contents(a), ContentsPdc.contents(b), ItemContents::stacksMatch);
    }

    /**
     * Whether two fluid or item carrying stacks may merge by their item data: neither holds fluid (TFC never stacks a
     * filled fluid item) and their item lists are equal (TFC: vanilla merges only on equal components). Other item data,
     * and items that never stack at all (the small vessel, the jug), are the caller's (or the item definition's) check.
     */
    public static boolean canMerge(ItemStack a, ItemStack b) {
        return ItemFluids.fluidsAllowMerge(a, b) && contentsMatch(a, b);
    }
}
