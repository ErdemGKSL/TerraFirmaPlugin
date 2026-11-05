/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.spike.s2;

import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntitySoundEffect;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSoundEffect;
import java.util.logging.Logger;

/**
 * Spike S2: logs every block sound the server sends ({@code [S2] sound <id> to <player>}), so the replayed SoundType of a
 * renderer cube can be told apart from the carrier's: the client's subtitles are the same ("Block broken") for metal and
 * stone. Client-predicted sounds never reach this log.
 */
final class SoundLog implements PacketListener {
    private final Logger log;

    SoundLog(Logger log) {
        this.log = log;
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        String id = null;
        if (event.getPacketType() == PacketType.Play.Server.SOUND_EFFECT) {
            id = new WrapperPlayServerSoundEffect(event).getSound().getSoundId().toString();
        } else if (event.getPacketType() == PacketType.Play.Server.ENTITY_SOUND_EFFECT) {
            id = new WrapperPlayServerEntitySoundEffect(event).getSound().getSoundId().toString();
        }
        if (id != null && id.contains("block.")) {
            log.info("[S2] sound " + id + " to " + event.getUser().getName());
        }
    }
}
