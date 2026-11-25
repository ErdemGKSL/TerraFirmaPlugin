/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.worldgen;

import dev.tfp.core.worldgen.deposit.DepositBlock;
import dev.tfp.core.worldgen.groundcover.Boulders;
import dev.tfp.core.worldgen.groundcover.Groundcover;
import java.util.List;
import java.util.Locale;
import org.bukkit.Material;

/**
 * The blocks of the groundcover, deposits and boulders (M2-GEN-11, PAR-WORLD-09). Each decision has its CraftEngine block id
 * ({@link #ceId}); until those CE blocks exist (loose rocks and small ores are items only, there are no deposit, groundcover
 * or peat blocks, and the clay blocks are per soil variant, which the vanilla dirt they replace does not carry) the terrain
 * writes a vanilla placeholder (ledger). The boulders are rock blocks that exist ({@code tfp:rock/raw|cobble|mossy_cobble}).
 * The placement rule is pure: {@link #write} applies the decisions of one chunk through a {@link Target}.
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
            case GUANO -> Material.LIGHT_GRAY_CARPET;
        };
    }

    /**
     * The CraftEngine block state of a groundcover decision: its block id, with the {@code count} of a loose rock
     * ({@code tfp:rock/loose/granite[count=2]}).
     */
    static String ceId(Groundcover cover) {
        return switch (cover.kind()) {
            case LOOSE_ROCK, MOSSY_LOOSE_ROCK -> cover.block() + "[count=" + cover.count() + "]";
            default -> cover.block();
        };
    }

    /**
     * PAR-WORLD-32: the CraftEngine block of a deposit block: {@code tfp:deposit/<mineral>/<rock>} for an ore deposit (the rock
     * of the replaced gravel); null for the soil discs, whose blocks keep their placeholder until the soil families are
     * written by the terrain.
     */
    static String ceId(DepositBlock block, String rockName) {
        return block.kind() == dev.tfp.core.worldgen.deposit.DepositKind.ORE_DEPOSIT
            ? "tfp:deposit/" + block.mineral() + "/" + rockName : null;
    }

    /** The CraftEngine block of a boulder piece: {@code tfp:rock/<raw|cobble|mossy_cobble>/<rock>}. */
    static String ceId(Boulders.Block block, List<String> rockNames) {
        return "tfp:rock/" + block.material().name().toLowerCase(Locale.ROOT) + "/" + rockNames.get(block.rock());
    }

    /**
     * The vanilla placeholder of a deposit block: a raw metal block per native metal, clay for clay, mud and moss for peat,
     * vanilla rooted dirt and powder snow; null for {@link dev.tfp.core.worldgen.deposit.DepositKind#MOLLISOL}, which changes
     * only the soil variant that the placeholder terrain does not show (the writer keeps the block there).
     */
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
            case ROOTED_DIRT -> Material.ROOTED_DIRT;
            case POWDER_SNOW -> Material.POWDER_SNOW;
            case MOLLISOL -> null;
        };
    }

    /** The blocks of the generating chunk, by local x and z, for the boulders. */
    interface BoulderTarget {
        void place(int localX, int y, int localZ, Boulders.Block block);
    }

    /**
     * Writes the boulder blocks of one chunk in order, over whatever is there ({@code Feature.setBlock}); a block outside the
     * chunk or its heights is skipped.
     *
     * @return the number of blocks written
     */
    static int writeBoulders(List<Boulders.Block> blocks, int chunkX, int chunkZ, int minY, int maxY, BoulderTarget target) {
        int written = 0;
        for (Boulders.Block b : blocks) {
            if (!b.inChunk(chunkX, chunkZ) || b.y() < minY || b.y() >= maxY) {
                continue;
            }
            target.place(b.x() & 15, b.y(), b.z() & 15, b);
            written++;
        }
        return written;
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
