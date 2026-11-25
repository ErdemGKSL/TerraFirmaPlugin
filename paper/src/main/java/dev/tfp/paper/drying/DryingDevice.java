/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/DryingBricksBlock.java @ c3b4d9536 (useItemOn, getStateForPlacement,
 *       canBeReplaced, setPlacedBy, tick),
 *       src/main/java/net/dries007/tfc/common/blockentities/TickCounterBlockEntity.java @ c3b4d9536
 */
package dev.tfp.paper.drying;

import dev.tfp.core.device.DeviceRecord;
import dev.tfp.core.drying.DryingState;
import dev.tfp.paper.device.DeviceContext;
import dev.tfp.paper.device.DeviceHandler;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceResult;
import java.util.Objects;

/**
 * The drying bricks as a device kind: {@value #ID} covers {@code tfp:drying_bricks/<soil>}. The record holds TFC's tick
 * counter, the dried tick, the rain clock and the brick count ({@link DryingState}); the block state ({@code count},
 * {@code dried}) is what the pack draws. Nothing ticks: the {@link DryingBricks} service settles a pile on a click, a
 * neighbour change, chunk load and the {@link DryingClock}. No slots: the bricks drop from the count when the pile goes.
 */
public final class DryingDevice {
    /** The kind id; the block ids are {@code tfp:drying_bricks/<soil>}. */
    public static final String ID = "tfp:drying_bricks";

    private DryingDevice() {
    }

    public static DeviceKind<DryingState> kind(DryingBricks bricks) {
        Objects.requireNonNull(bricks, "bricks");
        return DeviceKind.builder(ID, DryingState.CODEC, DryingState.FRESH)
            .removal(DeviceKind.RemoveBehaviour.NOOP)
            .handler(new DeviceHandler<>() {
                /** TFC {@code setPlacedBy}: {@code TickCounterBlockEntity.reset}. */
                @Override
                public DeviceResult<DryingState> placed(DeviceContext<DryingState> ctx) {
                    return DeviceResult.update(bricks.placed(ctx.block(), ctx.record()));
                }

                /** TFC {@code BottomSupportedDeviceBlock.updateShape}: a pile with nothing sturdy below goes. */
                @Override
                public DeviceResult<DryingState> neighbourChanged(DeviceContext<DryingState> ctx) {
                    bricks.later(ctx.block());
                    return DeviceResult.pass();
                }

                /** TFC loot table: the bricks drop (the wet block item, or the dry mud bricks once dry). */
                @Override
                public void removed(DeviceContext<DryingState> ctx) {
                    bricks.removed(ctx.block(), ctx.state().id(), ctx.record());
                }
            })
            .build();
    }

    static DeviceRecord<DryingState> with(DeviceRecord<DryingState> record, DryingState state) {
        return record.withState(state);
    }
}
