/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.weather;

import dev.tfp.core.world.BlockPos;
import dev.tfp.paper.pipeline.BlockAccess;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Block states of one world for the snow pipeline, with the pile memory. Reads and writes must run on the owner of the
 * chunk, which the pipeline guarantees. An unloaded chunk reads as {@code null} (unknown); it is never loaded here.
 *
 * <p>The pile memory (what a snow or ice pile covers) lives in the chunk's persistent data, under {@value #PILES_KEY}:
 * one string entry per pile position, {@code ground} and {@code above} block data strings separated by a newline. It is
 * read only for snow and ice blocks, and written or cleared with every block this access writes. A pile broken by a
 * player leaves its entry until a weather write there replaces it (ledger).
 */
final class WorldBlocks implements BlockAccess<SnowBlock> {
    static final String PILES_KEY = "tfp:snow_piles";
    private static final NamespacedKey PILES = NamespacedKey.fromString(PILES_KEY);

    private final World world;
    private final BlockData air = Material.AIR.createBlockData();

    WorldBlocks(World world) {
        this.world = world;
    }

    @Override
    public SnowBlock read(BlockPos pos) {
        if (!world.isChunkLoaded(pos.chunkX(), pos.chunkZ())) {
            return null;
        }
        BlockData data = world.getBlockData(pos.x(), pos.y(), pos.z());
        Material m = data.getMaterial();
        if (m != Material.SNOW && m != Material.ICE) {
            return SnowBlock.of(data);
        }
        return new SnowBlock(data, readPile(pos), null);
    }

    @Override
    public void write(BlockPos pos, SnowBlock state) {
        Block block = world.getBlockAt(pos.x(), pos.y(), pos.z());
        SnowBlock.Pile pile = state.pile();
        if (pile != null && pile.above() != null) {
            // The pile absorbs the block above: remove it first, without updates, so a two-block plant does not break
            // its lower half (the one the pile replaces). Only if it still is the block the decision saw.
            Block above = block.getRelative(BlockFace.UP);
            if (above.getBlockData().equals(pile.above())) {
                above.setBlockData(air, false);
            } else {
                pile = new SnowBlock.Pile(pile.ground(), null);
            }
        }
        block.setBlockData(state.data(), true);
        writePile(pos, pile);
        if (state.restoreAbove() != null) {
            Block above = block.getRelative(BlockFace.UP);
            if (above.getType().isAir()) {
                above.setBlockData(state.restoreAbove(), true);
            }
        }
    }

    /** The pile entries the chunk holds (the snow this system placed and still tracks), for TFC's per-chunk snow cap. */
    int pileCount(int chunkX, int chunkZ) {
        PersistentDataContainer piles = world.getChunkAt(chunkX, chunkZ).getPersistentDataContainer()
                .get(PILES, PersistentDataType.TAG_CONTAINER);
        return piles == null ? 0 : piles.getKeys().size();
    }

    private SnowBlock.Pile readPile(BlockPos pos) {
        PersistentDataContainer piles = world.getChunkAt(pos.chunkX(), pos.chunkZ()).getPersistentDataContainer()
                .get(PILES, PersistentDataType.TAG_CONTAINER);
        if (piles == null) {
            return null;
        }
        String entry = piles.get(entryKey(pos), PersistentDataType.STRING);
        if (entry == null) {
            return null;
        }
        try {
            int split = entry.indexOf('\n');
            BlockData ground = Bukkit.createBlockData(split < 0 ? entry : entry.substring(0, split));
            BlockData above = split < 0 ? null : Bukkit.createBlockData(entry.substring(split + 1));
            return new SnowBlock.Pile(ground, above);
        } catch (IllegalArgumentException e) {
            return null; // a block that no longer exists: a plain snow layer or ice
        }
    }

    private void writePile(BlockPos pos, SnowBlock.Pile pile) {
        Chunk chunk = world.getChunkAt(pos.chunkX(), pos.chunkZ());
        PersistentDataContainer root = chunk.getPersistentDataContainer();
        PersistentDataContainer piles = root.get(PILES, PersistentDataType.TAG_CONTAINER);
        if (piles == null) {
            if (pile == null) {
                return;
            }
            piles = root.getAdapterContext().newPersistentDataContainer();
        }
        NamespacedKey key = entryKey(pos);
        if (pile == null) {
            if (!piles.has(key)) {
                return;
            }
            piles.remove(key);
        } else {
            String entry = pile.ground().getAsString() + (pile.above() == null ? "" : "\n" + pile.above().getAsString());
            piles.set(key, PersistentDataType.STRING, entry);
        }
        if (piles.isEmpty()) {
            root.remove(PILES);
        } else {
            root.set(PILES, PersistentDataType.TAG_CONTAINER, piles);
        }
    }

    /** One key per block of the chunk: {@code tfp:p<index>}, the index from the height above the world's bottom. */
    private NamespacedKey entryKey(BlockPos pos) {
        long index = ((long) (pos.y() - world.getMinHeight()) << 8) | ((pos.x() & 15) << 4) | (pos.z() & 15);
        return NamespacedKey.fromString("tfp:p" + index);
    }
}
