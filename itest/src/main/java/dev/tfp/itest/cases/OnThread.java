/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.itest.cases;

import dev.tfp.paper.TfpPlugin;
import dev.tfp.paper.install.PackRebuildTrigger;
import dev.tfp.paper.scheduler.Scheduler;
import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;
import org.bukkit.World;
import org.bukkit.entity.Entity;

/**
 * Helpers for cases written as one blocking script on their own (virtual) thread: each world step is a
 * {@link Scheduler} task on the owning thread, awaited with a deadline. Never call these on a tick thread.
 */
final class OnThread {
    private OnThread() {
    }

    /** Runs {@code body} as the case on a fresh virtual thread; its return value is the pass detail. */
    static CompletableFuture<String> script(String name, Callable<String> body) {
        CompletableFuture<String> out = new CompletableFuture<>();
        Thread t = Thread.ofVirtual().name("tfp-selftest-" + name).unstarted(() -> {
            try {
                out.complete(body.call());
            } catch (Throwable e) {
                out.completeExceptionally(e);
            }
        });
        out.whenComplete((v, e) -> {
            if (out.isCancelled()) {
                t.interrupt(); // the runner's timeout
            }
        });
        t.start();
        return out;
    }

    /** {@code body} on the thread owning chunk {@code cx, cz}; waits at most {@code limit}. */
    static <T> T region(Scheduler s, World world, int cx, int cz, Duration limit, Callable<T> body) throws Exception {
        CompletableFuture<T> f = new CompletableFuture<>();
        s.region(world, cx, cz, () -> complete(f, body));
        return await(f, limit, "region task at chunk " + cx + "," + cz);
    }

    /**
     * Waits for this run's CraftEngine pack rebuild (M1-INST-03) if one was planned: it is a full CraftEngine
     * reload, and while it runs CraftEngine's block registry is empty, so CE block cases would see no blocks.
     */
    static void awaitPackRebuild(TfpPlugin tfp, Duration limit) throws Exception {
        PackRebuildTrigger trigger = tfp.packRebuild();
        if (trigger != null) {
            await(trigger.settled(), limit, "the CraftEngine pack rebuild");
        }
    }

    /** {@code body} on the global region thread (Folia: chunk force-load tickets are changed only there). */
    static <T> T global(Scheduler s, Duration limit, Callable<T> body) throws Exception {
        CompletableFuture<T> f = new CompletableFuture<>();
        s.global(() -> complete(f, body));
        return await(f, limit, "global region task");
    }

    /** {@code body} on {@code entity}'s owning thread; fails if the entity was removed first. */
    static <T> T entity(Scheduler s, Entity entity, Duration limit, Callable<T> body) throws Exception {
        CompletableFuture<T> f = new CompletableFuture<>();
        boolean scheduled = s.entity(entity, () -> complete(f, body),
                () -> f.completeExceptionally(new AssertionError("entity " + entity.getUniqueId() + " was removed")));
        if (!scheduled) {
            throw new AssertionError("entity " + entity.getUniqueId() + " is not valid");
        }
        return await(f, limit, "entity task for " + entity.getUniqueId());
    }

    static <T> T await(CompletableFuture<T> f, Duration limit, String what) throws Exception {
        try {
            return f.get(limit.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            f.cancel(true);
            throw new AssertionError(what + " did not finish within " + limit.toSeconds() + " s");
        } catch (ExecutionException e) {
            Throwable c = e.getCause();
            if (c instanceof Exception ex) {
                throw ex;
            }
            if (c instanceof Error err) {
                throw err;
            }
            throw e;
        }
    }

    /** Polls {@code condition} every 100 ms until it holds; false if {@code limit} passed first. */
    static boolean poll(Duration limit, BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + limit.toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                return false;
            }
            Thread.sleep(100);
        }
        return true;
    }

    private static <T> void complete(CompletableFuture<T> f, Callable<T> body) {
        try {
            f.complete(body.call());
        } catch (Throwable t) {
            f.completeExceptionally(t);
        }
    }
}
