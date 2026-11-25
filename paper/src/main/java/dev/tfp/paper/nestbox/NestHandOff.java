/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/ai/livestock/LayEggBehavior.java @ c3b4d9536
 *       (a hen with a nest box in memory walks to it instead of laying)
 */
package dev.tfp.paper.nestbox;

import java.util.UUID;

/**
 * PAR-FAUNA-27: what the animal products module asks the nest box module before a hen lays an egg on the ground. A hen
 * that sits in a nest, or has a nest box with a free slot within {@link NestSeeker#RADIUS}, lays there (TFC: she walks to
 * the nest box she remembers), so the ground laying skips her. Provided as a module service; any thread.
 */
public interface NestHandOff {
    /** Whether the hen at the given position lays in a nest box (or is about to) rather than on the ground. */
    boolean laysInNest(UUID hen, UUID world, double x, double y, double z);
}
