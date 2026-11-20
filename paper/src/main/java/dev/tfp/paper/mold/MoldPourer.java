/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/container/MoldLikeAlloyContainer.java @ c3b4d9536 (broadcastChanges)
 */
package dev.tfp.paper.mold;

import dev.tfp.core.heat.HeatState;
import dev.tfp.core.vessel.FluidAmount;
import dev.tfp.paper.heat.HeatPdc;
import dev.tfp.paper.itemdata.FluidPdc;
import dev.tfp.paper.vessel.VesselItems;
import java.util.Objects;
import org.bukkit.inventory.ItemStack;

/**
 * PAR-POT-05: moves molten metal from a source (a molten vessel or a molten mold) into a mold, one mB at a time as TFC's
 * {@code MoldLikeAlloyContainer} does while its screen is open. Both the screen (one mB a tick) and the sneak shortcut
 * (everything at once) run through {@link #pour}.
 *
 * <p>Threads: callers own both stacks (the player's thread).
 */
public final class MoldPourer {
    private final VesselItems vessels;
    private final MoldItems molds;

    public MoldPourer(VesselItems vessels, MoldItems molds) {
        this.vessels = Objects.requireNonNull(vessels, "vessels");
        this.molds = Objects.requireNonNull(molds, "molds");
    }

    /** Whether {@code stack} can pour: a fired vessel or a fired mold. */
    public boolean isSource(ItemStack stack) {
        return vessels.isVessel(stack) || molds.isMold(stack);
    }

    /** TFC {@code isMolten} of a source: a vessel's alloy or a mold's content is at its melt temperature. */
    public boolean isMolten(ItemStack source) {
        if (vessels.isVessel(source)) {
            return !vessels.fluid(source).isEmpty() && vessels.isMolten(source);
        }
        return molds.isMold(source) && molds.isMolten(source);
    }

    /** The fluid a source holds, empty when none. */
    public FluidAmount fluid(ItemStack source) {
        return FluidPdc.content(source);
    }

    /**
     * Pours up to {@code maxSteps} mB from {@code source} into {@code mold} (a single item) while the source is molten and
     * the mold takes it. Writes both stacks.
     *
     * @return the mB poured
     */
    public int pour(ItemStack source, ItemStack mold, int maxSteps) {
        if (!isSource(source) || !molds.isMold(mold) || mold.getAmount() != 1 || FluidPdc.isNewer(source)) {
            return 0;
        }
        boolean vessel = vessels.isVessel(source);
        long now = molds.now();
        int poured = 0;
        for (int i = 0; i < maxSteps; i++) {
            if (!isMolten(source)) {
                break;
            }
            FluidAmount fluid = fluid(source);
            float temperature = vessel ? vessels.temperature(source) : molds.temperature(source);
            int moved = molds.pourStep(fluid, temperature, mold);
            if (moved == 0) {
                break;
            }
            if (vessel && poured == 0) {
                // the vessel's heat capacity follows its contents: settle the temperature before the amount changes
                HeatPdc.write(source, new HeatState(temperature, now));
            }
            int left = fluid.amount() - moved;
            FluidPdc.write(source, left > 0 ? new FluidAmount(fluid.fluid(), left) : null);
            poured += moved;
        }
        return poured;
    }
}
