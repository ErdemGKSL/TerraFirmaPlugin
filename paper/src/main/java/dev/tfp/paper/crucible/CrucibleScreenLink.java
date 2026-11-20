/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/container/CrucibleContainer.java @ c3b4d9536 (the ten slots)
 */
package dev.tfp.paper.crucible;

import dev.tfp.core.crucible.CrucibleData;
import dev.tfp.core.device.DeviceRecord;
import dev.tfp.paper.crucible.gui.CrucibleLink;
import dev.tfp.paper.crucible.gui.CrucibleSnapshot;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.device.StackCodec;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;

/**
 * PAR-METAL-12: the {@link CrucibleLink} of a placed crucible, over its device record. Every call loads the record through
 * {@link DeviceService#edit} (catch-up first, so the screen shows the melt as it is now), changes it and stores it, then
 * answers the crucible as stored.
 *
 * <p>Threads: the screen calls on the viewer's thread; the record lives on the block's region. When this thread does not
 * own the block every call answers empty: the screen gives the viewer back what it held since the last commit and closes.
 */
public final class CrucibleScreenLink implements CrucibleLink {
    /** The screen key of a crucible: its block position. */
    public record Key(UUID world, int x, int y, int z) {
        public static Key of(Block block) {
            return new Key(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
        }
    }

    /** What a link needs. */
    public record Env(DeviceService devices, CrucibleDevice device, Scheduler scheduler) {
        public Env {
            Objects.requireNonNull(devices, "devices");
            Objects.requireNonNull(device, "device");
            Objects.requireNonNull(scheduler, "scheduler");
        }

        DeviceKind<CrucibleData> kind() {
            return device.kind();
        }

        StackCodec stacks() {
            return devices.stacks();
        }
    }

    private final Env env;
    private final Block block;

    public CrucibleScreenLink(Env env, Block block) {
        this.env = Objects.requireNonNull(env, "env");
        this.block = Objects.requireNonNull(block, "block");
    }

    public Key key() {
        return Key.of(block);
    }

    private boolean owned() {
        return env.scheduler().isOwner(block.getWorld(), block.getX() >> 4, block.getZ() >> 4);
    }

    @Override
    public Optional<CrucibleSnapshot> snapshot() {
        if (!owned()) {
            return Optional.empty();
        }
        return env.devices().edit(block, env.kind(), (record, now) -> record).map(this::snapshotOf);
    }

    @Override
    public Optional<CrucibleSnapshot> commit(List<ItemStack> slots) {
        if (slots.size() != CrucibleDevice.SLOTS) {
            throw new IllegalArgumentException(slots.size() + " slots");
        }
        if (!owned()) {
            return Optional.empty();
        }
        return env.devices().edit(block, env.kind(), (record, now) -> withSlots(record, slots, env.stacks()))
            .map(this::snapshotOf);
    }

    @Override
    public boolean isItemValid(int tfcSlot, ItemStack stack) {
        return env.device().isItemValid(tfcSlot, stack);
    }

    /** The ten slots stored. */
    static DeviceRecord<CrucibleData> withSlots(DeviceRecord<CrucibleData> record, List<ItemStack> slots, StackCodec stacks) {
        for (int i = 0; i < CrucibleDevice.SLOTS; i++) {
            record = record.withSlot(i, stacks.encode(slots.get(i)));
        }
        return record;
    }

    CrucibleSnapshot snapshotOf(DeviceRecord<CrucibleData> record) {
        StackCodec stacks = env.stacks();
        List<ItemStack> slots = new ArrayList<>(CrucibleDevice.SLOTS);
        for (int i = 0; i < CrucibleDevice.SLOTS; i++) {
            slots.add(stacks.decode(record.slot(i)));
        }
        CrucibleData d = record.state();
        return new CrucibleSnapshot(d.degrees(), d.alloy(), env.device().services().capacity(), slots);
    }
}
