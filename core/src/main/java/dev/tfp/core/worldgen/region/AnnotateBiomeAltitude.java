/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/region/AnnotateBiomeAltitude.java @ c3b4d9536 (the task; the algorithm is in
 * {@link dev.tfp.core.worldgen.biome.BiomeAltitude})
 */
package dev.tfp.core.worldgen.region;

import dev.tfp.core.worldgen.biome.BiomeAltitude;

/** The biome altitude task: runs after the islands and before the climate, as in TFC's task order. */
enum AnnotateBiomeAltitude implements RegionTask {
    INSTANCE;

    @Override
    public void apply(RegionGenerator.Context context) {
        BiomeAltitude.annotate(context.region, context.random);
    }
}
