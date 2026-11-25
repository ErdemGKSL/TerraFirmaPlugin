/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/ChannelBlock.java @ c3b4d9536 (useItemOn, neighborChanged,
 *       onDestroyedByPlayer, onBlockExploded),
 *       src/main/java/net/dries007/tfc/common/blockentities/ChannelBlockEntity.java @ c3b4d9536 (the saved state)
 */
package dev.tfp.paper.channel;

import dev.tfp.core.channel.ChannelData;
import dev.tfp.paper.device.DeviceContext;
import dev.tfp.paper.device.DeviceHandler;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceResult;
import java.util.Objects;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;

/**
 * The casting channel as a device kind (PAR-METAL-13): no slots, the flow link as its state. A click or a rising redstone
 * signal starts a pour; a removed channel tells the flow upstream. The handler only queues: the work runs on the region
 * thread a tick later ({@link PourService}).
 */
final class ChannelDevice {
    static final String ID = ChannelWorld.CHANNEL;

    private final ChannelWorld world;
    private final PourService pour;
    private final DeviceKind<ChannelData> kind;

    ChannelDevice(ChannelWorld world, PourService pour) {
        this.world = Objects.requireNonNull(world, "world");
        this.pour = Objects.requireNonNull(pour, "pour");
        this.kind = DeviceKind.builder(ID, ChannelData.CODEC, ChannelData.IDLE)
            .slots(0)
            .removal(DeviceKind.RemoveBehaviour.NOOP)
            .handler(new Handler())
            .build();
    }

    DeviceKind<ChannelData> kind() {
        return kind;
    }

    /** TFC {@code level.hasNeighborSignal(pos) || level.hasNeighborSignal(pos.above())}. */
    static boolean powered(Block block) {
        return block.isBlockIndirectlyPowered() || block.getRelative(BlockFace.UP).isBlockIndirectlyPowered();
    }

    private final class Handler implements DeviceHandler<ChannelData> {
        /** TFC {@code useItemOn}: a server click activates the channel, whatever the hand holds. */
        @Override
        public DeviceResult<ChannelData> useWithItem(DeviceContext<ChannelData> ctx) {
            Player player = ctx.player();
            if (player == null) {
                return DeviceResult.pass();
            }
            Block block = ctx.block();
            world.later(block, () -> pour.activate(block));
            return DeviceResult.consume();
        }

        /** TFC {@code neighborChanged}: a signal that was off starts a pour once; the {@code triggered} state follows the signal. */
        @Override
        public DeviceResult<ChannelData> neighbourChanged(DeviceContext<ChannelData> ctx) {
            Block block = ctx.block();
            boolean located = powered(block);
            boolean triggered = "true".equals(ctx.state().properties().get("triggered"));
            if (located == triggered) {
                return DeviceResult.pass();
            }
            world.later(block, () -> {
                if (!world.is(block, ChannelWorld.CHANNEL)) {
                    return;
                }
                if (located) {
                    pour.activate(block);
                }
                world.setProperty(block, "triggered", located);
            });
            return DeviceResult.pass();
        }

        @Override
        public void removed(DeviceContext<ChannelData> ctx) {
            ChannelData data = ctx.deviceState();
            Block block = ctx.block();
            world.later(block, () -> pour.channelRemoved(block, data));
        }
    }
}
