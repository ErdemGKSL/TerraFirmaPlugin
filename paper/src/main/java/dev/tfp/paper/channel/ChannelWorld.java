/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/ChannelFlow.java @ c3b4d9536 (findAdjacent: what a cell holds),
 *       src/main/java/net/dries007/tfc/common/blockentities/ChannelBlockEntity.java @ c3b4d9536 (setLinkProperties: the
 *       WITH_METAL state follows the flow)
 */
package dev.tfp.paper.channel;

import dev.tfp.core.channel.ChannelData;
import dev.tfp.core.channel.FlowFacts;
import dev.tfp.core.channel.MoldTableData;
import dev.tfp.core.crucible.ChannelFlow.Cell;
import dev.tfp.core.device.DeviceRecord;
import dev.tfp.core.world.BlockPos;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.BlockVisuals;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * The channel code's view of the world (PAR-METAL-13): what a block is, its device record, and the one state property the
 * flow writes ({@code with_metal}, {@code triggered}). Every method runs on the owner thread of the block it names; the
 * {@link #facts} reader throws {@link Unowned} for a cell another region owns (Folia), which callers treat as "unknown".
 */
final class ChannelWorld {
    static final String CHANNEL = "tfp:channel";
    static final String TABLE = "tfp:mold_table";
    static final String CRUCIBLE = "tfp:crucible";

    /** A cell in a chunk this thread does not own. */
    static final class Unowned extends RuntimeException {
        Unowned(BlockPos pos) {
            super("not owned: " + pos, null, false, false);
        }
    }

    private final CustomBlocks blocks;
    private final DeviceService devices;
    private final Scheduler scheduler;
    private final Supplier<BlockVisuals> visuals;
    private volatile DeviceKind<ChannelData> channelKind;
    private volatile DeviceKind<MoldTableData> tableKind;

    ChannelWorld(CustomBlocks blocks, DeviceService devices, Scheduler scheduler, Supplier<BlockVisuals> visuals) {
        this.blocks = Objects.requireNonNull(blocks, "blocks");
        this.devices = Objects.requireNonNull(devices, "devices");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.visuals = Objects.requireNonNull(visuals, "visuals");
    }

    /** The kinds are built from the devices, which need this; bound once before use. */
    void bind(DeviceKind<ChannelData> channel, DeviceKind<MoldTableData> table) {
        this.channelKind = Objects.requireNonNull(channel, "channel");
        this.tableKind = Objects.requireNonNull(table, "table");
    }

    DeviceService devices() {
        return devices;
    }

    CustomBlocks blocks() {
        return blocks;
    }

    Scheduler scheduler() {
        return scheduler;
    }

    DeviceKind<ChannelData> channelKind() {
        return channelKind;
    }

    DeviceKind<MoldTableData> tableKind() {
        return tableKind;
    }

    boolean owns(World world, BlockPos pos) {
        return scheduler.isOwner(world, pos.chunkX(), pos.chunkZ());
    }

    boolean owns(Block block) {
        return scheduler.isOwner(block.getWorld(), block.getX() >> 4, block.getZ() >> 4);
    }

    Block at(World world, BlockPos pos) {
        return world.getBlockAt(pos.x(), pos.y(), pos.z());
    }

    static BlockPos pos(Block block) {
        return new BlockPos(block.getX(), block.getY(), block.getZ());
    }

    Optional<BlockStateRef> state(Block block) {
        return blocks.stateAt(block);
    }

    boolean is(Block block, String id) {
        return blocks.stateAt(block).map(s -> s.id().equals(id)).orElse(false);
    }

    /** What the flow search sees in {@code block}: {@code SOLID} for an unloaded or foreign cell. */
    Cell cell(Block block) {
        if (!owns(block) || !block.getWorld().isChunkLoaded(block.getX() >> 4, block.getZ() >> 4)) {
            return Cell.SOLID;
        }
        Optional<BlockStateRef> st = blocks.stateAt(block);
        if (st.isPresent()) {
            String id = st.get().id();
            if (id.equals(CHANNEL)) {
                return "true".equals(st.get().properties().get("with_metal")) ? Cell.LINKED_CHANNEL : Cell.CHANNEL;
            }
            if (id.equals(TABLE)) {
                return table(block).map(MoldTableData::hasSource).orElse(true) ? Cell.LINKED_MOLD : Cell.MOLD;
            }
        }
        return block.getType().isAir() ? Cell.AIR : Cell.SOLID;
    }

    /** The channel state at {@code block}, caught up. Owner thread. */
    Optional<ChannelData> channel(Block block) {
        if (!is(block, CHANNEL)) {
            return Optional.empty();
        }
        return devices.edit(block, channelKind, (record, now) -> record).map(DeviceRecord::state);
    }

    Optional<DeviceRecord<MoldTableData>> tableRecord(Block block) {
        if (!is(block, TABLE)) {
            return Optional.empty();
        }
        return devices.edit(block, tableKind, (record, now) -> record);
    }

    Optional<MoldTableData> table(Block block) {
        return tableRecord(block).map(DeviceRecord::state);
    }

    /** Live facts for {@link dev.tfp.core.channel.FlowCheck}: the world of {@code world}, owner threads only. */
    FlowFacts facts(World world) {
        return new FlowFacts() {
            @Override
            public ChannelData channel(BlockPos p) {
                Block b = checked(world, p);
                return ChannelWorld.this.channel(b).orElse(null);
            }

            @Override
            public boolean isAir(BlockPos p) {
                return checked(world, p).getType().isAir();
            }

            @Override
            public boolean isCrucible(BlockPos p) {
                return is(checked(world, p), CRUCIBLE);
            }
        };
    }

    private Block checked(World world, BlockPos p) {
        if (!owns(world, p)) {
            throw new Unowned(p);
        }
        return at(world, p);
    }

    /**
     * Stores {@code data} as the channel's record and sets its {@code with_metal} property to the flow. Owner thread of
     * {@code block}; ignored when the block is no longer a channel.
     */
    void writeChannel(Block block, ChannelData data) {
        if (!is(block, CHANNEL)) {
            return;
        }
        devices.edit(block, channelKind, (record, now) -> record.withState(data));
        setProperty(block, "with_metal", data.hasFlow());
    }

    /** Sets one boolean property of the custom state at {@code block} without physics (the grill's {@code show}). */
    void setProperty(Block block, String property, boolean value) {
        Optional<BlockStateRef> st = blocks.stateAt(block);
        if (st.isEmpty() || !st.get().properties().containsKey(property)) {
            return;
        }
        String text = Boolean.toString(value);
        if (text.equals(st.get().properties().get(property))) {
            return;
        }
        BlockStateRef next = st.get().with(property, text);
        visuals.get().blockData(next).ifPresent(d -> block.setBlockData(d, false));
    }

    /** Runs {@code task} on the owner of {@code block}: now when this thread owns it, otherwise queued there. */
    void onOwner(Block block, Runnable task) {
        if (owns(block)) {
            task.run();
        } else {
            scheduler.region(block.getLocation(), task);
        }
    }

    /** Queues {@code task} on the owner of {@code block}, never inline. */
    void later(Block block, Runnable task) {
        scheduler.region(block.getLocation(), task);
    }
}
