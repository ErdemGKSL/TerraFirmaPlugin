/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.packet;

import java.util.UUID;

/**
 * One packet as a {@link PacketHandler} sees it. Valid only during the handler call, on the Netty thread
 * that delivered it; never keep it.
 */
public interface PacketContext {
    PacketDirection direction();

    /** The packet type name, PacketEvents' {@code PacketType} enum name (e.g. {@code CHUNK_DATA}). */
    String packetType();

    /** The player's id, or null while the connection is not yet bound to a player (login). */
    UUID playerId();

    /**
     * The underlying library event ({@code PacketSendEvent} / {@code PacketReceiveEvent} of PacketEvents),
     * for building a wrapper. Only {@link PacketHandler}s written against PacketEvents call this.
     *
     * @throws ClassCastException if the event is not a {@code type}
     */
    <T> T nativeEvent(Class<T> type);
}
