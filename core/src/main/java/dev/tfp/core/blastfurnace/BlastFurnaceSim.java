/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/BlastFurnaceBlockEntity.java @ c3b4d9536 (serverTick: the burn,
 *       the tuyere damage, the temperature, the heating and melting of the inputs, the fluid conversion)
 */
package dev.tfp.core.blastfurnace;

import dev.tfp.core.blastfurnace.BlastFurnaceRules.Charge;
import dev.tfp.core.blastfurnace.BlastFurnaceRules.FuelHead;
import dev.tfp.core.blastfurnace.BlastFurnaceRules.MeltResult;
import dev.tfp.core.blastfurnace.BlastFurnaceRules.Tank;
import dev.tfp.core.blastfurnace.BlastFurnaceRules.TickResult;
import dev.tfp.core.cooking.FirepitRules;
import dev.tfp.core.fuel.Fuel;
import dev.tfp.core.heat.HeatConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * PAR-METAL-15: the blast furnace's {@code serverTick} as a lazy replay. {@link #advance} runs TFC's live tick (the rules of
 * {@link BlastFurnaceRules#tick}, {@link BlastFurnaceRules#melt} and {@link BlastFurnaceRules#convert}) for every tick from
 * the last update to now, so the result is exactly the stepped one; splitting a span in two gives the same result as one
 * span. The caller (the device) reads the stacks into {@link Charge}s and writes the result back.
 *
 * <p><b>Cost.</b> A tick is one rule step and one heating step per charge, and the replay ends as soon as the furnace is
 * cold, unlit and out of air (only the air still runs down, which is arithmetic). A furnace can only be hot while its fuel
 * lasts (about 450 ticks per charcoal at the default multiplier) plus the cool-down (about a thousand ticks), so the number of
 * steps per call is bounded by the contents, whatever the length of the gap.
 *
 * <p><b>Drip.</b> The drip into the container below needs the world, so the replay only records the tick the output
 * started to drip from ({@link BlastFurnaceData#dripTick}); the device drips at most one mB per tick since then.
 *
 * <p><b>Offline time.</b> TFC's {@code onCalendarUpdate} catch-up burns the fuel at one tick per tick, without the config
 * multiplier or the bellows air, and does not melt anything. The replay uses the live rules over the gap instead, so a
 * furnace that stood unloaded burns, melts and cools as if it had been loaded (ledger divergence).
 *
 * <p>Pure and thread-safe.
 */
public final class BlastFurnaceSim {
    private BlastFurnaceSim() {
    }

    /**
     * The state after a span.
     *
     * @param data          the furnace after the span
     * @param fuelUsed      how many items of the fuel queue were taken (the head ones)
     * @param tuyereDamage  how many points of damage the tuyere took
     * @param charges       the charges that did not melt, in their original order, with their updated heat
     * @param melted        the indices (in the list given to {@link #advance}) of the charges that melted, ascending
     */
    public record Result(BlastFurnaceData data, int fuelUsed, int tuyereDamage, List<Charge> charges, List<Integer> melted) {
        public Result {
            charges = List.copyOf(charges);
            melted = List.copyOf(melted);
        }
    }

    /**
     * The furnace after the ticks {@code fromTick + 1 .. toTick}, as TFC's {@code serverTick} would leave it.
     *
     * @param data        the furnace at {@code fromTick}
     * @param fromTick    the tick of the last update
     * @param toTick      the tick to replay up to (not before {@code fromTick})
     * @param queue       the fuel items in queue order; an entry may be {@code null} for an item with no fuel data
     * @param tuyere      whether a tuyere is in its slot (the air damages it)
     * @param charges     the input stacks with their catalysts one to one: the heat of each input
     * @param recipes     the blast furnace recipes
     * @param config      the blast furnace config
     * @param device      the device heating modifier
     * @param heat        the item heating and cooling settings
     */
    public static Result advance(BlastFurnaceData data, long fromTick, long toTick, List<Fuel> queue, boolean tuyere,
            List<Charge> charges, List<BlastFurnaceRecipe> recipes, BlastFurnaceRules.Config config,
            FirepitRules.DeviceConfig device, HeatConfig heat) {
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(queue, "queue");
        Objects.requireNonNull(charges, "charges");
        Objects.requireNonNull(recipes, "recipes");
        BlastFurnaceState state = data.state();
        Tank input = data.input();
        Tank output = data.output();
        long dripTick = data.dripTick();
        List<Integer> alive = new ArrayList<>(charges.size());
        List<Charge> current = new ArrayList<>(charges);
        for (int i = 0; i < charges.size(); i++) {
            alive.add(i);
        }
        List<Integer> melted = new ArrayList<>();
        int used = 0;
        int damage = 0;
        long t = fromTick;
        while (t < toTick) {
            if (!state.lit() && state.burnTemperature() == 0f && state.temperature() == 0f) {
                // cold and unlit: only the air runs down, the charges only cool (their own records)
                int air = (int) Math.max(0L, state.airTicks() - (toTick - t));
                state = new BlastFurnaceState(false, 0, 0f, air, 0f);
                t = toTick;
                break;
            }
            t++;
            FuelHead head = used < queue.size() ? FuelHead.of(queue.get(used)) : FuelHead.EMPTY;
            TickResult tick = BlastFurnaceRules.tick(state, head, config, device);
            if (tick.consumedFuel()) {
                used++;
            }
            if (tick.tuyereDamaged() && tuyere) {
                damage++;
            }
            state = tick.state();
            if (!tick.hot()) {
                continue;
            }
            List<BlastFurnaceRules.FluidAmount> produced = new ArrayList<>();
            for (int k = 0; k < current.size(); ) {
                MeltResult r = BlastFurnaceRules.melt(List.of(current.get(k)), state.temperature(), t, heat);
                if (r.remaining().isEmpty()) {
                    produced.addAll(r.produced());
                    melted.add(alive.remove(k));
                    current.remove(k);
                } else {
                    current.set(k, r.remaining().getFirst());
                    k++;
                }
            }
            boolean wasEmpty = output.isEmpty();
            BlastFurnaceRules.Converted converted = BlastFurnaceRules.convert(input, produced, recipes, output, config.fluidCapacity());
            input = converted.input();
            output = converted.output();
            if (wasEmpty && !output.isEmpty()) {
                dripTick = t; // the output starts to drip from this tick (1 mB per tick into the container below)
            }
        }
        melted.sort(Integer::compare);
        BlastFurnaceData next = new BlastFurnaceData(state, input, output, dripTick);
        return new Result(next, used, damage, current, melted);
    }
}
