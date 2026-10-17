/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.climate;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * M2-SEA-02: the rate limit of chunk resends. Pure (the caller passes the tick), thread-safe (one monitor; offered from
 * workers, drained on the global thread, never touched from a Netty thread).
 *
 * <ul>
 *   <li>A key is queued at most once ({@link #offer} is idempotent while it waits).</li>
 *   <li>{@link #drain} hands out at most {@code perTick} keys per call, oldest first.</li>
 *   <li>A key handed out is refused for {@code cooldownTicks} afterwards, so a chunk whose table keeps missing cannot
 *       loop resend, miss, resend. {@link #clearCooldowns()} lifts this at a season change, where every chunk must go out
 *       again.</li>
 * </ul>
 */
public final class ResendLimiter<K> {
    private final int perTick;
    private final long cooldownTicks;
    private final ArrayDeque<K> queue = new ArrayDeque<>();
    private final Set<K> queued = new HashSet<>();
    /** Keys handed out, in the order of their tick (oldest first). */
    private final LinkedHashMap<K, Long> sent = new LinkedHashMap<>();

    public ResendLimiter(int perTick, long cooldownTicks) {
        if (perTick < 1 || cooldownTicks < 0) {
            throw new IllegalArgumentException("perTick " + perTick + ", cooldown " + cooldownTicks);
        }
        this.perTick = perTick;
        this.cooldownTicks = cooldownTicks;
    }

    /**
     * Queues {@code key} for a resend.
     *
     * @return false when it is already queued or was handed out less than the cooldown ago
     */
    public synchronized boolean offer(K key, long nowTick) {
        prune(nowTick);
        if (queued.contains(key) || sent.containsKey(key)) {
            return false;
        }
        queued.add(key);
        queue.addLast(key);
        return true;
    }

    /** Hands out up to {@code perTick} queued keys, oldest first, and starts their cooldown. */
    public synchronized List<K> drain(long nowTick) {
        prune(nowTick);
        int n = Math.min(perTick, queue.size());
        List<K> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            K key = queue.pollFirst();
            queued.remove(key);
            sent.put(key, nowTick);
            out.add(key);
        }
        return out;
    }

    /** Forgets every cooldown (season change). The queue is kept. */
    public synchronized void clearCooldowns() {
        sent.clear();
    }

    /** Keys waiting. */
    public synchronized int pending() {
        return queue.size();
    }

    private void prune(long nowTick) {
        Iterator<Map.Entry<K, Long>> it = sent.entrySet().iterator();
        while (it.hasNext()) {
            if (nowTick - it.next().getValue() < cooldownTicks) {
                return; // insertion order is tick order
            }
            it.remove();
        }
    }
}
