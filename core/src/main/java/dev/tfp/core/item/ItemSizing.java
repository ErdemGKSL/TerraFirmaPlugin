/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/component/size/ItemSizeManager.java @ c3b4d9536 (default size classes)
 */
package dev.tfp.core.item;

/**
 * An item's size and weight together (TFC {@code IItemSize}). The Paper layer resolves the item to a {@link ItemClass}
 * (or to an explicit definition) and then asks this class for the values.
 *
 * @param size the size, which containers accept the item
 * @param weight the weight, which sets the stack size
 */
public record ItemSizing(ItemSize size, ItemWeight weight) {
    /** TFC {@code TOOL_SIZE}: stored only in chests, weight medium (stack size 16). TFC's comment says tools should stack to 1, but the code does not enforce it. */
    public static final ItemSizing TOOL = new ItemSizing(ItemSize.LARGE, ItemWeight.MEDIUM);
    /** TFC {@code ARMOR_SIZE}: stored only in chests, stack size 1. */
    public static final ItemSizing ARMOR = new ItemSizing(ItemSize.LARGE, ItemWeight.VERY_HEAVY);
    /** TFC {@code BLOCK_SIZE}: fits small vessels, stack size 32. */
    public static final ItemSizing BLOCK = new ItemSizing(ItemSize.SMALL, ItemWeight.LIGHT);
    /** TFC {@code DEFAULT_SIZE}: stored anywhere, stack size 64. */
    public static final ItemSizing DEFAULT = new ItemSizing(ItemSize.VERY_SMALL, ItemWeight.VERY_LIGHT);

    /**
     * The default size classes of TFC's {@code ItemSizeManager.getDefinition}, used when no {@code item_size} data
     * matches the item. The check order in TFC is tiered item, then armour, then block item, then anything else.
     */
    public enum ItemClass {
        /** {@code TieredItem} or {@code BucketItem}. */
        TOOL,
        /** {@code ArmorItem} or {@code AnimalArmorItem}. */
        ARMOR,
        /** {@code BlockItem}. */
        BLOCK,
        /** Anything else. */
        OTHER;

        /** The default sizing for this class. */
        public ItemSizing defaultSizing() {
            return switch (this) {
                case TOOL -> ItemSizing.TOOL;
                case ARMOR -> ItemSizing.ARMOR;
                case BLOCK -> ItemSizing.BLOCK;
                case OTHER -> ItemSizing.DEFAULT;
            };
        }
    }

    /** TFC {@code IItemSize.modifyWeight}: the stack size an item of this weight gets. */
    public int maxStackSize() {
        return weight.stackSize();
    }
}
