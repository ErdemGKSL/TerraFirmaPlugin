/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/river/RiverBlendType.java @ c3b4d9536, and the sampler map of
 * src/main/java/net/dries007/tfc/world/TFCChunkGenerator.java (createRiverSamplersForChunk: one forkStable seed, in enum order)
 */
package dev.tfp.core.worldgen.river;

import dev.tfp.core.worldgen.Seed;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;

/** How a biome shapes the land around a river. The order is TFC's: the samplers take their seeds in this order. */
public enum RiverBlendType {
    NONE(seed -> RiverNoiseSampler.NONE),
    BANKED(RiverNoise::banked), // Raised banks 1-2 blocks above water that may be higher than surrounding terrain in swampy biomes
    TALL_BANKED(RiverNoise::tallBanked), // Sim to banked, but taller for use in mud flat/salt flat biomes
    FLOODPLAIN(RiverNoise::floodplain), // Flat banks at water level with steep banks farther from river
    WIDE(RiverNoise::wide), // Wide, smooth V-shaped valleys
    WIDE_DEEP(RiverNoise::wideDeep), // Wide, smooth V-shaped valleys, but a few blocks deeper
    CANYON(RiverNoise::canyon), // Tall, smooth V-shaped valleys
    TALL_CANYON(RiverNoise::tallCanyon), // Slot canyons with undercut walls
    TALUS(RiverNoise::talus), // Single line of cliffs with steep slopes above and below
    TERRACES(RiverNoise::terraces), // Stair-step canyons, like the Grand Canyon
    CAVE(RiverNoise::cave); // Underground river

    public static final RiverBlendType[] ALL = values();
    public static final int SIZE = ALL.length;

    private final Function<Seed, RiverNoiseSampler> factory;

    RiverBlendType(Function<Seed, RiverNoiseSampler> factory) {
        this.factory = factory;
    }

    public RiverNoiseSampler createNoiseSampler(Seed seed) {
        return factory.apply(seed);
    }

    /**
     * TFC's {@code createRiverSamplersForChunk}: every blend type's sampler, created from one fresh seed stream of the level
     * seed ({@code seed.forkStable()}) in enum order. The samplers are immutable, so the map is built once per world.
     */
    public static Map<RiverBlendType, RiverNoiseSampler> createSamplers(long levelSeed) {
        final Seed seed = Seed.of(levelSeed);
        final EnumMap<RiverBlendType, RiverNoiseSampler> out = new EnumMap<>(RiverBlendType.class);
        for (RiverBlendType type : ALL) {
            out.put(type, type.createNoiseSampler(seed));
        }
        return Collections.unmodifiableMap(out);
    }
}
