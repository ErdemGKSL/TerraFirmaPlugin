/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/component/item/ItemListComponent.java @ c3b4d9536 (CODEC)
 */
package dev.tfp.paper.itemdata;

import dev.tfp.core.itemdata.ItemListLimits;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * The item PDC codec of an item list (TFC's {@code ItemListComponent}, {@code tfc:contents}, PAR-INFRA-04): the slots
 * of the sealed large vessel and the powderkeg kept on their block items. (The small vessel's slots are TFC
 * {@code VesselComponent}, not this list.) Stored under {@value #KEY} as a nested container:
 * <pre>
 * tfp:contents = { tfp:v: int (version), tfp:items: [byte[] per slot: ItemStack.serializeAsBytes, empty array = empty slot] }
 * </pre>
 * Each slot keeps the whole stack, including its own PDC, so a vessel's food keeps its creation date ({@code tfp:food})
 * and a heated item its temperature record ({@code tfp:heat}): read a slot and pass it to {@code FoodPdc}/{@code HeatPdc}.
 * The list is bounded by {@link ItemListLimits}; a stored list over the bounds reads as empty and is never written.
 *
 * <p>Slots are returned as copies ({@code List<ItemStack>} with {@code null}-free {@link Material#AIR} stacks for empty
 * slots); changing them does not change the item until {@link #write} is called.
 *
 * <p>Threads: an ItemStack is plain data; call this on the thread that owns the stack.
 *
 * <h2>Version history</h2>
 * <ol>
 *   <li>items (PAR-INFRA-04)</li>
 * </ol>
 */
public final class ContentsPdc {
    public static final String KEY = "tfp:contents";
    public static final int VERSION = 1;

    private static final NamespacedKey ROOT = Objects.requireNonNull(NamespacedKey.fromString(KEY));
    private static final NamespacedKey V = new NamespacedKey("tfp", "v");
    private static final NamespacedKey ITEMS = new NamespacedKey("tfp", "items");
    private static final byte[] EMPTY_SLOT = new byte[0];

    /** How one slot's stack becomes bytes and back. Paper's own item serializer (data version aware) by default. */
    interface StackCodec {
        byte[] encode(ItemStack stack);

        ItemStack decode(byte[] bytes);
    }

    static final StackCodec PAPER = new StackCodec() {
        @Override
        public byte[] encode(ItemStack stack) {
            return stack.serializeAsBytes();
        }

        @Override
        public ItemStack decode(byte[] bytes) {
            return ItemStack.deserializeBytes(bytes);
        }
    };

    /**
     * The codec in use. Tests on MockBukkit swap it, because MockBukkit's serializer cannot write nested PDC containers
     * (it Java-serializes the meta); the real server always uses {@link #PAPER}.
     */
    static volatile StackCodec codec = PAPER;

    private ContentsPdc() {
    }

    /** The stack's slots, or empty when it has no list (no entry, malformed, over the bounds or a newer version). */
    public static Optional<List<ItemStack>> read(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return Optional.empty();
        }
        return read(stack.getItemMeta().getPersistentDataContainer());
    }

    /** Reads the entry from a container. */
    public static Optional<List<ItemStack>> read(PersistentDataContainer pdc) {
        PersistentDataContainer contents = pdc.get(ROOT, PersistentDataType.TAG_CONTAINER);
        if (contents == null) {
            return Optional.empty();
        }
        Integer v = contents.get(V, PersistentDataType.INTEGER);
        List<byte[]> raw = contents.get(ITEMS, PersistentDataType.LIST.byteArrays());
        if (v == null || v > VERSION || raw == null) {
            return Optional.empty();
        }
        long total = 0;
        for (byte[] b : raw) {
            total += b.length;
        }
        if (!ItemListLimits.withinBounds(raw.size(), total)) {
            return Optional.empty();
        }
        List<ItemStack> slots = new ArrayList<>(raw.size());
        try {
            for (byte[] b : raw) {
                slots.add(b.length == 0 ? ItemStack.empty() : codec.decode(b));
            }
        } catch (RuntimeException malformed) {
            return Optional.empty();
        }
        return Optional.of(Collections.unmodifiableList(slots));
    }

    /** The stack's slots, an empty list when it has none. */
    public static List<ItemStack> contents(ItemStack stack) {
        return read(stack).orElse(List.of());
    }

    /**
     * Writes {@code slots} into the stack's PDC ({@code null} removes the entry; {@code null} or air slots are stored
     * empty). Returns {@code false} and leaves the stack unchanged when it has no item meta, holds an entry of a newer
     * version, or the list is over {@link ItemListLimits}.
     */
    public static boolean write(ItemStack stack, List<ItemStack> slots) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null || !write(meta.getPersistentDataContainer(), slots)) {
            return false;
        }
        stack.setItemMeta(meta);
        return true;
    }

    /** As {@link #write(ItemStack, List)} on a container. */
    public static boolean write(PersistentDataContainer pdc, List<ItemStack> slots) {
        if (isNewer(pdc)) {
            return false;
        }
        if (slots == null) {
            pdc.remove(ROOT);
            return true;
        }
        List<byte[]> raw = new ArrayList<>(slots.size());
        long total = 0;
        for (ItemStack s : slots) {
            byte[] b = isEmpty(s) ? EMPTY_SLOT : codec.encode(s);
            total += b.length;
            raw.add(b);
        }
        if (!ItemListLimits.withinBounds(raw.size(), total)) {
            return false;
        }
        PersistentDataContainer contents = pdc.getAdapterContext().newPersistentDataContainer();
        contents.set(V, PersistentDataType.INTEGER, VERSION);
        contents.set(ITEMS, PersistentDataType.LIST.byteArrays(), raw);
        pdc.set(ROOT, PersistentDataType.TAG_CONTAINER, contents);
        return true;
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

    static boolean isEmpty(ItemStack s) {
        return s == null || s.getType().isAir() || s.getAmount() <= 0;
    }
}
