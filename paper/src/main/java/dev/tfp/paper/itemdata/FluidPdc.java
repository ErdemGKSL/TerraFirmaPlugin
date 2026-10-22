/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/component/fluid/FluidComponent.java @ c3b4d9536 (CODEC),
 *       src/main/java/net/dries007/tfc/common/items/FluidContainerItem.java @ c3b4d9536 (getMaxStackSize)
 */
package dev.tfp.paper.itemdata;

import dev.tfp.core.vessel.FluidAmount;
import java.util.Objects;
import java.util.Optional;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * The item PDC codec of a fluid container's content (TFC's {@code FluidComponent}, PAR-INFRA-04): buckets, jugs, glass
 * bottles and molds. Not the small vessel: TFC keeps its molten metal as a {@code FluidAlloy} inside
 * {@code VesselComponent} (several metals at once), which one fluid id plus amount cannot hold; it needs its own codec
 * over {@code core/alloy/FluidAlloy}. Stored under {@value #KEY} as a nested container:
 * <pre>
 * tfp:fluid = { tfp:v: int (version), tfp:id: string (fluid id), tfp:amount: int (mB),
 *               tfp:empty_stack: int (optional; the item's own max stack size override from before it was filled) }
 * </pre>
 * An empty content is stored as no entry. Other PDC entries and the rest of the item meta are left untouched, except the
 * max stack size: TFC never stacks a filled fluid item ({@code FluidContainerItem}/{@code MoldItem.getMaxStackSize}
 * return 1 when the item holds fluid), so {@link #write(ItemStack, FluidAmount)} sets the max stack size to 1 when it
 * stores a fluid and restores the item's own size (its earlier override, or the prototype's) when it drains to empty.
 *
 * <p>Threads: an ItemStack is plain data; call this on the thread that owns the stack.
 *
 * <h2>Version history</h2>
 * <ol>
 *   <li>id, amount, optional empty_stack (PAR-INFRA-04)</li>
 * </ol>
 */
public final class FluidPdc {
    public static final String KEY = "tfp:fluid";
    public static final int VERSION = 1;

    private static final NamespacedKey ROOT = Objects.requireNonNull(NamespacedKey.fromString(KEY));
    private static final NamespacedKey V = new NamespacedKey("tfp", "v");
    private static final NamespacedKey ID = new NamespacedKey("tfp", "id");
    private static final NamespacedKey AMOUNT = new NamespacedKey("tfp", "amount");
    private static final NamespacedKey EMPTY_STACK = new NamespacedKey("tfp", "empty_stack");

    private FluidPdc() {
    }

    /** The stack's fluid, or empty when it has none (no entry, malformed or a newer version). */
    public static Optional<FluidAmount> read(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return Optional.empty();
        }
        return read(stack.getItemMeta().getPersistentDataContainer());
    }

    /** Reads the entry from a container. Absent, malformed or newer-version entries read as empty. */
    public static Optional<FluidAmount> read(PersistentDataContainer pdc) {
        PersistentDataContainer fluid = pdc.get(ROOT, PersistentDataType.TAG_CONTAINER);
        if (fluid == null) {
            return Optional.empty();
        }
        Integer v = fluid.get(V, PersistentDataType.INTEGER);
        String id = fluid.get(ID, PersistentDataType.STRING);
        Integer amount = fluid.get(AMOUNT, PersistentDataType.INTEGER);
        if (v == null || v > VERSION || id == null || amount == null || amount <= 0 || id.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new FluidAmount(id, amount));
    }

    /** The stack's fluid, {@link FluidAmount#EMPTY} when it has none. */
    public static FluidAmount content(ItemStack stack) {
        return read(stack).orElse(FluidAmount.EMPTY);
    }

    /**
     * Writes {@code content} into the stack's PDC ({@code null} or empty removes the entry) and sets the max stack size:
     * 1 while the stack holds fluid, the item's own size again once it is empty. Returns {@code false} and leaves the
     * stack unchanged when it has no item meta (air) or holds an entry of a newer version. Writes the whole stack; fill
     * or drain one item at a time ({@link ItemFluids} refuses stacks of more than one).
     */
    public static boolean write(ItemStack stack, FluidAmount content) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return false;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (isNewer(pdc)) {
            return false;
        }
        PersistentDataContainer existing = pdc.get(ROOT, PersistentDataType.TAG_CONTAINER);
        Integer ownSize = existing != null ? existing.get(EMPTY_STACK, PersistentDataType.INTEGER)
                : meta.hasMaxStackSize() ? Integer.valueOf(meta.getMaxStackSize()) : null;
        if (content == null || content.isEmpty()) {
            pdc.remove(ROOT);
            if (existing != null) {
                meta.setMaxStackSize(ownSize);
            }
        } else {
            set(pdc, content, ownSize);
            meta.setMaxStackSize(1);
        }
        stack.setItemMeta(meta);
        return true;
    }

    /**
     * As {@link #write(ItemStack, FluidAmount)} on a bare container: the entry only (an earlier remembered stack size is
     * kept); the max stack size is the caller's.
     */
    public static boolean write(PersistentDataContainer pdc, FluidAmount content) {
        if (isNewer(pdc)) {
            return false;
        }
        if (content == null || content.isEmpty()) {
            pdc.remove(ROOT);
            return true;
        }
        PersistentDataContainer existing = pdc.get(ROOT, PersistentDataType.TAG_CONTAINER);
        set(pdc, content, existing == null ? null : existing.get(EMPTY_STACK, PersistentDataType.INTEGER));
        return true;
    }

    private static void set(PersistentDataContainer pdc, FluidAmount content, Integer ownSize) {
        PersistentDataContainer fluid = pdc.getAdapterContext().newPersistentDataContainer();
        fluid.set(V, PersistentDataType.INTEGER, VERSION);
        fluid.set(ID, PersistentDataType.STRING, content.fluid());
        fluid.set(AMOUNT, PersistentDataType.INTEGER, content.amount());
        if (ownSize != null) {
            fluid.set(EMPTY_STACK, PersistentDataType.INTEGER, ownSize);
        }
        pdc.set(ROOT, PersistentDataType.TAG_CONTAINER, fluid);
    }

    /** Whether the stack carries a {@value #KEY} entry of any version. */
    public static boolean has(ItemStack stack) {
        return stack != null && stack.hasItemMeta()
                && stack.getItemMeta().getPersistentDataContainer().has(ROOT, PersistentDataType.TAG_CONTAINER);
    }

    /** Whether the stack carries a {@value #KEY} entry of a version newer than this plugin knows (never overwritten). */
    public static boolean isNewer(ItemStack stack) {
        return stack != null && stack.hasItemMeta() && isNewer(stack.getItemMeta().getPersistentDataContainer());
    }

    static boolean isNewer(PersistentDataContainer pdc) {
        PersistentDataContainer existing = pdc.get(ROOT, PersistentDataType.TAG_CONTAINER);
        if (existing == null) {
            return false;
        }
        Integer v = existing.get(V, PersistentDataType.INTEGER);
        return v != null && v > VERSION;
    }
}
