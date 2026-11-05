/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.blocksound;

import dev.tfp.paper.integration.craftengine.CeBlockSoundTable;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PAR-INFRA-33: what to do with one outgoing sound packet (pure; the PacketEvents glue is {@link BlockSoundPacketHandler}).
 * CraftEngine 26.9.2 sends two sounds of a custom block at fixed values that ignore its configuration:
 * <ul>
 *   <li>the mining loop's hit sound ({@code BukkitServerPlayer}, every 4th tick, to the miner only) at 0.5/0.5, at
 *       the block's corner;</li>
 *   <li>the break sound it makes of the vanilla level event 2001 ({@code LevelEventListener}, to every viewer) at
 *       1.0/0.8, at the block's centre; the breaker also gets CraftEngine's own break sound at the configured values,
 *       so it hears two.</li>
 * </ul>
 * A packet whose position holds one of our blocks ({@link TrackedBlocks}) and whose id (without {@code replaced.}) is
 * that block's hit or break sound gets the TFC volume and pitch of {@link CeBlockSoundTable}; a second break sound of
 * the same block to the same player within {@link #DUPLICATE_NANOS} is dropped. Everything else passes untouched.
 * Thread-safe; called on Netty threads, reads only {@link TrackedBlocks} and the table.
 */
public final class BlockSoundRewrite {
    /** The verdict for one packet. */
    public enum Kind { PASS, SET, DROP }

    /** {@link Kind#SET} carries the volume and pitch to write. */
    public record Decision(Kind kind, float volume, float pitch) {
        public static final Decision PASS = new Decision(Kind.PASS, 0, 0);
        public static final Decision DROP = new Decision(Kind.DROP, 0, 0);

        static Decision set(CeBlockSoundTable.Sound s) {
            return new Decision(Kind.SET, s.volume(), s.pitch());
        }
    }

    /** The two break sound packets of one break arrive in the same tick; half a second leaves room for lag. */
    public static final long DUPLICATE_NANOS = 500_000_000L;

    private record BreakMark(TrackedBlocks.Pos pos, long at) {
    }

    private final TrackedBlocks tracked;
    private volatile CeBlockSoundTable table = CeBlockSoundTable.EMPTY;
    private final Map<UUID, BreakMark> lastBreak = new ConcurrentHashMap<>();

    public BlockSoundRewrite(TrackedBlocks tracked) {
        this.tracked = Objects.requireNonNull(tracked, "tracked");
    }

    public TrackedBlocks tracked() {
        return tracked;
    }

    public CeBlockSoundTable table() {
        return table;
    }

    /** Publishes a newly loaded table. Any thread. */
    public void table(CeBlockSoundTable table) {
        this.table = Objects.requireNonNull(table, "table");
    }

    /** False when nothing can match, so the packet handler need not decode the packet. */
    public boolean active() {
        return !tracked.isEmpty();
    }

    /**
     * @param player  the receiving player
     * @param soundId the packet's sound id ({@code ns:path}, possibly {@code ns:replaced.path})
     * @param x       the packet's position (decoded)
     * @param now     {@link System#nanoTime()}
     */
    public Decision decide(UUID player, String soundId, double x, double y, double z, float volume, float pitch, long now) {
        UUID world = tracked.worldOf(player);
        if (world == null || soundId == null) {
            return Decision.PASS;
        }
        TrackedBlocks.Pos pos = new TrackedBlocks.Pos(world, floor(x), floor(y), floor(z));
        String blockId = tracked.blockAt(pos, now);
        if (blockId == null) {
            return Decision.PASS;
        }
        CeBlockSoundTable.Entry entry = table.entry(blockId).orElse(null);
        if (entry == null) {
            return Decision.PASS;
        }
        String id = CeBlockSoundTable.normalise(soundId);
        if (entry.hit() != null && id.equals(entry.hit().id())) {
            return unchanged(entry.hit(), volume, pitch) ? Decision.PASS : Decision.set(entry.hit());
        }
        if (entry.breakSound() != null && id.equals(entry.breakSound().id())) {
            BreakMark previous = lastBreak.get(player);
            if (previous != null && previous.pos().equals(pos) && now - previous.at() < DUPLICATE_NANOS) {
                return Decision.DROP;
            }
            lastBreak.put(player, new BreakMark(pos, now));
            return unchanged(entry.breakSound(), volume, pitch) ? Decision.PASS : Decision.set(entry.breakSound());
        }
        return Decision.PASS;
    }

    /** Forgets a player that left. */
    public void forget(UUID player) {
        lastBreak.remove(player);
        tracked.forget(player);
    }

    private static boolean unchanged(CeBlockSoundTable.Sound s, float volume, float pitch) {
        return s.volume() == volume && s.pitch() == pitch;
    }

    private static int floor(double v) {
        return (int) Math.floor(v);
    }
}
