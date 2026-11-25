/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/BloomeryBlock.java @ c3b4d9536 (BASE_MULTIBLOCKS,
 *       BLOOMERY_CHIMNEY, GATE_X, GATE_Z)
 */
package dev.tfp.paper.bloomery;

import dev.tfp.core.bloomery.BloomeryStructure;
import java.util.Objects;
import org.bukkit.block.Block;

/**
 * The world around one origin block for {@link BloomeryStructure}, read through a {@link BloomeryProbe}. The origin is the
 * inside block for the base and the chimney, the gate block for the gate. A block the probe cannot read counts as not
 * matching, so a bloomery at a region edge is valid only by what its own region sees (TFC would load the chunk).
 */
public final class BloomeryWorld implements BloomeryStructure.World {
    public static final String BLOOMERY = "tfp:bloomery";
    public static final String BLOOM = "tfp:bloom";
    public static final String MOLTEN = "tfp:molten";

    private final Block origin;
    private final BloomeryProbe probe;

    public BloomeryWorld(Block origin, BloomeryProbe probe) {
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
    public boolean bloomery(int dx, int dy, int dz) {
        Block b = at(dx, dy, dz);
        return b != null && probe.blockId(b).filter(BLOOMERY::equals).isPresent();
    }

    @Override
    public boolean air(int dx, int dy, int dz) {
        Block b = at(dx, dy, dz);
        return b != null && probe.air(b);
    }

    @Override
    public boolean center(int dx, int dy, int dz) {
        Block b = at(dx, dy, dz);
        return b != null && (probe.air(b) || probe.blockId(b).filter(id -> id.equals(MOLTEN) || id.equals(BLOOM)).isPresent());
    }

    @Override
    public boolean inside(int dx, int dy, int dz) {
        Block b = at(dx, dy, dz);
        return b != null && (probe.air(b) || probe.blockId(b).filter(MOLTEN::equals).isPresent());
    }
}
