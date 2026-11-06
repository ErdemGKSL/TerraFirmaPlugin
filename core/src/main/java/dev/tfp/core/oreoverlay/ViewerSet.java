/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.oreoverlay;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntSupplier;

/**
 * What one viewer was sent (PAR-INFRA-16, design §3.2/§3.3): the overlay entity of every face, by chunk and face key
 * ({@link OverlayKeys#face}), and the difference to what it should see. The per-player cap keeps the nearest faces;
 * the caller limits spawns per call ({@code budget}, from the viewer's {@link TokenBucket}) and retries a chunk that was
 * cut short ({@link Plan#deferred()}). Not thread-safe: one viewer's lock guards it. Entity ids come from the caller.
 */
public final class ViewerSet {
    /** A face the viewer should see: its key in the chunk, overlay kind, outcrop flag and squared distance. */
    public record Wanted(int faceKey, int kind, boolean outcrop, double distSq) {
    }

    /** A spawn to send: the new entity of a face. */
    public record Spawn(long chunk, int faceKey, int kind, boolean outcrop, int entityId) {
    }

    /** Spawns and removals to send, and whether some wanted faces were left for later (cap or budget). */
    public record Plan(List<Spawn> spawns, int[] removes, boolean deferred) {
        public static final Plan NONE = new Plan(List.of(), new int[0], false);

        public boolean isEmpty() {
            return spawns.isEmpty() && removes.length == 0;
        }
    }

    private record Sent(int entityId, int kind, boolean outcrop) {
    }

    private final Map<Long, Map<Integer, Sent>> sent = new HashMap<>();
    private final int cap;
    private int total;

    /** @param cap the most overlay entities this viewer gets ({@code ore-overlay.max-per-player}) */
    public ViewerSet(int cap) {
        if (cap < 0) {
            throw new IllegalArgumentException("cap must be >= 0");
        }
        this.cap = cap;
    }

    public int cap() {
        return cap;
    }

    /** Overlay entities the viewer has now. */
    public int total() {
        return total;
    }

    /** Overlay entities the viewer has in {@code chunk}. */
    public int total(long chunk) {
        Map<Integer, Sent> m = sent.get(chunk);
        return m == null ? 0 : m.size();
    }

    /** The chunks the viewer has overlays in. */
    public Set<Long> chunks() {
        return Set.copyOf(sent.keySet());
    }

    /** The entity id of a sent face, or -1. */
    public int entity(long chunk, int faceKey) {
        Map<Integer, Sent> m = sent.get(chunk);
        Sent s = m == null ? null : m.get(faceKey);
        return s == null ? -1 : s.entityId();
    }

    /** The kind of a sent face, or -1. */
    public int kind(long chunk, int faceKey) {
        Map<Integer, Sent> m = sent.get(chunk);
        Sent s = m == null ? null : m.get(faceKey);
        return s == null ? -1 : s.kind();
    }

    /**
     * Makes the viewer's overlays of {@code chunk} equal to {@code wanted}: removes the faces that are not wanted (or
     * changed kind or range), spawns the missing ones nearest first, at most {@code budget} and never above the cap.
     */
    public Plan reconcile(long chunk, List<Wanted> wanted, IntSupplier ids, int budget) {
        Map<Integer, Sent> have = sent.computeIfAbsent(chunk, k -> new HashMap<>());
        Map<Integer, Wanted> want = new HashMap<>(wanted.size() * 2);
        for (Wanted w : wanted) {
            want.put(w.faceKey(), w);
        }
        List<Integer> removed = new ArrayList<>();
        for (Iterator<Map.Entry<Integer, Sent>> it = have.entrySet().iterator(); it.hasNext();) {
            Map.Entry<Integer, Sent> e = it.next();
            Wanted w = want.get(e.getKey());
            if (w == null || w.kind() != e.getValue().kind() || w.outcrop() != e.getValue().outcrop()) {
                removed.add(e.getValue().entityId());
                it.remove();
                total--;
            }
        }
        List<Wanted> missing = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        for (Wanted w : wanted) {
            if (!have.containsKey(w.faceKey()) && seen.add(w.faceKey())) {
                missing.add(w);
            }
        }
        missing.sort(Comparator.comparingDouble(Wanted::distSq));
        int room = Math.max(0, Math.min(budget, cap - total));
        int n = Math.min(room, missing.size());
        List<Spawn> spawns = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            Wanted w = missing.get(i);
            int id = ids.getAsInt();
            have.put(w.faceKey(), new Sent(id, w.kind(), w.outcrop()));
            total++;
            spawns.add(new Spawn(chunk, w.faceKey(), w.kind(), w.outcrop(), id));
        }
        if (have.isEmpty()) {
            sent.remove(chunk);
        }
        return new Plan(List.copyOf(spawns), toArray(removed), n < missing.size());
    }

    /** One face appearing or vanishing (the fast path): never above the cap, not limited by a budget. */
    public Plan change(long chunk, int faceKey, int kind, boolean outcrop, boolean visible, IntSupplier ids) {
        Map<Integer, Sent> have = sent.get(chunk);
        Sent s = have == null ? null : have.get(faceKey);
        if (!visible) {
            if (s == null || s.kind() != kind) {
                return Plan.NONE;
            }
            have.remove(faceKey);
            total--;
            if (have.isEmpty()) {
                sent.remove(chunk);
            }
            return new Plan(List.of(), new int[] {s.entityId()}, false);
        }
        int[] removes = new int[0];
        if (s != null) {
            if (s.kind() == kind) {
                return Plan.NONE;
            }
            removes = new int[] {s.entityId()};
            have.remove(faceKey);
            total--;
        }
        if (total >= cap) {
            if (have != null && have.isEmpty()) {
                sent.remove(chunk);
            }
            return new Plan(List.of(), removes, true);
        }
        int id = ids.getAsInt();
        sent.computeIfAbsent(chunk, k -> new HashMap<>()).put(faceKey, new Sent(id, kind, outcrop));
        total++;
        return new Plan(List.of(new Spawn(chunk, faceKey, kind, outcrop, id)), removes, false);
    }

    /** Forgets {@code chunk} and returns its entity ids (one {@code REMOVE_ENTITIES} for the chunk). */
    public int[] dropChunk(long chunk) {
        Map<Integer, Sent> m = sent.remove(chunk);
        if (m == null) {
            return new int[0];
        }
        total -= m.size();
        int[] ids = new int[m.size()];
        int i = 0;
        for (Sent s : m.values()) {
            ids[i++] = s.entityId();
        }
        return ids;
    }

    /** Forgets everything (the client dropped its entities: respawn, world change, quit) and returns the ids. */
    public int[] clear() {
        List<Integer> ids = new ArrayList<>();
        for (Map<Integer, Sent> m : sent.values()) {
            for (Sent s : m.values()) {
                ids.add(s.entityId());
            }
        }
        sent.clear();
        total = 0;
        return toArray(ids);
    }

    private static int[] toArray(List<Integer> l) {
        int[] a = new int[l.size()];
        for (int i = 0; i < a.length; i++) {
            a[i] = l.get(i);
        }
        return a;
    }
}
