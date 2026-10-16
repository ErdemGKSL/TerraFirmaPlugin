/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.packet;

import java.util.Set;

/**
 * Watches keep-alive packets (one per player every ~15 s each way) and does nothing: its metrics in
 * {@code /tfp debug packets} prove the layer is installed and show the bare dispatch overhead.
 */
public final class NoopPacketHandler implements PacketHandler {
    public static final String NAME = "noop-keepalive";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Set<String> sendTypes() {
        return Set.of("KEEP_ALIVE");
    }

    @Override
    public Set<String> receiveTypes() {
        return Set.of("KEEP_ALIVE");
    }
}
