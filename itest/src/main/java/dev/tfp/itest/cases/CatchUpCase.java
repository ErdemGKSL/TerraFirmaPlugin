/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.itest.cases;

import dev.tfp.core.calendar.TimeCommand;
import dev.tfp.core.catchup.SweepArea;
import dev.tfp.core.world.ChunkPos;
import dev.tfp.itest.SelfTestCase;
import dev.tfp.itest.SelfTestContext;
import dev.tfp.paper.TfpPlugin;
import dev.tfp.paper.catchup.CatchUpService;
import dev.tfp.paper.catchup.ProbeCatchUp;
import dev.tfp.paper.scheduler.Scheduler;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkUnloadEvent;

/**
 * {@code catchup.lazyblock} (PAR-INFRA-25): the catch-up trigger service (PAR-INFRA-11) on a real server, with the
 * fake lazy block {@link ProbeCatchUp} (vanilla wheat whose age is a pure function of the calendar, one stage per
 * {@value ProbeCatchUp#TICKS_PER_STAGE} player ticks from the moment it is placed).
 *
 * <ol>
 *   <li>Wheat age 0 is placed in a dark stone cell deep under a force-loaded chunk (no light: vanilla random ticks
 *       never grow it) and {@link CatchUpService#track tracked}. The calendar moves 3 stages: the wheat stays at age 0
 *       (nothing ticks it).</li>
 *   <li>The chunk is unloaded and loaded again: the age follows on load (at least 3).</li>
 *   <li>The calendar moves 3 more stages: the age stays put until the sweep period has passed, then the visible-area
 *       sweep around a watching position one chunk away (the self-test server has no players, so the case does what
 *       {@code CatchUpSweep} does for a player: {@link CatchUpService#sweep} for every chunk within the sweep radius,
 *       on its region thread) catches it up (at least 6).</li>
 * </ol>
 * The period comes from the service's settings: {@code -Dtfp.catchup.sweepPeriodTicks=40} keeps the wait short.
 */
public final class CatchUpCase implements SelfTestCase {
    /** Chunks east of spawn: away from ce.block_roundtrip (40), loot/suppression (44) and device.roundtrip (48). */
    static final int CHUNK_OFFSET = 56;
    /** Blocks above the world's minimum height: inside solid rock, well away from the surface. */
    static final int DEPTH = 6;
    private static final Duration STEP = Duration.ofSeconds(20);
    /** The longest sweep wait this case accepts (the default period is 30 s). */
    private static final Duration MAX_PERIOD = Duration.ofSeconds(60);

    @Override
    public String name() {
        return "catchup.lazyblock: a fake lazy block catches up on chunk load and under a watching position "
                + "(PAR-INFRA-25)";
    }

    @Override
    public Duration timeout() {
        return Duration.ofSeconds(10 * STEP.toSeconds() + MAX_PERIOD.toSeconds() + 60);
    }

    @Override
    public CompletableFuture<String> run(SelfTestContext ctx) {
        TfpPlugin tfp = ctx.tfp();
        CatchUpService service = tfp.moduleServices().get(CatchUpService.class)
                .orElseThrow(() -> new AssertionError("no CatchUpService: the catchup module is not enabled"));
        return OnThread.script("catchup-lazyblock", () -> {
            if (!OnThread.poll(STEP, () -> tfp.clock().get().isSet())) {
                throw new AssertionError("the calendar was never published");
            }
            return scenario(tfp, service);
        });
    }

    private static String scenario(TfpPlugin tfp, CatchUpService service) throws Exception {
        Scheduler s = tfp.scheduler();
        World world = Bukkit.getWorlds().getFirst();
        Location spawn = world.getSpawnLocation();
        int cx = (spawn.getBlockX() >> 4) + CHUNK_OFFSET;
        int cz = spawn.getBlockZ() >> 4;
        int x = (cx << 4) + 8;
        int y = world.getMinHeight() + DEPTH;
        int z = (cz << 4) + 8;
        long period = service.settings().sweepPeriodTicks();
        int radius = service.settings().sweepRadiusChunks();
        check(period * 50 <= MAX_PERIOD.toMillis(), "sweep period " + period + " ticks is longer than "
                + MAX_PERIOD.toSeconds() + " s: run with -Dtfp.catchup.sweepPeriodTicks=40");
        check(radius >= 1, "sweep radius " + radius + ": the watching position is one chunk away");
        List<String> log = new ArrayList<>();
        Map<Block, BlockData> original = new LinkedHashMap<>();

        OnThread.await(world.getChunkAtAsync(cx, cz), STEP, "loading chunk " + cx + "," + cz);
        forceLoad(s, world, cx, cz, true);
        long origin = tfp.clock().get().playerTicks();
        service.kinds().unregister(ProbeCatchUp.ID);
        service.kinds().register(ProbeCatchUp.kind(origin));
        try {
            OnThread.region(s, world, cx, cz, STEP, () -> {
                Block wheat = world.getBlockAt(x, y, z);
                for (Block b : List.of(wheat, wheat.getRelative(0, -1, 0), wheat.getRelative(0, 1, 0),
                        wheat.getRelative(1, 0, 0), wheat.getRelative(-1, 0, 0), wheat.getRelative(0, 0, 1),
                        wheat.getRelative(0, 0, -1))) {
                    original.put(b, b.getBlockData());
                    b.setType(Material.STONE, false);
                }
                wheat.getRelative(0, -1, 0).setType(Material.FARMLAND, false);
                wheat.setType(Material.WHEAT, false);
                check(age(wheat) == 0, "the wheat was placed at age " + age(wheat));
                check(service.track(wheat, ProbeCatchUp.ID), "track refused: the chunk's index is unreadable");
                check(service.kindAt(wheat).filter(ProbeCatchUp.ID::equals).isPresent(), "not indexed after track");
                return null;
            });
            log.add("wheat at " + x + "," + y + "," + z + " tracked at player tick " + origin);

            skip(tfp, s, 3);
            int untouched = age(s, world, x, y, z);
            check(untouched == 0, "the wheat grew to " + untouched + " without a catch-up trigger");

            // 1: chunk load
            unload(tfp, world, cx, cz);
            OnThread.await(world.getChunkAtAsync(cx, cz), STEP, "reloading chunk " + cx + "," + cz);
            forceLoad(s, world, cx, cz, true);
            int loaded = awaitAge(s, world, x, y, z, 3);
            check(loaded <= ProbeCatchUp.ageAt(tfp.clock().get().playerTicks(), origin),
                    "age " + loaded + " is ahead of the calendar");
            log.add("+3 stages: age 0 until unloaded, " + loaded + " after the chunk loaded again");

            // 2: a watching position one chunk away, after one sweep period
            skip(tfp, s, 3);
            int beforeSweep = age(s, world, x, y, z);
            check(beforeSweep == loaded, "the wheat moved from " + loaded + " to " + beforeSweep + " before the sweep");
            Thread.sleep(period * 50 + 1_000);
            boolean ourChunkDue = false;
            int swept = 0;
            for (ChunkPos c : SweepArea.around(cx + 1, cz, radius)) {
                boolean due = OnThread.region(s, world, c.x(), c.z(), STEP, () -> service.sweep(world, c.x(), c.z()));
                swept += due ? 1 : 0;
                ourChunkDue |= due && c.x() == cx && c.z() == cz;
            }
            check(ourChunkDue, "the sweep found the wheat's chunk not due " + (period * 50 + 1_000) + " ms after load");
            int watched = awaitAge(s, world, x, y, z, 6);
            log.add("+3 stages: age " + beforeSweep + " until swept, " + watched + " after the sweep of " + swept
                    + " due chunk(s) within radius " + radius + " (period " + period + " ticks)");
            return String.join("; ", log);
        } finally {
            try {
                OnThread.region(s, world, cx, cz, STEP, () -> {
                    service.untrack(world.getBlockAt(x, y, z));
                    original.forEach((b, d) -> b.setBlockData(d, false));
                    return null;
                });
            } catch (Exception | AssertionError e) {
                tfp.getLogger().warning("catchup.lazyblock cleanup failed: " + e);
            }
            service.kinds().unregister(ProbeCatchUp.ID);
            forceLoad(s, world, cx, cz, false);
        }
    }

    /** Moves the calendar by {@code stages} probe stages and checks the player ticks moved at least as far. */
    private static void skip(TfpPlugin tfp, Scheduler s, int stages) throws Exception {
        long ticks = stages * ProbeCatchUp.TICKS_PER_STAGE;
        long before = tfp.clock().get().playerTicks();
        OnThread.global(s, STEP, () -> tfp.calendar().skip(TimeCommand.add(TimeCommand.Unit.TICKS, (int) ticks)));
        long moved = tfp.clock().get().playerTicks() - before;
        check(moved >= ticks, "the calendar skip moved " + moved + " player ticks, expected " + ticks);
    }

    private static int age(Block b) {
        check(b.getType() == Material.WHEAT, "the wheat at " + b.getX() + "," + b.getY() + "," + b.getZ()
                + " is now " + b.getType());
        return ((Ageable) b.getBlockData()).getAge();
    }

    private static int age(Scheduler s, World world, int x, int y, int z) throws Exception {
        return OnThread.region(s, world, x >> 4, z >> 4, STEP, () -> age(world.getBlockAt(x, y, z)));
    }

    /** Polls the age on its region thread until it reaches {@code min}. */
    private static int awaitAge(Scheduler s, World world, int x, int y, int z, int min) throws Exception {
        long deadline = System.nanoTime() + STEP.toNanos();
        int age;
        while ((age = age(s, world, x, y, z)) < min) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("the wheat is at age " + age + ", expected at least " + min + " within "
                        + STEP.toSeconds() + " s");
            }
            Thread.sleep(100);
        }
        return age;
    }

    private static void forceLoad(Scheduler s, World world, int cx, int cz, boolean on) throws Exception {
        OnThread.global(s, STEP, () -> {
            world.setChunkForceLoaded(cx, cz, on);
            return null;
        });
    }

    /** Releases the chunk's force-load ticket and waits for it to unload (no players). */
    private static void unload(TfpPlugin tfp, World world, int cx, int cz) throws Exception {
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
            forceLoad(tfp.scheduler(), world, cx, cz, false);
            OnThread.await(unloaded, Duration.ofSeconds(60), "unloading chunk " + cx + "," + cz);
        } finally {
            HandlerList.unregisterAll(listener);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
