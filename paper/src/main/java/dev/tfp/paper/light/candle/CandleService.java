/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/TFCCandleBlock.java @ c3b4d9536 (onRandomTick),
 *       src/main/java/net/dries007/tfc/common/blocks/devices/JackOLanternBlock.java @ c3b4d9536 (randomTick, extinguish),
 *       src/main/java/net/dries007/tfc/ForgeEventHandler.java @ c3b4d9536 (onFireStart, onFireStop: candles, candle cakes,
 *       carved pumpkin, jack-o'-lantern),
 *       src/main/java/net/dries007/tfc/common/items/CandleBlockItem.java @ c3b4d9536 (useOn: a candle on a fresh cake),
 *       src/main/java/net/dries007/tfc/common/blocks/crop/TFCPumpkinBlock.java @ c3b4d9536 (useItemOn: carving)
 */
package dev.tfp.paper.light.candle;

import dev.tfp.core.light.LightSourceRules;
import dev.tfp.core.world.BlockPos;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.fire.FireLightable;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.light.candle.CandleRules.Family;
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
import java.util.function.LongSupplier;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;

/**
 * PAR-HEAT-11: the world side of candles, candle cakes and jack-o'-lanterns. TFC puts them out on the first random tick
 * after their burn time; here nothing ticks and the change is decided lazily, "decide async, apply sync":
 *
 * <pre>
 * trigger    owner thread: the chunk loads (every lit block its lazy index lists), the burnout callback fires, the block
 *            is clicked or notified past its burn time, a jack-o'-lantern is placed (TFC: no counter)
 * snapshot   owner thread: the block's state and counter ({@link Cell})
 * decide     worker pool: {@link CandleRules#burnsOut} at the snapshot's calendar tick, pure
 * apply      owner region, under the {@link CommitBudget}: dropped if the block changed (relit, broken, put out);
 *            otherwise {@code lit=false}, or vanilla's carved pumpkin with the same facing
 * </pre>
 *
 * Also TFC's {@code onFireStart} ({@link #lightable}), {@code onFireStop} ({@link #douse}), the candle on a fresh cake
 * ({@link #addCandle}) and the knife carving a pumpkin ({@link #carve}).
 */
public final class CandleService {
    /** Commit budget per region per tick (ARCHITECTURE.md: 1 ms). */
    public static final long BUDGET_NANOS = 1_000_000L;

    /**
     * One block as the pipeline sees it: its family (null for vanilla's carved pumpkin), state and counter. {@link #NONE}
     * for any other block.
     */
    public record Cell(Family family, BlockStateRef state, long litTick) {
        public static final Cell NONE = new Cell(null, null, 0);
    }

    record Snap(BlockPos pos, Cell cell, long now, long nanos) {
    }

    /** Whether TFC's pumpkin at a block is rotten ({@code DecayingBlockEntity.isRotten}); PAR-FARM-05 provides it. */
    @FunctionalInterface
    public interface Rotting {
        boolean rotten(Block block);
    }

    private final CustomBlocks blocks;
    private final DeviceService devices;
    private final CandleDevice device;
    private final LongSupplier calendar;
    private final Function<World, RegionExecutor> regions;
    private final Executor workers;
    private final CommitBudget budget;
    private final Map<UUID, AsyncDecisionPipeline<Cell, Snap>> pipelines = new ConcurrentHashMap<>();
    private final LongAdder burnouts = new LongAdder();

    /** @param calendar the player-tick calendar the counters are stamped with (any thread) */
    public CandleService(CustomBlocks blocks, DeviceService devices, CandleDevice device, LongSupplier calendar,
            Function<World, RegionExecutor> regions, Executor workers, CommitBudget budget) {
        this.blocks = Objects.requireNonNull(blocks, "blocks");
        this.devices = Objects.requireNonNull(devices, "devices");
        this.device = Objects.requireNonNull(device, "device");
        this.calendar = Objects.requireNonNull(calendar, "calendar");
        this.regions = Objects.requireNonNull(regions, "regions");
        this.workers = Objects.requireNonNull(workers, "workers");
        this.budget = Objects.requireNonNull(budget, "budget");
    }

    private CandleEffects effects() {
        return device.services().effects();
    }

    private int candleTicks() {
        return device.services().candleTicks().getAsInt();
    }

    private int jackTicks() {
        return device.services().jackTicks().getAsInt();
    }

    // ---- burnout ---------------------------------------------------------------------------------------------------

    /** Queues a burnout decision for the block at {@code block}. Region thread of the block; cheap. */
    public void burnout(Block block) {
        pipeline(block.getWorld()).submit(pos(block));
    }

    /** A lit block still burning after a chunk load, and the server ticks until its burnout callback. */
    public record Waiting(Block block, long ticks) {
    }

    /**
     * The chunk's lazy blocks were loaded: every lit candle, candle cake and jack-o'-lantern among {@code positions} whose
     * burn time ran out while the chunk was unloaded is queued for a decision; the ones still burning are returned with
     * the wait until their callback. Region thread of the chunk.
     */
    public List<Waiting> chunkLoaded(Chunk chunk, Collection<BlockPos> positions) {
        World world = chunk.getWorld();
        List<Waiting> waiting = new ArrayList<>();
        for (BlockPos p : positions) {
            Block b = world.getBlockAt(p.x(), p.y(), p.z());
            long wait = callback(b);
            if (wait > 0) {
                waiting.add(new Waiting(b, wait));
            }
        }
        return waiting;
    }

    /**
     * The burnout callback of the block at {@code block}: queues the decision when its burn time is over by the calendar,
     * otherwise answers the server ticks still to wait. Region thread.
     *
     * @return the ticks to wait before the next callback, or 0 when none is needed
     */
    public long callback(Block block) {
        Cell cell = read(block);
        if (cell.family() == null || !CandleDevice.lit(cell.family(), cell.state())) {
            return 0;
        }
        long due = CandleRules.burnoutTick(cell.litTick(), cell.family().limit(candleTicks(), jackTicks()));
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

    /** Worker thread: TFC's random tick, pure. */
    static List<BlockChange<Cell>> decide(Snap snap, int candleTicks, int jackTicks) {
        Cell c = snap.cell();
        if (c == null || c.family() == null) {
            return List.of();
        }
        boolean lit = CandleDevice.lit(c.family(), c.state());
        if (!CandleRules.burnsOut(c.family(), lit, c.litTick(), snap.now(), candleTicks, jackTicks)) {
            return List.of();
        }
        Cell next = c.family() == Family.JACK_O_LANTERN
            ? carved(c.state().properties().getOrDefault(CandleRules.FACING, "north"))
            : new Cell(c.family(), c.state().with(CandleRules.LIT, "false"), c.litTick());
        return List.of(new BlockChange<>(snap.pos(), c, next, snap.nanos()));
    }

    private static Cell carved(String facing) {
        return new Cell(null, BlockStateRef.of(CandleRules.CARVED_PUMPKIN).with(CandleRules.FACING, facing), 0);
    }

    private AsyncDecisionPipeline<Cell, Snap> pipeline(World world) {
        return pipelines.computeIfAbsent(world.getUID(), id -> {
            BlockAccess<Cell> access = new BlockAccess<>() {
                @Override
                public Cell read(BlockPos pos) {
                    if (!world.isChunkLoaded(pos.chunkX(), pos.chunkZ())) {
                        return null;
                    }
                    return CandleService.this.read(world.getBlockAt(pos.x(), pos.y(), pos.z()));
                }

                @Override
                public void write(BlockPos pos, Cell next) {
                    Block b = world.getBlockAt(pos.x(), pos.y(), pos.z());
                    if (CandleRules.CARVED_PUMPKIN.equals(next.state().id())) {
                        effects().placeCarvedPumpkin(b, next.state().properties().get(CandleRules.FACING));
                        burnouts.increment();
                    } else if (effects().setState(b, next.state())) {
                        burnouts.increment();
                    }
                }
            };
            return new AsyncDecisionPipeline<>("candle/" + world.getName(), regions.apply(world), access, workers, budget,
                (pos, nanos) -> CompletableFuture.completedFuture(new Snap(pos, access.read(pos), calendar.getAsLong(), nanos)),
                snap -> decide(snap, candleTicks(), jackTicks()));
        });
    }

    /** The block at {@code block} as a {@link Cell}. Region thread. */
    Cell read(Block block) {
        Optional<BlockStateRef> state = blocks.stateAt(block);
        Optional<Family> family = state.flatMap(s -> CandleRules.family(s.id()));
        if (family.isEmpty()) {
            Optional<String> carved = state.isEmpty() ? effects().carvedPumpkin(block) : Optional.empty();
            return carved.map(CandleService::carved).orElse(Cell.NONE);
        }
        long lit = devices.inspect(block).flatMap(DeviceService.Inspection::record)
            .map(r -> r.state() instanceof CandleDevice.State s ? s.litTick() : CandleRules.NEVER_LIT)
            .orElse(CandleRules.NEVER_LIT);
        return new Cell(family.get(), state.get(), lit);
    }

    // ---- fire start and douse ----------------------------------------------------------------------------------------

    /**
     * TFC {@code onFireStart}'s branches as a {@link FireLightable}: any strength; a candle or candle cake (not
     * waterlogged) becomes lit and its counter restarts; vanilla's carved pumpkin becomes the jack-o'-lantern (facing
     * kept) and a jack-o'-lantern restarts its counter. Each cancels the start.
     */
    public FireLightable lightable() {
        return ctx -> light(ctx.block());
    }

    /** {@link #lightable}'s step on {@code block}; also the held torch's weak start. Region thread of the block. */
    public boolean light(Block block) {
        Optional<BlockStateRef> state = blocks.stateAt(block);
        Optional<Family> family = state.flatMap(s -> CandleRules.family(s.id()));
        if (family.isEmpty()) {
            Optional<String> facing = state.isEmpty() ? effects().carvedPumpkin(block) : Optional.empty();
            if (facing.isEmpty() || !blocks.exists(CandleRules.JACK_O_LANTERN)) {
                return false;
            }
            if (!effects().place(block, BlockStateRef.of(CandleRules.JACK_O_LANTERN).with(CandleRules.FACING, facing.get()))) {
                return false;
            }
            stamp(block, Family.JACK_O_LANTERN);
            return true;
        }
        BlockStateRef s = state.get();
        boolean waterlogged = "true".equals(s.properties().get(CandleRules.WATERLOGGED));
        if (!CandleRules.lights(family.get(), waterlogged)) {
            return false;
        }
        if (family.get() != Family.JACK_O_LANTERN && !"true".equals(s.properties().get(CandleRules.LIT))) {
            effects().setState(block, s.with(CandleRules.LIT, "true"));
        }
        stamp(block, family.get());
        return true;
    }

    /** TFC {@code TickCounterBlockEntity.reset}: the counter is now, and the burnout callback is queued. */
    private void stamp(Block block, Family family) {
        devices.edit(block, device.kind(family), (record, now) -> {
            device.queueBurnout(block, family, now, now);
            return record.withState(new CandleDevice.State(now));
        });
    }

    /**
     * TFC {@code onFireStop}: a lit candle or candle cake is put out ({@code AbstractCandleBlock.extinguish}, the counter
     * kept), a jack-o'-lantern becomes vanilla's carved pumpkin. Returns whether it was handled. For PAR-HEAT-13 (water
     * bucket, splash potions). Region thread.
     */
    public boolean douse(Block block) {
        Cell cell = read(block);
        if (cell.family() == null || !CandleRules.douses(cell.family(), CandleDevice.lit(cell.family(), cell.state()))) {
            return false;
        }
        if (cell.family() == Family.JACK_O_LANTERN) {
            effects().placeCarvedPumpkin(block, cell.state().properties().getOrDefault(CandleRules.FACING, "north"));
        } else {
            effects().setState(block, cell.state().with(CandleRules.LIT, "false"));
            effects().sound(block, CandleEffects.Sound.EXTINGUISH);
        }
        return true;
    }

    // ---- candle on cake, carving -------------------------------------------------------------------------------------

    /**
     * TFC {@code CandleBlockItem.useOn}: a candle item {@code candleItem} used on a cake with no bite taken becomes that
     * candle's candle cake (unlit, no counter); the candle is used up unless the player is in creative. Returns whether
     * it was handled (then the candle is not placed). Region thread of the cake.
     */
    public boolean addCandle(Block cake, Player player, EquipmentSlot hand, String candleItem) {
        Optional<BlockStateRef> state = blocks.stateAt(cake);
        Optional<String> candleCake = CandleRules.candleCakeOf(candleItem);
        if (state.isEmpty() || !CandleRules.CAKE.equals(state.get().id()) || candleCake.isEmpty()) {
            return false;
        }
        int bites;
        try {
            bites = Integer.parseInt(state.get().properties().getOrDefault(CandleRules.BITES, "0"));
        } catch (NumberFormatException e) {
            return false;
        }
        if (!CandleRules.takesCandle(bites) || !effects().place(cake, BlockStateRef.of(candleCake.get()))) {
            return false;
        }
        if (player != null && player.getGameMode() != GameMode.CREATIVE) {
            effects().takeOne(player, hand);
        }
        effects().sound(cake, CandleEffects.Sound.ADD_CANDLE);
        return true;
    }

    /** What a knife or shears click on a block did ({@link #carve}). */
    public enum Carve {
        /** Not TFC's pumpkin, or carving is off: nothing happened. */
        PASS,
        /** TFC's pumpkin, but rotten: the click is consumed and nothing is carved (TFC {@code sidedSuccess}). */
        ROTTEN,
        /** Carved into vanilla's carved pumpkin. */
        CARVED
    }

    /**
     * TFC {@code TFCPumpkinBlock.useItemOn} with a knife or shears: a fresh pumpkin becomes vanilla's carved pumpkin facing
     * the clicked face (on the top or bottom: the player), the tool takes one point of wear. Region thread.
     *
     * @param clickedFace  the clicked face, lower case
     * @param playerFacing the player's horizontal facing, lower case
     */
    public Carve carve(Block block, String clickedFace, String playerFacing, Player player, EquipmentSlot hand,
                       boolean enabled, Rotting rotting) {
        Optional<BlockStateRef> state = blocks.stateAt(block);
        if (!enabled || state.isEmpty() || !CandleRules.PUMPKIN.equals(state.get().id())) {
            return Carve.PASS;
        }
        if (rotting.rotten(block)) {
            return Carve.ROTTEN;
        }
        effects().sound(block, CandleEffects.Sound.CARVE);
        effects().placeCarvedPumpkin(block, CandleRules.carvedFacing(clickedFace, playerFacing));
        if (player != null && hand != null) {
            effects().damageHeld(player, hand);
        }
        return Carve.CARVED;
    }

    // ---- metrics -------------------------------------------------------------------------------------------------------

    /** Blocks put out (or turned back into carved pumpkins) by the pipeline. */
    public long burnouts() {
        return burnouts.sum();
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
