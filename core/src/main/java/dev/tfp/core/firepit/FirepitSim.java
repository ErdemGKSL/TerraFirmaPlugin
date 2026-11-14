/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/AbstractFirepitBlockEntity.java @ c3b4d9536 (serverTick,
 *       updateSmokeLevel, consumeFuel, extinguish, light, intakeAir, addAsh, getSlotStackLimit, the slot constants),
 *       src/main/java/net/dries007/tfc/common/blockentities/FirepitBlockEntity.java @ c3b4d9536 (handleCooking, slots),
 *       src/main/java/net/dries007/tfc/common/component/heat/HeatCapability.java @ c3b4d9536 (addTemp)
 */
package dev.tfp.core.firepit;

import dev.tfp.core.cooking.FirepitRules;
import dev.tfp.core.cooking.FirepitState;
import dev.tfp.core.fuel.Fuel;
import dev.tfp.core.heat.HeatConfig;
import dev.tfp.core.heat.HeatState;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.random.RandomGenerator;

/**
 * The firepit's {@code serverTick} as a lazy catch-up (PAR-HEAT-06). {@link #advance} replays TFC's tick, one calendar
 * tick at a time, from the last update to now: the smoke update every 20 ticks, the burn and fuel consumption while lit,
 * the bellows air, the device temperature ({@link FirepitRules#adjustDeviceTemp}) and the cooking of the input item
 * ({@code handleCooking}). It is exactly TFC's live tick, so the result equals stepping, with the rain held at the value
 * sampled at the last callback.
 *
 * <p><b>Cost.</b> The replay stops as soon as nothing can change any more (unlit, no air, device at 0 C), so it runs at most
 * the burn time of the fuel in the four fuel slots plus the cooling time: about 13 000 cheap steps for the longest fuel,
 * whatever the gap. Nothing ticks in between.
 *
 * <p><b>Fuel queue.</b> TFC takes fuel from slot 0 and cascades the other fuel slots down at the end of the tick, so the
 * order fuel is taken in is the order of the non-empty fuel slots. The queue holds one entry per non-empty fuel slot,
 * {@link Optional#empty()} for an item that is no fuel (TFC removes it without adding burn time). Ash is a 50% draw per
 * taken item, from the given random.
 *
 * <p>Pure and thread-safe.
 */
public final class FirepitSim {
    /** TFC {@code SLOT_FUEL_CONSUME}..{@code SLOT_FUEL_INPUT}: slots 0 to 3 hold fuel. */
    public static final int SLOT_FUEL_CONSUME = 0;
    public static final int SLOT_FUEL_INPUT = 3;
    /** TFC {@code FirepitBlockEntity.SLOT_ITEM_INPUT}, {@code SLOT_OUTPUT_1}, {@code SLOT_OUTPUT_2}. */
    public static final int SLOT_ITEM_INPUT = 4;
    public static final int SLOT_OUTPUT_1 = 5;
    public static final int SLOT_OUTPUT_2 = 6;
    public static final int SLOTS = 7;
    /** TFC {@code level.getGameTime() % 20 == 0}: the smoke level and dirtiness update period. */
    public static final int SMOKE_PERIOD = 20;
    /** No conversion happened in the span. */
    public static final long NOT_CONVERTED = Long.MIN_VALUE;

    private FirepitSim() {
    }

    /** TFC {@code getSlotStackLimit}: the outputs hold full stacks, the rest one item. */
    public static int slotLimit(int slot) {
        return slot == SLOT_OUTPUT_1 || slot == SLOT_OUTPUT_2 ? 64 : 1;
    }

    /**
     * The item in the input slot.
     *
     * @param capacity          its heat capacity, {@code <= 0} when it cannot be heated (TFC {@code HeatCapability.get} null)
     * @param heat              its heat record
     * @param recipeTemperature the temperature of its heating recipe, {@link Float#NaN} for none
     */
    public record Cooking(float capacity, HeatState heat, float recipeTemperature) {
        public Cooking {
            Objects.requireNonNull(heat, "heat");
        }

        boolean heatable() {
            return capacity > 0f;
        }
    }

    /**
     * What happened to the input item.
     *
     * @param heat              its heat record at the end of the span, or at the conversion tick
     * @param convertedAt       the tick its recipe fired ({@code handleCooking}), or {@link #NOT_CONVERTED}
     * @param temperatureBefore the item temperature the recipe was tested against (TFC: before that tick's heating)
     */
    public record Cooked(HeatState heat, long convertedAt, float temperatureBefore) {
        public boolean converted() {
            return convertedAt != NOT_CONVERTED;
        }
    }

    /**
     * The state after a span.
     *
     * @param data     the firepit after the span
     * @param consumed how many queue entries were taken (TFC removes those fuel slots)
     * @param cooked   the input item's fate, {@code null} when there was no heatable input
     */
    public record Result(FirepitData data, int consumed, Cooked cooked) {
    }

    /**
     * The firepit after the ticks {@code fromTick + 1 .. toTick}, as TFC's {@code serverTick} would leave it.
     *
     * @param queue  the fuel queue (see the class comment)
     * @param input  the input item, or {@code null} for none
     * @param random ash draws
     */
    public static Result advance(FirepitData data, long fromTick, long toTick, List<Optional<Fuel>> queue, Cooking input,
            RandomGenerator random, FirepitRules.DeviceConfig device, HeatConfig heatConfig) {
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(queue, "queue");
        Mut m = new Mut(data, queue, random);
        float delta = (float) device.heatingModifier();
        boolean raining = data.raining();
        boolean cooking = input != null && input.heatable();
        HeatState itemHeat = input == null ? null : input.heat();
        long convertedAt = NOT_CONVERTED;
        float before = 0f;
        long t = fromTick;
        while (t < toTick) {
            if (!m.lit && m.air == 0 && m.temperature == 0f) {
                break; // nothing below can change any more; only the dirtiness decay is left
            }
            t++;
            if (Math.floorMod(t, SMOKE_PERIOD) == 0) {
                m.updateSmoke();
            }
            if (m.lit) {
                if (m.burn > 0) {
                    m.burn -= m.air > 0 || raining ? 2 : 1; // twice as fast with bellows air or in the rain
                }
                if (m.burn <= 0 && !m.consumeFuel()) {
                    m.extinguish();
                }
            }
            if (m.air > 0) {
                m.air--;
            }
            if (m.temperature > 0 || m.burnTemperature > 0) {
                m.temperature = FirepitRules.adjustDeviceTemp(m.temperature, m.burnTemperature, m.air, raining, delta);
            }
            if (cooking && m.temperature > 0) {
                float itemBefore = itemHeat.temperatureAt(t, input.capacity(), heatConfig);
                itemHeat = itemHeat.heat(m.temperature, t, input.capacity(), heatConfig);
                if (!Float.isNaN(input.recipeTemperature()) && itemBefore >= input.recipeTemperature()) {
                    convertedAt = t;
                    before = itemBefore;
                    cooking = false; // the input slot is cleared
                }
            }
        }
        if (t < toTick) {
            m.decaySmoke(t, toTick);
        }
        Cooked cooked = input == null || !input.heatable() ? null : new Cooked(itemHeat, convertedAt, before);
        return new Result(m.data(), m.used, cooked);
    }

    /**
     * TFC {@code light}: a lit firepit stays lit; otherwise it takes one fuel item and is lit when that gives burn time.
     *
     * @return the firepit and how many queue entries were taken (0 or 1)
     */
    public static Result light(FirepitData data, List<Optional<Fuel>> queue, RandomGenerator random) {
        if (data.heat().burnTicks() > 0) {
            return new Result(data, 0, null);
        }
        Mut m = new Mut(data, queue, random);
        if (m.consumeFuel()) {
            m.lit = true;
        }
        return new Result(m.data(), m.used, null);
    }

    /** TFC {@code extinguish}: only a lit firepit changes; burn, air, burn temperature and dirtiness drop to 0. */
    public static FirepitData extinguish(FirepitData data) {
        if (!data.lit()) {
            return data;
        }
        Mut m = new Mut(data, List.of(), null);
        m.extinguish();
        return m.data();
    }

    /** TFC {@code intakeAir}: air ticks add up to {@link FirepitState#MAX_AIR_TICKS}. */
    public static FirepitData intakeAir(FirepitData data, int amount) {
        FirepitState h = data.heat();
        int air = (int) Math.min(FirepitState.MAX_AIR_TICKS, Math.max(0L, (long) h.airTicks() + amount));
        return new FirepitData(new FirepitState(h.burnTicks(), h.burnTemperature(), h.temperature(), air), data.lit(),
            data.smokeLevel(), data.dirtiness(), data.ash(), data.lastMaxBurnTicks(), data.raining());
    }

    /**
     * The ticks until a lit firepit goes out with the given fuel and the sampled rain (the tick of {@code extinguish}),
     * or -1 when it is not lit. Used to schedule the visual change while the chunk is loaded.
     */
    public static long ticksUntilOut(FirepitData data, List<Optional<Fuel>> queue) {
        if (!data.lit()) {
            return -1;
        }
        Mut m = new Mut(data, queue, null);
        long ticks = 0;
        while (m.lit) {
            ticks++;
            if (m.burn > 0) {
                m.burn -= m.air > 0 || data.raining() ? 2 : 1;
            }
            if (m.burn <= 0 && !m.consumeFuel()) {
                m.extinguish();
            }
            if (m.air > 0) {
                m.air--;
            }
        }
        return ticks;
    }

    /** TFC {@code updateSmokeLevel}: {@code Mth.ceil(Mth.map(dirtiness, 0, 1, 0, 4))}. */
    public static int smokeLevel(float dirtiness) {
        return (int) Math.ceil(Math.clamp(dirtiness, 0f, 1f) * 4f);
    }

    /** The mutable working copy of one replay. */
    private static final class Mut {
        final List<Optional<Fuel>> queue;
        final RandomGenerator random;
        int burn;
        float burnTemperature;
        float temperature;
        int air;
        boolean lit;
        int smoke;
        float dirtiness;
        int ash;
        int lastMax;
        final boolean raining;
        int used;

        Mut(FirepitData d, List<Optional<Fuel>> queue, RandomGenerator random) {
            this.queue = queue;
            this.random = random;
            burn = d.heat().burnTicks();
            burnTemperature = d.heat().burnTemperature();
            temperature = d.heat().temperature();
            air = d.heat().airTicks();
            lit = d.lit();
            smoke = d.smokeLevel();
            dirtiness = d.dirtiness();
            ash = d.ash();
            lastMax = d.lastMaxBurnTicks();
            raining = d.raining();
        }

        /** TFC {@code consumeFuel}: takes the next item; a fuel adds its burn; 50% ash. True when burning after. */
        boolean consumeFuel() {
            if (used < queue.size()) {
                Optional<Fuel> next = queue.get(used++);
                if (next.isPresent()) {
                    Fuel fuel = next.get();
                    burn += fuel.duration();
                    lastMax = fuel.duration();
                    burnTemperature = fuel.temperature();
                    dirtiness += 1f - fuel.purity();
                }
                if (random != null && random.nextFloat() < 0.5f) {
                    ash = Math.min(FirepitData.MAX_ASH, ash + 1);
                }
            }
            return burn > 0;
        }

        void extinguish() {
            lit = false;
            burn = 0;
            air = 0;
            burnTemperature = 0;
            dirtiness = 0;
        }

        void updateSmoke() {
            dirtiness = Math.clamp(0.99f * dirtiness, 0f, 1f);
            smoke = smokeLevel(dirtiness);
        }

        /** The smoke updates of the ticks {@code t + 1 .. to} once nothing else changes. */
        void decaySmoke(long t, long to) {
            long n = Math.floorDiv(to, SMOKE_PERIOD) - Math.floorDiv(t, SMOKE_PERIOD);
            if (n <= 0) {
                return;
            }
            if (dirtiness == 0f) {
                smoke = 0;
                return;
            }
            for (long i = 0; i < Math.min(n, 4096); i++) {
                updateSmoke();
            }
            if (n > 4096) {
                dirtiness = 0f;
                smoke = 0;
            }
        }

        FirepitData data() {
            return new FirepitData(new FirepitState(Math.max(0, burn), burnTemperature, temperature, air), lit, smoke,
                dirtiness, ash, lastMax, raining);
        }
    }
}
