/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.worldgen;

import dev.tfp.core.worldgen.deposit.DepositBlock;
import dev.tfp.core.worldgen.groundcover.Groundcover;
import java.util.List;
import org.bukkit.Material;

/**
 * The blocks of the groundcover and deposits (M2-GEN-11). None of these blocks exists in the CraftEngine content yet (loose
 * rocks and small ores are items only, there are no deposit, groundcover or peat blocks, and the clay blocks are per soil
 * variant, which the vanilla dirt they replace does not carry), so each kind gets a vanilla placeholder (ledger). The
 * placement rule is pure: {@link #write} applies the decisions of one chunk through a {@link Target}.
 */
final class GroundcoverBlocks {
    private GroundcoverBlocks() {}

    /** The vanilla placeholder of a groundcover kind. Buttons are placed on the floor; they have the small hitbox of a piece. */
    static Material placeholder(Groundcover.Kind kind, boolean waterlogged) {
        return switch (kind) {
            case LOOSE_ROCK, MOSSY_LOOSE_ROCK -> Material.STONE_BUTTON;
            case SMALL_ORE -> Material.POLISHED_BLACKSTONE_BUTTON;
            case DRIFTWOOD -> Material.SPRUCE_BUTTON;
            case STICK -> Material.OAK_BUTTON;
            case CLAM, MOLLUSK, MUSSEL -> Material.SEA_PICKLE;
            case SEAWEED -> waterlogged ? Material.SEAGRASS : Material.BROWN_CARPET;
        };
    }

    /** The vanilla placeholder of a deposit block: a raw metal block per native metal, clay for clay, mud and moss for peat. */
    static Material placeholder(DepositBlock block) {
        return switch (block.kind()) {
            case ORE_DEPOSIT -> switch (block.mineral()) {
                case "native_copper" -> Material.RAW_COPPER_BLOCK;
                case "native_gold" -> Material.RAW_GOLD_BLOCK;
                case "native_silver" -> Material.RAW_IRON_BLOCK;
                case "cassiterite" -> Material.BLACKSTONE;
                default -> throw new IllegalArgumentException("unknown deposit mineral " + block.mineral());
            };
            case CLAY, CLAY_GRASS -> Material.CLAY;
            case PEAT -> Material.PACKED_MUD;
            case PEAT_GRASS -> Material.MOSS_BLOCK;
        };
    }

    /** The blocks of the generating chunk, by local x and z. */
    interface Target {
        /** Whether the position holds air or water, so a groundcover block may go there ({@code isWorldgenReplaceable}). */
        boolean free(int localX, int y, int localZ);

        void place(int localX, int y, int localZ, Groundcover cover);
    }

    /**
     * Writes the groundcover of one chunk in order: a block outside the chunk or its heights, or on a position that is no
     * longer air or water (an earlier feature took it), is skipped. The decisions are re-validated here, so the first feature
     * in TFC's step order keeps a contested position.
     *
     * @return the number of blocks written
     */
    static int write(List<Groundcover> covers, int chunkX, int chunkZ, int minY, int maxY, Target target) {
        int written = 0;
        for (Groundcover g : covers) {
            if (!g.inChunk(chunkX, chunkZ) || g.y() <= minY || g.y() >= maxY) {
                continue;
            }
            if (target.free(g.x() & 15, g.y(), g.z() & 15)) {
                target.place(g.x() & 15, g.y(), g.z() & 15, g);
                written++;
            }
        }
        return written;
    }
}
