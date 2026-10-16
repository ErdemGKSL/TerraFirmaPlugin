/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.packet;

/**
 * The packet library hook-up: feeds every packet to a {@link PacketHandlerRegistry}. Installed from our
 * {@code onEnable} ({@link PacketLayers#create}); the library must be running, so unit tests use the
 * registry directly with fake contexts.
 */
public interface PacketLayer {
    /** Installs one listener that dispatches into {@code registry}. Called once. */
    void install(PacketHandlerRegistry registry);

    /** Removes the listener (plugin disable). Idempotent. */
    void uninstall();

    /** For {@code /tfp version}/{@code debug packets}: which library, or why there is none. */
    String describe();
}
