/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.worldgen;

import dev.tfp.core.worldgen.chunk.PlaceholderRocks;
import dev.tfp.core.worldgen.volcano.VolcanoBlock;
import dev.tfp.core.worldgen.volcano.VolcanoKind;
import java.util.List;
import org.bukkit.Material;

/**
 * The blocks of the volcano and hot spring features (M2-GEN-14). Rock blocks are the CraftEngine rocks of the palette: raw
 * rock as itself, TFC's hardened rock as the raw rock of the same rock (no hardened rock block exists yet), a gem ore as the
 * palette's {@code tfp:ore/<gem>/<rock>} when the palette has it, else its rock. The rest are vanilla placeholders: lava,
 * water for TFC's spring water, gravel for rock gravel, magma blocks, dirt (ledger). The placement rule is pure:
 * {@link #write} applies the decisions of one chunk through a {@link Target}.
 */
final class VolcanoBlocks {
    private VolcanoBlocks() {}

    /** The vanilla placeholder of a block kind, or null for the kinds the rock palette supplies. */
    static Material placeholder(VolcanoKind kind) {
        return switch (kind) {
            case AIR -> Material.AIR;
            case LAVA -> Material.LAVA;
            case SPRING_WATER -> Material.WATER;
            case GRAVEL -> Material.GRAVEL;
            case MAGMA -> Material.MAGMA_BLOCK;
            case DIRT -> Material.DIRT;
            case RAW_ROCK, HARDENED_ROCK, GEM_ORE -> null;
        };
    }

    /** The raw rock id of a rock block ({@code tfp:rock/raw/<rock>}), the palette's fallback for every rock kind. */
    static String rockId(VolcanoBlock block) {
        return "tfp:rock/raw/" + PlaceholderRocks.names().get(block.rock());
    }

    /** The gem ore id of a {@link VolcanoKind#GEM_ORE} block ({@code tfp:ore/<gem>/<rock>}), else null. */
    static String gemOreId(VolcanoBlock block) {
        return block.kind() == VolcanoKind.GEM_ORE ? "tfp:ore/" + block.gem().id() + "/" + PlaceholderRocks.names().get(block.rock()) : null;
    }

    /** The blocks of the generating chunk, by local x and z. */
    interface Target {
        /** The block at a position of the chunk. */
        Material type(int localX, int y, int localZ);

        void place(int localX, int y, int localZ, VolcanoBlock block);
    }

    /**
     * Writes the volcano blocks of one chunk in order. TFC's features set their blocks whatever is there, so does this,
     * except outside the chunk or its heights (the bedrock floor included), and on a tree block: TFC's hot springs skip a
     * column under a solid block the decision (which reads the terrain only) does not see, so a tree keeps its blocks.
     *
     * @return the number of blocks written
     */
    static int write(List<VolcanoBlock> blocks, int chunkX, int chunkZ, int minY, int maxY, Target target) {
        int written = 0;
        for (VolcanoBlock b : blocks) {
            if (!b.inChunk(chunkX, chunkZ) || b.y() <= minY || b.y() >= maxY) {
                continue;
            }
            final int lx = b.x() & 15;
            final int lz = b.z() & 15;
            if (isTree(target.type(lx, b.y(), lz))) {
                continue;
            }
            target.place(lx, b.y(), lz, b);
            written++;
        }
        return written;
    }

    /** The tree placeholders of {@link TreeBlocks}: logs, wood, leaves and roots. */
    static boolean isTree(Material type) {
        final String name = type.name();
        return name.endsWith("_LOG") || name.endsWith("_WOOD") || name.endsWith("_LEAVES") || type == Material.ROOTED_DIRT
            || type == Material.MUDDY_MANGROVE_ROOTS;
    }
}
