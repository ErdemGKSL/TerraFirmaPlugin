/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.gather;

import java.util.List;
import java.util.Objects;

/**
 * The loot table of one block: its entries are rolled in order, one pool each.
 *
 * @param blockId the block the table belongs to, for example {@code tfc:plant/allium}
 * @param entries one entry per TFC pool, in file order
 */
public record HarvestTable(String blockId, List<LootEntry> entries) {
    public HarvestTable {
        if (blockId == null || blockId.isBlank()) {
            throw new IllegalArgumentException("blockId must not be blank");
        }
        Objects.requireNonNull(entries, "entries");
        entries = List.copyOf(entries);
    }
}
