/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.worldgen;

import dev.tfp.core.worldgen.chunk.PlaceholderRocks;
import dev.tfp.core.worldgen.volcano.VolcanicBlock;
import dev.tfp.core.worldgen.volcano.VolcanicKind;
import java.util.List;
import org.bukkit.Material;

/**
 * PAR-WORLD-27: the blocks of every volcanic feature ({@code VolcanicFeatures}: cinder cones, tuyas, stratovolcanoes, shield
 * volcanoes, ocean ridges, springs). Rock kinds are the CraftEngine rocks of the palette: raw rock as itself, hardened rock
 * as the CraftEngine hardened rock of the rock (PAR-WORLD-45; the raw rock while CraftEngine is not loaded), a gem or sulfur ore as {@code tfp:ore/<ore>/<rock>} when CraftEngine
 * knows it, else its rock. Pumice and obsidian shards are the CraftEngine groundcover blocks when loaded. The rest are vanilla
 * placeholders: lava, water for TFC's spring, fresh and salt water, gravel for rock gravel, magma blocks (the CraftEngine
 * per-rock magma of PAR-WORLD-45 when loaded, else vanilla magma), dirt, and buttons for the groundcover while CraftEngine is not loaded (ledger).
 */
final class VolcanicFeatureBlocks {
    private VolcanicFeatureBlocks() {}

    /** The vanilla block (or placeholder) of a kind, or null for the kinds the rock palette supplies. */
    static Material placeholder(VolcanicKind kind) {
        return switch (kind) {
            case AIR -> Material.AIR;
            case LAVA -> Material.LAVA;
            case SPRING_WATER, WATER, SALT_WATER -> Material.WATER;
            case GRAVEL -> Material.GRAVEL;
            case MAGMA -> Material.MAGMA_BLOCK;
            case DIRT -> Material.DIRT;
            case PUMICE -> Material.BIRCH_BUTTON;
            case OBSIDIAN_SHARD -> Material.DARK_OAK_BUTTON;
            case RAW_ROCK, HARDENED_ROCK, GEM_ORE, SULFUR_ORE -> null;
        };
    }

    /** The CraftEngine block of a groundcover kind ({@code tfp:groundcover/<name>}), else null. */
    static String groundcoverId(VolcanicKind kind) {
        return switch (kind) {
            case PUMICE -> "tfp:groundcover/pumice";
            case OBSIDIAN_SHARD -> "tfp:groundcover/obsidian_shard";
            default -> null;
        };
    }

    /** The raw rock id of a rock block ({@code tfp:rock/raw/<rock>}), the palette's fallback for every rock kind. */
    static String rockId(VolcanicBlock block) {
        return "tfp:rock/raw/" + PlaceholderRocks.names().get(block.rock());
    }

    /** The ore id of a gem or sulfur ore block ({@code tfp:ore/<gem|sulfur>/<rock>}), else null. */
    static String oreId(VolcanicBlock block) {
        return switch (block.kind()) {
            case GEM_ORE -> "tfp:ore/" + block.gem().id() + "/" + PlaceholderRocks.names().get(block.rock());
            case SULFUR_ORE -> "tfp:ore/sulfur/" + PlaceholderRocks.names().get(block.rock());
            default -> null;
        };
    }

    /** The blocks of the generating chunk, by local x and z. */
    interface Target {
        /** The block at a position of the chunk. */
        Material type(int localX, int y, int localZ);

        void place(int localX, int y, int localZ, VolcanicBlock block);
    }

    /**
     * Writes the volcanic blocks of one chunk in order. TFC's features set their blocks whatever is there (each feature reads
     * the level its earlier features wrote, so the decisions already hold their own checks), so does this, except outside the
     * chunk or its heights (the bedrock floor included) and on a tree block, as {@link VolcanoBlocks#write}.
     *
     * @return the number of blocks written
     */
    static int write(List<VolcanicBlock> blocks, int chunkX, int chunkZ, int minY, int maxY, Target target) {
        int written = 0;
        for (VolcanicBlock b : blocks) {
            if (!b.inChunk(chunkX, chunkZ) || b.y() <= minY || b.y() >= maxY) {
                continue;
            }
            final int lx = b.x() & 15;
            final int lz = b.z() & 15;
            if (VolcanoBlocks.isTree(target.type(lx, b.y(), lz))) {
                continue;
            }
            target.place(lx, b.y(), lz, b);
            written++;
        }
        return written;
    }
}
