/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/FireboxBlockEntity.java @ c3b4d9536 (setAndUpdateSlots ->
 *       cascadeFuelSlots; isItemValid; getSlotStackLimit)
 */
package dev.tfp.paper.firebox;

import dev.tfp.core.device.DeviceRecord;
import dev.tfp.core.firebox.FireboxData;
import dev.tfp.core.firebox.FireboxScreenLayout;
import dev.tfp.core.firebox.FireboxSim;
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
 * PAR-HEAT-12: the screen's view of one placed firebox, over its device record ({@link DeviceService#edit}). Every call
 * answers the firebox as stored, or empty when it is gone or this thread does not own its region (Folia: the screen then
 * gives the viewer back what it held since the last commit and closes).
 */
public final class FireboxLink {
    /** The screen key of a firebox: its block position. */
    public record Key(UUID world, int x, int y, int z) {
        public static Key of(Block block) {
            return new Key(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
        }
    }

    /**
     * What the screen shows: the firebox's data, the status at {@code now} and the sixteen slots (copies, TFC order).
     */
    public record Snapshot(FireboxData data, long now, List<ItemStack> slots) {
        public Snapshot {
            slots = List.copyOf(slots);
        }

        public FireboxScreenLayout.Status status() {
            return FireboxScreenLayout.status(data, now);
        }

        /** Whether two slot lists hold equal stacks (empty and null alike). */
        public static boolean sameSlots(List<ItemStack> a, List<ItemStack> b) {
            if (a.size() != b.size()) {
                return false;
            }
            for (int i = 0; i < a.size(); i++) {
                ItemStack x = a.get(i);
                ItemStack y = b.get(i);
                boolean ex = StackCodec.isEmpty(x);
                boolean ey = StackCodec.isEmpty(y);
                if (ex != ey || (!ex && !x.equals(y))) {
                    return false;
                }
            }
            return true;
        }

        /** Same slots and the same shown data (temperature frame, status and its numbers). */
        public boolean sameView(Snapshot o) {
            return o != null && sameSlots(slots, o.slots) && data.equals(o.data) && status() == o.status()
                && FireboxSim.timeLeft(data, now) / 1000 == FireboxSim.timeLeft(o.data, o.now) / 1000;
        }
    }

    private final DeviceService devices;
    private final FireboxDevice device;
    private final Scheduler scheduler;
    private final Block block;

    public FireboxLink(DeviceService devices, FireboxDevice device, Scheduler scheduler, Block block) {
        this.devices = Objects.requireNonNull(devices, "devices");
        this.device = Objects.requireNonNull(device, "device");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.block = Objects.requireNonNull(block, "block");
    }

    public Key key() {
        return Key.of(block);
    }

    private boolean owned() {
        return scheduler.isOwner(block.getWorld(), block.getX() >> 4, block.getZ() >> 4);
    }

    private Optional<Snapshot> edit(DeviceService.Edit<FireboxData> change) {
        if (!owned()) {
            return Optional.empty();
        }
        FireboxData[] seen = {null};
        long[] at = {0};
        Optional<DeviceRecord<FireboxData>> stored = devices.edit(block, device.kind(), (record, now) -> {
            seen[0] = record.state();
            at[0] = now;
            return change.apply(record, now);
        });
        device.pushHeat(block, seen[0]);
        return stored.map(r -> snapshotOf(r, at[0]));
    }

    Snapshot snapshotOf(DeviceRecord<FireboxData> record, long now) {
        StackCodec stacks = devices.stacks();
        List<ItemStack> slots = new ArrayList<>(FireboxSim.SLOTS);
        for (int i = 0; i < FireboxSim.SLOTS; i++) {
            slots.add(stacks.decode(record.slot(i)));
        }
        return new Snapshot(record.state(), now, slots);
    }

    public Optional<Snapshot> snapshot() {
        return edit((record, now) -> record);
    }

    /**
     * Stores the screen's sixteen slots, then TFC's {@code cascadeFuelSlots}. Stacks that are not fuel or hold more than
     * one item are refused: then, when they differ from the firebox's, the answer is empty so the screen hands the viewer's
     * copy back and closes.
     */
    public Optional<Snapshot> commit(List<ItemStack> slots) {
        if (slots.size() != FireboxSim.SLOTS) {
            throw new IllegalArgumentException(slots.size() + " slots");
        }
        StackCodec stacks = devices.stacks();
        boolean[] refused = {false};
        Optional<Snapshot> after = edit((record, now) -> {
            DeviceRecord<FireboxData> next = record;
            for (int i = 0; i < slots.size(); i++) {
                ItemStack s = slots.get(i);
                if (!StackCodec.isEmpty(s) && (!device.isItemValid(s) || s.getAmount() > FireboxSim.SLOT_LIMIT)) {
                    refused[0] = true;
                    return record;
                }
                next = next.withSlot(i, stacks.encode(s));
            }
            return FireboxDevice.cascade(next);
        });
        if (refused[0] && after.isPresent() && !Snapshot.sameSlots(after.get().slots(), slots)) {
            return Optional.empty();
        }
        return after;
    }
}
