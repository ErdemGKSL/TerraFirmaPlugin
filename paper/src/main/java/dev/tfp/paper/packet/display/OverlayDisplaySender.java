/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.packet.display;

import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.util.Vector3d;
import dev.tfp.core.visuals.OverlayFace;
import dev.tfp.paper.oreoverlay.OverlaySender;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.IntSupplier;

/**
 * {@link OverlaySender} over PacketEvents (PAR-INFRA-16). Users are learnt from the packets {@link OreOverlayPacketHandler}
 * sees; the packets go out with {@code sendPacketSilently}, so our own listeners do not see them again. Entity ids come
 * from the server's counter ({@code UnsafeValues#nextEntityId}), so they never collide with real entities.
 */
public final class OverlayDisplaySender implements OverlaySender {
    private final Map<UUID, User> users = new ConcurrentHashMap<>();
    private final IntSupplier ids;
    private final LongAdder spawned = new LongAdder();
    private final LongAdder removed = new LongAdder();
    private final LongAdder packets = new LongAdder();

    public OverlayDisplaySender(IntSupplier ids) {
        this.ids = ids;
    }

    /** Remembers the connection of a viewer (any thread). */
    public void user(UUID id, User user) {
        if (id != null && user != null) {
            users.put(id, user);
        }
    }

    public void forget(UUID id) {
        users.remove(id);
    }

    public long spawned() {
        return spawned.sum();
    }

    public long removed() {
        return removed.sum();
    }

    public long packets() {
        return packets.sum();
    }

    @Override
    public int nextEntityId() {
        return ids.getAsInt();
    }

    @Override
    public void spawn(UUID viewer, List<Spawn> spawns) {
        User u = users.get(viewer);
        if (u == null) {
            return;
        }
        for (Spawn s : spawns) {
            u.sendPacketSilently(OverlayDisplayPackets.spawn(s.entityId(), OverlayDisplayPackets.position(s.x(), s.y(), s.z(),
                    s.face())));
            u.sendPacketSilently(OverlayDisplayPackets.data(s.entityId(), s.face(), s.itemModel(), s.viewRange()));
        }
        spawned.add(spawns.size());
        packets.add(2L * spawns.size());
    }

    @Override
    public void remove(UUID viewer, int[] ids) {
        User u = users.get(viewer);
        if (u == null || ids.length == 0) {
            return;
        }
        u.sendPacketSilently(OverlayDisplayPackets.remove(ids));
        removed.add(ids.length);
        packets.increment();
    }

    @Override
    public void morph(UUID viewer, int entityId, OverlayFace face, String itemModel) {
        User u = users.get(viewer);
        if (u != null) {
            u.sendPacketSilently(OverlayDisplayPackets.morph(entityId, face, itemModel));
            packets.increment();
        }
    }

    @Override
    public void ride(UUID viewer, int vehicleId, double x, double y, double z, List<Rider> riders) {
        User u = users.get(viewer);
        if (u == null || riders.isEmpty()) {
            return;
        }
        int[] ids = new int[riders.size()];
        for (int i = 0; i < ids.length; i++) {
            Rider r = riders.get(i);
            ids[i] = r.entityId();
            u.sendPacketSilently(OverlayDisplayPackets.spawn(r.entityId(), new Vector3d(x, y, z)));
            u.sendPacketSilently(OverlayDisplayPackets.riderData(r.entityId(), r.face(), r.itemModel(), r.viewRange()));
        }
        u.sendPacketSilently(OverlayDisplayPackets.passengers(vehicleId, ids));
        spawned.add(ids.length);
        packets.add(2L * ids.length + 1);
    }
}
