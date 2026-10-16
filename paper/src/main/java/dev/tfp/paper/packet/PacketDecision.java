/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.packet;

/** What a {@link PacketHandler} did with a packet. Combined over all handlers: CANCEL &gt; MODIFIED &gt; PASS. */
public enum PacketDecision {
    /** Untouched. */
    PASS,
    /** The handler changed the packet through a wrapper: it must be re-encoded. */
    MODIFIED,
    /** Drop the packet. Later handlers are not called. */
    CANCEL
}
