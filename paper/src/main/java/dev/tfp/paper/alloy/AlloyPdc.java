/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/FluidAlloy.java @ c3b4d9536 (stored fluid map)
 */
package dev.tfp.paper.alloy;

import dev.tfp.core.alloy.AlloyCodec;
import dev.tfp.core.alloy.FluidAlloy;
import java.util.List;
import java.util.Objects;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * A mixed alloy ({@link FluidAlloy}) on an item, under {@value #KEY} as a nested container: {@code tfp:v} (version),
 * {@code tfp:amount} (the total, mB) and {@code tfp:s} (the {@link AlloyCodec} lines, one per metal). Used by the vessel and
 * crucible glue; read on Netty threads by {@link AlloyTooltipContributor} over the packet copy of the stack.
 */
public final class AlloyPdc {
    public static final String KEY = "tfp:alloy";
    public static final int VERSION = 1;

    private static final NamespacedKey ROOT = Objects.requireNonNull(NamespacedKey.fromString(KEY));
    private static final NamespacedKey V = new NamespacedKey("tfp", "v");
    private static final NamespacedKey AMOUNT = new NamespacedKey("tfp", "amount");
    private static final NamespacedKey LINES = new NamespacedKey("tfp", "s");

    private AlloyPdc() {
    }

    /** The stack's alloy; the empty alloy when it has none, or one of a newer version. */
    public static FluidAlloy read(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return FluidAlloy.empty();
        }
        PersistentDataContainer root = stack.getItemMeta().getPersistentDataContainer().get(ROOT, PersistentDataType.TAG_CONTAINER);
        if (root == null) {
            return FluidAlloy.empty();
        }
        Integer v = root.get(V, PersistentDataType.INTEGER);
        Integer amount = root.get(AMOUNT, PersistentDataType.INTEGER);
        List<String> lines = root.get(LINES, PersistentDataType.LIST.strings());
        if (v == null || v > VERSION || amount == null || lines == null) {
            return FluidAlloy.empty();
        }
        return AlloyCodec.decode(amount, lines);
    }

    /** Writes the alloy ({@code null} or empty removes the entry). Returns {@code false} for a stack without item meta. */
    public static boolean write(ItemStack stack, FluidAlloy alloy) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return false;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (alloy == null || alloy.isEmpty()) {
            pdc.remove(ROOT);
        } else {
            PersistentDataContainer root = pdc.getAdapterContext().newPersistentDataContainer();
            root.set(V, PersistentDataType.INTEGER, VERSION);
            root.set(AMOUNT, PersistentDataType.INTEGER, alloy.amount());
            root.set(LINES, PersistentDataType.LIST.strings(), AlloyCodec.encode(alloy));
            pdc.set(ROOT, PersistentDataType.TAG_CONTAINER, root);
        }
        stack.setItemMeta(meta);
        return true;
    }
}
