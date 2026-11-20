/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/items/MoldItem.java @ c3b4d9536 (containerInfo, use, overrideOtherStackedOnMe),
 *       src/main/java/net/dries007/tfc/common/container/MoldLikeAlloyContainer.java @ c3b4d9536 (broadcastChanges),
 *       src/main/java/net/dries007/tfc/common/component/mold/Mold.java @ c3b4d9536 (isMolten, updateFluid)
 */
package dev.tfp.paper.mold;

import dev.tfp.core.crucible.CrucibleRules;
import dev.tfp.core.heat.HeatState;
import dev.tfp.core.mold.MoldPouring;
import dev.tfp.core.vessel.FluidAmount;
import dev.tfp.core.vessel.MoldKind;
import dev.tfp.paper.heat.HeatPdc;
import dev.tfp.paper.heat.ItemHeatHooks;
import dev.tfp.paper.itemdata.FluidPdc;
import dev.tfp.paper.vessel.VesselItems;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.inventory.ItemStack;

/**
 * PAR-POT-05: a fired mold as data on an item. Its content is the item's {@code tfp:fluid}, its heat the item's
 * {@code tfp:heat} (the pottery capacity of the item heat definition); {@link dev.tfp.core.vessel.MoldRules} (core) holds
 * the rules. Nothing ticks: whether the content is molten is read from the heat record at the calendar tick, so a mold
 * "solidifies" by itself as it cools.
 *
 * <p>Threads: callers own the stack (the player's thread); the object itself is immutable.
 */
public final class MoldItems {
    /** The fired molds: {@code tfp:ceramic/<kind>_mold}, not the unfired ones and not the mold table. */
    private static final Pattern FIRED = Pattern.compile("tfp:ceramic/([a-z_]+)_mold");

    /** A casting recipe matched for a mold: the item it gives and the chance that the mold breaks. */
    public record Cast(ItemStack result, float breakChance) {
    }

    /**
     * What the molds need from the rest of the plugin.
     *
     * @param now        the calendar tick ({@code playerTicks})
     * @param idOf       the custom item id of a stack
     * @param fluidInTag whether a fluid id is in a fluid tag ({@code (fluid, tag)})
     * @param heat       the item temperature
     * @param casting    TFC {@code CastingRecipe.get(mold)}: the recipe of a mold item and its content, with the result item
     *                   already assembled (heat of the mold copied), empty when none
     */
    public record Services(LongSupplier now, Function<ItemStack, Optional<String>> idOf, BiPredicate<String, String> fluidInTag,
                           ItemHeatHooks heat, Function<ItemStack, Optional<Cast>> casting) {
        public Services {
            Objects.requireNonNull(now, "now");
            Objects.requireNonNull(idOf, "idOf");
            Objects.requireNonNull(fluidInTag, "fluidInTag");
            Objects.requireNonNull(heat, "heat");
            Objects.requireNonNull(casting, "casting");
        }
    }

    private final Services services;

    public MoldItems(Services services) {
        this.services = Objects.requireNonNull(services, "services");
    }

    /** The kind of a fired mold id, empty for any other id (the unfired molds, the mold table, other items). */
    public static Optional<MoldKind> kindOf(String id) {
        if (id == null) {
            return Optional.empty();
        }
        Matcher m = FIRED.matcher(id);
        if (!m.matches()) {
            return Optional.empty();
        }
        try {
            return Optional.of(MoldKind.valueOf(m.group(1).toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty(); // unfired_*, mold_table and the like
        }
    }

    public Optional<MoldKind> kind(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return Optional.empty();
        }
        return services.idOf().apply(stack).flatMap(MoldItems::kindOf);
    }

    public boolean isMold(ItemStack stack) {
        return kind(stack).isPresent();
    }

    /** The mold's tag of usable fluids: ingot molds (and the fire ingot mold), the bell mold, else the tool head molds. */
    static String usableTag(MoldKind kind) {
        return switch (kind) {
            case INGOT, FIRE_INGOT -> "tfc:usable_in_ingot_mold";
            case BELL -> "tfc:usable_in_bell_mold";
            default -> "tfc:usable_in_tool_head_mold";
        };
    }

    /** TFC {@code containerInfo.canContainFluid}: in the mold's fluid tag and a fluid with heat data. */
    public boolean canContain(MoldKind kind, String fluid) {
        return VesselItems.fluidHeat(fluid).isPresent() && services.fluidInTag().test(fluid, usableTag(kind));
    }

    public FluidAmount content(ItemStack mold) {
        return FluidPdc.content(mold);
    }

    /** The temperature of the mold now (the pottery capacity); 0 for a mold never heated. */
    public float temperature(ItemStack mold) {
        return services.heat().temperature(mold);
    }

    /** TFC {@code FluidHeat.getOrUnknown}: the melt temperature of a fluid. */
    public static float meltTemperature(String fluid) {
        return VesselItems.fluidHeat(fluid).map(h -> h[1]).orElse(CrucibleRules.UNKNOWN_MELT_TEMPERATURE);
    }

    /** TFC {@code Mold.isMolten}: the content is at or above its melt temperature. An empty mold is not molten. */
    public boolean isMolten(ItemStack mold) {
        FluidAmount content = content(mold);
        return !content.isEmpty() && temperature(mold) >= meltTemperature(content.fluid());
    }

    /**
     * One mB of a pour from a source holding {@code sourceFluid} at {@code sourceTemperature} into {@code mold} (a single
     * item): the mold's content and temperature are written.
     *
     * @return the mB taken from the source, 0 or 1
     */
    public int pourStep(FluidAmount sourceFluid, float sourceTemperature, ItemStack mold) {
        Optional<MoldKind> kind = kind(mold);
        if (kind.isEmpty() || mold.getAmount() != 1 || FluidPdc.isNewer(mold) || sourceFluid.isEmpty()) {
            return 0;
        }
        float[] heat = VesselItems.fluidHeat(sourceFluid.fluid()).orElse(null);
        if (heat == null) {
            return 0;
        }
        FluidAmount content = content(mold);
        MoldPouring.Step step = MoldPouring.step(sourceFluid, sourceTemperature, content, temperature(mold),
            kind.get().defaultCapacity(), canContain(kind.get(), sourceFluid.fluid()), heat[0]);
        if (step.moved() == 0) {
            return 0;
        }
        if (!FluidPdc.write(mold, step.content())) {
            return 0;
        }
        HeatPdc.write(mold, new HeatState(step.temperature(), services.now().getAsLong()));
        return step.moved();
    }

    /** TFC {@code CastingRecipe.get(mold)} and {@code assemble}: the casting of a cold mold, empty when none. */
    public Optional<Cast> cast(ItemStack mold) {
        if (!isMold(mold) || content(mold).isEmpty()) {
            return Optional.empty();
        }
        return services.casting().apply(mold);
    }

    /** {@code drainIgnoringTemperature(Integer.MAX_VALUE)}: empties the mold (a single item), the heat stays. */
    public boolean drain(ItemStack mold) {
        if (mold.getAmount() != 1 || FluidPdc.isNewer(mold)) {
            return false;
        }
        return FluidPdc.write(mold, null);
    }

    /** The calendar tick. */
    long now() {
        return services.now().getAsLong();
    }
}
