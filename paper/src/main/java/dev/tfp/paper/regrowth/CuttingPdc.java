/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/items/FlowerCuttingItem.java @ c3b4d9536 (of: the plant component)
 */
package dev.tfp.paper.regrowth;

import java.util.Optional;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * The plant a flower cutting carries (TFC's {@code PLANT} component), in the item's persistent data as the record the tooltip layer reads:
 * {@code tfp:plant = { tfp:v: int 1, tfp:item: string (the plant's item id) }}.
 */
public final class CuttingPdc {
    public static final NamespacedKey PLANT = new NamespacedKey("tfp", "plant");
    static final NamespacedKey V = new NamespacedKey("tfp", "v");
    static final NamespacedKey ITEM = new NamespacedKey("tfp", "item");
    static final int VERSION = 1;

    private CuttingPdc() {
    }

    /** Stores {@code plantItemId} on {@code stack}; false when the stack has no item meta. */
    public static boolean write(ItemStack stack, String plantItemId) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return false;
        }
        PersistentDataContainer record = meta.getPersistentDataContainer().getAdapterContext().newPersistentDataContainer();
        record.set(V, PersistentDataType.INTEGER, VERSION);
        record.set(ITEM, PersistentDataType.STRING, plantItemId);
        meta.getPersistentDataContainer().set(PLANT, PersistentDataType.TAG_CONTAINER, record);
        stack.setItemMeta(meta);
        return true;
    }

    /** The plant item id on {@code stack}, empty when there is none (TFC's {@code ItemComponent.EMPTY}). */
    public static Optional<String> read(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return Optional.empty();
        }
        PersistentDataContainer record = stack.getItemMeta().getPersistentDataContainer().get(PLANT, PersistentDataType.TAG_CONTAINER);
        if (record == null) {
            return Optional.empty();
        }
        Integer v = record.get(V, PersistentDataType.INTEGER);
        String item = record.get(ITEM, PersistentDataType.STRING);
        return v == null || v > VERSION || item == null || item.isEmpty() ? Optional.empty() : Optional.of(item);
    }
}
