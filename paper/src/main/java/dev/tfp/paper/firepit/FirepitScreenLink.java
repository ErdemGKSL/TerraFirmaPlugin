/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/container/FirepitContainer.java @ c3b4d9536 (the seven slots, CallbackSlot),
 * file: src/main/java/net/dries007/tfc/common/blockentities/AbstractFirepitBlockEntity.java @ c3b4d9536
 *       (setAndUpdateSlots, cascadeFuelSlots, isItemValid, getTemperature)
 */
package dev.tfp.paper.firepit;

import dev.tfp.core.device.DeviceRecord;
import dev.tfp.core.device.ItemBlob;
import dev.tfp.core.firepit.FirepitData;
import dev.tfp.core.firepit.FirepitSim;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.device.StackCodec;
import dev.tfp.paper.firepit.gui.FirepitLink;
import dev.tfp.paper.firepit.gui.FirepitSnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;

/**
 * PAR-HEAT-20: the {@link FirepitLink} of a placed firepit, over its device record. Every call loads the record through
 * {@link DeviceService#edit} (catch-up first), changes it and stores it, then answers the firepit as stored: a snapshot
 * after the catch-up; a commit stores the seven slots as the viewer left them (TFC's {@code CallbackSlot} ->
 * {@code setAndUpdateSlots}) and then runs {@code cascadeFuelSlots}; {@code isItemValid} is the device's own.
 *
 * <p>Threads: the screen calls on the viewer's thread; the record lives on the block's region. When this thread does not
 * own the block (Folia: the regions split while the screen is open) every call answers empty, and the screen gives the
 * viewer back what it held since the last commit and closes, as the pot and barrel screens.
 */
public final class FirepitScreenLink implements FirepitLink {
    /** The screen key of a firepit: its block position. */
    public record Key(UUID world, int x, int y, int z) {
        public static Key of(Block block) {
            return new Key(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
        }
    }

    private final DeviceService devices;
    private final FirepitDevice device;
    private final Block block;
    private final Predicate<Block> owned;

    /**
     * @param owned whether the current thread owns {@code block}'s region ({@code Scheduler.isOwner}, Folia's
     *              {@code Bukkit.isOwnedByCurrentRegion})
     */
    public FirepitScreenLink(DeviceService devices, FirepitDevice device, Block block, Predicate<Block> owned) {
        this.devices = Objects.requireNonNull(devices, "devices");
        this.device = Objects.requireNonNull(device, "device");
        this.block = Objects.requireNonNull(block, "block");
        this.owned = Objects.requireNonNull(owned, "owned");
    }

    public Key key() {
        return Key.of(block);
    }

    @Override
    public Optional<FirepitSnapshot> snapshot() {
        if (!owned.test(block)) {
            return Optional.empty();
        }
        return devices.edit(block, device.kind(), (record, now) -> record).map(this::snapshotOf);
    }

    @Override
    public Optional<FirepitSnapshot> commit(List<ItemStack> slots) {
        if (slots.size() != FirepitSim.SLOTS) {
            throw new IllegalArgumentException(slots.size() + " slots, not " + FirepitSim.SLOTS);
        }
        if (!owned.test(block)) {
            return Optional.empty();
        }
        StackCodec stacks = devices.stacks();
        return devices.edit(block, device.kind(), (record, now) -> withSlots(record, slots, stacks)).map(this::snapshotOf);
    }

    @Override
    public boolean isItemValid(int tfcSlot, ItemStack stack) {
        return device.isItemValid(tfcSlot, stack);
    }

    /** The seven slots stored, then TFC {@code cascadeFuelSlots}. */
    static DeviceRecord<FirepitData> withSlots(DeviceRecord<FirepitData> record, List<ItemStack> slots, StackCodec stacks) {
        for (int i = 0; i < FirepitSim.SLOTS; i++) {
            ItemStack s = slots.get(i);
            record = record.withSlot(i, s == null ? ItemBlob.EMPTY : stacks.encode(s));
        }
        return cascade(record);
    }

    /** TFC {@code cascadeFuelSlots}: the non-empty fuel slots move down to the lowest open slots, in order. */
    static DeviceRecord<FirepitData> cascade(DeviceRecord<FirepitData> record) {
        List<ItemBlob> fuel = new ArrayList<>(4);
        for (int i = FirepitSim.SLOT_FUEL_CONSUME; i <= FirepitSim.SLOT_FUEL_INPUT; i++) {
            if (!record.slot(i).isEmpty()) {
                fuel.add(record.slot(i));
            }
        }
        for (int i = FirepitSim.SLOT_FUEL_CONSUME; i <= FirepitSim.SLOT_FUEL_INPUT; i++) {
            record = record.withSlot(i, i < fuel.size() ? fuel.get(i) : ItemBlob.EMPTY);
        }
        return record;
    }

    private FirepitSnapshot snapshotOf(DeviceRecord<FirepitData> record) {
        StackCodec stacks = devices.stacks();
        List<ItemStack> slots = new ArrayList<>(FirepitSim.SLOTS);
        for (int i = 0; i < FirepitSim.SLOTS; i++) {
            slots.add(stacks.decode(record.slot(i)));
        }
        return new FirepitSnapshot(record.state().temperature(), slots);
    }
}
