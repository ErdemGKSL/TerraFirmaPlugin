/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/region/RegionTask.java @ c3b4d9536
 */
package dev.tfp.core.worldgen.region;

/** One annotation step of a region. Steps run in the order of {@link RegionGenerator.Task}, all on the calling thread. */
public interface RegionTask {
    void apply(RegionGenerator.Context context);
}
