/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/BarrelBlock.java @ c3b4d9536 (toggleSeal, useItemOn: sneak with
 * an empty hand toggles the seal, a fluid container transfers, anything else opens the menu; attack: the drum note with a
 * wooden rod),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/SealableDeviceBlock.java @ c3b4d9536 (beforeRemove),
 * file: src/main/java/net/dries007/tfc/common/blockentities/BarrelBlockEntity.java @ c3b4d9536 (SLOT_FLUID_CONTAINER_IN,
 * SLOT_FLUID_CONTAINER_OUT, SLOT_ITEM, isItemValid, onSeal, onUnseal, onCalendarUpdate, serverTick: updateFluidIOSlots and
 * the rain fill, ejectInventory),
 * file: src/main/java/net/dries007/tfc/common/fluids/FluidHelpers.java @ c3b4d9536 (with: the held container's update)
 */
package dev.tfp.paper.barrel;

import dev.tfp.core.barrel.BarrelData;
import dev.tfp.core.barrel.BarrelFluidTransfer;
import dev.tfp.core.barrel.BarrelRain;
import dev.tfp.core.barrel.BarrelSimulation;
import dev.tfp.core.device.DeviceRecord;
import dev.tfp.core.device.ItemBlob;
import dev.tfp.core.fluid.FluidTransferRules;
import dev.tfp.core.vessel.FluidAmount;
import dev.tfp.paper.device.DeviceContext;
import dev.tfp.paper.device.DeviceHandler;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceResult;
import dev.tfp.paper.device.StackCodec;
import dev.tfp.paper.integration.BlockStateRef;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * The barrel as a device kind (PAR-PROC-08): TFC's {@code BarrelBlock} + {@code BarrelBlockEntity} on the device framework
 * for every {@code tfp:wood/barrel/<wood>} block.
 *
 * <p>Slots: TFC's three ({@link #SLOT_FLUID_CONTAINER_IN}, {@link #SLOT_FLUID_CONTAINER_OUT}, {@link #SLOT_ITEM}) and
 * {@link #EXCESS_SLOTS} slots for TFC's excess list. The tank, the seal, TFC's ticks and the rain clock are the record's
 * {@link BarrelData}; the block state's {@code sealed} property follows the record.
 *
 * <p>Right click with {@link DeviceHandler#useWithItem}, in TFC's order: sneaking with an empty hand toggles the seal; a held
 * fluid container (TFC's buckets, jugs, glass bottles, the vanilla buckets) transfers with the tank; anything else opens the
 * barrel screen (PAR-PROC-35, {@link Screens}), where the three slots are edited ({@link BarrelScreenLink}). Instant
 * recipes run right after the change that makes them match.
 *
 * <p>Lazy: the kind's catch-up ({@link #advance}) settles the sealed recipes from the record's last tick with
 * {@link BarrelSimulation}, so every callback, and the removal drop, sees the barrel as of now. Every access that sees the
 * world (a click, the open screen, {@code /tfp debug barrel}) also {@link #settle}s TFC's fluid IO slots and the rain fill
 * ({@link BarrelRain}). Nothing ticks.
 */
public final class BarrelDevice {
    /** The kind id: covers {@code tfp:wood/barrel/<wood>}. */
    public static final String ID = "tfp:wood/barrel";
    public static final int SLOT_FLUID_CONTAINER_IN = 0;
    public static final int SLOT_FLUID_CONTAINER_OUT = 1;
    public static final int SLOT_ITEM = 2;
    public static final int FIRST_EXCESS = 3;
    /** TFC's excess list is unbounded; the record keeps this many stacks (ledger divergence). */
    public static final int EXCESS_SLOTS = 32;
    public static final int SLOTS = FIRST_EXCESS + EXCESS_SLOTS;
    /** TFC {@code SealableDeviceBlock.SEALED}. */
    public static final String SEALED = "sealed";
    /** NeoForge {@code Tags.Items.RODS_WOODEN}. */
    public static final String RODS_WOODEN = "c:rods/wooden";

    /** TFC {@code level.isRainingAt(pos.above())} for a barrel; region thread of the barrel. */
    @FunctionalInterface
    public interface Rain {
        boolean rainingAbove(Block barrel);

        /** Never raining (tests, no precipitation service). */
        Rain NONE = barrel -> false;
    }

    /** TFC {@code serverPlayer.openMenu(barrel)}: opens the barrel screen for a player. */
    @FunctionalInterface
    public interface Screens {
        /**
         * Called on the barrel's region thread from the click, before the click's record is stored; an implementation
         * opens the screen on the player's thread one tick later.
         */
        void open(Player player, Block block, String blockId);

        /** No screen (tests, the barrel GUI module disabled): the click does nothing. */
        Screens NONE = (player, block, blockId) -> {
        };
    }

    /**
     * What the device reads from the rest of the plugin.
     *
     * @param book    the barrel recipes and the item tanks
     * @param fits    TFC {@code isItemValid(SLOT_ITEM)}: not huge and very heavy ({@code ItemSizes.fitsBarrel})
     * @param rod     whether a stack is a wooden rod
     * @param effects the world effects
     * @param rain    the rain at the block above
     * @param screens opens the barrel screen
     */
    public record Services(BarrelRecipeBook book, Predicate<ItemStack> fits, Predicate<ItemStack> rod,
            BarrelEffects effects, Rain rain, Screens screens) {
        public Services {
            Objects.requireNonNull(book, "book");
            Objects.requireNonNull(fits, "fits");
            Objects.requireNonNull(rod, "rod");
            Objects.requireNonNull(effects, "effects");
            Objects.requireNonNull(rain, "rain");
            Objects.requireNonNull(screens, "screens");
        }

        /** No rain, no screen. */
        public Services(BarrelRecipeBook book, Predicate<ItemStack> fits, Predicate<ItemStack> rod, BarrelEffects effects) {
            this(book, fits, rod, effects, Rain.NONE, Screens.NONE);
        }
    }

    private BarrelDevice() {
    }

    /** The device kind of {@link #ID}; {@code stacks} is the device service's slot codec (the catch-up decodes slots). */
    public static DeviceKind<BarrelData> kind(Services services, StackCodec stacks) {
        return DeviceKind.builder(ID, BarrelData.CODEC, BarrelData.FRESH)
            .slots(SLOTS)
            .filter((slot, stack) -> slot != SLOT_ITEM || services.fits().test(stack))
            .advance((record, from, to) -> advance(record, from, to, services, stacks))
            .removal(DeviceKind.RemoveBehaviour.DROP)
            .handler(new Handler(services))
            .build();
    }

    /** The block state of a barrel block id with {@code sealed}. */
    public static BlockStateRef state(String blockId, boolean sealed) {
        return BlockStateRef.of(blockId).with(SEALED, Boolean.toString(sealed));
    }

    // ---- contents <-> record ---------------------------------------------------------------------------

    /** The record's contents. */
    static BarrelContents contents(DeviceRecord<BarrelData> record, StackCodec stacks) {
        List<ItemStack> excess = new ArrayList<>();
        for (int i = 0; i < EXCESS_SLOTS; i++) {
            ItemStack s = stacks.decode(record.slot(FIRST_EXCESS + i));
            if (!StackCodec.isEmpty(s)) {
                excess.add(s);
            }
        }
        return new BarrelContents(record.state().fluid(), stacks.decode(record.slot(SLOT_ITEM)), excess,
            stacks.decode(record.slot(SLOT_FLUID_CONTAINER_IN)), stacks.decode(record.slot(SLOT_FLUID_CONTAINER_OUT)));
    }

    /** The record holding {@code contents} (the excess beyond {@link #EXCESS_SLOTS} stacks is not kept). */
    static DeviceRecord<BarrelData> write(DeviceRecord<BarrelData> record, BarrelData data, BarrelContents contents,
            StackCodec stacks) {
        List<ItemBlob> slots = new ArrayList<>(SLOTS);
        slots.add(stacks.encode(contents.containerIn()));
        slots.add(stacks.encode(contents.containerOut()));
        slots.add(stacks.encode(contents.main()));
        List<ItemStack> excess = contents.excess();
        for (int i = 0; i < EXCESS_SLOTS; i++) {
            slots.add(i < excess.size() ? stacks.encode(excess.get(i)) : ItemBlob.EMPTY);
        }
        return record.withSlots(slots).withState(data.withFluid(contents.fluid()));
    }

    /**
     * The catch-up over {@code [from, to)}: a sealed barrel settles its recipes ({@link BarrelSimulation#settle}); an
     * unsealed one moves its excess into an empty main slot and runs the instant recipes that move enables.
     */
    static DeviceRecord<BarrelData> advance(DeviceRecord<BarrelData> record, long from, long to, Services services,
            StackCodec stacks) {
        BarrelData data = record.state();
        BarrelContents contents = contents(record, stacks);
        BarrelRecipeBook.Session session = services.book().session();
        if (!data.sealed()) {
            BarrelContents flushed = contents.flushed();
            if (flushed.equals(contents)) {
                return record;
            }
            return write(record, data, session.instant(flushed, to), stacks);
        }
        BarrelSimulation.Progress<BarrelContents> p = BarrelSimulation.settle(contents, data.recipeTick(), from, to, session);
        if (p.contents().equals(contents) && p.recipeTick() == data.recipeTick()) {
            return record;
        }
        return write(record, data.withRecipeTick(p.recipeTick()), p.contents(), stacks);
    }

    // ---- PAR-PROC-35: the access-time settle ------------------------------------------------------------

    /**
     * What an access that sees the world does to the caught-up record before anything else: the rain fill up to
     * {@code now} ({@link BarrelRain#settle}, {@code raining} read at the block above), then TFC's fluid IO slots
     * ({@code updateFluidIOSlots}: a container in the fluid-in slot transfers with the tank when the fluid-out slot is empty,
     * and the result lands in fluid-out), then the instant recipes that change enables. Every barrel is upright until the
     * barrel rack (PAR-PROC-38) adds the facing.
     */
    static DeviceRecord<BarrelData> settle(DeviceRecord<BarrelData> record, long now, boolean raining, Block block,
            Services services, StackCodec stacks) {
        BarrelData before = record.state();
        BarrelData data = BarrelRain.settle(before, now, raining, true, services.book().capacity());
        DeviceRecord<BarrelData> next = data.equals(before) ? record : record.withState(data);
        if (data.sealed()) {
            return next;
        }
        BarrelContents contents = contents(next, stacks);
        BarrelContents io = fluidIo(contents, services, block);
        if (io == contents) {
            if (!data.fluid().equals(before.fluid())) {
                // the rain changed the tank: an instant recipe may match now
                BarrelContents after = instant(block, contents, services);
                return after.equals(contents) ? next : write(next, data, after, stacks);
            }
            return next;
        }
        return write(next, data, instant(block, io, services), stacks);
    }

    /** TFC {@code updateFluidIOSlots} once (it repeats every 5 ticks, but after one move the fluid-out slot is full). */
    static BarrelContents fluidIo(BarrelContents contents, Services services, Block block) {
        ItemStack in = contents.containerIn();
        if (StackCodec.isEmpty(in) || !StackCodec.isEmpty(contents.containerOut())) {
            return contents;
        }
        Optional<BarrelFluidTransfer.ItemTank> tank = services.book().tanks().tank(in);
        if (tank.isEmpty()) {
            return contents;
        }
        BarrelRecipeBook.Session session = services.book().session();
        Optional<BarrelFluidTransfer.Result> moved = BarrelFluidTransfer.transfer(contents.fluid(), false, session::usable,
            services.book().capacity(), tank.get());
        if (moved.isEmpty()) {
            return contents;
        }
        BarrelFluidTransfer.Result r = moved.get();
        ItemStack container = services.book().tanks().withContent(in, r.item());
        ItemStack rest;
        if (in.getAmount() > 1) {
            // a new container was produced: it goes out, the rest of the stack stays in
            rest = in.clone();
            rest.setAmount(in.getAmount() - 1);
        } else {
            rest = ItemStack.empty();
        }
        services.effects().transfer(block, r.moved(), r.direction() == FluidTransferRules.BlockTransfer.FILL_ITEM);
        return contents.withFluid(r.barrel()).withContainers(rest, container);
    }

    /** The instant recipes after a change of the contents (TFC {@code needsInstantRecipeUpdate}). */
    static BarrelContents instant(Block block, BarrelContents contents, Services services) {
        Optional<BarrelRecipeBook.Applied> applied = services.book().session().applyInstant(contents);
        if (applied.isEmpty()) {
            return contents;
        }
        services.effects().recipe(block, applied.get().sound());
        return applied.get().contents();
    }

    /**
     * TFC {@code toggleSeal}: {@code onUnseal} or {@code onSeal}, the block state, the sounds. {@code record} is settled.
     */
    static DeviceRecord<BarrelData> toggleSeal(DeviceRecord<BarrelData> record, long now, Block block, String blockId,
            Services services, StackCodec stacks) {
        BarrelData data = record.state();
        BarrelContents contents = contents(record, stacks);
        BarrelRecipeBook.Session session = services.book().session();
        BarrelData next;
        BarrelContents after;
        if (data.sealed()) {
            // onUnseal: the ticks cleared, then the current recipe's on_unseal
            next = data.unsealed();
            BarrelRecipeView recipe = session.select(contents);
            after = recipe == null ? contents : session.onUnsealed(recipe, contents);
        } else {
            // onSeal: the container slots are dropped, then the recipe's on_seal, which starts at the seal tick
            for (ItemStack s : List.of(contents.containerIn(), contents.containerOut())) {
                if (!StackCodec.isEmpty(s)) {
                    services.effects().spawn(block, s);
                }
            }
            after = contents.withContainers(ItemStack.empty(), ItemStack.empty());
            BarrelRecipeView recipe = session.select(after);
            if (recipe != null) {
                after = session.onSealed(recipe, after, now);
            }
            next = data.sealedAt(now, recipe != null);
        }
        services.effects().sealed(block, state(blockId, next.sealed()), next.sealed());
        return write(record, next, instant(block, after, services), stacks);
    }

    /**
     * The three slots as a screen left them (TFC's slot callbacks: {@code setAndUpdateSlots}), then the fluid IO slots
     * and the instant recipes. {@code record} is settled and unsealed.
     */
    static DeviceRecord<BarrelData> withSlots(DeviceRecord<BarrelData> record, ItemStack in, ItemStack out, ItemStack item,
            Block block, Services services, StackCodec stacks) {
        BarrelContents contents = contents(record, stacks).withContainers(in, out).withMain(item);
        BarrelContents io = fluidIo(contents, services, block);
        return write(record, record.state(), instant(block, io, services), stacks);
    }

    /** After a write: the fluid surface follows the tank and the seal. */
    static void surface(Block block, BarrelData before, BarrelData after, Services services) {
        if (before.sealed() != after.sealed() || !before.fluid().equals(after.fluid())) {
            services.effects().surface(block, after.sealed() ? FluidAmount.EMPTY : after.fluid(),
                services.book().capacity());
        }
    }

    // ---- the handler ----------------------------------------------------------------------------------

    private record Handler(Services services) implements DeviceHandler<BarrelData> {
        @Override
        public DeviceResult<BarrelData> useWithItem(DeviceContext<BarrelData> ctx) {
            Player player = ctx.player();
            if (player == null || ctx.hand() == null) {
                return DeviceResult.pass();
            }
            ItemStack stack = ctx.item();
            BarrelData original = ctx.record().state();
            DeviceRecord<BarrelData> record = settle(ctx.record(), ctx.now(), services.rain().rainingAbove(ctx.block()),
                ctx.block(), services, ctx.stacks());
            DeviceRecord<BarrelData> result = click(ctx, player, stack, record);
            surface(ctx.block(), original, result.state(), services);
            return DeviceResult.consume(result);
        }

        private DeviceRecord<BarrelData> click(DeviceContext<BarrelData> ctx, Player player, ItemStack stack,
                DeviceRecord<BarrelData> record) {
            BarrelData data = record.state();
            if (StackCodec.isEmpty(stack) && player.isSneaking()) {
                return toggleSeal(record, ctx.now(), ctx.block(), ctx.state().id(), services, ctx.stacks());
            }
            if (!data.sealed()) {
                Optional<BarrelFluidTransfer.ItemTank> tank = services.book().tanks().tank(stack);
                if (tank.isPresent()) {
                    BarrelContents contents = contents(record, ctx.stacks());
                    Optional<BarrelFluidTransfer.Result> moved = BarrelFluidTransfer.transfer(contents.fluid(), false,
                        services.book().session()::usable, services.book().capacity(), tank.get());
                    if (moved.isPresent()) {
                        BarrelFluidTransfer.Result r = moved.get();
                        ItemStack container = services.book().tanks().withContent(stack, r.item());
                        updateHand(player, ctx.hand(), stack, container);
                        services.effects().transfer(ctx.block(), r.moved(),
                            r.direction() == FluidTransferRules.BlockTransfer.FILL_ITEM);
                        return write(record, data, instant(ctx.block(), contents.withFluid(r.barrel()), services),
                            ctx.stacks());
                    }
                }
            }
            // TFC: serverPlayer.openMenu(barrel), also while sealed (the screen locks the slots then)
            services.screens().open(player, ctx.block(), ctx.state().id());
            return record;
        }

        /** TFC {@code BarrelBlock.attack}: a sealed barrel hit with a wooden rod sounds its fill level. */
        @Override
        public DeviceResult<BarrelData> attack(DeviceContext<BarrelData> ctx) {
            Player player = ctx.player();
            BarrelData data = ctx.deviceState();
            if (player != null && data.sealed() && services.rod().test(player.getInventory().getItemInMainHand())) {
                float fill = (float) data.fluid().amount() / services.book().capacity();
                int note = (int) Math.ceil(fill * 24);
                services.effects().drum(ctx.block(), pitch(note));
            }
            return DeviceResult.pass();
        }

        /**
         * TFC {@code SealableDeviceBlock.beforeRemove} ejects only an unsealed barrel; here every barrel, sealed or not,
         * drops its slots and splashes its fluid (the item never carries contents; see the ledger's DROP divergence).
         */
        @Override
        public void removed(DeviceContext<BarrelData> ctx) {
            BarrelData data = ctx.deviceState();
            if (!data.fluid().isEmpty()) {
                services.effects().spilled(ctx.block(), data.fluid());
            }
            services.effects().surface(ctx.block(), FluidAmount.EMPTY, services.book().capacity());
        }
    }

    /** Vanilla {@code NoteBlock.getPitchFromNote}. */
    static float pitch(int note) {
        return (float) Math.pow(2.0, (note - 12) / 12.0);
    }

    /**
     * TFC {@code FluidHelpers.with(player, hand)} after {@code updateContainerItem}: a single held item is replaced by the
     * container; from a larger stack one item is used and the container goes into the inventory (or drops). In creative a
     * single held item stays as it was (TFC skips {@code setItemInHand}); a larger stack still loses one item, because TFC
     * shrinks {@code originalStack} in place before the creative check.
     */
    static void updateHand(Player player, EquipmentSlot hand, ItemStack held, ItemStack container) {
        if (held.getAmount() <= 1) {
            if (player.getGameMode() != GameMode.CREATIVE) {
                player.getInventory().setItem(hand, container);
            }
            return;
        }
        ItemStack rest = held.clone();
        rest.setAmount(held.getAmount() - 1);
        player.getInventory().setItem(hand, rest);
        give(player, container);
    }

    /** NeoForge {@code ItemHandlerHelper.giveItemToPlayer}: into the inventory, the rest dropped at the player. */
    static void give(Player player, ItemStack stack) {
        if (StackCodec.isEmpty(stack)) {
            return;
        }
        for (ItemStack rest : player.getInventory().addItem(stack).values()) {
            player.getWorld().dropItem(player.getLocation(), rest, e -> e.setPickupDelay(0));
        }
    }
}
