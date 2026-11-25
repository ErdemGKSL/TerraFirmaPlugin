/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/SluiceBlockEntity.java @ c3b4d9536 (serverTick, getRepresentativeState,
 * getWaterInputPos, getWaterOutputPos, isItemValid),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/SluiceBlock.java @ c3b4d9536 (FACING, UPPER, getFluidOutputPos)
 */
package dev.tfp.paper.sluice;

import dev.tfp.core.device.DeviceRecord;
import dev.tfp.core.device.ItemBlob;
import dev.tfp.core.pan.SluiceRules;
import dev.tfp.core.sluice.SluiceState;
import dev.tfp.paper.device.DeviceContext;
import dev.tfp.paper.device.DeviceHandler;
import dev.tfp.paper.device.DeviceInventory;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceResult;
import dev.tfp.paper.device.StackCodec;
import dev.tfp.paper.integration.BlockStateRef;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import org.bukkit.block.BlockFace;
import org.bukkit.inventory.ItemStack;

/**
 * The sluice as a device kind (PAR-PROC-13): TFC's {@code SluiceBlock} + {@code SluiceBlockEntity} on the device framework.
 * Thirty-two soil slots of one item each, dropped with the block. Only the upper half holds the soil and runs (TFC ticks
 * the top block only); the lower half is a plain block of the same id.
 *
 * <p>Nothing ticks. The framework's catch-up hands the gap since the last access to {@link SluiceRules#advance} with the
 * stored {@code working} flag; the deposits it produced wait in {@link SluiceState#pending()} and every callback rolls
 * them into loot at the water output. Each callback also re-reads the water ({@link SluiceEffects#flow}), stores the new
 * flag and, while there is soil, asks for a wake-up after {@link #WAKE_TICKS} so a change of the water is noticed (no
 * block update reaches the sluice from the diagonal input and output).
 */
public final class SluiceDevice {
    /** The block family: one block {@code tfp:wood/sluice/<wood>} per TFC wood. */
    public static final String ID = "tfp:wood/sluice";
    public static final String UPPER = "upper";
    public static final String FACING = "facing";
    /** TFC {@code SluiceBlockEntity.MAX_SOIL}. */
    public static final int SLOTS = SluiceRules.MAX_SOIL;
    /** How often a sluice with soil looks at its water. TFC checks every {@link SluiceRules#GATHER_INTERVAL} ticks. */
    public static final long WAKE_TICKS = SluiceRules.GATHER_INTERVAL;

    /**
     * What the device reads from the rest of the plugin.
     *
     * @param deposit the deposit id of an item, empty when it is no deposit ({@code Deposit.get(stack)}); any thread
     * @param cycle   TFC {@code sluiceTicks}
     * @param codec   the slot codec the lazy catch-up decodes with (the framework's; {@link StackCodec#PAPER} in the server)
     */
    public record Services(Function<ItemStack, Optional<String>> deposit, int cycle, SluiceEffects effects,
            StackCodec codec) {
        public Services {
            Objects.requireNonNull(deposit, "deposit");
            Objects.requireNonNull(effects, "effects");
            Objects.requireNonNull(codec, "codec");
        }

        public Services(Function<ItemStack, Optional<String>> deposit, int cycle, SluiceEffects effects) {
            this(deposit, cycle, effects, StackCodec.PAPER);
        }
    }

    private SluiceDevice() {
    }

    public static DeviceKind<SluiceState> kind(Services services) {
        return DeviceKind.builder(ID, SluiceState.CODEC, SluiceState.FRESH.withRemaining(services.cycle()))
            .slots(SLOTS)
            .slotLimit(1) // TFC getSlotStackLimit
            .filter((slot, stack) -> services.deposit().apply(stack).isPresent()) // TFC isItemValid
            .advance((record, from, to) -> advance(record, services, to - from))
            .removal(DeviceKind.RemoveBehaviour.DROP)
            .handler(new Handler(services))
            .build();
    }

    /** True for the half that holds the data (TFC {@code UPPER}). */
    public static boolean isUpper(BlockStateRef state) {
        return "true".equals(state.properties().get(UPPER));
    }

    /** The horizontal direction the sluice slopes to, from {@code facing}; north when unknown. */
    public static BlockFace facing(BlockStateRef state) {
        return switch (state.properties().getOrDefault(FACING, "north")) {
            case "east" -> BlockFace.EAST;
            case "south" -> BlockFace.SOUTH;
            case "west" -> BlockFace.WEST;
            default -> BlockFace.NORTH;
        };
    }

    /** TFC {@code getWaterInputPos} as an offset from the upper block: one up, one against the facing. */
    public static int[] inputOffset(BlockFace facing) {
        BlockFace back = facing.getOppositeFace();
        return new int[] {back.getModX(), 1, back.getModZ()};
    }

    /** TFC {@code SluiceBlock.getFluidOutputPos}: two along the facing, one down. */
    public static int[] outputOffset(BlockFace facing) {
        return new int[] {facing.getModX() * 2, -1, facing.getModZ() * 2};
    }

    /**
     * TFC's gathering box ({@code AABB(-0.2, 0.5, -0.2, 1.2, 1.25, 1.2)} moved to the upper block): whether a point at
     * {@code (dx, dy, dz)} from the block's corner is inside.
     */
    public static boolean inGatherBox(double dx, double dy, double dz) {
        return dx >= -0.2 && dx <= 1.2 && dy >= 0.5 && dy <= 1.25 && dz >= -0.2 && dz <= 1.2;
    }

    /** The lazy gap: {@link SluiceRules#advance} over the slots' deposits; its products join the pending list. */
    static DeviceRecord<SluiceState> advance(DeviceRecord<SluiceState> record, Services services, long elapsed) {
        SluiceState state = record.state();
        if (!state.working() || elapsed <= 0) {
            return record;
        }
        List<String> ids = new ArrayList<>(SLOTS);
        for (int i = 0; i < SLOTS; i++) {
            ItemStack stack = services.codec().decode(record.slot(i));
            ids.add(StackCodec.isEmpty(stack) ? null : services.deposit().apply(stack).orElse("?"));
        }
        SluiceRules.Advanced done = SluiceRules.advance(new SluiceRules.Run(state.ticksRemaining(), ids),
            SluiceRules.State.BOTH, services.cycle(), elapsed);
        DeviceRecord<SluiceState> next = record;
        for (int i = 0; i < SLOTS; i++) {
            if (ids.get(i) != null && done.run().slots().get(i) == null) {
                next = next.withSlot(i, ItemBlob.EMPTY);
            }
        }
        List<String> produced = new ArrayList<>(done.produced());
        produced.removeIf("?"::equals); // an item whose deposit data vanished is used up and gives nothing
        return next.withState(state.withRemaining(done.run().ticksRemaining()).produced(produced));
    }

    /**
     * The common reaction of every callback on the upper half: roll the produced deposits at the output, re-read the water,
     * keep the wake-up while there is soil.
     */
    static DeviceRecord<SluiceState> refresh(DeviceContext<SluiceState> ctx, Services services) {
        DeviceRecord<SluiceState> record = ctx.record();
        SluiceState state = record.state();
        if (!state.pending().isEmpty()) {
            services.effects().output(ctx.block(), state.pending());
            services.effects().used(ctx.block());
            state = state.withPending(List.of());
        }
        SluiceRules.State flow = services.effects().flow(ctx.block());
        if (flow == SluiceRules.State.INPUT_ONLY || flow == SluiceRules.State.OUTPUT_ONLY) {
            services.effects().water(ctx.block(), flow);
        }
        state = state.withWorking(flow == SluiceRules.State.BOTH);
        record = record.withState(state);
        if (hasSoil(new DeviceInventory<>(ctx.kind(), record, ctx.stacks()))) {
            services.effects().wake(ctx.block(), WAKE_TICKS);
        }
        return record;
    }

    static boolean hasSoil(DeviceInventory<SluiceState> inv) {
        for (int i = 0; i < inv.size(); i++) {
            if (!StackCodec.isEmpty(inv.get(i))) {
                return true;
            }
        }
        return false;
    }

    private record Handler(Services services) implements DeviceHandler<SluiceState> {
        @Override
        public DeviceResult<SluiceState> neighbourChanged(DeviceContext<SluiceState> ctx) {
            return answer(ctx);
        }

        @Override
        public DeviceResult<SluiceState> placed(DeviceContext<SluiceState> ctx) {
            return answer(ctx);
        }

        private DeviceResult<SluiceState> answer(DeviceContext<SluiceState> ctx) {
            services.effects().verify(ctx.block(), ctx.state());
            if (!isUpper(ctx.state())) {
                return DeviceResult.pass();
            }
            DeviceRecord<SluiceState> next = refresh(ctx, services);
            return next.equals(ctx.record()) ? DeviceResult.pass() : DeviceResult.update(next);
        }
    }
}
