/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/FirepitBlock.java @ c3b4d9536 (useItemOn, tryInsertLog, the
 *       AXIS / LIT / SMOKE_LEVEL state, InventoryRemoveBehavior.DROP),
 *       src/main/java/net/dries007/tfc/common/blocks/devices/BottomSupportedDeviceBlock.java @ c3b4d9536 (updateShape),
 *       src/main/java/net/dries007/tfc/common/blockentities/AbstractFirepitBlockEntity.java @ c3b4d9536 (isItemValid,
 *       getSlotStackLimit, cascadeFuelSlots, light, extinguish, ejectInventory with the ash),
 *       src/main/java/net/dries007/tfc/common/blockentities/FirepitBlockEntity.java @ c3b4d9536 (handleCooking,
 *       mergeOutputStack),
 *       src/main/java/net/dries007/tfc/ForgeEventHandler.java @ c3b4d9536 (onFireStart and onFireStop for the firepit),
 *       src/main/java/net/dries007/tfc/util/InteractionManager.java @ c3b4d9536 (SHOVEL_DOUSE)
 */
package dev.tfp.paper.firepit;

import dev.tfp.core.cooking.FirepitRules;
import dev.tfp.core.device.DeviceRecord;
import dev.tfp.core.device.ItemBlob;
import dev.tfp.core.firepit.FirepitData;
import dev.tfp.core.firepit.FirepitSim;
import dev.tfp.core.fuel.Fuel;
import dev.tfp.core.heat.HeatConfig;
import dev.tfp.core.heat.HeatState;
import dev.tfp.paper.device.DeviceContext;
import dev.tfp.paper.device.DeviceHandler;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceResult;
import dev.tfp.paper.device.StackCodec;
import dev.tfp.paper.heat.HeatPdc;
import dev.tfp.paper.integration.BlockStateRef;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;
import java.util.random.RandomGenerator;
import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * The firepit as a device kind (PAR-HEAT-06): TFC's {@code FirepitBlock} + {@code FirepitBlockEntity} on the device
 * framework, with the burn, temperature, ash, smoke and cooking replayed lazily by {@link FirepitSim} on every access
 * (the kind's catch-up). Seven slots as TFC: fuel 0..3, the input 4, the outputs 5 and 6.
 *
 * <p>Right click, in TFC's order: a shovel (or a water bucket, our addition) douses a warm firepit; a firepit fuel goes
 * into the first empty fuel slot; an unlit firepit with ash gives the ash; otherwise TFC opens the firepit screen. Until
 * the screen exists (PAR-HEAT-07) the last step is a stand-in: a heatable item goes into the empty input slot, an empty
 * hand takes the outputs (then the input) out.
 *
 * <p>Fire starting lights it ({@link #light}), and a fire starter on a stick and log pile builds one ({@link #build});
 * both go through the router's neighbour callback with a pending action, since only a handler may change a record.
 * While lit and loaded, a callback is queued at the tick the fuel runs out so the block turns unlit on time. The rain is
 * sampled at every callback and held until the next one.
 */
public final class FirepitDevice {
    public static final String ID = "tfp:firepit";
    public static final String LIT = "lit";
    public static final String SMOKE_LEVEL = "smoke_level";
    public static final String AXIS = "axis";
    public static final String WOOD_ASH = "tfp:powder/wood_ash";

    /** TFC {@code HeatingRecipe} lookup and conversion, as the firepit uses them. */
    public interface Cooker {
        /** The temperature the stack's heating recipe fires at, {@link Float#NaN} for none. */
        float recipeTemperature(ItemStack stack);

        /**
         * The item result of converting one {@code heated} item tested at {@code before} (TFC {@code assembleItem}; an
         * empty stack when the recipe makes no item), empty when the recipe does not fire.
         */
        Optional<ItemStack> convert(ItemStack heated, float before);
    }

    /**
     * What the device reads from the rest of the plugin.
     *
     * @param fuel        TFC {@code Fuel.get(stack)}
     * @param firepitFuel whether the stack is in {@code #tfc:firepit_fuel}
     * @param capacity    the heat capacity of a stack, {@code <= 0} when it cannot be heated
     * @param cooker      the heating recipes
     * @param douse       what the held stack does as a douser
     * @param raining     TFC {@code level.isRainingAt(pos)} at the firepit
     * @param canSurvive  TFC {@code BottomSupportedDeviceBlock.canSurvive}
     * @param woodAsh     a stack of {@code n} wood ash
     * @param random      the ash draws
     * @param heatConfig  the item heating and cooling modifiers
     * @param effects     the world effects
     */
    public record Services(Function<ItemStack, Optional<Fuel>> fuel, Predicate<ItemStack> firepitFuel,
            ToDoubleFunction<ItemStack> capacity, Cooker cooker, Function<ItemStack, FirepitEffects.Douse> douse,
            Predicate<Block> raining, Predicate<Block> canSurvive, IntFunction<ItemStack> woodAsh,
            Supplier<RandomGenerator> random, HeatConfig heatConfig, FirepitEffects effects) {
        public Services {
            Objects.requireNonNull(fuel, "fuel");
            Objects.requireNonNull(firepitFuel, "firepitFuel");
            Objects.requireNonNull(capacity, "capacity");
            Objects.requireNonNull(cooker, "cooker");
            Objects.requireNonNull(douse, "douse");
            Objects.requireNonNull(raining, "raining");
            Objects.requireNonNull(canSurvive, "canSurvive");
            Objects.requireNonNull(woodAsh, "woodAsh");
            Objects.requireNonNull(random, "random");
            Objects.requireNonNull(heatConfig, "heatConfig");
            Objects.requireNonNull(effects, "effects");
        }
    }

    /** A pending action for the next callback at a position. */
    private sealed interface Pending permits Light, Build {
    }

    private static final class Light implements Pending {
        boolean lit;
    }

    private record Build(List<ItemStack> logs) implements Pending {
    }

    private record Pos(UUID world, int x, int y, int z) {
        static Pos of(Block b) {
            return new Pos(b.getWorld().getUID(), b.getX(), b.getY(), b.getZ());
        }
    }

    private final Services services;
    private final StackCodec stacks;
    private final Map<Pos, Pending> pending = new ConcurrentHashMap<>();
    private final DeviceKind<FirepitData> kind;

    public FirepitDevice(Services services, StackCodec stacks) {
        this.services = Objects.requireNonNull(services, "services");
        this.stacks = Objects.requireNonNull(stacks, "stacks");
        this.kind = DeviceKind.builder(ID, FirepitData.CODEC, FirepitData.FRESH)
            .slots(FirepitSim.SLOTS)
            .slotLimit(FirepitSim::slotLimit)
            .filter(this::isItemValid)
            .advance((record, from, to) -> catchUp(record, from, to))
            .removal(DeviceKind.RemoveBehaviour.DROP)
            .handler(new Handler())
            .build();
    }

    public DeviceKind<FirepitData> kind() {
        return kind;
    }

    /** The block state of {@code data} with the axis {@code axis}. */
    public static BlockStateRef state(FirepitData data, String axis) {
        return BlockStateRef.of(ID).with(LIT, Boolean.toString(data.lit()))
            .with(SMOKE_LEVEL, Integer.toString(data.smokeLevel())).with(AXIS, axis);
    }

    /**
     * Lights the firepit at {@code block} through {@code callback} (the router's neighbour update), TFC
     * {@code onFireStart}: {@code light(state)}. Returns whether it is lit after. Region thread of the block.
     */
    public boolean light(Block block, java.util.function.Consumer<Block> callback) {
        Light l = new Light();
        Pos key = Pos.of(block);
        pending.put(key, l);
        try {
            callback.accept(block);
        } finally {
            pending.remove(key, l);
        }
        return l.lit;
    }

    /** TFC {@code StartFireEvent}: the logs go into fuel slots 0.., then {@code light}. The block is already placed. */
    public void build(Block block, List<ItemStack> logs, java.util.function.Consumer<Block> callback) {
        Build b = new Build(List.copyOf(logs));
        Pos key = Pos.of(block);
        pending.put(key, b);
        try {
            callback.accept(block);
        } finally {
            pending.remove(key, b);
        }
    }

    /** TFC {@code isItemValid}. */
    boolean isItemValid(int slot, ItemStack stack) {
        return switch (slot) {
            case FirepitSim.SLOT_FUEL_INPUT -> services.fuel().apply(stack).isPresent() && services.firepitFuel().test(stack);
            case FirepitSim.SLOT_ITEM_INPUT -> services.capacity().applyAsDouble(stack) > 0;
            case FirepitSim.SLOT_OUTPUT_1, FirepitSim.SLOT_OUTPUT_2 -> true;
            default -> false;
        };
    }

    // ---- the lazy simulation ---------------------------------------------------------------------------

    /** The kind's catch-up: TFC's serverTick over {@code (from, to]}, with the slots changed to match. */
    DeviceRecord<FirepitData> catchUp(DeviceRecord<FirepitData> record, long from, long to) {
        Slots s = new Slots(record);
        FirepitSim.Cooking cooking = null;
        ItemStack input = s.get(FirepitSim.SLOT_ITEM_INPUT);
        float capacity = StackCodec.isEmpty(input) ? 0f : (float) services.capacity().applyAsDouble(input);
        if (capacity > 0f) {
            HeatState heat = HeatPdc.read(input).orElseGet(() -> HeatState.cold(from));
            cooking = new FirepitSim.Cooking(capacity, heat, services.cooker().recipeTemperature(input));
        }
        FirepitSim.Result r = FirepitSim.advance(record.state(), from, to, s.queue(), cooking, services.random().get(),
            FirepitRules.DeviceConfig.DEFAULT, services.heatConfig());
        s.consume(r.consumed());
        if (r.cooked() != null) {
            ItemStack heated = input.clone();
            if (HeatPdc.write(heated, r.cooked().heat())) {
                s.set(FirepitSim.SLOT_ITEM_INPUT, heated);
                if (r.cooked().converted()) {
                    convert(s, heated, r.cooked().temperatureBefore());
                }
            }
        }
        return s.record().withState(r.data());
    }

    /**
     * TFC {@code handleCooking} after a recipe fired: the input is cleared and the result merged into the two outputs.
     * TFC drops what the outputs cannot take; here the input stays (and converts once there is room).
     */
    private void convert(Slots s, ItemStack heated, float before) {
        Optional<ItemStack> out = services.cooker().convert(heated, before);
        if (out.isEmpty()) {
            return;
        }
        ItemStack result = out.get();
        if (StackCodec.isEmpty(result)) {
            s.set(FirepitSim.SLOT_ITEM_INPUT, ItemStack.empty());
            return;
        }
        ItemStack rest = s.merge(FirepitSim.SLOT_OUTPUT_1, result);
        if (!StackCodec.isEmpty(rest)) {
            Slots trial = s.copy();
            rest = trial.merge(FirepitSim.SLOT_OUTPUT_2, rest);
            if (!StackCodec.isEmpty(rest)) {
                return;
            }
            s.adopt(trial);
        }
        s.set(FirepitSim.SLOT_ITEM_INPUT, ItemStack.empty());
    }

    /** The slots of one record as stacks, changed in place. */
    private final class Slots {
        private DeviceRecord<FirepitData> record;

        Slots(DeviceRecord<FirepitData> record) {
            this.record = record;
        }

        Slots copy() {
            return new Slots(record);
        }

        void adopt(Slots other) {
            record = other.record;
        }

        DeviceRecord<FirepitData> record() {
            return record;
        }

        ItemStack get(int slot) {
            return stacks.decode(record.slot(slot));
        }

        void set(int slot, ItemStack stack) {
            record = record.withSlot(slot, stacks.encode(stack));
        }

        /** The non-empty fuel slots in order (TFC takes slot 0 and cascades), as fuels. */
        List<Optional<Fuel>> queue() {
            List<Optional<Fuel>> out = new ArrayList<>(4);
            for (int i = FirepitSim.SLOT_FUEL_CONSUME; i <= FirepitSim.SLOT_FUEL_INPUT; i++) {
                ItemStack stack = get(i);
                if (!StackCodec.isEmpty(stack)) {
                    out.add(services.fuel().apply(stack));
                }
            }
            return out;
        }

        /** Removes the first {@code n} non-empty fuel slots, then TFC {@code cascadeFuelSlots}. */
        void consume(int n) {
            List<ItemBlob> left = new ArrayList<>(4);
            int skipped = 0;
            for (int i = FirepitSim.SLOT_FUEL_CONSUME; i <= FirepitSim.SLOT_FUEL_INPUT; i++) {
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
            for (int i = FirepitSim.SLOT_FUEL_CONSUME; i <= FirepitSim.SLOT_FUEL_INPUT; i++) {
                record = record.withSlot(i, i < left.size() ? left.get(i) : ItemBlob.EMPTY);
            }
        }

        /** NeoForge {@code insertItem(slot, stack, false)} with the output limit; returns the rest. */
        ItemStack merge(int slot, ItemStack stack) {
            ItemStack content = get(slot);
            int limit = Math.min(stack.getMaxStackSize(), FirepitSim.slotLimit(slot));
            if (StackCodec.isEmpty(content)) {
                int put = Math.min(limit, stack.getAmount());
                ItemStack placed = stack.clone();
                placed.setAmount(put);
                set(slot, placed);
                return rest(stack, stack.getAmount() - put);
            }
            if (!content.isSimilar(stack) || content.getAmount() >= limit) {
                return stack;
            }
            int put = Math.min(limit - content.getAmount(), stack.getAmount());
            ItemStack grown = content.clone();
            grown.setAmount(content.getAmount() + put);
            set(slot, grown);
            return rest(stack, stack.getAmount() - put);
        }

        private ItemStack rest(ItemStack stack, int n) {
            if (n <= 0) {
                return ItemStack.empty();
            }
            ItemStack r = stack.clone();
            r.setAmount(n);
            return r;
        }
    }

    // ---- reactions --------------------------------------------------------------------------------------

    private final class Handler implements DeviceHandler<FirepitData> {
        @Override
        public DeviceResult<FirepitData> useWithItem(DeviceContext<FirepitData> ctx) {
            Player player = ctx.player();
            if (player == null || ctx.hand() == null) {
                return DeviceResult.pass();
            }
            Block block = ctx.block();
            DeviceRecord<FirepitData> record = sampled(ctx);
            FirepitData data = record.state();
            ItemStack stack = ctx.item();
            boolean creative = player.getGameMode() == GameMode.CREATIVE;

            // InteractionManager SHOVEL_DOUSE -> DouseFireEvent -> onFireStop: a firepit above 0 C is extinguished
            FirepitEffects.Douse douse = StackCodec.isEmpty(stack) ? FirepitEffects.Douse.NONE : services.douse().apply(stack);
            if (douse != FirepitEffects.Douse.NONE && data.temperature() > 0f) {
                if (data.lit()) {
                    services.effects().sound(block, FirepitEffects.Sound.EXTINGUISH);
                }
                record = record.withState(FirepitSim.extinguish(data));
                if (!creative) {
                    services.effects().useDouse(player, ctx.hand(), douse);
                }
                return finish(ctx, record, true);
            }
            // tryInsertLog(player, held, firepit, true)
            if (!StackCodec.isEmpty(stack) && isItemValid(FirepitSim.SLOT_FUEL_INPUT, stack)) {
                Slots s = new Slots(record);
                for (int i = FirepitSim.SLOT_FUEL_CONSUME; i <= FirepitSim.SLOT_FUEL_INPUT; i++) {
                    if (StackCodec.isEmpty(s.get(i))) {
                        ItemStack one = stack.clone();
                        one.setAmount(1);
                        s.set(i, one);
                        if (!creative) {
                            shrink(player, ctx.hand());
                        }
                        services.effects().sound(block, FirepitEffects.Sound.LOG);
                        return finish(ctx, s.record(), true);
                    }
                }
            }
            // an unlit firepit gives its ash
            if (!data.lit() && data.ash() > 0) {
                services.effects().give(player, services.woodAsh().apply(data.ash()));
                services.effects().sound(block, FirepitEffects.Sound.ASH);
                return finish(ctx, record.withState(data.withAsh(0)), true);
            }
            // TFC opens the firepit screen (PAR-HEAT-07); stand-in: the input and the outputs by hand
            Slots s = new Slots(record);
            if (!StackCodec.isEmpty(stack)) {
                if (StackCodec.isEmpty(s.get(FirepitSim.SLOT_ITEM_INPUT)) && isItemValid(FirepitSim.SLOT_ITEM_INPUT, stack)) {
                    ItemStack one = stack.clone();
                    one.setAmount(1);
                    s.set(FirepitSim.SLOT_ITEM_INPUT, one);
                    if (!creative) {
                        shrink(player, ctx.hand());
                    }
                }
            } else {
                for (int slot : new int[] {FirepitSim.SLOT_OUTPUT_1, FirepitSim.SLOT_OUTPUT_2, FirepitSim.SLOT_ITEM_INPUT}) {
                    ItemStack out = s.get(slot);
                    if (!StackCodec.isEmpty(out)) {
                        services.effects().give(player, out);
                        s.set(slot, ItemStack.empty());
                        break;
                    }
                }
            }
            return finish(ctx, s.record(), true);
        }

        /** The burn-out callback, fire starting (pending light or build) and a lost floor. */
        @Override
        public DeviceResult<FirepitData> neighbourChanged(DeviceContext<FirepitData> ctx) {
            DeviceRecord<FirepitData> record = sampled(ctx);
            Pending p = pending.remove(Pos.of(ctx.block()));
            if (p instanceof Build b) {
                Slots s = new Slots(record);
                for (int i = 0; i < b.logs().size() && i <= FirepitSim.SLOT_FUEL_INPUT; i++) {
                    s.set(i, b.logs().get(i));
                }
                record = lit(s.record(), null);
            } else if (p instanceof Light l) {
                record = lit(record, l);
            } else if (!services.canSurvive().test(ctx.block())) {
                services.effects().breakBlock(ctx.block());
            }
            return finish(ctx, record, false);
        }

        /** TFC {@code removed} -> {@code ejectInventory}: the slots drop (the kind's DROP) and the ash as wood ash. */
        @Override
        public void removed(DeviceContext<FirepitData> ctx) {
            int ash = ctx.deviceState().ash();
            if (ash > 0) {
                services.effects().spawn(ctx.block(), services.woodAsh().apply(ash));
            }
        }

        /** TFC {@code light(state)}. */
        private DeviceRecord<FirepitData> lit(DeviceRecord<FirepitData> record, Light l) {
            Slots s = new Slots(record);
            FirepitSim.Result r = FirepitSim.light(record.state(), s.queue(), services.random().get());
            s.consume(r.consumed());
            if (l != null) {
                l.lit = r.data().lit();
            }
            return s.record().withState(r.data());
        }

        /** The caught-up record with the rain sampled now. */
        private DeviceRecord<FirepitData> sampled(DeviceContext<FirepitData> ctx) {
            return ctx.record().withState(ctx.deviceState().withRaining(services.raining().test(ctx.block())));
        }

        /** The block state follows the record; a lit firepit queues its burn-out callback; smoke on a click. */
        private DeviceResult<FirepitData> finish(DeviceContext<FirepitData> ctx, DeviceRecord<FirepitData> record,
                boolean consumed) {
            Block block = ctx.block();
            FirepitData data = record.state();
            String axis = ctx.state().properties().getOrDefault(AXIS, "x");
            BlockStateRef wanted = state(data, axis);
            if (!wanted.equals(ctx.state())) {
                services.effects().show(block, wanted);
            }
            if (data.lit()) {
                long out = FirepitSim.ticksUntilOut(data, new Slots(record).queue());
                services.effects().callbackIn(block, Math.max(1, out));
                if (consumed) {
                    services.effects().smoke(block, data.smokeLevel());
                }
            }
            if (consumed) {
                return DeviceResult.consume(record);
            }
            return record.equals(ctx.record()) ? DeviceResult.pass() : DeviceResult.update(record);
        }
    }

    /** TFC {@code stack.shrink(1)} on the hand's stack. */
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
}
