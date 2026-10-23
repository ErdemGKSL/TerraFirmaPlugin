/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.catchup;

import dev.tfp.core.persist.DataMap;
import dev.tfp.core.persist.PersistException;
import dev.tfp.core.persist.VersionedCodec;
import dev.tfp.core.world.BlockPos;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * The lazy blocks of one chunk (PAR-INFRA-11): position → catch-up kind id. A lazy block is one whose state is a
 * stored timestamp plus base values (a crop, a bush, a torch, drying bricks, a device); it is not ticked, so this index
 * tells the catch-up service which blocks to bring up to date when the chunk loads or a player watches it
 * (NP-SIM-01, substitute of TFC's per-tick {@code ICalendarTickable.checkForCalendarUpdate}).
 *
 * <p>Immutable; entries are kept in (y, z, x) order so the encoding is deterministic. Positions must lie in the chunk.
 * At most {@link #MAX_ENTRIES} entries (every block of a 384-high chunk). Pure; any thread.
 */
public final class LazyBlockIndex {
    /** 16 × 16 × 384. */
    public static final int MAX_ENTRIES = 16 * 16 * 384;
    /** A kind id: a namespaced id such as {@code tfp:crop}. */
    public static final Pattern KIND_ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    private static final Comparator<BlockPos> ORDER = Comparator.comparingInt(BlockPos::y)
            .thenComparingInt(BlockPos::z).thenComparingInt(BlockPos::x);

    private final int chunkX;
    private final int chunkZ;
    private final Map<BlockPos, String> entries;

    private LazyBlockIndex(int chunkX, int chunkZ, Map<BlockPos, String> sorted) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.entries = Collections.unmodifiableMap(sorted);
    }

    /** The empty index of chunk ({@code chunkX}, {@code chunkZ}). */
    public static LazyBlockIndex empty(int chunkX, int chunkZ) {
        return new LazyBlockIndex(chunkX, chunkZ, new LinkedHashMap<>());
    }

    /** An index holding {@code entries}; every position must lie in the chunk and every kind id be valid. */
    public static LazyBlockIndex of(int chunkX, int chunkZ, Map<BlockPos, String> entries) {
        TreeMap<BlockPos, String> sorted = new TreeMap<>(ORDER);
        for (Map.Entry<BlockPos, String> e : entries.entrySet()) {
            check(chunkX, chunkZ, e.getKey(), e.getValue());
            sorted.put(e.getKey(), e.getValue());
        }
        if (sorted.size() > MAX_ENTRIES) {
            throw new IllegalArgumentException("more than " + MAX_ENTRIES + " lazy blocks in one chunk");
        }
        return new LazyBlockIndex(chunkX, chunkZ, new LinkedHashMap<>(sorted));
    }

    private static void check(int chunkX, int chunkZ, BlockPos pos, String kind) {
        Objects.requireNonNull(pos, "pos");
        if (pos.chunkX() != chunkX || pos.chunkZ() != chunkZ) {
            throw new IllegalArgumentException(pos + " is not in chunk " + chunkX + "," + chunkZ);
        }
        checkKind(kind);
    }

    /** Fails unless {@code kind} matches {@link #KIND_ID}. */
    public static String checkKind(String kind) {
        Objects.requireNonNull(kind, "kind");
        if (!KIND_ID.matcher(kind).matches()) {
            throw new IllegalArgumentException("bad catch-up kind id '" + kind + "': must match " + KIND_ID.pattern());
        }
        return kind;
    }

    public int chunkX() {
        return chunkX;
    }

    public int chunkZ() {
        return chunkZ;
    }

    /** Position → kind id, in (y, z, x) order. Unmodifiable. */
    public Map<BlockPos, String> entries() {
        return entries;
    }

    public int size() {
        return entries.size();
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public Optional<String> kindAt(BlockPos pos) {
        return Optional.ofNullable(entries.get(pos));
    }

    /** This index with {@code pos} set to {@code kind}; {@code this} when it already is. */
    public LazyBlockIndex with(BlockPos pos, String kind) {
        check(chunkX, chunkZ, pos, kind);
        if (kind.equals(entries.get(pos))) {
            return this;
        }
        Map<BlockPos, String> next = new LinkedHashMap<>(entries);
        next.put(pos, kind);
        return of(chunkX, chunkZ, next);
    }

    /** This index without {@code positions}; {@code this} when none was indexed. */
    public LazyBlockIndex without(Collection<BlockPos> positions) {
        boolean any = false;
        for (BlockPos p : positions) {
            if (entries.containsKey(p)) {
                any = true;
                break;
            }
        }
        if (!any) {
            return this;
        }
        Map<BlockPos, String> next = new LinkedHashMap<>(entries);
        next.keySet().removeAll(positions);
        return new LazyBlockIndex(chunkX, chunkZ, next);
    }

    public LazyBlockIndex without(BlockPos pos) {
        return without(List.of(pos));
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof LazyBlockIndex i && i.chunkX == chunkX && i.chunkZ == chunkZ && i.entries.equals(entries);
    }

    @Override
    public int hashCode() {
        return Objects.hash(chunkX, chunkZ, entries);
    }

    @Override
    public String toString() {
        return "LazyBlockIndex[" + chunkX + "," + chunkZ + ": " + entries.size() + " entries]";
    }

    /** The current layout version of {@link #CODEC}. */
    public static final int VERSION = 1;

    /**
     * Layout v1: {@code cx}, {@code cz}, {@code kinds} (list of {@code {id}}: the kind table), {@code e} (bytes, 8 per
     * entry, big endian: {@code int} kind table index, {@code int} packed position {@code y << 8 | localZ << 4 | localX}).
     * Immutable and thread-safe.
     */
    public static final VersionedCodec<LazyBlockIndex> CODEC = VersionedCodec.<LazyBlockIndex>builder("lazy_blocks",
            VERSION).build(LazyBlockIndex::write, LazyBlockIndex::read);

    private static void write(LazyBlockIndex index, DataMap out) {
        Map<String, Integer> table = new LinkedHashMap<>();
        ByteBuffer buf = ByteBuffer.allocate(index.entries.size() * 8);
        for (Map.Entry<BlockPos, String> e : index.entries.entrySet()) {
            int k = table.computeIfAbsent(e.getValue(), id -> table.size());
            BlockPos p = e.getKey();
            buf.putInt(k).putInt((p.y() << 8) | ((p.z() & 15) << 4) | (p.x() & 15));
        }
        List<DataMap> kinds = new ArrayList<>(table.size());
        for (String id : table.keySet()) {
            kinds.add(new DataMap().putString("id", id));
        }
        out.putInt("cx", index.chunkX).putInt("cz", index.chunkZ).putList("kinds", kinds).putBytes("e", buf.array());
    }

    private static LazyBlockIndex read(DataMap in) {
        int cx = in.requireInt("cx");
        int cz = in.requireInt("cz");
        List<DataMap> kindMaps = in.getList("kinds");
        List<String> kinds = new ArrayList<>(kindMaps.size());
        for (DataMap m : kindMaps) {
            kinds.add(m.requireString("id"));
        }
        byte[] bytes = in.getBytes("e", new byte[0]);
        if (bytes.length % 8 != 0) {
            throw new PersistException("lazy block entries: " + bytes.length + " bytes is not a multiple of 8");
        }
        ByteBuffer buf = ByteBuffer.wrap(bytes);
        Map<BlockPos, String> entries = new LinkedHashMap<>();
        while (buf.hasRemaining()) {
            int k = buf.getInt();
            int packed = buf.getInt();
            if (k < 0 || k >= kinds.size()) {
                throw new PersistException("lazy block entry: kind index " + k + " outside the table");
            }
            BlockPos pos = new BlockPos((cx << 4) | (packed & 15), packed >> 8, (cz << 4) | ((packed >> 4) & 15));
            entries.put(pos, kinds.get(k));
        }
        try {
            return of(cx, cz, entries);
        } catch (IllegalArgumentException e) {
            throw new PersistException("lazy block index: " + e.getMessage(), e);
        }
    }
}
