/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/PitKilnBlockEntity.java @ c3b4d9536 (isValid, tryLight side
 *       check),
 *       src/main/java/net/dries007/tfc/common/blocks/devices/PitKilnBlock.java @ c3b4d9536 (updateShape DOWN)
 */
package dev.tfp.paper.kiln;

import java.util.List;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockSupport;

/**
 * The structure checks on the server blocks: {@code isFaceSturdy} with the full support type, flammability as vanilla's
 * {@code Material.isFlammable} of the server block (as the charcoal pit's cover check). Region thread.
 */
public final class PaperKilnStructure implements PitKilns.Structure {
    /** {@code Direction.Plane.HORIZONTAL} order. */
    static final List<BlockFace> SIDES = List.of(BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST);

    @Override
    public boolean valid(Block kiln) {
        for (BlockFace face : SIDES) {
            Block side = kiln.getRelative(face);
            if (!side.getBlockData().isFaceSturdy(face.getOppositeFace(), BlockSupport.FULL) || side.getType().isFlammable()) {
                return false;
            }
        }
        return baseSturdy(kiln);
    }

    @Override
    public boolean sidesSturdy(Block kiln) {
        for (BlockFace face : SIDES) {
            if (!kiln.getRelative(face).getBlockData().isFaceSturdy(face.getOppositeFace(), BlockSupport.FULL)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean baseSturdy(Block kiln) {
        return kiln.getRelative(BlockFace.DOWN).getBlockData().isFaceSturdy(BlockFace.UP, BlockSupport.FULL);
    }
}
