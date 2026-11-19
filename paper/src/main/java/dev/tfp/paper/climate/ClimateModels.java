/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/climate/ClimateModels.java @ c3b4d9536
 */
package dev.tfp.paper.climate;

import dev.tfp.core.climate.model.ClimateModelKind;
import dev.tfp.paper.worldgen.TfcChunkGenerator;
import org.bukkit.World;

/** Picks the climate model of a world exactly as TFC does (PAR-CLIM-11). Any thread. */
public final class ClimateModels {
    private ClimateModels() {
    }

    /** Overworld with our generator: the overworld model; the Nether, the End and vanilla-generated worlds: biome-based. */
    public static ClimateModelKind kindOf(World world) {
        return ClimateModelKind.select(world.getEnvironment() == World.Environment.NORMAL,
                world.getGenerator() instanceof TfcChunkGenerator);
    }

    /**
     * Whether the biome at the position has precipitation. Paper exposes no flag, so this is the biome downfall (0 for desert,
     * savanna, badlands, Nether) and never in the End, whose biomes have downfall but no precipitation. Region thread.
     */
    public static boolean hasPrecipitation(World world, int x, int y, int z) {
        return world.getEnvironment() != World.Environment.THE_END && world.getHumidity(x, y, z) > 0;
    }
}
