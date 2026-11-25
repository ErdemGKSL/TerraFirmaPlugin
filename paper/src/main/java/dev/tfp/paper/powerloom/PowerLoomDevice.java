/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/rotation/PowerLoomBlockEntity.java @ c3b4d9536 (powerLoomTick,
 *       canPushManually),
 * file: src/main/java/net/dries007/tfc/common/blockentities/LoomBlockEntity.java @ c3b4d9536 (onRightClick, isItemValid,
 *       getSlotStackLimit, updateCachedRecipe, tick),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/PowerLoomBlock.java @ c3b4d9536 (useItemOn)
 */
package dev.tfp.paper.powerloom;

import dev.tfp.core.device.DeviceRecord;
import dev.tfp.core.powerloom.PowerLoomCatchUp;
import dev.tfp.core.powerloom.PowerLoomState;
import dev.tfp.paper.device.DeviceContext;
import dev.tfp.paper.device.DeviceHandler;
import dev.tfp.paper.device.DeviceInventory;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceResult;
import dev.tfp.paper.device.StackCodec;
import dev.tfp.paper.loom.LoomDevice;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * The power loom as a device kind (PAR-MECH-08): TFC's {@code PowerLoomBlock} + {@code PowerLoomBlockEntity}, a loom that the
 * rotation network pushes. Two slots (the yarn, the output), dropped with the block.
 *
 * <p>The hand does what it does at a loom except the push ({@code canPushManually} is false): sneaking takes the yarn back,
 * a finished cloth is taken, an empty loom takes one valid item, the same item adds more until the recipe's count is in. The
 * rest is the kind's catch-up ({@link #catchUp}): the ticks since the last look are stepped at the speed the last look saw
 * ({@link PowerLoomCatchUp}); the {@link PowerLoomService} looks at a loom with a rotation node on its side.
 */
public final class PowerLoomDevice {
    public static final String ID = "tfp:power_loom";
    public static final int SLOT_RECIPE = 0;
    public static final int SLOT_OUTPUT = 1;
    public static final String FACING = "facing";

    /** What the device reads from the rest of the plugin: the recipe of a stack ({@code LoomRecipe.getRecipe}), any thread. */
    public record Services(Function<ItemStack, Optional<LoomDevice.Recipe>> recipe) {
        public Services {
            Objects.requireNonNull(recipe, "recipe");
        }
    }

    private final Services services;
    private final DeviceKind<PowerLoomState> kind;

    public PowerLoomDevice(Services services, StackCodec stacks) {
        this.services = Objects.requireNonNull(services, "services");
        Objects.requireNonNull(stacks, "stacks");
        this.kind = DeviceKind.builder(ID, PowerLoomState.CODEC, PowerLoomState.FRESH)
            .slots(2)
            .slotLimit(64)
            .filter((slot, stack) -> slot != SLOT_RECIPE || services.recipe().apply(stack).isPresent()) // isItemValid
            .advance((record, from, to) -> catchUp(record, from, to, stacks))
            .removal(DeviceKind.RemoveBehaviour.DROP)
            .handler(new Handler())
            .build();
    }

    public DeviceKind<PowerLoomState> kind() {
        return kind;
    }

    /**
     * TFC {@code updateCachedRecipe}: the recipe numbers of the yarn slot go into the state; without a recipe, progress is 0
     * (unless a finished cloth still waits in the output).
     */
    DeviceRecord<PowerLoomState> refresh(DeviceRecord<PowerLoomState> record, StackCodec stacks) {
        PowerLoomState state = record.state();
        ItemStack yarn = stacks.decode(record.slot(SLOT_RECIPE));
        Optional<LoomDevice.Recipe> recipe = services.recipe().apply(yarn);
        PowerLoomState next = recipe.isPresent()
            ? state.withRecipe(recipe.get().steps(), recipe.get().inputCount(), yarn.getAmount())
            : state.withRecipe(0, 0, 0);
        if (recipe.isEmpty() && next.progress() > 0 && record.slot(SLOT_OUTPUT).isEmpty()) {
            next = next.cleared();
        }
        return next.equals(state) ? record : record.withState(next);
    }

    /**
     * The kind's catch-up: the recipe numbers read from the slots (so hopper-fed yarn counts too), then the ticks stepped; a
     * finished weave empties the yarn slot and fills the output (TFC {@code tick}).
     */
    DeviceRecord<PowerLoomState> catchUp(DeviceRecord<PowerLoomState> record, long from, long to, StackCodec stacks) {
        DeviceRecord<PowerLoomState> r = refresh(record, stacks);
        PowerLoomCatchUp.Result result = PowerLoomCatchUp.advance(r.state(), from, to);
        r = r.withState(result.state());
        if (result.finishedAt() >= 0) {
            Optional<LoomDevice.Recipe> recipe = services.recipe().apply(stacks.decode(r.slot(SLOT_RECIPE)));
            DeviceInventory<PowerLoomState> inv = new DeviceInventory<>(kind, r, stacks);
            inv = inv.set(SLOT_RECIPE, ItemStack.empty());
            if (recipe.isPresent()) {
                inv = inv.set(SLOT_OUTPUT, recipe.get().assemble().apply(ItemStack.empty()));
            }
            r = inv.record();
        }
        return r;
    }

    private final class Handler implements DeviceHandler<PowerLoomState> {
        @Override
        public DeviceResult<PowerLoomState> useWithItem(DeviceContext<PowerLoomState> ctx) {
            Player player = ctx.player();
            if (player == null || ctx.hand() != EquipmentSlot.HAND) {
                return DeviceResult.pass();
            }
            DeviceInventory<PowerLoomState> inv = ctx.inventory();
            PowerLoomState state = ctx.deviceState();
            ItemStack held = ctx.item();
            ItemStack yarn = inv.get(SLOT_RECIPE);
            ItemStack output = inv.get(SLOT_OUTPUT);
            if (player.isSneaking()) {
                if (!StackCodec.isEmpty(yarn)) {
                    give(player, yarn);
                    return changed(inv.set(SLOT_RECIPE, ItemStack.empty()).record().withState(state.cleared()), ctx);
                }
                return DeviceResult.pass();
            }
            if (!StackCodec.isEmpty(output)) {
                give(player, output);
                return changed(inv.set(SLOT_OUTPUT, ItemStack.empty()).record().withState(state.cleared()), ctx);
            }
            Optional<LoomDevice.Recipe> recipe = services.recipe().apply(yarn);
            if (StackCodec.isEmpty(yarn) && !StackCodec.isEmpty(held) && services.recipe().apply(held).isPresent()) {
                ItemStack one = held.clone();
                one.setAmount(1);
                take(ctx, held, 1);
                return changed(inv.set(SLOT_RECIPE, one).record(), ctx);
            }
            if (!StackCodec.isEmpty(yarn) && !StackCodec.isEmpty(held) && held.isSimilar(yarn) && recipe.isPresent()
                && recipe.get().inputCount() > yarn.getAmount()) {
                ItemStack more = yarn.clone();
                more.setAmount(yarn.getAmount() + 1);
                take(ctx, held, 1);
                return changed(inv.set(SLOT_RECIPE, more).record(), ctx);
            }
            return DeviceResult.pass(); // no manual push: the rotation pushes
        }

        private DeviceResult<PowerLoomState> changed(DeviceRecord<PowerLoomState> record, DeviceContext<PowerLoomState> ctx) {
            return DeviceResult.consume(refresh(record, ctx.stacks()));
        }

        @Override
        public DeviceResult<PowerLoomState> placed(DeviceContext<PowerLoomState> ctx) {
            return DeviceResult.pass();
        }
    }

    /** NeoForge {@code ItemHandlerHelper.giveItemToPlayer}: into the inventory, the rest dropped at the player. */
    private static void give(Player player, ItemStack stack) {
        if (StackCodec.isEmpty(stack)) {
            return;
        }
        for (ItemStack rest : player.getInventory().addItem(stack).values()) {
            player.getWorld().dropItem(player.getLocation(), rest, e -> e.setPickupDelay(0));
        }
    }

    private static void take(DeviceContext<PowerLoomState> ctx, ItemStack held, int amount) {
        ItemStack left = held.clone();
        left.setAmount(held.getAmount() - amount);
        ctx.player().getInventory().setItem(ctx.hand(), left.getAmount() <= 0 ? null : left);
    }
}
