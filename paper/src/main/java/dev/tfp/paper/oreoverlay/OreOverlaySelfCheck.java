/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.oreoverlay;

import dev.tfp.core.oreoverlay.OreOverlayTable;
import dev.tfp.core.visuals.OverlayFace;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.scheduler.Scheduler;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

/**
 * The body of the self-test {@code ore.overlay} (PAR-INFRA-16), kept here so the itest case is one line. A synthetic
 * viewer (no connection: the sender drops its packets) has the test chunk; every world change goes through the region
 * thread and is reported to the service as the block-change packets of a real viewer would be ({@link
 * OreOverlayService#markDirty}). Checks: a buried ore has no face; mining the block above gives exactly the up face; a
 * {@code /fill}-like clear of the 3 x 3 x 3 around it leaves no overlay; after an explosion next to an exposed ore the
 * viewer holds exactly the faces of the rebuilt table (no leftover id). Thread ownership is checked by the suite's log
 * scan (Folia throws on a wrong-thread world access).
 */
public final class OreOverlaySelfCheck {
    static final String ORE = "tfp:ore/normal_native_gold/granite";
    /** Chunks east of spawn, away from the other self-test cases (0..3, 24, 40, 44, 48, 56, 60). */
    static final int CHUNK_OFFSET = 68;
    private static final Duration STEP = Duration.ofSeconds(30);

    private OreOverlaySelfCheck() {
    }

    /** Starts the check on its own thread; the future fails with an {@link AssertionError} on a wrong result. */
    public static CompletableFuture<String> run(OreOverlayService service, CustomBlocks blocks, Scheduler scheduler,
            World world) {
        CompletableFuture<String> out = new CompletableFuture<>();
        Thread t = new Thread(() -> {
            try {
                out.complete(check(service, blocks, scheduler, world));
            } catch (Throwable e) {
                out.completeExceptionally(e);
            }
        }, "tfp-selftest-oreoverlay");
        t.setDaemon(true);
        t.start();
        return out;
    }

    static String check(OreOverlayService service, CustomBlocks blocks, Scheduler s, World world) throws Exception {
        check(blocks.exists(ORE), ORE + " is not a loaded CraftEngine block");
        Location spawn = world.getSpawnLocation();
        int cx = (spawn.getBlockX() >> 4) + CHUNK_OFFSET;
        int cz = spawn.getBlockZ() >> 4;
        world.getChunkAtAsync(cx, cz).get(STEP.toSeconds(), TimeUnit.SECONDS);
        forceLoad(s, world, cx, cz, true);
        UUID w = world.getUID();
        UUID viewer = UUID.randomUUID();
        int x = (cx << 4) + 8;
        int z = (cz << 4) + 8;
        int y = onThread(s, world, cx, cz, () -> Math.min(world.getMaxHeight() - 8, world.getHighestBlockYAt(x, z) + 20));
        try {
            onThread(s, world, cx, cz, () -> {
                cube(world, x, y, z, 2, Material.STONE);
                check(blocks.place(world.getBlockAt(x, y, z), BlockStateRef.of(ORE), false), "placing " + ORE + " failed");
                return null;
            });
            service.viewerWorld(viewer, w);
            service.center(viewer, cx, cz, new Outbox(viewer));
            service.chunkSent(viewer, cx, cz, new Outbox(viewer));
            OreOverlayTable t = settle(service, w, cx, cz);
            int i = t.find(x, y, z);
            check(i >= 0, "the placed ore is not in the table: " + t);
            check(t.mask(i) == 0, "a buried ore has faces " + t.mask(i));
            check(service.sentCount(viewer) == 0, "buried: the viewer holds " + service.sentCount(viewer) + " overlays");

            onThread(s, world, cx, cz, () -> {
                world.getBlockAt(x, y + 1, z).setType(Material.AIR, false);
                return null;
            });
            service.markDirty(w, x, y + 1, z);
            t = settle(service, w, cx, cz);
            i = t.find(x, y, z);
            check(i >= 0 && t.mask(i) == 1 << OverlayFace.UP.ordinal(), "after mining above: mask "
                    + (i < 0 ? "none" : t.mask(i)));
            check(service.sentCount(viewer) == 1, "after mining above the viewer holds " + service.sentCount(viewer));

            onThread(s, world, cx, cz, () -> {
                cube(world, x, y, z, 1, Material.AIR);
                return null;
            });
            dirtyCube(service, w, x, y, z, 1);
            t = settle(service, w, cx, cz);
            check(t.find(x, y, z) < 0, "the cleared ore is still in the table");
            check(service.sentCount(viewer) == 0, "after the clear the viewer holds " + service.sentCount(viewer));
            String afterFill = "fill: 0 overlays left";

            onThread(s, world, cx, cz, () -> {
                cube(world, x, y, z, 2, Material.STONE);
                check(blocks.place(world.getBlockAt(x, y, z), BlockStateRef.of(ORE), false), "placing " + ORE + " failed");
                world.getBlockAt(x, y + 1, z).setType(Material.AIR, false);
                return null;
            });
            dirtyCube(service, w, x, y, z, 2);
            settle(service, w, cx, cz);
            onThread(s, world, cx, cz, () -> world.createExplosion(new Location(world, x + 0.5, y + 2.5, z + 0.5), 4f,
                    false, true));
            dirtyCube(service, w, x, y, z, 4);
            t = settle(service, w, cx, cz);
            int held = service.sentCount(viewer);
            check(held == t.faceCount(), "after the explosion the viewer holds " + held + " overlays, the table has "
                    + t.faceCount() + " faces");
            return "buried 0 faces; mined above: 1 (up); " + afterFill + "; explosion: viewer " + held + " = table "
                    + t.faceCount() + "; " + service.stats(viewer);
        } finally {
            service.quit(viewer);
            onThread(s, world, cx, cz, () -> {
                cube(world, x, y, z, 4, Material.AIR);
                return null;
            });
            forceLoad(s, world, cx, cz, false);
        }
    }

    private static void cube(World world, int x, int y, int z, int r, Material m) {
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    world.getBlockAt(x + dx, y + dy, z + dz).setType(m, false);
                }
            }
        }
    }

    private static void dirtyCube(OreOverlayService service, UUID w, int x, int y, int z, int r) {
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    service.markDirty(w, x + dx, y + dy, z + dz);
                }
            }
        }
    }

    private static OreOverlayTable settle(OreOverlayService service, UUID w, int cx, int cz) throws Exception {
        long deadline = System.nanoTime() + STEP.toNanos();
        int idle = 0;
        while (idle < 5 && System.nanoTime() < deadline) {
            Thread.sleep(50);
            idle = service.idle() ? idle + 1 : 0;
        }
        check(service.idle(), "the ore overlay service did not settle: " + service.stats(null));
        return service.settled(w, cx, cz).get(STEP.toSeconds(), TimeUnit.SECONDS);
    }

    private static void check(boolean ok, String message) {
        if (!ok) {
            throw new AssertionError(message);
        }
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
        } catch (ExecutionException e) {
            if (e.getCause() instanceof Exception ex) {
                throw ex;
            }
            if (e.getCause() instanceof Error err) {
                throw err;
            }
            throw e;
        }
    }
}
