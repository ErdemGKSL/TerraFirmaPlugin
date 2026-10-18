/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/recipes/ItemStackProvider.java @ c3b4d9536 (a fixed item stack)
 */
package dev.tfp.core.craft;

/**
 * A fixed item result: an item id and a count. TFC's scraping, loom and sewing results are all fixed stacks.
 *
 * @param item  the item id, for example {@code tfc:wool}
 * @param count the count, {@code >= 1}
 */
public record CraftOutput(String item, int count) {
    public CraftOutput {
        if (item == null || item.isEmpty()) throw new IllegalArgumentException("empty result item");
        if (count < 1) throw new IllegalArgumentException("result count must be >= 1: " + count);
    }
}
