/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/component/mold/Vessel.java @ c3b4d9536 (the vessel component, its heat view
 *       and its item and fluid mutations), src/main/java/net/dries007/tfc/common/component/heat/HeatComponent.java @ c3b4d9536
 *       (withHeatCapacity settles at the current tick)
 */
package dev.tfp.core.vessel;

import dev.tfp.core.heat.HeatConfig;
import dev.tfp.core.heat.HeatState;
import java.util.ArrayList;
import java.util.List;

/**
 * A small vessel's state, evaluated lazily from the calendar tick. Immutable, and nothing ticks. Every mutation first
 * settles the temperature to its tick, then recomputes the heat capacity of the new contents, as TFC's
 * {@code Vessel.updateWith} does through the heat component. A {@link #heatTo} melts the items that reached their
 * recipe temperature, adding their fluid to the alloy and voiding any overflow.
 *
 * @param slots        the {@link VesselRules#SLOTS} item slots
 * @param alloy        the fluid
 * @param heatCapacity the heat capacity of the vessel with its contents
 * @param heat         the temperature recorded at its tick
 */
public record VesselState(List<VesselSlot> slots, Alloy alloy, float heatCapacity, HeatState heat) {

    public VesselState {
        if (slots == null || slots.size() != VesselRules.SLOTS) {
            throw new IllegalArgumentException("a vessel has " + VesselRules.SLOTS + " slots");
        }
        slots = List.copyOf(slots);
        if (alloy == null || heat == null) {
            throw new IllegalArgumentException("alloy and heat must not be null");
        }
        if (!(heatCapacity > 0f)) {
            throw new IllegalArgumentException("heatCapacity must be > 0: " + heatCapacity);
        }
    }

    /** An empty vessel recorded cold at {@code tick}. */
    public static VesselState empty(long tick) {
        List<VesselSlot> slots = new ArrayList<>(VesselRules.SLOTS);
        for (int i = 0; i < VesselRules.SLOTS; i++) {
            slots.add(VesselSlot.EMPTY);
        }
        return new VesselState(slots, Alloy.EMPTY, MoldRules.POTTERY_HEAT_CAPACITY, HeatState.cold(tick));
    }

    /** The temperature at calendar tick {@code now}. */
    public float temperatureAt(long now, HeatConfig config) {
        return heat.temperatureAt(now, heatCapacity, config);
    }

    /** {@code Vessel.isInventory} at {@code now}. */
    public boolean isInventory(long now, HeatConfig config) {
        return VesselRules.isInventory(temperatureAt(now, config), alloy.amount());
    }

    /** {@code Vessel.isMolten} at {@code now}. */
    public boolean isMolten(long now, HeatConfig config) {
        return VesselRules.isMoltenVessel(!alloy.isEmpty(), temperatureAt(now, config), alloy.meltTemperature());
    }

    /**
     * The result of an item insert.
     *
     * @param state     the vessel after the insert (unchanged when the vessel is not in inventory mode)
     * @param remainder the count that did not fit
     */
    public record InsertOutcome(VesselState state, int remainder) {
    }

    /**
     * {@code Vessel.insertItem}. Only in inventory mode. The item's melt and per-item heat capacity are recorded with the
     * slot (an existing stack of the same item keeps its own values).
     *
     * @param now          the calendar tick
     * @param slot         the slot index, {@code 0..SLOTS-1}
     * @param item         the item identifier
     * @param count        the count offered
     * @param maxStack     the stack size of the item
     * @param perItemHeatCapacity the item's heat capacity per item (used when the slot is empty)
     * @param melt         the item's melt recipe, or {@code null}
     * @param canContain   whether the vessel accepts the item (the size limit)
     * @param config       the heat config
     */
    public InsertOutcome insert(long now, int slot, String item, int count, int maxStack, float perItemHeatCapacity,
                                Melt melt, boolean canContain, HeatConfig config) {
        if (!isInventory(now, config)) {
            return new InsertOutcome(this, count);
        }
        VesselSlot current = slots.get(slot);
        boolean sameItem = current.isEmpty() || current.item().equals(item);
        VesselRules.ItemInsert result = VesselRules.insert(current.count(), count, maxStack, sameItem, canContain);
        VesselSlot next;
        if (result.content() == 0) {
            next = VesselSlot.EMPTY;
        } else if (current.isEmpty()) {
            next = new VesselSlot(item, result.content(), perItemHeatCapacity, melt);
        } else {
            next = new VesselSlot(current.item(), result.content(), current.perItemHeatCapacity(), current.melt());
        }
        List<VesselSlot> updated = new ArrayList<>(slots);
        updated.set(slot, next);
        return new InsertOutcome(settled(now, updated, alloy, config), result.remainder());
    }

    /**
     * The result of a fluid fill.
     *
     * @param state  the vessel after the fill
     * @param filled the amount taken from the input
     */
    public record FillOutcome(VesselState state, int filled) {
    }

    /**
     * {@code Vessel.fill} in any mode. The amount is capped by the alloy capacity. The first fluid into an empty vessel
     * sets the alloy's specific heat and melt temperature. Later fills keep the first fluid's values (single-result model).
     * <p>TFC quirk, mirrored: {@code Vessel.fill} writes the vessel component directly and does not call
     * {@code updateHeatCapacity}, unlike insert, extract and melt. So a fluid fill keeps the heat capacity and the recorded
     * temperature, and the capacity follows the fluid at the next item or melt change.
     *
     * @param input        the fluid offered
     * @param canContain   whether the vessel accepts this fluid
     * @param specificHeat the specific heat of the input fluid, per mB
     * @param meltTemperature the melt temperature of the input fluid, or {@code null}
     */
    public FillOutcome fill(FluidAmount input, boolean canContain, float specificHeat, Float meltTemperature) {
        int filled = VesselRules.fillAlloy(alloy.amount(), input.amount(), VesselRules.SMALL_VESSEL_CAPACITY, canContain);
        if (filled == 0) {
            return new FillOutcome(this, 0);
        }
        Alloy next = alloy.isEmpty()
            ? new Alloy(filled, specificHeat, meltTemperature)
            : new Alloy(alloy.amount() + filled, alloy.specificHeat(), alloy.meltTemperature());
        return new FillOutcome(new VesselState(slots, next, heatCapacity, heat), filled);
    }

    /**
     * {@code Vessel.setTemperature} followed by {@code updateInventoryOnMelt}. Every slot whose recipe temperature the new
     * temperature reaches melts: its fluid is added to the alloy (the overflow is voided), and the slot is emptied
     * (single-result model: the recipe leaves no solid residue).
     *
     * @param now         the calendar tick of the change
     * @param temperature the new temperature, {@code >= 0}
     * @param config      the heat config
     */
    public VesselState heatTo(long now, float temperature, HeatConfig config) {
        VesselState heated = new VesselState(slots, alloy, heatCapacity, new HeatState(temperature, now));
        List<VesselSlot> updated = new ArrayList<>(slots);
        Alloy melted = alloy;
        boolean changed = false;
        for (int i = 0; i < slots.size(); i++) {
            VesselSlot slot = slots.get(i);
            if (slot.isEmpty() || !VesselRules.melts(temperature, slot.melt())) {
                continue;
            }
            Melt melt = slot.melt();
            int total = melt.perItem().amount() * slot.count();
            int filled = VesselRules.fillAlloy(melted.amount(), total, VesselRules.SMALL_VESSEL_CAPACITY, true);
            if (melted.isEmpty()) {
                melted = new Alloy(filled, melt.specificHeat(), melt.meltTemperature());
            } else if (filled > 0) {
                melted = new Alloy(melted.amount() + filled, melted.specificHeat(), melted.meltTemperature());
            }
            updated.set(i, VesselSlot.EMPTY);
            changed = true;
        }
        if (!changed) {
            return heated;
        }
        return heated.settled(now, updated, melted, config);
    }

    /** The vessel with the given contents, its temperature settled to {@code now} under the old capacity. */
    VesselState settled(long now, List<VesselSlot> newSlots, Alloy newAlloy, HeatConfig config) {
        float settledTemperature = temperatureAt(now, config);
        float capacity = VesselRules.contentHeatCapacity(newSlots, newAlloy.amount(), newAlloy.specificHeat());
        return new VesselState(newSlots, newAlloy, capacity, new HeatState(settledTemperature, now));
    }
}
