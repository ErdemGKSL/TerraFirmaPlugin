/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.itest.cases;

import dev.tfp.itest.SelfTestCase;
import dev.tfp.itest.SelfTestContext;
import dev.tfp.paper.TfpPlugin;
import dev.tfp.paper.collapse.CollapseService;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.scheduler.Scheduler;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * {@code collapse.stress} (PAR-PHYS-07): many simultaneous mining triggers in two far-apart regions (on Folia, two region
 * owners; on Paper, one thread). Each site is a 15 x 15 x 4 slab of raw granite hanging over air. Every cell of the slab's
 * underside is handed to {@link CollapseService#trigger} on its owner, so hundreds of snapshots, worker decisions and
 * commit windows overlap. Passes when the service settles ({@code isIdle}), every trigger was counted, the world's commit
 * queue recorded no thread violation (every read and write ran on its owner) and the real collapses made blocks fall.
 * The commit budget is reported (deferrals, longest latency), not asserted: it is a tuning number, not a correctness one.
 */
public final class CollapseStressCase implements SelfTestCase {
    static final String RAW = "tfp:rock/raw/granite";
    static final int SIZE = 15;
    static final int THICKNESS = 4;
    /** Chunks east of spawn; the two sites are 120 chunks apart so they never share a region. */
    static final int[] CHUNK_OFFSETS = {100, 220};
    private static final Duration STEP = Duration.ofSeconds(60);
    private static final Duration SETTLE = Duration.ofSeconds(120);

    @Override
    public String name() {
        return "collapse.stress: simultaneous collapse triggers in two regions settle with no thread violation "
                + "(PAR-PHYS-07)";
    }

    @Override
    public Duration timeout() {
        return Duration.ofMinutes(8);
    }

    @Override
    public CompletableFuture<String> run(SelfTestContext ctx) {
        TfpPlugin tfp = ctx.tfp();
        if (!tfp.integrations().activePlugins().contains("CraftEngine")) {
            throw new Skip("CraftEngine is not installed or its adapter is disabled");
        }
        CollapseService service = tfp.moduleServices().get(CollapseService.class)
                .orElseThrow(() -> new AssertionError("no CollapseService: the collapse module is not enabled"));
        CustomBlocks blocks = tfp.integrations().customBlocks();
        Scheduler s = tfp.scheduler();
        return OnThread.script("collapse-stress", () -> {
            OnThread.awaitPackRebuild(tfp, Duration.ofSeconds(180));
            if (!blocks.exists(RAW)) {
                throw new Skip(RAW + " is not loaded: the pack was not installed this run");
            }
            World world = Bukkit.getWorlds().getFirst();
            Location spawn = world.getSpawnLocation();
            int cz = spawn.getBlockZ() >> 4;
            List<int[]> sites = new ArrayList<>();
            for (int off : CHUNK_OFFSETS) {
                sites.add(new int[] {(spawn.getBlockX() >> 4) + off, cz});
            }
            List<List<Block>> undersides = new ArrayList<>();
            List<int[]> forced = new ArrayList<>();
            try {
                for (int[] site : sites) {
                    // the trigger needs the 32-block area around the mined block loaded: force 3 x 3 chunks
                    for (int dx = -2; dx <= 2; dx++) {
                        for (int dz = -2; dz <= 2; dz++) {
                            int fx = site[0] + dx;
                            int fz = site[1] + dz;
                            OnThread.await(world.getChunkAtAsync(fx, fz), STEP, "loading chunk " + fx + "," + fz);
                            OnThread.global(s, STEP, () -> {
                                world.setChunkForceLoaded(fx, fz, true);
                                return null;
                            });
                            forced.add(new int[] {fx, fz});
                        }
                    }
                    undersides.add(build(s, world, blocks, site[0], site[1]));
                }
                long triggers0 = service.triggers();
                long falls0 = service.falls(world);
                long violations0 = service.metrics(world).threadViolations.sum();
                List<CompletableFuture<Void>> fired = new ArrayList<>();
                for (int i = 0; i < sites.size(); i++) {
                    int[] site = sites.get(i);
                    List<Block> underside = undersides.get(i);
                    CompletableFuture<Void> f = new CompletableFuture<>();
                    fired.add(f);
                    // one region task per site that triggers every cell: they run concurrently on Folia
                    s.region(world, site[0], site[1], () -> {
                        try {
                            for (Block b : underside) {
                                service.trigger(b);
                            }
                            f.complete(null);
                        } catch (Throwable e) {
                            f.completeExceptionally(e);
                        }
                    });
                }
                for (CompletableFuture<Void> f : fired) {
                    OnThread.await(f, STEP, "the trigger task");
                }
                long deadline = System.nanoTime() + SETTLE.toNanos();
                int stable = 0;
                while (stable < 20 && System.nanoTime() < deadline) {
                    stable = service.isIdle() ? stable + 1 : 0;
                    Thread.sleep(100);
                }
                check(service.isIdle(), "the collapse service did not settle: " + service.metrics(world));
                long triggered = service.triggers() - triggers0;
                long expected = (long) sites.size() * SIZE * SIZE;
                check(triggered == expected, triggered + " triggers counted, expected " + expected);
                long violations = service.metrics(world).threadViolations.sum() - violations0;
                check(violations == 0, violations + " thread violations: " + service.metrics(world));
                long falls = service.falls(world) - falls0;
                check(service.realCollapses() > 0 && falls > 0,
                        "no real collapse made a block fall (real=" + service.realCollapses() + ", falls=" + falls + ")");
                return triggered + " triggers in " + sites.size() + " regions settled; real=" + service.realCollapses()
                        + " fake=" + service.fakeCollapses() + " propagations=" + service.propagations() + " falls="
                        + falls + "; thread violations 0; " + service.metrics(world);
            } finally {
                for (int i = 0; i < undersides.size(); i++) {
                    clear(s, world, sites.get(i), undersides.get(i));
                }
                for (int[] c : forced) {
                    OnThread.global(s, STEP, () -> {
                        world.setChunkForceLoaded(c[0], c[1], false);
                        return null;
                    });
                }
            }
        });
    }

    /** The slab; returns its underside (the blocks mined). */
    private static List<Block> build(Scheduler s, World world, CustomBlocks blocks, int cx, int cz) throws Exception {
        return OnThread.region(s, world, cx, cz, STEP, () -> {
            int x0 = (cx << 4) + 1;
            int z0 = (cz << 4) + 1;
            int y0 = Math.min(world.getMaxHeight() - THICKNESS - 2, world.getHighestBlockYAt(x0, z0) + 12);
            List<Block> underside = new ArrayList<>();
            for (int dy = 0; dy < THICKNESS; dy++) {
                for (int dx = 0; dx < SIZE; dx++) {
                    for (int dz = 0; dz < SIZE; dz++) {
                        Block b = world.getBlockAt(x0 + dx, y0 + dy, z0 + dz);
                        check(blocks.place(b, BlockStateRef.of(RAW), false), "place(" + RAW + ") returned false");
                        if (dy == 0) {
                            underside.add(b);
                        }
                    }
                }
            }
            return underside;
        });
    }

    /** Removes what is left of the slab (it may have fallen already: only raw rock cells are removed). */
    private static void clear(Scheduler s, World world, int[] site, List<Block> underside) {
        try {
            OnThread.region(s, world, site[0], site[1], STEP, () -> {
                for (Block b : underside) {
                    for (int dy = 0; dy < THICKNESS; dy++) {
                        Block c = b.getRelative(0, dy, 0);
                        if (c.getType() != Material.AIR) {
                            c.setType(Material.AIR, false);
                        }
                    }
                }
                return null;
            });
        } catch (Exception ignored) {
            // best effort: the case's verdict is already decided
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
