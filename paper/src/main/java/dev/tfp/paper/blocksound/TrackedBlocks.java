/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.blocksound;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * PAR-INFRA-33: the snapshot the Netty-thread sound rewrite reads instead of the live world. The region thread records
 * which of our CraftEngine blocks sits at a position when a player starts mining it or breaks it (the only moments
 * CraftEngine sends the two fixed-value sounds), and which world each player is in (a sound packet names no world).
 * Entries expire on their own; nothing here touches Bukkit. Thread-safe.
 */
public final class TrackedBlocks {
    /** A block position in a world. */
    public record Pos(UUID world, int x, int y, int z) {
    }

    private record Tracked(String blockId, long expiresAt) {
    }

    /** How long a position being mined stays known (TFC's slowest hand break of a metal block is 500 ticks, 25 s). */
    public static final long MINING_NANOS = 120_000_000_000L;
    /** How long a broken block stays known, for the break sound packets sent after it is gone. */
    public static final long BROKEN_NANOS = 2_000_000_000L;
    private static final int PURGE_EVERY = 256;

    private final Map<Pos, Tracked> blocks = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> playerWorlds = new ConcurrentHashMap<>();
    private final AtomicInteger writes = new AtomicInteger();

    /** Records that CraftEngine block {@code blockId} is at {@code pos} for {@code ttlNanos} from {@code now}. */
    public void track(Pos pos, String blockId, long ttlNanos, long now) {
        blocks.put(pos, new Tracked(blockId, now + ttlNanos));
        if (writes.incrementAndGet() % PURGE_EVERY == 0) {
            purge(now);
        }
    }

    /** The block id recorded at {@code pos} that has not expired, or null. */
    public String blockAt(Pos pos, long now) {
        Tracked t = blocks.get(pos);
        if (t == null) {
            return null;
        }
        if (now - t.expiresAt() > 0) {
            blocks.remove(pos, t);
            return null;
        }
        return t.blockId();
    }

    /** True when no position is recorded (the packet handler then skips decoding). */
    public boolean isEmpty() {
        return blocks.isEmpty();
    }

    public int size() {
        return blocks.size();
    }

    /** Drops expired entries. */
    public void purge(long now) {
        blocks.entrySet().removeIf(e -> now - e.getValue().expiresAt() > 0);
    }

    public void setWorld(UUID player, UUID world) {
        playerWorlds.put(player, world);
    }

    /** The world {@code player} was last seen in, or null. */
    public UUID worldOf(UUID player) {
        return player == null ? null : playerWorlds.get(player);
    }

    public void forget(UUID player) {
        playerWorlds.remove(player);
    }
}
