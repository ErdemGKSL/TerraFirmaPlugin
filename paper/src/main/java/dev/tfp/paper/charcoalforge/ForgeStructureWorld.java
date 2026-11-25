/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/CharcoalForgeBlock.java @ c3b4d9536 (FORGE_MULTIBLOCK: the
 *       top block, canSeeSky, isForgeInsulationBlock)
 */
package dev.tfp.paper.charcoalforge;

import dev.tfp.core.charcoalforge.CharcoalForgeStructure;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.tags.Tags;
import java.util.Objects;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * The world around one forge for {@link CharcoalForgeStructure#isValid}, read from the live world. Region thread of the
 * forge. A block in an unloaded chunk or in another region (Folia) counts as not matching, so a forge at a region edge is
 * valid only by what its own region can see (TFC would load the chunk).
 */
public final class ForgeStructureWorld implements CharcoalForgeStructure.World {
    public static final String INSULATION = "tfc:charcoal_forge_insulation";
    public static final String INVISIBLE = "tfc:charcoal_forge_invisible";
    /** TFC {@code LevelReader.canSeeSky}: the sky light at the position is the maximum. */
    static final int MAX_SKY_LIGHT = 15;

    private final Block forge;
    private final Tags tags;
    private final Scheduler scheduler;

    public ForgeStructureWorld(Block forge, Tags tags, Scheduler scheduler) {
        this.forge = Objects.requireNonNull(forge, "forge");
        this.tags = Objects.requireNonNull(tags, "tags");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    private Block at(int dx, int dy, int dz) {
        World w = forge.getWorld();
        int x = forge.getX() + dx;
        int y = forge.getY() + dy;
        int z = forge.getZ() + dz;
        int cx = x >> 4;
        int cz = z >> 4;
        if (y < w.getMinHeight() || y >= w.getMaxHeight() || !w.isChunkLoaded(cx, cz) || !scheduler.isOwner(w, cx, cz)) {
            return null;
        }
        return w.getBlockAt(x, y, z);
    }

    @Override
    public boolean topClear() {
        Block b = at(0, 1, 0);
        return b != null && (b.getType().isAir() || tags.is(b, INVISIBLE));
    }

    @Override
    public boolean canSeeSky(int dx, int dy, int dz) {
        Block b = at(dx, dy, dz);
        return b != null && b.getLightFromSky() >= MAX_SKY_LIGHT;
    }

    @Override
    public boolean insulation(int dx, int dy, int dz) {
        Block b = at(dx, dy, dz);
        return b != null && tags.is(b, INSULATION);
    }
}
