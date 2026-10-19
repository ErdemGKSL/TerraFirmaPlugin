/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.packet;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.configuration.client.WrapperConfigClientSettings;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientSettings;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Remembers each player's client language from the {@code CLIENT_SETTINGS} packet (sent in the configuration phase and
 * again when the player changes the setting), so the Netty thread can translate the text it adds to item packets
 * without touching a Bukkit player. The code is Minecraft's lower-case form, such as {@code tr_tr}.
 *
 * <p>Netty thread for the packet, any thread for {@link #locale}; a player who has not sent settings reads as
 * {@value #FALLBACK}.
 */
public final class ClientLocaleHandler implements PacketHandler, Listener {
    public static final String NAME = "client-locale";
    public static final String FALLBACK = "en_us";

    private final ConcurrentHashMap<UUID, String> locales = new ConcurrentHashMap<>();

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Set<String> receiveTypes() {
        return Set.of("CLIENT_SETTINGS");
    }

    @Override
    public PacketDecision onReceive(PacketContext packet) {
        UUID player = packet.playerId();
        if (player == null) {
            return PacketDecision.PASS;
        }
        PacketReceiveEvent event = packet.nativeEvent(PacketReceiveEvent.class);
        String locale = event.getPacketType() == PacketType.Configuration.Client.CLIENT_SETTINGS
                ? new WrapperConfigClientSettings(event).getLocale()
                : new WrapperPlayClientSettings(event).getLocale();
        if (locale != null && !locale.isBlank()) {
            locales.put(player, locale.toLowerCase(Locale.ROOT).replace('-', '_'));
        }
        return PacketDecision.PASS;
    }

    /** The player's client language code, or {@value #FALLBACK}. Any thread. */
    public String locale(UUID player) {
        return player == null ? FALLBACK : locales.getOrDefault(player, FALLBACK);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        locales.remove(event.getPlayer().getUniqueId());
    }
}
