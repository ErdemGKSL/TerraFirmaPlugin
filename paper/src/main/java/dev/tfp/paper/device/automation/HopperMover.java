/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/capabilities/PartialItemHandler.java @ c3b4d9536 (insertItem, extractItem),
 *       src/main/java/net/dries007/tfc/common/capabilities/InventoryItemHandler.java @ c3b4d9536 (onContentsChanged)
 * NeoForge VanillaInventoryCodeHooks (insertHook, extractHook) for the hopper's order: one item per move, the hopper's
 * slots in order on insertion, the device's slots in order on extraction.
 */
package dev.tfp.paper.device.automation;

import dev.tfp.core.device.DeviceRecord;
import dev.tfp.core.device.automation.Face;
import dev.tfp.core.device.automation.SideTable;
import dev.tfp.paper.device.DeviceInventory;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.device.StackCodec;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.bukkit.block.Block;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * PAR-INFRA-60: one hopper move into or out of a device, as a single {@link DeviceService#edit} (load, catch up, decide
 * on the record, store), then the hopper's own inventory. The decision is pure on the record and the hopper's copied
 * stacks; one record is small, so it runs inline on the region thread like every other device access.
 *
 * <p>Threads: the region thread that owns both the hopper and the device (checked by the listener).
 */
public final class HopperMover {
    private final DeviceService devices;

    public HopperMover(DeviceService devices) {
        this.devices = Objects.requireNonNull(devices, "devices");
    }

    /**
     * NeoForge {@code insertHook}: for each non-empty hopper slot in order, one item into the first of the face's insert
     * slots that takes it all.
     *
     * @return true when an item moved
     */
    public boolean push(Block device, DeviceAutomation.Spec spec, SideTable table, Face face, Inventory hopper) {
        List<Integer> slots = table.insertSlots(face);
        if (slots.isEmpty()) {
            return false;
        }
        Optional<DeviceKind<?>> kind = devices.kinds().find(spec.kindId());
        return kind.isPresent() && push(device, kind.get(), spec, slots, hopper);
    }

    private <S> boolean push(Block device, DeviceKind<S> kind, DeviceAutomation.Spec spec, List<Integer> slots,
            Inventory hopper) {
        int[] taken = {-1};
        spec.screens().edit(device);
        Optional<DeviceRecord<S>> stored = devices.edit(device, kind, (record, now) -> {
            if (spec.locked().test(record.state())) {
                return record;
            }
            DeviceInventory<S> inv = new DeviceInventory<>(kind, record, devices.stacks());
            for (int h = 0; h < hopper.getSize(); h++) {
                ItemStack source = hopper.getItem(h);
                if (StackCodec.isEmpty(source)) {
                    continue;
                }
                ItemStack one = source.clone();
                one.setAmount(1);
                for (int slot : slots) {
                    if (slot >= kind.slots()) {
                        continue;
                    }
                    DeviceInventory.Insert<S> r = inv.insert(slot, one);
                    if (StackCodec.isEmpty(r.remainder())) {
                        taken[0] = h;
                        return fixed(r.inventory().record(), spec, now, device);
                    }
                }
            }
            return record;
        });
        if (stored.isPresent() && taken[0] >= 0) {
            spec.screens().reload(device);
        }
        if (stored.isEmpty() || taken[0] < 0) {
            return false;
        }
        ItemStack source = hopper.getItem(taken[0]);
        if (source.getAmount() <= 1) {
            hopper.setItem(taken[0], null);
        } else {
            source.setAmount(source.getAmount() - 1);
            hopper.setItem(taken[0], source);
        }
        return true;
    }

    /**
     * NeoForge {@code extractHook}: the first of the face's extract slots whose one item fits a hopper slot (empty, or
     * the same item below its stack size) goes there.
     *
     * @return true when an item moved
     */
    public boolean pull(Block device, DeviceAutomation.Spec spec, SideTable table, Face face, Inventory hopper) {
        List<Integer> slots = table.extractSlots(face);
        if (slots.isEmpty()) {
            return false;
        }
        Optional<DeviceKind<?>> kind = devices.kinds().find(spec.kindId());
        return kind.isPresent() && pull(device, kind.get(), spec, slots, hopper);
    }

    private <S> boolean pull(Block device, DeviceKind<S> kind, DeviceAutomation.Spec spec, List<Integer> slots,
            Inventory hopper) {
        int[] target = {-1};
        ItemStack[] moved = {null};
        spec.screens().edit(device);
        Optional<DeviceRecord<S>> stored = devices.edit(device, kind, (record, now) -> {
            if (spec.locked().test(record.state())) {
                return record;
            }
            DeviceInventory<S> inv = new DeviceInventory<>(kind, record, devices.stacks());
            for (int slot : slots) {
                if (slot >= kind.slots()) {
                    continue;
                }
                DeviceInventory.Extract<S> r = inv.extract(slot, 1);
                if (StackCodec.isEmpty(r.extracted())) {
                    continue;
                }
                int j = room(hopper, r.extracted());
                if (j >= 0) {
                    target[0] = j;
                    moved[0] = r.extracted();
                    return fixed(r.inventory().record(), spec, now, device);
                }
            }
            return record;
        });
        if (stored.isPresent() && target[0] >= 0) {
            spec.screens().reload(device);
        }
        if (stored.isEmpty() || target[0] < 0) {
            return false;
        }
        ItemStack dest = hopper.getItem(target[0]);
        if (StackCodec.isEmpty(dest)) {
            hopper.setItem(target[0], moved[0]);
        } else {
            dest.setAmount(dest.getAmount() + 1);
            hopper.setItem(target[0], dest);
        }
        return true;
    }

    /** The first hopper slot that takes one {@code item}, or -1. */
    static int room(Inventory hopper, ItemStack item) {
        for (int j = 0; j < hopper.getSize(); j++) {
            ItemStack dest = hopper.getItem(j);
            if (StackCodec.isEmpty(dest)) {
                return j;
            }
            if (dest.isSimilar(item) && dest.getAmount() < dest.getMaxStackSize()
                    && dest.getAmount() < hopper.getMaxStackSize()) {
                return j;
            }
        }
        return -1;
    }

    /**
     * The slot fix, then the device's world-dependent settle (PAR-INFRA-66: the barrel's fluid IO slots at once, TFC does
     * it within 5 ticks of the insert).
     */
    @SuppressWarnings("unchecked")
    private static <S> DeviceRecord<S> fixed(DeviceRecord<S> record, DeviceAutomation.Spec spec, long now, Block device) {
        DeviceRecord<S> slots = record.withSlots(spec.afterMove().apply(record.slots()));
        return (DeviceRecord<S>) spec.settle().apply(slots, now, device);
    }
}
