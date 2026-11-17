/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/FireboxBlock.java @ c3b4d9536 (useItemOn, LIT,
 *       InventoryRemoveBehavior.DROP),
 *       src/main/java/net/dries007/tfc/common/blockentities/FireboxBlockEntity.java @ c3b4d9536 (serverTick: provideHeatTo,
 *       cascadeFuelSlots, updateOperableBlocks; isItemValid, getSlotStackLimit, light, extinguish),
 *       src/main/java/net/dries007/tfc/ForgeEventHandler.java @ c3b4d9536 (onFireStart: a strong start lights an unlit
 *       firebox; onFireStop: a douse extinguishes it),
 *       src/main/java/net/dries007/tfc/util/InteractionManager.java @ c3b4d9536 (SHOVEL_DOUSE)
 */
package dev.tfp.paper.firebox;

import dev.tfp.core.cooking.FirepitRules;
import dev.tfp.core.device.DeviceRecord;
import dev.tfp.core.device.ItemBlob;
import dev.tfp.core.firebox.FireboxData;
import dev.tfp.core.firebox.FireboxKiln;
import dev.tfp.core.firebox.FireboxScreenLayout;
import dev.tfp.core.firebox.FireboxSim;
import dev.tfp.core.fuel.Fuel;
import dev.tfp.paper.device.DeviceContext;
import dev.tfp.paper.device.DeviceHandler;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceResult;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.device.StackCodec;
import dev.tfp.paper.integration.BlockStateRef;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * PAR-HEAT-12: the firebox as a device kind ({@code tfp:firebox}): TFC's {@code FireboxBlock} + {@code FireboxBlockEntity}
 * on the device framework, with the burn, temperature and kiln timer replayed lazily by {@link FireboxSim} on every access.
 * Sixteen fuel slots of one item each; fuel is taken from slot 0 and the rest cascade down.
 *
 * <ul>
 *   <li>Right click, in TFC's {@code useItemOn} order: a fuel goes into the first empty slot (TFC {@code stack.split(1)},
 *       in every game mode); an empty hand while sneaking takes slot 0 back; otherwise the firebox screen opens. As for
 *       the firepit, a shovel or a water bucket douses a warm firebox on a plain click before the screen step (TFC's
 *       douse is a sneak click, which CraftEngine does not route to devices; ledger divergence).</li>
 *   <li>Lit by a strong fire start on an unlit firebox ({@link #lightAt}). It goes out, cold at once, when the fuel runs
 *       out; a callback is queued for that tick.</li>
 *   <li>Heat: every tick it burns, TFC hands the device temperature to the block above
 *       ({@code HeatCapability.provideHeatTo}). Here each catch-up collects those runs and {@link #pushHeat} gives them to
 *       the {@link HeatConsumers.Consumer} registered for the block above, if any; while such a consumer stands above a
 *       lit firebox the firebox re-runs every {@link #HEAT_PERIOD} ticks so the consumer stays supplied.</li>
 *   <li>The kiln flood fill ({@link FireboxKiln}) runs at every access and keeps TFC's {@code heatingCount} and timer
 *       for the screen. Heating the placed items inside the kiln waits for placed items (follow-up PAR-HEAT-32).</li>
 * </ul>
 */
public final class FireboxDevice {
    public static final String ID = "tfp:firebox";
    /** TFC {@code FireboxBlock.LIT}. */
    public static final String LIT = "lit";
    /** How often a lit firebox with a heat consumer above re-runs to supply it, in ticks. */
    public static final long HEAT_PERIOD = 20;

    /** A constant run of heat given to the block above (TFC {@code provideHeatTo} each tick of it). */
    public record HeatRun(long fromTick, long toTick, float temperature) {
    }

    /**
     * What the device reads from the rest of the plugin.
     *
     * @param fuel      TFC {@code Fuel.get(stack)}
     * @param douse     what the held stack does as a douser
     * @param kiln      the kiln flood fill's view of the world around a firebox (region thread)
     * @param consumers the heat consumers ({@code IHeatConsumer} of the block above)
     * @param blockId   the CraftEngine block id at a block, if any (region thread)
     * @param effects   the world effects
     * @param open      opens the firebox screen (TFC {@code openMenu}); must open it later, never inline
     */
    public record Services(Function<ItemStack, Optional<Fuel>> fuel, Function<ItemStack, FireboxEffects.Douse> douse,
            Function<Block, FireboxKiln.World> kiln, HeatConsumers consumers, Function<Block, Optional<String>> blockId,
            FireboxEffects effects, BiConsumer<Player, Block> open) {
        public Services {
            Objects.requireNonNull(fuel, "fuel");
            Objects.requireNonNull(douse, "douse");
            Objects.requireNonNull(kiln, "kiln");
            Objects.requireNonNull(consumers, "consumers");
            Objects.requireNonNull(blockId, "blockId");
            Objects.requireNonNull(effects, "effects");
            Objects.requireNonNull(open, "open");
        }
    }

    /** The heat runs of the last catch-up on this thread and the state it ended in. */
    private record Caught(FireboxData state, List<HeatRun> runs) {
    }

    /** The last catch-up on this thread, handed out by {@link #pushHeat}. */
    private static final ThreadLocal<Caught> RUNS = new ThreadLocal<>();

    private final Services services;
    private final StackCodec stacks;
    private final DeviceKind<FireboxData> kind;

    public FireboxDevice(Services services, StackCodec stacks) {
        this.services = Objects.requireNonNull(services, "services");
        this.stacks = Objects.requireNonNull(stacks, "stacks");
        this.kind = DeviceKind.builder(ID, FireboxData.CODEC, FireboxData.FRESH)
            .slots(FireboxSim.SLOTS)
            .slotLimit(slot -> FireboxSim.SLOT_LIMIT)
            .filter((slot, stack) -> isItemValid(stack))
            .advance(this::catchUp)
            .removal(DeviceKind.RemoveBehaviour.DROP)
            .handler(new Handler())
            .build();
    }

    public DeviceKind<FireboxData> kind() {
        return kind;
    }

    public Services services() {
        return services;
    }

    /** The block state of a firebox. */
    public static BlockStateRef state(boolean lit) {
        return BlockStateRef.of(ID).with(LIT, Boolean.toString(lit));
    }

    /** TFC {@code isItemValid}: any fuel. */
    public boolean isItemValid(ItemStack stack) {
        return !StackCodec.isEmpty(stack) && services.fuel().apply(stack).isPresent();
    }

    // ---- the lazy simulation ---------------------------------------------------------------------------

    /** The kind's catch-up: TFC's serverTick over {@code (from, to]}; the heat it gave is kept for {@link #pushHeat}. */
    DeviceRecord<FireboxData> catchUp(DeviceRecord<FireboxData> record, long from, long to) {
        List<HeatRun> runs = new ArrayList<>();
        FireboxSim.Result r = FireboxSim.advance(record.state(), from, to, queue(record), FirepitRules.DeviceConfig.DEFAULT,
            (a, b, t) -> runs.add(new HeatRun(a, b, t)));
        RUNS.set(runs.isEmpty() ? null : new Caught(r.data(), List.copyOf(runs)));
        return consume(record, r.consumed()).withState(r.data());
    }

    /** The non-empty slots in order (TFC takes slot 0 and cascades), as fuels. */
    List<Optional<Fuel>> queue(DeviceRecord<FireboxData> record) {
        List<Optional<Fuel>> out = new ArrayList<>(FireboxSim.SLOTS);
        for (int i = 0; i < FireboxSim.SLOTS; i++) {
            ItemBlob b = record.slot(i);
            if (!b.isEmpty()) {
                out.add(services.fuel().apply(stacks.decode(b)));
            }
        }
        return out;
    }

    /** Removes the first {@code n} non-empty slots, then TFC {@code cascadeFuelSlots}. */
    static DeviceRecord<FireboxData> consume(DeviceRecord<FireboxData> record, int n) {
        List<ItemBlob> left = new ArrayList<>(FireboxSim.SLOTS);
        int skipped = 0;
        for (int i = 0; i < FireboxSim.SLOTS; i++) {
            ItemBlob b = record.slot(i);
            if (b.isEmpty()) {
                continue;
            }
            if (skipped < n) {
                skipped++;
            } else {
                left.add(b);
            }
        }
        if (skipped == 0 && cascaded(record)) {
            return record;
        }
        List<ItemBlob> slots = new ArrayList<>(FireboxSim.SLOTS);
        for (int i = 0; i < FireboxSim.SLOTS; i++) {
            slots.add(i < left.size() ? left.get(i) : ItemBlob.EMPTY);
        }
        return record.withSlots(slots);
    }

    /** TFC {@code cascadeFuelSlots} on the record. */
    static DeviceRecord<FireboxData> cascade(DeviceRecord<FireboxData> record) {
        return consume(record, 0);
    }

    private static boolean cascaded(DeviceRecord<FireboxData> record) {
        boolean empty = false;
        for (int i = 0; i < FireboxSim.SLOTS; i++) {
            boolean e = record.slot(i).isEmpty();
            if (!e && empty) {
                return false;
            }
            empty |= e;
        }
        return true;
    }

    /**
     * TFC {@code provideHeatTo(level, pos.above(), DOWN, temperature)}: hands the heat runs of this thread's last catch-up
     * to the consumer above {@code firebox}, if any, and forgets them. Only when that catch-up ended in {@code caughtUp}
     * (the very state instance this firebox's access saw), so runs of another block never leak. Region thread.
     */
    public void pushHeat(Block firebox, FireboxData caughtUp) {
        Caught c = RUNS.get();
        if (c == null) {
            return;
        }
        RUNS.remove();
        if (c.state() != caughtUp) {
            return;
        }
        Block above = firebox.getRelative(BlockFace.UP);
        consumerAbove(firebox).ifPresent(consumer -> c.runs().forEach(run -> consumer.heat(above, run.fromTick(),
            run.toTick(), run.temperature())));
    }

    /** The heat consumer of the block above {@code firebox}. */
    Optional<HeatConsumers.Consumer> consumerAbove(Block firebox) {
        if (services.consumers().isEmpty()) {
            return Optional.empty();
        }
        return services.blockId().apply(firebox.getRelative(BlockFace.UP)).flatMap(services.consumers()::find);
    }

    /** TFC {@code updateOperableBlocks}: the kiln flood fill's size, restarting the timer when it changed. */
    FireboxData floodfill(Block block, FireboxData data, long now) {
        List<FireboxKiln.Pos> filled = FireboxKiln.floodfill(services.kiln().apply(block),
            new FireboxKiln.Pos(block.getX(), block.getY(), block.getZ()), data.temperature());
        return data.withHeatingCount(filled.size(), now);
    }

    /**
     * Lights the firebox at {@code block} (TFC {@code onFireStart}: a strong start on an unlit firebox, {@code light}).
     * Region thread of {@code block}. Returns whether it is lit after.
     */
    public boolean lightAt(DeviceService devices, Block block) {
        boolean[] lit = {false};
        FireboxData[] seen = {null};
        Optional<DeviceRecord<FireboxData>> stored = devices.edit(block, kind, (record, now) -> {
            seen[0] = record.state();
            if (record.state().lit()) {
                return record;
            }
            FireboxSim.Result r = FireboxSim.light(record.state(), queue(record));
            lit[0] = r.data().lit();
            DeviceRecord<FireboxData> next = consume(record, r.consumed()).withState(r.data());
            after(block, next, now);
            return next;
        });
        pushHeat(block, seen[0]);
        return stored.isPresent() && lit[0];
    }

    /** The state follows the record; a lit firebox queues its next callback. */
    private void after(Block block, DeviceRecord<FireboxData> record, long now) {
        FireboxData data = record.state();
        services.effects().show(block, data.lit());
        if (data.lit()) {
            long out = Math.max(1, FireboxSim.ticksUntilOut(data, queue(record)));
            services.effects().callbackIn(block, consumerAbove(block).isPresent() ? Math.min(out, HEAT_PERIOD) : out);
        }
    }

    // ---- reactions --------------------------------------------------------------------------------------

    private final class Handler implements DeviceHandler<FireboxData> {
        @Override
        public DeviceResult<FireboxData> useWithItem(DeviceContext<FireboxData> ctx) {
            Player player = ctx.player();
            if (player == null || ctx.hand() == null) {
                return DeviceResult.pass();
            }
            DeviceRecord<FireboxData> record = ctx.record();
            ItemStack stack = ctx.item();
            if (isItemValid(stack)) {
                // Helpers.insertAllSlots(inv, stack.split(1)): the first slot that takes it; a full firebox gives it back
                for (int i = 0; i < FireboxSim.SLOTS; i++) {
                    if (record.slot(i).isEmpty()) {
                        ItemStack one = stack.clone();
                        one.setAmount(1);
                        shrink(player, ctx.hand());
                        return finish(ctx, record.withSlot(i, stacks.encode(one)), true);
                    }
                }
                return finish(ctx, record, true);
            }
            if (StackCodec.isEmpty(stack) && player.isSneaking() && !record.slot(0).isEmpty()) {
                services.effects().give(player, stacks.decode(record.slot(0)));
                return finish(ctx, cascade(record.withSlot(0, ItemBlob.EMPTY)), true);
            }
            FireboxEffects.Douse douse = StackCodec.isEmpty(stack) ? FireboxEffects.Douse.NONE : services.douse().apply(stack);
            if (douse != FireboxEffects.Douse.NONE && record.state().temperature() > 0f) {
                // onFireStop: firebox.extinguish(state)
                if (player.getGameMode() != org.bukkit.GameMode.CREATIVE) {
                    services.effects().useDouse(player, ctx.hand(), douse);
                }
                return finish(ctx, record.withState(FireboxSim.extinguish(record.state())), true);
            }
            services.open().accept(player, ctx.block());
            return finish(ctx, record, true);
        }

        @Override
        public DeviceResult<FireboxData> placed(DeviceContext<FireboxData> ctx) {
            return finish(ctx, ctx.record(), false);
        }

        /** The burn-out and heat callbacks, and neighbour changes (the kiln's walls). */
        @Override
        public DeviceResult<FireboxData> neighbourChanged(DeviceContext<FireboxData> ctx) {
            return finish(ctx, ctx.record(), false);
        }

        private DeviceResult<FireboxData> finish(DeviceContext<FireboxData> ctx, DeviceRecord<FireboxData> record,
                boolean consumed) {
            Block block = ctx.block();
            DeviceRecord<FireboxData> next = record.withState(floodfill(block, record.state(), ctx.now()));
            boolean shownLit = "true".equals(ctx.state().properties().get(LIT));
            if (shownLit != next.state().lit()) {
                services.effects().show(block, next.state().lit());
            }
            if (next.state().lit()) {
                long out = Math.max(1, FireboxSim.ticksUntilOut(next.state(), queue(next)));
                services.effects().callbackIn(block, consumerAbove(block).isPresent() ? Math.min(out, HEAT_PERIOD) : out);
            }
            pushHeat(block, ctx.record().state());
            if (consumed) {
                return DeviceResult.consume(next);
            }
            return next.equals(ctx.record()) ? DeviceResult.pass() : DeviceResult.update(next);
        }
    }

    /** TFC {@code stack.split(1)} on the hand's stack. */
    static void shrink(Player player, EquipmentSlot hand) {
        ItemStack held = player.getInventory().getItem(hand);
        if (StackCodec.isEmpty(held)) {
            return;
        }
        if (held.getAmount() <= 1) {
            player.getInventory().setItem(hand, null);
        } else {
            held.setAmount(held.getAmount() - 1);
            player.getInventory().setItem(hand, held);
        }
    }

    /** The firebox screen's status line of {@code data} at {@code now}. */
    public static FireboxScreenLayout.Status status(FireboxData data, long now) {
        return FireboxScreenLayout.status(data, now);
    }
}
