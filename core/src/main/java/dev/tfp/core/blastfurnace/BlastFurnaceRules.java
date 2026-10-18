/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/BlastFurnaceBlockEntity.java @ c3b4d9536 (serverTick: fuel burn,
 *       extinguish, tuyere damage, temperature, melt of inputs, fluid conversion and drip; consumeFuel; addItemsFromWorld;
 *       updateCachedRecipe; calculateCapacity; insertOrReplaceFluid; popItemsOffOverCapacity; light),
 *       src/main/java/net/dries007/tfc/common/blocks/devices/BlastFurnaceBlock.java @ c3b4d9536 (getChimneyLevels),
 *       src/main/java/net/dries007/tfc/common/recipes/BlastFurnaceRecipe.java @ c3b4d9536 (get by melt, get by fluid),
 *       src/main/java/net/dries007/tfc/config/ServerConfig.java @ c3b4d9536 (blastFurnace* defaults),
 *       src/main/java/net/dries007/tfc/common/fluids/FluidHelpers.java @ c3b4d9536 (transferExact of 1 mB)
 */
package dev.tfp.core.blastfurnace;

import dev.tfp.core.bloomery.BloomeryRules;
import dev.tfp.core.cooking.FirepitRules;
import dev.tfp.core.crucible.CrucibleRecipe;
import dev.tfp.core.crucible.CrucibleRules;
import dev.tfp.core.fuel.Fuel;
import dev.tfp.core.heat.HeatConfig;
import dev.tfp.core.heat.HeatState;
import java.util.ArrayList;
import java.util.List;

/**
 * The blast furnace rules: fuel burn, bellows air, the device temperature, the melt of inputs into fluid, the fluid
 * conversion and the drip into the container below, plus the intake of world items. Pure: nothing here reads blocks or
 * items, nothing ticks on its own, and nothing draws random numbers.
 *
 * <p><b>Facts in, decisions out.</b> The multiblock check reads blocks, so the caller supplies its result as the chimney
 * levels formed (from the bottom) and the world items as {@link WorldItem}s, with each item's melt already looked up. The
 * rules return the state after a tick and the decisions the caller then applies.
 *
 * <p><b>Live tick, not lazy catch-up.</b> {@link #tick} is one TFC {@code serverTick} with the calendar advancing by one
 * tick. Fuel burns at {@code fuelMultiplier} times the normal rate (twice that with bellows air). TFC's
 * {@code onCalendarUpdate} catch-up does not apply that multiplier, so the lazy form is not a port of this rule and is
 * left open (see the plan entry).
 *
 * <p><b>Slag.</b> TFC's blast furnace makes no slag. Its only outputs are the fluid, dripped into the container below, and
 * the fluid kept in the output tank.
 */
public final class BlastFurnaceRules {
    /** {@code blastFurnaceCapacity} at the TFC default: items per level of chimney. */
    public static final int DEFAULT_CAPACITY_PER_LEVEL = 4;
    /** {@code blastFurnaceMaxChimneyHeight} at the TFC default. */
    public static final int DEFAULT_MAX_CHIMNEY_HEIGHT = 5;
    /** {@code blastFurnaceFluidCapacity} at the TFC default, in mB. */
    public static final int DEFAULT_FLUID_CAPACITY = 10_000;
    /** {@code blastFurnaceFuelConsumptionMultiplier} at the TFC default. */
    public static final int DEFAULT_FUEL_MULTIPLIER = 4;
    /** The fluid that drips into the container below each tick (TFC {@code transferExact(..., 1)}), in mB. */
    public static final int DRIP_AMOUNT = 1;
    /** The device temperature above which bellows air damages the tuyere. */
    public static final float TUYERE_DAMAGE_TEMPERATURE = 400f;
    /** The air tick interval at which the tuyere takes damage. */
    public static final int TUYERE_DAMAGE_INTERVAL = 20;

    private BlastFurnaceRules() {
    }

    /**
     * The config values of the blast furnace.
     *
     * @param capacityPerLevel  items per level of chimney ({@code blastFurnaceCapacity}, at least 1)
     * @param maxChimneyHeight  the most chimney levels that count ({@code blastFurnaceMaxChimneyHeight}, at least 1)
     * @param fluidCapacity     the output tank capacity in mB ({@code blastFurnaceFluidCapacity}, at least 1)
     * @param fuelMultiplier    how much faster fuel burns ({@code blastFurnaceFuelConsumptionMultiplier}, at least 1)
     */
    public record Config(int capacityPerLevel, int maxChimneyHeight, int fluidCapacity, int fuelMultiplier) {
        public static final Config DEFAULT = new Config(DEFAULT_CAPACITY_PER_LEVEL, DEFAULT_MAX_CHIMNEY_HEIGHT,
            DEFAULT_FLUID_CAPACITY, DEFAULT_FUEL_MULTIPLIER);

        public Config {
            if (capacityPerLevel < 1) {
                throw new IllegalArgumentException("capacityPerLevel must be >= 1: " + capacityPerLevel);
            }
            if (maxChimneyHeight < 1) {
                throw new IllegalArgumentException("maxChimneyHeight must be >= 1: " + maxChimneyHeight);
            }
            if (fluidCapacity < 1) {
                throw new IllegalArgumentException("fluidCapacity must be >= 1: " + fluidCapacity);
            }
            if (fuelMultiplier < 1) {
                throw new IllegalArgumentException("fuelMultiplier must be >= 1: " + fuelMultiplier);
            }
        }
    }

    /**
     * The head of the fuel queue, as the rule sees it. {@link #EMPTY} means the queue has no item. {@link #of} with a
     * {@code null} fuel means an item that is in the queue but has no fuel data: TFC removes it and the furnace goes out.
     *
     * @param queued whether an item is at the head of the queue
     * @param fuel   the fuel data of that item, or {@code null} when it has none
     */
    public record FuelHead(boolean queued, Fuel fuel) {
        public static final FuelHead EMPTY = new FuelHead(false, null);

        /** A queued item with the given fuel data (or {@code null} for an item with none). */
        public static FuelHead of(Fuel fuel) {
            return new FuelHead(true, fuel);
        }
    }

    /**
     * The result of one live tick.
     *
     * @param state         the state after the tick
     * @param consumedFuel  whether the head of the fuel queue was taken (the caller removes that item)
     * @param tuyereDamaged whether the tuyere takes one point of damage this tick
     * @param hot           whether the device was hot ({@code temperature > 0} or {@code burnTemperature > 0}) after the
     *                      burn update: the caller runs {@link #melt} on the inputs only when this is set, as TFC does
     */
    public record TickResult(BlastFurnaceState state, boolean consumedFuel, boolean tuyereDamaged, boolean hot) {
    }

    /**
     * The inputs of one melt step: one molten input stack paired with its catalyst. A {@code null} heat means the item has no
     * heat capability, so it is never heated and never melts.
     *
     * @param heat         the item's heat before this tick, or {@code null}
     * @param heatCapacity the item's heat capacity ({@link HeatState#heatCapacity})
     * @param melt         the heating recipe of the item, or {@code null} when it has none
     * @param damage       the item's damage
     * @param maxDamage    the item's maximum damage, or 0 when it cannot wear
     */
    public record Charge(HeatState heat, float heatCapacity, CrucibleRecipe melt, int damage, int maxDamage) {
    }

    /**
     * A fluid amount produced by a melt.
     *
     * @param fluid  the fluid id
     * @param amount the amount in mB
     */
    public record FluidAmount(String fluid, int amount) {
    }

    /**
     * The result of a melt step.
     *
     * @param remaining the charges that did not melt, with their heat updated
     * @param produced  the fluid amounts made, in charge order
     */
    public record MeltResult(List<Charge> remaining, List<FluidAmount> produced) {
    }

    /**
     * A fluid tank as the rules see it: a fluid id and an amount. An amount of 0 is empty.
     *
     * @param fluid  the fluid id, or {@code ""} when empty
     * @param amount the amount in mB, {@code >= 0}
     */
    public record Tank(String fluid, int amount) {
        public static final Tank EMPTY = new Tank("", 0);

        public Tank {
            if (fluid == null) {
                throw new IllegalArgumentException("fluid must not be null");
            }
            if (amount < 0) {
                throw new IllegalArgumentException("amount must be >= 0: " + amount);
            }
        }

        public boolean isEmpty() {
            return amount <= 0;
        }
    }

    /**
     * The result of converting the input tank.
     *
     * @param input  the input tank after the conversion (the remainder of the molten input)
     * @param output the output tank after the conversion
     */
    public record Converted(Tank input, Tank output) {
    }

    /**
     * The result of one drip step.
     *
     * @param output      the output tank after the drip
     * @param transferred whether 1 mB moved into the container below (the caller then heats that container)
     */
    public record Drip(Tank output, boolean transferred) {
    }

    /**
     * An item in the world, in the area above the furnace, as the rules see it.
     *
     * @param item  the item id
     * @param count the stack size, at least 1
     * @param melt  the fluid id the item melts into, or {@code null} when it has no heating recipe
     * @param fuel  whether the item is in the blast furnace fuel tag
     */
    public record WorldItem(String item, int count, String melt, boolean fuel) {
        public WorldItem {
            if (item == null) {
                throw new IllegalArgumentException("item must not be null");
            }
            if (count < 1) {
                throw new IllegalArgumentException("count must be >= 1: " + count);
            }
        }
    }

    /**
     * The intake from the world. Each array is parallel to the world list, and holds how many units of that entry are taken.
     *
     * @param inputs    units of the molten inputs taken
     * @param catalysts units of the catalyst taken
     * @param fuel      units of fuel taken
     */
    public record Intake(int[] inputs, int[] catalysts, int[] fuel) {
        public int inputTotal() {
            return sum(inputs);
        }

        public int catalystTotal() {
            return sum(catalysts);
        }

        public int fuelTotal() {
            return sum(fuel);
        }

        private static int sum(int[] values) {
            int total = 0;
            for (int value : values) {
                total += value;
            }
            return total;
        }
    }

    /**
     * TFC {@code getChimneyLevels}: the leading formed chimney levels, capped at {@code maxHeight}. The same rule as the
     * bloomery, so it is delegated to {@link BloomeryRules#chimneyLevels}.
     *
     * @param levelFormed whether each chimney level, from the bottom (level 1), is formed
     * @param maxHeight   the config maximum
     */
    public static int chimneyLevels(boolean[] levelFormed, int maxHeight) {
        return BloomeryRules.chimneyLevels(levelFormed, maxHeight);
    }

    /** TFC {@code calculateCapacity}: chimney levels times the capacity per level. */
    public static int capacity(int chimneyLevels, int capacityPerLevel) {
        return Math.multiplyExact(chimneyLevels, capacityPerLevel);
    }

    /** Fuel ticks burnt per calendar tick: the multiplier, doubled while bellows air is blowing. */
    public static int burnRate(int airTicks, int fuelMultiplier) {
        return fuelMultiplier * (airTicks > 0 ? 2 : 1);
    }

    /**
     * TFC {@code light}: a furnace with fuel in its queue is lit. Lit furnaces and furnaces without fuel are unchanged.
     *
     * @param state   the state before lighting
     * @param hasFuel whether the fuel queue is not empty
     */
    public static BlastFurnaceState light(BlastFurnaceState state, boolean hasFuel) {
        if (state.lit() || !hasFuel) {
            return state;
        }
        return new BlastFurnaceState(true, state.burnTicks(), state.burnTemperature(), state.airTicks(), state.temperature());
    }

    /**
     * One live tick of TFC's {@code serverTick}, with the calendar advancing by one tick. The order is TFC's: the fuel burn
     * (with air read before it counts down), the extinguish of an unlit furnace that still has heat, the tuyere check and
     * the air count down, then the device temperature (with the air already counted down).
     *
     * @param state  the state at the start of the tick
     * @param head   the head of the fuel queue
     * @param config the blast furnace config
     * @param device the device heating modifier
     * @return the state after the tick, and what the caller must apply
     */
    public static TickResult tick(BlastFurnaceState state, FuelHead head, Config config, FirepitRules.DeviceConfig device) {
        boolean lit = state.lit();
        int burn = state.burnTicks();
        float burnTemperature = state.burnTemperature();
        int air = state.airTicks();
        float temperature = state.temperature();
        boolean consumed = false;

        if (lit) {
            if (burn > 0) {
                burn -= burnRate(air, config.fuelMultiplier());
            }
            if (burn <= 0) {
                // TFC consumeFuel: take the head item. Fuel with data adds its duration and sets its temperature.
                if (head.queued()) {
                    consumed = true;
                    if (head.fuel() != null) {
                        burn += head.fuel().duration();
                        burnTemperature = head.fuel().temperature();
                    }
                }
                if (!consumed || burn <= 0) {
                    lit = false;
                    burn = 0;
                    burnTemperature = 0f;
                }
            }
        } else if (burnTemperature > 0) {
            lit = false;
            burn = 0;
            burnTemperature = 0f;
        }

        boolean tuyereDamaged = false;
        if (air > 0) {
            tuyereDamaged = air % TUYERE_DAMAGE_INTERVAL == 0 && temperature > TUYERE_DAMAGE_TEMPERATURE;
            air--;
        }

        boolean hot = temperature > 0 || burnTemperature > 0;
        if (hot) {
            temperature = FirepitRules.adjustDeviceTemp(temperature, burnTemperature, air, false, (float) device.heatingModifier());
        }
        return new TickResult(new BlastFurnaceState(lit, burn, burnTemperature, air, temperature), consumed, tuyereDamaged, hot);
    }

    /**
     * TFC's melt step of one tick: each charge is heated towards the device temperature (TFC {@code addTemp}), then melts when
     * its temperature reaches its heating recipe (inclusive). A melted charge leaves the list and makes its fluid; the others
     * keep their updated heat. Only called when the device is hot, see {@link TickResult#hot}.
     *
     * @param charges           the charges in slot order
     * @param deviceTemperature the device temperature after this tick's update
     * @param now               the calendar tick
     * @param heatConfig        the item heat settings
     */
    public static MeltResult melt(List<Charge> charges, float deviceTemperature, long now, HeatConfig heatConfig) {
        List<Charge> remaining = new ArrayList<>();
        List<FluidAmount> produced = new ArrayList<>();
        for (Charge charge : charges) {
            if (charge.heat() == null) {
                remaining.add(charge);
                continue;
            }
            HeatState heat = charge.heat().heat(deviceTemperature, now, charge.heatCapacity(), heatConfig);
            float itemTemperature = heat.temperatureAt(now, charge.heatCapacity(), heatConfig);
            CrucibleRules.Melt melted = CrucibleRules.melt(charge.melt(), itemTemperature, charge.damage(), charge.maxDamage());
            if (melted.converts()) {
                produced.add(new FluidAmount(melted.fluid(), melted.amount()));
            } else {
                remaining.add(new Charge(heat, charge.heatCapacity(), charge.melt(), charge.damage(), charge.maxDamage()));
            }
        }
        return new MeltResult(remaining, produced);
    }

    /**
     * TFC {@code insertOrReplaceFluid}: an empty tank takes the new fluid, the same fluid adds to the tank, and a different
     * fluid voids the tank and replaces it.
     */
    public static Tank insertOrReplace(Tank current, String fluid, int amount) {
        if (current.isEmpty()) {
            return new Tank(fluid, amount);
        }
        if (current.fluid().equals(fluid)) {
            return new Tank(fluid, current.amount() + amount);
        }
        return new Tank(fluid, amount);
    }

    /**
     * TFC {@code FluidTank.fill} into the output: a different fluid is refused, and the amount is capped at the capacity
     * (the excess is not stored).
     *
     * @param tank     the output tank
     * @param fluid    the fluid id to add
     * @param amount   the amount to add, in mB
     * @param capacity the tank capacity in mB
     */
    public static Tank fill(Tank tank, String fluid, int amount, int capacity) {
        if (amount <= 0) {
            return tank;
        }
        if (!tank.isEmpty() && !tank.fluid().equals(fluid)) {
            return tank;
        }
        int stored = tank.isEmpty() ? 0 : tank.amount();
        int accepted = Math.min(amount, Math.max(0, capacity - stored));
        if (accepted <= 0) {
            return tank;
        }
        return new Tank(fluid, stored + accepted);
    }

    /**
     * TFC {@code serverTick} fluid step: the melted fluids are inserted into the input tank, and when the input has a recipe,
     * the whole batches of it are converted into the output, with the remainder kept in the input. Nothing happens when
     * nothing melted this tick.
     *
     * @param input         the input tank before the step
     * @param produced      the fluid amounts made this tick, in order
     * @param recipes       the blast furnace recipes
     * @param output        the output tank before the step
     * @param fluidCapacity the output tank capacity in mB
     */
    public static Converted convert(Tank input, List<FluidAmount> produced, List<BlastFurnaceRecipe> recipes, Tank output, int fluidCapacity) {
        if (produced.isEmpty()) {
            return new Converted(input, output);
        }
        Tank in = input;
        for (FluidAmount fluid : produced) {
            in = insertOrReplace(in, fluid.fluid(), fluid.amount());
        }
        BlastFurnaceRecipe recipe = in.isEmpty() ? null : recipeForMelt(recipes, in.fluid());
        if (recipe == null) {
            return new Converted(in, output);
        }
        BlastFurnaceRecipe.Assembly assembled = recipe.assemble(in.amount());
        Tank out = fill(output, assembled.fluid(), assembled.amount(), fluidCapacity);
        return new Converted(new Tank(in.fluid(), assembled.remainder()), out);
    }

    /**
     * TFC's drip into the container below: when the output tank holds fluid and the container accepts a whole 1 mB, 1 mB
     * moves across. Otherwise nothing moves.
     *
     * @param output        the output tank
     * @param belowAccepts  whether the container below accepts {@link #DRIP_AMOUNT} of the output fluid
     */
    public static Drip drip(Tank output, boolean belowAccepts) {
        if (output.isEmpty() || !belowAccepts || output.amount() < DRIP_AMOUNT) {
            return new Drip(output, false);
        }
        return new Drip(new Tank(output.fluid(), output.amount() - DRIP_AMOUNT), true);
    }

    /**
     * The recipe for a molten fluid id, the first in order whose input it is (TFC {@code BlastFurnaceRecipe.get(level, fluid)}).
     *
     * @return the recipe, or {@code null} when the fluid is {@code null}, empty, or has no recipe
     */
    public static BlastFurnaceRecipe recipeForMelt(List<BlastFurnaceRecipe> recipes, String fluid) {
        if (fluid == null || fluid.isEmpty()) {
            return null;
        }
        for (BlastFurnaceRecipe recipe : recipes) {
            if (recipe.matchesInput(fluid)) {
                return recipe;
            }
        }
        return null;
    }

    /**
     * TFC {@code updateCachedRecipe}: the recipe of the first input, or {@code null} when there are no inputs.
     *
     * @param inputMelts the melt fluid id of each input, in slot order, each {@code null} when it has no heating recipe
     */
    public static BlastFurnaceRecipe cachedRecipe(List<BlastFurnaceRecipe> recipes, List<String> inputMelts) {
        return inputMelts.isEmpty() ? null : recipeForMelt(recipes, inputMelts.get(0));
    }

    /**
     * TFC {@code addItemsFromWorld}: whether the inputs must be dumped before intake. This happens when there are inputs and
     * none has a recipe, which is the case after a reload that changed the recipe.
     *
     * @param inputMelts the melt fluid id of each input, in slot order
     * @param cached     the result of {@link #cachedRecipe}
     */
    public static boolean mustDump(List<String> inputMelts, BlastFurnaceRecipe cached) {
        return !inputMelts.isEmpty() && cached == null;
    }

    /**
     * TFC {@code addItemsFromWorld} when no recipe is cached: the first world item whose melt matches a recipe.
     *
     * @return the recipe, or {@code null} when no world item melts into a recipe
     */
    public static BlastFurnaceRecipe worldRecipe(List<BlastFurnaceRecipe> recipes, List<WorldItem> world) {
        for (WorldItem item : world) {
            BlastFurnaceRecipe recipe = recipeForMelt(recipes, item.melt());
            if (recipe != null) {
                return recipe;
            }
        }
        return null;
    }

    /**
     * TFC's intake from the world, for a recipe (call {@link #mustDump}, then {@link #cachedRecipe} or {@link #worldRecipe}
     * first). Items that melt into the recipe's input are inputs, the others that match its catalyst are catalysts, and
     * the rest that are in the fuel tag are fuel. Inputs and catalysts each take up to {@code capacity - inputs} units, and
     * no more than the other set holds. Fuel takes up to {@code capacity - fuel}. Nothing is taken when the inputs and the
     * fuel are both full, or when there is no recipe.
     *
     * @param recipe   the recipe, or {@code null} for no intake
     * @param world    the world items, one entry per stack
     * @param inputs   the number of inputs in the furnace
     * @param catalysts the number of catalysts in the furnace
     * @param fuel     the number of fuel items in the furnace
     * @param capacity the capacity from {@link #capacity}
     */
    public static Intake intake(BlastFurnaceRecipe recipe, List<WorldItem> world, int inputs, int catalysts, int fuel, int capacity) {
        int n = world.size();
        int[] counts = new int[n];
        int[] zeros = new int[n];
        if (recipe == null || (inputs >= capacity && fuel >= capacity)) {
            return new Intake(zeros, zeros.clone(), zeros.clone());
        }
        List<Integer> inputOrder = new ArrayList<>();
        List<Integer> catalystOrder = new ArrayList<>();
        List<Integer> fuelOrder = new ArrayList<>();
        int inputCount = 0;
        int catalystCount = 0;
        int fuelCount = 0;
        for (int i = 0; i < n; i++) {
            WorldItem item = world.get(i);
            counts[i] = item.count();
            if (item.melt() != null && recipe.matchesInput(item.melt())) {
                inputOrder.add(i);
                inputCount += item.count();
            } else if (recipe.matchesCatalyst(item.item())) {
                catalystOrder.add(i);
                catalystCount += item.count();
            } else if (item.fuel()) {
                fuelOrder.add(i);
                fuelCount += item.count();
            }
        }
        int pairs = Math.max(0, Math.min(capacity - inputs, Math.min(inputCount, catalystCount)));
        int fuelRoom = Math.max(0, Math.min(capacity - fuel, fuelCount));
        return new Intake(
            takeIndividually(inputOrder, counts, pairs, n),
            takeIndividually(catalystOrder, counts, pairs, n),
            takeIndividually(fuelOrder, counts, fuelRoom, n));
    }

    /** Takes up to {@code limit} units from the listed entries in order, one entry after another. */
    private static int[] takeIndividually(List<Integer> order, int[] counts, int limit, int size) {
        int[] taken = new int[size];
        int left = limit;
        for (int idx : order) {
            if (left <= 0) {
                break;
            }
            int units = Math.min(left, counts[idx]);
            taken[idx] = units;
            left -= units;
        }
        return taken;
    }
}
