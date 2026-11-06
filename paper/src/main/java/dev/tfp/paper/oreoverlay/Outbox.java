/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.oreoverlay;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The overlay packets one decision produced for one viewer, sent in one go (PAR-INFRA-16). A packet handler fills it
 * while the triggering packet (chunk, block change) is being sent and flushes it right after that packet, so the overlays
 * travel in the same flush; other callers flush at once. Removals go first, so a face that changes kind never shows
 * twice. Not thread-safe.
 */
public final class Outbox {
    private final UUID viewer;
    private final List<OverlaySender.Spawn> spawns = new ArrayList<>();
    private final List<int[]> removes = new ArrayList<>();
    private int removeCount;

    public Outbox(UUID viewer) {
        this.viewer = viewer;
    }

    public UUID viewer() {
        return viewer;
    }

    public void spawn(OverlaySender.Spawn s) {
        spawns.add(s);
    }

    public void remove(int[] ids) {
        if (ids.length > 0) {
            removes.add(ids);
            removeCount += ids.length;
        }
    }

    public boolean isEmpty() {
        return spawns.isEmpty() && removes.isEmpty();
    }

    public int spawnCount() {
        return spawns.size();
    }

    public int removeCount() {
        return removeCount;
    }

    /** Sends everything collected (one {@code REMOVE_ENTITIES} for all removals) and empties the box. */
    public void flush(OverlaySender sender) {
        if (!removes.isEmpty()) {
            int[] all = new int[removeCount];
            int i = 0;
            for (int[] r : removes) {
                System.arraycopy(r, 0, all, i, r.length);
                i += r.length;
            }
            sender.remove(viewer, all);
        }
        if (!spawns.isEmpty()) {
            sender.spawn(viewer, List.copyOf(spawns));
        }
        spawns.clear();
        removes.clear();
        removeCount = 0;
    }
}
