/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.wildraid;

import java.util.function.Predicate;
import org.bukkit.block.Block;
import org.bukkit.entity.Mob;

/**
 * Vanilla's {@code MoveToBlockGoal.findNearestBlock} over {@link dev.tfp.core.wildraid.RaidSearch}'s order: the first valid
 * block around the animal, in one call ({@link RaidScan} with no time slice). Blocks outside the region this thread owns and
 * in unloaded chunks are skipped (never loaded or touched).
 */
final class RaidFinder {
    private RaidFinder() {
    }

    static Block nearest(Mob mob, int range, int vertical, Predicate<Block> valid) {
        RaidScan scan = RaidScan.around(mob.getLocation(), range, vertical, valid);
        scan.advance(System::nanoTime, RaidScan.UNLIMITED);
        return scan.found();
    }
}
