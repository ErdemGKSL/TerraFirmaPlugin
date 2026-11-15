/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/GrillBlockEntity.java @ c3b4d9536 (SLOT_EXTRA_INPUT_START/END,
 *       getSlotStackLimit, handleCooking: addTemp then isValidTemperature on every grill slot),
 *       src/main/java/net/dries007/tfc/common/blockentities/AbstractFirepitBlockEntity.java @ c3b4d9536 (serverTick: the
 *       burn and the device temperature run before handleCooking, copyFrom: the fields a conversion keeps)
 */
package dev.tfp.core.grill;

import dev.tfp.core.cooking.FirepitRules;
import dev.tfp.core.cooking.FirepitState;
import dev.tfp.core.firepit.FirepitData;
import dev.tfp.core.firepit.FirepitSim;
import dev.tfp.core.fuel.Fuel;
import dev.tfp.core.heat.HeatConfig;
import dev.tfp.core.heat.HeatState;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.random.RandomGenerator;

/**
 * The grill's {@code serverTick} as a lazy catch-up (PAR-HEAT-08). The grill is TFC's firepit with five grill slots in
 * place of the input and the outputs: the burn, the fuel, the smoke, the ash and the device temperature are the firepit's
 * ({@link FirepitSim}), and every tick {@code handleCooking} heats each grill item towards the device temperature
 * ({@code addTemp}) and then fires its heating recipe when the item is at or above the recipe temperature (TFC tests the
 * grill after the heating step, the firepit before it).
 *
 * <p>{@link #advance} replays the ticks one at a time and stops at the first tick on which a recipe fires: the caller
 * replaces the converted items (TFC {@code setStackInSlot(slot, output)}, whose new item has a recipe of its own or none)
 * and calls again for the rest of the span. Once nothing can heat any more (the firepit unlit, airless and at 0 C, or no
 * heatable item on the grill) the rest of the span is the firepit's own catch-up in one call; the items' heat records are
 * lazy and cool by themselves.
 *
 * <p>Pure and thread-safe.
 */
public final class GrillSim {
    /** TFC {@code GrillBlockEntity.SLOT_EXTRA_INPUT_START} and {@code SLOT_EXTRA_INPUT_END}: slots 4 to 8. */
    public static final int SLOT_EXTRA_INPUT_START = 4;
    public static final int SLOT_EXTRA_INPUT_END = 8;
    /** Five grill slots. */
    public static final int GRILL_SLOTS = SLOT_EXTRA_INPUT_END - SLOT_EXTRA_INPUT_START + 1;
    /** TFC {@code defaultInventory(9)}: fuel 0..3, grill 4..8. */
    public static final int SLOTS = 9;

    private GrillSim() {
    }

    /** TFC {@code GrillBlockEntity.getSlotStackLimit}: every slot holds one item. */
    public static int slotLimit(int slot) {
        return 1;
    }

    /** Whether {@code slot} is one of the five grill slots. */
    public static boolean isGrillSlot(int slot) {
        return slot >= SLOT_EXTRA_INPUT_START && slot <= SLOT_EXTRA_INPUT_END;
    }

    /**
     * One grill item.
     *
     * @param capacity          its heat capacity, {@code <= 0} when it cannot be heated (TFC {@code HeatCapability.get} null)
     * @param heat              its heat record
     * @param recipeTemperature the temperature of its heating recipe, {@link Float#NaN} for none
     */
    public record Item(float capacity, HeatState heat, float recipeTemperature) {
        public Item {
            Objects.requireNonNull(heat, "heat");
        }

        boolean heatable() {
            return capacity > 0f;
        }

        boolean hasRecipe() {
            return !Float.isNaN(recipeTemperature);
        }
    }

    /**
     * The state after a span, or at the first tick a recipe fired.
     *
     * @param data      the firepit part at {@link #tick}
     * @param consumed  how many fuel queue entries were taken
     * @param heats     the heat record of each grill slot at {@link #tick} (index 0 = slot 4), {@code null} for an empty
     *                  or unheatable slot
     * @param converted the grill indices (0 = slot 4) whose recipe fired on {@link #tick}; empty when the span ran out
     * @param temperatureTested the item temperature each converted recipe was tested at (index as {@code heats})
     * @param tick      where the replay stopped: the end of the span, or the conversion tick
     */
    public record Result(FirepitData data, int consumed, List<HeatState> heats, List<Integer> converted,
            List<Float> temperatureTested, long tick) {
        public Result {
            heats = Collections.unmodifiableList(new ArrayList<>(heats));
            converted = List.copyOf(converted);
            temperatureTested = Collections.unmodifiableList(new ArrayList<>(temperatureTested));
        }

        public boolean anyConverted() {
            return !converted.isEmpty();
        }
    }

    /**
     * The grill after the ticks {@code fromTick + 1 .. toTick}, or up to the first tick a recipe fires.
     *
     * @param queue  the fuel queue (non-empty fuel slots in order, see {@link FirepitSim})
     * @param items  the five grill slots (index 0 = slot 4), {@code null} for an empty slot
     * @param random ash draws
     */
    public static Result advance(FirepitData data, long fromTick, long toTick, List<Optional<Fuel>> queue, List<Item> items,
            RandomGenerator random, FirepitRules.DeviceConfig device, HeatConfig heatConfig) {
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(queue, "queue");
        Objects.requireNonNull(items, "items");
        if (items.size() != GRILL_SLOTS) {
            throw new IllegalArgumentException(items.size() + " grill items, not " + GRILL_SLOTS);
        }
        HeatState[] heats = new HeatState[GRILL_SLOTS];
        boolean anyHeatable = false;
        for (int i = 0; i < GRILL_SLOTS; i++) {
            Item item = items.get(i);
            if (item != null && item.heatable()) {
                heats[i] = item.heat();
                anyHeatable = true;
            }
        }
        FirepitData d = data;
        int used = 0;
        long t = fromTick;
        List<Integer> converted = new ArrayList<>();
        Float[] tested = new Float[GRILL_SLOTS];
        while (t < toTick) {
            boolean dead = !d.lit() && d.heat().airTicks() == 0 && d.temperature() == 0f
                && d.heat().burnTemperature() == 0f;
            if (!anyHeatable || (dead && !readyNow(items, heats, t + 1, heatConfig))) {
                // nothing heats any more: the firepit's own catch-up for the rest, the items cool lazily
                FirepitSim.Result r = FirepitSim.advance(d, t, toTick, rest(queue, used), null, random, device, heatConfig);
                used += r.consumed();
                d = r.data();
                t = toTick;
                break;
            }
            FirepitSim.Result r = FirepitSim.advance(d, t, t + 1, rest(queue, used), null, random, device, heatConfig);
            used += r.consumed();
            d = r.data();
            t++;
            float temperature = d.temperature();
            for (int i = 0; i < GRILL_SLOTS; i++) {
                if (heats[i] == null) {
                    continue;
                }
                Item item = items.get(i);
                heats[i] = heats[i].heat(temperature, t, item.capacity(), heatConfig); // HeatCapability.addTemp
                float now = heats[i].temperatureAt(t, item.capacity(), heatConfig);
                if (item.hasRecipe() && now >= item.recipeTemperature()) { // HeatingRecipe.isValidTemperature
                    converted.add(i);
                    tested[i] = now;
                }
            }
            if (!converted.isEmpty()) {
                break;
            }
        }
        List<HeatState> heatList = new ArrayList<>(GRILL_SLOTS);
        List<Float> testedList = new ArrayList<>(GRILL_SLOTS);
        for (int i = 0; i < GRILL_SLOTS; i++) {
            heatList.add(heats[i]);
            testedList.add(tested[i]);
        }
        return new Result(d, used, heatList, converted, testedList, t);
    }

    /** A recipe that would fire on the next tick without any heat (an item put on already hot enough). */
    private static boolean readyNow(List<Item> items, HeatState[] heats, long tick, HeatConfig heatConfig) {
        for (int i = 0; i < GRILL_SLOTS; i++) {
            Item item = items.get(i);
            if (heats[i] != null && item.hasRecipe()
                    && heats[i].temperatureAt(tick, item.capacity(), heatConfig) >= item.recipeTemperature()) {
                return true;
            }
        }
        return false;
    }

    private static List<Optional<Fuel>> rest(List<Optional<Fuel>> queue, int used) {
        return used >= queue.size() ? List.of() : queue.subList(used, queue.size());
    }

    /**
     * TFC {@code AbstractFirepitBlockEntity.convertTo} + {@code copyFrom}: the device a firepit, grill or pot turns into
     * keeps the burn ticks, the air, the burn temperature and the device temperature, and the {@code lit} and
     * {@code smoke_level} block state ({@code copyProperties}); the new block entity starts with no ash (the old one drops
     * its ash), no dirtiness and TFC's initial {@code lastMaxBurnTicks}. The rain sample is kept.
     */
    public static FirepitData converted(FirepitData from) {
        FirepitState h = from.heat();
        return new FirepitData(new FirepitState(h.burnTicks(), h.burnTemperature(), h.temperature(), h.airTicks()),
            from.lit(), from.smokeLevel(), 0f, 0, FirepitData.FRESH.lastMaxBurnTicks(), from.raining());
    }
}
