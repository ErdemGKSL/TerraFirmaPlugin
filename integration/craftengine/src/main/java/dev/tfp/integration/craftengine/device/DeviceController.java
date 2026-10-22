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
import net.momirealms.craftengine.bukkit.util.ItemStackUtils;
import net.momirealms.craftengine.core.block.entity.BlockEntity;
import net.momirealms.craftengine.core.item.Item;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;

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
 * record to the device router, which drops the inventory (or keeps it for the dropped item, {@code SAVE}).
 * {@link #onLoad} stamps a device that has no record yet; {@link #loadCustomDataFromItem} restores a record saved on
 * the placed item (PAR-INFRA-18).
 * Unloading is not removal and does nothing. No ticker.
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

    /**
     * CraftEngine added the block entity to a loaded chunk: on creation (placement by anyone, a command, the API, first
     * access of a generated device) and when its chunk loads. A device without a stored record gets a fresh one with
     * {@code lastUpdateTick} stamped now, so its time starts with its block entity (TFC's block entity ticks from
     * creation). Region thread.
     */
    @Override
    public void onLoad() {
        BlockEntity be = blockEntity;
        if (be == null) {
            return;
        }
        try {
            stampIfFresh(be.blockState.owner().value().id().toString());
        } catch (RuntimeException e) {
            LOG.log(Level.SEVERE, "Device at " + be.pos + ": stamping its creation failed", e);
        }
    }

    /** Stores the router's stamped fresh record when nothing (readable or not) is stored yet. */
    void stampIfFresh(String blockId) {
        if (hasUnreadableData() || data().size() != 0) {
            return;
        }
        DeviceBridge.router().created(blockId).ifPresent(this::write);
    }

    /**
     * CraftEngine's {@code BlockItemBehavior}: a player placed this device from a CraftEngine block item, after
     * {@code onLoad} stamped it and after the placement events (TFC {@code applyImplicitComponents}). A record saved
     * on the item ({@code SAVE} removal) replaces the fresh one. Region thread.
     */
    @Override
    public void loadCustomDataFromItem(Item item) {
        BlockEntity be = blockEntity;
        if (be == null || item == null) {
            return;
        }
        try {
            restoreFrom(be.blockState.owner().value().id().toString(), ItemStackUtils.getBukkitStack(item));
        } catch (RuntimeException e) {
            LOG.log(Level.SEVERE, "Device at " + be.pos + ": restoring the record saved on the placed item failed", e);
        }
    }

    /** Stores the router's restored record of {@code stack}, if it carries one. */
    void restoreFrom(String blockId, ItemStack stack) {
        if (hasUnreadableData()) {
            return;
        }
        DeviceBridge.router().fromItem(blockId, stack).ifPresent(this::write);
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
