/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.blastfurnace;

import dev.tfp.core.blastfurnace.BlastFurnaceStructure;
import java.util.Objects;
import org.bukkit.block.Block;

/**
 * The world around one blast furnace block for {@link BlastFurnaceStructure}, read through a {@link BlastFurnaceProbe}. A
 * block the probe cannot read counts as not matching, so a chimney across a region edge is formed only as far as the
 * region of the furnace sees it (TFC would load the chunk).
 */
public final class BlastFurnaceWorld implements BlastFurnaceStructure.World {
    private final Block origin;
    private final BlastFurnaceProbe probe;

    public BlastFurnaceWorld(Block origin, BlastFurnaceProbe probe) {
        this.origin = Objects.requireNonNull(origin, "origin");
        this.probe = Objects.requireNonNull(probe, "probe");
    }

    private Block at(int dx, int dy, int dz) {
        Block b = origin.getRelative(dx, dy, dz);
        return probe.readable(b) ? b : null;
    }

    @Override
    public boolean insulation(int dx, int dy, int dz) {
        Block b = at(dx, dy, dz);
        return b != null && probe.insulation(b);
    }

    @Override
    public boolean centre(int dx, int dy, int dz) {
        Block b = at(dx, dy, dz);
        return b != null && probe.centre(b);
    }
}
