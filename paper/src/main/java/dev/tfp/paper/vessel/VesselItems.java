/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/component/mold/Vessel.java @ c3b4d9536 (isInventory, isMolten, setTemperature
 *       with updateInventoryOnMelt, updateHeatCapacity),
 *       src/main/java/net/dries007/tfc/common/items/VesselItem.java @ c3b4d9536 (canContainItem, slot capacity)
 */
package dev.tfp.paper.vessel;

import dev.tfp.core.heat.HeatConfig;
import dev.tfp.core.heat.HeatState;
import dev.tfp.core.vessel.Alloy;
import dev.tfp.core.vessel.FluidAmount;
import dev.tfp.core.vessel.Melt;
import dev.tfp.core.vessel.VesselRules;
import dev.tfp.core.vessel.VesselSlot;
import dev.tfp.core.vessel.VesselState;
import dev.tfp.core.fluid.TfcFluids;
import dev.tfp.core.metal.Metal;
import dev.tfp.paper.heat.HeatPdc;
import dev.tfp.paper.itemdata.ContentsPdc;
import dev.tfp.paper.itemdata.FluidPdc;
import dev.tfp.paper.itemdata.ItemContents;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import java.util.regex.Pattern;
import org.bukkit.inventory.ItemStack;

/**
 * PAR-POT-04: the small vessel as data on an item. Contents are the item's {@code tfp:contents} (four slots, each a whole
 * stack), the alloy is its {@code tfp:fluid}, the heat is its {@code tfp:heat}; {@link VesselState} (core) holds the rules.
 * Nothing ticks: the temperature is read from the heat record at the calendar tick, and the melt of the contents is
 * {@link #settle evaluated lazily} when somebody looks (open, click, the player's sweep).
 *
 * <p>The vessel's own heat capacity depends on its contents ({@link VesselRules#contentHeatCapacity}); the temperature
 * read here uses it. The heater and the tooltip read the pottery capacity of the item's heat definition (ledger).
 *
 * <p>Threads: callers own the stack (the player's thread); the object itself is immutable.
 */
public final class VesselItems {
    /** {@code tfc:fired_vessels}: the plain and the 16 glazed vessels. */
    private static final Pattern FIRED = Pattern.compile("tfp:ceramic/(vessel|(?!unfired)[a-z_]+_glazed_vessel)");

    /**
     * What the vessel needs from the rest of the plugin.
     *
     * @param now          the calendar tick ({@code playerTicks})
     * @param idOf         the custom item id of a stack
     * @param heatCapacity the heat capacity per item of an item id (the {@code item_heat} definition), 0 when none
     * @param melt         the melt recipe of one item of a stack (temperature, fluid and the fluid's heat), empty when none
     * @param fits         TFC {@code canContainItem}: the size limit
     */
    public record Services(LongSupplier now, Function<ItemStack, Optional<String>> idOf,
                           ToDoubleFunction<String> heatCapacity, Function<ItemStack, Optional<Melt>> melt,
                           Predicate<ItemStack> fits) {
        public Services {
            Objects.requireNonNull(now, "now");
            Objects.requireNonNull(idOf, "idOf");
            Objects.requireNonNull(heatCapacity, "heatCapacity");
            Objects.requireNonNull(melt, "melt");
            Objects.requireNonNull(fits, "fits");
        }
    }

    private final Services services;
    private final HeatConfig config;

    public VesselItems(Services services, HeatConfig config) {
        this.services = Objects.requireNonNull(services, "services");
        this.config = Objects.requireNonNull(config, "config");
    }

    /** Whether {@code id} is a fired (plain or glazed) vessel. */
    public static boolean isVesselId(String id) {
        return id != null && FIRED.matcher(id).matches();
    }

    public boolean isVessel(ItemStack stack) {
        return stack != null && !stack.isEmpty() && services.idOf().apply(stack).filter(VesselItems::isVesselId).isPresent();
    }

    /** TFC {@code canContainItem}. */
    public boolean canContain(ItemStack stack) {
        return stack != null && !stack.isEmpty() && !isVessel(stack) && services.fits().test(stack);
    }

    /**
     * The vessel's state at {@code now}. The slot items are keyed by their custom id, else their material.
     * A vessel without a heat record is cold since {@code now}.
     */
    public VesselState state(ItemStack vessel) {
        long now = services.now().getAsLong();
        List<ItemStack> stored = ItemContents.slots(vessel, VesselRules.SLOTS);
        List<VesselSlot> slots = new ArrayList<>(VesselRules.SLOTS);
        for (ItemStack s : stored) {
            if (s == null || s.isEmpty()) {
                slots.add(VesselSlot.EMPTY);
                continue;
            }
            String id = services.idOf().apply(s).orElse(s.getType().getKey().toString());
            float perItem = (float) services.heatCapacity().applyAsDouble(id);
            slots.add(new VesselSlot(id, Math.min(s.getAmount(), VesselRules.SLOT_CAPACITY), perItem,
                    services.melt().apply(s).orElse(null)));
        }
        Alloy alloy = alloy(vessel);
        float capacity = VesselRules.contentHeatCapacity(slots, alloy.amount(), alloy.specificHeat());
        HeatState heat = HeatPdc.read(vessel).orElseGet(() -> HeatState.cold(now));
        return new VesselState(slots, alloy, capacity, heat);
    }

    /** The alloy of the vessel: its fluid with the heat values of the fluid's metal. */
    public static Alloy alloy(ItemStack vessel) {
        dev.tfp.core.vessel.FluidAmount fluid = FluidPdc.content(vessel);
        if (fluid.isEmpty()) {
            return Alloy.EMPTY;
        }
        return alloyOf(fluid);
    }

    static Alloy alloyOf(FluidAmount fluid) {
        Optional<Metal> metal = TfcFluids.get(fluid.fluid()).flatMap(f -> f.metal());
        return metal.map(m -> new Alloy(fluid.amount(), m.specificHeatCapacity(), m.meltTemperature()))
                .orElse(new Alloy(fluid.amount(), 0f, null));
    }

    /** The fluid melt data of a molten metal id: the specific heat and melt temperature, empty for a non-metal. */
    public static Optional<float[]> fluidHeat(String fluidId) {
        return TfcFluids.get(fluidId).flatMap(f -> f.metal()).map(m -> new float[] {m.specificHeatCapacity(), m.meltTemperature()});
    }

    /** The temperature of the vessel now, in degrees. 0 for a vessel that was never heated. */
    public float temperature(ItemStack vessel) {
        return state(vessel).temperatureAt(services.now().getAsLong(), config);
    }

    /** TFC {@code Vessel.isInventory}: cold and no fluid. */
    public boolean isInventory(ItemStack vessel) {
        return state(vessel).isInventory(services.now().getAsLong(), config);
    }

    /** TFC {@code Vessel.isMolten}. */
    public boolean isMolten(ItemStack vessel) {
        return state(vessel).isMolten(services.now().getAsLong(), config);
    }

    /**
     * The lazy melt check: when the vessel is at or above the melt temperature of any slot, the slots melt into the
     * alloy (TFC {@code updateInventoryOnMelt}), and the new contents, alloy and settled heat are written back. Does
     * nothing (and writes nothing) for a vessel that holds no meltable item or is not hot enough.
     *
     * @return whether the stack changed
     */
    public boolean settle(ItemStack vessel) {
        if (!isVessel(vessel) || ContentsPdc.isNewer(vessel) || FluidPdc.isNewer(vessel)) {
            return false;
        }
        if (!HeatPdc.has(vessel) || ContentsPdc.contents(vessel).isEmpty()) {
            return false; // never heated, or nothing to melt: the common case costs two PDC reads
        }
        long now = services.now().getAsLong();
        VesselState state = state(vessel);
        float temperature = state.temperatureAt(now, config);
        String fluidId = null;
        for (VesselSlot slot : state.slots()) {
            if (!slot.isEmpty() && VesselRules.melts(temperature, slot.melt())) {
                fluidId = slot.melt().perItem().fluid();
                break;
            }
        }
        if (fluidId == null) {
            return false;
        }
        String alloyFluid = FluidPdc.content(vessel).isEmpty() ? fluidId : FluidPdc.content(vessel).fluid();
        VesselState melted = state.heatTo(now, temperature, config);
        List<ItemStack> stored = ItemContents.slots(vessel, VesselRules.SLOTS);
        for (int i = 0; i < VesselRules.SLOTS; i++) {
            if (melted.slots().get(i).isEmpty()) {
                stored.set(i, ItemStack.empty());
            }
        }
        if (!ContentsPdc.write(vessel, stored)) {
            return false;
        }
        FluidPdc.write(vessel, melted.alloy().isEmpty() ? null : new FluidAmount(alloyFluid, melted.alloy().amount()));
        HeatPdc.write(vessel, melted.heat());
        return true;
    }

    /**
     * Inserts into slot {@code slot} of an inventory-mode vessel (TFC {@code insertItem}).
     *
     * @return what did not fit; {@code stack} itself when the vessel is not in inventory mode
     */
    public ItemStack insert(ItemStack vessel, int slot, ItemStack stack) {
        if (!isInventory(vessel)) {
            return stack.clone();
        }
        return ItemContents.insert(vessel, VesselRules.SLOTS, slot, stack, VesselRules.SLOT_CAPACITY, this::canContain);
    }

    /** The four slots as stacks ({@link ItemStack#empty()} for an empty slot). */
    public List<ItemStack> slots(ItemStack vessel) {
        return ItemContents.slots(vessel, VesselRules.SLOTS);
    }

    /** The alloy fluid of the vessel, {@link FluidAmount#EMPTY}-like empty when none. */
    public FluidAmount fluid(ItemStack vessel) {
        return FluidPdc.content(vessel);
    }

    /** The melt temperature the alloy needs to be liquid, empty when the vessel has no alloy. */
    public Optional<Float> alloyMeltTemperature(ItemStack vessel) {
        return Optional.ofNullable(alloy(vessel).meltTemperature());
    }
}
