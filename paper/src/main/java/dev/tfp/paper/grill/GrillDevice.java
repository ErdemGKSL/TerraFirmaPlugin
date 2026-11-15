/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/GrillBlock.java @ c3b4d9536 (useItemOn: slot insert and
 *       extract, sneak ash pickup, sneak removal of the grill with the grill damage, tryInsertLog below 0.6, openMenu),
 *       src/main/java/net/dries007/tfc/common/blockentities/GrillBlockEntity.java @ c3b4d9536 (getSlotStackLimit,
 *       isItemValid, handleCooking: assembleItem + FoodTraits.WOOD_GRILLED in place),
 *       src/main/java/net/dries007/tfc/common/blocks/devices/FirepitBlock.java @ c3b4d9536 (tryInsertLog),
 *       src/main/java/net/dries007/tfc/common/blockentities/AbstractFirepitBlockEntity.java @ c3b4d9536 (isItemValid,
 *       cascadeFuelSlots, light, extinguish, ejectInventory with the ash),
 *       src/main/java/net/dries007/tfc/ForgeEventHandler.java @ c3b4d9536 (onFireStart: a strong start lights any
 *       AbstractFirepitBlockEntity; onFireStop)
 */
package dev.tfp.paper.grill;

import dev.tfp.core.cooking.FirepitRules;
import dev.tfp.core.device.DeviceRecord;
import dev.tfp.core.device.ItemBlob;
import dev.tfp.core.firepit.FirepitData;
import dev.tfp.core.firepit.FirepitSim;
import dev.tfp.core.fuel.Fuel;
import dev.tfp.core.grill.GrillSim;
import dev.tfp.core.grill.GrillSlots;
import dev.tfp.core.heat.HeatConfig;
import dev.tfp.core.heat.HeatState;
import dev.tfp.paper.device.DeviceContext;
import dev.tfp.paper.device.DeviceHandler;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceResult;
import dev.tfp.paper.device.StackCodec;
import dev.tfp.paper.firepit.FirepitDevice;
import dev.tfp.paper.firepit.FirepitEffects;
import dev.tfp.paper.heat.HeatPdc;
import dev.tfp.paper.integration.BlockStateRef;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;
import java.util.function.UnaryOperator;
import java.util.random.RandomGenerator;
import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * The grill as a device kind (PAR-HEAT-08): TFC's {@code GrillBlock} + {@code GrillBlockEntity} on the device framework.
 * The grill is the firepit ({@link FirepitData}, the same burn, fuel, ash, smoke and temperature, replayed by
 * {@link GrillSim}) with five one-item grill slots (4 to 8) in place of the input and the outputs. Each grill item heats
 * towards the device temperature every tick and turns into its heating recipe's result in its own slot, with the
 * {@code tfp:wood_grilled} food trait (TFC {@code FoodTraits.WOOD_GRILLED}).
 *
 * <p>Right click, in TFC's {@code useItemOn} order, with the slot picked from where the click lands on the top
 * ({@link GrillSlots#slotAt}): a heatable item goes into the empty slot under it; an empty hand takes the item under it
 * when sneaking (or when the item is no longer valid there); a sneaking empty hand takes the ash of an unlit grill, or
 * else takes the grill off (the wrought iron grill comes back, the block turns into a firepit, and a lit grill burns for
 * 1 {@code tfp:grill} damage); fuel goes into the first empty fuel slot when the click lands below 0.6; otherwise the grill
 * screen opens. As the firepit (and its ledger divergence), a shovel or water bucket douses a warm grill on a plain click
 * before the screen step.
 *
 * <p>A strong fire start lights it ({@link #light}, TFC {@code onFireStart} for every firepit-type block entity). While
 * lit and loaded a callback is queued at the tick the fuel runs out, as the firepit's.
 */
public final class GrillDevice {
    public static final String ID = "tfp:grill";
    public static final String GRILL_ITEM = "tfp:wrought_iron_grill";
    /** How many recipe rounds one catch-up runs (each round ends at a conversion); the rest is the firepit alone. */
    static final int MAX_ROUNDS = 32;

    /**
     * What the device reads from the rest of the plugin.
     *
     * @param fuel        TFC {@code Fuel.get(stack)}
     * @param firepitFuel whether the stack is in {@code #tfc:firepit_fuel}
     * @param capacity    the heat capacity of a stack, {@code <= 0} when it cannot be heated
     * @param cooker      the heating recipes (the firepit's lookup and conversion)
     * @param grilled     TFC {@code FoodCapability.applyTrait(output, WOOD_GRILLED)} on a converted stack
     * @param douse       what the held stack does as a douser
     * @param raining     TFC {@code level.isRainingAt(pos)} at the grill
     * @param canSurvive  TFC {@code BottomSupportedDeviceBlock.canSurvive}
     * @param woodAsh     a stack of {@code n} wood ash
     * @param grillItem   one {@code tfp:wrought_iron_grill}
     * @param hit         where the player's click lands in the block (block-local), empty when it does not
     * @param random      the ash draws
     * @param heatConfig  the item heating and cooling modifiers
     * @param effects     the world effects
     */
    public record Services(Function<ItemStack, Optional<Fuel>> fuel, Predicate<ItemStack> firepitFuel,
            ToDoubleFunction<ItemStack> capacity, FirepitDevice.Cooker cooker, UnaryOperator<ItemStack> grilled,
            Function<ItemStack, FirepitEffects.Douse> douse, Predicate<Block> raining, Predicate<Block> canSurvive,
            IntFunction<ItemStack> woodAsh, Supplier<ItemStack> grillItem,
            BiFunction<Player, Block, Optional<GrillSlots.Point>> hit, Supplier<RandomGenerator> random,
            HeatConfig heatConfig, GrillEffects effects) {
        public Services {
            Objects.requireNonNull(fuel, "fuel");
            Objects.requireNonNull(firepitFuel, "firepitFuel");
            Objects.requireNonNull(capacity, "capacity");
            Objects.requireNonNull(cooker, "cooker");
            Objects.requireNonNull(grilled, "grilled");
            Objects.requireNonNull(douse, "douse");
            Objects.requireNonNull(raining, "raining");
            Objects.requireNonNull(canSurvive, "canSurvive");
            Objects.requireNonNull(woodAsh, "woodAsh");
            Objects.requireNonNull(grillItem, "grillItem");
            Objects.requireNonNull(hit, "hit");
            Objects.requireNonNull(random, "random");
            Objects.requireNonNull(heatConfig, "heatConfig");
            Objects.requireNonNull(effects, "effects");
        }
    }

    private static final class Light {
        boolean lit;
    }

    private record Pos(UUID world, int x, int y, int z) {
        static Pos of(Block b) {
            return new Pos(b.getWorld().getUID(), b.getX(), b.getY(), b.getZ());
        }
    }

    private final Services services;
    private final StackCodec stacks;
    private final Map<Pos, Light> pending = new ConcurrentHashMap<>();
    private final DeviceKind<FirepitData> kind;

    public GrillDevice(Services services, StackCodec stacks) {
        this.services = Objects.requireNonNull(services, "services");
        this.stacks = Objects.requireNonNull(stacks, "stacks");
        this.kind = DeviceKind.builder(ID, FirepitData.CODEC, FirepitData.FRESH)
            .slots(GrillSim.SLOTS)
            .slotLimit(GrillSim::slotLimit)
            .filter(this::isItemValid)
            .advance(this::catchUp)
            .removal(DeviceKind.RemoveBehaviour.DROP)
            .handler(new Handler())
            .build();
    }

    public DeviceKind<FirepitData> kind() {
        return kind;
    }

    public StackCodec stacks() {
        return stacks;
    }

    public Services services() {
        return services;
    }

    /** The block state of {@code data} with the axis {@code axis}. */
    public static BlockStateRef state(FirepitData data, String axis) {
        return BlockStateRef.of(ID).with(FirepitDevice.LIT, Boolean.toString(data.lit()))
            .with(FirepitDevice.SMOKE_LEVEL, Integer.toString(data.smokeLevel())).with(FirepitDevice.AXIS, axis);
    }

    /** TFC {@code onFireStart}: {@code light(state)} through {@code callback} (the router's neighbour update). */
    public boolean light(Block block, Consumer<Block> callback) {
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

    /** TFC {@code GrillBlockEntity.isItemValid}: heatables on the grill, fuel only into slot 3. */
    public boolean isItemValid(int slot, ItemStack stack) {
        if (StackCodec.isEmpty(stack)) {
            return false;
        }
        if (GrillSim.isGrillSlot(slot)) {
            return services.capacity().applyAsDouble(stack) > 0;
        }
        return slot == FirepitSim.SLOT_FUEL_INPUT && services.fuel().apply(stack).isPresent()
            && services.firepitFuel().test(stack);
    }

    /** The five grill stacks of {@code record} (index 0 = slot 4), empty stacks for empty slots. */
    public List<ItemStack> grill(DeviceRecord<FirepitData> record) {
        List<ItemStack> out = new ArrayList<>(GrillSim.GRILL_SLOTS);
        for (int s = GrillSim.SLOT_EXTRA_INPUT_START; s <= GrillSim.SLOT_EXTRA_INPUT_END; s++) {
            out.add(stacks.decode(record.slot(s)));
        }
        return out;
    }

    // ---- the lazy simulation ---------------------------------------------------------------------------

    /** The kind's catch-up: TFC's serverTick over {@code (from, to]}, the conversions applied in their slots. */
    DeviceRecord<FirepitData> catchUp(DeviceRecord<FirepitData> record, long from, long to) {
        Slots s = new Slots(record);
        FirepitData data = record.state();
        RandomGenerator random = services.random().get();
        Set<Integer> stuck = new HashSet<>();
        long t = from;
        for (int round = 0; round < MAX_ROUNDS && t < to; round++) {
            List<GrillSim.Item> items = new ArrayList<>(GrillSim.GRILL_SLOTS);
            for (int slot = GrillSim.SLOT_EXTRA_INPUT_START; slot <= GrillSim.SLOT_EXTRA_INPUT_END; slot++) {
                items.add(item(s.get(slot), t, stuck.contains(slot)));
            }
            GrillSim.Result r = GrillSim.advance(data, t, to, s.queue(), items, random, FirepitRules.DeviceConfig.DEFAULT,
                services.heatConfig());
            s.consume(r.consumed());
            data = r.data();
            for (int i = 0; i < GrillSim.GRILL_SLOTS; i++) {
                HeatState heat = r.heats().get(i);
                int slot = GrillSim.SLOT_EXTRA_INPUT_START + i;
                if (heat != null) {
                    ItemStack heated = s.get(slot).clone();
                    if (HeatPdc.write(heated, heat)) {
                        s.set(slot, heated);
                    }
                }
            }
            for (int i : r.converted()) {
                int slot = GrillSim.SLOT_EXTRA_INPUT_START + i;
                if (!convert(s, slot, r.temperatureTested().get(i))) {
                    stuck.add(slot);
                }
            }
            t = r.tick();
            if (!r.anyConverted()) {
                break;
            }
        }
        if (t < to) {
            FirepitSim.Result r = FirepitSim.advance(data, t, to, s.queue(), null, random,
                FirepitRules.DeviceConfig.DEFAULT, services.heatConfig());
            s.consume(r.consumed());
            data = r.data();
        }
        return s.record().withState(data);
    }

    private GrillSim.Item item(ItemStack stack, long tick, boolean stuck) {
        if (StackCodec.isEmpty(stack)) {
            return null;
        }
        float capacity = (float) services.capacity().applyAsDouble(stack);
        if (!(capacity > 0f)) {
            return new GrillSim.Item(0f, HeatState.cold(tick), Float.NaN);
        }
        HeatState heat = HeatPdc.read(stack).orElseGet(() -> HeatState.cold(tick));
        return new GrillSim.Item(capacity, heat, stuck ? Float.NaN : services.cooker().recipeTemperature(stack));
    }

    /**
     * TFC {@code handleCooking}: {@code setStackInSlot(slot, applyTrait(recipe.assembleItem(input), WOOD_GRILLED))}.
     * Returns false when the recipe gave no answer (the slot keeps its item and is not tried again in this catch-up).
     */
    private boolean convert(Slots s, int slot, Float tested) {
        ItemStack heated = s.get(slot);
        Optional<ItemStack> out = services.cooker().convert(heated, tested == null ? 0f : tested);
        if (out.isEmpty()) {
            return false;
        }
        ItemStack result = out.get();
        s.set(slot, StackCodec.isEmpty(result) ? ItemStack.empty() : services.grilled().apply(result));
        return true;
    }

    /** The slots of one record as stacks, changed in place. */
    final class Slots {
        private DeviceRecord<FirepitData> record;

        Slots(DeviceRecord<FirepitData> record) {
            this.record = record;
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

        /** The non-empty fuel slots in order, as fuels. */
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
    }

    /** TFC {@code cascadeFuelSlots} on a record: the non-empty fuel slots move down to the lowest open slots. */
    public static DeviceRecord<FirepitData> cascade(DeviceRecord<FirepitData> record) {
        List<ItemBlob> fuel = new ArrayList<>(4);
        for (int i = FirepitSim.SLOT_FUEL_CONSUME; i <= FirepitSim.SLOT_FUEL_INPUT; i++) {
            if (!record.slot(i).isEmpty()) {
                fuel.add(record.slot(i));
            }
        }
        DeviceRecord<FirepitData> out = record;
        for (int i = FirepitSim.SLOT_FUEL_CONSUME; i <= FirepitSim.SLOT_FUEL_INPUT; i++) {
            out = out.withSlot(i, i < fuel.size() ? fuel.get(i) : ItemBlob.EMPTY);
        }
        return out;
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
            boolean empty = StackCodec.isEmpty(stack);
            boolean sneaking = player.isSneaking();
            boolean creative = player.getGameMode() == GameMode.CREATIVE;
            Optional<GrillSlots.Point> hit = services.hit().apply(player, block);
            int slot = hit.map(p -> GrillSlots.slotAt(p.x(), p.y(), p.z())).orElse(-1);
            Slots s = new Slots(record);
            ItemStack current = slot == -1 ? ItemStack.empty() : s.get(slot);

            // a heatable item into the empty grill slot under the click: inventory.insertItem(slot, stack.split(1))
            if (!empty && slot != -1 && StackCodec.isEmpty(current) && isItemValid(slot, stack)) {
                ItemStack one = stack.clone();
                one.setAmount(1);
                s.set(slot, one);
                shrink(player, ctx.hand()); // TFC stack.split(1), in every game mode
                return finish(ctx, s.record(), true);
            }
            // an empty hand takes the item under the click when sneaking, or when it is no longer valid there
            if (empty && slot != -1 && !StackCodec.isEmpty(current) && (!isItemValid(slot, current) || sneaking)) {
                services.effects().give(player, current);
                s.set(slot, ItemStack.empty());
                return finish(ctx, s.record(), true);
            }
            if (empty && sneaking) {
                if (!data.lit() && data.ash() > 0) {
                    services.effects().give(player, services.woodAsh().apply(data.ash()));
                    services.effects().sound(block, FirepitEffects.Sound.ASH);
                    return finish(ctx, record.withState(data.withAsh(0)), true);
                }
                // the grill comes off: the item back, the block a firepit again (after this route), a lit grill burns
                if (services.effects().toFirepit(block)) {
                    services.effects().give(player, services.grillItem().get());
                    if (data.lit()) {
                        services.effects().burn(player, 1f);
                        services.effects().cool(block);
                    }
                }
                return DeviceResult.consume(record);
            }
            // tryInsertLog(player, stack, grill, hit.y - pos.y < 0.6)
            boolean low = hit.map(p -> p.y() < GrillSlots.LOG_INSERT_BELOW).orElse(false);
            if (!empty && low && isItemValid(FirepitSim.SLOT_FUEL_INPUT, stack)) {
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
            // the firepit's shovel / water bucket douse on a plain click (ledger divergence of the firepit)
            FirepitEffects.Douse douse = empty ? FirepitEffects.Douse.NONE : services.douse().apply(stack);
            if (douse != FirepitEffects.Douse.NONE && data.temperature() > 0f) {
                if (!creative) {
                    services.effects().useDouse(player, ctx.hand(), douse);
                }
                return finish(ctx, record.withState(FirepitSim.extinguish(data)), true);
            }
            // serverPlayer.openMenu(grill, pos)
            services.effects().openScreen(player, block);
            return finish(ctx, record, true);
        }

        /** The burn-out callback, fire starting (pending light), a conversion and a lost floor. */
        @Override
        public DeviceResult<FirepitData> neighbourChanged(DeviceContext<FirepitData> ctx) {
            DeviceRecord<FirepitData> record = sampled(ctx);
            Light l = pending.remove(Pos.of(ctx.block()));
            if (l != null) {
                Slots s = new Slots(record);
                FirepitSim.Result r = FirepitSim.light(record.state(), s.queue(), services.random().get());
                s.consume(r.consumed());
                l.lit = r.data().lit();
                record = s.record().withState(r.data());
            } else if (!services.canSurvive().test(ctx.block())) {
                services.effects().breakBlock(ctx.block());
            }
            return finish(ctx, record, false);
        }

        /** TFC {@code ejectInventory}: the slots drop (the kind's DROP), the ash as wood ash, the drawn items go. */
        @Override
        public void removed(DeviceContext<FirepitData> ctx) {
            int ash = ctx.deviceState().ash();
            if (ash > 0) {
                services.effects().spawn(ctx.block(), services.woodAsh().apply(ash));
            }
            services.effects().items(ctx.block(), List.of());
        }

        private DeviceRecord<FirepitData> sampled(DeviceContext<FirepitData> ctx) {
            return ctx.record().withState(ctx.deviceState().withRaining(services.raining().test(ctx.block())));
        }

        /** The block state and the drawn items follow the record; a lit grill queues its burn-out callback. */
        private DeviceResult<FirepitData> finish(DeviceContext<FirepitData> ctx, DeviceRecord<FirepitData> record,
                boolean consumed) {
            Block block = ctx.block();
            FirepitData data = record.state();
            String axis = ctx.state().properties().getOrDefault(FirepitDevice.AXIS, "x");
            BlockStateRef wanted = state(data, axis);
            if ("true".equals(ctx.state().properties().get(FirepitDevice.LIT)) && !data.lit()) {
                services.effects().sound(block, FirepitEffects.Sound.EXTINGUISH);
            }
            if (!wanted.equals(ctx.state())) {
                services.effects().show(block, wanted);
            }
            services.effects().items(block, grill(record));
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
