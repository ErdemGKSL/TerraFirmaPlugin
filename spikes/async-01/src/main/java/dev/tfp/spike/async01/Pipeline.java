package dev.tfp.spike.async01;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

/**
 * Prototype of AsyncDecisionPipeline + RegionCommitQueue (M0-ASYNC-01).
 *
 * <pre>
 * submit(pos)            owner thread: coalesce per position, capture snapshot (other chunks are
 *                        captured on their own owners), hand off
 * decide(snapshot)       worker pool: pure function -> List&lt;BlockChange&gt;
 * enqueue + drain        owner region of each chunk: validate expectedOld, apply under a per-thread
 *                        tick budget; stale changes are re-submitted, never re-decided inline
 * </pre>
 */
final class Pipeline {
    record Pos(int x, int y, int z) {
        long chunkKey() { return ((long) (x >> 4) << 32) ^ ((z >> 4) & 0xffffffffL); }
        int cx() { return x >> 4; }
        int cz() { return z >> 4; }
        Pos off(int dx, int dy, int dz) { return new Pos(x + dx, y + dy, z + dz); }
    }

    record BlockChange(Pos pos, Material expectedOld, Material next, long submittedNanos) {}

    /** Immutable view of materials around a position; missing entries are "unknown". */
    record Snapshot(Pos center, Map<Pos, Material> blocks, long submittedNanos) {}

    final Plugin plugin;
    final World world;
    final Function<Snapshot, List<BlockChange>> decide;
    final long budgetNanos;
    final ExecutorService workers = Executors.newFixedThreadPool(
            Math.max(2, Runtime.getRuntime().availableProcessors() - 1), r -> {
                Thread t = new Thread(r, "tfp-decide");
                t.setDaemon(true);
                return t;
            });
    /**
     * Positions with a job in flight -> "re-run" flag. A trigger that arrives while the job is in flight
     * must not be dropped: the job's snapshot may predate it (the expectedOld check only covers the
     * target block, not the neighbours the decision read). It sets the flag and the job re-runs.
     */
    private final Map<Pos, java.util.concurrent.atomic.AtomicBoolean> pending = new ConcurrentHashMap<>();
    /** chunk -> queued changes (latest per position wins). Only touched from that chunk's owner. */
    private final Map<Long, LinkedHashMap<Pos, BlockChange>> queues = new ConcurrentHashMap<>();
    private final Set<Long> drainScheduled = ConcurrentHashMap.newKeySet();
    /** Snapshot time of the last applied decision per position (owner-thread access per chunk). */
    private final Map<Pos, Long> appliedSnapshot = new ConcurrentHashMap<>();
    final Stats stats = new Stats();
    final AtomicLong inFlight = new AtomicLong();

    Pipeline(Plugin plugin, World world, Function<Snapshot, List<BlockChange>> decide, long budgetNanos) {
        this.plugin = plugin;
        this.world = world;
        this.decide = decide;
        this.budgetNanos = budgetNanos;
    }

    /** Owner thread of {@code pos}. Cheap: no neighbour logic here. */
    void submit(Pos pos) {
        stats.submits.incrementAndGet();
        var flag = new java.util.concurrent.atomic.AtomicBoolean();
        var existing = pending.putIfAbsent(pos, flag);
        if (existing != null) {
            existing.set(true);
            stats.coalesced.incrementAndGet();
            return;
        }
        inFlight.incrementAndGet();
        long t0 = System.nanoTime();
        capture(pos, 2).thenApplyAsync(blocks -> decide.apply(new Snapshot(pos, blocks, t0)), workers)
                .whenComplete((changes, err) -> {
            pending.remove(pos);
            if (flag.get()) { // triggered again meanwhile: decide again from a fresh snapshot
                stats.reruns.incrementAndGet();
                inFlight.incrementAndGet();
                Bukkit.getRegionScheduler().run(plugin, world, pos.cx(), pos.cz(), t -> {
                    inFlight.decrementAndGet();
                    submit(pos);
                });
            }
            if (err != null) {
                stats.errors.incrementAndGet();
                plugin.getSLF4JLogger().error("decide failed", err);
            } else {
                stats.decisions.incrementAndGet();
                if (changes.isEmpty()) {
                    stats.noops.incrementAndGet();
                }
                Map<Long, List<BlockChange>> byChunk = new HashMap<>();
                for (BlockChange c : changes) {
                    byChunk.computeIfAbsent(c.pos().chunkKey(), k -> new ArrayList<>()).add(c);
                }
                byChunk.forEach((key, list) -> {
                    Pos p = list.getFirst().pos();
                    inFlight.incrementAndGet();
                    Bukkit.getRegionScheduler().run(plugin, world, p.cx(), p.cz(), t -> {
                        inFlight.decrementAndGet();
                        enqueue(key, p.cx(), p.cz(), list);
                    });
                });
            }
            inFlight.decrementAndGet();
        });
    }

    /** Manhattan-radius neighbourhood. Blocks in chunks we own are read now, others on their owner. */
    CompletableFuture<Map<Pos, Material>> capture(Pos center, int radius) {
        Map<Long, List<Pos>> byChunk = new HashMap<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) <= radius) {
                        Pos p = center.off(dx, dy, dz);
                        byChunk.computeIfAbsent(p.chunkKey(), k -> new ArrayList<>()).add(p);
                    }
                }
            }
        }
        Map<Pos, Material> out = new ConcurrentHashMap<>();
        List<CompletableFuture<Void>> remote = new ArrayList<>();
        for (List<Pos> list : byChunk.values()) {
            Pos first = list.getFirst();
            if (Bukkit.isOwnedByCurrentRegion(world, first.cx(), first.cz())) {
                read(list, out);
            } else {
                stats.remoteCaptures.incrementAndGet();
                CompletableFuture<Void> f = new CompletableFuture<>();
                Bukkit.getRegionScheduler().run(plugin, world, first.cx(), first.cz(), t -> {
                    read(list, out);
                    f.complete(null);
                });
                remote.add(f);
            }
        }
        return remote.isEmpty()
                ? CompletableFuture.completedFuture(out)
                : CompletableFuture.allOf(remote.toArray(CompletableFuture[]::new)).thenApply(v -> out);
    }

    private void read(List<Pos> list, Map<Pos, Material> out) {
        for (Pos p : list) {
            if (world.isChunkLoaded(p.cx(), p.cz())) {
                out.put(p, world.getBlockAt(p.x(), p.y(), p.z()).getType());
            }
        }
    }

    /** Owner thread of the chunk. */
    private void enqueue(long key, int cx, int cz, List<BlockChange> changes) {
        LinkedHashMap<Pos, BlockChange> q = queues.computeIfAbsent(key, k -> new LinkedHashMap<>());
        for (BlockChange c : changes) {
            // two jobs may decide the same position from different snapshots: the newer snapshot wins
            BlockChange prev = q.get(c.pos());
            if (prev != null) {
                stats.queueCoalesced.incrementAndGet();
                if (prev.submittedNanos() > c.submittedNanos()) {
                    continue;
                }
            }
            q.put(c.pos(), c);
        }
        stats.enqueued.addAndGet(changes.size());
        scheduleDrain(key, cx, cz, 0);
    }

    private void scheduleDrain(long key, int cx, int cz, long delayTicks) {
        if (!drainScheduled.add(key)) {
            return;
        }
        inFlight.incrementAndGet();
        Runnable task = () -> {
            inFlight.decrementAndGet();
            drainScheduled.remove(key);
            drain(key, cx, cz);
        };
        if (delayTicks <= 0) {
            Bukkit.getRegionScheduler().run(plugin, world, cx, cz, t -> task.run());
        } else {
            Bukkit.getRegionScheduler().runDelayed(plugin, world, cx, cz, t -> task.run(), delayTicks);
        }
    }

    private void drain(long key, int cx, int cz) {
        if (!Bukkit.isOwnedByCurrentRegion(world, cx, cz)) {
            stats.threadViolations.incrementAndGet();
        }
        LinkedHashMap<Pos, BlockChange> q = queues.get(key);
        if (q == null) {
            return;
        }
        var it = q.values().iterator();
        while (it.hasNext()) {
            if (!ThreadBudget.tryStart(budgetNanos)) {
                stats.budgetDeferrals.incrementAndGet();
                scheduleDrain(key, cx, cz, 1); // rest next tick
                return;
            }
            long s = System.nanoTime();
            BlockChange c = it.next();
            it.remove();
            var block = world.getBlockAt(c.pos().x(), c.pos().y(), c.pos().z());
            Long last = appliedSnapshot.get(c.pos());
            if (last != null && last > c.submittedNanos()) {
                stats.superseded.incrementAndGet(); // a decision from a newer snapshot was applied already
            } else if (block.getType() != c.expectedOld()) {
                stats.stale.incrementAndGet();
                submit(c.pos()); // world changed since the snapshot: decide again, async
            } else {
                block.setType(c.next(), false);
                appliedSnapshot.put(c.pos(), c.submittedNanos());
                stats.applied.incrementAndGet();
                stats.latency(System.nanoTime() - c.submittedNanos());
            }
            ThreadBudget.charge(System.nanoTime() - s);
        }
        queues.remove(key, q);
    }

    boolean idle() {
        return inFlight.get() == 0 && pending.isEmpty() && queues.values().stream().allMatch(Map::isEmpty);
    }

    /** Per-thread, per-50ms window budget: a region thread runs one region tick at a time. */
    static final class ThreadBudget {
        static final ThreadLocal<long[]> WINDOW = ThreadLocal.withInitial(() -> new long[] {0, 0});
        static final AtomicLong maxWindowNanos = new AtomicLong();
        static final AtomicLong windowsOverBudget = new AtomicLong();

        static boolean tryStart(long budget) {
            long[] w = WINDOW.get();
            long now = System.nanoTime();
            if (now - w[0] >= 50_000_000L) {
                if (w[1] > budget) {
                    windowsOverBudget.incrementAndGet();
                }
                w[0] = now;
                w[1] = 0;
            }
            return w[1] < budget;
        }

        static void charge(long nanos) {
            long[] w = WINDOW.get();
            w[1] += nanos;
            maxWindowNanos.accumulateAndGet(w[1], Math::max);
        }
    }

    static final class Stats {
        final AtomicLong submits = new AtomicLong(), coalesced = new AtomicLong(), decisions = new AtomicLong(),
                noops = new AtomicLong(), enqueued = new AtomicLong(), queueCoalesced = new AtomicLong(),
                applied = new AtomicLong(), stale = new AtomicLong(), budgetDeferrals = new AtomicLong(),
                threadViolations = new AtomicLong(), remoteCaptures = new AtomicLong(), errors = new AtomicLong(),
                reruns = new AtomicLong(), superseded = new AtomicLong();
        private final ArrayDeque<Long> latencies = new ArrayDeque<>();

        synchronized void latency(long nanos) {
            latencies.add(nanos);
        }

        synchronized String latencySummary() {
            if (latencies.isEmpty()) {
                return "n=0";
            }
            long[] a = latencies.stream().mapToLong(Long::longValue).sorted().toArray();
            return "n=" + a.length + " p50=" + ms(a[a.length / 2]) + " p95=" + ms(a[(int) (a.length * 0.95)])
                    + " p99=" + ms(a[(int) (a.length * 0.99)]) + " max=" + ms(a[a.length - 1]);
        }

        synchronized void resetLatency() {
            latencies.clear();
        }

        static String ms(long n) {
            return String.format("%.1fms", n / 1e6);
        }

        String summary() {
            return "submits=" + submits + " coalesced=" + coalesced + " reruns=" + reruns + " decisions=" + decisions + " noops=" + noops
                    + " enqueued=" + enqueued + " queueCoalesced=" + queueCoalesced + " applied=" + applied
                    + " stale=" + stale + " superseded=" + superseded + " budgetDeferrals=" + budgetDeferrals + " remoteCaptures=" + remoteCaptures
                    + " threadViolations=" + threadViolations + " errors=" + errors
                    + " maxRegionMsPerTick=" + ms(ThreadBudget.maxWindowNanos.get())
                    + " windowsOverBudget=" + ThreadBudget.windowsOverBudget + " latency{" + latencySummary() + "}";
        }
    }
}
