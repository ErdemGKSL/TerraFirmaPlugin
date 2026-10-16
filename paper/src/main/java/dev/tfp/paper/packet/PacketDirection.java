/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.packet;

/** {@link #SEND}: server to client (clientbound). {@link #RECEIVE}: client to server (serverbound). */
public enum PacketDirection {
    SEND,
    RECEIVE
}
