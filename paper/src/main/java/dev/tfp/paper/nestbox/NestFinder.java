/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/ai/livestock/NearestNestBoxSensor.java @ c3b4d9536 (the search for
 *       the nearest nest box, replaced by a registry of known nest boxes)
 */
package dev.tfp.paper.nestbox;

import dev.tfp.core.world.BlockPos;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PAR-FAUNA-07: the nest boxes a hen can look for. TFC's {@code NearestNestBoxSensor} asks the POI manager for nest boxes
 * within 48 blocks and paths to one; here the module keeps the positions of the nest boxes it has seen (placed, or in a
 * loaded chunk) and a hen takes the nearest within a horizontal and vertical radius. Thread-safe; holds positions only.
 */
public final class NestFinder {
    private final Map<UUID, Set<BlockPos>> byWorld = new ConcurrentHashMap<>();

    public void add(UUID world, BlockPos pos) {
        byWorld.computeIfAbsent(world, w -> ConcurrentHashMap.newKeySet()).add(pos);
    }

    public void remove(UUID world, BlockPos pos) {
        Set<BlockPos> set = byWorld.get(world);
        if (set != null) {
            set.remove(pos);
        }
    }

    public int size() {
        return byWorld.values().stream().mapToInt(Set::size).sum();
    }

    /**
     * The nearest nest box to ({@code x}, {@code y}, {@code z}) within {@code radius} blocks horizontally and
     * {@code vertical} blocks up or down; ties go to the lowest position so the answer is stable.
     */
    public Optional<BlockPos> nearest(UUID world, double x, double y, double z, double radius, double vertical) {
        Set<BlockPos> set = byWorld.get(world);
        if (set == null) {
            return Optional.empty();
        }
        double limit = radius * radius;
        return set.stream()
            .filter(p -> Math.abs(p.y() + 0.5 - y) <= vertical && dist2(p, x, z) <= limit)
            .min(Comparator.comparingDouble((BlockPos p) -> dist2(p, x, z)).thenComparingInt(BlockPos::x)
                .thenComparingInt(BlockPos::y).thenComparingInt(BlockPos::z));
    }

    private static double dist2(BlockPos p, double x, double z) {
        double dx = p.x() + 0.5 - x;
        double dz = p.z() + 0.5 - z;
        return dx * dx + dz * dz;
    }
}
