/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/calendar/ICalendarTickable.java @ c3b4d9536 (checkForCalendarUpdate)
 */
package dev.tfp.paper.catchup;

import dev.tfp.core.catchup.LazyBlockIndex;
import dev.tfp.core.catchup.SweepArea;
import dev.tfp.core.world.BlockPos;
import dev.tfp.core.world.ChunkPos;
import dev.tfp.paper.blockdata.ChunkDataKey;
import dev.tfp.paper.blockdata.ChunkDataStore;
import dev.tfp.paper.pipeline.AsyncDecisionPipeline;
import dev.tfp.paper.pipeline.BlockAccess;
import dev.tfp.paper.pipeline.BlockChange;
import dev.tfp.paper.pipeline.CommitBudget;
import dev.tfp.paper.pipeline.RegionExecutor;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * The catch-up trigger service (PAR-INFRA-11, NP-SIM-01): the substitute for TFC's per-tick
 * {@code ICalendarTickable.checkForCalendarUpdate}. Lazy blocks are not ticked; a chunk's lazy blocks are listed in its
 * {@link LazyBlockIndex} (chunk PDC {@code tfp:catchup/index}) and are brought up to date
 * <ul>
 *   <li>when the chunk loads ({@link #chunkLoaded}),</li>
 *   <li>when a player interacts with one ({@link #touch}),</li>
 *   <li>every {@link CatchUpSettings#sweepPeriodTicks} for the chunks around each player ({@link #sweep}), so crops
 *       and bushes visibly change stage while watched.</li>
 * </ul>
 * Feature code {@link #track}s a lazy block when it is placed (and may {@link #untrack} it when it is broken; an entry
 * whose block is gone is dropped at its next catch-up anyway).
 *
 * <p>Threads, "decide async, apply sync": the triggers run on the chunk's region thread and only copy the index bytes
 * or the cached index. The index is decoded on the worker pool. {@link CatchUpFeeder} hands the blocks to their kind
 * under the feed budget; each {@link CatchUpKind} has one {@link AsyncDecisionPipeline} per world (snapshot =
 * {@link CatchUpKind#read}, decision = the pure {@link CatchUpKind#advance}, commit re-validated and budgeted by the
 * region commit queue). The decoded index of a loaded chunk is cached; every per-chunk entry is touched only on the
 * chunk's owner thread.
 */
public final class CatchUpService {
    /** The chunk PDC entry holding the index. */
    public static final ChunkDataKey<LazyBlockIndex> INDEX_KEY = new ChunkDataKey<>("catchup/index",
            LazyBlockIndex.CODEC);

    private static final long TICK_NANOS = 50_000_000L;

    /** A chunk of a world. */
    record ChunkRef(UUID world, long key) {
        static ChunkRef of(World world, int chunkX, int chunkZ) {
            return new ChunkRef(world.getUID(), ChunkPos.key(chunkX, chunkZ));
        }
    }

    /** What the pipeline of an async kind decides on. */
    record Snap<S>(BlockPos pos, S state, long now, long snapshotNanos) {
    }

    /** Metrics for {@code /tfp debug} and tests. */
    public record Stats(long chunksLoaded, long sweeps, long touches, long tracked, long orphans, long unknownKinds,
            long fed, long feedDeferrals, long decodeErrors) {
    }

    private final CatchUpKinds kinds;
    private final ChunkDataStore store;
    private final Function<World, RegionExecutor> regionsOf;
    private final Executor workers;
    private final LongSupplier playerTicks;
    private final LongSupplier nanos;
    private final CatchUpSettings settings;
    private final Logger logger;
    private final CommitBudget feedBudget;
    private final CommitBudget commitBudget;
    private final Map<UUID, WorldState> worlds = new ConcurrentHashMap<>();
    private final Map<ChunkRef, LazyBlockIndex> indexes = new ConcurrentHashMap<>();
    private final Map<ChunkRef, Long> lastCaughtUp = new ConcurrentHashMap<>();
    private final Map<ChunkRef, Set<BlockPos>> orphans = new ConcurrentHashMap<>();
    private final LongAdder chunksLoaded = new LongAdder();
    private final LongAdder sweeps = new LongAdder();
    private final LongAdder touches = new LongAdder();
    private final LongAdder trackedCount = new LongAdder();
    private final LongAdder orphanCount = new LongAdder();
    private final LongAdder unknownKinds = new LongAdder();
    private final LongAdder decodeErrors = new LongAdder();

    /**
     * @param regionsOf   the region executor of a world (production: {@code ModuleContext.regions})
     * @param workers     the worker pool for decoding and deciding
     * @param playerTicks the calendar's player ticks, the {@code now} handed to {@link CatchUpKind#advance}
     * @param nanos       the monotonic clock of the budgets and the sweep throttle
     */
    public CatchUpService(CatchUpKinds kinds, ChunkDataStore store, Function<World, RegionExecutor> regionsOf,
            Executor workers, LongSupplier playerTicks, LongSupplier nanos, CatchUpSettings settings, Logger logger) {
        this.kinds = Objects.requireNonNull(kinds, "kinds");
        this.store = Objects.requireNonNull(store, "store");
        this.regionsOf = Objects.requireNonNull(regionsOf, "regionsOf");
        this.workers = Objects.requireNonNull(workers, "workers");
        this.playerTicks = Objects.requireNonNull(playerTicks, "playerTicks");
        this.nanos = Objects.requireNonNull(nanos, "nanos");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.feedBudget = new CommitBudget(settings.feedBudgetNanos(), nanos);
        this.commitBudget = new CommitBudget(settings.commitBudgetNanos(), nanos);
    }

    public CatchUpKinds kinds() {
        return kinds;
    }

    public CatchUpSettings settings() {
        return settings;
    }

    public CommitBudget feedBudget() {
        return feedBudget;
    }

    public CommitBudget commitBudget() {
        return commitBudget;
    }

    // ---- index maintenance -------------------------------------------------------------------------------------

    /**
     * Lists {@code block} as a lazy block of {@code kind} (call it when the block is placed or generated). Region
     * thread of the block. Returns false when the chunk's stored index is unreadable (it is then left untouched).
     */
    public boolean track(Block block, String kind) {
        LazyBlockIndex.checkKind(kind);
        Chunk chunk = block.getChunk();
        Optional<LazyBlockIndex> current = current(block.getWorld(), chunk);
        if (current.isEmpty()) {
            return false;
        }
        LazyBlockIndex next = current.get().with(pos(block), kind);
        if (next != current.get()) {
            save(block.getWorld(), chunk, next);
            trackedCount.increment();
        }
        return true;
    }

    /** Removes {@code block} from its chunk's index (call it when the block is broken). Region thread. */
    public void untrack(Block block) {
        Chunk chunk = block.getChunk();
        current(block.getWorld(), chunk).ifPresent(index -> {
            LazyBlockIndex next = index.without(pos(block));
            if (next != index) {
                save(block.getWorld(), chunk, next);
            }
        });
    }

    /** The kind {@code block} is indexed as, from the cached index (empty when not cached). Region thread. */
    public Optional<String> kindAt(Block block) {
        LazyBlockIndex index = indexes.get(ChunkRef.of(block.getWorld(), block.getX() >> 4, block.getZ() >> 4));
        return index == null ? Optional.empty() : index.kindAt(pos(block));
    }

    /** The index of a loaded chunk: cached, or read from the chunk (small, on this thread). Region thread. */
    public Optional<LazyBlockIndex> index(Chunk chunk) {
        return current(chunk.getWorld(), chunk);
    }

    private Optional<LazyBlockIndex> current(World world, Chunk chunk) {
        ChunkRef ref = ChunkRef.of(world, chunk.getX(), chunk.getZ());
        LazyBlockIndex cached = indexes.get(ref);
        if (cached != null) {
            return Optional.of(cached);
        }
        LazyBlockIndex read;
        try {
            read = store.load(chunk, INDEX_KEY).orElseGet(() -> LazyBlockIndex.empty(chunk.getX(), chunk.getZ()));
        } catch (RuntimeException e) { // PersistException, or garbage the binary decoder rejects
            decodeErrors.increment();
            logger.log(Level.WARNING, "[catchup] unreadable lazy block index in " + world.getName() + " chunk "
                    + chunk.getX() + "," + chunk.getZ() + " (kept untouched): " + e.getMessage());
            return Optional.empty();
        }
        indexes.put(ref, read);
        return Optional.of(read);
    }

    private void save(World world, Chunk chunk, LazyBlockIndex index) {
        if (index.isEmpty()) {
            store.remove(chunk, INDEX_KEY);
        } else {
            store.save(chunk, INDEX_KEY, index);
        }
        indexes.put(ChunkRef.of(world, chunk.getX(), chunk.getZ()), index);
    }

    // ---- triggers ----------------------------------------------------------------------------------------------

    /**
     * Chunk load: copies the index bytes, decodes them on the worker pool and queues every indexed block for catch-up.
     * Region thread of the chunk.
     */
    public void chunkLoaded(Chunk chunk) {
        chunksLoaded.increment();
        World world = chunk.getWorld();
        int cx = chunk.getX();
        int cz = chunk.getZ();
        ChunkRef ref = ChunkRef.of(world, cx, cz);
        lastCaughtUp.put(ref, nanos.getAsLong());
        LazyBlockIndex cached = indexes.get(ref);
        if (cached != null) {
            feed(world, cached);
            return;
        }
        Optional<byte[]> raw = store.read(chunk, INDEX_KEY);
        if (raw.isEmpty()) {
            indexes.put(ref, LazyBlockIndex.empty(cx, cz));
            return;
        }
        byte[] bytes = raw.get();
        RegionExecutor regions = state(world).regions;
        CompletableFuture.supplyAsync(() -> LazyBlockIndex.CODEC.fromBytes(bytes), workers).whenComplete((index, err) -> {
            if (err != null) {
                decodeErrors.increment();
                logger.log(Level.WARNING, "[catchup] unreadable lazy block index in " + world.getName() + " chunk "
                        + cx + "," + cz + " (kept untouched)", err);
                return;
            }
            regions.run(cx, cz, () -> {
                if (!world.isChunkLoaded(cx, cz)) {
                    return; // unloaded meanwhile: the next load catches up
                }
                LazyBlockIndex live = indexes.putIfAbsent(ref, index); // a track() meanwhile read the same bytes
                feed(world, live != null ? live : index);
            });
        });
    }

    /** Chunk unload: forgets the cached index and the queued blocks. Region thread of the chunk. */
    public void chunkUnloaded(Chunk chunk) {
        World world = chunk.getWorld();
        ChunkRef ref = ChunkRef.of(world, chunk.getX(), chunk.getZ());
        flushOrphans(world, chunk.getX(), chunk.getZ());
        indexes.remove(ref);
        lastCaughtUp.remove(ref);
        WorldState w = worlds.get(world.getUID());
        if (w != null) {
            w.feeder.drop(chunk.getX(), chunk.getZ());
        }
    }

    /**
     * Interaction: catches {@code block} up now if it is indexed (one block, not budgeted, asynchronously; a kind that
     * needs the exact state inside the interaction catches up itself, as devices do). Region thread.
     *
     * @return whether the block is indexed and its kind registered
     */
    public boolean touch(Block block) {
        Optional<String> kind = kindAt(block);
        if (kind.isEmpty()) {
            return false;
        }
        touches.increment();
        return submit(block.getWorld(), pos(block), kind.get());
    }

    /**
     * Visible-area sweep of one chunk: if it was not caught up within the sweep period, queues its indexed blocks.
     * Region thread of the chunk; an unloaded chunk is skipped (never loaded).
     *
     * @return whether the chunk was due
     */
    public boolean sweep(World world, int chunkX, int chunkZ) {
        if (!world.isChunkLoaded(chunkX, chunkZ)) {
            return false;
        }
        ChunkRef ref = ChunkRef.of(world, chunkX, chunkZ);
        long now = nanos.getAsLong();
        long period = settings.sweepPeriodTicks() * TICK_NANOS;
        boolean[] due = new boolean[1];
        lastCaughtUp.compute(ref, (k, last) -> {
            // a little slack so a sweep every period is not skipped by a few nanoseconds of scheduling jitter
            due[0] = SweepArea.due(last == null ? Long.MIN_VALUE : last, now, period - TICK_NANOS);
            return due[0] ? now : last;
        });
        if (!due[0]) {
            return false;
        }
        sweeps.increment();
        LazyBlockIndex index = indexes.get(ref);
        if (index == null) {
            chunkLoaded(world.getChunkAt(chunkX, chunkZ)); // loaded before the module was enabled
            return true;
        }
        feed(world, index);
        return true;
    }

    private void feed(World world, LazyBlockIndex index) {
        if (!index.isEmpty()) {
            state(world).feeder.enqueue(index.chunkX(), index.chunkZ(), index.entries());
        }
    }

    /** Hands one block to its kind's runner. Owner thread. */
    private boolean submit(World world, BlockPos pos, String kind) {
        Optional<CatchUpKinds.Entry> entry = kinds.find(kind);
        if (entry.isEmpty()) {
            unknownKinds.increment();
            return false;
        }
        WorldState w = state(world);
        switch (entry.get()) {
            case CatchUpKinds.Sync s -> {
                if (!s.direct().catchUp(world, pos)) {
                    orphan(world, pos);
                }
            }
            case CatchUpKinds.Async<?> a -> w.pipeline(a).submit(pos);
        }
        return true;
    }

    // ---- orphans -----------------------------------------------------------------------------------------------

    /** The block at {@code pos} is no longer of its kind: drop its entry (batched per chunk). Owner thread. */
    private void orphan(World world, BlockPos pos) {
        orphanCount.increment();
        int cx = pos.chunkX();
        int cz = pos.chunkZ();
        ChunkRef ref = ChunkRef.of(world, cx, cz);
        boolean[] first = new boolean[1];
        orphans.compute(ref, (k, set) -> {
            if (set == null) {
                set = new HashSet<>();
                first[0] = true;
            }
            set.add(pos);
            return set;
        });
        if (first[0]) {
            state(world).regions.run(cx, cz, () -> flushOrphans(world, cx, cz));
        }
    }

    private void flushOrphans(World world, int cx, int cz) {
        Set<BlockPos> gone = orphans.remove(ChunkRef.of(world, cx, cz));
        if (gone == null || gone.isEmpty() || !world.isChunkLoaded(cx, cz)) {
            return;
        }
        Chunk chunk = world.getChunkAt(cx, cz);
        current(world, chunk).ifPresent(index -> {
            LazyBlockIndex next = index.without(gone);
            if (next != index) {
                save(world, chunk, next);
            }
        });
    }

    // ---- per world ---------------------------------------------------------------------------------------------

    private WorldState state(World world) {
        return worlds.computeIfAbsent(world.getUID(), id -> new WorldState(world, regionsOf.apply(world)));
    }

    /** True when nothing is queued, deciding or committing (tests, self-tests; racy). */
    public boolean isIdle() {
        for (WorldState w : worlds.values()) {
            if (!w.feeder.isEmpty()) {
                return false;
            }
            for (AsyncDecisionPipeline<?, ?> p : w.pipelines.values()) {
                if (!p.isIdle()) {
                    return false;
                }
            }
        }
        return orphans.isEmpty();
    }

    public Stats stats() {
        long fed = 0;
        long deferrals = 0;
        for (WorldState w : worlds.values()) {
            fed += w.feeder.handed.sum();
            deferrals += w.feeder.deferrals.sum();
        }
        return new Stats(chunksLoaded.sum(), sweeps.sum(), touches.sum(), trackedCount.sum(), orphanCount.sum(),
                unknownKinds.sum(), fed, deferrals, decodeErrors.sum());
    }

    /** The pipeline of an async kind in {@code world} (tests). */
    Optional<AsyncDecisionPipeline<?, ?>> pipeline(World world, String kind) {
        WorldState w = worlds.get(world.getUID());
        return w == null ? Optional.empty() : Optional.ofNullable(w.pipelines.get(kind));
    }

    private static BlockPos pos(Block block) {
        return new BlockPos(block.getX(), block.getY(), block.getZ());
    }

    private final class WorldState {
        final World world;
        final RegionExecutor regions;
        final CatchUpFeeder feeder;
        final Map<String, AsyncDecisionPipeline<?, ?>> pipelines = new ConcurrentHashMap<>();

        WorldState(World world, RegionExecutor regions) {
            this.world = world;
            this.regions = regions;
            this.feeder = new CatchUpFeeder(regions, feedBudget, world::isChunkLoaded,
                    (pos, kind) -> submit(world, pos, kind));
        }

        @SuppressWarnings("unchecked")
        <S> AsyncDecisionPipeline<S, Snap<S>> pipeline(CatchUpKinds.Async<S> async) {
            CatchUpKind<S> kind = async.kind();
            return (AsyncDecisionPipeline<S, Snap<S>>) pipelines.computeIfAbsent(kind.id(), id -> build(kind));
        }

        private <S> AsyncDecisionPipeline<S, Snap<S>> build(CatchUpKind<S> kind) {
            BlockAccess<S> access = new BlockAccess<>() {
                @Override
                public S read(BlockPos pos) {
                    return world.isChunkLoaded(pos.chunkX(), pos.chunkZ()) ? kind.read(world, pos) : null;
                }

                @Override
                public void write(BlockPos pos, S state) {
                    kind.write(world, pos, state);
                }
            };
            AsyncDecisionPipeline.SnapshotSource<Snap<S>> snapshots = (pos, snapshotNanos) -> {
                S state = null;
                if (world.isChunkLoaded(pos.chunkX(), pos.chunkZ())) {
                    state = kind.read(world, pos);
                    if (state == null) {
                        orphan(world, pos);
                    }
                }
                return CompletableFuture.completedFuture(new Snap<>(pos, state, playerTicks.getAsLong(),
                        snapshotNanos));
            };
            Function<Snap<S>, List<BlockChange<S>>> decide = snap -> {
                if (snap.state() == null) {
                    return List.of();
                }
                S next = Objects.requireNonNull(kind.advance().advance(snap.state(), snap.now()),
                        () -> kind.id() + " advance returned null");
                return next.equals(snap.state()) ? List.of()
                        : List.of(new BlockChange<>(snap.pos(), snap.state(), next, snap.snapshotNanos()));
            };
            return new AsyncDecisionPipeline<>("catchup:" + kind.id(), regions, access, workers, commitBudget,
                    snapshots, decide);
        }
    }
}
