/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.blocksound;

import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSoundEffect;
import dev.tfp.paper.packet.PacketContext;
import dev.tfp.paper.packet.PacketDecision;
import dev.tfp.paper.packet.PacketHandler;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * PAR-INFRA-33: applies {@link BlockSoundRewrite} to outgoing {@code SOUND_EFFECT} packets (CraftEngine sends its block
 * sounds as positioned sound packets). Linked against PacketEvents; {@link BlockSoundModule} creates it reflectively,
 * only when PacketEvents is installed. Netty thread: reads only the rewrite's snapshots.
 */
public final class BlockSoundPacketHandler implements PacketHandler {
    public static final String NAME = "block-sound-volume";
    /** {@code -Dtfp.blocksound.log=true} logs every rewritten or dropped packet (in-game check); off by default. */
    public static final String LOG_PROPERTY = "tfp.blocksound.log";

    private final BlockSoundRewrite rewrite;
    private final Logger log;

    /** @param log the debug log; used only when {@link #LOG_PROPERTY} is set */
    public BlockSoundPacketHandler(BlockSoundRewrite rewrite, Logger log) {
        this.rewrite = rewrite;
        this.log = Boolean.getBoolean(LOG_PROPERTY) ? log : null;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Set<String> sendTypes() {
        return Set.of("SOUND_EFFECT");
    }

    @Override
    public PacketDecision onSend(PacketContext packet) {
        UUID player = packet.playerId();
        if (player == null || !rewrite.active()) {
            return PacketDecision.PASS;
        }
        PacketSendEvent event = packet.nativeEvent(PacketSendEvent.class);
        WrapperPlayServerSoundEffect sound = new WrapperPlayServerSoundEffect(event);
        if (sound.getSound() == null || sound.getSound().getSoundId() == null) {
            return PacketDecision.PASS;
        }
        Vector3d p = sound.getPosition();
        BlockSoundRewrite.Decision d = rewrite.decide(player, sound.getSound().getSoundId().toString(), p.getX(), p.getY(),
                p.getZ(), sound.getVolume(), sound.getPitch(), System.nanoTime());
        if (log != null && d.kind() != BlockSoundRewrite.Kind.PASS) {
            log.info("[tfp-sound] " + d.kind() + " " + sound.getSound().getSoundId() + " " + sound.getVolume() + "/"
                    + sound.getPitch() + " -> " + d.volume() + "/" + d.pitch() + " to " + playerName(event, player)
                    + (d.kind() == BlockSoundRewrite.Kind.DROP ? " (cancelled; a MONITOR listener may still log it)"
                            : ""));
        }
        return switch (d.kind()) {
            case PASS -> PacketDecision.PASS;
            case DROP -> PacketDecision.CANCEL;
            case SET -> {
                sound.setVolume(d.volume());
                sound.setPitch(d.pitch());
                yield PacketDecision.MODIFIED;
            }
        };
    }

    /** The player's name for the debug log (the UUID when PacketEvents has no name yet). */
    private static String playerName(PacketSendEvent event, UUID player) {
        String name = event.getUser() == null ? null : event.getUser().getName();
        return name == null ? player.toString() : name + " (" + player + ")";
    }
}
