/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.packet;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketListenerCommon;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.protocol.player.User;
import java.util.UUID;

/**
 * {@link PacketLayer} on PacketEvents (decided by M0-NET-01: no NMS, Netty-thread listeners, biome ids from
 * the per-user registry). One listener at {@code NORMAL} priority feeds every packet to the registry; packets
 * no handler wants cost one map lookup.
 *
 * <p>Loaded only through {@link PacketLayers#create} when the PacketEvents plugin is installed: this class
 * links against PacketEvents and must not be touched otherwise.
 */
final class PacketEventsLayer implements PacketLayer {
    private PacketListenerCommon listener;

    @Override
    public synchronized void install(PacketHandlerRegistry registry) {
        if (listener != null) {
            throw new IllegalStateException("packet layer already installed");
        }
        listener = PacketEvents.getAPI().getEventManager().registerListener(new Bridge(registry), PacketListenerPriority.NORMAL);
    }

    @Override
    public synchronized void uninstall() {
        if (listener != null) {
            PacketEvents.getAPI().getEventManager().unregisterListener(listener);
            listener = null;
        }
    }

    @Override
    public String describe() {
        return "PacketEvents " + PacketEvents.getAPI().getVersion();
    }

    private record Bridge(PacketHandlerRegistry registry) implements PacketListener {
        @Override
        public void onPacketSend(PacketSendEvent event) {
            dispatch(event, PacketDirection.SEND);
        }

        @Override
        public void onPacketReceive(PacketReceiveEvent event) {
            dispatch(event, PacketDirection.RECEIVE);
        }

        private void dispatch(ProtocolPacketEvent event, PacketDirection direction) {
            PacketTypeCommon type = event.getPacketType();
            if (type == null) {
                return;
            }
            String name = type.getName();
            if (!registry.wants(direction, name)) {
                return;
            }
            switch (registry.dispatch(new Context(event, direction, name))) {
                case MODIFIED -> event.markForReEncode(true);
                case CANCEL -> event.setCancelled(true);
                case PASS -> {
                }
            }
        }
    }

    private record Context(ProtocolPacketEvent event, PacketDirection direction, String packetType)
            implements PacketContext {
        @Override
        public UUID playerId() {
            User user = event.getUser();
            return user == null ? null : user.getUUID();
        }

        @Override
        public <T> T nativeEvent(Class<T> type) {
            return type.cast(event);
        }
    }
}
