/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/LogPileBlock.java @ c3b4d9536 (tick, updateShape, useItemOn,
 *       extractFromTop, insertAndPushUp, InventoryRemoveBehavior.DROP, AXIS, COUNT),
 *       src/main/java/net/dries007/tfc/common/blocks/devices/BurningLogPileBlock.java @ c3b4d9536 (lightLogPile,
 *       tryLightLogPile, tryLightNearby, isValidCoverBlock, randomTick, TICK_DELAY),
 *       src/main/java/net/dries007/tfc/common/blockentities/LogPileBlockEntity.java @ c3b4d9536 (isItemValid,
 *       getSlotStackLimit, setAndUpdateSlots),
 *       src/main/java/net/dries007/tfc/common/blockentities/BurningLogPileBlockEntity.java @ c3b4d9536 (serverTick,
 *       light, createCharcoal, isPileBlock)
 */
package dev.tfp.paper.charcoal;

import dev.tfp.core.charcoal.BurningPileData;
import dev.tfp.core.charcoal.CharcoalPit;
import dev.tfp.core.charcoal.CharcoalSpread;
import dev.tfp.core.charcoal.LogPile;
import dev.tfp.core.charcoal.LogPileColumn;
import dev.tfp.core.charcoal.LogPileData;
import dev.tfp.core.device.DeviceRecord;
import dev.tfp.core.device.ItemBlob;
import dev.tfp.paper.device.DeviceContext;
import dev.tfp.paper.device.DeviceHandler;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceResult;
import dev.tfp.paper.device.StackCodec;
import dev.tfp.paper.integration.BlockStateRef;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * The log pile and the burning log pile as two device kinds (PAR-PROC-10) on the device framework, with the charcoal pit
 * rules of {@code core/charcoal}.
 *
 * <p>Log pile ({@value #LOG_PILE}, state {@code axis} x/z and {@code count} 1..16): sixteen slots of one log each
 * ({@code #tfp:log_pile_logs}). A sneak click with a log inserts one (a quick second sneak click inserts as many as fit)
 * and pushes up into a new pile above a full one ({@link #insert}, from {@link CharcoalListener}); a plain click takes a
 * log (a quick second click: all logs) from the top pile of the column. Without a sturdy block or a pile below it breaks
 * and drops its logs; fire next to it, a strong fire start or a neighbouring burning pile's sweep (30 ticks later)
 * lights it ({@link #light}).
 *
 * <p>Burning log pile ({@value #BURNING}, the same {@code axis}/{@code count}): stores its logs and the calendar tick it
 * was lit at ({@link BurningPileData}). When lit and on every random tick ({@link PitClock}) it sweeps its six sides
 * ({@link CharcoalSpread}). Once the top pile of a column has burned for more than {@code charcoalTicks} calendar ticks,
 * the column turns into charcoal piles ({@link CharcoalPit#convert}); the check runs at every callback of the pile, so a
 * pile that was unloaded converts when its chunk loads again.
 *
 * <p>Only a device handler may change a record, so actions started outside one (the listener, fire starting, the clock,
 * one pile acting on another) are handed to the pile's neighbour callback as a pending action, as the firepit does. World
 * writes go through {@link CharcoalEffects} and run after the callback.
 */
public final class CharcoalPitDevice {
    public static final String LOG_PILE = "tfp:log_pile";
    public static final String BURNING = "tfp:burning_log_pile";
    public static final String CHARCOAL_PILE = "tfp:charcoal_pile";
    public static final String AXIS = "axis";
    public static final String COUNT = "count";
    public static final String LAYERS = "layers";
    /** TFC {@code BurningLogPileBlock.TICK_DELAY}. */
    public static final int LIGHT_DELAY = CharcoalSpread.LOG_PILE_LIGHT_DELAY;
    /** The most piles one column walk visits (the height of a vanilla overworld). */
    static final int MAX_COLUMN = 384;

    /** TFC {@code Helpers.DIRECTIONS} / {@code Direction.values()} order. */
    static final List<BlockFace> SIDES = List.of(BlockFace.DOWN, BlockFace.UP, BlockFace.NORTH, BlockFace.SOUTH,
        BlockFace.WEST, BlockFace.EAST);

    /**
     * What the devices read from the rest of the plugin.
     *
     * @param log           whether a stack is in {@code #tfp:log_pile_logs} (TFC {@code isItemValid})
     * @param state         the custom block state at a block, empty for a vanilla block
     * @param exists        whether a custom block id is loaded
     * @param sturdyTop     whether a block has a sturdy (full) top face (TFC {@code isFaceSturdy(UP)} / {@code isFaceFull})
     * @param fire          whether a block is in {@code #minecraft:fire}
     * @param cover         TFC {@code isValidCoverBlock} as a cover kind: the block on a side and its face towards the pile
     * @param records       the stored record of the device at a block (another pile of the column)
     * @param random        the draws (charcoal amount, the 1-in-7 burn)
     * @param charcoalTicks TFC {@code charcoalTicks}
     * @param effects       the world writes
     */
    public record Services(Predicate<ItemStack> log, Function<Block, Optional<BlockStateRef>> state,
            Predicate<String> exists, Predicate<Block> sturdyTop, Predicate<Block> fire,
            BiFunction<Block, BlockFace, CharcoalSpread.Cover> cover, Function<Block, Optional<DeviceRecord<?>>> records,
            Supplier<RandomGenerator> random, int charcoalTicks, CharcoalEffects effects) {
        public Services {
            Objects.requireNonNull(log, "log");
            Objects.requireNonNull(state, "state");
            Objects.requireNonNull(exists, "exists");
            Objects.requireNonNull(sturdyTop, "sturdyTop");
            Objects.requireNonNull(fire, "fire");
            Objects.requireNonNull(cover, "cover");
            Objects.requireNonNull(records, "records");
            Objects.requireNonNull(random, "random");
            Objects.requireNonNull(effects, "effects");
        }
    }

    // ---- pending actions ---------------------------------------------------------------------------------------

    private static final class InsertOp {
        final ItemStack log;
        final int count;
        final boolean placement;
        int inserted;

        InsertOp(ItemStack log, int count, boolean placement) {
            this.log = log;
            this.count = count;
            this.placement = placement;
        }
    }

    private record SetSlots(List<ItemBlob> slots, LogPileData data) {
    }

    private record ExtractOp(Player player, boolean all) {
    }

    private static final class LightOp {
        boolean lit;
    }

    private record LitOp(int logs) {
    }

    private record SweepOp(int times) {
    }

    private record Pos(UUID world, int x, int y, int z) {
        static Pos of(Block b) {
            return new Pos(b.getWorld().getUID(), b.getX(), b.getY(), b.getZ());
        }
    }

    private final Services services;
    private final StackCodec stacks;
    private final PitClock clock;
    private final Consumer<Block> callback;
    private final Map<Pos, Object> pending = new ConcurrentHashMap<>();
    private final DeviceKind<LogPileData> logPileKind;
    private final DeviceKind<BurningPileData> burningKind;

    /**
     * @param callback the device router's neighbour update ({@code DeviceService::neighbourChanged})
     */
    public CharcoalPitDevice(Services services, StackCodec stacks, PitClock clock, Consumer<Block> callback) {
        this.services = Objects.requireNonNull(services, "services");
        this.stacks = Objects.requireNonNull(stacks, "stacks");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.callback = Objects.requireNonNull(callback, "callback");
        this.logPileKind = DeviceKind.builder(LOG_PILE, LogPileData.CODEC, LogPileData.FRESH)
            .slots(LogPile.SLOTS)
            .slotLimit(1)
            .filter((slot, stack) -> services.log().test(stack))
            .removal(DeviceKind.RemoveBehaviour.NOOP)
            .handler(new LogPileHandler())
            .build();
        this.burningKind = DeviceKind.builder(BURNING, BurningPileData.CODEC, BurningPileData.FRESH)
            .removal(DeviceKind.RemoveBehaviour.NOOP)
            .handler(new BurningHandler())
            .build();
    }

    public DeviceKind<LogPileData> logPileKind() {
        return logPileKind;
    }

    public DeviceKind<BurningPileData> burningKind() {
        return burningKind;
    }

    public PitClock clock() {
        return clock;
    }

    /** The log pile state. */
    public static BlockStateRef logPile(String axis, int count) {
        return BlockStateRef.of(LOG_PILE).with(AXIS, axis).with(COUNT, Integer.toString(count));
    }

    /** The burning log pile state. */
    public static BlockStateRef burning(String axis, int count) {
        return BlockStateRef.of(BURNING).with(AXIS, axis).with(COUNT, Integer.toString(count));
    }

    /** The charcoal pile state. */
    public static BlockStateRef charcoalPile(int layers) {
        return BlockStateRef.of(CHARCOAL_PILE).with(LAYERS, Integer.toString(layers));
    }

    // ---- entry points (region thread of the block) -------------------------------------------------------------

    /**
     * InteractionManager, holding a log and sneak clicking a log pile: {@code insertAndPushUp} from that pile (all that
     * fit on a quick second click). Returns the logs taken from {@code held} (the caller shrinks the hand).
     */
    public int insert(Block pile, ItemStack held) {
        if (!isLogPile(pile) || StackCodec.isEmpty(held) || !services.log().test(held)) {
            return 0;
        }
        return run(pile, new InsertOp(one(held), held.getAmount(), true)).inserted;
    }

    /**
     * InteractionManager, holding a log and sneak clicking another block: the pile placed at {@code at} on {@code axis},
     * then {@code insertOne}. The caller has checked the placement rules. Returns whether one log went in.
     */
    public boolean placePile(Block at, String axis, ItemStack held) {
        if (StackCodec.isEmpty(held) || !services.log().test(held) || !services.exists().test(LOG_PILE)) {
            return false;
        }
        if (!services.effects().placeNow(at, logPile(axis, 1))) {
            return false;
        }
        InsertOp op = run(at, new InsertOp(one(held), 1, false));
        if (op.inserted == 0) {
            services.effects().breakBlock(at);
            return false;
        }
        return true;
    }

    /** TFC {@code lightLogPile}. Returns whether the log pile at {@code block} was lit. */
    public boolean light(Block block) {
        if (!isLogPile(block) || !services.exists().test(BURNING)) {
            return false;
        }
        return run(block, new LightOp()).lit;
    }

    /** {@link PitClock}: {@code times} random ticks of the burning pile at {@code block}, then the conversion check. */
    public void randomTicks(Block block, int times) {
        if (times > 0) {
            run(block, new SweepOp(times));
        } else {
            callback.accept(block);
        }
    }

    private <T> T run(Block block, T action) {
        Pos key = Pos.of(block);
        pending.put(key, action);
        try {
            callback.accept(block);
        } finally {
            pending.remove(key, action);
        }
        return action;
    }

    private Object take(Block block) {
        return pending.remove(Pos.of(block));
    }

    private Optional<String> idAt(Block block) {
        return services.state().apply(block).map(BlockStateRef::id);
    }

    private boolean isLogPile(Block block) {
        return idAt(block).filter(LOG_PILE::equals).isPresent();
    }

    private static ItemStack one(ItemStack held) {
        ItemStack one = held.clone();
        one.setAmount(1);
        return one;
    }

    private static String axisOf(BlockStateRef state) {
        return state.properties().getOrDefault(AXIS, "x");
    }

    /** The log pile's state after its slots changed: its count, or air when empty. */
    private void showPile(Block block, String axis, List<ItemBlob> slots) {
        int n = LogPileColumn.count(nullable(slots));
        if (n == 0) {
            services.effects().air(block, LOG_PILE);
        } else {
            services.effects().show(block, logPile(axis, n));
        }
    }

    private static List<ItemBlob> nullable(List<ItemBlob> slots) {
        List<ItemBlob> out = new ArrayList<>(slots.size());
        for (ItemBlob b : slots) {
            out.add(b == null || b.isEmpty() ? null : b);
        }
        return out;
    }

    private static List<ItemBlob> blobs(List<ItemBlob> slots) {
        List<ItemBlob> out = new ArrayList<>(slots.size());
        for (ItemBlob b : slots) {
            out.add(b == null ? ItemBlob.EMPTY : b);
        }
        return out;
    }

    // ---- log pile ---------------------------------------------------------------------------------------------

    private final class LogPileHandler implements DeviceHandler<LogPileData> {
        /** TFC {@code useItemOn} without sneaking: extract from the top pile, all on a quick second click. */
        @Override
        public DeviceResult<LogPileData> useWithItem(DeviceContext<LogPileData> ctx) {
            Player player = ctx.player();
            if (player == null || player.isSneaking()) {
                return DeviceResult.pass();
            }
            long now = ctx.now();
            DeviceRecord<LogPileData> record = ctx.record();
            boolean all = record.state().extractionDoubleClick(now);
            Block top = ctx.block();
            for (int i = 0; i < MAX_COLUMN && isLogPile(top.getRelative(BlockFace.UP)); i++) {
                top = top.getRelative(BlockFace.UP);
            }
            if (top.equals(ctx.block())) {
                record = extract(ctx.block(), axisOf(ctx.state()), record, player, all);
            } else {
                run(top, new ExtractOp(player, all));
            }
            return DeviceResult.consume(record.withState(record.state().clicked(now, false)));
        }

        @Override
        public DeviceResult<LogPileData> neighbourChanged(DeviceContext<LogPileData> ctx) {
            Block block = ctx.block();
            DeviceRecord<LogPileData> record = ctx.record();
            Object p = take(block);
            DeviceRecord<LogPileData> next;
            if (p instanceof InsertOp op) {
                next = insertColumn(ctx, op);
            } else if (p instanceof SetSlots s) {
                next = record.withSlots(blobs(s.slots()));
                if (s.data() != null) {
                    next = next.withState(s.data());
                }
            } else if (p instanceof ExtractOp e) {
                next = extract(block, axisOf(ctx.state()), record, e.player(), e.all());
            } else if (p instanceof LightOp l) {
                next = lightNow(ctx, l);
            } else {
                // updateShape: no sturdy block and no pile below -> air (the logs drop); fire next to it lights it
                Block below = block.getRelative(BlockFace.DOWN);
                if (!services.sturdyTop().test(below) && !isLogPile(below)) {
                    services.effects().breakBlock(block);
                    return DeviceResult.pass();
                }
                next = record;
                for (BlockFace side : SIDES) {
                    if (services.fire().test(block.getRelative(side))) {
                        next = lightNow(ctx, new LightOp());
                        break;
                    }
                }
            }
            return next.equals(record) ? DeviceResult.pass() : DeviceResult.update(next);
        }

        /** TFC {@code InventoryRemoveBehavior.DROP}: the logs drop, unless the pile was lit (its slots are cleared then). */
        @Override
        public void removed(DeviceContext<LogPileData> ctx) {
            if (idAt(ctx.block()).filter(BURNING::equals).isPresent()) {
                return;
            }
            for (ItemStack stack : ctx.inventory().contents()) {
                services.effects().spawn(ctx.block(), stack);
            }
        }

        /** {@code insertAndPushUp} over the column standing on the clicked pile. */
        private DeviceRecord<LogPileData> insertColumn(DeviceContext<LogPileData> ctx, InsertOp op) {
            long now = ctx.now();
            Block block = ctx.block();
            String axis = axisOf(ctx.state());
            DeviceRecord<LogPileData> record = ctx.record();
            boolean all = op.placement && record.state().placementDoubleClick(now);
            List<List<ItemBlob>> column = new ArrayList<>();
            List<Block> blocks = new ArrayList<>();
            column.add(nullable(record.slots()));
            blocks.add(block);
            Block cursor = block.getRelative(BlockFace.UP);
            while (column.size() < MAX_COLUMN && isLogPile(cursor)) {
                Optional<DeviceRecord<?>> r = services.records().apply(cursor);
                column.add(nullable(r.map(DeviceRecord::slots).orElseGet(() -> emptySlots())));
                blocks.add(cursor);
                cursor = cursor.getRelative(BlockFace.UP);
            }
            boolean airAbove = cursor.getType().isAir() && services.state().apply(cursor).isEmpty();
            LogPileColumn.Insert<ItemBlob> result = LogPileColumn.insert(column, airAbove, stacks.encode(op.log), op.count,
                all);
            op.inserted = result.inserted();
            // dumbInsert plays the place sound at each pile that took logs
            for (int i = 0; i < result.piles().size(); i++) {
                int before = i < column.size() ? LogPileColumn.count(column.get(i)) : 0;
                if (LogPileColumn.count(result.piles().get(i)) > before) {
                    services.effects().sound(i < blocks.size() ? blocks.get(i) : cursor,
                        CharcoalEffects.Sound.LOG_PLACE);
                }
            }
            for (int i = 1; i < column.size(); i++) {
                List<ItemBlob> slots = result.piles().get(i);
                if (!slots.equals(column.get(i))) {
                    run(blocks.get(i), new SetSlots(List.copyOf(blobs(slots)), null));
                    showPile(blocks.get(i), axis, slots);
                }
            }
            if (result.newPileOnTop()) {
                Block top = cursor;
                List<ItemBlob> slots = List.copyOf(blobs(result.piles().get(column.size())));
                services.effects().place(top, logPile(axis, LogPileColumn.count(nullable(slots))), null,
                    () -> run(top, new SetSlots(slots, LogPileData.placedAt(now))));
            }
            List<ItemBlob> own = result.piles().get(0);
            showPile(block, axis, own);
            LogPileData data = op.placement ? record.state().clicked(now, true) : LogPileData.placedAt(now);
            return record.withSlots(blobs(own)).withState(data);
        }

        /** {@code extractFromTop} on this (the top) pile; the logs go to the player. */
        private DeviceRecord<LogPileData> extract(Block block, String axis, DeviceRecord<LogPileData> record, Player player,
                boolean all) {
            LogPileColumn.Extract<ItemBlob> result = LogPileColumn.extractFromTop(nullable(record.slots()), all);
            if (result.extracted().isEmpty()) {
                return record;
            }
            for (ItemBlob b : result.extracted()) {
                services.effects().give(player, stacks.decode(b));
            }
            showPile(block, axis, result.top());
            return record.withSlots(blobs(result.top()));
        }

        /**
         * {@code lightLogPile}: the logs are counted and cleared, the block becomes a burning pile with the same axis and
         * count, which is then lit with those logs.
         */
        private DeviceRecord<LogPileData> lightNow(DeviceContext<LogPileData> ctx, LightOp l) {
            DeviceRecord<LogPileData> record = ctx.record();
            int logs = LogPileColumn.count(nullable(record.slots()));
            Block block = ctx.block();
            BlockStateRef state = ctx.state();
            String count = state.properties().getOrDefault(COUNT, Integer.toString(Math.max(1, logs)));
            services.effects().place(block, BlockStateRef.of(BURNING).with(AXIS, axisOf(state)).with(COUNT, count), LOG_PILE,
                () -> run(block, new LitOp(logs)));
            l.lit = true;
            return record.cleared();
        }
    }

    private static List<ItemBlob> emptySlots() {
        List<ItemBlob> out = new ArrayList<>(LogPile.SLOTS);
        for (int i = 0; i < LogPile.SLOTS; i++) {
            out.add(ItemBlob.EMPTY);
        }
        return out;
    }

    // ---- burning log pile -------------------------------------------------------------------------------------

    private final class BurningHandler implements DeviceHandler<BurningPileData> {
        @Override
        public DeviceResult<BurningPileData> neighbourChanged(DeviceContext<BurningPileData> ctx) {
            Block block = ctx.block();
            long now = ctx.now();
            DeviceRecord<BurningPileData> record = ctx.record();
            Object p = take(block);
            boolean gone = false;
            if (p instanceof LitOp lit) {
                // light(logs): resetCounter, then lightLogPile's BLAZE_SHOOT and tryLightNearby
                record = record.withState(BurningPileData.lit(lit.logs(), now));
                services.effects().sound(block, CharcoalEffects.Sound.LIGHT);
                gone = sweep(block);
            } else if (p instanceof SweepOp s) {
                for (int i = 0; i < s.times() && !gone; i++) {
                    gone = sweep(block);
                }
            }
            if (gone) {
                clock.untrack(block);
            } else {
                serverTick(block, record.state(), now);
            }
            return record.equals(ctx.record()) ? DeviceResult.pass() : DeviceResult.update(record);
        }

        @Override
        public void removed(DeviceContext<BurningPileData> ctx) {
            clock.untrack(ctx.block());
        }

        /** {@code serverTick}: the top pile of a column converts it once its time is up; otherwise list the next check. */
        private void serverTick(Block block, BurningPileData data, long now) {
            Optional<String> above = idAt(block.getRelative(BlockFace.UP));
            boolean pileAbove = above.filter(id -> id.equals(BURNING) || id.equals(CHARCOAL_PILE)).isPresent();
            if (!pileAbove && data.converted(now, services.charcoalTicks())) {
                clock.untrack(block);
                createCharcoal(block, data);
                return;
            }
            clock.track(block, pileAbove ? CharcoalPit.NEVER : data.convertTick(services.charcoalTicks()));
        }

        /** {@code tryLightNearby}. Returns whether the pile itself became fire. */
        private boolean sweep(Block block) {
            List<CharcoalSpread.Cover> covers = new ArrayList<>(SIDES.size());
            for (BlockFace side : SIDES) {
                covers.add(services.cover().apply(block.getRelative(side), side.getOppositeFace()));
            }
            RandomGenerator r = services.random().get();
            for (CharcoalSpread.Step step : CharcoalSpread.sweep(covers, r::nextInt)) {
                Block side = block.getRelative(SIDES.get(step.side()));
                switch (step.action()) {
                    case LIGHT_LOG_PILE -> services.effects().later(side, LIGHT_DELAY, () -> light(side));
                    case PLACE_FIRE -> services.effects().fire(side, null);
                    case FIRE_REPLACES_PILE -> {
                        services.effects().fire(block, BURNING);
                        return true;
                    }
                    default -> throw new IllegalStateException(step.action().name());
                }
            }
            return false;
        }

        /** {@code createCharcoal} from the top pile: the column below it and the charcoal pile under it. */
        private void createCharcoal(Block top, BurningPileData data) {
            List<Integer> piles = new ArrayList<>();
            piles.add(data.logs());
            Block cursor = top.getRelative(BlockFace.DOWN);
            while (piles.size() < MAX_COLUMN && idAt(cursor).filter(BURNING::equals).isPresent()) {
                piles.add(services.records().apply(cursor).map(DeviceRecord::state)
                    .filter(BurningPileData.class::isInstance).map(s -> ((BurningPileData) s).logs()).orElse(0));
                cursor = cursor.getRelative(BlockFace.DOWN);
            }
            Block under = cursor;
            int layersBelow = services.state().apply(under).filter(s -> s.id().equals(CHARCOAL_PILE))
                .map(s -> layers(s)).orElse(-1);
            RandomGenerator r = services.random().get();
            CharcoalPit.Conversion c = CharcoalPit.convert(piles, r::nextFloat, false, layersBelow);
            if (layersBelow >= 0 && c.layersBelow() != layersBelow) {
                services.effects().show(under, charcoalPile(c.layersBelow()));
            }
            for (int i = 0; i < c.layers().size(); i++) {
                Block cell = under.getRelative(BlockFace.UP, i + 1);
                clock.untrack(cell); // the lower piles were listed with NEVER
                int layers = c.layers().get(i);
                if (layers > 0) {
                    services.effects().place(cell, charcoalPile(layers), BURNING, null);
                } else {
                    services.effects().air(cell, BURNING);
                }
            }
        }
    }

    /** The {@code layers} of a charcoal pile state ({@code 1..8}). */
    static int layers(BlockStateRef state) {
        try {
            return Math.max(1, Math.min(CharcoalPit.MAX_LAYERS, Integer.parseInt(state.properties().getOrDefault(LAYERS, "1"))));
        } catch (NumberFormatException e) {
            return 1;
        }
    }
}
