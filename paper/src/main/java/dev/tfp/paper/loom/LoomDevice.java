/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/LoomBlockEntity.java @ c3b4d9536 (onRightClick, isItemValid,
 * getSlotStackLimit, tick),
 * file: src/main/java/net/dries007/tfc/common/blocks/wood/TFCLoomBlock.java @ c3b4d9536 (useItemOn, getStateForPlacement)
 */
package dev.tfp.paper.loom;

import dev.tfp.core.device.DeviceRecord;
import dev.tfp.core.loom.LoomRules;
import dev.tfp.core.loom.LoomState;
import dev.tfp.paper.device.DeviceContext;
import dev.tfp.paper.device.DeviceHandler;
import dev.tfp.paper.device.DeviceInventory;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceResult;
import dev.tfp.paper.device.StackCodec;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * The loom as a device kind (PAR-PROC-06): TFC's {@code TFCLoomBlock} + {@code LoomBlockEntity} on the device framework.
 * Two slots (the yarn, the output); dropped with the block.
 *
 * <p>Right click with the main hand, in TFC's order: sneaking takes the yarn back (progress reset); a finished cloth is taken
 * (progress reset); an empty loom takes one valid item from the hand (an item with a loom recipe, none while a cloth waits);
 * with yarn in, the same item adds one until the recipe's count is reached; an empty hand pushes when the full count is in,
 * steps are left, none is pending and more than 20 ticks passed since the last push. Nothing ticks: the step of a push is
 * counted when the loom is next looked at, and the device asks for a callback 20 ticks after each push so it finishes on
 * time ({@link LoomRules#settle}).
 */
public final class LoomDevice {
    public static final String ID = "tfp:wood/loom";
    public static final int SLOT_RECIPE = 0;
    public static final int SLOT_OUTPUT = 1;
    public static final String FACING = "facing";

    /**
     * A loom recipe (TFC {@code LoomRecipe}).
     *
     * @param inputCount TFC {@code getInputCount}: the yarn the ingredient needs
     * @param steps      TFC {@code getStepCount}
     * @param assemble   TFC {@code assemble}: the result (the recipe's provider, given an empty input as TFC does)
     */
    public record Recipe(int inputCount, int steps, UnaryOperator<ItemStack> assemble) {
    }

    /** What the device reads from the rest of the plugin: the recipe of a stack ({@code LoomRecipe.getRecipe}), any thread. */
    public record Services(Function<ItemStack, Optional<Recipe>> recipe, LoomEffects effects) {
        public Services {
            Objects.requireNonNull(recipe, "recipe");
            Objects.requireNonNull(effects, "effects");
        }
    }

    private LoomDevice() {
    }

    public static DeviceKind<LoomState> kind(Services services) {
        return DeviceKind.builder(ID, LoomState.CODEC, LoomState.FRESH)
            .slots(2)
            .slotLimit(64)
            .filter((slot, stack) -> slot != SLOT_RECIPE || services.recipe().apply(stack).isPresent()) // isItemValid
            .removal(DeviceKind.RemoveBehaviour.DROP)
            .handler(new Handler(services))
            .build();
    }

    /**
     * TFC {@code tick}: counts a due step; the last one empties the yarn slot and fills the output with the result.
     * Returns the record after it (the same one when nothing was due).
     */
    static DeviceRecord<LoomState> settle(DeviceContext<LoomState> ctx, Services services) {
        LoomState state = ctx.deviceState();
        DeviceInventory<LoomState> inv = ctx.inventory();
        Optional<Recipe> recipe = services.recipe().apply(inv.get(SLOT_RECIPE));
        if (!state.pending()) {
            return ctx.record();
        }
        if (recipe.isEmpty()) {
            return ctx.record().withState(state.cleared()); // the yarn went away: TFC resets the progress
        }
        LoomRules.Settled s = LoomRules.settle(state, ctx.now(), recipe.get().steps());
        if (s.state().equals(state)) {
            return ctx.record();
        }
        if (s.finished()) {
            ItemStack result = recipe.get().assemble().apply(ItemStack.empty());
            inv = inv.set(SLOT_RECIPE, ItemStack.empty()).set(SLOT_OUTPUT, result);
        }
        return inv.record().withState(s.state());
    }

    private record Handler(Services services) implements DeviceHandler<LoomState> {
        @Override
        public DeviceResult<LoomState> useWithItem(DeviceContext<LoomState> ctx) {
            Player player = ctx.player();
            if (player == null || ctx.hand() != EquipmentSlot.HAND) {
                return DeviceResult.pass();
            }
            DeviceRecord<LoomState> settled = settle(ctx, services);
            boolean changed = !settled.equals(ctx.record());
            DeviceInventory<LoomState> inv = new DeviceInventory<>(ctx.kind(), settled, ctx.stacks());
            LoomState state = settled.state();
            ItemStack held = ctx.item();
            ItemStack yarn = inv.get(SLOT_RECIPE);
            ItemStack output = inv.get(SLOT_OUTPUT);
            DeviceResult<LoomState> result = DeviceResult.pass();
            if (player.isSneaking()) {
                if (!StackCodec.isEmpty(yarn)) {
                    give(player, yarn);
                    result = DeviceResult.consume(inv.set(SLOT_RECIPE, ItemStack.empty()).record().withState(state.cleared()));
                }
            } else if (!StackCodec.isEmpty(output)) {
                give(player, output);
                result = DeviceResult.consume(inv.set(SLOT_OUTPUT, ItemStack.empty()).record().withState(state.cleared()));
            } else {
                Optional<Recipe> recipe = services.recipe().apply(yarn);
                if (StackCodec.isEmpty(yarn) && !StackCodec.isEmpty(held) && services.recipe().apply(held).isPresent()) {
                    ItemStack one = held.clone();
                    one.setAmount(1);
                    take(ctx, held, 1);
                    result = DeviceResult.consume(inv.set(SLOT_RECIPE, one).record());
                } else if (!StackCodec.isEmpty(yarn) && !StackCodec.isEmpty(held) && held.isSimilar(yarn) && recipe.isPresent()
                    && recipe.get().inputCount() > yarn.getAmount()) {
                    ItemStack more = yarn.clone();
                    more.setAmount(yarn.getAmount() + 1);
                    take(ctx, held, 1);
                    result = DeviceResult.consume(inv.set(SLOT_RECIPE, more).record());
                } else if (recipe.isPresent() && StackCodec.isEmpty(held)
                    && LoomRules.canPush(state, ctx.now(), recipe.get().steps(), recipe.get().inputCount(), yarn.getAmount())) {
                    services.effects().weave(ctx.block());
                    services.effects().wake(ctx.block(), LoomRules.PUSH_TICKS);
                    result = DeviceResult.consume(settled.withState(LoomRules.push(state, ctx.now())));
                }
            }
            if (result.consumed()) {
                return result;
            }
            return changed ? DeviceResult.update(settled) : DeviceResult.pass();
        }

        /** The callback 20 ticks after a push: counts the step. */
        @Override
        public DeviceResult<LoomState> neighbourChanged(DeviceContext<LoomState> ctx) {
            DeviceRecord<LoomState> settled = settle(ctx, services);
            if (settled.equals(ctx.record())) {
                if (ctx.deviceState().pending()) {
                    services.effects().wake(ctx.block(), LoomRules.PUSH_TICKS); // the calendar ran slower than the server
                }
                return DeviceResult.pass();
            }
            return DeviceResult.update(settled);
        }

        @Override
        public DeviceResult<LoomState> placed(DeviceContext<LoomState> ctx) {
            return DeviceResult.pass();
        }
    }

    private static void take(DeviceContext<LoomState> ctx, ItemStack held, int amount) {
        ItemStack left = held.clone();
        left.setAmount(held.getAmount() - amount);
        ctx.player().getInventory().setItem(ctx.hand(), left.getAmount() <= 0 ? null : left);
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
