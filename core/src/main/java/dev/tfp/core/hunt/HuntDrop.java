/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/generated/resources/data/tfc/loot_table/entities/*.json (one pool with one entry) @ c3b4d9536
 */
package dev.tfp.core.hunt;

import java.util.List;
import java.util.function.DoubleSupplier;

/**
 * One drop of an animal's loot table: an item, its count and the conditions that must all hold for it to drop. Immutable.
 *
 * @param item       the item id, such as {@code tfc:food/beef}, as the TFC loot table names it
 * @param count      how many the drop yields when it drops
 * @param conditions the conditions, tested in order
 */
public record HuntDrop(String item, HuntCount count, List<HuntCondition> conditions) {

    public HuntDrop {
        conditions = List.copyOf(conditions);
    }

    /**
     * Rolls this drop for one kill. The conditions are tested in order and the first failure stops the roll, so the draws it
     * reads depend only on the conditions up to that point.
     *
     * @return the number of items yielded, 0 when a condition fails or the count rolls 0
     */
    public int yield(HuntKill kill, DoubleSupplier rng) {
        for (HuntCondition condition : conditions) {
            if (!condition.test(kill, rng)) {
                return 0;
            }
        }
        return Math.max(0, count.roll(kill, rng));
    }
}
