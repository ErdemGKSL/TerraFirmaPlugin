/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/resources/data/tfc/tags/worldgen/placed_feature/in_biome/veins.json @ c3b4d9536 (tfc:geode after the veins,
 * listed by every biome), with placed_feature/geode.json.
 */
package dev.tfp.core.worldgen.geode;

import dev.tfp.core.worldgen.groundcover.FeatureRandom;
import dev.tfp.core.worldgen.random.XoroshiroRandomSource;
import java.util.Map;

/**
 * TFC's geodes of one world under the chunk-boundary rule (NP-WORLD-01): a chunk runs the geode of itself and of its 8
 * neighbours, in a fixed order, and writes only its own blocks, so a geode crossing chunk borders is the same whichever chunk
 * generates first. Each origin chunk draws from its own feature random ({@link FeatureRandom} of {@code tfc:geode}). Pure and
 * thread-safe.
 */
public final class Geodes {
    /** Where a geode block goes. */
    @FunctionalInterface
    public interface Sink {
        void set(int x, int y, int z, Geode.Layer layer);
    }

    private final Geode geode;
    private final FeatureRandom random;

    public Geodes(long levelSeed) {
        this.geode = new Geode(levelSeed, GeodeConfig.TFC);
        this.random = new FeatureRandom(levelSeed, "tfc:geode");
    }

    public Geode geode() {
        return geode;
    }

    /** The geode origin of a chunk, or null when the rarity filter rejects the chunk. */
    public int[] origin(int chunkX, int chunkZ) {
        return geode.origin(random.chunk(chunkX, chunkZ), chunkX, chunkZ);
    }

    /** The blocks a chunk's own geode writes (any chunk), or an empty map; {@code world} must not depend on the caller. */
    public Map<Long, Geode.Layer> placed(int chunkX, int chunkZ, Geode.World world) {
        final XoroshiroRandomSource r = random.chunk(chunkX, chunkZ);
        final int[] o = geode.origin(r, chunkX, chunkZ);
        return o == null ? Map.of() : geode.place(r, o[0], o[1], o[2], world);
    }

    /**
     * The geode blocks inside a chunk: the geodes of the chunk and its 8 neighbours (x outer, z inner, from -1 to 1), a later
     * geode's block replacing an earlier one's.
     */
    public void chunk(int chunkX, int chunkZ, Geode.World world, Sink sink) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (Map.Entry<Long, Geode.Layer> e : placed(chunkX + dx, chunkZ + dz, world).entrySet()) {
                    final long p = e.getKey();
                    final int x = Geode.unpackX(p);
                    final int z = Geode.unpackZ(p);
                    if (x >> 4 == chunkX && z >> 4 == chunkZ) {
                        sink.set(x, Geode.unpackY(p), z, e.getValue());
                    }
                }
            }
        }
    }
}
