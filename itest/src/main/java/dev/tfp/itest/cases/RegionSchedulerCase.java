/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.itest.cases;

import dev.tfp.itest.SelfTestCase;
import dev.tfp.itest.SelfTestContext;
import dev.tfp.paper.TfpPlugin;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.scheduler.ThreadAssertionException;
import dev.tfp.paper.scheduler.ThreadAsserts;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * M1-THR-01: a {@code Scheduler.region} task for the spawn chunk runs on the thread that owns that chunk
 * (the server thread on Paper, a region thread on Folia), and the self-test thread itself is not the owner
 * (so {@code ThreadAsserts.owned} throws there when assertions are on).
 */
public final class RegionSchedulerCase implements SelfTestCase {
    @Override
    public String name() {
        return "Scheduler region task runs on the owning thread (M1-THR-01)";
    }

    @Override
    public Duration timeout() {
        return Duration.ofSeconds(15);
    }

    @Override
    public CompletableFuture<String> run(SelfTestContext ctx) {
        Scheduler scheduler = ctx.tfp().scheduler();
        List<World> worlds = Bukkit.getWorlds();
        if (worlds.isEmpty()) {
            throw new AssertionError("no worlds loaded");
        }
        World world = worlds.getFirst();
        Location spawn = world.getSpawnLocation();
        int cx = spawn.getBlockX() >> 4;
        int cz = spawn.getBlockZ() >> 4;

        if (scheduler.isOwner(world, cx, cz)) {
            throw new AssertionError("the self-test thread " + Thread.currentThread().getName() + " claims to own the chunk");
        }
        if (ThreadAsserts.ENABLED) {
            try {
                ThreadAsserts.owned(world, cx, cz);
                throw new AssertionError("ThreadAsserts.owned did not throw on the self-test thread");
            } catch (ThreadAssertionException expected) {
                // the assertion works off the owner thread
            }
        }

        CompletableFuture<String> out = new CompletableFuture<>();
        scheduler.region(world, cx, cz, () -> {
            try {
                String thread = Thread.currentThread().getName();
                if (!Bukkit.isOwnedByCurrentRegion(world, cx, cz)) {
                    throw new AssertionError("region task for " + cx + "," + cz + " ran on non-owner " + thread);
                }
                if (!scheduler.isOwner(world, cx, cz)) {
                    throw new AssertionError("Scheduler.isOwner is false on the owner thread " + thread);
                }
                ThreadAsserts.owned(world, cx, cz);
                out.complete("chunk " + world.getName() + " " + cx + "," + cz + " on '" + thread + "' (assertions "
                        + (ThreadAsserts.ENABLED ? "on" : "off") + ")");
            } catch (Throwable t) {
                out.completeExceptionally(t);
            }
        });
        return out;
    }
}
