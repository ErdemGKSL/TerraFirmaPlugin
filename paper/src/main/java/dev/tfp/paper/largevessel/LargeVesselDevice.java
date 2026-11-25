/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/LargeVesselBlock.java @ c3b4d9536 (toggleSeal, useItemOn,
 *       neighborChanged),
 *       src/main/java/net/dries007/tfc/common/blocks/devices/SealableDeviceBlock.java @ c3b4d9536 (handleNeighborChanged,
 *       getStateForPlacement, beforeRemove),
 *       src/main/java/net/dries007/tfc/common/blockentities/LargeVesselBlockEntity.java @ c3b4d9536 (SLOTS, onSeal,
 *       onUnseal, VesselInventory.isItemValid, collectImplicitComponents)
 */
package dev.tfp.paper.largevessel;

import dev.tfp.core.device.DeviceRecord;
import dev.tfp.core.largevessel.LargeVesselState;
import dev.tfp.core.vessel.LargeVesselRules;
import dev.tfp.paper.device.DeviceContext;
import dev.tfp.paper.device.DeviceHandler;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceResult;
import dev.tfp.paper.device.StackCodec;
import dev.tfp.paper.integration.BlockStateRef;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * PAR-POT-06: the large vessel as a device kind ({@code tfp:ceramic/large_vessel}, also covering the sixteen glazed
 * {@code tfp:ceramic/large_vessel/<colour>}): TFC's {@code LargeVesselBlock} + {@code LargeVesselBlockEntity} on the device
 * framework, with the rules of {@code core/vessel/LargeVesselRules} and {@code core/largevessel}.
 *
 * <ul>
 *   <li>Nine slots; only items smaller than {@code LARGE} go in, and only while open ({@code VesselInventory.isItemValid}).</li>
 *   <li>Sneaking and right-clicking toggles the seal, any other click opens the screen ({@code useItemOn}). Sealing applies
 *       the {@code preserved} food trait to every stack, opening removes it ({@code onSeal}, {@code onUnseal}); both are
 *       instant, there is no timer.</li>
 *   <li>Redstone: a signal change seals or opens the vessel ({@code neighborChanged} with
 *       {@code largeVesselEnableRedstoneSeal}); the record keeps {@code POWERED}.</li>
 *   <li>Breaking: the whole record goes onto the dropped item ({@link DeviceKind.RemoveBehaviour#SAVE}); placing that item
 *       restores it, sealed or not (ledger: TFC ejects an open vessel's contents instead).</li>
 * </ul>
 */
public final class LargeVesselDevice {
    public static final String ID = "tfp:ceramic/large_vessel";
    /** TFC {@code SealableDeviceBlock.SEALED}, the CraftEngine block state property. */
    public static final String SEALED = "sealed";
    /** TFC {@code LargeVesselBlock.FACING}. */
    public static final String FACING = "facing";

    /**
     * What the device reads from the rest of the plugin.
     *
     * @param redstoneSeal TFC {@code largeVesselEnableRedstoneSeal}, read at each neighbour update
     * @param fits         TFC {@code isItemValid}: the stack is smaller than {@code LARGE}
     * @param signal       TFC {@code level.hasNeighborSignal(pos)} (region thread)
     * @param food         applies or removes the {@code preserved} trait on one stack ({@code onSeal}, {@code onUnseal})
     * @param effects      the world effects
     * @param open         opens the vessel's screen for the player (TFC {@code openMenu})
     */
    public record Services(BooleanSupplier redstoneSeal, Predicate<ItemStack> fits, Predicate<Block> signal, Preserver food,
            LargeVesselEffects effects, BiConsumer<Player, Block> open) {
        public Services {
            Objects.requireNonNull(redstoneSeal, "redstoneSeal");
            Objects.requireNonNull(fits, "fits");
            Objects.requireNonNull(signal, "signal");
            Objects.requireNonNull(food, "food");
            Objects.requireNonNull(effects, "effects");
            Objects.requireNonNull(open, "open");
        }
    }

    /** TFC {@code FoodCapability.applyTrait} / {@code removeTrait} of {@code preserved} on one stack (non-food is left). */
    @FunctionalInterface
    public interface Preserver {
        ItemStack apply(ItemStack stack, boolean sealing);
    }

    private LargeVesselDevice() {
    }

    public static boolean covers(String blockId) {
        return blockId.equals(ID) || blockId.startsWith(ID + "/");
    }

    public static DeviceKind<LargeVesselState> kind(Services services) {
        return DeviceKind.builder(ID, LargeVesselState.CODEC, LargeVesselState.FRESH)
            .slots(LargeVesselRules.SLOTS)
            .filter((slot, stack) -> services.fits().test(stack))
            .removal(DeviceKind.RemoveBehaviour.SAVE)
            .handler(new Handler(services))
            .build();
    }

    /**
     * The seal change of {@code change} on {@code record}: the new state, {@code preserved} applied or removed on every
     * stack for a seal or an opening ({@code onSeal}, {@code onUnseal}), the sound and the block state.
     */
    static DeviceRecord<LargeVesselState> apply(DeviceRecord<LargeVesselState> record, LargeVesselState.Change change,
            StackCodec stacks, Block block, Services services) {
        DeviceRecord<LargeVesselState> next = record.withState(change.state());
        if (change.seal() != LargeVesselState.Seal.NONE) {
            boolean sealing = change.seal() == LargeVesselState.Seal.SEAL;
            for (int i = 0; i < LargeVesselRules.SLOTS; i++) {
                ItemStack stack = stacks.decode(next.slot(i));
                if (!StackCodec.isEmpty(stack)) {
                    next = next.withSlot(i, stacks.encode(services.food().apply(stack.clone(), sealing)));
                }
            }
            services.effects().sound(block, sealing);
        }
        services.effects().show(block, change.state().sealed());
        return next;
    }

    /** TFC {@code LargeVesselBlock.toggleSeal}. */
    static DeviceRecord<LargeVesselState> toggleSeal(DeviceRecord<LargeVesselState> record, Block block, StackCodec stacks,
            Services services) {
        return apply(record, record.state().toggleSeal(), stacks, block, services);
    }

    private record Handler(Services services) implements DeviceHandler<LargeVesselState> {
        /** TFC {@code useItemOn}: sneaking toggles the seal (with any item), else the menu opens. */
        @Override
        public DeviceResult<LargeVesselState> useWithItem(DeviceContext<LargeVesselState> ctx) {
            Player player = ctx.player();
            if (player == null || ctx.hand() == null) {
                return DeviceResult.pass();
            }
            if (player.isSneaking()) {
                return DeviceResult.consume(toggleSeal(ctx.record(), ctx.block(), ctx.stacks(), services));
            }
            services.open().accept(player, ctx.block());
            return DeviceResult.consume();
        }

        /**
         * TFC {@code getStateForPlacement}: {@code POWERED} follows the signal at the position, a vessel placed from an
         * item that kept its contents is sealed (the restored record's seal; its block state is written here).
         */
        @Override
        public DeviceResult<LargeVesselState> placed(DeviceContext<LargeVesselState> ctx) {
            LargeVesselState state = ctx.record().state();
            LargeVesselState placed = LargeVesselState.placed(state.sealed(), services.signal().test(ctx.block()));
            services.effects().show(ctx.block(), placed.sealed());
            return placed.equals(state) ? DeviceResult.pass() : DeviceResult.update(ctx.record().withState(placed));
        }

        /** TFC {@code neighborChanged}: a signal change seals or opens the vessel, when the redstone seal is on. */
        @Override
        public DeviceResult<LargeVesselState> neighbourChanged(DeviceContext<LargeVesselState> ctx) {
            if (!services.redstoneSeal().getAsBoolean()) {
                return DeviceResult.pass();
            }
            LargeVesselState.Change change = ctx.record().state().neighbourSignal(services.signal().test(ctx.block()));
            if (change == null) {
                return DeviceResult.pass();
            }
            return DeviceResult.update(apply(ctx.record(), change, ctx.stacks(), ctx.block(), services));
        }
    }

    /** The block state of {@code state} with {@code facing}. */
    public static BlockStateRef state(String blockId, String facing, LargeVesselState state) {
        return BlockStateRef.of(blockId).with(FACING, facing).with(SEALED, Boolean.toString(state.sealed()));
    }
}
