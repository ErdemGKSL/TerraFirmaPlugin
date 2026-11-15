/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/PotBlockEntity.java @ c3b4d9536 (handleCooking, isBoiling,
 *       coolInstantly),
 *       src/main/java/net/dries007/tfc/common/blockentities/AbstractFirepitBlockEntity.java @ c3b4d9536 (serverTick: the
 *       firepit part, then handleCooking with the new temperature),
 *       src/main/java/net/dries007/tfc/common/recipes/PotRecipe.java @ c3b4d9536 (isHotEnough: tempIn > temperature)
 */
package dev.tfp.core.pot;

import dev.tfp.core.cooking.FirepitRules;
import dev.tfp.core.cooking.PotRules;
import dev.tfp.core.firepit.FirepitData;
import dev.tfp.core.firepit.FirepitSim;
import dev.tfp.core.fuel.Fuel;
import dev.tfp.core.heat.HeatConfig;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.random.RandomGenerator;

/**
 * The pot's {@code serverTick} as a lazy catch-up (PAR-HEAT-09): the firepit part is {@link FirepitSim} (burn, fuel,
 * smoke, ash, device temperature), and after it on every tick TFC's {@code handleCooking} with that tick's temperature:
 * the pot boils while it has a recipe, no output, and a temperature above the recipe's ({@code isBoiling}); the counters
 * are {@link PotRules}. The replay stops at the tick the recipe finishes, so the caller can make the output and pick the
 * next recipe from the changed contents, then continue from there.
 *
 * <p><b>Cost.</b> Without a recipe (or with an output waiting) nothing can boil, and the firepit part runs as one
 * {@link FirepitSim#advance} (it stops itself once cold). With a recipe the firepit is stepped one tick at a time until
 * the recipe finishes or the firepit is out and cold (it cannot boil again before something changes), so the replay is
 * bounded by the burn time of the fuel in the four fuel slots plus the recipe, whatever the gap.
 *
 * <p>Pure and thread-safe.
 */
public final class PotSim {
    /** TFC's pot slots: fuel 0..3 (as the firepit), the ingredients 4..8 ({@code SLOT_EXTRA_INPUT_START..END}). */
    public static final int SLOT_INPUT_START = 4;
    public static final int SLOT_INPUT_END = 8;
    public static final int SLOTS = 9;
    /** The tick of no finished recipe. */
    public static final long NOT_FINISHED = Long.MIN_VALUE;

    private PotSim() {
    }

    /** TFC {@code PotBlockEntity.getSlotStackLimit}: every slot holds one item. */
    public static int slotLimit(int slot) {
        return 1;
    }

    /**
     * The recipe the pot holds (TFC {@code cachedRecipe}).
     *
     * @param temperature the recipe's temperature: the pot boils above it ({@code isHotEnough})
     * @param duration    the boil ticks after the pre-boil ({@code getDuration})
     */
    public record Boil(float temperature, int duration) {
        public Boil {
            if (duration < 0) {
                throw new IllegalArgumentException("duration must be >= 0: " + duration);
            }
        }
    }

    /**
     * The state after a span.
     *
     * @param data       the pot at the end of the span, or at the tick the recipe finished (the counters reset there)
     * @param consumed   how many fuel queue entries were taken
     * @param finishedAt the tick the recipe finished ({@code handleCooking} made the output), or {@link #NOT_FINISHED}
     */
    public record Result(PotData data, int consumed, long finishedAt) {
        public boolean finished() {
            return finishedAt != NOT_FINISHED;
        }
    }

    /**
     * The pot after the ticks {@code fromTick + 1 .. toTick}, or up to the tick its recipe finishes.
     *
     * @param queue  the fuel queue, as {@link FirepitSim#advance}
     * @param recipe the recipe the contents match now, or {@code null} for none
     * @param random ash draws
     */
    public static Result advance(PotData data, long fromTick, long toTick, List<Optional<Fuel>> queue, Boil recipe,
            RandomGenerator random, FirepitRules.DeviceConfig device, HeatConfig heatConfig) {
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(queue, "queue");
        if (toTick <= fromTick) {
            return new Result(data, 0, NOT_FINISHED);
        }
        PotRules.PotState counters = data.counters();
        if (recipe == null || data.hasOutput()) {
            // isBoiling is false on every tick: the first tick resets the counters if boiling had started
            FirepitSim.Result r = FirepitSim.advance(data.firepit(), fromTick, toTick, queue, null, random, device, heatConfig);
            PotRules.PotState c = PotRules.advance(counters, toTick - fromTick, false, 0).state();
            return new Result(data.withFirepit(r.data()).withCounters(c), r.consumed(), NOT_FINISHED);
        }
        FirepitData f = data.firepit();
        int used = 0;
        long t = fromTick;
        while (t < toTick) {
            if (cold(f) && !(0f > recipe.temperature())) {
                // out and cold: no tick of the rest boils; the firepit part (smoke decay) in one go
                FirepitSim.Result r = FirepitSim.advance(f, t, toTick, rest(queue, used), null, random, device, heatConfig);
                counters = PotRules.advance(counters, toTick - t, false, recipe.duration()).state();
                return new Result(data.withFirepit(r.data()).withCounters(counters), used + r.consumed(), NOT_FINISHED);
            }
            FirepitSim.Result r = FirepitSim.advance(f, t, t + 1, rest(queue, used), null, random, device, heatConfig);
            used += r.consumed();
            f = r.data();
            t++;
            boolean boiling = f.temperature() > recipe.temperature(); // isHotEnough(temperature)
            PotRules.Advance a = PotRules.advance(counters, 1, boiling, recipe.duration());
            counters = a.state();
            if (a.finishedAtTick() > 0) {
                return new Result(data.withFirepit(f).withCounters(counters), used, t);
            }
        }
        return new Result(data.withFirepit(f).withCounters(counters), used, NOT_FINISHED);
    }

    /** TFC {@code coolInstantly}: the counters reset. */
    public static PotData coolInstantly(PotData data) {
        return data.withCounters(PotRules.PotState.IDLE);
    }

    /** TFC {@code isBoiling} with the device temperature now. */
    public static boolean isBoiling(PotData data, Boil recipe) {
        return recipe != null && !data.hasOutput() && data.temperature() > recipe.temperature();
    }

    /** TFC {@code hasRecipeStarted}: boiling and past the pre-boil, so the inputs and the tank are locked. */
    public static boolean hasRecipeStarted(PotData data, Boil recipe) {
        return isBoiling(data, recipe) && data.counters().preBoilingTicks() >= PotRules.PRE_BOIL_TIME;
    }

    /** Unlit, no bellows air and at 0 C: the firepit part cannot change any more by itself. */
    private static boolean cold(FirepitData f) {
        return !f.lit() && f.heat().airTicks() == 0 && f.temperature() == 0f;
    }

    private static List<Optional<Fuel>> rest(List<Optional<Fuel>> queue, int used) {
        return used == 0 ? queue : queue.subList(Math.min(used, queue.size()), queue.size());
    }
}
