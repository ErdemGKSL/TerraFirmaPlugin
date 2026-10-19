/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/CharcoalForgeBlockEntity.java @ c3b4d9536 (serverTick: burn,
 *       extinguish, heat level, temperature update; onCalendarUpdate; light; consumeFuel; extinguish; onFirstCreation;
 *       intakeAir; handleInputMelting heat step),
 *       src/main/java/net/dries007/tfc/common/blocks/devices/CharcoalForgeBlock.java @ c3b4d9536 (HEAT, insulation tag),
 *       src/main/java/net/dries007/tfc/common/component/heat/HeatCapability.java @ c3b4d9536 (addTemp, consumeFuelForTicks)
 */
package dev.tfp.core.charcoalforge;

import dev.tfp.core.cooking.FirepitRules;
import dev.tfp.core.cooking.FirepitState;
import dev.tfp.core.fuel.Fuel;
import dev.tfp.core.fuel.FuelBurn;
import dev.tfp.core.heat.HeatConfig;
import dev.tfp.core.heat.HeatLevel;
import dev.tfp.core.heat.HeatState;
import java.util.List;

/**
 * The charcoal forge rules: fuel burn, bellows air, the forge temperature, the lit heat level, the item heat step and
 * the calendar catch-up. Pure: the caller reads the world (rain, the multiblock, the insulation, the fuel slots, the
 * items in the input slots) and removes the fuel items the rules report as consumed.
 *
 * <p><b>Live rule.</b> {@link #step} is one TFC {@code serverTick} for the burn and the temperature. It runs once per
 * calendar tick: the caller passes the calendar tick as the tick, since TFC only burns while the calendar advances.
 *
 * <p><b>Lazy rule.</b> {@link #advance} gives the same state as stepping over a span of ticks, and {@link #catchUp} is
 * TFC's {@code onCalendarUpdate}, which burns the fuel only and does not move the temperature. The temperature curve
 * is therefore live-only in TFC, and the lazy form matches it tick for tick.
 *
 * <p><b>Divergence from the firepit.</b> A forge that goes out keeps its bellows air, because TFC's forge
 * {@code extinguish} clears only the burn and the burn temperature. The firepit's extinguish clears the air too, and
 * {@link FirepitRules#step} follows that. Here the air is kept, so the air count keeps running down after the forge is
 * out.
 *
 * <p><b>No randomness.</b> Nothing here draws random numbers. The rain sound is presentation and is left out.
 */
public final class CharcoalForgeRules {
    /** {@code CharcoalForgeBlockEntity.onFirstCreation}: the burn ticks a freshly built forge starts with. */
    public static final int FIRST_CREATION_BURN_TICKS = 200;
    /** {@code CharcoalForgeBlockEntity.onFirstCreation}: the burn temperature a freshly built forge starts with. */
    public static final float FIRST_CREATION_TEMPERATURE = 500f;
    /** The highest heat level the forge block shows ({@code CharcoalForgeBlock.HEAT} runs 0 through 7). */
    public static final int MAX_HEAT_LEVEL = 7;

    private CharcoalForgeRules() {
    }

    /**
     * The result of one live tick.
     *
     * @param state        the state after the tick
     * @param consumedFuel whether the next fuel item was taken (the caller removes it from the fuel slots)
     * @param heatLevel    the {@code HEAT} block property after the tick: 0 when unlit, otherwise {@link #heatLevel} of the
     *                     temperature at the start of the tick, as TFC sets it before the temperature moves
     */
    public record Step(CharcoalForgeState state, boolean consumedFuel, int heatLevel) {
    }

    /**
     * The result of a span of calendar ticks.
     *
     * @param state    the state at the end of the span
     * @param consumed how many items from the front of the queue were taken
     */
    public record Advance(CharcoalForgeState state, int consumed) {
    }

    /**
     * The result of the calendar catch-up ({@code onCalendarUpdate}).
     *
     * @param state        the state after the catch-up
     * @param consumed     how many items from the front of the queue were taken
     * @param extinguished whether the fuel ran out in the span: the forge is out, and the caller cools the input items
     *                     to 0 (TFC {@code setTemperature(stack, 0)})
     */
    public record CatchUp(CharcoalForgeState state, int consumed, boolean extinguished) {
    }

    /**
     * The result of lighting the forge.
     *
     * @param state        the state after the attempt
     * @param consumedFuel whether a fuel item was taken, even when it did not light the forge
     * @param lit          the return value of TFC {@code light}: true when the forge is burning now or was lit
     */
    public record Light(CharcoalForgeState state, boolean consumedFuel, boolean lit) {
    }

    /**
     * TFC {@code serverTick} burn rate: fuel burns twice as fast with bellows air or in the rain, otherwise once per tick.
     *
     * @param airTicks the air ticks read before they count down this tick
     * @param raining  whether it rains on the forge (the rain is read at the forge's top, as TFC does)
     */
    public static int burnRate(int airTicks, boolean raining) {
        return airTicks > 0 || raining ? 2 : 1;
    }

    /**
     * TFC's {@code HEAT} property for a temperature: {@code clamp((int) (t / 1600 x 6) + 1, 1, 7)}.
     *
     * @param temperature the forge temperature in C
     */
    public static int heatLevel(float temperature) {
        int level = (int) (temperature / HeatLevel.MAX_VISIBLE * 6) + 1;
        return Math.max(1, Math.min(MAX_HEAT_LEVEL, level));
    }

    /**
     * A temperature that {@link #heatLevel} maps back to {@code level}: 0 for level 0 (unlit), the middle of the level's
     * band for levels 1 to 6 and the visible maximum for level 7. A stand-in for a forge whose block entity does not keep
     * the real temperature yet; {@code heatLevel(levelTemperature(n)) == n} for every level.
     *
     * @param level the {@code HEAT} block property, 0 through {@link #MAX_HEAT_LEVEL}
     */
    public static float levelTemperature(int level) {
        if (level <= 0) {
            return 0f;
        }
        if (level >= MAX_HEAT_LEVEL) {
            return HeatLevel.MAX_VISIBLE;
        }
        return (level - 0.5f) * HeatLevel.MAX_VISIBLE / 6f;
    }

    /**
     * One live tick of the burn and temperature part of TFC {@code serverTick}, with the calendar advancing by one tick.
     *
     * @param state   the state at the start of the tick
     * @param raining whether it rains on the forge
     * @param next    the next fuel item in the slots, or {@code null} for none
     * @param config  the device settings (the device heating modifier)
     */
    public static Step step(CharcoalForgeState state, boolean raining, Fuel next, FirepitRules.DeviceConfig config) {
        boolean lit = state.lit();
        int burn = state.burnTicks();
        float burnTemperature = state.burnTemperature();
        int air = state.airTicks();
        boolean consumed = false;
        if (lit) {
            if (burn > 0) {
                burn -= burnRate(air, raining);
            }
            if (burn <= 0) {
                // consumeFuel: takes the next fuel item, and adds its duration to whatever is left of the burn.
                if (next != null) {
                    consumed = true;
                    burn += next.duration();
                    burnTemperature = next.temperature();
                }
                if (burn <= 0) {
                    // extinguish: the burn and the burn temperature clear, the air stays.
                    lit = false;
                    burn = 0;
                    burnTemperature = 0f;
                }
            }
        } else if (burnTemperature > 0) {
            // An unlit forge that still has a burn temperature (its check failed) is extinguished.
            lit = false;
            burn = 0;
            burnTemperature = 0f;
        }
        // The heat level is set from the temperature before it moves, and is 0 once the forge is out.
        int heat = lit ? heatLevel(state.temperature()) : 0;
        if (air > 0) {
            air--;
        }
        float temperature = state.temperature();
        if (temperature > 0 || burnTemperature > 0) {
            temperature = FirepitRules.adjustDeviceTemp(temperature, burnTemperature, air, raining, (float) config.heatingModifier());
        }
        return new Step(new CharcoalForgeState(lit, burn, burnTemperature, temperature, air), consumed, heat);
    }

    /**
     * The lazy form: the state after {@code ticks} calendar ticks, equal to stepping {@link #step} that many times.
     *
     * <p>While the bellows air runs, or while the forge is in a state the closed form does not cover (unlit with a burn
     * temperature, or lit with no burn ticks), the ticks are stepped. Once the forge is lit with burn ticks and no air,
     * the rest is the firepit's closed form, which is the same burn and temperature rule with no air.
     *
     * @param state   the state at the start of the span
     * @param ticks   the span, {@code >= 0}
     * @param raining whether it rains over the whole span (the weather is constant over the span)
     * @param queue   the fuel items in slot order, only the fuel ones (non-fuel stacks are not in the queue)
     * @param config  the device settings
     */
    public static Advance advance(CharcoalForgeState state, long ticks, boolean raining, List<Fuel> queue, FirepitRules.DeviceConfig config) {
        if (ticks < 0) {
            throw new IllegalArgumentException("ticks must be >= 0: " + ticks);
        }
        CharcoalForgeState cur = state;
        long left = ticks;
        int used = 0;
        while (left > 0 && !closedFormCovers(cur)) {
            Fuel next = used < queue.size() ? queue.get(used) : null;
            Step step = step(cur, raining, next, config);
            if (step.consumedFuel()) {
                used++;
            }
            cur = step.state();
            left--;
        }
        if (left == 0) {
            return new Advance(cur, used);
        }
        if (!cur.lit()) {
            // Unlit with no burn temperature: the temperature only cools towards 0.
            FirepitRules.Advance cooled = FirepitRules.advance(new FirepitState(0, 0f, cur.temperature(), 0), left, raining, List.of(), config);
            return new Advance(new CharcoalForgeState(false, 0, 0f, cooled.state().temperature(), 0), used);
        }
        List<Fuel> rest = queue.subList(Math.min(used, queue.size()), queue.size());
        FirepitRules.Advance burning = FirepitRules.advance(
            new FirepitState(cur.burnTicks(), cur.burnTemperature(), cur.temperature(), 0), left, raining, rest, config);
        FirepitState end = burning.state();
        boolean lit = end.burnTicks() > 0;
        return new Advance(new CharcoalForgeState(lit, end.burnTicks(), end.burnTemperature(), end.temperature(), 0), used + burning.consumed());
    }

    /**
     * TFC {@code onCalendarUpdate}: burns the lit forge's fuel for {@code ticks} calendar ticks, with no air and no rain,
     * and no temperature movement. When the fuel runs out in the span the forge is extinguished.
     *
     * @param state the state at the start of the span
     * @param ticks calendar ticks since the last update; a non-positive span changes nothing
     * @param queue the fuel items in slot order, only the fuel ones
     */
    public static CatchUp catchUp(CharcoalForgeState state, long ticks, List<Fuel> queue) {
        if (ticks <= 0 || !state.lit()) {
            return new CatchUp(state, 0, false);
        }
        FuelBurn.Remainder remainder = FuelBurn.consume(ticks, state.burnTicks(), state.burnTemperature(), queue);
        if (remainder.ticks() > 0) {
            return new CatchUp(new CharcoalForgeState(false, 0, 0f, state.temperature(), state.airTicks()), remainder.consumed(), true);
        }
        return new CatchUp(
            new CharcoalForgeState(true, remainder.burnTicks(), remainder.burnTemperature(), state.temperature(), state.airTicks()),
            remainder.consumed(),
            false);
    }

    /**
     * TFC {@code light}: lights the forge from its fuel. A forge that still has burn ticks is already burning and only
     * reports success. Otherwise the next fuel item is taken (even one that does not burn), and the forge is lit when the
     * burn ticks are positive afterwards.
     *
     * @param state the state before the attempt
     * @param next  the next fuel item in the slots, or {@code null} for none
     */
    public static Light light(CharcoalForgeState state, Fuel next) {
        if (state.burnTicks() > 0) {
            return new Light(state, false, true);
        }
        if (next == null) {
            return new Light(state, false, false);
        }
        int burn = state.burnTicks() + next.duration();
        if (burn > 0) {
            return new Light(new CharcoalForgeState(true, burn, next.temperature(), state.temperature(), state.airTicks()), true, true);
        }
        // Fuel taken with no burn time: the forge stays as it was, with the new burn temperature.
        return new Light(new CharcoalForgeState(state.lit(), 0, next.temperature(), state.temperature(), state.airTicks()), true, false);
    }

    /**
     * TFC {@code onFirstCreation}: the state of a forge built on a charcoal pile: lit, with the first burn and its
     * temperature, a cold device and no air.
     */
    public static CharcoalForgeState firstCreation() {
        return new CharcoalForgeState(true, FIRST_CREATION_BURN_TICKS, FIRST_CREATION_TEMPERATURE, 0f, 0);
    }

    /**
     * TFC {@code intakeAir}: a bellows push adds its air to the forge, capped at the device maximum.
     *
     * @param state  the state before the push
     * @param amount the air ticks to add
     */
    public static CharcoalForgeState intakeAir(CharcoalForgeState state, int amount) {
        return new CharcoalForgeState(state.lit(), state.burnTicks(), state.burnTemperature(), state.temperature(),
            BellowsRules.intakeAir(state.airTicks(), amount));
    }

    /**
     * The item heat step of TFC {@code serverTick} for one input item: {@code addTemp(item, forgeTemperature)}, with the
     * default modifier. The caller applies it to each input item that has heat.
     *
     * @param item              the item's heat at {@code now}
     * @param forgeTemperature  the forge temperature (the target)
     * @param heatCapacity      the item's heat capacity, from {@link HeatState#heatCapacity}
     * @param now               the calendar tick of the step
     * @param config            the item heat settings
     */
    public static HeatState heatInput(HeatState item, float forgeTemperature, float heatCapacity, long now, HeatConfig config) {
        return item.heat(forgeTemperature, now, heatCapacity, config);
    }

    /** Whether the closed form of {@link #advance} covers this state: lit with burn ticks and no air, or unlit with no burn temperature. */
    private static boolean closedFormCovers(CharcoalForgeState state) {
        if (state.airTicks() > 0) {
            return false;
        }
        return state.lit() ? state.burnTicks() > 0 : state.burnTemperature() == 0f;
    }
}
