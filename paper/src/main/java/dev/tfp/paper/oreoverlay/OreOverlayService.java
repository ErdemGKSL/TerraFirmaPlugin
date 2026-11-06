/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.oreoverlay;

import dev.tfp.core.oreoverlay.CellLookup;
import dev.tfp.core.oreoverlay.ExposedFaces;
import dev.tfp.core.oreoverlay.FastPath;
import dev.tfp.core.oreoverlay.OreOverlayTable;
import dev.tfp.core.oreoverlay.OreSnapshot;
import dev.tfp.core.oreoverlay.OverlayGate;
import dev.tfp.core.oreoverlay.OverlayKeys;
import dev.tfp.core.oreoverlay.TokenBucket;
import dev.tfp.core.oreoverlay.ViewerSet;
import dev.tfp.core.visuals.OverlayFace;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.IntUnaryOperator;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The ore-overlay runtime (PAR-INFRA-16, design {@code docs/research/ore-entity-renderer-design.md} §3): packet-only
 * {@code item_display} face overlays on the exposed faces of ore blocks, per viewer.
 *
 * <p>Threads (decide async, apply sync):
 * <ul>
 *   <li>viewer packets ({@link #chunkSent}, {@link #blockChanged}, {@link #chunkUnloaded}, {@link #center}) arrive on the
 *       viewer's Netty thread; they read only published immutable tables and the viewer's own state, and fill an
 *       {@link Outbox} the packet handler sends right after the triggering packet. No world access;</li>
 *   <li>a full table build copies the chunk on its region thread ({@link CellReader#snapshot}), computes on the CPU pool
 *       ({@link ExposedFaces#compute}), reads the few border cells it needs on the neighbours' own region threads and
 *       publishes by an atomic map swap;</li>
 *   <li>block changes are coalesced per chunk and tick; the region thread reads the changed cells (and the neighbours of
 *       new ores), the CPU pool patches the tables ({@link ExposedFaces#patch}), and each viewer gets only the difference
 *       to what it was sent. One patch per chunk is in flight at a time, so patches apply in read order.</li>
 * </ul>
 * Nothing is persisted and no Bukkit entity exists; a table lives while a viewer has its chunk.
 */
public final class OreOverlayService {
    /** How the service reaches region threads. */
    public interface Regions {
        /** Runs {@code task} on the owner of the chunk, soon (possibly later this tick). */
        void run(UUID world, int chunkX, int chunkZ, Runnable task);

        /** Runs {@code task} on the owner of the chunk next tick (coalescing). */
        void nextTick(UUID world, int chunkX, int chunkZ, Runnable task);
    }

    record Key(UUID world, long chunk) {
        int chunkX() {
            return OverlayKeys.chunkX(chunk);
        }

        int chunkZ() {
            return OverlayKeys.chunkZ(chunk);
        }
    }

    /** One viewer: what it has loaded and was sent. Guarded by itself. */
    static final class Viewer {
        final UUID id;
        volatile UUID world;
        volatile int centerX;
        volatile int centerZ;
        final Set<Long> loaded = new HashSet<>();
        final Set<Long> deferred = new HashSet<>();
        final Map<Integer, int[]> riders = new HashMap<>();
        /** Crack stage this viewer's client draws per block of its world: {@code block -> stage << 32 | tick}. */
        final Map<Long, Long> cracks = new HashMap<>();
        final ViewerSet set;
        final TokenBucket bucket = TokenBucket.defaults();

        Viewer(UUID id, int cap) {
            this.id = id;
            this.set = new ViewerSet(cap);
        }

        double vx() {
            return (centerX << 4) + 8;
        }

        double vz() {
            return (centerZ << 4) + 8;
        }
    }

    /** Counters for {@code /tfp debug oreoverlay stats}. */
    public record Stats(int tables, long tableFaces, long tableOres, long builds, long buildNanos, long maxBuildNanos,
            long snapshotNanos,
            long patches, long fastPathChanges, int viewers, int viewerSent, int viewerLoaded, int viewerDeferred,
            int viewerCap, int radiusChunks) {
    }

    private final OreOverlayData data;
    private final CellReader reader;
    private final Regions regions;
    private final Executor cpu;
    private final OverlaySender sender;
    private final OverlayGate gate;
    private final int cap;
    private final float fullRange;
    private final IntUnaryOperator networkState;
    private final Logger log;

    private final Map<Key, OreOverlayTable> tables = new ConcurrentHashMap<>();
    private final Set<Key> building = ConcurrentHashMap.newKeySet();
    private final Map<Key, Set<Long>> dirty = new ConcurrentHashMap<>();
    private final Set<Key> scheduled = ConcurrentHashMap.newKeySet();
    private final Set<Key> patching = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Viewer> viewers = new ConcurrentHashMap<>();
    private final Map<String, UUID> worldIds = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> falling = new ConcurrentHashMap<>();
    private final AtomicLong tick = new AtomicLong();
    private final LongAdder builds = new LongAdder();
    private final LongAdder buildNanos = new LongAdder();
    private final AtomicLong maxBuildNanos = new AtomicLong();
    private final LongAdder patches = new LongAdder();
    private final LongAdder snapshotNanos = new LongAdder();
    private final LongAdder fastPath = new LongAdder();

    /**
     * @param networkState {@link OreBlockSource#networkState} bound to the data's kinds
     * @param fullRange    view range of surface outcrops ({@link OverlayGate#fullViewRange})
     */
    public OreOverlayService(OreOverlayData data, CellReader reader, Regions regions, Executor cpu, OverlaySender sender,
            OverlayGate gate, int cap, float fullRange, IntUnaryOperator networkState, Logger log) {
        this.data = data;
        this.reader = reader;
        this.regions = regions;
        this.cpu = cpu;
        this.sender = sender;
        this.gate = gate;
        this.cap = cap;
        this.fullRange = fullRange;
        this.networkState = networkState;
        this.log = log;
    }

    public OreOverlayData data() {
        return data;
    }

    public OverlayGate gate() {
        return gate;
    }

    // ---------------------------------------------------------------- worlds and viewers

    /** Registers a world name (worlds load at startup on Folia). Any thread. */
    public void world(String name, UUID id) {
        worldIds.put(name, id);
    }

    /** The id of a world by name, or null. */
    public UUID worldId(String name) {
        return name == null ? null : worldIds.get(name);
    }

    private Viewer viewer(UUID id) {
        return viewers.computeIfAbsent(id, k -> new Viewer(k, cap));
    }

    /**
     * The viewer's client starts a level ({@code LOGIN}/{@code RESPAWN}): it dropped every entity, so the sent state is
     * forgotten without removals. Any thread.
     */
    public void viewerWorld(UUID viewer, UUID world) {
        Viewer v = viewer(viewer);
        synchronized (v) {
            v.set.clear();
            v.loaded.clear();
            v.deferred.clear();
            v.riders.clear();
            v.cracks.clear();
            v.world = world;
        }
    }

    /** The viewer left: its state goes, and tables nobody else has go with it. Any thread. */
    public void quit(UUID viewer) {
        Viewer v = viewers.remove(viewer);
        if (v == null) {
            return;
        }
        Set<Long> had;
        synchronized (v) {
            had = Set.copyOf(v.loaded);
        }
        UUID w = v.world;
        if (w != null) {
            for (long c : had) {
                dropIfUnwatched(new Key(w, c));
            }
        }
    }

    /** The viewer's chunk centre moved ({@code SET_CHUNK_CACHE_CENTER}); chunks crossing the radius change faces. */
    public void center(UUID viewer, int chunkX, int chunkZ, Outbox out) {
        Viewer v = viewer(viewer);
        UUID w = v.world;
        synchronized (v) {
            int oldX = v.centerX;
            int oldZ = v.centerZ;
            v.centerX = chunkX;
            v.centerZ = chunkZ;
            if (w == null) {
                return;
            }
            for (long c : v.loaded) {
                int cx = OverlayKeys.chunkX(c);
                int cz = OverlayKeys.chunkZ(c);
                if (gate.near(oldX, oldZ, cx, cz) != gate.near(chunkX, chunkZ, cx, cz)) {
                    OreOverlayTable t = tables.get(new Key(w, c));
                    if (t != null) {
                        reconcile(v, t, out);
                    }
                }
            }
        }
    }

    /** The viewer is being sent a chunk ({@code CHUNK_DATA}): its faces go right after it, or a build is queued. */
    public void chunkSent(UUID viewer, int chunkX, int chunkZ, Outbox out) {
        Viewer v = viewer(viewer);
        UUID w = v.world;
        if (w == null) {
            return;
        }
        long c = OverlayKeys.chunk(chunkX, chunkZ);
        Key key = new Key(w, c);
        OreOverlayTable t = tables.get(key);
        synchronized (v) {
            v.loaded.add(c);
            if (t != null) {
                // A resend replaces the client's chunk but not its entities: reconcile against what was sent.
                reconcile(v, t, out);
                return;
            }
        }
        requestBuild(key);
    }

    /** The viewer unloads a chunk ({@code UNLOAD_CHUNK}): one removal with all its overlay ids. */
    public void chunkUnloaded(UUID viewer, int chunkX, int chunkZ, Outbox out) {
        Viewer v = viewers.get(viewer);
        if (v == null) {
            return;
        }
        long c = OverlayKeys.chunk(chunkX, chunkZ);
        synchronized (v) {
            v.loaded.remove(c);
            v.deferred.remove(c);
            out.remove(v.set.dropChunk(c));
        }
        UUID w = v.world;
        if (w != null) {
            dropIfUnwatched(new Key(w, c));
        }
    }

    /** The server unloaded a chunk: its table goes (recomputed at the next send). Any thread. */
    public void serverChunkUnloaded(UUID world, int chunkX, int chunkZ) {
        Key key = new Key(world, OverlayKeys.chunk(chunkX, chunkZ));
        tables.remove(key);
        dirty.remove(key);
    }

    private void dropIfUnwatched(Key key) {
        for (Viewer o : viewers.values()) {
            if (key.world().equals(o.world)) {
                synchronized (o) {
                    if (o.loaded.contains(key.chunk())) {
                        return;
                    }
                }
            }
        }
        tables.remove(key);
    }

    /**
     * The viewer is being sent a block change ({@code BLOCK_CHANGE}, one entry of {@code MULTI_BLOCK_CHANGE}): the
     * provisional face changes ({@link FastPath}) go right after it, and the cell is queued for the authoritative patch.
     */
    public void blockChanged(UUID viewer, int x, int y, int z, int stateId, Outbox out) {
        Viewer v = viewers.get(viewer);
        UUID w = v == null ? null : v.world;
        if (w == null) {
            return;
        }
        // The authoritative patch first: whatever the fast path does, the table follows the world.
        markDirty(w, x, y, z);
        int info = networkState.applyAsInt(stateId);
        if (info >= 0) {
            boolean occludes = (info & OreBlockSource.OCCLUDES) != 0;
            int kind = (info >> 1) - 1;
            int known = kind >= 0 ? kind : occludes ? FastPath.UNKNOWN_ORE : FastPath.NOT_ORE;
            List<FastPath.FaceChange> changes = FastPath.decide(
                    (cx, cz) -> tables.get(new Key(w, OverlayKeys.chunk(cx, cz))), x, y, z, occludes, known);
            if (!changes.isEmpty()) {
                int minY = reader.minY(w);
                synchronized (v) {
                    int spawned = 0;
                    for (FastPath.FaceChange ch : changes) {
                        long chunk = OverlayKeys.chunk(ch.x() >> 4, ch.z() >> 4);
                        if (!v.loaded.contains(chunk)) {
                            continue;
                        }
                        boolean outcrop = ch.outcrop() && gate.outcropsFullRange();
                        if (ch.visible() && !outcrop && !gate.near(v.centerX, v.centerZ, ch.x() >> 4, ch.z() >> 4)) {
                            continue;
                        }
                        int key = OverlayKeys.face(OverlayKeys.local(ch.x(), ch.y(), ch.z(), minY), ch.face().ordinal());
                        ViewerSet.Plan p = v.set.change(chunk, key, ch.kind(), outcrop, ch.visible(),
                                sender::nextEntityId);
                        out.remove(p.removes());
                        for (ViewerSet.Spawn s : p.spawns()) {
                            out.spawn(spawn(v, s, minY));
                            spawned++;
                        }
                    }
                    v.bucket.charge(spawned, tick.get());
                }
                fastPath.add(changes.size());
            }
        }
    }

    // ---------------------------------------------------------------- reconciliation

    /** Makes the viewer's overlays of the table's chunk what it should see. Caller holds the viewer's lock. */
    private void reconcile(Viewer v, OreOverlayTable t, Outbox out) {
        boolean near = gate.near(v.centerX, v.centerZ, t.chunkX(), t.chunkZ());
        List<ViewerSet.Wanted> wanted = gate.wanted(t, near, v.vx(), 64, v.vz());
        long now = tick.get();
        int budget = (int) Math.min(Integer.MAX_VALUE, v.bucket.available(now));
        ViewerSet.Plan p = v.set.reconcile(t.chunkKey(), wanted, sender::nextEntityId, budget);
        out.remove(p.removes());
        for (ViewerSet.Spawn s : p.spawns()) {
            out.spawn(spawn(v, s, t.minY()));
        }
        v.bucket.charge(p.spawns().size(), now);
        if (p.deferred()) {
            v.deferred.add(t.chunkKey());
        } else {
            v.deferred.remove(t.chunkKey());
        }
    }

    /** Caller holds the viewer's lock. */
    private OverlaySender.Spawn spawn(Viewer v, ViewerSet.Spawn s, int minY) {
        int local = OverlayKeys.faceLocal(s.faceKey());
        int x = (OverlayKeys.chunkX(s.chunk()) << 4) | OverlayKeys.localX(local);
        int y = OverlayKeys.localY(local, minY);
        int z = (OverlayKeys.chunkZ(s.chunk()) << 4) | OverlayKeys.localZ(local);
        OverlayFace face = OverlayFace.values()[OverlayKeys.faceOrdinal(s.faceKey())];
        Long crack = v.cracks.get(OverlayKeys.block(x, y, z));
        int stage = crack == null ? -1 : (int) (crack >> 32);
        return new OverlaySender.Spawn(s.entityId(), x, y, z, face, data.itemModel(s.kind(), stage),
                s.outcrop() ? fullRange : gate.viewRange());
    }

    /** Sends every viewer of the table's chunk the difference to the table. Any thread. */
    private void reconcileViewers(UUID world, OreOverlayTable t) {
        for (Viewer v : viewers.values()) {
            if (!world.equals(v.world)) {
                continue;
            }
            Outbox out = new Outbox(v.id);
            synchronized (v) {
                if (!v.loaded.contains(t.chunkKey())) {
                    continue;
                }
                reconcile(v, t, out);
            }
            out.flush(sender);
        }
    }

    /** Once per server tick (global): advances the rate limiter clock and sends the faces the limit held back. */
    public void tick() {
        long now = tick.incrementAndGet();
        for (Viewer v : viewers.values()) {
            UUID w = v.world;
            if (w == null) {
                continue;
            }
            Outbox out = new Outbox(v.id);
            synchronized (v) {
                if (!v.cracks.isEmpty()) {
                    expireCracks(v, w, now);
                }
                if (v.deferred.isEmpty() || v.bucket.available(now) == 0) {
                    continue;
                }
                List<Long> order = new ArrayList<>(v.deferred);
                order.sort(Comparator.comparingLong(c -> {
                    long dx = OverlayKeys.chunkX(c) - v.centerX;
                    long dz = OverlayKeys.chunkZ(c) - v.centerZ;
                    return dx * dx + dz * dz;
                }));
                for (long c : order) {
                    if (v.bucket.available(now) == 0) {
                        break;
                    }
                    OreOverlayTable t = tables.get(new Key(w, c));
                    if (t == null) {
                        v.deferred.remove(c);
                        continue;
                    }
                    reconcile(v, t, out);
                }
            }
            out.flush(sender);
        }
    }

    // ---------------------------------------------------------------- full builds

    /** Queues a full build of a chunk unless one is in flight. Any thread. */
    void requestBuild(Key key) {
        if (!building.add(key)) {
            return;
        }
        regions.run(key.world(), key.chunkX(), key.chunkZ(), () -> snapshotForBuild(key));
    }

    /** Region thread of the chunk. */
    private void snapshotForBuild(Key key) {
        try {
            if (!reader.loaded(key.world(), key.chunkX(), key.chunkZ())) {
                building.remove(key);
                return;
            }
            long start = System.nanoTime();
            OreSnapshot s = reader.snapshot(key.world(), key.chunkX(), key.chunkZ());
            snapshotNanos.add(System.nanoTime() - start);
            cpu.execute(() -> computeBuild(key, s));
        } catch (RuntimeException e) {
            building.remove(key);
            log.log(Level.WARNING, "Ore overlay: chunk snapshot failed at " + key, e);
        }
    }

    /** CPU pool. */
    private void computeBuild(Key key, OreSnapshot s) {
        long start = System.nanoTime();
        long[] needs = ExposedFaces.borderNeeds(s);
        long spent = System.nanoTime() - start;
        if (needs.length == 0) {
            finishBuild(key, s, CellMap.EMPTY, spent);
            return;
        }
        readCells(key.world(), needs).whenCompleteAsync((cells, error) -> {
            if (error != null) {
                building.remove(key);
                log.log(Level.WARNING, "Ore overlay: border read failed at " + key, error);
                return;
            }
            finishBuild(key, s, cells, spent);
        }, cpu);
    }

    /** CPU pool. {@code spent}: CPU time of the build so far (the border read on the region threads not counted). */
    private void finishBuild(Key key, OreSnapshot s, CellLookup border, long spent) {
        try {
            long start = System.nanoTime();
            OreOverlayTable t = ExposedFaces.compute(s, border);
            long took = spent + System.nanoTime() - start;
            builds.increment();
            buildNanos.add(took);
            maxBuildNanos.accumulateAndGet(took, Math::max);
            tables.put(key, t);
            reconcileViewers(key.world(), t);
        } finally {
            building.remove(key);
        }
    }

    /** Reads cells on their owners' region threads; completes with all of them (missing chunks: unknown). */
    private CompletableFuture<CellMap> readCells(UUID world, long[] blocks) {
        Map<Long, List<Long>> byChunk = new HashMap<>();
        for (long b : blocks) {
            byChunk.computeIfAbsent(OverlayKeys.chunk(OverlayKeys.blockX(b) >> 4, OverlayKeys.blockZ(b) >> 4),
                    k -> new ArrayList<>()).add(b);
        }
        List<CompletableFuture<CellMap>> parts = new ArrayList<>();
        for (Map.Entry<Long, List<Long>> e : byChunk.entrySet()) {
            int cx = OverlayKeys.chunkX(e.getKey());
            int cz = OverlayKeys.chunkZ(e.getKey());
            CompletableFuture<CellMap> f = new CompletableFuture<>();
            parts.add(f);
            regions.run(world, cx, cz, () -> {
                try {
                    CellMap.Builder b = new CellMap.Builder();
                    if (reader.loaded(world, cx, cz)) {
                        for (long p : e.getValue()) {
                            b.put(p, reader.cell(world, OverlayKeys.blockX(p), OverlayKeys.blockY(p), OverlayKeys.blockZ(p)));
                        }
                    }
                    f.complete(b.build());
                } catch (RuntimeException ex) {
                    f.completeExceptionally(ex);
                }
            });
        }
        return CompletableFuture.allOf(parts.toArray(CompletableFuture[]::new)).thenApply(x -> {
            CellMap.Builder all = new CellMap.Builder();
            parts.forEach(p -> all.putAll(p.join()));
            return all.build();
        });
    }

    // ---------------------------------------------------------------- authoritative patches

    /** Queues a changed cell for the patch of its chunk next tick (coalesced per chunk). Any thread. */
    public void markDirty(UUID world, int x, int y, int z) {
        Key key = new Key(world, OverlayKeys.chunk(x >> 4, z >> 4));
        dirty.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet()).add(OverlayKeys.block(x, y, z));
        if (scheduled.add(key)) {
            regions.nextTick(world, key.chunkX(), key.chunkZ(), () -> runPatch(key));
        }
    }

    /** Region thread of the chunk: reads the changed cells and hands the patch to the CPU pool. */
    private void runPatch(Key key) {
        scheduled.remove(key);
        if (!patching.add(key)) {
            // The previous patch of this chunk is still in flight: keep the cells dirty and come back next tick.
            if (dirty.containsKey(key) && scheduled.add(key)) {
                regions.nextTick(key.world(), key.chunkX(), key.chunkZ(), () -> runPatch(key));
            }
            return;
        }
        Set<Long> cellsDirty = dirty.remove(key);
        if (cellsDirty == null || cellsDirty.isEmpty() || !reader.loaded(key.world(), key.chunkX(), key.chunkZ())) {
            patching.remove(key);
            return;
        }
        try {
            UUID w = key.world();
            long[] changed = cellsDirty.stream().mapToLong(Long::longValue).toArray();
            CellMap.Builder cells = new CellMap.Builder();
            List<Long> foreign = new ArrayList<>();
            for (long p : changed) {
                int x = OverlayKeys.blockX(p);
                int y = OverlayKeys.blockY(p);
                int z = OverlayKeys.blockZ(p);
                long cell = reader.cell(w, x, y, z);
                cells.put(p, cell);
                if (CellMap.codeOf(cell) >= 0) {
                    for (long n : ExposedFaces.neighbours(x, y, z)) {
                        int nx = OverlayKeys.blockX(n);
                        int ny = OverlayKeys.blockY(n);
                        int nz = OverlayKeys.blockZ(n);
                        if (ny < reader.minY(w) || ny >= reader.maxY(w) || cells.has(n)) {
                            continue;
                        }
                        if ((nx >> 4) == key.chunkX() && (nz >> 4) == key.chunkZ()) {
                            cells.put(n, reader.cell(w, nx, ny, nz));
                        } else {
                            foreign.add(n);
                        }
                    }
                }
            }
            CellMap own = cells.build();
            if (foreign.isEmpty()) {
                cpu.execute(() -> applyPatch(key, changed, own));
            } else {
                readCells(w, foreign.stream().mapToLong(Long::longValue).toArray()).whenCompleteAsync((more, error) -> {
                    if (error != null) {
                        patching.remove(key);
                        log.log(Level.WARNING, "Ore overlay: neighbour read failed at " + key, error);
                        return;
                    }
                    applyPatch(key, changed, new CellMap.Builder().putAll(own).putAll(more).build());
                }, cpu);
            }
        } catch (RuntimeException e) {
            patching.remove(key);
            throw e;
        }
    }

    /** CPU pool: patches every table the changed cells touch and sends the viewers the difference. */
    private void applyPatch(Key key, long[] changed, CellMap cells) {
        try {
            UUID w = key.world();
            int maxY = reader.maxY(w);
            clearCracks(w, changed, cells);
            Set<Long> touched = new HashSet<>();
            for (long p : changed) {
                int x = OverlayKeys.blockX(p);
                int z = OverlayKeys.blockZ(p);
                touched.add(OverlayKeys.chunk(x >> 4, z >> 4));
                for (OverlayFace f : OverlayFace.values()) {
                    touched.add(OverlayKeys.chunk((x + f.dx()) >> 4, (z + f.dz()) >> 4));
                }
            }
            for (long c : touched) {
                Key k = new Key(w, c);
                OreOverlayTable t = tables.computeIfPresent(k, (kk, old) -> ExposedFaces.patch(old, maxY, cells, changed));
                if (t != null) {
                    patches.increment();
                    reconcileViewers(w, t);
                } else if (building.contains(k)) {
                    // The build snapshot may predate the change: patch again once it is published.
                    for (long p : changed) {
                        markDirty(w, OverlayKeys.blockX(p), OverlayKeys.blockY(p), OverlayKeys.blockZ(p));
                    }
                }
            }
        } finally {
            patching.remove(key);
        }
    }

    // ---------------------------------------------------------------- mining cracks

    /** Ticks after which a vanilla client drops a crumbling overlay nobody updated ({@code LevelRenderer}: 400). */
    static final int CRACK_EXPIRY_TICKS = 400;

    /**
     * The server's dig progress of a block changed ({@code BlockBreakProgressUpdateEvent}, region thread): every viewer's
     * overlays of that block swap to the cracked variant of {@code stage} (0..9), or back to the plain overlay
     * ({@code stage < 0}). This covers the breaker, whose own client draws its cracks without a packet.
     */
    public void crack(UUID world, int x, int y, int z, int stage) {
        for (Viewer v : viewers.values()) {
            if (world.equals(v.world)) {
                synchronized (v) {
                    applyCrack(v, world, x, y, z, stage);
                }
            }
        }
    }

    /**
     * The viewer's client was sent a crumbling stage ({@code BLOCK_DESTRUCTION}, Netty, after the packet): its overlays of
     * that block follow. CraftEngine runs its own dig progress for custom blocks and sends this packet itself, so no
     * server event fires for it (the hand-mining case of the in-game run). Stages outside 0..9 clear it, as on the client.
     */
    public void crackSeen(UUID viewer, int x, int y, int z, int stage) {
        Viewer v = viewers.get(viewer);
        UUID w = v == null ? null : v.world;
        if (w == null) {
            return;
        }
        synchronized (v) {
            applyCrack(v, w, x, y, z, stage >= 0 && stage <= 9 ? stage : -1);
        }
    }

    /** Caller holds the viewer's lock. */
    private void applyCrack(Viewer v, UUID world, int x, int y, int z, int stage) {
        long block = OverlayKeys.block(x, y, z);
        Long old = stage < 0 ? v.cracks.remove(block) : v.cracks.put(block, ((long) stage << 32) | (tick.get() & 0xFFFFFFFFL));
        if (old == null ? stage < 0 : (int) (old >> 32) == stage) {
            return;
        }
        morphBlock(v, world, x, y, z, stage);
    }

    /**
     * Morphs the viewer's overlays of one block to the stage's variant. Caller holds the viewer's lock. Runs on the
     * block's region thread (server event), a Netty thread (destruction packet) or the global tick thread (expiry), so
     * {@link OverlaySender#morph} must not depend on the calling thread.
     */
    private void morphBlock(Viewer v, UUID world, int x, int y, int z, int stage) {
        OreOverlayTable t = tables.get(new Key(world, OverlayKeys.chunk(x >> 4, z >> 4)));
        int i = t == null ? -1 : t.find(x, y, z);
        if (i < 0 || t.mask(i) == 0) {
            return;
        }
        int local = t.local(i);
        for (OverlayFace f : OverlayFace.values()) {
            int key = OverlayKeys.face(local, f.ordinal());
            int id = v.set.entity(t.chunkKey(), key);
            if (id >= 0) {
                sender.morph(v.id, id, f, data.itemModel(v.set.kind(t.chunkKey(), key), stage));
            }
        }
    }

    /**
     * Drops the stages no update refreshed for {@link #CRACK_EXPIRY_TICKS}, as the client does. Caller holds the lock.
     * Runs on the global tick thread ({@link #tick}): the morphs it sends go through the thread-agnostic sender.
     */
    private void expireCracks(Viewer v, UUID world, long now) {
        for (Iterator<Map.Entry<Long, Long>> it = v.cracks.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Long, Long> e = it.next();
            long at = e.getValue() & 0xFFFFFFFFL;
            if (((now - at) & 0xFFFFFFFFL) > CRACK_EXPIRY_TICKS) {
                it.remove();
                long b = e.getKey();
                morphBlock(v, world, OverlayKeys.blockX(b), OverlayKeys.blockY(b), OverlayKeys.blockZ(b), -1);
            }
        }
    }

    /** A patch saw cells that are no ore any more (broken, exploded, filled): their stale stages go. Any thread. */
    private void clearCracks(UUID world, long[] changed, CellMap cells) {
        for (Viewer v : viewers.values()) {
            if (!world.equals(v.world)) {
                continue;
            }
            synchronized (v) {
                if (v.cracks.isEmpty()) {
                    continue;
                }
                for (long p : changed) {
                    int code = cells.code(OverlayKeys.blockX(p), OverlayKeys.blockY(p), OverlayKeys.blockZ(p));
                    if (code < 0 && code != CellLookup.UNKNOWN) {
                        v.cracks.remove(p);
                    }
                }
            }
        }
    }

    /** The crack stage the viewer's overlays of a block show, or -1 (tests). */
    int crackStage(UUID viewer, int x, int y, int z) {
        Viewer v = viewers.get(viewer);
        if (v == null) {
            return -1;
        }
        synchronized (v) {
            Long c = v.cracks.get(OverlayKeys.block(x, y, z));
            return c == null ? -1 : (int) (c >> 32);
        }
    }

    // ---------------------------------------------------------------- falling ores

    /** A falling block entity carrying an ore spawned (region thread): its overlays ride it for every viewer. */
    public void fallingSpawned(int entityId, int kind) {
        falling.put(entityId, kind);
    }

    public void fallingRemoved(int entityId) {
        falling.remove(entityId);
    }

    /** True while any falling ore entity exists (the packet handler skips spawn decoding otherwise). */
    public boolean hasFalling() {
        return !falling.isEmpty();
    }

    /** True if the viewer has overlays riding a falling ore. */
    public boolean hasRiders(UUID viewer) {
        Viewer v = viewers.get(viewer);
        if (v == null) {
            return false;
        }
        synchronized (v) {
            return !v.riders.isEmpty();
        }
    }

    /** The ore kind of a falling block entity, or -1. */
    public int fallingKind(int entityId) {
        Integer k = falling.get(entityId);
        return k == null ? -1 : k;
    }

    /** Netty, right after the viewer got the falling block's spawn: six overlays ride it. */
    public void rideFalling(UUID viewer, int entityId, double x, double y, double z) {
        int kind = fallingKind(entityId);
        Viewer v = viewers.get(viewer);
        if (kind < 0 || v == null) {
            return;
        }
        List<OverlaySender.Rider> riders = new ArrayList<>(6);
        int[] ids = new int[6];
        for (OverlayFace f : OverlayFace.values()) {
            int id = sender.nextEntityId();
            ids[f.ordinal()] = id;
            riders.add(new OverlaySender.Rider(id, f, data.itemModel(kind, -1), gate.viewRange()));
        }
        int[] old;
        synchronized (v) {
            old = v.riders.put(entityId, ids);
        }
        if (old != null) {
            sender.remove(viewer, old);
        }
        sender.ride(viewer, entityId, x, y, z, riders);
    }

    /** Netty: the viewer is told to remove entities; the overlays riding any of them go too. */
    public void entitiesRemoved(UUID viewer, int[] entityIds, Outbox out) {
        Viewer v = viewers.get(viewer);
        if (v == null) {
            return;
        }
        synchronized (v) {
            if (v.riders.isEmpty()) {
                return;
            }
            for (int id : entityIds) {
                int[] r = v.riders.remove(id);
                if (r != null) {
                    out.remove(r);
                }
            }
        }
    }

    // ---------------------------------------------------------------- inspection

    /** The published table of a chunk, or null. Any thread. */
    public OreOverlayTable table(UUID world, int chunkX, int chunkZ) {
        return tables.get(new Key(world, OverlayKeys.chunk(chunkX, chunkZ)));
    }

    /** Builds a chunk's table now (debug, self-test); completes when it is published. */
    public CompletableFuture<OreOverlayTable> rebuild(UUID world, int chunkX, int chunkZ) {
        Key key = new Key(world, OverlayKeys.chunk(chunkX, chunkZ));
        tables.remove(key);
        requestBuild(key);
        return awaitTable(key, 400);
    }

    private CompletableFuture<OreOverlayTable> awaitTable(Key key, int polls) {
        CompletableFuture<OreOverlayTable> f = new CompletableFuture<>();
        poll(key, polls, f);
        return f;
    }

    private void poll(Key key, int left, CompletableFuture<OreOverlayTable> f) {
        OreOverlayTable t = tables.get(key);
        if (t != null && !building.contains(key) && !scheduled.contains(key) && !patching.contains(key)) {
            f.complete(t);
            return;
        }
        if (left <= 0) {
            f.completeExceptionally(new IllegalStateException("no table for " + key + " in time"));
            return;
        }
        regions.nextTick(key.world(), key.chunkX(), key.chunkZ(), () -> poll(key, left - 1, f));
    }

    /** Completes once the chunk's table is published and no patch of it is pending (self-test). */
    public CompletableFuture<OreOverlayTable> settled(UUID world, int chunkX, int chunkZ) {
        return awaitTable(new Key(world, OverlayKeys.chunk(chunkX, chunkZ)), 400);
    }

    /** True when no build, patch or dirty cell is pending (self-test, tests). */
    public boolean idle() {
        return building.isEmpty() && scheduled.isEmpty() && patching.isEmpty() && dirty.isEmpty();
    }

    /** Counters, plus one viewer's numbers ({@code viewer} known) or the sum over all viewers ({@code null}). */
    public Stats stats(UUID viewer) {
        long faces = 0;
        long ores = 0;
        for (OreOverlayTable t : tables.values()) {
            faces += t.faceCount();
            ores += t.size();
        }
        int sent = 0;
        int loaded = 0;
        int deferred = 0;
        for (Viewer v : viewer == null ? viewers.values() : Collections.singleton(viewers.get(viewer))) {
            if (v == null) {
                continue;
            }
            synchronized (v) {
                sent += v.set.total();
                loaded += v.loaded.size();
                deferred += v.deferred.size();
            }
        }
        return new Stats(tables.size(), faces, ores, builds.sum(), buildNanos.sum(), maxBuildNanos.get(),
                snapshotNanos.sum(), patches.sum(),
                fastPath.sum(), viewers.size(), sent, loaded, deferred, cap, gate.radiusChunks());
    }

    /** The overlay entity ids one viewer holds now (self-test leftovers check). */
    public int sentCount(UUID viewer) {
        Viewer v = viewers.get(viewer);
        if (v == null) {
            return 0;
        }
        synchronized (v) {
            return v.set.total();
        }
    }
}
