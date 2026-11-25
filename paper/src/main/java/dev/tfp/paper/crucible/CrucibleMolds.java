/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/CrucibleBlockEntity.java @ c3b4d9536 (serverTick: draining a
 *       mold-like input, filling the output slot),
 *       src/main/java/net/dries007/tfc/util/Helpers.java @ c3b4d9536 (mergeOutputFluidIntoSlot)
 */
package dev.tfp.paper.crucible;

import dev.tfp.core.alloy.AlloyRecipe;
import dev.tfp.core.alloy.FluidAlloy;
import dev.tfp.core.vessel.FluidAmount;
import dev.tfp.paper.alloy.AlloyRecipes;
import dev.tfp.paper.itemdata.FluidPdc;
import dev.tfp.paper.mold.MoldItems;
import dev.tfp.paper.vessel.VesselItems;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import org.bukkit.inventory.ItemStack;

/**
 * PAR-METAL-23: what the crucible needs of the molds (PAR-POT-05's {@link MoldItems}) and of the alloy recipes. Every
 * method takes a single-item stack the caller owns and changes it in place. PAR-METAL-29: the pouring side
 * ({@link #isContainer}, {@link #pourStep}, {@link #pour}) also takes a fired small vessel (TFC {@code mergeOutputFluidIntoSlot}
 * fills any fluid handler item), through {@link VesselItems#pour}.
 */
public interface CrucibleMolds {
    /** No molds: the output slot takes nothing and nothing is poured. */
    CrucibleMolds NONE = new CrucibleMolds() {
        @Override
        public boolean isMold(ItemStack stack) {
            return false;
        }

        @Override
        public boolean isMolten(ItemStack mold) {
            return false;
        }

        @Override
        public FluidAmount content(ItemStack mold) {
            return FluidAmount.EMPTY;
        }

        @Override
        public int pourStep(FluidAmount fluid, float temperature, ItemStack mold) {
            return 0;
        }

        @Override
        public boolean drainOne(ItemStack mold) {
            return false;
        }

        @Override
        public String result(FluidAlloy alloy) {
            return "";
        }
    };

    /** A fired mold (a fluid container for the output slot). */
    boolean isMold(ItemStack stack);

    /** A fired small vessel (PAR-METAL-29). */
    default boolean isVessel(ItemStack stack) {
        return false;
    }

    /**
     * What a pour can fill: a fired mold or a fired vessel (TFC: any item with a fluid handler; jugs, buckets and the like
     * are not poured into, see the ledger).
     */
    default boolean isContainer(ItemStack stack) {
        return isMold(stack) || isVessel(stack);
    }

    /**
     * TFC {@code Helpers.mergeOutputFluidIntoSlot} for a whole amount: the container takes what fits of {@code fluid} at
     * {@code temperature}. A mold takes it one mB at a time ({@link #pourStep}); a vessel in one fill with one temperature
     * mix (TFC's {@code fill} takes the whole fitting amount).
     *
     * @return the mB taken
     */
    default int pour(FluidAmount fluid, float temperature, ItemStack container) {
        int poured = 0;
        while (poured < fluid.amount()
                && pourStep(new FluidAmount(fluid.fluid(), fluid.amount() - poured), temperature, container) == 1) {
            poured++;
        }
        return poured;
    }

    /** TFC {@code IMold.isMolten}: the mold holds a fluid at or above its melt temperature. */
    boolean isMolten(ItemStack mold);

    /** The mold's content. */
    FluidAmount content(ItemStack mold);

    /** TFC {@code fill} + {@code addTemperatureFromSourceWithHeatCapacity}: one mB of {@code fluid} at {@code temperature}. */
    int pourStep(FluidAmount fluid, float temperature, ItemStack mold);

    /** TFC {@code transferExact(mold, crucible, 1)}, the mold's half: takes one mB out of the mold. */
    boolean drainOne(ItemStack mold);

    /** TFC {@code CrucibleInventory.getResult}: the fluid id the alloy makes. */
    String result(FluidAlloy alloy);

    /**
     * The molds as they become available: the mold module loads after the crucible module, so its {@link MoldItems} is read
     * on every call; until it exists this behaves like {@link #NONE}.
     */
    static CrucibleMolds lazy(Supplier<Optional<MoldItems>> molds, AlloyRecipes alloys) {
        return lazy(molds, Optional::empty, alloys);
    }

    /** As {@link #lazy(Supplier, AlloyRecipes)}, with the small vessels as pour targets too (PAR-METAL-29). */
    static CrucibleMolds lazy(Supplier<Optional<MoldItems>> molds, Supplier<Optional<VesselItems>> vessels,
            AlloyRecipes alloys) {
        Objects.requireNonNull(molds, "molds");
        Objects.requireNonNull(vessels, "vessels");
        Objects.requireNonNull(alloys, "alloys");
        return new CrucibleMolds() {
            private CrucibleMolds now() {
                Optional<VesselItems> v = vessels.get();
                return molds.get().map(m -> of(m, v.orElse(null), alloys)).orElseGet(
                    () -> v.isPresent() ? of(null, v.get(), alloys) : NONE);
            }

            @Override
            public boolean isVessel(ItemStack stack) {
                return now().isVessel(stack);
            }

            @Override
            public int pour(FluidAmount fluid, float temperature, ItemStack container) {
                return now().pour(fluid, temperature, container);
            }

            @Override
            public boolean isMold(ItemStack stack) {
                return now().isMold(stack);
            }

            @Override
            public boolean isMolten(ItemStack mold) {
                return now().isMolten(mold);
            }

            @Override
            public FluidAmount content(ItemStack mold) {
                return now().content(mold);
            }

            @Override
            public int pourStep(FluidAmount fluid, float temperature, ItemStack mold) {
                return now().pourStep(fluid, temperature, mold);
            }

            @Override
            public boolean drainOne(ItemStack mold) {
                return now().drainOne(mold);
            }

            @Override
            public String result(FluidAlloy alloy) {
                return alloy.result(alloys.get());
            }
        };
    }

    /** The real molds and alloy recipes. */
    static CrucibleMolds of(MoldItems molds, AlloyRecipes alloys) {
        Objects.requireNonNull(molds, "molds");
        return of(molds, null, alloys);
    }

    /**
     * The real molds, small vessels and alloy recipes; either of {@code molds} and {@code vessels} may be null (then that
     * kind is not a container).
     */
    static CrucibleMolds of(MoldItems molds, VesselItems vessels, AlloyRecipes alloys) {
        Objects.requireNonNull(alloys, "alloys");
        return of(molds, vessels, alloys::get);
    }

    /** As {@link #of(MoldItems, VesselItems, AlloyRecipes)} with the alloy recipes as a supplier. */
    static CrucibleMolds of(MoldItems molds, VesselItems vessels, Supplier<List<AlloyRecipe>> alloys) {
        Objects.requireNonNull(alloys, "alloys");
        return new CrucibleMolds() {
            @Override
            public boolean isMold(ItemStack stack) {
                return molds != null && molds.isMold(stack);
            }

            @Override
            public boolean isVessel(ItemStack stack) {
                return vessels != null && vessels.isVessel(stack);
            }

            @Override
            public int pour(FluidAmount fluid, float temperature, ItemStack container) {
                if (vessels != null && vessels.isVessel(container)) {
                    return container.getAmount() == 1 && !fluid.isEmpty()
                        ? vessels.pour(container, fluid.fluid(), fluid.amount(), temperature) : 0;
                }
                return CrucibleMolds.super.pour(fluid, temperature, container);
            }

            @Override
            public boolean isMolten(ItemStack mold) {
                return molds != null && molds.isMold(mold) && molds.isMolten(mold);
            }

            @Override
            public FluidAmount content(ItemStack mold) {
                return molds == null ? FluidAmount.EMPTY : molds.content(mold);
            }

            @Override
            public int pourStep(FluidAmount fluid, float temperature, ItemStack mold) {
                if (vessels != null && vessels.isVessel(mold)) {
                    return pour(new FluidAmount(fluid.fluid(), Math.min(1, fluid.amount())), temperature, mold);
                }
                return molds == null ? 0 : molds.pourStep(fluid, temperature, mold);
            }

            @Override
            public boolean drainOne(ItemStack mold) {
                FluidAmount c = molds == null ? FluidAmount.EMPTY : molds.content(mold);
                if (c.isEmpty() || mold.getAmount() != 1 || FluidPdc.isNewer(mold)) {
                    return false;
                }
                return FluidPdc.write(mold, c.amount() <= 1 ? null : new FluidAmount(c.fluid(), c.amount() - 1));
            }

            @Override
            public String result(FluidAlloy alloy) {
                return alloy.result(alloys.get());
            }
        };
    }
}
