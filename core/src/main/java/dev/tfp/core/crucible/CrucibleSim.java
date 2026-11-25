/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/CrucibleBlockEntity.java @ c3b4d9536 (serverTick: the
 *       temperature step, the heating and melting of the inputs),
 *       src/main/java/net/dries007/tfc/common/component/heat/HeatCapability.java @ c3b4d9536 (addTemp)
 */
package dev.tfp.core.crucible;

import dev.tfp.core.alloy.FluidAlloy;
import dev.tfp.core.cooking.FirepitRules;
import dev.tfp.core.heat.HeatConfig;
import dev.tfp.core.heat.HeatState;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * PAR-METAL-12: TFC's crucible {@code serverTick} replayed lazily over a span of ticks. Pure, any thread.
 *
 * <p>Per tick, in TFC's order: the heat source below sets the temperature (a {@link Run} covering the tick, TFC
 * {@code setTemperature}: only upward, and it holds the target for 5 ticks), the crucible's own temperature steps toward
 * its target and the target decays, then each input slot is heated toward the crucible ({@code addTemp} with
 * {@code 2 + temperature x 0.0025}) and melts when its recipe temperature is reached. A melt stops the replay at that tick
 * ({@link Result#conversions}): the glue replaces the melted items (a recipe may leave an item) and replays the rest.
 *
 * <p>Once nothing is hot and nothing is held, the replay ends early: items only cool, which their own heat record covers
 * lazily. With no input items at all, the temperature uses the closed-form {@link CrucibleTemperature#catchUp}.
 */
public final class CrucibleSim {
    private CrucibleSim() {
    }

    /** The heat source gave {@code temperature} to the crucible on every tick of {@code fromTick..toTick}, inclusive. */
    public record Run(long fromTick, long toTick, float temperature) {
        public Run {
            if (toTick < fromTick) {
                throw new IllegalArgumentException("toTick before fromTick: " + this);
            }
        }

        boolean covers(long tick) {
            return tick >= fromTick && tick <= toTick;
        }
    }

    /**
     * One input slot for the replay.
     *
     * @param heat      the item's heat record
     * @param capacity  the item's heat capacity
     * @param recipe    the heating recipe the item matches, or {@code null} when it never melts
     * @param damage    the item's damage
     * @param maxDamage the item's maximum damage, 0 when it cannot wear
     */
    public record Input(HeatState heat, float capacity, CrucibleRecipe recipe, int damage, int maxDamage) {
        public Input {
            Objects.requireNonNull(heat, "heat");
        }
    }

    /** One slot that melted at {@link Result#stoppedAt()}: the fluid it made and what was accepted into the alloy. */
    public record Conversion(int slot, String fluid, int made, int accepted) {
    }

    /**
     * The result of a replay.
     *
     * @param data        the crucible after the replay (the alloy includes the conversions)
     * @param heats       the heat record of every input slot after the replay, {@code null} for an empty slot
     * @param stoppedAt   the tick the replay stopped at: the end of the span, or the tick items melted
     * @param conversions the slots that melted at {@code stoppedAt}, in slot order; empty when the span ended
     */
    public record Result(CrucibleData data, List<HeatState> heats, long stoppedAt, List<Conversion> conversions) {
        public boolean finished() {
            return conversions.isEmpty();
        }
    }

    /**
     * Replays ticks {@code from + 1 .. to}.
     *
     * @param data     the crucible at {@code from}
     * @param from     the last tick already applied
     * @param to       the last tick to apply
     * @param runs     the heat runs, any order
     * @param inputs   the nine input slots, {@code null} for an empty slot
     * @param capacity the crucible capacity in mB
     */
    public static Result advance(CrucibleData data, long from, long to, List<Run> runs, List<Input> inputs, int capacity,
            FirepitRules.DeviceConfig device, HeatConfig heat) {
        if (inputs.size() != CrucibleRules.INPUT_SLOTS) {
            throw new IllegalArgumentException("nine inputs expected: " + inputs.size());
        }
        CrucibleTemperature temperature = data.temperature();
        FluidAlloy alloy = data.alloy();
        List<HeatState> heats = new ArrayList<>(Arrays.asList(new HeatState[CrucibleRules.INPUT_SLOTS]));
        boolean anyItem = false;
        for (int i = 0; i < inputs.size(); i++) {
            Input in = inputs.get(i);
            if (in != null) {
                heats.set(i, in.heat());
                anyItem = true;
            }
        }
        long lastRun = Long.MIN_VALUE;
        for (Run r : runs) {
            lastRun = Math.max(lastRun, r.toTick());
        }
        long t = from;
        while (t < to) {
            if (!anyItem && lastRun <= t) {
                // nothing to heat and no source left: the closed form of the remaining ticks
                temperature = temperature.catchUp(to - t, device);
                t = to;
                break;
            }
            t++;
            for (Run r : runs) {
                if (r.covers(t)) {
                    temperature = temperature.setTemperature(r.temperature());
                }
            }
            temperature = temperature.tick(device);
            if (!anyItem) {
                continue;
            }
            float degrees = temperature.temperature();
            if (degrees <= 0f && temperature.target() <= 0f && lastRun <= t) {
                // cold, nothing holds it: the items only cool from here, which their records give lazily
                t = to;
                break;
            }
            List<Conversion> melted = new ArrayList<>(0);
            for (int slot = 0; slot < inputs.size(); slot++) {
                Input in = inputs.get(slot);
                HeatState h = heats.get(slot);
                if (in == null || h == null) {
                    continue;
                }
                h = h.heat(degrees, CrucibleRules.itemHeatModifier(degrees), t, in.capacity(), heat);
                heats.set(slot, h);
                CrucibleRules.Melt m = CrucibleRules.melt(in.recipe(), h.temperatureAt(t, in.capacity(), heat), in.damage(),
                    in.maxDamage());
                if (m.converts()) {
                    FluidAlloy.Fill fill = m.amount() > 0 && !m.fluid().isEmpty()
                        ? CrucibleRules.fill(alloy, m.fluid(), m.amount(), capacity) : new FluidAlloy.Fill(alloy, 0);
                    alloy = fill.alloy();
                    melted.add(new Conversion(slot, m.fluid(), m.amount(), fill.accepted()));
                }
            }
            if (!melted.isEmpty()) {
                return new Result(new CrucibleData(temperature, alloy, data.pour()), heats, t, List.copyOf(melted));
            }
        }
        return new Result(new CrucibleData(temperature, alloy, data.pour()), heats, to, List.of());
    }
}
