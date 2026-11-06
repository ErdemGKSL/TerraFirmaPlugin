/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.packet.display;

import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.netty.buffer.ByteBufHelper;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockBreakAnimation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockChange;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerChunkData;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerJoinGame;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerMultiBlockChange;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerRespawn;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerUnloadChunk;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerUpdateViewPosition;
import dev.tfp.paper.oreoverlay.OreOverlayService;
import dev.tfp.paper.oreoverlay.Outbox;
import dev.tfp.paper.packet.PacketContext;
import dev.tfp.paper.packet.PacketDecision;
import dev.tfp.paper.packet.PacketHandler;
import java.util.Set;
import java.util.UUID;

/**
 * The viewer-packet side of the ore overlays (PAR-INFRA-16, design §3.3): every visible block change reaches a viewer as
 * a packet, whatever caused it (mining, placing, {@code /fill}, explosions, pistons, fluids, CraftEngine placement), so the
 * overlays follow these packets and never miss a source that fires no Bukkit event. Netty thread: reads the service's
 * immutable tables and the viewer's own state only; the overlay packets are queued with
 * {@link PacketSendEvent#getTasksAfterSend()}, so they leave right after the chunk or block change in the same flush.
 * Never modifies or cancels a packet.
 */
public final class OreOverlayPacketHandler implements PacketHandler {
    public static final String NAME = "ore-overlay";

    private final OreOverlayService service;
    private final OverlayDisplaySender sender;

    public OreOverlayPacketHandler(OreOverlayService service, OverlayDisplaySender sender) {
        this.service = service;
        this.sender = sender;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Set<String> sendTypes() {
        return Set.of("JOIN_GAME", "RESPAWN", "UPDATE_VIEW_POSITION", "CHUNK_DATA", "UNLOAD_CHUNK", "BLOCK_CHANGE",
                "MULTI_BLOCK_CHANGE", "SPAWN_ENTITY", "DESTROY_ENTITIES", "BLOCK_BREAK_ANIMATION");
    }

    @Override
    public PacketDecision onSend(PacketContext packet) {
        UUID player = packet.playerId();
        if (player == null) {
            return PacketDecision.PASS;
        }
        PacketSendEvent event = packet.nativeEvent(PacketSendEvent.class);
        Outbox out = new Outbox(player);
        switch (packet.packetType()) {
            case "JOIN_GAME" -> {
                sender.user(player, event.getUser());
                service.viewerWorld(player, service.worldId(new WrapperPlayServerJoinGame(event).getWorldName()));
            }
            case "RESPAWN" -> {
                sender.user(player, event.getUser());
                service.viewerWorld(player,
                        new WrapperPlayServerRespawn(event).getWorldName().map(service::worldId).orElse(null));
            }
            case "UPDATE_VIEW_POSITION" -> {
                WrapperPlayServerUpdateViewPosition w = new WrapperPlayServerUpdateViewPosition(event);
                service.center(player, w.getChunkX(), w.getChunkZ(), out);
            }
            case "CHUNK_DATA" -> {
                long xz = chunkCoords(event);
                service.chunkSent(player, (int) (xz >> 32), (int) xz, out);
            }
            case "UNLOAD_CHUNK" -> {
                WrapperPlayServerUnloadChunk w = new WrapperPlayServerUnloadChunk(event);
                service.chunkUnloaded(player, w.getChunkX(), w.getChunkZ(), out);
            }
            case "BLOCK_CHANGE" -> {
                WrapperPlayServerBlockChange w = new WrapperPlayServerBlockChange(event);
                Vector3i p = w.getBlockPosition();
                service.blockChanged(player, p.getX(), p.getY(), p.getZ(), w.getBlockId(), out);
            }
            case "MULTI_BLOCK_CHANGE" -> {
                for (WrapperPlayServerMultiBlockChange.EncodedBlock b : new WrapperPlayServerMultiBlockChange(event)
                        .getBlocks()) {
                    service.blockChanged(player, b.getX(), b.getY(), b.getZ(), b.getBlockId(), out);
                }
            }
            case "SPAWN_ENTITY" -> {
                if (service.hasFalling()) {
                    WrapperPlayServerSpawnEntity w = new WrapperPlayServerSpawnEntity(event);
                    if (service.fallingKind(w.getEntityId()) >= 0) {
                        int id = w.getEntityId();
                        Vector3d pos = w.getPosition();
                        event.getTasksAfterSend().add(() -> service.rideFalling(player, id, pos.getX(), pos.getY(),
                                pos.getZ()));
                    }
                }
            }
            case "BLOCK_BREAK_ANIMATION" -> {
                // BLOCK_DESTRUCTION: the crumbling stage this client draws; the overlays follow right after it.
                WrapperPlayServerBlockBreakAnimation w = new WrapperPlayServerBlockBreakAnimation(event);
                Vector3i p = w.getBlockPosition();
                int stage = w.getDestroyStage();
                event.getTasksAfterSend().add(() -> service.crackSeen(player, p.getX(), p.getY(), p.getZ(), stage));
            }
            case "DESTROY_ENTITIES" -> {
                if (service.hasRiders(player)) {
                    service.entitiesRemoved(player, new WrapperPlayServerDestroyEntities(event).getEntityIds(), out);
                }
            }
            default -> {
            }
        }
        if (!out.isEmpty()) {
            event.getTasksAfterSend().add(() -> out.flush(sender));
        }
        return PacketDecision.PASS;
    }

    /** The chunk position of a {@code CHUNK_DATA} packet ({@code x << 32 | z}) without decoding its sections. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    static long chunkCoords(PacketSendEvent event) {
        if (event.getLastUsedWrapper() instanceof WrapperPlayServerChunkData data) {
            return ((long) data.getColumn().getX() << 32) | (data.getColumn().getZ() & 0xFFFFFFFFL);
        }
        Object buf = event.getByteBuf();
        int start = ByteBufHelper.readerIndex(buf);
        try {
            PacketWrapper<?> w = new PacketWrapper(event, false);
            int x = w.readInt();
            int z = w.readInt();
            return ((long) x << 32) | (z & 0xFFFFFFFFL);
        } finally {
            ByteBufHelper.readerIndex(buf, start);
        }
    }
}
