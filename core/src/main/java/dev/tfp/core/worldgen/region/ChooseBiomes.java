/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/region/ChooseBiomes.java @ c3b4d9536 (the task; the rules are in
 * {@link dev.tfp.core.worldgen.biome.BiomeChoice})
 */
package dev.tfp.core.worldgen.region;

import dev.tfp.core.worldgen.biome.BiomeChoice;
import dev.tfp.core.worldgen.layer.Area;

/** The biome task: the last step of the region, so the two seeds it draws come after every earlier draw, as in TFC. */
enum ChooseBiomes implements RegionTask {
    INSTANCE;

    @Override
    public void apply(RegionGenerator.Context context) {
        final RegionGenerator generator = context.generator();
        final Area blobArea = generator.biomeArea.get();
        final long rngSeed = context.random.nextLong();
        final long climateSeed = context.random.nextLong();

        BiomeChoice.choose(context.region, blobArea, generator.continentNoise, rngSeed, climateSeed);
    }
}
