/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.piston;

import dev.tfp.core.piston.PistonFluidRule;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.pipeline.AsyncDecisionPipeline;
import dev.tfp.paper.pipeline.PipelineMetrics;
import dev.tfp.paper.scheduler.Scheduler;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Piston;

/**
 * The body of the self-test {@code piston.no_water_dupe} (PAR-DATA-17), kept here so the itest case is one line in
 * {@code SelfTestSuite}. A real vanilla sticky piston on the running server pushes a waterlogged oak slab east, in the
 * air well above the terrain (no other water near):
 *
 * <ol>
 *   <li><b>1-tick pulse</b>: a redstone block behind the piston is set in a region task and removed on the first
 *       tick the piston base is seen extended (its extend block event has run, the slab is still moving). The
 *       sticky piston spits the slab ({@code PistonMovingBlockEntity.finalTick}, MC-130183, which lands it with its
 *       water). Once {@link PistonFluidService} is idle (pipeline idle, nothing watched), the slab at its destination
 *       is {@code waterlogged=false}, exactly one dry write was committed, and no water source and no waterlogged
 *       block exists in the 3x3x3 around the origin and the destination.</li>
 *   <li><b>Held pulse</b>: the same setup with the power held for {@link #HELD_TICKS} ticks after the piston
 *       extended. Vanilla's own {@code tick} lands the slab dry; the service decides on it and commits nothing.</li>
 * </ol>
 * Both runs require zero pipeline thread violations. Runs on its own thread; every world access goes through the
 * {@link Scheduler}. The blocks are removed afterwards.
 */
public final class PistonFluidSelfCheck {
    static final String WET_SLAB = "minecraft:oak_slab[type=bottom,waterlogged=true]";
    static final String SLAB = "minecraft:oak_slab";
    static final String PISTON = "minecraft:sticky_piston[facing=east,extended=false]";
    /** Chunks east of spawn, away from the other self-test cases (0..3, 24, 40, 44, 48, 56, 60, 68). */
    static final int CHUNK_OFFSET = 76;
    /** Ticks the held pulse keeps the power: a push lands after 2-3 ticks. */
    static final int HELD_TICKS = 10;
    /** Ticks the pulse clock waits for the piston to extend. */
    static final int PULSE_LIMIT_TICKS = 40;
    private static final Duration STEP = Duration.ofSeconds(20);

    private PistonFluidSelfCheck() {
    }

    /** Starts the check on its own thread; the future fails with an {@link AssertionError} on a wrong result. */
    public static CompletableFuture<String> run(PistonFluidService service, Scheduler scheduler, World world) {
        CompletableFuture<String> out = new CompletableFuture<>();
        Thread t = new Thread(() -> {
            try {
                out.complete(check(service, scheduler, world));
            } catch (Throwable e) {
                out.completeExceptionally(e);
            }
        }, "tfp-selftest-piston");
        t.setDaemon(true);
        t.start();
        return out;
    }

    static String check(PistonFluidService service, Scheduler s, World world) throws Exception {
        Location spawn = world.getSpawnLocation();
        int cx = (spawn.getBlockX() >> 4) + CHUNK_OFFSET;
        int cz = spawn.getBlockZ() >> 4;
        world.getChunkAtAsync(cx, cz).get(STEP.toSeconds(), TimeUnit.SECONDS);
        forceLoad(s, world, cx, cz, true);
        AsyncDecisionPipeline<BlockStateRef, PistonFluidService.Arrival> pipeline = service.pipeline(world);
        PipelineMetrics m = pipeline.metrics();
        long violations0 = m.threadViolations.sum();
        int x0 = (cx << 4) + 4;
        int z1 = (cz << 4) + 4;
        int z2 = z1 + 6;
        int y = onThread(s, world, cx, cz,
                () -> Math.min(world.getMaxHeight() - 8, world.getHighestBlockYAt(x0, z1) + 16));
        try {
            String oneTick = pulse(service, s, world, cx, cz, x0, y, z1, 1, true);
            String held = pulse(service, s, world, cx, cz, x0, y, z2, HELD_TICKS, false);
            long violations = m.threadViolations.sum() - violations0;
            require(violations == 0, violations + " pipeline thread violations: " + m);
            return "1-tick pulse: " + oneTick + "; held pulse: " + held + "; thread violations 0; " + m;
        } finally {
            onThread(s, world, cx, cz, () -> {
                clear(world, x0, y, z1);
                clear(world, x0, y, z2);
                return null;
            });
            forceLoad(s, world, cx, cz, false);
        }
    }

    /**
     * One pulse of {@code ticks} ticks on a fresh setup in row {@code z}: redstone block at {@code x0}, piston at
     * {@code x0+1}, slab at {@code x0+2}, destination {@code x0+3}.
     */
    private static String pulse(PistonFluidService service, Scheduler s, World world, int cx, int cz, int x0, int y,
            int z, int ticks, boolean oneTick) throws Exception {
        PipelineMetrics m = service.pipeline(world).metrics();
        int origin = x0 + 2;
        int dest = x0 + 3;
        onThread(s, world, cx, cz, () -> {
            clear(world, x0, y, z);
            world.getBlockAt(x0 + 1, y, z).setBlockData(Bukkit.createBlockData(PISTON), false);
            world.getBlockAt(origin, y, z).setBlockData(Bukkit.createBlockData(WET_SLAB), false);
            return null;
        });
        String before = onThread(s, world, cx, cz, () -> world.getBlockAt(origin, y, z).getBlockData().getAsString());
        require(before.contains("waterlogged=true"), "the slab was not placed waterlogged: " + before);
        long submits0 = m.submits.sum();
        long applied0 = m.applied.sum();
        CompletableFuture<Void> released = new CompletableFuture<>();
        // The falling edge follows the piston, not the scheduler: a tick task checks the piston base each tick and
        // removes the power on the first tick it sees it extended (the extend block event has run, the slab is still
        // moving), for the 1-tick pulse, or after it has been extended for the held ticks. Where a region task runs
        // relative to the tick's block events differs between Paper and Folia; a fixed delay from the rising edge
        // could reach the piston before its extend event and cancel the pulse.
        AtomicInteger extendedTicks = new AtomicInteger();
        AtomicInteger waited = new AtomicInteger();
        AtomicReference<Scheduler.TaskHandle> clock = new AtomicReference<>();
        onThread(s, world, cx, cz, () -> {
            world.getBlockAt(x0, y, z).setType(Material.REDSTONE_BLOCK, true);
            clock.set(s.regionRepeating(world, cx, cz, 1, 1, () -> {
                try {
                    boolean extended = world.getBlockAt(x0 + 1, y, z).getBlockData() instanceof Piston p
                            && p.isExtended();
                    if (extended && extendedTicks.incrementAndGet() >= ticks) {
                        if (oneTick) {
                            world.getBlockAt(x0, y, z).setType(Material.AIR, true);
                        }
                        released.complete(null);
                    } else if (waited.incrementAndGet() > PULSE_LIMIT_TICKS) {
                        released.completeExceptionally(new AssertionError("the piston did not extend within "
                                + PULSE_LIMIT_TICKS + " ticks: " + world.getBlockAt(x0 + 1, y, z).getBlockData()));
                    }
                } catch (Throwable e) {
                    released.completeExceptionally(e);
                }
                if (released.isDone() && clock.get() != null) {
                    clock.get().cancel();
                }
            }));
            return null;
        });
        await(released);
        awaitIdle(service, world);
        BlockStateRef landed = onThread(s, world, cx, cz,
                () -> BlockStateRef.parse(world.getBlockAt(dest, y, z).getBlockData().getAsString()));
        List<String> wet = onThread(s, world, cx, cz, () -> {
            List<String> out = new ArrayList<>();
            water(world, origin, y, z, out);
            water(world, dest, y, z, out);
            return out;
        });
        String row = onThread(s, world, cx, cz, () -> {
            StringBuilder b = new StringBuilder();
            for (int x = x0; x <= x0 + 4; x++) {
                b.append(x == x0 ? "" : " | ").append(world.getBlockAt(x, y, z).getBlockData().getAsString());
            }
            return b.toString();
        });
        long submits = m.submits.sum() - submits0;
        long applied = m.applied.sum() - applied0;
        require(SLAB.equals(landed.id()), "the slab did not land at its destination " + dest + "," + y + "," + z
                + ": found " + landed + "; row " + row + "; " + m);
        require("false".equals(landed.properties().get(PistonFluidRule.WATERLOGGED)),
                "the slab landed with its water: " + landed + "; " + m);
        require(wet.isEmpty(), "water left around the origin or destination: " + wet);
        require(submits >= 1, "the piston move never reached the pipeline (submits " + submits + "): " + m);
        if (oneTick) {
            require(applied == 1, applied + " dry writes after the 1-tick pulse, expected 1 (MC-130183 landing): " + m);
        } else {
            require(applied == 0, applied + " dry writes after the held pulse, expected 0 (vanilla lands it dry): "
                    + m);
        }
        if (!oneTick) {
            onThread(s, world, cx, cz, () -> {
                world.getBlockAt(x0, y, z).setType(Material.AIR, true);
                return null;
            });
        }
        return "slab landed " + landed + ", " + applied + " commit(s), " + submits + " submit(s), no water in the "
                + "3x3x3 around origin and destination";
    }

    /** Water sources and waterlogged blocks in the 3x3x3 around {@code x, y, z}, appended to {@code out}. */
    private static void water(World world, int x, int y, int z, List<String> out) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    Block b = world.getBlockAt(x + dx, y + dy, z + dz);
                    BlockStateRef st = BlockStateRef.parse(b.getBlockData().getAsString());
                    boolean source = "minecraft:water".equals(st.id()) && "0".equals(st.properties().get("level"));
                    if (source || "true".equals(st.properties().get(PistonFluidRule.WATERLOGGED))) {
                        out.add(b.getX() + "," + b.getY() + "," + b.getZ() + " " + st);
                    }
                }
            }
        }
    }

    /** Air over the setup row and its 3x3x3 neighbourhood, without physics. */
    private static void clear(World world, int x0, int y, int z) {
        for (int x = x0 - 1; x <= x0 + 5; x++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    world.getBlockAt(x, y + dy, z + dz).setType(Material.AIR, false);
                }
            }
        }
    }

    /** Waits until nothing is watched and the pipeline is idle for ten polls (half a second) in a row. */
    private static void awaitIdle(PistonFluidService service, World world) throws InterruptedException {
        AsyncDecisionPipeline<?, ?> pipeline = service.pipeline(world);
        long deadline = System.nanoTime() + STEP.toNanos();
        int stable = 0;
        while (stable < 10 && System.nanoTime() < deadline) {
            stable = pipeline.isIdle() && service.watching(world) == 0 ? stable + 1 : 0;
            Thread.sleep(50);
        }
        require(pipeline.isIdle() && service.watching(world) == 0, "the piston fluid service did not settle: watching "
                + service.watching(world) + ", " + pipeline.metrics());
    }

    /** Chunk force-load tickets change only on the global region thread on Folia. */
    private static void forceLoad(Scheduler s, World world, int cx, int cz, boolean on) throws Exception {
        CompletableFuture<Void> f = new CompletableFuture<>();
        s.global(() -> {
            try {
                world.setChunkForceLoaded(cx, cz, on);
                f.complete(null);
            } catch (Throwable e) {
                f.completeExceptionally(e);
            }
        });
        await(f);
    }

    private static <T> T onThread(Scheduler s, World world, int cx, int cz, Callable<T> body) throws Exception {
        CompletableFuture<T> f = new CompletableFuture<>();
        s.region(world, cx, cz, () -> {
            try {
                f.complete(body.call());
            } catch (Throwable e) {
                f.completeExceptionally(e);
            }
        });
        return await(f);
    }

    private static <T> T await(CompletableFuture<T> f) throws Exception {
        try {
            return f.get(STEP.toSeconds(), TimeUnit.SECONDS);
        } catch (java.util.concurrent.ExecutionException e) {
            if (e.getCause() instanceof Exception ex) {
                throw ex;
            }
            if (e.getCause() instanceof Error err) {
                throw err;
            }
            throw e;
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
