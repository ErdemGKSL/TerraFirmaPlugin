/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.itest.cases;

import dev.tfp.itest.SelfTestCase;
import dev.tfp.itest.SelfTestPack;
import dev.tfp.itest.SelfTestContext;
import dev.tfp.paper.TfpPlugin;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.scheduler.Scheduler;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkUnloadEvent;

/**
 * {@code ce.block_roundtrip} (M1-INT-02): through our {@code CustomBlocks} adapter, on the owning region
 * thread, a CraftEngine block is placed, read back, broken, placed again, and is still the same custom block
 * after its chunk was unloaded and loaded again.
 *
 * <p>Block: the first loaded of {@link #CANDIDATES} (a generated rock, else the self-test pack's marker).
 * Chunk: {@value #CHUNK_OFFSET} chunks east of spawn (outside the always-loaded spawn chunks), force-loaded
 * while the block is written, then released so it unloads (no players). CraftEngine keeps an unloaded chunk's
 * storage cached for 30 s (M0-CE-01), so the case waits {@code tfp.selftest.ceCacheSeconds} (default 35)
 * before loading it again, so the second read comes from the saved chunk.
 */
public final class CraftEngineBlockRoundTripCase implements SelfTestCase {
    /** Preferred block ids, in order. */
    static final List<String> CANDIDATES = List.of("tfp:rock/raw/granite", SelfTestPack.MARKER);
    static final int CHUNK_OFFSET = 40;
    private static final Duration STEP = Duration.ofSeconds(20);
    /** Our first-install pack rebuild (a CraftEngine reload plus pack generation) took about 11 s in the M1 matrix. */
    static final Duration REBUILD = Duration.ofSeconds(120);

    @Override
    public String name() {
        return "ce.block_roundtrip: CraftEngine block places, breaks, places and survives a chunk unload (M1-INT-02)";
    }

    @Override
    public Duration timeout() {
        return Duration.ofSeconds(REBUILD.toSeconds() + 60 + cacheSeconds() + 4 * STEP.toSeconds() + 60);
    }

    static long cacheSeconds() {
        return Long.getLong("tfp.selftest.ceCacheSeconds", 35);
    }

    /** The first candidate {@code exists} accepts. */
    static Optional<String> pick(java.util.function.Predicate<String> exists) {
        return CANDIDATES.stream().filter(exists).findFirst();
    }

    @Override
    public CompletableFuture<String> run(SelfTestContext ctx) {
        TfpPlugin tfp = ctx.tfp();
        if (!tfp.integrations().activePlugins().contains("CraftEngine")) {
            throw new Skip("CraftEngine is not installed or its adapter is disabled");
        }
        CustomBlocks blocks = tfp.integrations().customBlocks();
        Scheduler s = tfp.scheduler();
        return OnThread.script("ce-roundtrip", () -> {
            OnThread.awaitPackRebuild(tfp, REBUILD);
            String id = pick(blocks::exists).orElseThrow(() -> new Skip("none of " + CANDIDATES + " is loaded: neither "
                    + "our pack nor the self-test pack was installed this run (plugins/CraftEngine/resources must exist "
                    + "before the start: run the self-test a second time)"));
            BlockStateRef state = BlockStateRef.of(id);
            World world = Bukkit.getWorlds().getFirst();
            Location spawn = world.getSpawnLocation();
            int cx = (spawn.getBlockX() >> 4) + CHUNK_OFFSET;
            int cz = spawn.getBlockZ() >> 4;
            int x = (cx << 4) + 8;
            int z = (cz << 4) + 8;
            OnThread.await(world.getChunkAtAsync(cx, cz), STEP, "loading chunk " + cx + "," + cz);
            // Folia: force-load tickets may only change on the global region thread; the block work stays on
            // the chunk's owning region thread.
            OnThread.global(s, STEP, () -> {
                world.setChunkForceLoaded(cx, cz, true);
                return null;
            });
            AtomicBoolean forced = new AtomicBoolean(true);
            try {
                return roundTrip(tfp, blocks, s, world, id, state, cx, cz, x, z, forced);
            } finally {
                // An earlier run that failed here left its block and a persistent force-load ticket behind
                // (paper-26.3 M1 rerun: "place returned false" on the leftover); never leave either again.
                if (forced.get()) {
                    OnThread.global(s, STEP, () -> {
                        world.setChunkForceLoaded(cx, cz, false);
                        return null;
                    });
                }
            }
        });
    }

    /** Lowest free spot at or above {@code fromY}: air with no custom block, removing a custom leftover first. */
    static int freeY(World world, CustomBlocks blocks, int x, int fromY, int z, List<String> log) {
        int maxY = world.getMaxHeight() - 2;
        for (int y = Math.min(fromY, maxY); y <= maxY; y++) {
            Block b = world.getBlockAt(x, y, z);
            Optional<BlockStateRef> leftover = blocks.stateAt(b);
            if (leftover.isPresent()) {
                // CraftEngine's server block may not count for the motion-blocking heightmap, so a block left
                // by an aborted earlier run can sit exactly at highest + 2; placing the same state there is a
                // no-op and CraftEngine reports false.
                blocks.remove(b);
                log.add("removed leftover " + leftover.get() + " at y " + y);
            }
            if (b.getType().isAir()) {
                return y;
            }
        }
        throw new AssertionError("no free block above y " + fromY + " at " + x + "," + z);
    }

    private String roundTrip(TfpPlugin tfp, CustomBlocks blocks, Scheduler s, World world, String id,
            BlockStateRef state, int cx, int cz, int x, int z, AtomicBoolean forced) throws Exception {
        List<String> log = new ArrayList<>();
        AtomicReference<BlockStateRef> placed = new AtomicReference<>();
        int y = OnThread.region(s, world, cx, cz, STEP, () -> {
            int top = freeY(world, blocks, x, world.getHighestBlockYAt(x, z) + 2, z, log);
            Block b = world.getBlockAt(x, top, z);
            try {
                placeReadBreakPlace(blocks, b, id, state, placed);
            } catch (RuntimeException | Error e) {
                blocks.remove(b); // leave nothing behind for the next run
                throw e;
            }
            return top;
        });
        BlockStateRef expected = placed.get();
        log.add(expected + " at " + world.getName() + " " + x + "," + y + "," + z + ": place/read/break/place ok");

        CompletableFuture<Void> unloaded = new CompletableFuture<>();
        Listener listener = new Listener() {
            @EventHandler
            public void onUnload(ChunkUnloadEvent e) {
                if (e.getWorld().equals(world) && e.getChunk().getX() == cx && e.getChunk().getZ() == cz) {
                    unloaded.complete(null);
                }
            }
        };
        Bukkit.getPluginManager().registerEvents(listener, tfp);
        try {
            OnThread.global(s, STEP, () -> {
                world.setChunkForceLoaded(cx, cz, false);
                return null;
            });
            forced.set(false);
            OnThread.await(unloaded, Duration.ofSeconds(60), "unloading chunk " + cx + "," + cz
                    + " after removing the force-load ticket");
        } finally {
            HandlerList.unregisterAll(listener);
        }
        log.add("chunk unloaded");
        Thread.sleep(cacheSeconds() * 1000);

        OnThread.await(world.getChunkAtAsync(cx, cz), STEP, "reloading chunk " + cx + "," + cz);
        String after = OnThread.region(s, world, cx, cz, STEP, () -> {
            Block b = world.getBlockAt(x, y, z);
            Optional<BlockStateRef> read = blocks.stateAt(b);
            String seen = read.map(String::valueOf).orElse("vanilla " + b.getBlockData().getAsString());
            blocks.remove(b); // clean up either way
            return read.filter(expected::equals).isPresent() ? null : seen;
        });
        if (after != null) {
            throw new AssertionError("after unload + " + cacheSeconds() + " s + reload the block is " + after
                    + ", expected " + expected + " (" + String.join("; ", log) + ")");
        }
        log.add("still " + expected + " after reload (" + cacheSeconds() + " s past unload)");
        return String.join("; ", log);
    }

    private static void placeReadBreakPlace(CustomBlocks blocks, Block b, String id, BlockStateRef state,
            AtomicReference<BlockStateRef> placed) {
        check(blocks.place(b, state, false), "place(" + id + ") returned false");
        // place(id) puts the block's default state, which carries its properties (raw rock: axis=y), so
        // the first read is checked by block id and later reads against that exact state.
        BlockStateRef first = blocks.stateAt(b).orElse(null);
        check(first != null && first.id().equals(id), "after place: " + blocks.stateAt(b) + ", expected " + id);
        placed.set(first);
        check(blocks.remove(b), "remove returned false");
        check(blocks.stateAt(b).isEmpty(), "after remove still custom: " + blocks.stateAt(b));
        check(blocks.place(b, state, false), "second place(" + id + ") returned false");
        check(first.equals(blocks.stateAt(b).orElse(null)), "after second place: " + blocks.stateAt(b)
                + ", expected " + first);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
