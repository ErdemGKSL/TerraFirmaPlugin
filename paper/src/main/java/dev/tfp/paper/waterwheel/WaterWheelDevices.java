/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/rotation/WaterWheelBlock.java @ c3b4d9536 (getStateForPlacement),
 *       src/main/java/net/dries007/tfc/common/blockentities/rotation/WaterWheelBlockEntity.java @ c3b4d9536
 *       (onLoadAdditional ADD_SOURCE, onUnloadAdditional REMOVE)
 */
package dev.tfp.paper.waterwheel;

import dev.tfp.core.power.ShaftAxis;
import dev.tfp.core.waterwheel.WaterWheelSample;
import dev.tfp.paper.device.DeviceContext;
import dev.tfp.paper.device.DeviceHandler;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceResult;
import dev.tfp.paper.integration.BlockStateRef;
import java.util.Objects;
import java.util.Optional;
import org.bukkit.block.Block;

/**
 * The device kind of the water wheels: placing one starts its 40-tick sample (and lists it in the chunk index so a chunk load
 * starts it again); breaking it stops the sample and removes it from the network. Callbacks run on the block's region thread.
 */
public final class WaterWheelDevices {
    /** Writes the wheel's own new state on the next tick, if the block still holds {@code expected}. Region thread. */
    @FunctionalInterface
    public interface Writer {
        void later(Block block, BlockStateRef expected, BlockStateRef next);
    }

    private final WaterWheelService service;
    private final WaterWheelChunkIndex index;
    private final Writer writer;
    private final DeviceKind<WaterWheelSample> kind;

    public WaterWheelDevices(WaterWheelService service, WaterWheelChunkIndex index, Writer writer) {
        this.service = Objects.requireNonNull(service, "service");
        this.index = Objects.requireNonNull(index, "index");
        this.writer = Objects.requireNonNull(writer, "writer");
        this.kind = DeviceKind.builder(WaterWheelBlocks.KIND, WaterWheelBlocks.CODEC, WaterWheelSample.FRESH)
            .removal(DeviceKind.RemoveBehaviour.NOOP) // TFC: no inventory
            .handler(new Handler())
            .build();
    }

    public DeviceKind<WaterWheelSample> kind() {
        return kind;
    }

    private final class Handler implements DeviceHandler<WaterWheelSample> {
        @Override
        public DeviceResult<WaterWheelSample> placed(DeviceContext<WaterWheelSample> ctx) {
            index.track(ctx.block());
            if (ctx.player() != null) {
                // TFC getStateForPlacement: AXIS = the axis of the player's horizontal direction
                ShaftAxis axis = WaterWheelBlocks.placementAxis(WaterWheelBlocks.horizontal(ctx.player().getLocation().getYaw()));
                Optional<ShaftAxis> now = WaterWheelBlocks.axis(ctx.state());
                if (now.isEmpty() || now.get() != axis) {
                    writer.later(ctx.block(), ctx.state(), ctx.state().with(WaterWheelBlocks.AXIS, WaterWheelBlocks.name(axis)));
                }
            }
            service.start(ctx.block());
            return DeviceResult.pass();
        }

        @Override
        public void removed(DeviceContext<WaterWheelSample> ctx) {
            index.untrack(ctx.block());
            service.stop(ctx.block().getWorld().getUID(), WaterWheelService.pos(ctx.block()));
        }
    }
}
