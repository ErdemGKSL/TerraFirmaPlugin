/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/recipes/CollapseRecipe.java @ c3b4d9536 (tryTriggerCollapse, startCollapse,
 * the sounds and the fake-collapse effects), src/main/java/net/dries007/tfc/util/tracker/WorldTracker.java (the collapses in
 * progress and their propagation in tick)
 */
package dev.tfp.paper.collapse;

import dev.tfp.core.collapse.CollapseKinds;
import dev.tfp.core.collapse.CollapseRandom;
import dev.tfp.core.collapse.CollapseRules;
import dev.tfp.core.collapse.CollapseView;
import dev.tfp.core.collapse.SupportTable;
import dev.tfp.core.collapse.TriggerDecision;
import dev.tfp.core.world.BlockPos;
import dev.tfp.paper.pipeline.BlockChange;
import dev.tfp.paper.pipeline.CommitBudget;
import dev.tfp.paper.pipeline.PipelineMetrics;
import dev.tfp.paper.pipeline.RegionCommitQueue;
import dev.tfp.paper.pipeline.RegionExecutor;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.SplittableRandom;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * PAR-PHYS-02: TFC's mining collapses, decide async, apply sync. One commit queue per world.
 *
 * <pre>
 * trigger(block)  owner thread of the mined block, before it breaks: tag filter, TFC's isAreaLoaded(pos, 32), then a
 *                 snapshot of the 9 x 5 x 9 search box (plus the block below it and the support reach)
 * decide          worker: {@link CollapseRules#trigger}; a real collapse draws its radius next
 * fake            owner of the mined block: the fake sound and the break particles of the displayed starts
 * real            snapshot of the columns within the radius around the start (4 below to 4 above), captured in slices
 *                 of {@link #SLICE} blocks per tick on each chunk's owner; worker: {@link CollapseRules#start}
 * apply           each chunk's owner under the {@link CommitBudget}: the commit queue drops a change whose block changed;
 *                 {@link CollapseBlocks} breaks the block below when told, removes the block and starts the falling
 *                 rubble (PAR-PHYS-01)
 * propagate       TFC's tracker advances a collapse on a tick with nextInt(10) == 0: here a delayed task per collapse,
 *                 after a delay drawn the same way, once the commits are applied; snapshot of the next positions and
 *                 the blocks below them, worker {@link CollapseRules#propagate}, apply, again until nothing falls
 * </pre>
 * Every snapshot reads positions in other regions on their owners (Folia), so a collapse crossing a region border is
 * decided as one and applied per chunk on each owner. Nothing is persisted: a collapse in progress when the server stops
 * ends there, where TFC saves the list in the level data.
 */
public final class CollapseService {
    /** Commit budget per region per tick (ARCHITECTURE.md: 1 ms). */
    public static final long BUDGET_NANOS = 1_000_000L;
    /** Blocks read per region task while capturing a large snapshot; the rest continues next tick. */
    public static final int SLICE = 2048;
    /** TFC's {@code level.isAreaLoaded(pos, 32)} before a trigger. */
    public static final int LOADED_RADIUS = 32;
    /** Ticks a propagation waits at most for the previous phase's commits before it reads anyway. */
    static final int MAX_COMMIT_WAIT = 40;
    /** The trigger's search box: TFC's radius draws reach 4, 2 and 4. */
    static final int SEARCH_XZ = 4;
    static final int SEARCH_Y = 2;
    /** What an unloaded position reads as. */
    static final String UNLOADED = "tfp:unloaded";

    private static final Logger LOG = Logger.getLogger("tfp");

    private final Function<World, RegionExecutor> regions;
    private final CollapseWorld blocks;
    private final Scheduler scheduler;
    private final Executor workers;
    private final CommitBudget budget;
    private final Supplier<SupportTable> supports;
    private final Supplier<CollapseRandom> randoms;
    private final CollapseConfig config;
    private final Map<UUID, WorldRunner> runners = new ConcurrentHashMap<>();
    private final AtomicLong inFlight = new AtomicLong();
    private final LongAdder triggers = new LongAdder();
    private final LongAdder real = new LongAdder();
    private final LongAdder fake = new LongAdder();
    private final LongAdder explosions = new LongAdder();
    private final LongAdder propagations = new LongAdder();
    private final LongAdder decided = new LongAdder();

    private record WorldRunner(World world, RegionExecutor regions, CollapseBlocks blocks,
            RegionCommitQueue<CollapseState> queue, PipelineMetrics metrics) {}

    /** A captured snapshot: unknown positions read as {@link CollapseState.Cell#UNLOADED}. */
    record Snapshot(Map<BlockPos, CollapseState.Cell> cells, long capturedNanos) {
        CollapseState.Cell at(BlockPos pos) {
            return cells.getOrDefault(pos, CollapseState.Cell.UNLOADED);
        }

        CollapseView view() {
            return pos -> at(pos).toCore();
        }
    }

    /**
     * @param supports the support blocks (TFC {@code Support}); empty until PAR-PHYS-04 adds the beams
     * @param randoms  a fresh generator per trigger; each collapse uses its own from the trigger to its last step
     */
    public CollapseService(Function<World, RegionExecutor> regions, CollapseWorld blocks, Scheduler scheduler,
            Executor workers, CommitBudget budget, Supplier<SupportTable> supports, Supplier<CollapseRandom> randoms) {
        this(regions, blocks, scheduler, workers, budget, supports, randoms, CollapseConfig.DEFAULT);
    }

    /** As above with TFC's server collapse options ({@link CollapseConfig}). */
    public CollapseService(Function<World, RegionExecutor> regions, CollapseWorld blocks, Scheduler scheduler,
            Executor workers, CommitBudget budget, Supplier<SupportTable> supports, Supplier<CollapseRandom> randoms,
            CollapseConfig config) {
        this.config = Objects.requireNonNull(config, "config");
        this.regions = regions;
        this.blocks = blocks;
        this.scheduler = scheduler;
        this.workers = workers;
        this.budget = budget;
        this.supports = supports;
        this.randoms = randoms;
    }

    /** The server one: draws from a {@link SplittableRandom} per collapse. */
    public CollapseService(Function<World, RegionExecutor> regions, CollapseWorld blocks, Scheduler scheduler,
            Executor workers, CommitBudget budget, Supplier<SupportTable> supports) {
        this(regions, blocks, scheduler, workers, budget, supports, CollapseService::splittable);
    }

    /** The server one with the configured options. */
    public CollapseService(Function<World, RegionExecutor> regions, CollapseWorld blocks, Scheduler scheduler,
            Executor workers, CommitBudget budget, Supplier<SupportTable> supports, CollapseConfig config) {
        this(regions, blocks, scheduler, workers, budget, supports, CollapseService::splittable, config);
    }

    /** The options in force (TFC server config {@code collapse*}). */
    public CollapseConfig config() {
        return config;
    }

    private static CollapseRandom splittable() {
        SplittableRandom r = new SplittableRandom();
        return new CollapseRandom() {
            @Override
            public float nextFloat() {
                return (r.nextInt() >>> 8) * 0x1.0p-24f;
            }

            @Override
            public int nextInt(int bound) {
                return r.nextInt(bound);
            }
        };
    }

    private WorldRunner runner(World world) {
        return runners.computeIfAbsent(world.getUID(), id -> {
            RegionExecutor r = regions.apply(world);
            CollapseBlocks access = new CollapseBlocks(world, blocks);
            PipelineMetrics metrics = new PipelineMetrics();
            RegionCommitQueue<CollapseState> queue = new RegionCommitQueue<>(r, access, budget, pos -> { }, metrics);
            return new WorldRunner(world, r, access, queue, metrics);
        });
    }

    // ---- pure decisions (worker threads) ------------------------------------------------------------------------------

    /** The kind tables of the rules, from the snapshot's own cells (read with the live tags and recipes). */
    static CollapseKinds kinds(Snapshot snap) {
        CollapseKinds.Builder b = CollapseKinds.builder().unbreakable(UNLOADED);
        Map<String, CollapseState.Cell> byId = new HashMap<>();
        for (CollapseState.Cell cell : snap.cells().values()) {
            byId.putIfAbsent(cell.id(), cell);
        }
        for (CollapseState.Cell cell : byId.values()) {
            String id = cell.id();
            if (cell.canStart()) {
                b.canStart(id);
            }
            if (cell.canTrigger()) {
                b.canTrigger(id);
            }
            if (cell.notSolidSupporting()) {
                b.notSolidSupporting(id);
            }
            if (cell.unbreakable()) {
                b.unbreakable(id);
            }
            if (cell.rubble() != null) {
                b.recipe(cell.rubble(), id);
            }
            b.toughness(id, cell.toughness());
        }
        return b.build();
    }

    static CollapseRules rules(Snapshot snap, SupportTable supports, CollapseConfig config) {
        return new CollapseRules(kinds(snap), supports, config.triggerChance(), config.fakeTriggerChance(),
                config.propagateChance(), config.minRadius(), config.radiusVariance());
    }

    /** What {@link #decideTrigger} decided: the decision, and the radius drawn after a real one (0 otherwise). */
    record Triggered(TriggerDecision decision, int radius) {}

    static Triggered decideTrigger(Snapshot snap, BlockPos mined, SupportTable supports, CollapseConfig config,
            CollapseRandom random) {
        CollapseRules rules = rules(snap, supports, config);
        TriggerDecision d = rules.trigger(snap.view(), mined, random);
        return new Triggered(d, d instanceof TriggerDecision.Real ? rules.drawRadius(random) : 0);
    }

    /** The changes of one phase, with each block's own rubble state (the kind table keeps one result per id). */
    static List<BlockChange<CollapseState>> changes(Snapshot snap, CollapseRules.Phase phase) {
        List<BlockChange<CollapseState>> out = new ArrayList<>(phase.steps().size());
        for (CollapseRules.CollapseStep step : phase.steps()) {
            CollapseState.Cell cell = snap.at(step.pos());
            String result = cell.rubble() != null ? cell.rubble() : step.result();
            out.add(new BlockChange<>(step.pos(), cell, new CollapseState.Fall(result, step.breakBelow()),
                    snap.capturedNanos()));
        }
        return out;
    }

    // ---- positions to capture -----------------------------------------------------------------------------------------

    /** The trigger's box with the block below its bottom layer and the support reach around it. */
    static List<BlockPos> triggerBox(BlockPos mined, SupportTable supports) {
        int h = SEARCH_XZ + supports.rangeHorizontal();
        int below = Math.max(SEARCH_Y + 1, SEARCH_Y + supports.rangeDown());
        int above = SEARCH_Y + supports.rangeUp();
        List<BlockPos> out = new ArrayList<>();
        for (int dx = -h; dx <= h; dx++) {
            for (int dz = -h; dz <= h; dz++) {
                for (int dy = -below; dy <= above; dy++) {
                    out.add(mined.offset(dx, dy, dz));
                }
            }
        }
        return out;
    }

    /** The columns {@link CollapseRules#start} can act on: closer than the radius, 4 below to 4 above the centre. */
    static List<BlockPos> startBox(BlockPos center, int radius) {
        int r2 = radius * radius;
        List<BlockPos> out = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz >= r2) {
                    continue; // never within the radius: TFC draws nothing there
                }
                for (int dy = -CollapseRules.START_BELOW; dy <= CollapseRules.START_ABOVE; dy++) {
                    out.add(center.offset(dx, dy, dz));
                }
            }
        }
        return out;
    }

    static List<BlockPos> propagationBox(CollapseRules.InProgress collapse) {
        LinkedHashSet<BlockPos> out = new LinkedHashSet<>();
        for (BlockPos p : collapse.next()) {
            out.add(p);
            out.add(p.offset(0, -1, 0));
        }
        return new ArrayList<>(out);
    }

    // ---- capture (owner threads) --------------------------------------------------------------------------------------

    /**
     * Reads {@code positions} on their owners, at most {@link #SLICE} per chunk per tick; chunks the calling thread owns
     * start inline. Unloaded chunks are left out (read as {@link CollapseState.Cell#UNLOADED}).
     */
    private CompletableFuture<Snapshot> capture(WorldRunner w, List<BlockPos> positions) {
        long now = budget.now();
        Map<Long, List<BlockPos>> byChunk = new HashMap<>();
        for (BlockPos p : positions) {
            byChunk.computeIfAbsent(p.chunkKey(), k -> new ArrayList<>()).add(p);
        }
        Map<BlockPos, CollapseState.Cell> out = new ConcurrentHashMap<>();
        List<CompletableFuture<Void>> parts = new ArrayList<>();
        for (List<BlockPos> list : byChunk.values()) {
            BlockPos first = list.getFirst();
            CompletableFuture<Void> f = new CompletableFuture<>();
            parts.add(f);
            if (w.regions.isOwner(first.chunkX(), first.chunkZ())) {
                readSlices(w, list, 0, out, f);
            } else {
                w.metrics.remoteCaptures.increment();
                w.regions.run(first.chunkX(), first.chunkZ(), () -> readSlices(w, list, 0, out, f));
            }
        }
        return CompletableFuture.allOf(parts.toArray(CompletableFuture[]::new))
                .thenApply(v -> new Snapshot(Map.copyOf(out), now));
    }

    private void readSlices(WorldRunner w, List<BlockPos> list, int from, Map<BlockPos, CollapseState.Cell> out,
            CompletableFuture<Void> done) {
        try {
            int to = Math.min(list.size(), from + SLICE);
            for (int i = from; i < to; i++) {
                BlockPos p = list.get(i);
                if (w.blocks.read(p) instanceof CollapseState.Cell c) {
                    out.put(p, c);
                }
            }
            if (to == list.size()) {
                done.complete(null);
                return;
            }
            BlockPos first = list.getFirst();
            w.regions.runNextTick(first.chunkX(), first.chunkZ(), () -> readSlices(w, list, to, out, done));
        } catch (Throwable t) {
            done.completeExceptionally(t);
        }
    }

    // ---- the flow -----------------------------------------------------------------------------------------------------

    /**
     * A player is breaking {@code block} (TFC {@code onBreakBlock}, before the block is removed). Owner thread of the block;
     * returns at once unless the block is in {@code #can_trigger_collapse} and the area around it is loaded.
     */
    public void trigger(Block block) {
        if (block.getType().isAir() || !blocks.canTrigger(block)) {
            return;
        }
        World world = block.getWorld();
        BlockPos mined = new BlockPos(block.getX(), block.getY(), block.getZ());
        if (!areaLoaded(world, mined)) {
            return;
        }
        triggers.increment();
        WorldRunner w = runner(world);
        SupportTable table = supports.get();
        CollapseRandom random = randoms.get();
        track(capture(w, triggerBox(mined, table))
                .thenApplyAsync(snap -> decideTrigger(snap, mined, table, config, random), workers)
                .thenCompose(t -> switch (t.decision()) {
                    case TriggerDecision.Real r -> {
                        real.increment();
                        yield startCollapse(w, mined, r.start(), t.radius(), table, random);
                    }
                    case TriggerDecision.Fake f -> {
                        fake.increment();
                        onOwner(w, mined, () -> showFake(w, mined, f.display()));
                        yield CompletableFuture.completedFuture(null);
                    }
                    case TriggerDecision.None none -> CompletableFuture.completedFuture(null);
                }), "trigger at " + mined);
    }

    /** TFC {@code collapseExplosionPropagateChance} default; the live value is {@link CollapseConfig#explosionPropagateChance}. */
    public static final double EXPLOSION_PROPAGATE_CHANCE = CollapseConfig.DEFAULT.explosionPropagateChance();

    /**
     * PAR-PHYS-05, TFC {@code WorldTracker.addCollapsePositions}: an explosion at {@code center} that blows {@code blown}
     * (the unfiltered block list, read before any plugin changes it) starts a collapse in progress. Each blown block's upper
     * neighbour joins the first propagation with chance 0.3 (one draw per block, in list order); the reach is the farthest
     * blown block, squared. Nothing falls immediately: the collapse then runs exactly like a mining collapse's follow-up
     * (after TFC's per-tick 1-in-10 delay, decided on the CPU pool from a snapshot, applied on the owners). Call on the
     * explosion's owner thread; an explosion that selects no position starts nothing (TFC's collapse would end at once).
     */
    public void explosion(World world, BlockPos center, List<BlockPos> blown) {
        if (blown.isEmpty()) {
            return;
        }
        CollapseRandom random = randoms.get();
        List<BlockPos> next = new ArrayList<>();
        double maxRadiusSquared = 0;
        for (BlockPos pos : blown) {
            double dx = pos.x() - center.x();
            double dy = pos.y() - center.y();
            double dz = pos.z() - center.z();
            double dist = dx * dx + dy * dy + dz * dz;
            if (dist > maxRadiusSquared) {
                maxRadiusSquared = dist;
            }
            if (random.nextFloat() < config.explosionPropagateChance()) {
                next.add(pos.offset(0, 1, 0));
            }
        }
        if (next.isEmpty()) {
            return;
        }
        explosions.increment();
        WorldRunner w = runner(world);
        schedulePropagation(w, new CollapseRules.InProgress(center, next, maxRadiusSquared),
                CollapseRules.propagationDelay(random), supports.get(), random, 0);
    }

    /** Explosion collapses started. */
    public long explosions() {
        return explosions.sum();
    }

    private static boolean areaLoaded(World world, BlockPos pos) {
        for (int cx = (pos.x() - LOADED_RADIUS) >> 4; cx <= (pos.x() + LOADED_RADIUS) >> 4; cx++) {
            for (int cz = (pos.z() - LOADED_RADIUS) >> 4; cz <= (pos.z() + LOADED_RADIUS) >> 4; cz++) {
                if (!world.isChunkLoaded(cx, cz)) {
                    return false;
                }
            }
        }
        return true;
    }

    private CompletableFuture<Void> startCollapse(WorldRunner w, BlockPos mined, BlockPos center, int radius,
            SupportTable table, CollapseRandom random) {
        CompletableFuture<Void> done = new CompletableFuture<>();
        onOwner(w, center, () -> capture(w, startBox(center, radius))
                .thenApplyAsync(snap -> {
                    CollapseRules.Phase phase = rules(snap, table, config).start(snap.view(), center, radius, random);
                    decided.increment();
                    return new Decided(changes(snap, phase), phase.followUp(),
                            phase.followUp().isPresent() ? CollapseRules.propagationDelay(random) : 0L);
                }, workers)
                .whenComplete((d, err) -> {
                    if (err != null) {
                        done.completeExceptionally(err);
                        return;
                    }
                    commit(w, d.changes());
                    if (!d.changes().isEmpty()) {
                        onOwner(w, mined, () -> blocks.sound(block(w, mined), CollapseWorld.CollapseSound.LONG));
                    }
                    d.followUp().ifPresent(c -> schedulePropagation(w, c, d.delay(), table, random, 0));
                    done.complete(null);
                }));
        return done;
    }

    private record Decided(List<BlockChange<CollapseState>> changes, Optional<CollapseRules.InProgress> followUp,
            long delay) {}

    private void schedulePropagation(WorldRunner w, CollapseRules.InProgress collapse, long delay, SupportTable table,
            CollapseRandom random, int waited) {
        BlockPos c = collapse.center();
        inFlight.incrementAndGet();
        Scheduler.TaskHandle handle = scheduler.regionDelayed(w.world, c.chunkX(), c.chunkZ(), Math.max(1L, delay), () -> {
            try {
                if (!w.queue.isEmpty() && waited < MAX_COMMIT_WAIT) {
                    schedulePropagation(w, collapse, 1L, table, random, waited + 1); // the last phase is still falling
                    return;
                }
                if (!w.world.isChunkLoaded(c.chunkX(), c.chunkZ())) {
                    return; // the collapse ends with its chunk, as nothing is persisted
                }
                propagate(w, collapse, table, random);
            } finally {
                inFlight.decrementAndGet();
            }
        });
        if (handle == null) {
            inFlight.decrementAndGet();
        }
    }

    private void propagate(WorldRunner w, CollapseRules.InProgress collapse, SupportTable table, CollapseRandom random) {
        propagations.increment();
        track(capture(w, propagationBox(collapse))
                .thenApplyAsync(snap -> {
                    CollapseRules.Phase phase = rules(snap, table, config).propagate(snap.view(), collapse, random);
                    decided.increment();
                    return new Decided(changes(snap, phase), phase.followUp(),
                            phase.followUp().isPresent() ? CollapseRules.propagationDelay(random) : 0L);
                }, workers)
                .thenAccept(d -> {
                    commit(w, d.changes());
                    if (!d.changes().isEmpty()) {
                        BlockPos c = collapse.center();
                        onOwner(w, c, () -> blocks.sound(block(w, c), CollapseWorld.CollapseSound.SHORT));
                    }
                    d.followUp().ifPresent(next -> schedulePropagation(w, next, d.delay(), table, random, 0));
                }), "propagation at " + collapse.center());
    }

    private void commit(WorldRunner w, List<BlockChange<CollapseState>> changes) {
        Map<Long, List<BlockChange<CollapseState>>> byChunk = new HashMap<>();
        for (BlockChange<CollapseState> c : changes) {
            byChunk.computeIfAbsent(c.pos().chunkKey(), k -> new ArrayList<>()).add(c);
        }
        for (List<BlockChange<CollapseState>> group : byChunk.values()) {
            onOwner(w, group.getFirst().pos(), () -> w.queue.enqueue(group));
        }
    }

    private void showFake(WorldRunner w, BlockPos mined, List<BlockPos> display) {
        blocks.sound(block(w, mined), CollapseWorld.CollapseSound.LONG_FAKE);
        for (BlockPos p : display) {
            onOwner(w, p, () -> blocks.fakeStart(block(w, p)));
        }
    }

    private static Block block(WorldRunner w, BlockPos p) {
        return w.world.getBlockAt(p.x(), p.y(), p.z());
    }

    /** Runs on the owner of {@code pos}, inline when already there; counted until it has run. */
    private void onOwner(WorldRunner w, BlockPos pos, Runnable task) {
        if (w.regions.isOwner(pos.chunkX(), pos.chunkZ())) {
            task.run();
            return;
        }
        inFlight.incrementAndGet();
        w.regions.run(pos.chunkX(), pos.chunkZ(), () -> {
            try {
                task.run();
            } finally {
                inFlight.decrementAndGet();
            }
        });
    }

    private void track(CompletableFuture<?> f, String what) {
        inFlight.incrementAndGet();
        f.whenComplete((v, err) -> {
            inFlight.decrementAndGet();
            if (err != null) {
                LOG.log(Level.SEVERE, "[collapse] " + what + " failed", err);
            }
        });
    }

    // ---- observation --------------------------------------------------------------------------------------------------

    /** True when nothing is being captured, decided, waiting to propagate or queued. Racy; poll it. */
    public boolean isIdle() {
        return inFlight.get() == 0 && runners.values().stream().allMatch(w -> w.queue.isEmpty());
    }

    public PipelineMetrics metrics(World world) {
        return runner(world).metrics;
    }

    /** Mined blocks that could trigger (tag and loaded area passed). */
    public long triggers() {
        return triggers.sum();
    }

    public long realCollapses() {
        return real.sum();
    }

    public long fakeCollapses() {
        return fake.sum();
    }

    /** Propagation steps run. */
    public long propagations() {
        return propagations.sum();
    }

    /** Blocks that started to fall in {@code world}. */
    public long falls(World world) {
        return runner(world).blocks.falls.sum();
    }

    /** Blocks broken under a collapsing block in {@code world}. */
    public long broken(World world) {
        return runner(world).blocks.broken.sum();
    }
}
