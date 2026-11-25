/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/CharcoalForgeBlockEntity.java @ c3b4d9536 (serverTick: the
 *       burn, the temperature, provideHeatTo, addTemp on every input item, handleInputMelting),
 *       src/main/java/net/dries007/tfc/common/component/heat/HeatCapability.java @ c3b4d9536 (addTemp, provideHeatTo)
 */
package dev.tfp.core.charcoalforge;

import dev.tfp.core.cooking.FirepitRules;
import dev.tfp.core.fuel.Fuel;
import dev.tfp.core.heat.HeatConfig;
import dev.tfp.core.heat.HeatState;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * PAR-METAL-08: the charcoal forge's {@code serverTick} as a lazy replay. {@link #advance} runs TFC's live tick from the
 * last update to now: the burn and fuel (twice as fast with bellows air or in the rain), the forge temperature, the heat
 * handed to the block above and, on every tick the forge is warm, {@code addTemp} on each input item with that tick's
 * temperature. It uses {@link CharcoalForgeRules#step} for the burn and the temperature, so the result is exactly the
 * stepped one.
 *
 * <p><b>Cost.</b> Warm-up and cool-down are stepped tick by tick (about a thousand ticks, one degree per tick). While the
 * forge has settled on its fuel's temperature (lit, no air, no rain) and every item has reached a fixed point of
 * {@code addTemp}, the replay jumps to the tick the next fuel item is taken, so the cost is a few steps per fuel item
 * whatever the gap. A cold forge with no air ends the replay: items only cool from there, which their own heat records
 * give lazily.
 *
 * <p><b>Conversions.</b> TFC tests each input item's recipe after heating it (temperature at or above the recipe's). The
 * replay stops at the first tick where an item converts ({@link Result#stoppedAt}); the caller replaces the item and
 * replays the rest from that tick. Items without a convertible recipe pass {@link Input#recipeTemperature} as NaN.
 *
 * <p>Pure and thread-safe.
 */
public final class CharcoalForgeSim {
    /** TFC {@code CharcoalForgeBlockEntity.SLOT_FUEL_MIN..MAX}. */
    public static final int SLOT_FUEL_MIN = 0;
    public static final int SLOT_FUEL_MAX = 4;
    /** TFC {@code SLOT_INPUT_MIN..MAX}. */
    public static final int SLOT_INPUT_MIN = 5;
    public static final int SLOT_INPUT_MAX = 9;
    /** TFC {@code SLOT_EXTRA_MIN..MAX}. */
    public static final int SLOT_EXTRA_MIN = 10;
    public static final int SLOT_EXTRA_MAX = 13;
    /** TFC {@code defaultInventory(14)}. */
    public static final int SLOTS = 14;
    /** TFC {@code getSlotStackLimit}: one item per slot. */
    public static final int SLOT_LIMIT = 1;
    /** The number of input slots. */
    public static final int INPUTS = SLOT_INPUT_MAX - SLOT_INPUT_MIN + 1;

    private CharcoalForgeSim() {
    }

    /**
     * TFC {@code HeatCapability.provideHeatTo(level, pos.above(), DOWN, temperature)}: on every tick in {@code fromTick ..
     * toTick} (inclusive) the forge gave {@code temperature} to the block above. Constant runs come as one call.
     */
    @FunctionalInterface
    public interface HeatSink {
        HeatSink NONE = (from, to, temperature) -> {
        };

        void heat(long fromTick, long toTick, float temperature);
    }

    /**
     * One input item as the replay sees it.
     *
     * @param heat              its heat record
     * @param capacity          its heat capacity, {@code > 0}
     * @param recipeTemperature the temperature of its heating recipe when the recipe may fire here, NaN for none
     */
    public record Input(HeatState heat, float capacity, float recipeTemperature) {
        public Input {
            Objects.requireNonNull(heat, "heat");
        }
    }

    /**
     * The state after a span.
     *
     * @param data        the forge after the span (or at {@code stoppedAt})
     * @param consumed    how many queue entries were taken
     * @param heats       the heat record of every input slot after the replay, {@code null} for an empty slot
     * @param stoppedAt   the tick the replay stopped at, {@code toTick} when it ran to the end
     * @param conversions the input indices (0 to {@link #INPUTS} - 1) whose recipe fired at {@code stoppedAt}
     */
    public record Result(CharcoalForgeData data, int consumed, List<HeatState> heats, long stoppedAt, List<Integer> conversions) {
        public Result {
            heats = Collections.unmodifiableList(new ArrayList<>(heats));
            conversions = List.copyOf(conversions);
        }

        /** Whether the replay ran to {@code toTick}. */
        public boolean finished(long toTick) {
            return stoppedAt >= toTick && conversions.isEmpty();
        }
    }

    /**
     * The forge after the ticks {@code fromTick + 1 .. toTick}, as TFC's {@code serverTick} would leave it.
     *
     * @param data     the forge at {@code fromTick} (the lit flag, the burn, the temperature, the air; the rain is held)
     * @param queue    the fuel items in slot order, only the ones that are fuel
     * @param inputs   the five input slots, {@code null} for an empty slot or one that cannot be heated
     * @param config   the device settings
     * @param heat     the item heating and cooling settings
     * @param sink     receives the heat given to the block above
     */
    public static Result advance(CharcoalForgeData data, long fromTick, long toTick, List<Fuel> queue, List<Input> inputs,
            FirepitRules.DeviceConfig config, HeatConfig heat, HeatSink sink) {
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(queue, "queue");
        Objects.requireNonNull(inputs, "inputs");
        Objects.requireNonNull(sink, "sink");
        if (inputs.size() != INPUTS) {
            throw new IllegalArgumentException("inputs must hold " + INPUTS + " entries: " + inputs.size());
        }
        boolean raining = data.raining();
        CharcoalForgeState state = data.state();
        HeatState[] heats = new HeatState[INPUTS];
        for (int i = 0; i < INPUTS; i++) {
            heats[i] = inputs.get(i) == null ? null : inputs.get(i).heat();
        }
        List<Integer> converted = new ArrayList<>(INPUTS);
        int used = 0;
        long t = fromTick;
        while (t < toTick) {
            if (!state.lit() && state.burnTemperature() == 0f && state.temperature() == 0f) {
                // cold and unlit: only the air runs down, the items only cool (their own records)
                long k = Math.min(state.airTicks(), toTick - t);
                state = new CharcoalForgeState(false, 0, 0f, 0f, state.airTicks() - (int) k);
                t = toTick;
                break;
            }
            if (settled(state, raining) && stable(state.temperature(), t, heats, inputs, heat)) {
                // only the burn counter moves until the tick the next item is taken
                long k = Math.min(state.burnTicks() - 1L, toTick - t);
                state = new CharcoalForgeState(true, state.burnTicks() - (int) k, state.burnTemperature(),
                    state.temperature(), 0);
                sink.heat(t + 1, t + k, state.temperature());
                for (int i = 0; i < INPUTS; i++) {
                    if (heats[i] != null) {
                        heats[i] = new HeatState(heats[i].temperature(), t + k);
                    }
                }
                t += k;
                continue;
            }
            t++;
            CharcoalForgeRules.Step step = CharcoalForgeRules.step(state, raining, used < queue.size() ? queue.get(used) : null,
                config);
            if (step.consumedFuel()) {
                used++;
            }
            state = step.state();
            if (state.temperature() > 0 || state.burnTemperature() > 0) {
                sink.heat(t, t, state.temperature());
                for (int i = 0; i < INPUTS; i++) {
                    Input in = inputs.get(i);
                    if (in == null || heats[i] == null) {
                        continue;
                    }
                    heats[i] = CharcoalForgeRules.heatInput(heats[i], state.temperature(), in.capacity(), t, heat);
                    if (!Float.isNaN(in.recipeTemperature()) && heats[i].temperature() >= in.recipeTemperature()) {
                        converted.add(i);
                    }
                }
                if (!converted.isEmpty()) {
                    break;
                }
            }
        }
        return new Result(data.withState(state), used, java.util.Arrays.asList(heats), t, converted);
    }

    /** Lit with burn ticks to spare, no air and no rain, and at the fuel's temperature: only the burn counter moves. */
    private static boolean settled(CharcoalForgeState s, boolean raining) {
        return s.lit() && !raining && s.airTicks() == 0 && s.burnTicks() > 1 && s.burnTemperature() > 0f
            && s.temperature() == s.burnTemperature();
    }

    /** Whether every input has reached a fixed point of {@code addTemp} at {@code target} (one more step changes nothing). */
    private static boolean stable(float target, long t, HeatState[] heats, List<Input> inputs, HeatConfig heat) {
        for (int i = 0; i < INPUTS; i++) {
            Input in = inputs.get(i);
            if (in == null || heats[i] == null) {
                continue;
            }
            HeatState next = CharcoalForgeRules.heatInput(heats[i], target, in.capacity(), t + 1, heat);
            if (next.temperature() != heats[i].temperature()) {
                return false;
            }
        }
        return true;
    }

    /**
     * The ticks until a lit forge goes out with the given fuel (the tick of {@code extinguish}), or -1 when it is not
     * lit. Used to schedule the visual change while the chunk is loaded.
     */
    public static long ticksUntilOut(CharcoalForgeData data, List<Fuel> queue) {
        CharcoalForgeState s = data.state();
        if (!s.lit()) {
            return -1;
        }
        boolean raining = data.raining();
        long ticks = 0;
        int used = 0;
        int burn = s.burnTicks();
        int air = s.airTicks();
        boolean lit = true;
        while (lit) {
            if (air == 0 && !raining && burn > 1) {
                ticks += burn - 1L;
                burn = 1;
            }
            ticks++;
            if (burn > 0) {
                burn -= CharcoalForgeRules.burnRate(air, raining);
            }
            if (burn <= 0) {
                if (used < queue.size()) {
                    burn += queue.get(used++).duration();
                }
                if (burn <= 0) {
                    lit = false;
                }
            }
            if (air > 0) {
                air--;
            }
        }
        return ticks;
    }
}
