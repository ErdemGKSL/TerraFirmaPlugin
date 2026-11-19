/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/rock/MossGrowingBlock.java @ c3b4d9536 (convertToMossy, water check)
 */
package dev.tfp.paper.rock;

import dev.tfp.core.rock.MossSpread;
import dev.tfp.core.world.BlockPos;
import dev.tfp.paper.blockdata.ChunkDataKey;
import dev.tfp.paper.blockdata.ChunkDataStore;
import dev.tfp.paper.catchup.CatchUpService;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.core.persist.VersionedCodec;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Waterlogged;

/**
 * Catches mossy rock blocks up (PAR-GATHER-06). A source replays {@link MossSpread#attempts} spread attempts for the
 * ticks since it was last caught up (since its chunk was unloaded, for the first catch-up after a load); each attempt
 * picks TFC's random offset and converts a cobble/brick block there that touches water. Region thread only; the
 * decision is a handful of random draws, so it runs inline under the catch-up feed budget (one source per call).
 */
public final class MossService {
    /** The catch-up kind mossy blocks are indexed under. */
    public static final String KIND = "tfp:rock/moss";
    /** Chunk PDC: the player tick at which the chunk last unloaded. */
    public static final ChunkDataKey<Long> SYNC_KEY = new ChunkDataKey<>("rock/moss_sync",
            VersionedCodec.<Long>builder("moss_sync", 1).build((v, out) -> out.putLong("t", v), in -> in.requireLong("t")));

    private static final BlockFace[] FACES = {BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH,
        BlockFace.EAST, BlockFace.WEST};

    private final CustomBlocks custom;
    private final CatchUpService catchUp;
    private final ChunkDataStore store;
    private final LongSupplier clock;
    private final boolean enabled;
    private final int rate;
    /** chunk key -> (source -> player tick of its last catch-up); owned by the chunk's region thread. */
    private final Map<String, Map<BlockPos, Long>> synced = new ConcurrentHashMap<>();
    private final Map<String, Long> chunkBase = new ConcurrentHashMap<>();

    MossService(CustomBlocks custom, CatchUpService catchUp, ChunkDataStore store, LongSupplier clock, boolean enabled, int rate) {
        this.custom = custom;
        this.catchUp = catchUp;
        this.store = store;
        this.clock = clock;
        this.enabled = enabled;
        this.rate = rate;
    }

    private static String key(Chunk c) {
        return c.getWorld().getUID() + ":" + c.getX() + "," + c.getZ();
    }

    /** Indexes {@code block} when it is a mossy rock block. Region thread. */
    boolean trackIfMossy(Block block) {
        Optional<BlockStateRef> s = custom.stateAt(block);
        if (s.isPresent() && MossSpread.isMossy(s.get().id())) {
            synced.computeIfAbsent(key(block.getChunk()), k -> new ConcurrentHashMap<>())
                    .put(new BlockPos(block.getX(), block.getY(), block.getZ()), clock.getAsLong());
            return catchUp.track(block, KIND);
        }
        return false;
    }

    /** The {@link dev.tfp.paper.catchup.CatchUpKind.Direct}: false when the block is no longer mossy. */
    boolean catchUp(World world, BlockPos pos) {
        Block source = world.getBlockAt(pos.x(), pos.y(), pos.z());
        Optional<BlockStateRef> state = custom.stateAt(source);
        if (state.isEmpty() || !MossSpread.isMossy(state.get().id())) {
            return false;
        }
        if (!enabled) {
            return true;
        }
        Chunk chunk = source.getChunk();
        String ck = key(chunk);
        long now = clock.getAsLong();
        long base = chunkBase.computeIfAbsent(ck, k -> chunkSyncTick(chunk, now));
        Map<BlockPos, Long> last = synced.computeIfAbsent(ck, k -> new ConcurrentHashMap<>());
        long since = last.getOrDefault(pos, base);
        last.put(pos, now);
        Random random = new Random(world.getSeed() ^ (31L * pos.x() + 17L * pos.y() + 7L * pos.z()) ^ (now * 0x9E3779B97F4A7C15L));
        int attempts = MossSpread.attempts(now - since, MossSpread.RANDOM_TICK_SPEED, rate, random);
        for (int i = 0; i < attempts; i++) {
            int[] o = MossSpread.offset(random);
            spreadTo(world, pos.x() + o[0], pos.y() + o[1], pos.z() + o[2]);
        }
        return true;
    }

    private long chunkSyncTick(Chunk chunk, long fallback) {
        return store.load(chunk, SYNC_KEY).orElse(fallback); // no data: first sight of the chunk, nothing to replay
    }

    private void spreadTo(World world, int x, int y, int z) {
        if (y < world.getMinHeight() || y >= world.getMaxHeight() || !world.isChunkLoaded(x >> 4, z >> 4)
                || !Bukkit.isOwnedByCurrentRegion(world, x >> 4, z >> 4)) {
            return; // TFC's isAreaLoaded gate; a target in another region is skipped (divergence)
        }
        Block target = world.getBlockAt(x, y, z);
        Optional<BlockStateRef> ref = custom.stateAt(target);
        if (ref.isEmpty()) {
            return;
        }
        Optional<String> mossy = MossSpread.mossyOf(ref.get().id());
        if (mossy.isEmpty() || !adjacentToWater(target)) {
            return;
        }
        BlockStateRef next = new BlockStateRef(mossy.get(), ref.get().properties());
        if (custom.place(target, next, false)) {
            trackIfMossy(target);
        }
    }

    private static boolean adjacentToWater(Block block) {
        for (BlockFace face : FACES) {
            int nx = block.getX() + face.getModX();
            int nz = block.getZ() + face.getModZ();
            if (!Bukkit.isOwnedByCurrentRegion(block.getWorld(), nx >> 4, nz >> 4)
                    || !block.getWorld().isChunkLoaded(nx >> 4, nz >> 4)) {
                continue;
            }
            Block n = block.getRelative(face);
            if (n.getType() == org.bukkit.Material.WATER
                    || n.getBlockData() instanceof Waterlogged w && w.isWaterlogged()) {
                return true;
            }
        }
        return false;
    }

    /** Chunk unload: stores the unload tick, drops the chunk's bookkeeping. Region thread. */
    void chunkUnloaded(Chunk chunk) {
        String ck = key(chunk);
        if (synced.remove(ck) != null || chunkBase.remove(ck) != null) {
            store.save(chunk, SYNC_KEY, clock.getAsLong());
        }
    }
}
