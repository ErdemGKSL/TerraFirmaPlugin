package dev.tfp.spike.ce01;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.LimitedRegion;
import org.bukkit.generator.WorldInfo;

/**
 * Flat stone world. Every chunk gets:
 * <ul>
 *   <li>noise: {@code marker} at local (8, Y, 8) and {@code marker_be} at (4, Y, 4)</li>
 *   <li>populator: {@code marker} at (12, Y, 12) in its own chunk, and at local (2, Y, 2) of the
 *       east neighbour (a cross-chunk {@link LimitedRegion} write)</li>
 * </ul>
 * Block data is resolved once by {@link Ce01Plugin} and read from volatile fields here.
 */
public final class SpikeGenerator extends ChunkGenerator {
    static final int SURFACE = 60;
    static final int Y = SURFACE + 1;

    static volatile BlockData marker;
    static volatile BlockData markerBe;

    static final AtomicInteger noiseWithoutCe = new AtomicInteger();
    static final AtomicInteger noiseWrites = new AtomicInteger();
    static final AtomicInteger popWrites = new AtomicInteger();
    static final AtomicInteger popNeighbourWrites = new AtomicInteger();
    static final AtomicInteger popNeighbourOutside = new AtomicInteger();
    static final Map<String, AtomicInteger> NOISE_THREADS = new ConcurrentHashMap<>();
    static final Map<String, AtomicInteger> POP_THREADS = new ConcurrentHashMap<>();
    static volatile int buffer = -1;

    @Override
    public void generateNoise(WorldInfo info, Random random, int cx, int cz, ChunkData data) {
        MarkerBeBehavior.count(NOISE_THREADS);
        data.setRegion(0, data.getMinHeight(), 0, 16, data.getMinHeight() + 1, 16, Material.BEDROCK);
        data.setRegion(0, data.getMinHeight() + 1, 0, 16, SURFACE + 1, 16, Material.STONE);
        BlockData m = marker, mb = markerBe;
        if (m == null || mb == null) {
            noiseWithoutCe.incrementAndGet();
            return;
        }
        data.setBlock(8, Y, 8, m);
        data.setBlock(4, Y, 4, mb);
        noiseWrites.incrementAndGet();
    }

    @Override
    public List<BlockPopulator> getDefaultPopulators(World world) {
        return List.of(new BlockPopulator() {
            @Override
            public void populate(WorldInfo info, Random random, int cx, int cz, LimitedRegion region) {
                MarkerBeBehavior.count(POP_THREADS);
                buffer = region.getBuffer();
                BlockData m = marker;
                if (m == null) {
                    return;
                }
                region.setBlockData(cx * 16 + 12, Y, cz * 16 + 12, m);
                popWrites.incrementAndGet();
                int nx = cx * 16 + 16 + 2, nz = cz * 16 + 2;
                if (region.isInRegion(nx, Y, nz)) {
                    region.setBlockData(nx, Y, nz, m);
                    popNeighbourWrites.incrementAndGet();
                } else {
                    popNeighbourOutside.incrementAndGet();
                }
            }
        });
    }

    @Override
    public Location getFixedSpawnLocation(World world, Random random) {
        return new Location(world, 0.5, Y + 1, 0.5);
    }

    @Override public boolean shouldGenerateNoise() { return false; }
    @Override public boolean shouldGenerateSurface() { return false; }
    @Override public boolean shouldGenerateCaves() { return false; }
    @Override public boolean shouldGenerateDecorations() { return false; }
    @Override public boolean shouldGenerateMobs() { return false; }
    @Override public boolean shouldGenerateStructures() { return false; }
}
