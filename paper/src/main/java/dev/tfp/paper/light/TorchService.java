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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
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
 * PAR-HEAT-28: after the apply step swaps a torch, the chunk's light is sent again to the players tracking it once the
 * server's light engine settled ({@link LightRefresh}, one resend per changed chunk and refresh window). A torch that
 * burns out while its chunk loads can otherwise be committed around the chunk packet, and the client kept the lit
 * torch's block light 14 in the dead torch until a relog.
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
     * {@link #REFRESH_DELAY_TICKS} after a burnout was applied there.
     */
    @FunctionalInterface
    public interface LightRefresh {
        void refresh(World world, int chunkX, int chunkZ);

        /**
         * Paper: {@link World#refreshChunk} (the chunk with its light, to every player seeing it); nothing when no player
         * sees the chunk. The resend is cosmetic: a server implementation that fails it (a test server without chunk
         * resends) leaves the light as the engine sent it instead of failing the region task.
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
                // no resend on this server implementation: the client keeps the light it was sent
            }
        };
    }

    /**
     * Server ticks between a burnout's apply step and the chunk's light resend: the light engine propagates the removed
     * source after the tick that set the block, so the resend waits for it.
     */
    public static final int REFRESH_DELAY_TICKS = 5;
    /** A queued resend older than this (10 s) counts as dropped. */
    static final long REFRESH_STALE_NANOS = 10_000_000_000L;

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
    private final Map<UUID, Map<Long, Long>> refreshing = new ConcurrentHashMap<>();
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
     * PAR-HEAT-28: queues the light resend of the chunk holding {@code pos} after a burnout there, once per chunk while
     * one is pending. Region thread of the chunk (the commit queue's apply step).
     */
    void queueLightRefresh(World world, BlockPos pos) {
        int cx = pos.chunkX();
        int cz = pos.chunkZ();
        Map<Long, Long> pending = refreshing.computeIfAbsent(world.getUID(), id -> new ConcurrentHashMap<>());
        long key = chunkKey(cx, cz);
        long queued = System.nanoTime();
        boolean[] fresh = {false};
        pending.compute(key, (k, at) -> {
            if (at != null && queued - at < REFRESH_STALE_NANOS) {
                return at;
            }
            fresh[0] = true;
            return queued;
        });
        if (!fresh[0]) {
            return;
        }
        RegionExecutor region = regions.apply(world);
        Runnable send = () -> {
            pending.remove(key, queued);
            lightRefreshes.increment();
            lightRefresh.refresh(world, cx, cz);
        };
        Runnable chain = send;
        for (int i = 1; i < REFRESH_DELAY_TICKS; i++) {
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
