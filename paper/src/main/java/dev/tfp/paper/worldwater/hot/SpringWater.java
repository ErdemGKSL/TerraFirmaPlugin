/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/feature/HotSpringFeature.java @ c3b4d9536 (where the spring water lies)
 */
package dev.tfp.paper.worldwater.hot;

import dev.tfp.core.worldwater.ChunkWater;
import dev.tfp.core.worldwater.WaterKind;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.util.BoundingBox;

/**
 * PAR-FLUID-04: which vanilla water blocks of a TFC world are TFC's spring water (the hot spring pools and their fissures),
 * by {@link HotSpringRules#isSpringWater} and {@link HotSpringRules#springChunk} over the live blocks and the cached chunk
 * biomes of {@code WorldWaterService}. Provided as a module service (for buckets, PAR-FLUID-10).
 *
 * <p>Threads: the owner thread of the blocks read (the block, its column up to {@link HotSpringRules#MAX_SCAN} blocks and
 * its four neighbours). A chunk whose biomes are not cached yet answers false and queues them.
 */
public final class SpringWater {
    private static final double DEFLATE = 1.0E-7;

    /** The water facts of a chunk when cached (a miss queues them). Any thread. */
    @FunctionalInterface
    public interface Chunks {
        Optional<ChunkWater> chunk(World world, int chunkX, int chunkZ);
    }

    private final Chunks chunks;

    public SpringWater(Chunks chunks) {
        this.chunks = chunks;
    }

    /** Owner thread: whether a block is spring water. */
    public boolean isSpringWater(Block block) {
        return isSpringWater(block.getWorld(), block.getX(), block.getY(), block.getZ(), new HashMap<>(1));
    }

    /** Owner thread: the spring water blocks inside a box (deflated like vanilla's {@code checkInsideBlocks}). */
    int blocksIn(World world, BoundingBox box) {
        int x0 = floor(box.getMinX() + DEFLATE);
        int y0 = floor(box.getMinY() + DEFLATE);
        int z0 = floor(box.getMinZ() + DEFLATE);
        int x1 = floor(box.getMaxX() - DEFLATE);
        int y1 = floor(box.getMaxY() - DEFLATE);
        int z1 = floor(box.getMaxZ() - DEFLATE);
        Map<Long, Boolean> springChunks = new HashMap<>(4);
        int count = 0;
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                for (int y = y0; y <= y1; y++) {
                    if (isSpringWater(world, x, y, z, springChunks)) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private boolean isSpringWater(World world, int x, int y, int z, Map<Long, Boolean> springChunks) {
        HotSpringRules.Blocks blocks = (bx, by, bz) -> kind(world, bx, by, bz);
        if (blocks.kind(x, y, z) != HotSpringRules.Kind.WATER) {
            return false;
        }
        int cx = x >> 4;
        int cz = z >> 4;
        boolean near = springChunks.computeIfAbsent(((long) cx << 32) ^ (cz & 0xFFFFFFFFL),
                k -> HotSpringRules.springChunk(cx, cz, (ax, az) -> centreBiome(world, ax, az)).orElse(false));
        return near && notRiverOrSalt(world, x, y, z) && HotSpringRules.isSpringWater(blocks, x, y, z);
    }

    private boolean notRiverOrSalt(World world, int x, int y, int z) {
        return chunks.chunk(world, x >> 4, z >> 4)
                .map(c -> {
                    WaterKind k = c.kind(x & 15, y, z & 15);
                    return k != WaterKind.RIVER && !k.salty();
                })
                .orElse(false);
    }

    private int centreBiome(World world, int chunkX, int chunkZ) {
        return chunks.chunk(world, chunkX, chunkZ).map(c -> c.biome(8, 8)).orElse(-1);
    }

    static HotSpringRules.Kind kind(World world, int x, int y, int z) {
        if (y < world.getMinHeight() || y >= world.getMaxHeight()) {
            return HotSpringRules.Kind.OTHER;
        }
        Material type = world.getBlockAt(x, y, z).getType();
        if (type == Material.WATER) {
            return HotSpringRules.Kind.WATER;
        }
        // Vanilla gravel only: VolcanoBlocks and TfcTerrain write vanilla gravel today. When rock gravel becomes
        // tfp:rock/gravel/<rock> CraftEngine blocks this check must go through the same palette lookup, or springs stop healing.
        return type == Material.GRAVEL ?HotSpringRules.Kind.GRAVEL : HotSpringRules.Kind.OTHER;
    }

    private static int floor(double v) {
        return (int) Math.floor(v);
    }
}
