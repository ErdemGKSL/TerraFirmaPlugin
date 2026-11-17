/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/TFCTorchBlock.java @ c3b4d9536 (onRandomTick),
 *       src/main/java/net/dries007/tfc/common/blocks/TFCWallTorchBlock.java @ c3b4d9536 (randomTick: withPropertiesOf),
 *       src/main/java/net/dries007/tfc/ForgeEventHandler.java @ c3b4d9536 (onFireStart, onFireStop: the torch branches)
 */
package dev.tfp.paper.light;

import dev.tfp.core.light.LightSourceRules;
import dev.tfp.core.light.TorchRules;
import dev.tfp.core.light.TorchRules.Kind;
import dev.tfp.core.world.BlockPos;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.fire.FireLightable;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.pipeline.AsyncDecisionPipeline;
import dev.tfp.paper.pipeline.BlockAccess;
import dev.tfp.paper.pipeline.BlockChange;
import dev.tfp.paper.pipeline.CommitBudget;
import dev.tfp.paper.pipeline.PipelineMetrics;
import dev.tfp.paper.pipeline.RegionExecutor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * PAR-HEAT-10: the torch's world side. TFC swaps a lit torch for the dead one on the first random tick after its burn
 * time; here nothing ticks, and the swap is decided lazily, "decide async, apply sync":
 *
 * <pre>
 * trigger    owner thread: the chunk loads (every lit torch it lists), the torch's burnout callback fires, it is clicked
 *            or notified past its burn time
 * snapshot   owner thread: the torch's kind, facing and counter ({@link Cell})
 * decide     worker pool: {@link TorchRules#afterCatchUp} at the snapshot's calendar tick, pure
 * apply      owner region, under the {@link CommitBudget}: the commit queue drops the change if the torch changed
 *            (relit, broken, replaced); otherwise the dead kind is placed with the same facing
 * </pre>
 *
 * PAR-HEAT-28: after the apply step swaps a torch, the light of its chunk and of the neighbour chunks its light reached
 * is sent again to the players tracking them once the server's light engine settled ({@link LightRefresh}, one resend
 * per changed chunk and refresh window). A torch that
 * burns out while its chunk loads can otherwise be committed around the chunk packet, and the client kept the lit
 * torch's block light 14 in the dead torch until a relog. "Settled" is read, not assumed ({@link LightProbe}): right
 * after a chunk load the server's light engine (Moonrise, off the tick thread) was seen still holding 14 in the dead
 * torch more than 10 ticks after the swap (2026-11-17, Paper 1.21.11), so a resend after a fixed delay sent the stale
 * light again.
 *
 * <p>Also TFC's {@code onFireStart} ({@link #lightable}: a lit torch resets its counter, a dead one becomes lit) and
 * {@code onFireStop} ({@link #douse}).
 */
public final class TorchService {
    /** The wall torches' horizontal facing (TFC {@code WallTorchBlock.FACING}). */
    public static final String FACING = "facing";
    /** Commit budget per region per tick (ARCHITECTURE.md: 1 ms). */
    public static final long BUDGET_NANOS = 1_000_000L;

    /**
     * One torch as the pipeline sees it: its kind, the wall facing (null for a standing torch) and, for a lit torch, the
     * counter ({@link TorchDevice#NEVER_LIT} when never stamped). {@link #NONE} for any other block.
     */
    public record Cell(Kind kind, String facing, long litTick) {
        public static final Cell NONE = new Cell(null, null, 0);

        /** The block state of this cell. */
        BlockStateRef state() {
            BlockStateRef s = BlockStateRef.of(kind.id());
            return kind.wall() && facing != null ? s.with(FACING, facing) : s;
        }
    }

    /**
     * PAR-HEAT-28: sends the light of one chunk again to the players tracking it. Called on the chunk's region thread,
     * at least {@link #REFRESH_DELAY_TICKS} after a burnout whose light reached the chunk was applied.
     */
    @FunctionalInterface
    public interface LightRefresh {
        void refresh(World world, int chunkX, int chunkZ);

        /** Set once the unsupported-resend warning was logged. */
        AtomicBoolean UNSUPPORTED_WARNED = new AtomicBoolean();

        /**
         * Paper: {@link World#refreshChunk} (the chunk with its light, to every player seeing it); nothing when no player
         * sees the chunk. The resend is cosmetic: a server implementation without it (a test server whose
         * calls throw) logs one warning with the exception and leaves the light as the engine sent it instead of failing the region task.
         */
        LightRefresh PAPER = (world, chunkX, chunkZ) -> {
            if (!world.isChunkLoaded(chunkX, chunkZ)) {
                return;
            }
            try {
                if (!world.getPlayersSeeingChunk(chunkX, chunkZ).isEmpty()) {
                    world.refreshChunk(chunkX, chunkZ);
                }
            } catch (RuntimeException e) {
                // no resend on this server implementation: the client keeps the light it was sent (warned once)
                if (UNSUPPORTED_WARNED.compareAndSet(false, true)) {
                    org.bukkit.Bukkit.getLogger().warning(
                            "[tfp] torch light resend unsupported on this server (" + e + "); a torch that burns out"
                                    + " on chunk load may keep stale client light until the chunk is resent");
                }
            }
        };
    }

    /**
     * PAR-HEAT-28: whether the server's light engine has removed the dead torch's former light at {@code pos}. Called on
     * the chunk's region thread while a resend waits.
     */
    @FunctionalInterface
    public interface LightProbe {
        boolean settled(World world, BlockPos pos);

        /**
         * Paper: the server block light at the position is below the lit torch's {@link #LIT_BLOCK_LIGHT} (the removed
         * source left only light from neighbours, at most one less). An unloaded chunk, or a server implementation
         * without light access (also a position another Folia region owns, read for a neighbour chunk's wait),
         * counts as settled.
         */
        LightProbe PAPER = (world, pos) -> {
            if (!world.isChunkLoaded(pos.chunkX(), pos.chunkZ())) {
                return true;
            }
            try {
                return world.getBlockAt(pos.x(), pos.y(), pos.z()).getLightFromBlocks() < LIT_BLOCK_LIGHT;
            } catch (RuntimeException e) {
                return true;
            }
        };
    }

    /** The lit torch's block light (TFC {@code TFCTorchBlock}: vanilla torch, 14). */
    public static final int LIT_BLOCK_LIGHT = 14;
    /**
     * Server ticks between a burnout's apply step and the first light check: the light engine propagates the removed
     * source after the tick that set the block, so the resend waits for it.
     */
    public static final int REFRESH_DELAY_TICKS = 5;
    /** Ticks between two light checks while the engine still holds the lit light ({@link LightProbe}). */
    public static final int REFRESH_POLL_TICKS = 5;
    /** The last check: the resend goes out this many ticks after the apply step even if the light never settled. */
    public static final int REFRESH_MAX_TICKS = 400;
    /** A queued resend older than this (60 s, longer than {@link #REFRESH_MAX_TICKS}) counts as dropped. */
    static final long REFRESH_STALE_NANOS = 60_000_000_000L;

    /** A chunk's queued resend: when it was queued and the burnt-out positions it waits for. */
    private record Pending(long queued, Set<BlockPos> positions) {
    }

    /** The snapshot one decision reads: the cell and the calendar tick it was taken at. */
    record Snap(BlockPos pos, Cell cell, long now, long nanos) {
    }

    private final CustomBlocks blocks;
    private final DeviceService devices;
    private final TorchDevice device;
    private final LongSupplier calendar;
    private final IntSupplier torchTicks;
    private final Function<World, RegionExecutor> regions;
    private final Executor workers;
    private final CommitBudget budget;
    private final Map<UUID, AsyncDecisionPipeline<Cell, Snap>> pipelines = new ConcurrentHashMap<>();
    private final LongAdder burnouts = new LongAdder();
    private final LightRefresh lightRefresh;
    /**
     * Chunks with a light resend queued, per world ({@code chunkKey} to the {@link System#nanoTime} it was queued at); a
     * second burnout there joins the first resend. An entry older than {@link #REFRESH_STALE_NANOS} (its task was dropped
     * with an unloaded region) no longer holds a new resend back.
     */
    private final Map<UUID, Map<Long, Pending>> refreshing = new ConcurrentHashMap<>();
    private final LightProbe lightProbe;
    private final LongAdder lightRefreshes = new LongAdder();

    /**
     * @param calendar   the player-tick calendar the counters are stamped with (any thread)
     * @param torchTicks TFC {@code torchTicks}
     */
    public TorchService(CustomBlocks blocks, DeviceService devices, TorchDevice device, LongSupplier calendar,
            IntSupplier torchTicks, Function<World, RegionExecutor> regions, Executor workers, CommitBudget budget) {
        this(blocks, devices, device, calendar, torchTicks, regions, workers, budget, LightRefresh.PAPER);
    }

    /** As above, with the light resend after a burnout ({@link LightRefresh#PAPER} on a server). */
    public TorchService(CustomBlocks blocks, DeviceService devices, TorchDevice device, LongSupplier calendar,
            IntSupplier torchTicks, Function<World, RegionExecutor> regions, Executor workers, CommitBudget budget,
            LightRefresh lightRefresh) {
        this(blocks, devices, device, calendar, torchTicks, regions, workers, budget, lightRefresh, LightProbe.PAPER);
    }

    /** As above, with the light check that holds the resend back until the server's light engine settled. */
    public TorchService(CustomBlocks blocks, DeviceService devices, TorchDevice device, LongSupplier calendar,
            IntSupplier torchTicks, Function<World, RegionExecutor> regions, Executor workers, CommitBudget budget,
            LightRefresh lightRefresh, LightProbe lightProbe) {
        this.lightProbe = Objects.requireNonNull(lightProbe, "lightProbe");
        this.lightRefresh = Objects.requireNonNull(lightRefresh, "lightRefresh");
        this.blocks = Objects.requireNonNull(blocks, "blocks");
        this.devices = Objects.requireNonNull(devices, "devices");
        this.device = Objects.requireNonNull(device, "device");
        this.calendar = Objects.requireNonNull(calendar, "calendar");
        this.torchTicks = Objects.requireNonNull(torchTicks, "torchTicks");
        this.regions = Objects.requireNonNull(regions, "regions");
        this.workers = Objects.requireNonNull(workers, "workers");
        this.budget = Objects.requireNonNull(budget, "budget");
    }

    // ---- burnout ---------------------------------------------------------------------------------------------------

    /** Queues a burnout decision for the torch at {@code block}. Region thread of the block; cheap. */
    public void burnout(Block block) {
        pipeline(block.getWorld()).submit(pos(block));
    }

    /** A lit torch still burning after a chunk load, and the server ticks until its burnout callback. */
    public record Waiting(Block block, long ticks) {
    }

    /**
     * The chunk's lazy blocks were loaded: every lit torch among {@code positions} (the chunk's lazy block index) whose
     * burn time ran out while the chunk was unloaded is queued for a burnout decision ({@link #callback}); the ones still
     * burning are returned with the wait until their callback. Region thread of the chunk.
     */
    public List<Waiting> chunkLoaded(Chunk chunk, Collection<BlockPos> positions) {
        World world = chunk.getWorld();
        List<Waiting> waiting = new ArrayList<>();
        for (BlockPos p : positions) {
            Block b = world.getBlockAt(p.x(), p.y(), p.z());
            if (litKind(b).isPresent()) {
                long wait = callback(b);
                if (wait > 0) {
                    waiting.add(new Waiting(b, wait));
                }
            }
        }
        return waiting;
    }

    /**
     * The burnout callback of the torch at {@code block}: queues the decision when its burn time is over by the calendar,
     * otherwise answers the server ticks still to wait (the calendar may run slower than the server). Region thread.
     *
     * @return the ticks to wait before the next callback, or 0 when none is needed (decision queued, not a lit torch,
     *     never burns out)
     */
    public long callback(Block block) {
        Cell cell = read(block);
        if (cell == null || cell.kind() == null || !cell.kind().lit()) {
            return 0;
        }
        long due = TorchRules.burnoutTick(cell.litTick(), torchTicks.getAsInt());
        if (due == LightSourceRules.NEVER) {
            return 0;
        }
        long now = calendar.getAsLong();
        if (now >= due) {
            burnout(block);
            return 0;
        }
        return due - now;
    }

    /** Worker thread: TFC {@code onRandomTick}, pure. */
    static List<BlockChange<Cell>> decide(Snap snap, int torchTicks) {
        Cell c = snap.cell();
        if (c == null || c.kind() == null || !c.kind().lit()) {
            return List.of();
        }
        Kind after = TorchRules.afterCatchUp(c.kind(), c.litTick(), snap.now(), torchTicks);
        if (after == c.kind()) {
            return List.of();
        }
        return List.of(new BlockChange<>(snap.pos(), c, new Cell(after, c.facing(), 0), snap.nanos()));
    }

    private AsyncDecisionPipeline<Cell, Snap> pipeline(World world) {
        return pipelines.computeIfAbsent(world.getUID(), id -> {
            BlockAccess<Cell> access = new BlockAccess<>() {
                @Override
                public Cell read(BlockPos pos) {
                    if (!world.isChunkLoaded(pos.chunkX(), pos.chunkZ())) {
                        return null;
                    }
                    return TorchService.this.read(world.getBlockAt(pos.x(), pos.y(), pos.z()));
                }

                @Override
                public void write(BlockPos pos, Cell next) {
                    Block b = world.getBlockAt(pos.x(), pos.y(), pos.z());
                    if (blocks.place(b, next.state(), true)) {
                        burnouts.increment();
                        queueLightRefresh(world, pos);
                    }
                }
            };
            return new AsyncDecisionPipeline<>("torch/" + world.getName(), regions.apply(world), access, workers, budget,
                (pos, nanos) -> CompletableFuture.completedFuture(new Snap(pos, access.read(pos), calendar.getAsLong(), nanos)),
                snap -> decide(snap, torchTicks.getAsInt()));
        });
    }

    /**
     * PAR-HEAT-28: queues the light resend of every chunk the burnt-out torch at {@code pos} lit ({@link #chunksInReach}:
     * its own chunk and the neighbours its light 14 reached), once per chunk while one is pending (a later burnout whose
     * light reached the chunk joins it). Each chunk's resend waits {@link #REFRESH_DELAY_TICKS}, then checks every
     * {@link #REFRESH_POLL_TICKS} until the light at every joined position settled ({@link LightProbe}), at most until
     * {@link #REFRESH_MAX_TICKS}. The neighbours matter: a floor torch's chunk sent before the light of a dead wall torch 9
     * blocks away in the next chunk was removed kept that torch's light 5 on the client (2026-11-17, Paper 1.21.11).
     * Region thread of {@code pos} (the commit queue's apply step); each chunk's checks run on that chunk's region.
     */
    void queueLightRefresh(World world, BlockPos pos) {
        for (long key : chunksInReach(pos)) {
            queueChunkRefresh(world, (int) (key >> 32), (int) key, pos);
        }
    }

    /**
     * The chunks (as {@code chunkKey}s, own chunk first) holding a cell the lit torch at {@code pos} gave block light:
     * those whose nearest column is at most {@code LIT_BLOCK_LIGHT - 1} blocks away (taxicab, as light spreads).
     */
    static List<Long> chunksInReach(BlockPos pos) {
        int reach = LIT_BLOCK_LIGHT - 1;
        int cx = pos.chunkX();
        int cz = pos.chunkZ();
        List<Long> out = new ArrayList<>();
        out.add(chunkKey(cx, cz));
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                int nx = cx + dx;
                int nz = cz + dz;
                int distX = Math.max(0, Math.max((nx << 4) - pos.x(), pos.x() - ((nx << 4) + 15)));
                int distZ = Math.max(0, Math.max((nz << 4) - pos.z(), pos.z() - ((nz << 4) + 15)));
                if (distX + distZ <= reach) {
                    out.add(chunkKey(nx, nz));
                }
            }
        }
        return out;
    }

    /** {@link #queueLightRefresh} for one chunk. */
    private void queueChunkRefresh(World world, int cx, int cz, BlockPos pos) {
        Map<Long, Pending> pending = refreshing.computeIfAbsent(world.getUID(), id -> new ConcurrentHashMap<>());
        long key = chunkKey(cx, cz);
        long queued = System.nanoTime();
        Pending[] fresh = {null};
        pending.compute(key, (k, at) -> {
            if (at != null && queued - at.queued() < REFRESH_STALE_NANOS) {
                at.positions().add(pos);
                return at;
            }
            Set<BlockPos> positions = ConcurrentHashMap.newKeySet();
            positions.add(pos);
            fresh[0] = new Pending(queued, positions);
            return fresh[0];
        });
        if (fresh[0] == null) {
            return;
        }
        Pending mine = fresh[0];
        RegionExecutor region = regions.apply(world);
        Runnable send = () -> {
            pending.remove(key, mine);
            lightRefreshes.increment();
            lightRefresh.refresh(world, cx, cz);
        };
        after(region, cx, cz, REFRESH_DELAY_TICKS, () -> check(world, region, cx, cz, mine, REFRESH_DELAY_TICKS, send));
    }

    /** One light check of a pending resend, {@code waited} ticks after the apply step. Region thread of the chunk. */
    private void check(World world, RegionExecutor region, int cx, int cz, Pending pending, int waited, Runnable send) {
        boolean settled = pending.positions().stream().allMatch(p -> lightProbe.settled(world, p));
        if (settled || waited >= REFRESH_MAX_TICKS) {
            send.run();
            return;
        }
        int next = Math.min(REFRESH_POLL_TICKS, REFRESH_MAX_TICKS - waited);
        after(region, cx, cz, next, () -> check(world, region, cx, cz, pending, waited + next, send));
    }

    /** Runs {@code task} on the chunk's region {@code ticks} ticks from now (at least one). */
    private static void after(RegionExecutor region, int cx, int cz, int ticks, Runnable task) {
        Runnable chain = task;
        for (int i = 1; i < ticks; i++) {
            Runnable next = chain;
            chain = () -> region.runNextTick(cx, cz, next);
        }
        region.runNextTick(cx, cz, chain);
    }

    private static long chunkKey(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    /** The torch at {@code block} as a {@link Cell}. Region thread. */
    Cell read(Block block) {
        Optional<BlockStateRef> state = blocks.stateAt(block);
        Optional<Kind> kind = state.flatMap(s -> Kind.byId(s.id()));
        if (kind.isEmpty()) {
            return Cell.NONE;
        }
        String facing = kind.get().wall() ? state.get().properties().get(FACING) : null;
        if (!kind.get().lit()) {
            return new Cell(kind.get(), facing, 0);
        }
        long lit = devices.inspect(block).flatMap(DeviceService.Inspection::record)
            .map(r -> r.state() instanceof TorchDevice.State s ? s.litTick() : TorchDevice.NEVER_LIT)
            .orElse(TorchDevice.NEVER_LIT);
        return new Cell(kind.get(), facing, lit);
    }

    private Optional<Kind> litKind(Block block) {
        return blocks.stateAt(block).flatMap(s -> Kind.byId(s.id())).filter(Kind::lit);
    }

    // ---- fire start and douse ----------------------------------------------------------------------------------------

    /**
     * TFC {@code onFireStart}'s torch branches as a {@link FireLightable}: any strength; a lit torch resets its counter, a
     * dead torch is replaced by the lit kind (facing kept) with a fresh counter. Either cancels the start.
     */
    public FireLightable lightable() {
        return ctx -> light(ctx.block());
    }

    /** {@link #lightable}'s step on {@code block}; also the torch item's weak start. Region thread of the block. */
    public boolean light(Block block) {
        Optional<BlockStateRef> state = blocks.stateAt(block);
        Optional<Kind> kind = state.flatMap(s -> Kind.byId(s.id()));
        if (kind.isEmpty()) {
            return false;
        }
        // Apply step of an already-decided fire start (FireStarting decided it), so the relit block and the counter are
        // written here on the owning region thread rather than through the burnout pipeline. The burnout callback is
        // queued inside the edit because it needs the edit's calendar "now".
        TorchRules.FireStart start = TorchRules.fireStart(kind.get());
        Kind lit = kind.get().relit();
        if (start == TorchRules.FireStart.RELIT) {
            String facing = lit.wall() ? state.get().properties().get(FACING) : null;
            blocks.place(block, new Cell(lit, facing, 0).state(), true);
        }
        devices.edit(block, device.kind(lit), (record, now) -> {
            device.queueBurnout(block, now, now);
            return record.withState(new TorchDevice.State(now));
        });
        return start.handled();
    }

    /**
     * TFC {@code onFireStop}'s torch branches: a lit torch becomes the dead kind (facing kept). Returns whether it was
     * handled (a dead torch is not). For the dousing of PAR-HEAT-13 (water bucket and splash potions). Region thread.
     */
    public boolean douse(Block block) {
        Cell cell = read(block);
        if (cell.kind() == null) {
            return false;
        }
        Optional<Kind> dead = TorchRules.douse(cell.kind());
        dead.ifPresent(d -> blocks.place(block, new Cell(d, cell.facing(), 0).state(), true));
        return dead.isPresent();
    }

    // ---- metrics -------------------------------------------------------------------------------------------------------

    /** Torches swapped for dead ones by the pipeline. */
    public long burnouts() {
        return burnouts.sum();
    }

    /** PAR-HEAT-28: chunk light resends sent after burnouts. */
    public long lightRefreshes() {
        return lightRefreshes.sum();
    }

    /** True when no decision is running and no change is queued in any world. Racy; poll it. */
    public boolean isIdle() {
        return pipelines.values().stream().allMatch(AsyncDecisionPipeline::isIdle);
    }

    public PipelineMetrics metrics(World world) {
        return pipeline(world).metrics();
    }

    private static BlockPos pos(Block b) {
        return new BlockPos(b.getX(), b.getY(), b.getZ());
    }
}
