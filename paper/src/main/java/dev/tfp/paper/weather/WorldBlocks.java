/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.weather;

import dev.tfp.core.world.BlockPos;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.BlockVisuals;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.pipeline.BlockAccess;
import java.util.Optional;
import java.util.function.Supplier;
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
 *
 * <p>PAR-CLIM-04: with CraftEngine ({@link #install}) the snow pile, ice pile, sea ice and icicle are the CraftEngine blocks
 * {@value #SNOW_PILE}, {@value #ICE_PILE}, {@value #SEA_ICE} and {@value #ICICLE}: a snow pile is a snow layer with a pile
 * memory, an ice pile is ice with one, sea ice is blue ice, an icicle is downward pointed dripstone (the placeholders the
 * decision works on) and are written and read as those blocks. Without CraftEngine, or while a block is not loaded there,
 * the placeholders themselves are written, as before.
 */
final class WorldBlocks implements BlockAccess<SnowBlock> {
    static final String PILES_KEY = "tfp:snow_piles";
    private static final NamespacedKey PILES = NamespacedKey.fromString(PILES_KEY);

    static final String SNOW_PILE = "tfp:snow_pile";
    static final String ICE_PILE = "tfp:ice_pile";
    static final String SEA_ICE = "tfp:sea_ice";
    static final String ICICLE = "tfp:icicle";

    /** The CraftEngine access the weather blocks are written through ({@link #install}), or null without CraftEngine. */
    record Custom(CustomBlocks blocks, Supplier<BlockVisuals> visuals) {
    }

    private static volatile Custom installed;

    /** Called once by the weather module when it is enabled; {@code null} switches back to the placeholders. */
    static void install(Custom custom) {
        installed = custom;
    }

    private final World world;
    private final Supplier<Custom> custom;
    private final BlockData air = Material.AIR.createBlockData();

    WorldBlocks(World world) {
        this(world, () -> installed);
    }

    WorldBlocks(World world, Supplier<Custom> custom) {
        this.world = world;
        this.custom = custom;
    }

    @Override
    public SnowBlock read(BlockPos pos) {
        if (!world.isChunkLoaded(pos.chunkX(), pos.chunkZ())) {
            return null;
        }
        BlockData data = world.getBlockData(pos.x(), pos.y(), pos.z());
        Custom c = custom.get();
        if (c != null) {
            Optional<BlockStateRef> ref = c.blocks().stateAt(world.getBlockAt(pos.x(), pos.y(), pos.z()));
            if (ref.isPresent()) {
                SnowBlock placeholder = placeholder(ref.get(), pos);
                return placeholder != null ? placeholder : SnowBlock.of(data);
            }
        }
        Material m = data.getMaterial();
        if (m != Material.SNOW && m != Material.ICE) {
            return SnowBlock.of(data);
        }
        return new SnowBlock(data, readPile(pos), null);
    }

    /** The placeholder the decision sees for one of the four weather blocks, or null for any other custom block. */
    private SnowBlock placeholder(BlockStateRef ref, BlockPos pos) {
        switch (ref.id()) {
            case SNOW_PILE -> {
                int layers = clamp(parse(ref.properties().get("layers"), 1), 1, 8);
                return new SnowBlock(snowLayers(layers), pileOrUnknown(pos), null);
            }
            case ICE_PILE -> {
                return new SnowBlock(Material.ICE.createBlockData(), pileOrUnknown(pos), null);
            }
            case SEA_ICE -> {
                return SnowBlock.of(Material.BLUE_ICE.createBlockData());
            }
            case ICICLE -> {
                boolean tip = Boolean.parseBoolean(ref.properties().getOrDefault("tip", "false"));
                return SnowBlock.of(parser.apply(SnowService.ICICLE_TIP.replace("thickness=tip",
                        tip ? "thickness=tip" : "thickness=frustum")));
            }
            default -> {
                return null;
            }
        }
    }

    /** A pile whose memory is gone (the chunk data was cleared) still is a pile; it melts to nothing. */
    private SnowBlock.Pile pileOrUnknown(BlockPos pos) {
        SnowBlock.Pile pile = readPile(pos);
        return pile != null ? pile : new SnowBlock.Pile(air, null);
    }

    /** Builds block data from a state string; tests swap it (MockBukkit knows neither snow layers nor dripstone). */
    static volatile java.util.function.Function<String, BlockData> parser = Bukkit::createBlockData;

    /** A snow layer block of {@code layers}, built from its state string (the block data interface differs by version). */
    static BlockData snowLayers(int layers) {
        return parser.apply("minecraft:snow[layers=" + layers + "]");
    }

    private static int parse(String value, int fallback) {
        try {
            return value == null ? fallback : Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    /** The CraftEngine state a placeholder is written as, or null when it stays the vanilla block. */
    private static BlockStateRef customState(SnowBlock state) {
        BlockData d = state.data();
        Material m = d.getMaterial();
        if (m == Material.SNOW && state.pile() != null) {
            return BlockStateRef.of(SNOW_PILE, java.util.Map.of("layers", Integer.toString(parse(SnowService.property(d.getAsString(), "layers"), 1))));
        }
        if (m == Material.ICE && state.pile() != null) {
            return BlockStateRef.of(ICE_PILE);
        }
        if (m == Material.BLUE_ICE && state.pile() == null) {
            return BlockStateRef.of(SEA_ICE);
        }
        if (SnowService.isDownIcicle(d)) {
            String thickness = SnowService.property(d.getAsString(), "thickness");
            return BlockStateRef.of(ICICLE, java.util.Map.of("tip", Boolean.toString("tip".equals(thickness))));
        }
        return null;
    }

    private static boolean isWeatherId(String id) {
        return id.equals(SNOW_PILE) || id.equals(ICE_PILE) || id.equals(SEA_ICE) || id.equals(ICICLE);
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
        writeBlock(block, state);
        writePile(pos, pile);
        if (state.restoreAbove() != null) {
            Block above = block.getRelative(BlockFace.UP);
            if (above.getType().isAir()) {
                above.setBlockData(state.restoreAbove(), true);
            }
        }
    }

    /**
     * Writes the block: the CraftEngine weather block when there is one for the placeholder (a state change of the same
     * block through its server state, without physics; a new one over a vanilla block through the placement), else the
     * placeholder. A weather block that stays is replaced by what the decision wrote.
     */
    private void writeBlock(Block block, SnowBlock state) {
        Custom c = custom.get();
        Optional<BlockStateRef> now = c == null ? Optional.empty() : c.blocks().stateAt(block);
        BlockStateRef want = c == null ? null : customState(state);
        if (want != null && c.blocks().exists(want.id())) {
            if (now.isPresent() && now.get().id().equals(want.id())) {
                if (!now.get().equals(want)) {
                    c.visuals().get().blockData(want).ifPresent(d -> block.setBlockData(d, false));
                }
                return;
            }
            if (now.isPresent()) {
                c.blocks().remove(block);
            }
            block.setBlockData(air, false);
            if (c.blocks().place(block, want, true)) {
                return;
            }
        } else if (now.isPresent() && isWeatherId(now.get().id())) {
            c.blocks().remove(block);
        }
        block.setBlockData(state.data(), true);
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
