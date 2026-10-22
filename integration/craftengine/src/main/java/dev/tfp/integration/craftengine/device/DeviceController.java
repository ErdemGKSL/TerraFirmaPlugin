/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.integration.craftengine.device;

import dev.tfp.core.persist.DataMap;
import dev.tfp.core.persist.VersionedCodec;
import dev.tfp.integration.craftengine.TfpBlockEntityController;
import dev.tfp.paper.device.DeviceBridge;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.momirealms.craftengine.core.block.entity.BlockEntity;
import org.bukkit.World;

/**
 * The block-entity controller of every {@code tfp:device} block (PAR-INFRA-02). It stores the device's record map
 * opaquely under {@code tfp:device}; the typed record (kind state, slots, lastUpdateTick) is decoded by the device
 * kind on the Paper side ({@code DeviceKind.codec()}), so this controller holds no kind or behaviour reference and
 * survives {@code /ce reload} and kind changes.
 *
 * <pre>
 * tfp:device = { v: 1, d: { ...the DeviceRecord layout of dev.tfp.core.device.DeviceRecordCodec... } }
 * </pre>
 *
 * <p>{@link #onRemove} (region thread; the block was broken, exploded or replaced by another block) hands the last
 * record to the device router, which drops the inventory. Unloading is not removal and does nothing. No ticker.
 */
public final class DeviceController extends TfpBlockEntityController<DataMap> {
    /** Our key's path: the data is stored as {@code tfp:device}. */
    public static final String NAME = "device";

    private static final Logger LOG = Logger.getLogger("TerraFirmaPlugin");

    /** The opaque envelope; version 1: the record map under {@code d}. */
    static final VersionedCodec<DataMap> CODEC = VersionedCodec.<DataMap>builder("tfp:device", 1)
            .build((map, out) -> out.putMap("d", map.copy()), in -> in.requireMap("d").copy());

    /** @param blockEntity CraftEngine's block entity ({@code null} only in unit tests) */
    public DeviceController(BlockEntity blockEntity) {
        super(blockEntity, NAME, CODEC, new DataMap());
    }

    /** A copy of the stored record map; empty when never written. Any thread. */
    public DataMap read() {
        return data().copy();
    }

    /** Replaces the record map (a copy is kept) and marks the chunk for saving. Region thread. */
    public void write(DataMap map) {
        setData(map.copy());
    }

    @Override
    public void onRemove() {
        BlockEntity be = blockEntity;
        if (be == null || be.world == null) {
            return;
        }
        String blockId = be.blockState.owner().value().id().toString();
        if (hasUnreadableData()) {
            LOG.warning("Device " + blockId + " at " + be.pos + " was removed with unreadable data; nothing dropped");
            return;
        }
        try {
            World world = (World) be.world.world().platformWorld();
            DeviceBridge.router().removed(world, be.pos.x(), be.pos.y(), be.pos.z(), blockId, read());
        } catch (RuntimeException e) {
            LOG.log(Level.SEVERE, "Device " + blockId + " at " + be.pos + ": removal routing failed", e);
        }
    }
}
