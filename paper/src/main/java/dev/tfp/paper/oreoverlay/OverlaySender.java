/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.oreoverlay;

import dev.tfp.core.visuals.OverlayFace;
import java.util.List;
import java.util.UUID;

/**
 * Sends the overlay display entities to one viewer (PAR-INFRA-16). Implemented over PacketEvents in
 * {@code dev.tfp.paper.packet.display.OverlayDisplaySender}, the only place that builds these packets. Thread-safe: the
 * packets go to the viewer's connection from whatever thread calls.
 */
public interface OverlaySender {
    /** One face overlay: the ore block it lies on, the face, the overlay item and its view range. */
    record Spawn(int entityId, int x, int y, int z, OverlayFace face, String itemModel, float viewRange) {
    }

    /** One overlay riding a falling block: its face, item and view range (the vehicle's passengers are set after). */
    record Rider(int entityId, OverlayFace face, String itemModel, float viewRange) {
    }

    /** A fresh entity id from the server's counter (thread-safe). */
    int nextEntityId();

    void spawn(UUID viewer, List<Spawn> spawns);

    /** One {@code REMOVE_ENTITIES} with all ids. */
    void remove(UUID viewer, int[] ids);

    /**
     * Swaps the item of a sent overlay (crack stages), keeping its face shade. Called from any thread (region, Netty,
     * global tick).
     */
    void morph(UUID viewer, int entityId, OverlayFace face, String itemModel);

    /** Spawns overlays at {@code (x, y, z)} and sets them as the passengers of {@code vehicleId}. */
    void ride(UUID viewer, int vehicleId, double x, double y, double z, List<Rider> riders);

    /** Does nothing (no packet library). */
    OverlaySender NONE = new OverlaySender() {
        private final java.util.concurrent.atomic.AtomicInteger ids = new java.util.concurrent.atomic.AtomicInteger(1_000_000_000);

        @Override
        public int nextEntityId() {
            return ids.incrementAndGet();
        }

        @Override
        public void spawn(UUID viewer, List<Spawn> spawns) {
        }

        @Override
        public void remove(UUID viewer, int[] ids) {
        }

        @Override
        public void morph(UUID viewer, int entityId, OverlayFace face, String itemModel) {
        }

        @Override
        public void ride(UUID viewer, int vehicleId, double x, double y, double z, List<Rider> riders) {
        }
    };
}
