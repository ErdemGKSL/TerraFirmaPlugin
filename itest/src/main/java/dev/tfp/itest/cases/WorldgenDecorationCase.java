/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.itest.cases;

import dev.tfp.itest.SelfTestCase;
import dev.tfp.itest.SelfTestContext;
import dev.tfp.paper.TfpPlugin;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.scheduler.Scheduler;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * {@code worldgen.decoration} (PAR-WORLD-66): a 4 x 4 sample of fresh chunks ({@value #CHUNK_OFFSET} chunks south of spawn)
 * per biome: the forest floor and shore groundcover ({@value #COVER_PREFIX}*), the tide pool blocks (cobble with salt water
 * next to it) and the bamboo stalks of the surface columns. The census is reported for the review; the case fails when the
 * sample's land chunks carry no groundcover at all (TFC's sticks patch alone reaches one chunk in three of a forest).
 * Each chunk is force-loaded while generated and read on its region thread, then released.
 */
public final class WorldgenDecorationCase implements SelfTestCase {
    static final int CHUNK_OFFSET = 48;
    static final String COVER_PREFIX = "tfp:groundcover/";
    private static final int SIDE = 4;
    private static final Duration STEP = Duration.ofSeconds(30);
    private static final Duration GENERATE = Duration.ofSeconds(60);

    @Override
    public String name() {
        return "worldgen.decoration: fresh chunks carry the forest floor, tide pools and bamboo of their biomes (PAR-WORLD-66)";
    }

    @Override
    public Duration timeout() {
        return Duration.ofSeconds(CraftEngineBlockRoundTripCase.REBUILD.toSeconds()
            + SIDE * SIDE * (GENERATE.toSeconds() + 3 * STEP.toSeconds()) + 30);
    }

    @Override
    public CompletableFuture<String> run(SelfTestContext ctx) {
        TfpPlugin tfp = ctx.tfp();
        Scheduler s = tfp.scheduler();
        CustomBlocks blocks = tfp.integrations().customBlocks();
        return OnThread.script("worldgen-decoration", () -> {
            OnThread.awaitPackRebuild(tfp, CraftEngineBlockRoundTripCase.REBUILD);
            World world = Bukkit.getWorlds().getFirst();
            Location spawn = world.getSpawnLocation();
            int cx0 = spawn.getBlockX() >> 4;
            int cz0 = (spawn.getBlockZ() >> 4) + CHUNK_OFFSET;
            Map<String, int[]> census = new TreeMap<>();
            int landCovers = 0;
            int landChunks = 0;
            for (int dx = 0; dx < SIDE; dx++) {
                for (int dz = 0; dz < SIDE; dz++) {
                    int cx = cx0 + dx;
                    int cz = cz0 + dz;
                    OnThread.global(s, STEP, () -> {
                        world.setChunkForceLoaded(cx, cz, true);
                        return null;
                    });
                    try {
                        OnThread.await(world.getChunkAtAsync(cx, cz), GENERATE, "generating chunk " + cx + "," + cz);
                        Object[] r = OnThread.region(s, world, cx, cz, STEP, () -> count(world, blocks, cx, cz));
                        String biome = (String) r[0];
                        int[] c = (int[]) r[1];
                        int[] sum = census.computeIfAbsent(biome, k -> new int[4]);
                        for (int i = 0; i < 3; i++) {
                            sum[i] += c[i];
                        }
                        sum[3]++;
                        if (!biome.contains("ocean") && !biome.contains("shore") && !biome.contains("flats")
                            && !biome.contains("ice_sheet") && !biome.contains("dune")) {
                            landChunks++;
                            landCovers += c[0];
                        }
                    } finally {
                        OnThread.global(s, STEP, () -> {
                            world.setChunkForceLoaded(cx, cz, false);
                            return null;
                        });
                    }
                }
            }
            StringBuilder out = new StringBuilder();
            census.forEach((biome, c) -> out.append(' ').append(biome).append(" x").append(c[3]).append(": covers=").append(c[0])
                .append(" pools=").append(c[1]).append(" bamboo=").append(c[2]).append(';'));
            if (landChunks > 0 && landCovers == 0) {
                throw new AssertionError("no groundcover in " + landChunks + " land chunks:" + out);
            }
            return "16 chunks:" + out;
        });
    }

    /**
     * Runs on the owning region thread: the biome at the chunk's centre and, over the chunk's surface columns (the top
     * three blocks), the groundcover, tide pool cobble beside salt water and bamboo stalks.
     */
    static Object[] count(World world, CustomBlocks blocks, int cx, int cz) {
        Chunk chunk = world.getChunkAt(cx, cz);
        int[] out = new int[3];
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int x = (cx << 4) + lx;
                int z = (cz << 4) + lz;
                int top = world.getHighestBlockYAt(x, z, HeightMap.WORLD_SURFACE);
                for (int y = top - 2; y <= top + 1; y++) {
                    Block b = chunk.getBlock(lx, y, lz);
                    Material m = b.getType();
                    if (m == Material.BAMBOO) {
                        out[2]++;
                        continue;
                    }
                    if (m.isAir() || m == Material.WATER) {
                        continue;
                    }
                    Optional<BlockStateRef> ref = blocks.stateAt(b);
                    String id = ref.map(BlockStateRef::id).orElse("");
                    if (id.startsWith(COVER_PREFIX)) {
                        out[0]++;
                    } else if (id.startsWith("tfp:plant/golden_bamboo")) {
                        out[2]++;
                    } else if (id.startsWith("tfp:rock/cobble/") && chunk.getBlock(lx, y + 1, lz).getType() == Material.AIR
                        && (lx < 15 && chunk.getBlock(lx + 1, y, lz).getType() == Material.WATER
                            || lx > 0 && chunk.getBlock(lx - 1, y, lz).getType() == Material.WATER)) {
                        out[1]++;
                    }
                }
            }
        }
        String biome = world.getBiome((cx << 4) + 8, 64, (cz << 4) + 8).getKey().getKey();
        return new Object[] {biome, out};
    }
}
