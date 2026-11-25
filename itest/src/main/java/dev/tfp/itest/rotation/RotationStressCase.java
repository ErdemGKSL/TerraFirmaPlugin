/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.itest.rotation;

import dev.tfp.core.world.BlockPos;
import dev.tfp.itest.SelfTestCase;
import dev.tfp.itest.SelfTestContext;
import dev.tfp.paper.TfpPlugin;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.rotation.RotationService;
import dev.tfp.paper.scheduler.Scheduler;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * {@code rotation.stress} (PAR-MECH-11): many rotation lines in separate chunks (separate Folia regions once they are far
 * apart), then chunk unload and load churn. A line is a creative motor and five oak axles along x, placed through our
 * CraftEngine adapter on the chunk's region thread and handed to the device framework as a player placement would.
 *
 * <p>Checks: every line is one network of six blocks; after every churn round (all line chunks unloaded, then loaded again)
 * each line is again exactly the same network (the chunk load hook re-added its blocks, so the networks equal a fresh load);
 * the world holds one network per line. Wrong-thread access shows up in the server log, which the self-test harness scans
 * (Folia's {@code TickThread} checks and our thread asserts), so a clean run on Folia is the Folia stress result.
 */
public final class RotationStressCase implements SelfTestCase {
    static final String MOTOR = "tfp:creative_motor";
    static final String AXLE = "tfp:wood/axle/oak";
    static final int LINES = 8;
    static final int AXLES = 5;
    /** Chunks between two lines: far enough apart to be separate regions on Folia. */
    static final int SPACING = 12;
    /** Chunks east of spawn: away from the other cases. */
    static final int OFFSET = 80;
    static final int ROUNDS = 2;
    private static final Duration STEP = Duration.ofSeconds(30);

    @Override
    public String name() {
        return "rotation.stress: many rotation lines keep their networks over chunk unload and load churn (PAR-MECH-11)";
    }

    @Override
    public Duration timeout() {
        return Duration.ofSeconds(12 * STEP.toSeconds());
    }

    @Override
    public CompletableFuture<String> run(SelfTestContext ctx) {
        TfpPlugin tfp = ctx.tfp();
        if (!tfp.integrations().activePlugins().contains("CraftEngine")) {
            throw new Skip("CraftEngine is not installed or its adapter is disabled");
        }
        RotationService networks = tfp.moduleServices().get(RotationService.class)
            .orElseThrow(() -> new AssertionError("no RotationService: the rotation module is not enabled"));
        DeviceService devices = tfp.moduleServices().get(DeviceService.class)
            .orElseThrow(() -> new AssertionError("no DeviceService: the device module is not enabled"));
        CompletableFuture<String> out = new CompletableFuture<>();
        Thread t = Thread.ofVirtual().name("tfp-selftest-rotation-stress").unstarted(() -> {
            try {
                out.complete(body(tfp, networks, devices));
            } catch (Throwable e) {
                out.completeExceptionally(e);
            }
        });
        out.whenComplete((v, e) -> {
            if (out.isCancelled()) {
                t.interrupt();
            }
        });
        t.start();
        return out;
    }

    /** One line: its chunk, its blocks. */
    private record Line(int cx, int cz, int y, List<BlockPos> blocks) {
        BlockPos motor() {
            return blocks.getFirst();
        }
    }

    private static String body(TfpPlugin tfp, RotationService networks, DeviceService devices) throws Exception {
        CustomBlocks blocks = tfp.integrations().customBlocks();
        if (!blocks.exists(MOTOR) || !blocks.exists(AXLE)) {
            throw new Skip(MOTOR + " or " + AXLE + " is not loaded: the generated pack was not installed this run");
        }
        Scheduler s = tfp.scheduler();
        World world = Bukkit.getWorlds().getFirst();
        Location spawn = world.getSpawnLocation();
        int baseCx = (spawn.getBlockX() >> 4) + OFFSET;
        int cz = spawn.getBlockZ() >> 4;
        List<Line> lines = new ArrayList<>();
        try {
            for (int i = 0; i < LINES; i++) {
                int cx = baseCx + i * SPACING;
                await(world.getChunkAtAsync(cx, cz), "loading chunk " + cx + "," + cz);
                global(s, () -> {
                    world.setChunkForceLoaded(cx, cz, true);
                    return null;
                });
                lines.add(place(s, world, blocks, devices, cx, cz));
            }
            Set<String> before = snapshot(networks, world, lines);
            for (Line l : lines) {
                check(members(networks, world, l.motor()).size() == 1 + AXLES, "line " + l.cx() + " is not one network of "
                    + (1 + AXLES) + ": " + members(networks, world, l.motor()));
            }
            RotationService.Report one = networks.report(world.getUID(), lines.getFirst().motor()).get(30, TimeUnit.SECONDS)
                .orElseThrow(() -> new AssertionError("no report for the first motor"));
            check(one.networks() >= LINES, "the world has " + one.networks() + " networks for " + LINES + " lines");

            for (int round = 1; round <= ROUNDS; round++) {
                for (Line l : lines) {
                    global(s, () -> {
                        world.setChunkForceLoaded(l.cx(), l.cz(), false);
                        return null;
                    });
                }
                for (Line l : lines) {
                    poll("chunk " + l.cx() + " unload", () -> !world.isChunkLoaded(l.cx(), l.cz()));
                }
                for (Line l : lines) {
                    await(world.getChunkAtAsync(l.cx(), l.cz()), "reloading chunk " + l.cx() + "," + l.cz());
                    global(s, () -> {
                        world.setChunkForceLoaded(l.cx(), l.cz(), true);
                        return null;
                    });
                }
                for (Line l : lines) {
                    poll("round " + round + ": line " + l.cx() + " back in its network",
                        () -> members(networks, world, l.motor()).size() == 1 + AXLES);
                }
                Set<String> after = snapshot(networks, world, lines);
                check(after.equals(before), "round " + round + ": the networks differ from before the churn:\nbefore "
                    + before + "\nafter  " + after);
            }
            return LINES + " lines of " + (1 + AXLES) + " blocks, " + ROUNDS + " unload/load rounds: every network equals "
                + "its first load";
        } finally {
            for (Line l : lines) {
                try {
                    region(s, world, l.cx(), l.cz(), () -> {
                        for (BlockPos p : l.blocks()) {
                            blocks.remove(world.getBlockAt(p.x(), p.y(), p.z()));
                        }
                        return null;
                    });
                } catch (Exception | AssertionError e) {
                    tfp.getLogger().warning("rotation.stress cleanup failed: " + e);
                }
                global(s, () -> {
                    world.setChunkForceLoaded(l.cx(), l.cz(), false);
                    return null;
                });
            }
        }
    }

    private static Line place(Scheduler s, World world, CustomBlocks blocks, DeviceService devices, int cx, int cz)
            throws Exception {
        int x = (cx << 4) + 2;
        int z = (cz << 4) + 8;
        return region(s, world, cx, cz, () -> {
            int y = world.getHighestBlockYAt(x, z) + 3;
            List<BlockPos> placed = new ArrayList<>();
            for (int i = 0; i <= AXLES; i++) {
                Block b = world.getBlockAt(x + i, y, z);
                BlockStateRef state = BlockStateRef.of(i == 0 ? MOTOR : AXLE).with("axis", "x");
                check(blocks.place(b, state, false), "place(" + state + ") returned false");
                devices.placed(b, null, EquipmentSlot.HAND, ItemStack.empty());
                placed.add(new BlockPos(b.getX(), b.getY(), b.getZ()));
            }
            return new Line(cx, cz, y, List.copyOf(placed));
        });
    }

    /** The sorted member positions of the network of {@code pos}, as text; empty when the block is in no network. */
    private static Set<String> members(RotationService networks, World world, BlockPos pos) throws Exception {
        Set<String> out = new TreeSet<>();
        networks.report(world.getUID(), pos).get(30, TimeUnit.SECONDS).ifPresent(r -> {
            if (r.target().network().isPresent()) {
                r.network().forEach(m -> out.add(m.pos().x() + "," + m.pos().y() + "," + m.pos().z()));
            }
        });
        return out;
    }

    private static Set<String> snapshot(RotationService networks, World world, List<Line> lines) throws Exception {
        Set<String> all = new TreeSet<>();
        for (Line l : lines) {
            all.add(l.cx() + ": " + members(networks, world, l.motor()));
        }
        return all;
    }

    private static void check(boolean ok, String message) {
        if (!ok) {
            throw new AssertionError(message);
        }
    }

    private static <T> T region(Scheduler s, World world, int cx, int cz, Callable<T> body) throws Exception {
        CompletableFuture<T> f = new CompletableFuture<>();
        s.region(world, cx, cz, () -> complete(f, body));
        return await(f, "region task at chunk " + cx + "," + cz);
    }

    private static <T> T global(Scheduler s, Callable<T> body) throws Exception {
        CompletableFuture<T> f = new CompletableFuture<>();
        s.global(() -> complete(f, body));
        return await(f, "global task");
    }

    private static <T> void complete(CompletableFuture<T> f, Callable<T> body) {
        try {
            f.complete(body.call());
        } catch (Throwable e) {
            f.completeExceptionally(e);
        }
    }

    private static <T> T await(CompletableFuture<T> f, String what) throws Exception {
        try {
            return f.get(STEP.toSeconds(), TimeUnit.SECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            throw new AssertionError("timed out waiting for " + what);
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

    private interface Check {
        boolean ok() throws Exception;
    }

    private static void poll(String what, Check condition) throws Exception {
        long end = System.nanoTime() + STEP.toNanos();
        while (System.nanoTime() < end) {
            if (condition.ok()) {
                return;
            }
            Thread.sleep(200);
        }
        throw new AssertionError("timed out waiting for " + what);
    }
}
