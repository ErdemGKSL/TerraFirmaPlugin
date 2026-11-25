/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.rotation.spin;

import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.netty.buffer.ByteBufHelper;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerChunkData;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerJoinGame;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerRespawn;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerUnloadChunk;
import dev.tfp.paper.packet.PacketContext;
import dev.tfp.paper.packet.PacketDecision;
import dev.tfp.paper.packet.PacketHandler;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * The viewers of the spinning shafts (PAR-MECH-12), from the packets a player receives, as the ore overlays learn theirs
 * (PAR-INFRA-16): the world from {@code JOIN_GAME} and {@code RESPAWN}, the chunks from {@code CHUNK_DATA} (the shafts spawn
 * right after the chunk, in the same flush) and {@code UNLOAD_CHUNK}. Netty thread; never modifies or cancels a packet.
 * PAR-MECH-26: the crankshafts' parts ({@link SpinCranks}) learn their viewers from the same packets.
 */
public final class SpinPacketHandler implements PacketHandler {
    public static final String NAME = "rotation-spin";

    private final SpinShafts shafts;
    private final PacketSpinWire wire;
    private final Function<String, UUID> worlds;
    private final SpinCranks cranks;

    /**
     * @param worlds the world of a dimension key ({@code minecraft:overworld}), null when unknown
     */
    public SpinPacketHandler(SpinShafts shafts, PacketSpinWire wire, Function<String, UUID> worlds) {
        this(shafts, wire, worlds, null);
    }

    /** PAR-MECH-26: also feeds the crankshafts' viewers ({@code cranks} may be null). */
    public SpinPacketHandler(SpinShafts shafts, PacketSpinWire wire, Function<String, UUID> worlds, SpinCranks cranks) {
        this.shafts = shafts;
        this.wire = wire;
        this.worlds = worlds;
        this.cranks = cranks;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Set<String> sendTypes() {
        return Set.of("JOIN_GAME", "RESPAWN", "CHUNK_DATA", "UNLOAD_CHUNK");
    }

    @Override
    public PacketDecision onSend(PacketContext packet) {
        UUID player = packet.playerId();
        if (player == null) {
            return PacketDecision.PASS;
        }
        PacketSendEvent event = packet.nativeEvent(PacketSendEvent.class);
        switch (packet.packetType()) {
            case "JOIN_GAME" -> {
                wire.user(player, event.getUser());
                UUID world = worlds.apply(new WrapperPlayServerJoinGame(event).getWorldName());
                shafts.viewerWorld(player, world);
                if (cranks != null) {
                    cranks.viewerWorld(player, world);
                }
            }
            case "RESPAWN" -> {
                wire.user(player, event.getUser());
                UUID world = new WrapperPlayServerRespawn(event).getWorldName().map(worlds).orElse(null);
                shafts.viewerWorld(player, world);
                if (cranks != null) {
                    cranks.viewerWorld(player, world);
                }
            }
            case "CHUNK_DATA" -> {
                wire.user(player, event.getUser());
                long xz = chunkCoords(event);
                event.getTasksAfterSend().add(() -> {
                    shafts.chunkSent(player, (int) (xz >> 32), (int) xz);
                    if (cranks != null) {
                        cranks.chunkSent(player, (int) (xz >> 32), (int) xz);
                    }
                });
            }
            case "UNLOAD_CHUNK" -> {
                WrapperPlayServerUnloadChunk w = new WrapperPlayServerUnloadChunk(event);
                shafts.chunkUnloaded(player, w.getChunkX(), w.getChunkZ());
                if (cranks != null) {
                    cranks.chunkUnloaded(player, w.getChunkX(), w.getChunkZ());
                }
            }
            default -> {
            }
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
