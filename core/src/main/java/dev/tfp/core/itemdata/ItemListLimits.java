/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.itemdata;

/**
 * The bounds of the stored item list ({@code tfp:contents}). TFC's {@code ItemListComponent} codec has no bound; ours
 * keeps a crafted or corrupted item from carrying an unbounded payload into every inventory packet and chunk save (see the
 * ledger divergence). The largest TFC item list on an item is the small vessel's 4 slots; the bounds leave ample room.
 */
public final class ItemListLimits {
    /** The most slots a stored item list may hold. */
    public static final int MAX_SLOTS = 64;
    /** The most bytes the serialized stacks of one list may take together. */
    public static final int MAX_TOTAL_BYTES = 256 * 1024;

    private ItemListLimits() {
    }

    /** Whether a list of {@code slots} slots, {@code totalBytes} serialized bytes in all, is within the bounds. */
    public static boolean withinBounds(int slots, long totalBytes) {
        return slots >= 0 && slots <= MAX_SLOTS && totalBytes >= 0 && totalBytes <= MAX_TOTAL_BYTES;
    }
}
