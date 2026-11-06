/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.oreoverlay;

import dev.tfp.core.oreoverlay.OreSnapshot;
import java.util.UUID;

/**
 * The world reads of the ore overlays (PAR-INFRA-16). Every method except the height limits runs on the region thread
 * that owns the chunk or block; what it returns is a copy that any thread may read. {@link BukkitCellReader} is the
 * server's; tests use fakes.
 */
public interface CellReader {
    boolean loaded(UUID world, int chunkX, int chunkZ);

    /** A copy of the chunk for a full build (ores, cell codes, heightmap); only called while it is loaded. */
    OreSnapshot snapshot(UUID world, int chunkX, int chunkZ);

    /** One cell: {@link CellMap#pack}{@code (code, column surface)}; only called while its chunk is loaded. */
    long cell(UUID world, int x, int y, int z);

    int minY(UUID world);

    int maxY(UUID world);
}
