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

import dev.tfp.core.alloy.AlloyDisplay;
import dev.tfp.core.alloy.AlloyRecipe;
import dev.tfp.core.alloy.FluidAlloy;
import dev.tfp.core.crucible.CrucibleRules;
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
import dev.tfp.paper.alloy.AlloyPdc;
import dev.tfp.paper.heat.HeatPdc;
import dev.tfp.paper.itemdata.ContentsPdc;
import dev.tfp.paper.itemdata.FluidPdc;
import dev.tfp.paper.itemdata.ItemContents;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import java.util.regex.Pattern;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * PAR-POT-04: the small vessel as data on an item. Contents are the item's {@code tfp:contents} (four slots, each a whole
 * stack), the metals are its {@code tfp:alloy} mix (PAR-METAL-22; an old vessel with one {@code tfp:fluid} still reads and is
 * rewritten as {@code tfp:alloy} on its next change), the heat is its {@code tfp:heat}; {@link VesselState} (core) holds the rules.
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
     * @param alloyRecipes the alloy recipes in lookup order, read when the mix's result is needed
     */
    public record Services(LongSupplier now, Function<ItemStack, Optional<String>> idOf,
                           ToDoubleFunction<String> heatCapacity, Function<ItemStack, Optional<Melt>> melt,
                           Predicate<ItemStack> fits, Supplier<List<AlloyRecipe>> alloyRecipes) {
        /** Without alloy recipes: every mix of several metals is {@code metal/unknown}. */
        public Services(LongSupplier now, Function<ItemStack, Optional<String>> idOf, ToDoubleFunction<String> heatCapacity,
                        Function<ItemStack, Optional<Melt>> melt, Predicate<ItemStack> fits) {
            this(now, idOf, heatCapacity, melt, fits, List::of);
        }

        public Services {
            Objects.requireNonNull(now, "now");
            Objects.requireNonNull(idOf, "idOf");
            Objects.requireNonNull(heatCapacity, "heatCapacity");
            Objects.requireNonNull(melt, "melt");
            Objects.requireNonNull(fits, "fits");
            Objects.requireNonNull(alloyRecipes, "alloyRecipes");
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

    private static final NamespacedKey ALLOY_ROOT = Objects.requireNonNull(NamespacedKey.fromString(AlloyPdc.KEY));
    private static final NamespacedKey ALLOY_V = new NamespacedKey("tfp", "v");

    /** Whether the stack holds a {@code tfp:alloy} of a newer version than this build reads (never overwritten). */
    static boolean alloyIsNewer(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return false;
        }
        PersistentDataContainer root = stack.getItemMeta().getPersistentDataContainer().get(ALLOY_ROOT,
            PersistentDataType.TAG_CONTAINER);
        Integer v = root == null ? null : root.get(ALLOY_V, PersistentDataType.INTEGER);
        return v != null && v > AlloyPdc.VERSION;
    }

    /**
     * The metals in the vessel: its {@code tfp:alloy}, else (an old vessel) its single {@code tfp:fluid} as one metal.
     */
    public static FluidAlloy mix(ItemStack vessel) {
        FluidAlloy mix = AlloyPdc.read(vessel);
        if (!mix.isEmpty()) {
            return mix;
        }
        FluidAmount legacy = FluidPdc.content(vessel);
        if (legacy.isEmpty()) {
            return FluidAlloy.empty();
        }
        Map<String, Double> one = new LinkedHashMap<>();
        one.put(legacy.fluid(), (double) legacy.amount());
        return FluidAlloy.of(legacy.amount(), one);
    }

    /** The alloy of the vessel: the heat values of the fluid its metals make (TFC {@code FluidAlloy.getResult}). */
    public Alloy alloy(ItemStack vessel) {
        return Alloy.ofMix(mix(vessel), services.alloyRecipes().get());
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
        if (!isVessel(vessel) || ContentsPdc.isNewer(vessel) || FluidPdc.isNewer(vessel) || alloyIsNewer(vessel)) {
            return false;
        }
        if (!HeatPdc.has(vessel) || ContentsPdc.contents(vessel).isEmpty()) {
            return false; // never heated, or nothing to melt: the common case costs two PDC reads
        }
        long now = services.now().getAsLong();
        VesselState state = state(vessel);
        float temperature = state.temperatureAt(now, config);
        VesselRules.MeltOutcome outcome = VesselRules.meltInto(mix(vessel), state.slots(), temperature);
        if (!outcome.changed()) {
            return false;
        }
        VesselState melted = state.heatTo(now, temperature, config)
            .withAlloy(now, Alloy.ofMix(outcome.mix(), services.alloyRecipes().get()), config);
        List<ItemStack> stored = ItemContents.slots(vessel, VesselRules.SLOTS);
        for (int i = 0; i < VesselRules.SLOTS; i++) {
            if (melted.slots().get(i).isEmpty()) {
                stored.set(i, ItemStack.empty());
            }
        }
        if (!ContentsPdc.write(vessel, stored)) {
            return false;
        }
        writeMix(vessel, outcome.mix());
        HeatPdc.write(vessel, melted.heat());
        return true;
    }

    /**
     * Writes the mix as {@code tfp:alloy}, drops an old single {@code tfp:fluid}, and keeps TFC's rule that a vessel holding
     * fluid never stacks.
     */
    private static void writeMix(ItemStack vessel, FluidAlloy mix) {
        if (!FluidPdc.content(vessel).isEmpty()) {
            FluidPdc.write(vessel, null);
        }
        AlloyPdc.write(vessel, mix);
        ItemMeta meta = vessel.getItemMeta();
        if (meta != null) {
            meta.setMaxStackSize(mix.isEmpty() ? null : 1);
            vessel.setItemMeta(meta);
        }
    }

    /**
     * TFC {@code Vessel.fill}: adds a molten metal to the vessel's mix, up to the capacity ({@link
     * VesselRules#SMALL_VESSEL_CAPACITY}), in any mode. Only a fluid with a melt temperature is accepted (TFC's
     * {@code canContainFluid}). The temperature is settled to now first, so it does not jump with the new heat capacity.
     *
     * @return the amount taken, 0 for a stack that is no vessel, a refused fluid or a full vessel
     */
    public int fill(ItemStack vessel, String fluid, int amount) {
        if (!isVessel(vessel) || amount <= 0 || FluidPdc.isNewer(vessel) || alloyIsNewer(vessel)
            || CrucibleRules.meltTemperature(fluid).isEmpty()) {
            return 0;
        }
        FluidAlloy.Fill fill = mix(vessel).fill(fluid, amount, VesselRules.SMALL_VESSEL_CAPACITY);
        if (fill.accepted() == 0) {
            return 0;
        }
        if (HeatPdc.has(vessel)) {
            HeatPdc.write(vessel, new HeatState(temperature(vessel), services.now().getAsLong()));
        }
        writeMix(vessel, fill.alloy());
        return fill.accepted();
    }

    /**
     * Takes {@code amount} mB out of the mix, each metal in proportion (TFC {@code FluidAlloy.drain}); the temperature is
     * settled to now first. A vessel that cannot be written (newer data) gives nothing.
     *
     * @return the amount taken
     */
    public int drain(ItemStack vessel, int amount) {
        if (!isVessel(vessel) || amount <= 0 || FluidPdc.isNewer(vessel) || alloyIsNewer(vessel)) {
            return 0;
        }
        FluidAlloy.Drain drain = mix(vessel).drain(amount);
        if (drain.drained() == 0) {
            return 0;
        }
        if (HeatPdc.has(vessel)) {
            HeatPdc.write(vessel, new HeatState(temperature(vessel), services.now().getAsLong()));
        }
        writeMix(vessel, drain.alloy());
        return drain.drained();
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

    /**
     * The fluid the vessel's metals make and the total amount ({@code tfp:metal/bronze 1000}, {@code tfp:metal/unknown} for a mix
     * no recipe makes), empty when none.
     */
    public FluidAmount fluid(ItemStack vessel) {
        FluidAlloy mix = mix(vessel);
        if (mix.isEmpty()) {
            return FluidAmount.EMPTY;
        }
        AlloyDisplay.View view = AlloyDisplay.of(mix, services.alloyRecipes().get());
        return new FluidAmount(view.result(), view.amount());
    }

    /** The melt temperature the alloy needs to be liquid, empty when the vessel has no alloy. */
    public Optional<Float> alloyMeltTemperature(ItemStack vessel) {
        return Optional.ofNullable(alloy(vessel).meltTemperature());
    }
}
