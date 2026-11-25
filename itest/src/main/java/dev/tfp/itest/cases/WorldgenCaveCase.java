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
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;

/**
 * {@code worldgen.cave} (PAR-WORLD-64): 16 fresh chunks (4 x 4, {@value #CHUNK_OFFSET} chunks east of spawn) carry TFC's cave
 * decoration: rock spikes ({@value #SPIKE_PREFIX}*) and hardened rock in the caves, and no falling block entity appears on
 * their first load (erosion stabilised the generated slopes). Counts per chunk are reported for the census; the case fails
 * when no spike generated in any of the 16 chunks (TFC places a spike at 1 in 5 carved positions, so a cave chunk has many).
 * Each chunk is force-loaded while generated and read on its region thread, then released.
 */
public final class WorldgenCaveCase implements SelfTestCase {
    static final int CHUNK_OFFSET = 40;
    static final String SPIKE_PREFIX = "tfp:rock/spike/";
    static final String HARDENED_PREFIX = "tfp:rock/hardened/";
    static final String MOSSY_PREFIX = "tfp:rock/mossy_cobble/";
    private static final int SIDE = 4;
    private static final Duration STEP = Duration.ofSeconds(30);
    private static final Duration GENERATE = Duration.ofSeconds(60);

    @Override
    public String name() {
        return "worldgen.cave: fresh chunks have rock spikes in their caves and no falling blocks on first load (PAR-WORLD-64)";
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
        return OnThread.script("worldgen-cave", () -> {
            OnThread.awaitPackRebuild(tfp, CraftEngineBlockRoundTripCase.REBUILD);
            World world = Bukkit.getWorlds().getFirst();
            Location spawn = world.getSpawnLocation();
            int cx0 = (spawn.getBlockX() >> 4) + CHUNK_OFFSET;
            int cz0 = spawn.getBlockZ() >> 4;
            int spikes = 0;
            int hardened = 0;
            int mossy = 0;
            int falling = 0;
            StringBuilder perChunk = new StringBuilder();
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
                        int[] c = OnThread.region(s, world, cx, cz, STEP, () -> count(world, blocks, cx, cz));
                        spikes += c[0];
                        hardened += c[1];
                        mossy += c[2];
                        falling += c[3];
                        perChunk.append(' ').append(cx).append(',').append(cz).append('=').append(c[0]).append('/')
                            .append(c[1]).append('/').append(c[2]);
                    } finally {
                        OnThread.global(s, STEP, () -> {
                            world.setChunkForceLoaded(cx, cz, false);
                            return null;
                        });
                    }
                }
            }
            if (falling > 0) {
                throw new AssertionError(falling + " falling block entities in the fresh chunks: erosion left an unsupported block");
            }
            if (spikes == 0) {
                throw new AssertionError("no rock spike in 16 fresh chunks (spikes/hardened/mossy per chunk:" + perChunk + ")");
            }
            return "16 chunks: " + spikes + " spike blocks, " + hardened + " hardened rock, " + mossy + " mossy cobble; per chunk"
                + perChunk;
        });
    }

    /**
     * Runs on the owning region thread: the spike, hardened rock and mossy cobble blocks next to cave air below y 60 (only
     * those are read through CraftEngine), and the falling block entities of the chunk.
     */
    static int[] count(World world, CustomBlocks blocks, int cx, int cz) {
        Chunk chunk = world.getChunkAt(cx, cz);
        int[] out = new int[4];
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                for (int y = world.getMinHeight() + 1; y < 60; y++) {
                    Block b = chunk.getBlock(lx, y, lz);
                    Material m = b.getType();
                    if (m.isAir() || m == Material.WATER || m == Material.LAVA) {
                        continue;
                    }
                    if (!chunk.getBlock(lx, y + 1, lz).getType().isAir() && !chunk.getBlock(lx, y - 1, lz).getType().isAir()) {
                        continue;
                    }
                    Optional<BlockStateRef> ref = blocks.stateAt(b);
                    if (ref.isEmpty()) {
                        continue;
                    }
                    String id = ref.get().id();
                    if (id.startsWith(SPIKE_PREFIX)) {
                        out[0]++;
                    } else if (id.startsWith(HARDENED_PREFIX)) {
                        out[1]++;
                    } else if (id.startsWith(MOSSY_PREFIX)) {
                        out[2]++;
                    }
                }
            }
        }
        for (Entity e : chunk.getEntities()) {
            if (e instanceof FallingBlock) {
                out[3]++;
            }
        }
        return out;
    }
}
