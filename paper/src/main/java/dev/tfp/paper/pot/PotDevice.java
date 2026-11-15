/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/PotBlock.java @ c3b4d9536 (useItemOn: the sneak branch with
 *       the ash, the pot item and TFCDamageTypes.pot; the fluid transfer; the output; tryInsertLog; openMenu),
 *       src/main/java/net/dries007/tfc/common/blockentities/PotBlockEntity.java @ c3b4d9536 (getSlotStackLimit,
 *       isItemValid, handleCooking, interactWithOutput, PotInventory fill / drain / canInsertFluid),
 *       src/main/java/net/dries007/tfc/common/recipes/SoupPotRecipe.java @ c3b4d9536 (SoupOutput.onInteract: a soup bowl
 *       takes one serving and is stored as its BOWL),
 *       src/main/java/net/dries007/tfc/common/recipes/JamPotRecipe.java @ c3b4d9536 (JamOutput.onInteract: an empty jar
 *       takes an unsealed jar, a jar with a lid a sealed one),
 *       src/main/java/net/dries007/tfc/common/blockentities/AbstractFirepitBlockEntity.java @ c3b4d9536 (isItemValid,
 *       light, extinguish, ejectInventory with the ash),
 *       src/main/java/net/dries007/tfc/ForgeEventHandler.java @ c3b4d9536 (onFireStart: a strong start lights the pot)
 */
package dev.tfp.paper.pot;

import dev.tfp.core.barrel.BarrelFluidTransfer;
import dev.tfp.core.cooking.FirepitRules;
import dev.tfp.core.device.DeviceRecord;
import dev.tfp.core.device.ItemBlob;
import dev.tfp.core.firepit.FirepitData;
import dev.tfp.core.firepit.FirepitSim;
import dev.tfp.core.fluid.FluidTransferRules;
import dev.tfp.core.fuel.Fuel;
import dev.tfp.core.heat.HeatConfig;
import dev.tfp.core.pot.PotData;
import dev.tfp.core.pot.PotOutput;
import dev.tfp.core.pot.PotSim;
import dev.tfp.core.recipe.food.FoodData;
import dev.tfp.paper.barrel.BarrelItemTanks;
import dev.tfp.paper.device.DeviceContext;
import dev.tfp.paper.device.DeviceHandler;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceResult;
import dev.tfp.paper.device.StackCodec;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.salad.SaladPdc;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;
import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * The cooking pot as a device kind (PAR-HEAT-09): TFC's {@code PotBlock} + {@code PotBlockEntity} on the device framework.
 * The firepit part (burn, temperature, ash, smoke) and the boil are replayed lazily by {@link PotSim} on every access; a
 * finished recipe makes its output there ({@link PotRecipeBook#finish}) and the replay goes on with the new contents. Nine
 * slots as TFC, one item each: fuel 0..3, the ingredients 4..8; a 1000 mB tank.
 *
 * <p>Right click, in TFC's {@code useItemOn} order, while the pot is not boiling: sneaking with an empty hand takes the ash
 * of an unlit pot, else takes the pot off (the pot item back, the block a firepit again; a lit pot burns for 1); a fluid
 * container fills or drains the tank; a soup bowl takes a serving of soup, an empty jar (with or without a lid) a jar of
 * jam. Then, boiling or not: a firepit fuel goes into the first empty fuel slot; anything else opens the pot screen.
 *
 * <p>Fire starting lights it ({@link #light}) through the router's neighbour callback, as the firepit. While lit and
 * loaded, a callback is queued at the tick the fuel runs out so the block turns unlit on time.
 */
public final class PotDevice {
    public static final String ID = "tfp:pot";
    public static final String LIT = "lit";
    public static final String SMOKE_LEVEL = "smoke_level";
    public static final String AXIS = "axis";
    public static final String WOOD_ASH = "tfp:powder/wood_ash";
    /** TFC {@code TFCItems.POT}. */
    public static final String POT_ITEM = "tfp:ceramic/pot";
    /** TFC {@code TFCDamageTypes.pot(player, 1f)}. */
    public static final float BURN_DAMAGE = 1f;
    /** The most recipes one catch-up finishes in a row (a guard; each one needs its own boil). */
    static final int FINISH_LIMIT = 64;

    /**
     * What the device reads from the rest of the plugin.
     *
     * @param fuel        TFC {@code Fuel.get(stack)}
     * @param firepitFuel whether the stack is in {@code #tfc:firepit_fuel}
     * @param book        the pot recipes
     * @param tanks       the fluid handlers of held items
     * @param soupBowl    {@code #tfc:soup_bowls}
     * @param emptyJar    TFC {@code TFCItems.EMPTY_JAR}
     * @param jarWithLid  {@code #tfc:empty_jars_with_lid}
     * @param raining     TFC {@code level.isRainingAt(pos)} at the pot
     * @param canSurvive  TFC {@code BottomSupportedDeviceBlock.canSurvive}
     * @param woodAsh     a stack of {@code n} wood ash
     * @param potItem     one ceramic pot item
     * @param random      the ash draws
     * @param heatConfig  the heating modifiers
     * @param effects     the world effects
     */
    public record Services(Function<ItemStack, Optional<Fuel>> fuel, Predicate<ItemStack> firepitFuel, PotRecipeBook book,
            BarrelItemTanks tanks, Predicate<ItemStack> soupBowl, Predicate<ItemStack> emptyJar,
            Predicate<ItemStack> jarWithLid, Predicate<Block> raining, Predicate<Block> canSurvive,
            IntFunction<ItemStack> woodAsh, Supplier<ItemStack> potItem, Supplier<RandomGenerator> random,
            HeatConfig heatConfig, PotEffects effects) {
        public Services {
            Objects.requireNonNull(fuel, "fuel");
            Objects.requireNonNull(firepitFuel, "firepitFuel");
            Objects.requireNonNull(book, "book");
            Objects.requireNonNull(tanks, "tanks");
            Objects.requireNonNull(soupBowl, "soupBowl");
            Objects.requireNonNull(emptyJar, "emptyJar");
            Objects.requireNonNull(jarWithLid, "jarWithLid");
            Objects.requireNonNull(raining, "raining");
            Objects.requireNonNull(canSurvive, "canSurvive");
            Objects.requireNonNull(woodAsh, "woodAsh");
            Objects.requireNonNull(potItem, "potItem");
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
    private final DeviceKind<PotData> kind;

    public PotDevice(Services services, StackCodec stacks) {
        this.services = Objects.requireNonNull(services, "services");
        this.stacks = Objects.requireNonNull(stacks, "stacks");
        this.kind = DeviceKind.builder(ID, PotData.CODEC, PotData.FRESH)
            .slots(PotSim.SLOTS)
            .slotLimit(PotSim::slotLimit)
            .filter(this::isItemValid)
            .advance(this::catchUp)
            .removal(DeviceKind.RemoveBehaviour.DROP)
            .handler(new Handler())
            .build();
    }

    public DeviceKind<PotData> kind() {
        return kind;
    }

    public Services services() {
        return services;
    }

    /** The block state of {@code data} with the axis {@code axis}. */
    public static BlockStateRef state(PotData data, String axis) {
        return BlockStateRef.of(ID).with(LIT, Boolean.toString(data.firepit().lit()))
            .with(SMOKE_LEVEL, Integer.toString(data.firepit().smokeLevel())).with(AXIS, axis);
    }

    /**
     * Lights the pot at {@code block} through {@code callback} (the router's neighbour update), TFC {@code onFireStart}:
     * {@code light(state)}. Returns whether it is lit after. Region thread of the block.
     */
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

    /** TFC {@code isItemValid}: the ingredients take anything, fuel only the top fuel slot. */
    public boolean isItemValid(int slot, ItemStack stack) {
        if (slot >= PotSim.SLOT_INPUT_START && slot <= PotSim.SLOT_INPUT_END) {
            return true;
        }
        return slot == FirepitSim.SLOT_FUEL_INPUT && services.fuel().apply(stack).isPresent()
            && services.firepitFuel().test(stack);
    }

    /** The recipe the record's contents match (TFC {@code cachedRecipe}). */
    public Optional<PotRecipeView> recipe(DeviceRecord<PotData> record) {
        return services.book().select(inputs(record, stacks), record.state().fluid());
    }

    /** The five input slots as stacks. */
    public static List<ItemStack> inputs(DeviceRecord<PotData> record, StackCodec stacks) {
        List<ItemStack> out = new ArrayList<>(5);
        for (int i = PotSim.SLOT_INPUT_START; i <= PotSim.SLOT_INPUT_END; i++) {
            out.add(stacks.decode(record.slot(i)));
        }
        return out;
    }

    // ---- the lazy simulation ---------------------------------------------------------------------------

    /** The kind's catch-up: TFC's serverTick over {@code (from, to]}; finished recipes make their output on the way. */
    DeviceRecord<PotData> catchUp(DeviceRecord<PotData> record, long from, long to) {
        long t = from;
        for (int i = 0; i < FINISH_LIMIT && t < to; i++) {
            Optional<PotRecipeView> recipe = recipe(record);
            PotSim.Result r = PotSim.advance(record.state(), t, to, queue(record), recipe.map(PotRecipeView::boil).orElse(null),
                services.random().get(), FirepitRules.DeviceConfig.DEFAULT, services.heatConfig());
            record = consume(record, r.consumed()).withState(r.data());
            if (!r.finished() || recipe.isEmpty()) {
                return record;
            }
            record = finish(record, recipe.get());
            t = r.finishedAt();
        }
        return record;
    }

    /** TFC {@code handleCooking} at the end of the boil: the output made, the inputs and the tank replaced. */
    DeviceRecord<PotData> finish(DeviceRecord<PotData> record, PotRecipeView recipe) {
        PotRecipeBook.Finished f = services.book().finish(recipe, inputs(record, stacks), record.state().fluid(), stacks);
        for (int i = 0; i < f.inputs().size(); i++) {
            record = record.withSlot(PotSim.SLOT_INPUT_START + i, stacks.encode(f.inputs().get(i)));
        }
        return record.withState(record.state().withFluid(f.fluid()).withOutput(f.output()));
    }

    /** The non-empty fuel slots in order (TFC takes slot 0 and cascades), as fuels. */
    List<Optional<Fuel>> queue(DeviceRecord<PotData> record) {
        List<Optional<Fuel>> out = new ArrayList<>(4);
        for (int i = FirepitSim.SLOT_FUEL_CONSUME; i <= FirepitSim.SLOT_FUEL_INPUT; i++) {
            ItemStack stack = stacks.decode(record.slot(i));
            if (!StackCodec.isEmpty(stack)) {
                out.add(services.fuel().apply(stack));
            }
        }
        return out;
    }

    /** Removes the first {@code n} non-empty fuel slots, then TFC {@code cascadeFuelSlots}. */
    static DeviceRecord<PotData> consume(DeviceRecord<PotData> record, int n) {
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
        return record;
    }

    // ---- the output --------------------------------------------------------------------------------------

    /**
     * TFC {@code SoupOutput.onInteract}: one serving goes out in the clicked bowl, which is stored on it ({@code BOWL}).
     * Returns the serving and the output after, or empty when the stack is no soup bowl or there is no soup.
     */
    Optional<Taken> takeSoup(PotOutput output, ItemStack bowl, Function<ItemStack, Optional<String>> itemIds) {
        if (!(output instanceof PotOutput.Soup soup) || soup.isEmpty() || !services.soupBowl().test(bowl)) {
            return Optional.empty();
        }
        ItemStack all = stacks.decode(soup.stack());
        if (StackCodec.isEmpty(all)) {
            return Optional.empty();
        }
        ItemStack one = all.clone();
        one.setAmount(1);
        Optional<FoodData> food = SaladPdc.food(one);
        if (food.isPresent()) {
            String bowlId = itemIds.apply(bowl).orElse(bowl.getType().getKey().toString());
            SaladPdc.write(one, food.get(), SaladPdc.ingredients(one), bowlId);
        }
        all.setAmount(all.getAmount() - 1);
        PotOutput after = new PotOutput.Soup(all.getAmount() <= 0 ? ItemBlob.EMPTY : stacks.encode(all));
        return Optional.of(new Taken(one, after));
    }

    /**
     * TFC {@code JamOutput.onInteract}: an empty jar takes an unsealed jar, a jar with a lid a sealed one; both stacks
     * shrink. Empty when the stack is neither or there is no jam.
     */
    Optional<Taken> takeJam(PotOutput output, ItemStack jar) {
        if (!(output instanceof PotOutput.Jam jam) || jam.isEmpty()) {
            return Optional.empty();
        }
        boolean unsealed = services.emptyJar().test(jar);
        if (!unsealed && !services.jarWithLid().test(jar)) {
            return Optional.empty();
        }
        ItemStack u = stacks.decode(jam.unsealed());
        ItemStack s = stacks.decode(jam.sealed());
        ItemStack given = (unsealed ? u : s).clone();
        given.setAmount(1);
        u.setAmount(u.getAmount() - 1);
        s.setAmount(s.getAmount() - 1);
        PotOutput after = new PotOutput.Jam(u.getAmount() <= 0 ? ItemBlob.EMPTY : stacks.encode(u),
            s.getAmount() <= 0 ? ItemBlob.EMPTY : stacks.encode(s), jam.texture());
        return Optional.of(new Taken(given, after));
    }

    /** One item taken out of the output, and the output after. */
    record Taken(ItemStack item, PotOutput after) {
    }

    // ---- reactions --------------------------------------------------------------------------------------

    private final class Handler implements DeviceHandler<PotData> {
        @Override
        public DeviceResult<PotData> useWithItem(DeviceContext<PotData> ctx) {
            Player player = ctx.player();
            if (player == null || ctx.hand() == null) {
                return DeviceResult.pass();
            }
            Block block = ctx.block();
            DeviceRecord<PotData> record = sampled(ctx);
            PotData data = record.state();
            ItemStack stack = ctx.item();
            boolean empty = StackCodec.isEmpty(stack);
            boolean creative = player.getGameMode() == GameMode.CREATIVE;
            boolean boiling = PotSim.isBoiling(data, recipe(record).map(PotRecipeView::boil).orElse(null));

            if (!boiling && empty && player.isSneaking()) {
                FirepitData f = data.firepit();
                if (!f.lit() && f.ash() > 0) {
                    services.effects().give(player, services.woodAsh().apply(f.ash()));
                    services.effects().sound(block, PotEffects.Sound.ASH);
                    return finish(ctx, record.withState(data.withFirepit(f.withAsh(0))), true);
                }
                if (services.effects().converting(block)) {
                    return DeviceResult.consume(record); // already being taken off this tick
                }
                // the pot item back; the block becomes a firepit (convertTo FIREPIT). The item, the burn and the sound
                // follow only a conversion that happened (a refused one would duplicate the pot).
                boolean lit = f.lit();
                services.effects().toFirepit(block, player,
                    () -> {
                        if (lit) {
                            services.effects().sound(block, PotEffects.Sound.COOL);
                        }
                    },
                    () -> {
                        services.effects().give(player, services.potItem().get());
                        if (lit) {
                            services.effects().hurt(player, BURN_DAMAGE);
                        }
                    },
                    () -> services.effects().spawn(block, services.potItem().get()));
                return DeviceResult.consume(record);
            }
            if (!boiling && !empty) {
                Optional<BarrelFluidTransfer.ItemTank> tank = services.tanks().tank(stack);
                if (tank.isPresent()) {
                    boolean noOutput = !data.hasOutput();
                    Optional<BarrelFluidTransfer.Result> moved = BarrelFluidTransfer.transfer(data.fluid(), false,
                        fluid -> noOutput && services.book().usable(fluid), PotData.CAPACITY, tank.get());
                    if (moved.isPresent()) {
                        BarrelFluidTransfer.Result r = moved.get();
                        ItemStack container = services.tanks().withContent(stack, r.item());
                        services.effects().transfer(block, r.moved(),
                            r.direction() == FluidTransferRules.BlockTransfer.FILL_ITEM);
                        updateHand(player, ctx.hand(), stack, container, creative);
                        return finish(ctx, record.withState(data.withFluid(r.barrel())), true);
                    }
                }
            }
            if (!boiling && !empty && data.hasOutput()) {
                Optional<Taken> taken = takeSoup(data.output(), stack, services.book()::itemId);
                if (taken.isEmpty()) {
                    taken = takeJam(data.output(), stack);
                }
                if (taken.isPresent()) {
                    // TFC SoupOutput / JamOutput.onInteract: clickedWith.shrink(1) in every game mode (creative too)
                    shrink(player, ctx.hand());
                    services.effects().give(player, taken.get().item());
                    return finish(ctx, record.withState(data.withOutput(taken.get().after())), true);
                }
            }
            // tryInsertLog(player, held, pot, ...): a fuel into the first empty fuel slot
            if (!empty && isItemValid(FirepitSim.SLOT_FUEL_INPUT, stack)) {
                for (int i = FirepitSim.SLOT_FUEL_CONSUME; i <= FirepitSim.SLOT_FUEL_INPUT; i++) {
                    if (record.slot(i).isEmpty()) {
                        ItemStack one = stack.clone();
                        one.setAmount(1);
                        record = record.withSlot(i, stacks.encode(one));
                        if (!creative) {
                            shrink(player, ctx.hand());
                        }
                        services.effects().sound(block, PotEffects.Sound.LOG);
                        return finish(ctx, record, true);
                    }
                }
            }
            services.effects().openScreen(player, block);
            return finish(ctx, record, true);
        }

        /** The burn-out callback, fire starting (a pending light) and a lost floor. */
        @Override
        public DeviceResult<PotData> neighbourChanged(DeviceContext<PotData> ctx) {
            DeviceRecord<PotData> record = sampled(ctx);
            Light l = pending.remove(Pos.of(ctx.block()));
            if (l != null) {
                List<Optional<Fuel>> queue = queue(record);
                FirepitSim.Result r = FirepitSim.light(record.state().firepit(), queue, services.random().get());
                record = consume(record, r.consumed()).withState(record.state().withFirepit(r.data()));
                l.lit = r.data().lit();
            } else if (!services.canSurvive().test(ctx.block())) {
                services.effects().breakBlock(ctx.block());
            }
            return finish(ctx, record, false);
        }

        /** TFC {@code removed} -> {@code ejectInventory}: the slots drop (the kind's DROP) and the ash as wood ash. */
        @Override
        public void removed(DeviceContext<PotData> ctx) {
            int ash = ctx.deviceState().firepit().ash();
            if (ash > 0) {
                services.effects().spawn(ctx.block(), services.woodAsh().apply(ash));
            }
        }

        /** The caught-up record with the rain sampled now. */
        private DeviceRecord<PotData> sampled(DeviceContext<PotData> ctx) {
            PotData d = ctx.deviceState();
            return ctx.record().withState(d.withFirepit(d.firepit().withRaining(services.raining().test(ctx.block()))));
        }

        /** The block state follows the record; a lit pot queues its burn-out callback. */
        private DeviceResult<PotData> finish(DeviceContext<PotData> ctx, DeviceRecord<PotData> record, boolean consumed) {
            Block block = ctx.block();
            PotData data = record.state();
            String axis = ctx.state().properties().getOrDefault(AXIS, "x");
            BlockStateRef wanted = state(data, axis);
            if ("true".equals(ctx.state().properties().get(LIT)) && !data.firepit().lit()) {
                services.effects().sound(block, PotEffects.Sound.EXTINGUISH);
            }
            if (!wanted.equals(ctx.state())) {
                services.effects().show(block, wanted);
            }
            if (data.firepit().lit()) {
                long out = FirepitSim.ticksUntilOut(data.firepit(), queue(record));
                services.effects().callbackIn(block, Math.max(1, out));
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

    /** TFC {@code FluidHelpers.transferBetweenBlockEntityAndItem}: the held container replaced by its filled or emptied self. */
    static void updateHand(Player player, EquipmentSlot hand, ItemStack held, ItemStack container, boolean creative) {
        if (creative) {
            return;
        }
        if (held.getAmount() <= 1) {
            player.getInventory().setItem(hand, container);
            return;
        }
        ItemStack rest = held.clone();
        rest.setAmount(held.getAmount() - 1);
        player.getInventory().setItem(hand, rest);
        for (ItemStack r : player.getInventory().addItem(container).values()) {
            player.getWorld().dropItem(player.getLocation(), r, e -> e.setPickupDelay(0));
        }
    }
}
