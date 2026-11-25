/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/component/Bait.java @ c3b4d9536
 */
package dev.tfp.paper.fishing;

import java.util.Optional;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * The bait a rod holds (TFC's {@code Bait} component), as a nested PDC container {@value #KEY}:
 * <pre>
 * tfp:bait = { tfp:v: int (1), tfp:id: string (item id of the bait: the CraftEngine id or the vanilla key) }
 * </pre>
 * Only the item id is kept: the bait's tag decides the hook's tier and the stack's own data never matters once it is on a rod.
 * Call on the thread that owns the stack.
 */
public final class RodBait {
    public static final String KEY = "tfp:bait";
    public static final int VERSION = 1;

    private static final NamespacedKey ROOT = new NamespacedKey("tfp", "bait");
    private static final NamespacedKey V = new NamespacedKey("tfp", "v");
    private static final NamespacedKey ID = new NamespacedKey("tfp", "id");

    private RodBait() {
    }

    /** The item id of the bait on {@code rod}; empty when none, malformed or of a newer version. */
    public static Optional<String> get(ItemStack rod) {
        if (rod == null || !rod.hasItemMeta()) {
            return Optional.empty();
        }
        PersistentDataContainer bait = rod.getItemMeta().getPersistentDataContainer().get(ROOT, PersistentDataType.TAG_CONTAINER);
        if (bait == null) {
            return Optional.empty();
        }
        Integer v = bait.get(V, PersistentDataType.INTEGER);
        String id = bait.get(ID, PersistentDataType.STRING);
        return v == null || v > VERSION || id == null || id.isEmpty() ? Optional.empty() : Optional.of(id);
    }

    /** Puts {@code baitId} on the rod, replacing what it held (TFC {@code Bait.setBait}). */
    public static void set(ItemStack rod, String baitId) {
        ItemMeta meta = rod.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        PersistentDataContainer bait = pdc.getAdapterContext().newPersistentDataContainer();
        bait.set(V, PersistentDataType.INTEGER, VERSION);
        bait.set(ID, PersistentDataType.STRING, baitId);
        pdc.set(ROOT, PersistentDataType.TAG_CONTAINER, bait);
        rod.setItemMeta(meta);
    }

    /** Removes the bait (TFC {@code use.remove(TFCComponents.BAIT)}). */
    public static void clear(ItemStack rod) {
        if (rod == null || !rod.hasItemMeta()) {
            return;
        }
        ItemMeta meta = rod.getItemMeta();
        meta.getPersistentDataContainer().remove(ROOT);
        rod.setItemMeta(meta);
    }
}
