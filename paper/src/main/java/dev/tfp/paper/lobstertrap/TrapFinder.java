/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/ai/EnterLobsterTrapGoal.java @ c3b4d9536 (findNearestBlock)
 */
package dev.tfp.paper.lobstertrap;

import dev.tfp.core.world.BlockPos;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The baited lobster traps (PAR-FAUNA-19). TFC asks the POI manager for the closest {@code BAITED_LOBSTER_TRAP} within 16 blocks;
 * the module keeps the positions of the traps it last saw baited with a free slot. Thread-safe; holds positions only.
 */
public final class TrapFinder {
    private final Map<UUID, Set<BlockPos>> baited = new ConcurrentHashMap<>();

    public void add(UUID world, BlockPos pos) {
        baited.computeIfAbsent(world, w -> ConcurrentHashMap.newKeySet()).add(pos);
    }

    public void remove(UUID world, BlockPos pos) {
        Set<BlockPos> set = baited.get(world);
        if (set != null) {
            set.remove(pos);
        }
    }

    public int size() {
        return baited.values().stream().mapToInt(Set::size).sum();
    }

    /** The closest baited trap within {@code range} blocks (straight line from the animal to the block centre); ties by position. */
    public Optional<BlockPos> nearest(UUID world, double x, double y, double z, double range) {
        Set<BlockPos> set = baited.get(world);
        if (set == null) {
            return Optional.empty();
        }
        double limit = range * range;
        return set.stream().filter(p -> dist2(p, x, y, z) <= limit)
            .min(Comparator.comparingDouble((BlockPos p) -> dist2(p, x, y, z)).thenComparingInt(BlockPos::x)
                .thenComparingInt(BlockPos::y).thenComparingInt(BlockPos::z));
    }

    static double dist2(BlockPos p, double x, double y, double z) {
        double dx = p.x() + 0.5 - x;
        double dy = p.y() + 0.5 - y;
        double dz = p.z() + 0.5 - z;
        return dx * dx + dy * dy + dz * dz;
    }
}
