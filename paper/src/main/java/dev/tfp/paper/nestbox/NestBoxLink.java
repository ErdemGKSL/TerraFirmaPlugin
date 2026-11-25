/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/NestBoxBlockEntity.java @ c3b4d9536 (isItemValid,
 *       getSlotStackLimit)
 */
package dev.tfp.paper.nestbox;

import dev.tfp.core.device.DeviceRecord;
import dev.tfp.core.nestbox.NestBoxRules;
import dev.tfp.core.nestbox.NestBoxState;
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
 * PAR-FAUNA-07: the screen's view of one placed nest box, over its device record ({@link DeviceService#edit}). Every call
 * answers the box as stored, or empty when it is gone or this thread does not own its region.
 */
public final class NestBoxLink {
    /** The screen key of a nest box: its block position. */
    public record Key(UUID world, int x, int y, int z) {
        public static Key of(Block block) {
            return new Key(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
        }
    }

    /** What the screen shows: the four slots (copies, TFC order). */
    public record Snapshot(List<ItemStack> slots) {
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

    /**
     * The result of a commit.
     *
     * @param snapshot the box after the commit
     * @param overflow items the box refused: the part of a stack above the slot limit (one egg per slot); the screen gives
     *                 them back to the viewer
     */
    public record Committed(Snapshot snapshot, List<ItemStack> overflow) {
    }

    private final DeviceService devices;
    private final DeviceKind<NestBoxState> kind;
    private final NestBoxDevice.Services services;
    private final Scheduler scheduler;
    private final Block block;

    public NestBoxLink(DeviceService devices, DeviceKind<NestBoxState> kind, NestBoxDevice.Services services,
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

    Snapshot snapshotOf(DeviceRecord<NestBoxState> record) {
        StackCodec stacks = devices.stacks();
        List<ItemStack> slots = new ArrayList<>(NestBoxRules.SLOTS);
        for (int i = 0; i < NestBoxRules.SLOTS; i++) {
            slots.add(stacks.decode(record.slot(i)));
        }
        return new Snapshot(slots);
    }

    /** The box as stored, brought up to date (a due hatch has happened). */
    public Optional<Snapshot> snapshot() {
        if (!owned()) {
            return Optional.empty();
        }
        return devices.edit(block, kind, (record, now) -> NestBoxDevice.process(record, now, block, devices.stacks(), services))
            .map(this::snapshotOf);
    }

    /**
     * Stores the screen's four slots. A stack of more than one egg keeps one and the rest comes back as overflow
     * (TFC {@code getSlotStackLimit}); a stack that is not a fresh egg is refused the same way. The next hatch is queued.
     */
    public Optional<Committed> commit(List<ItemStack> slots) {
        if (slots.size() != NestBoxRules.SLOTS) {
            throw new IllegalArgumentException(slots.size() + " slots");
        }
        if (!owned()) {
            return Optional.empty();
        }
        StackCodec stacks = devices.stacks();
        List<ItemStack> overflow = new ArrayList<>();
        Optional<DeviceRecord<NestBoxState>> stored = devices.edit(block, kind, (record, now) -> {
            DeviceRecord<NestBoxState> next = record;
            for (int i = 0; i < slots.size(); i++) {
                ItemStack s = slots.get(i);
                if (StackCodec.isEmpty(s)) {
                    next = next.withSlot(i, stacks.encode(ItemStack.empty()));
                } else if (!NestBoxDevice.accepts(s, services)) {
                    overflow.add(s.clone());
                    next = next.withSlot(i, stacks.encode(ItemStack.empty()));
                } else {
                    ItemStack one = s.clone();
                    one.setAmount(NestBoxRules.SLOT_LIMIT);
                    if (s.getAmount() > NestBoxRules.SLOT_LIMIT) {
                        ItemStack rest = s.clone();
                        rest.setAmount(s.getAmount() - NestBoxRules.SLOT_LIMIT);
                        overflow.add(rest);
                    }
                    next = next.withSlot(i, stacks.encode(one));
                }
            }
            NestBoxDevice.scheduleHatch(next, now, block, stacks, services);
            return next;
        });
        return stored.map(r -> new Committed(snapshotOf(r), List.copyOf(overflow)));
    }
}
