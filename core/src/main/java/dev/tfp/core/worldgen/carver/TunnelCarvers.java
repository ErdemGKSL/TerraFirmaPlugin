/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/TFCChunkGenerator.java @ c3b4d9536 (applyCarvers), and the carver list of
 * src/main/resources/data/tfc/worldgen/biome/*.json (all 125 biomes: air carvers tfc:cave, tfc:deep_cave, tfc:canyon).
 */
package dev.tfp.core.worldgen.carver;

import dev.tfp.core.worldgen.random.PositionalRandom;
import dev.tfp.core.worldgen.random.XoroshiroRandomSource;

/**
 * The cave and canyon shapes of a chunk: TFC's carver pass. For each of the 17 x 17 chunks around the chunk and each of the
 * three carvers in the biomes' order (cave, deep cave, canyon), a positional random of the world seed at
 * {@code (chunk x, carver number from 1, chunk z)} decides whether that chunk starts a carver and then drives it. The result
 * is the set of positions inside a shape; what a position becomes is {@link CaveCarver#carve}'s decision, and positions the
 * terrain does not hold as carvable rock are the caller's to skip (TFC's {@code canReplaceBlock}).
 *
 * <p>Every TFC biome carries the same three carvers, so the pass does not ask for the biome of a start chunk. The mask is a
 * pure function of the seed, the chunk and the world height: the order of the start chunks cannot change it, because the
 * shapes only add positions. Thread-safe: no shared state.
 */
public final class TunnelCarvers {
    /** The chunk distance TFC's carver pass looks for start chunks ({@code applyCarvers}: -8 to 8). */
    public static final int START_CHUNK_RANGE = 8;

    private TunnelCarvers() {}

    /** The carvers of a TFC biome, in the order of the biome files. The positional random's y is the ordinal plus one. */
    enum Carver {
        CAVE,
        DEEP_CAVE,
        CANYON
    }

    /**
     * @param fork   the world seed's positional random: {@code new XoroshiroRandomSource(seed).forkPositional()}
     * @param chunkX the chunk x
     * @param chunkZ the chunk z
     * @param minY   the world's lowest block y
     * @param height the world's number of block layers
     * @return the positions of the chunk inside a cave or canyon shape
     */
    public static TunnelMask mask(PositionalRandom fork, int chunkX, int chunkZ, int minY, int height) {
        final TunnelMask mask = new TunnelMask(minY, height);
        for (int offsetX = -START_CHUNK_RANGE; offsetX <= START_CHUNK_RANGE; offsetX++) {
            for (int offsetZ = -START_CHUNK_RANGE; offsetZ <= START_CHUNK_RANGE; offsetZ++) {
                for (Carver carver : Carver.values()) {
                    carve(mask, fork, chunkX, chunkZ, chunkX + offsetX, chunkZ + offsetZ, carver);
                }
            }
        }
        return mask;
    }

    /** The mask of one carver alone, for tests. */
    static TunnelMask mask(PositionalRandom fork, int chunkX, int chunkZ, int minY, int height, Carver carver) {
        final TunnelMask mask = new TunnelMask(minY, height);
        for (int offsetX = -START_CHUNK_RANGE; offsetX <= START_CHUNK_RANGE; offsetX++) {
            for (int offsetZ = -START_CHUNK_RANGE; offsetZ <= START_CHUNK_RANGE; offsetZ++) {
                carve(mask, fork, chunkX, chunkZ, chunkX + offsetX, chunkZ + offsetZ, carver);
            }
        }
        return mask;
    }

    private static void carve(TunnelMask mask, PositionalRandom fork, int chunkX, int chunkZ, int startX, int startZ,
        Carver carver) {
        final XoroshiroRandomSource random = fork.at(startX, carver.ordinal() + 1, startZ);
        switch (carver) {
            case CAVE -> {
                if (CaveTunnels.isStartChunk(CaveTunnels.CAVE, random)) {
                    CaveTunnels.carve(CaveTunnels.CAVE, mask, chunkX, chunkZ, random, startX, startZ);
                }
            }
            case DEEP_CAVE -> {
                if (CaveTunnels.isStartChunk(CaveTunnels.DEEP_CAVE, random)) {
                    CaveTunnels.carve(CaveTunnels.DEEP_CAVE, mask, chunkX, chunkZ, random, startX, startZ);
                }
            }
            case CANYON -> {
                if (CanyonTunnels.isStartChunk(random)) {
                    CanyonTunnels.carve(mask, chunkX, chunkZ, random, startX, startZ);
                }
            }
        }
    }
}
