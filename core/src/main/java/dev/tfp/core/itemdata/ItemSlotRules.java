/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/component/item/ItemComponent.java @ c3b4d9536 (insert, extract),
 *       src/main/java/net/dries007/tfc/common/component/item/ItemContainer.java @ c3b4d9536 (slot limit, validity)
 */
package dev.tfp.core.itemdata;

/**
 * The insert and extract of one slot of an item container (a small vessel's slots, a mold's contents), as TFC's
 * {@code ItemComponent} does them, on counts. The caller supplies the facts it reads from the stacks: whether the slot
 * content and the inserted stack are the same item with the same data, the item's max stack size, the container's slot
 * capacity and whether the container may hold the item. Immutable results, any thread.
 *
 * <p>TFC's rules:
 * <ul>
 *   <li>Inserting nothing, or an item the container may not hold, changes nothing; all of it remains.</li>
 *   <li>A non-empty slot holding a different item (or the same item with other data) takes nothing.</li>
 *   <li>Otherwise the slot takes up to {@code min(maxStackSize, slotCapacity)}; the rest remains.</li>
 *   <li>Extracting from an empty slot extracts nothing; otherwise it extracts {@code min(count, amount)}.</li>
 * </ul>
 */
public final class ItemSlotRules {

    private ItemSlotRules() {
    }

    /**
     * The result of an insert.
     *
     * @param contentCount   the count in the slot after the insert; the slot holds the inserted item when it changed
     * @param remainderCount the count left over from the inserted stack
     */
    public record Insert(int contentCount, int remainderCount) {
        /** @return how many items moved into the slot */
        public int inserted(int stackCount) {
            return stackCount - remainderCount;
        }
    }

    /**
     * The result of an extract.
     *
     * @param contentCount   the count left in the slot ({@code 0} empties it)
     * @param extractedCount the count taken out
     */
    public record Extract(int contentCount, int extractedCount) {
    }

    /**
     * Inserts a stack into a slot.
     *
     * @param contentCount the count in the slot, {@code 0} for an empty slot
     * @param stackCount   the count of the inserted stack, {@code 0} for an empty stack
     * @param sameItem     whether the slot content and the stack are the same item with the same data (TFC
     *                     {@code ItemStack.isSameItemSameComponents}); ignored when the slot is empty
     * @param canContain   whether the container may hold the item (TFC {@code canContainItem})
     * @param maxStackSize the item's max stack size, {@code >= 1}
     * @param slotCapacity the container's slot capacity, {@code >= 0}
     */
    public static Insert insert(int contentCount, int stackCount, boolean sameItem, boolean canContain,
                                int maxStackSize, int slotCapacity) {
        if (contentCount < 0 || stackCount < 0 || maxStackSize < 1 || slotCapacity < 0) {
            throw new IllegalArgumentException("negative count or capacity");
        }
        if (stackCount == 0 || !canContain) {
            return new Insert(contentCount, stackCount);
        }
        if (contentCount > 0 && !sameItem) {
            return new Insert(contentCount, stackCount);
        }
        final int capacity = Math.min(maxStackSize, slotCapacity);
        final long total = (long) contentCount + stackCount;
        final int content = (int) Math.min(capacity, total);
        final int remainder = total > capacity ? (int) (total - capacity) : 0;
        return new Insert(content, remainder);
    }

    /**
     * Extracts up to {@code amount} from a slot.
     *
     * @param contentCount the count in the slot
     * @param amount       the most to extract, {@code >= 0}
     */
    public static Extract extract(int contentCount, int amount) {
        if (contentCount < 0 || amount < 0) {
            throw new IllegalArgumentException("negative count or amount");
        }
        if (contentCount == 0) {
            return new Extract(0, 0);
        }
        final int remainder = contentCount > amount ? contentCount - amount : 0;
        final int extracted = contentCount >= amount ? amount : contentCount;
        return new Extract(remainder, extracted);
    }
}
