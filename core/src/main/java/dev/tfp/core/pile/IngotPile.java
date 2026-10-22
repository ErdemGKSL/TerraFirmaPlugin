/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/IngotPileBlockEntity.java @ c3b4d9536 (addIngot, removeIngot, getPickedItemStack)
 */
package dev.tfp.core.pile;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The contents of one ingot pile: its kind and its ingots, bottom first. The count is the number of ingots, so the counter of
 * TFC's block state and the list of its block entity are one value here and cannot drift apart.
 *
 * <p>Adding puts an ingot on top; removing takes the top ingot (last in, first out), as TFC's {@code addIngot} and
 * {@code removeIngot} do. Picking the block gives the bottom ingot, as TFC's {@code getPickedItemStack} does.
 *
 * @param kind  the kind of the pile
 * @param items the item ids of the ingots, bottom first; {@code 1..kind.capacity()} entries, none empty
 */
public record IngotPile(PileKind kind, List<String> items) {

    public IngotPile {
        Objects.requireNonNull(kind, "kind");
        items = List.copyOf(items);
        if (items.isEmpty() || items.size() > kind.capacity()) {
            throw new IllegalArgumentException("ingot count out of range for " + kind + ": " + items.size());
        }
        for (String item : items) {
            if (item == null || item.isEmpty()) throw new IllegalArgumentException("empty ingot item");
        }
    }

    /** A pile of one ingot, as a block placed with the first ingot. */
    public static IngotPile single(PileKind kind, String item) {
        return new IngotPile(kind, List.of(item));
    }

    /** The count of ingots in the pile. */
    public int count() {
        return items.size();
    }

    /** Whether the pile holds {@link PileKind#capacity()} ingots. */
    public boolean full() {
        return count() == kind.capacity();
    }

    /** The pile as a {@link PileCell} for the rules. */
    public PileCell asCell() {
        return new PileCell.Filled(kind, count());
    }

    /**
     * The pile with one more ingot on top.
     *
     * @throws IllegalStateException when the pile is full
     */
    public IngotPile add(String item) {
        if (full()) throw new IllegalStateException("ingot pile is full: " + kind);
        List<String> next = new ArrayList<>(items);
        next.add(item);
        return new IngotPile(kind, next);
    }

    /**
     * Takes the top ingot.
     *
     * @return the ingot taken, and the pile that remains, which is empty (no pile) when the pile had one ingot
     */
    public Removal removeTop() {
        List<String> next = new ArrayList<>(items);
        String top = next.remove(next.size() - 1);
        Optional<IngotPile> remaining = next.isEmpty() ? Optional.empty() : Optional.of(new IngotPile(kind, next));
        return new Removal(remaining, top);
    }

    /** The item a pick of the block gives: the bottom ingot (TFC {@code getPickedItemStack}). */
    public String pickedItem() {
        return items.get(0);
    }

    /**
     * The result of {@link #removeTop()}.
     *
     * @param remaining the pile left, or empty when the removed ingot was the last
     * @param item      the item id of the ingot taken
     */
    public record Removal(Optional<IngotPile> remaining, String item) {}
}
