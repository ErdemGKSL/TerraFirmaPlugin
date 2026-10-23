/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.neighbour;

import dev.tfp.core.neighbour.Neighbourhood;
import dev.tfp.core.neighbour.ShapeRule;
import dev.tfp.core.world.Direction;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.scheduler.Scheduler;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * The body of the self-test {@code neighbour.converge} (PAR-INFRA-10), kept here so the itest case is one line in
 * {@code SelfTestSuite}. A test {@link ShapeRule} on vanilla bone blocks (no CraftEngine needed; vanilla never updates a
 * bone block's axis by itself): {@code axis=x} when a bone block is east or west, else {@code axis=y}; provisional
 * {@code axis=y}.
 *
 * <ol>
 *   <li>Ten bone blocks are placed in a row through {@link NeighbourService#placeProvisional} in one region task:
 *       after the runner settles every block is {@code axis=x} and exactly ten changes were applied (one per changed
 *       block), with no thread violation.</li>
 *   <li>The second block is broken with vanilla physics: the {@code BlockPhysicsEvent} path re-decides the first one,
 *       which becomes {@code axis=y} with exactly one more applied change.</li>
 * </ol>
 * Runs on its own thread; every world access goes through the {@link Scheduler}. The blocks and the rule are removed
 * afterwards.
 */
public final class NeighbourSelfCheck {
    public static final String RULE_ID = "tfp:selftest/bone_axis";
    static final String BONE = "minecraft:bone_block";
    static final int COUNT = 10;
    /** Chunks east of spawn, away from the other self-test cases (0..3, 40, 48). */
    static final int CHUNK_OFFSET = 56;
    private static final Duration STEP = Duration.ofSeconds(20);

    private NeighbourSelfCheck() {
    }

    /** The test rule. */
    public static ShapeRule<BlockStateRef> boneAxisRule() {
        return new ShapeRule<>() {
            @Override
            public String id() {
                return RULE_ID;
            }

            @Override
            public Set<String> blockIds() {
                return Set.of(BONE);
            }

            @Override
            public boolean appliesTo(BlockStateRef state) {
                return state.id().equals(BONE);
            }

            @Override
            public BlockStateRef decide(Neighbourhood<BlockStateRef> n) {
                BlockStateRef east = n.at(Direction.EAST);
                BlockStateRef west = n.at(Direction.WEST);
                if (east == null || west == null) {
                    return null;
                }
                boolean row = appliesTo(east) || appliesTo(west);
                return n.center().with("axis", row ? "x" : "y");
            }

            @Override
            public BlockStateRef provisional(BlockStateRef placed) {
                return placed.with("axis", "y");
            }
        };
    }

    /** Starts the check on its own thread; the future fails with an {@link AssertionError} on a wrong result. */
    public static CompletableFuture<String> run(NeighbourService service, Scheduler scheduler, World world) {
        CompletableFuture<String> out = new CompletableFuture<>();
        Thread t = new Thread(() -> {
            try {
                out.complete(converge(service, scheduler, world));
            } catch (Throwable e) {
                out.completeExceptionally(e);
            }
        }, "tfp-selftest-neighbour");
        t.setDaemon(true);
        t.start();
        return out;
    }

    static String converge(NeighbourService service, Scheduler s, World world) throws Exception {
        Location spawn = world.getSpawnLocation();
        int cx = (spawn.getBlockX() >> 4) + CHUNK_OFFSET;
        int cz = spawn.getBlockZ() >> 4;
        world.getChunkAtAsync(cx, cz).get(STEP.toSeconds(), TimeUnit.SECONDS);
        onThread(s, world, cx, cz, () -> {
            world.setChunkForceLoaded(cx, cz, true);
            return null;
        });
        NeighbourRunner<BlockStateRef> runner = service.runner(world);
        List<Block> row = new ArrayList<>();
        NeighbourService.Registration rule = service.register(boneAxisRule());
        try {
            int x0 = (cx << 4) + 3;
            int z = (cz << 4) + 8;
            int y = onThread(s, world, cx, cz,
                    () -> Math.min(world.getMaxHeight() - 4, world.getHighestBlockYAt(x0, z) + 12));
            long applied0 = runner.metrics().applied.sum();
            long violations0 = runner.metrics().threadViolations.sum();
            onThread(s, world, cx, cz, () -> {
                for (int i = 0; i < COUNT; i++) {
                    Block b = world.getBlockAt(x0 + i, y, z);
                    row.add(b);
                    service.placeProvisional(b, BlockStateRef.of(BONE));
                }
                return null;
            });
            awaitIdle(runner);
            List<String> axes = onThread(s, world, cx, cz, () -> axes(row));
            long applied = runner.metrics().applied.sum() - applied0;
            check(axes.stream().allMatch("x"::equals), "after placing the row the axes are " + axes);
            check(applied == COUNT, applied + " changes applied for " + COUNT + " changed blocks: " + runner);

            onThread(s, world, cx, cz, () -> {
                row.get(1).setType(Material.AIR, true);
                return null;
            });
            awaitIdle(runner);
            List<String> after = onThread(s, world, cx, cz, () -> axes(row));
            long applied2 = runner.metrics().applied.sum() - applied0 - applied;
            check("y".equals(after.get(0)), "the block left alone is axis=" + after.get(0) + " after its neighbour broke");
            check(applied2 == 1, applied2 + " changes applied after the break, expected 1: " + runner);
            long violations = runner.metrics().threadViolations.sum() - violations0;
            return COUNT + " placements converged with " + applied + " commits; break re-decided by physics with "
                    + applied2 + " commit; thread violations " + violations + "; " + runner;
        } finally {
            rule.unregister();
            onThread(s, world, cx, cz, () -> {
                for (Block b : row) {
                    b.setType(Material.AIR, false);
                }
                world.setChunkForceLoaded(cx, cz, false);
                return null;
            });
        }
    }

    private static List<String> axes(List<Block> row) {
        List<String> out = new ArrayList<>();
        for (Block b : row) {
            BlockStateRef st = BlockStateRef.parse(b.getBlockData().getAsString());
            out.add(st.id().equals(BONE) ? st.properties().get("axis") : st.id());
        }
        return out;
    }

    private static void awaitIdle(NeighbourRunner<?> runner) throws InterruptedException {
        long deadline = System.nanoTime() + STEP.toNanos();
        int stable = 0;
        while (stable < 10 && System.nanoTime() < deadline) {
            stable = runner.isIdle() ? stable + 1 : 0;
            Thread.sleep(50);
        }
        check(runner.isIdle(), "the neighbour runner did not settle: " + runner);
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

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
