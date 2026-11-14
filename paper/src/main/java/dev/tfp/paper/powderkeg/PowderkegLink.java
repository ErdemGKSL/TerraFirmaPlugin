/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/container/PowderkegContainer.java @ c3b4d9536 (clicked: no slot edits while
 *       sealed; onButtonPress: toggleSeal)
 */
package dev.tfp.paper.powderkeg;

import dev.tfp.core.device.DeviceRecord;
import dev.tfp.core.powderkeg.PowderkegRules;
import dev.tfp.core.powderkeg.PowderkegState;
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
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * PAR-PROC-18: the screen's view of one placed keg, over its device record ({@link DeviceService#edit}). Every call
 * answers the keg as stored, or empty when it is gone or this thread does not own its region (Folia: the screen then
 * gives the viewer back what it held since the last commit and closes).
 */
public final class PowderkegLink {
    /** The screen key of a keg: its block position. */
    public record Key(UUID world, int x, int y, int z) {
        public static Key of(Block block) {
            return new Key(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
        }
    }

    /** What the screen shows: the seal and the twelve slots (copies, TFC order). */
    public record Snapshot(boolean sealed, List<ItemStack> slots) {
        public Snapshot {
            slots = List.copyOf(slots);
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
    }

    private final DeviceService devices;
    private final DeviceKind<PowderkegState> kind;
    private final PowderkegDevice.Services services;
    private final Scheduler scheduler;
    private final Block block;

    public PowderkegLink(DeviceService devices, DeviceKind<PowderkegState> kind, PowderkegDevice.Services services,
            Scheduler scheduler, Block block) {
        this.devices = Objects.requireNonNull(devices, "devices");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.services = Objects.requireNonNull(services, "services");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.block = Objects.requireNonNull(block, "block");
    }

    public Key key() {
        return Key.of(block);
    }

    private boolean owned() {
        return scheduler.isOwner(block.getWorld(), block.getX() >> 4, block.getZ() >> 4);
    }

    private Optional<Snapshot> edit(DeviceService.Edit<PowderkegState> change) {
        if (!owned()) {
            return Optional.empty();
        }
        return devices.edit(block, kind, change).map(this::snapshotOf);
    }

    Snapshot snapshotOf(DeviceRecord<PowderkegState> record) {
        StackCodec stacks = devices.stacks();
        List<ItemStack> slots = new ArrayList<>(PowderkegRules.SLOTS);
        for (int i = 0; i < PowderkegRules.SLOTS; i++) {
            slots.add(stacks.decode(record.slot(i)));
        }
        return new Snapshot(record.state().sealed(), slots);
    }

    public Optional<Snapshot> snapshot() {
        return edit((record, now) -> record);
    }

    /**
     * Stores the screen's twelve slots. A keg sealed meanwhile by another path refuses them (TFC
     * {@code PowderkegInventory.canModify}); then, when they differ from the keg's, the answer is empty so the screen
     * hands the viewer's copy back and closes. Stacks that are not powderkeg fuel are refused the same way.
     */
    public Optional<Snapshot> commit(List<ItemStack> slots) {
        if (slots.size() != PowderkegRules.SLOTS) {
            throw new IllegalArgumentException(slots.size() + " slots");
        }
        StackCodec stacks = devices.stacks();
        boolean[] refused = {false};
        Optional<Snapshot> after = edit((record, now) -> {
            if (!record.state().canModify()) {
                refused[0] = true;
                return record;
            }
            DeviceRecord<PowderkegState> next = record;
            for (int i = 0; i < slots.size(); i++) {
                ItemStack s = slots.get(i);
                if (!StackCodec.isEmpty(s) && !services.fuel().test(s)) {
                    refused[0] = true;
                    return record;
                }
                next = next.withSlot(i, stacks.encode(s));
            }
            return next;
        });
        if (refused[0] && after.isPresent() && !Snapshot.sameSlots(after.get().slots(), slots)) {
            return Optional.empty();
        }
        return after;
    }

    /** TFC {@code onButtonPress}: {@code toggleSeal}, the same as a sneak click with an empty hand on an unlit keg. */
    public Optional<Snapshot> toggleSeal(Player viewer) {
        return edit((record, now) -> PowderkegDevice.toggleSeal(record, now, block, services));
    }
}
