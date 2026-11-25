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

import dev.tfp.core.alloy.FluidAlloy;
import dev.tfp.core.vessel.FluidAmount;
import dev.tfp.paper.alloy.AlloyRecipes;
import dev.tfp.paper.itemdata.FluidPdc;
import dev.tfp.paper.mold.MoldItems;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import org.bukkit.inventory.ItemStack;

/**
 * PAR-METAL-23: what the crucible needs of the molds (PAR-POT-05's {@link MoldItems}) and of the alloy recipes. Every
 * method takes a single-item stack the caller owns and changes it in place.
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
        Objects.requireNonNull(molds, "molds");
        Objects.requireNonNull(alloys, "alloys");
        return new CrucibleMolds() {
            private CrucibleMolds now() {
                return molds.get().map(m -> of(m, alloys)).orElse(NONE);
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
        Objects.requireNonNull(alloys, "alloys");
        return new CrucibleMolds() {
            @Override
            public boolean isMold(ItemStack stack) {
                return molds.isMold(stack);
            }

            @Override
            public boolean isMolten(ItemStack mold) {
                return molds.isMold(mold) && molds.isMolten(mold);
            }

            @Override
            public FluidAmount content(ItemStack mold) {
                return molds.content(mold);
            }

            @Override
            public int pourStep(FluidAmount fluid, float temperature, ItemStack mold) {
                return molds.pourStep(fluid, temperature, mold);
            }

            @Override
            public boolean drainOne(ItemStack mold) {
                FluidAmount c = molds.content(mold);
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
