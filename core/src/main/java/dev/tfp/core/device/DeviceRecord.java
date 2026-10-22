/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/InventoryBlockEntity.java @ c3b4d9536 (inventory),
 *       src/main/java/net/dries007/tfc/util/calendar/ICalendarTickable.java @ c3b4d9536 (lastUpdateTick)
 */
package dev.tfp.core.device;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Everything one device block persists (PAR-INFRA-02): the kind's own state {@code S}, the fixed slot list of TFC's
 * {@code InventoryBlockEntity} and the {@code lastUpdateTick} of TFC's {@code ICalendarTickable} (player ticks;
 * {@link #NEVER} until the first catch-up stamps it, as TFC initialises the field to {@code Integer.MIN_VALUE}).
 * Immutable; the {@code with*} methods return copies.
 *
 * @param <S> the kind's state, immutable with value equality
 */
public record DeviceRecord<S>(S state, List<ItemBlob> slots, long lastUpdateTick) {
    /** {@code lastUpdateTick} of a device that has never been caught up. */
    public static final long NEVER = Long.MIN_VALUE;

    public DeviceRecord {
        Objects.requireNonNull(state, "state");
        slots = List.copyOf(slots);
    }

    /** A freshly placed device: {@code state}, {@code slotCount} empty slots, never updated. */
    public static <S> DeviceRecord<S> fresh(S state, int slotCount) {
        if (slotCount < 0) {
            throw new IllegalArgumentException("slotCount < 0");
        }
        return new DeviceRecord<>(state, Collections.nCopies(slotCount, ItemBlob.EMPTY), NEVER);
    }

    public boolean neverUpdated() {
        return lastUpdateTick == NEVER;
    }

    public ItemBlob slot(int index) {
        return slots.get(index);
    }

    public DeviceRecord<S> withState(S next) {
        return new DeviceRecord<>(next, slots, lastUpdateTick);
    }

    public DeviceRecord<S> withSlot(int index, ItemBlob blob) {
        Objects.checkIndex(index, slots.size());
        List<ItemBlob> next = new ArrayList<>(slots);
        next.set(index, blob == null ? ItemBlob.EMPTY : blob);
        return new DeviceRecord<>(state, next, lastUpdateTick);
    }

    public DeviceRecord<S> withSlots(List<ItemBlob> next) {
        return new DeviceRecord<>(state, next, lastUpdateTick);
    }

    public DeviceRecord<S> withLastUpdateTick(long tick) {
        return new DeviceRecord<>(state, slots, tick);
    }

    /** TFC {@code clearContent}: every slot emptied. */
    public DeviceRecord<S> cleared() {
        return withSlots(Collections.nCopies(slots.size(), ItemBlob.EMPTY));
    }

    /** The non-empty slots in slot order (what TFC's {@code ejectInventory} spawns). */
    public List<ItemBlob> nonEmptySlots() {
        List<ItemBlob> out = new ArrayList<>();
        for (ItemBlob b : slots) {
            if (!b.isEmpty()) {
                out.add(b);
            }
        }
        return List.copyOf(out);
    }
}
