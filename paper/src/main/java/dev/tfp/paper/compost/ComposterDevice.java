/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/TFCComposterBlock.java @ c3b4d9536 (useItemOn, randomTick,
 * animateTick, setPlacedBy, STAGE, TYPE, InventoryRemoveBehavior.DROP),
 * file: src/main/java/net/dries007/tfc/common/blockentities/ComposterBlockEntity.java @ c3b4d9536 (use, randomTick,
 * checkAndSetEmpty, setAndUpdateSlots, getReadyTicks, finishUse, the bottom-face extraction of composterEnableAutomation)
 */
package dev.tfp.paper.compost;

import dev.tfp.core.compost.CompostAddition;
import dev.tfp.core.compost.CompostBin;
import dev.tfp.core.compost.CompostRules;
import dev.tfp.core.compost.ComposterState;
import dev.tfp.core.device.DeviceRecord;
import dev.tfp.paper.device.DeviceContext;
import dev.tfp.paper.device.DeviceHandler;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceResult;
import dev.tfp.paper.device.StackCodec;
import dev.tfp.paper.integration.BlockStateRef;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.ToLongFunction;
import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * The composter as a device kind (PAR-PROC-11): TFC's {@code TFCComposterBlock} + {@code ComposterBlockEntity} on the
 * device framework (PAR-INFRA-02), with the rules of {@code core/compost} ({@link CompostBin}).
 *
 * <p>Right click ({@link DeviceHandler#useWithItem}), in TFC's order: nothing while the player stands in the composter; an
 * empty hand while sneaking empties the bin (one compost from a ready bin, the rotten count from a rotten one, nothing from
 * an unready one; always reset); a green or brown item is added (one item, none in creative) or refused with TFC's message
 * when that side is full; any other click on a rotten bin shows the rotten message; a poison rots the bin; anything else
 * passes to the item's own use.
 *
 * <p>Lazy: the bin keeps the tick of its last change and the ready ticks sampled at the last callback
 * ({@link ComposterState}); every callback and every catch-up settles it ({@link ComposterState#settle}), and a callback
 * is scheduled at the ripening tick while the chunk is loaded so the block state turns {@code ready} on time. The output
 * lives in the bin's {@code output} count (TFC's one-slot inventory); a hopper directly below takes it at each callback.
 */
public final class ComposterDevice {
    public static final String ID = "tfp:composter";
    /** TFC {@code TFCComposterBlock.STAGE} ({@code STAGE_8}). */
    public static final String STAGE = "stage";
    /** TFC {@code TFCComposterBlock.TYPE}: {@code normal}, {@code ready}, {@code rotten}. */
    public static final String TYPE = "type";
    /** TFC {@code TFCItems.COMPOST}. */
    public static final String COMPOST = "tfp:compost";
    /** TFC {@code TFCItems.ROTTEN_COMPOST}. */
    public static final String ROTTEN_COMPOST = "tfp:rotten_compost";

    /**
     * What the device reads from the rest of the plugin.
     *
     * @param addition   TFC {@code getCompost}: the addition of a held stack (its composter tags and whether it is rotten)
     * @param readyTicks TFC {@code getReadyTicks}: the ready ticks at a composter now (rainfall, neighbouring composters)
     * @param compost    a stack of {@code n} compost, empty when the item is unknown
     * @param rotten     a stack of {@code n} rotten compost, empty when the item is unknown
     * @param automation TFC {@code composterEnableAutomation}: whether a hopper below takes the output
     * @param effects    the world effects
     */
    public record Services(Function<ItemStack, CompostAddition> addition, ToLongFunction<Block> readyTicks,
            IntFunction<ItemStack> compost, IntFunction<ItemStack> rotten, boolean automation, ComposterEffects effects) {
        public Services {
            Objects.requireNonNull(addition, "addition");
            Objects.requireNonNull(readyTicks, "readyTicks");
            Objects.requireNonNull(compost, "compost");
            Objects.requireNonNull(rotten, "rotten");
            Objects.requireNonNull(effects, "effects");
        }
    }

    private ComposterDevice() {
    }

    /** The device kind of {@link #ID}: no slots (the output is the bin's count), settled on catch-up. */
    public static DeviceKind<ComposterState> kind(Services services) {
        return DeviceKind.builder(ID, ComposterState.CODEC, ComposterState.FRESH)
            .slots(0)
            .advance((record, from, to) -> record.withState(record.state().settle(to)))
            .removal(DeviceKind.RemoveBehaviour.DROP) // no slots: the output drop is the handler's removed()
            .handler(new Handler(services))
            .build();
    }

    /** The block state of {@code bin} (TFC {@code setState(type, stage)}). */
    public static BlockStateRef state(CompostBin bin) {
        return BlockStateRef.of(ID)
            .with(STAGE, Integer.toString(bin.stage()))
            .with(TYPE, bin.type().name().toLowerCase(Locale.ROOT));
    }

    /** Whether a composter state is rotten (TFC {@code isRotten}; the hook PAR-FAUNA-13's infestation reads). */
    public static boolean rotten(ComposterState state) {
        return state.bin().type() == CompostBin.Type.ROTTEN;
    }

    /** The output of {@code bin} as a stack: compost for a ready bin, rotten compost otherwise; empty for none. */
    static ItemStack output(CompostBin bin, Services services) {
        if (bin.output() <= 0) {
            return ItemStack.empty();
        }
        ItemStack s = bin.type() == CompostBin.Type.READY ? services.compost().apply(bin.output())
            : services.rotten().apply(bin.output());
        return s == null ? ItemStack.empty() : s;
    }

    private record Handler(Services services) implements DeviceHandler<ComposterState> {
        @Override
        public DeviceResult<ComposterState> useWithItem(DeviceContext<ComposterState> ctx) {
            Player player = ctx.player();
            if (player == null || ctx.hand() == null) {
                return DeviceResult.pass();
            }
            ComposterState before = ctx.deviceState();
            ComposterState state = sampled(ctx);
            Block block = ctx.block();
            long now = ctx.now();
            // TFC: if (player.blockPosition().equals(pos)) return FAIL (not consumed)
            if (player.getLocation().getBlockX() == block.getX() && player.getLocation().getBlockY() == block.getY()
                && player.getLocation().getBlockZ() == block.getZ()) {
                return finish(ctx, before, state, false);
            }
            ItemStack stack = ctx.item();
            CompostBin bin = state.bin();
            if (StackCodec.isEmpty(stack) && player.isSneaking()) {
                CompostBin.Extraction ex = bin.extract(now);
                if (ex.compost() > 0) {
                    give(ctx, services.compost().apply(ex.compost()));
                }
                if (ex.rotten() > 0) {
                    give(ctx, services.rotten().apply(ex.rotten()));
                }
                services.effects().sound(block, ComposterEffects.Sound.EXTRACT);
                return finish(ctx, before, state.withBin(ex.bin()), true);
            }
            CompostAddition addition = StackCodec.isEmpty(stack) ? CompostAddition.NOTHING : services.addition().apply(stack);
            boolean creative = player.getGameMode() == GameMode.CREATIVE;
            CompostBin.Insert insert = bin.add(addition, creative, now);
            switch (insert.outcome()) {
                case ADDED_GREEN, ADDED_BROWN, POISONED -> {
                    shrink(player, ctx.hand(), insert.consumed());
                    services.effects().sound(block, ComposterEffects.Sound.ADD);
                }
                case FULL_GREEN -> services.effects().message(player, ComposterEffects.Message.TOO_MANY_GREENS);
                case FULL_BROWN -> services.effects().message(player, ComposterEffects.Message.TOO_MANY_BROWNS);
                case ROTTEN_REJECT -> services.effects().message(player, ComposterEffects.Message.ROTTEN);
                case PASS -> {
                    return finish(ctx, before, state, false);
                }
                default -> throw new IllegalStateException("unknown outcome " + insert.outcome());
            }
            return finish(ctx, before, state.withBin(insert.bin()), true);
        }

        /** The neighbour update and the ripening callback: re-sample, settle, feed a hopper below. */
        @Override
        public DeviceResult<ComposterState> neighbourChanged(DeviceContext<ComposterState> ctx) {
            ComposterState before = ctx.deviceState();
            return finish(ctx, before, sampled(ctx), false);
        }

        /** TFC {@code setPlacedBy}: {@code resetCounter}. */
        @Override
        public DeviceResult<ComposterState> placed(DeviceContext<ComposterState> ctx) {
            ComposterState state = new ComposterState(CompostBin.empty().reset(ctx.now()),
                services.readyTicks().applyAsLong(ctx.block()));
            services.effects().show(ctx.block(), state.bin());
            return DeviceResult.update(ctx.record().withState(state));
        }

        /** TFC {@code InventoryRemoveBehavior.DROP}: the output slot drops with the block. */
        @Override
        public void removed(DeviceContext<ComposterState> ctx) {
            ItemStack out = output(ctx.deviceState().bin(), services);
            if (!StackCodec.isEmpty(out)) {
                services.effects().spawn(ctx.block(), out, false);
            }
        }

        /** The caught-up state with the ready ticks sampled now, settled at now (TFC's random ticks until now). */
        private ComposterState sampled(DeviceContext<ComposterState> ctx) {
            long ready = Math.max(0, services.readyTicks().applyAsLong(ctx.block()));
            return ctx.deviceState().withReadyTicks(ready).settle(ctx.now());
        }

        /**
         * The common tail of every callback: the hopper below takes the output, the block state follows the bin, the
         * ripening callback is queued, and an interaction shows the ready or rotten particles.
         */
        private DeviceResult<ComposterState> finish(DeviceContext<ComposterState> ctx, ComposterState before,
                ComposterState state, boolean consumed) {
            Block block = ctx.block();
            long now = ctx.now();
            ComposterState next = feedHopper(block, state, now);
            if (!state(next.bin()).equals(ctx.state())) { // the block state shown, also after a catch-up ripened the bin
                services.effects().show(block, next.bin());
            }
            long ripens = next.ripenedAt();
            if (ripens != CompostRules.NOT_RIPENING) {
                services.effects().ripenAt(block, Math.max(1, ripens - now));
            }
            if (consumed && next.bin().type() != CompostBin.Type.NORMAL) {
                services.effects().particles(block, next.bin().type());
            }
            DeviceRecord<ComposterState> record = ctx.record().withState(next);
            if (consumed) {
                return DeviceResult.consume(record);
            }
            return next.equals(before) ? DeviceResult.pass() : DeviceResult.update(record);
        }

        /** TFC's bottom-face extraction: a hopper below takes what it can of the output slot ({@code setAndUpdateSlots}). */
        private ComposterState feedHopper(Block block, ComposterState state, long now) {
            CompostBin bin = state.bin();
            if (!services.automation() || bin.output() <= 0) {
                return state;
            }
            ItemStack out = output(bin, services);
            if (StackCodec.isEmpty(out)) {
                return state;
            }
            int taken = Math.min(bin.output(), services.effects().hopperRoom(block, out));
            if (taken <= 0) {
                return state;
            }
            ItemStack moved = out.clone();
            moved.setAmount(taken);
            services.effects().toHopper(block, moved);
            return state.withBin(bin.outputChanged(bin.output() - taken, now));
        }

        /** TFC {@code Helpers.spawnItem(level, pos.above(), ...)}. */
        private void give(DeviceContext<ComposterState> ctx, ItemStack stack) {
            if (stack != null && !StackCodec.isEmpty(stack)) {
                services.effects().spawn(ctx.block(), stack, true);
            }
        }
    }

    /** TFC {@code stack.shrink(n)} on the hand's stack. */
    static void shrink(Player player, EquipmentSlot hand, int n) {
        if (n <= 0) {
            return;
        }
        ItemStack held = player.getInventory().getItem(hand);
        if (StackCodec.isEmpty(held)) {
            return;
        }
        int left = held.getAmount() - n;
        if (left <= 0) {
            player.getInventory().setItem(hand, null);
        } else {
            held.setAmount(left);
            player.getInventory().setItem(hand, held);
        }
    }
}
