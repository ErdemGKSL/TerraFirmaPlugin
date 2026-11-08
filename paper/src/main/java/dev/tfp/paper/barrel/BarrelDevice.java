/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/BarrelBlock.java @ c3b4d9536 (toggleSeal, useItemOn: sneak with
 * an empty hand toggles the seal, a fluid container transfers; attack: the drum note with a wooden rod),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/SealableDeviceBlock.java @ c3b4d9536 (beforeRemove),
 * file: src/main/java/net/dries007/tfc/common/blockentities/BarrelBlockEntity.java @ c3b4d9536 (SLOT_FLUID_CONTAINER_IN,
 * SLOT_FLUID_CONTAINER_OUT, SLOT_ITEM, isItemValid, onSeal, onUnseal, onCalendarUpdate, serverTick, ejectInventory),
 * file: src/main/java/net/dries007/tfc/common/fluids/FluidHelpers.java @ c3b4d9536 (with: the held container's update)
 */
package dev.tfp.paper.barrel;

import dev.tfp.core.barrel.BarrelData;
import dev.tfp.core.barrel.BarrelFluidTransfer;
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
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * The barrel as a device kind (PAR-PROC-08): TFC's {@code BarrelBlock} + {@code BarrelBlockEntity} on the device framework
 * for every {@code tfp:wood/barrel/<wood>} block.
 *
 * <p>Slots: TFC's three ({@link #SLOT_FLUID_CONTAINER_IN}, {@link #SLOT_FLUID_CONTAINER_OUT}, {@link #SLOT_ITEM}) and
 * {@link #EXCESS_SLOTS} slots for TFC's excess list. The tank, the seal and TFC's ticks are the record's
 * {@link BarrelData}; the block state's {@code sealed} property follows the record.
 *
 * <p>Right click with {@link DeviceHandler#useWithItem}, in TFC's order: sneaking with an empty hand toggles the seal; a held
 * fluid container (TFC's buckets, jugs, glass bottles, the vanilla buckets) transfers with the tank. Until the barrel GUI
 * (PAR-PROC-09) exists, any other held item goes into the item slot and an empty hand takes it out (TFC opens the GUI);
 * a sealed barrel accepts nothing. Instant recipes run right after the change that makes them match.
 *
 * <p>Lazy: the kind's catch-up ({@link #advance}) settles the sealed recipes from the record's last tick with
 * {@link BarrelSimulation}, so every callback, and the removal drop, sees the barrel as of now. Nothing ticks.
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

    /**
     * What the device reads from the rest of the plugin.
     *
     * @param book    the barrel recipes and the item tanks
     * @param fits    TFC {@code isItemValid(SLOT_ITEM)}: not huge and very heavy ({@code ItemSizes.fitsBarrel})
     * @param rod     whether a stack is a wooden rod
     * @param effects the world effects
     */
    public record Services(BarrelRecipeBook book, Predicate<ItemStack> fits, Predicate<ItemStack> rod,
            BarrelEffects effects) {
        public Services {
            Objects.requireNonNull(book, "book");
            Objects.requireNonNull(fits, "fits");
            Objects.requireNonNull(rod, "rod");
            Objects.requireNonNull(effects, "effects");
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

    // ---- the handler ----------------------------------------------------------------------------------

    private record Handler(Services services) implements DeviceHandler<BarrelData> {
        @Override
        public DeviceResult<BarrelData> useWithItem(DeviceContext<BarrelData> ctx) {
            Player player = ctx.player();
            if (player == null || ctx.hand() == null) {
                return DeviceResult.pass();
            }
            ItemStack stack = ctx.item();
            DeviceRecord<BarrelData> record = ctx.record();
            BarrelData data = record.state();
            BarrelContents contents = contents(record, ctx.stacks());
            BarrelRecipeBook.Session session = services.book().session();
            if (StackCodec.isEmpty(stack) && player.isSneaking()) {
                return DeviceResult.consume(toggleSeal(ctx, data, contents, session));
            }
            if (data.sealed()) {
                return DeviceResult.consume(); // TFC: canModify is false; the GUI (PAR-PROC-09) would open
            }
            Optional<BarrelFluidTransfer.ItemTank> tank = services.book().tanks().tank(stack);
            if (tank.isPresent()) {
                Optional<BarrelFluidTransfer.Result> moved = BarrelFluidTransfer.transfer(contents.fluid(), false,
                    session::usable, services.book().capacity(), tank.get());
                if (moved.isPresent()) {
                    BarrelFluidTransfer.Result r = moved.get();
                    ItemStack container = services.book().tanks().withContent(stack, r.item());
                    updateHand(player, ctx.hand(), stack, container);
                    services.effects().transfer(ctx.block(), r.moved(),
                        r.direction() == FluidTransferRules.BlockTransfer.FILL_ITEM);
                    return DeviceResult.consume(changed(ctx, data, contents.withFluid(r.barrel()), session));
                }
            }
            if (!StackCodec.isEmpty(stack)) {
                return insert(ctx, data, contents, stack, session);
            }
            return extract(ctx, data, contents, session);
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

        /** TFC {@code SealableDeviceBlock.beforeRemove}: an unsealed barrel ejects (the slots drop; the fluid splashes). */
        @Override
        public void removed(DeviceContext<BarrelData> ctx) {
            BarrelData data = ctx.deviceState();
            if (!data.fluid().isEmpty()) {
                services.effects().spilled(ctx.block(), data.fluid());
            }
        }

        /** TFC {@code toggleSeal}: {@code onUnseal} or {@code onSeal}, the block state, the sounds. */
        private DeviceRecord<BarrelData> toggleSeal(DeviceContext<BarrelData> ctx, BarrelData data, BarrelContents contents,
                BarrelRecipeBook.Session session) {
            long now = ctx.now();
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
                        services.effects().spawn(ctx.block(), s);
                    }
                }
                after = contents.withContainers(ItemStack.empty(), ItemStack.empty());
                BarrelRecipeView recipe = session.select(after);
                if (recipe != null) {
                    after = session.onSealed(recipe, after, now);
                }
                next = data.sealedAt(now, recipe != null);
            }
            services.effects().sealed(ctx.block(), state(ctx.state().id(), next.sealed()), next.sealed());
            return written(ctx, next, instant(ctx, after, session));
        }

        /** The item slot takes the held stack (TFC's GUI slot; filter {@code isItemValid}). */
        private DeviceResult<BarrelData> insert(DeviceContext<BarrelData> ctx, BarrelData data, BarrelContents contents,
                ItemStack stack, BarrelRecipeBook.Session session) {
            if (!services.fits().test(stack)) {
                return DeviceResult.consume();
            }
            ItemStack main = contents.main();
            int limit = Math.min(64, stack.getMaxStackSize());
            int room;
            if (StackCodec.isEmpty(main)) {
                room = limit;
            } else if (main.isSimilar(stack)) {
                room = Math.max(0, limit - main.getAmount());
            } else {
                room = 0;
            }
            int put = Math.min(room, stack.getAmount());
            if (put <= 0) {
                return DeviceResult.consume();
            }
            ItemStack next = stack.clone();
            next.setAmount((StackCodec.isEmpty(main) ? 0 : main.getAmount()) + put);
            ItemStack left = stack.clone();
            left.setAmount(stack.getAmount() - put);
            ctx.player().getInventory().setItem(ctx.hand(), left.getAmount() <= 0 ? null : left);
            return DeviceResult.consume(changed(ctx, data, contents.withMain(next), session));
        }

        /** An empty hand takes the item slot (then the excess moves in on the next catch-up, as TFC's next tick). */
        private DeviceResult<BarrelData> extract(DeviceContext<BarrelData> ctx, BarrelData data, BarrelContents contents,
                BarrelRecipeBook.Session session) {
            ItemStack main = contents.main();
            if (StackCodec.isEmpty(main)) {
                return DeviceResult.consume();
            }
            give(ctx.player(), main);
            return DeviceResult.consume(changed(ctx, data, contents.withMain(ItemStack.empty()), session));
        }

        /** After a change of the contents: the instant recipes, then the record. */
        private DeviceRecord<BarrelData> changed(DeviceContext<BarrelData> ctx, BarrelData data, BarrelContents contents,
                BarrelRecipeBook.Session session) {
            return written(ctx, data, instant(ctx, contents, session));
        }

        private BarrelContents instant(DeviceContext<BarrelData> ctx, BarrelContents contents,
                BarrelRecipeBook.Session session) {
            Optional<BarrelRecipeBook.Applied> applied = session.applyInstant(contents);
            if (applied.isEmpty()) {
                return contents;
            }
            services.effects().recipe(ctx.block(), applied.get().sound());
            return applied.get().contents();
        }

        private DeviceRecord<BarrelData> written(DeviceContext<BarrelData> ctx, BarrelData data, BarrelContents contents) {
            return write(ctx.record(), data, contents, ctx.stacks());
        }
    }

    /** Vanilla {@code NoteBlock.getPitchFromNote}. */
    static float pitch(int note) {
        return (float) Math.pow(2.0, (note - 12) / 12.0);
    }

    /**
     * TFC {@code FluidHelpers.with(player, hand)} after {@code updateContainerItem}: a single held item is replaced by the
     * container; from a larger stack one item is used and the container goes into the inventory (or drops).
     */
    static void updateHand(Player player, EquipmentSlot hand, ItemStack held, ItemStack container) {
        if (held.getAmount() <= 1) {
            player.getInventory().setItem(hand, container);
            return;
        }
        ItemStack rest = held.clone();
        if (player.getGameMode() != GameMode.CREATIVE) {
            rest.setAmount(held.getAmount() - 1);
        }
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
