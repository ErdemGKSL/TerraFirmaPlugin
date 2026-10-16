/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.packet;

import java.util.Set;

/**
 * A packet rewriter (seasonal biome remap, item lore, creative-return stripping; ARCHITECTURE.md §10).
 * Registered with {@link PacketHandlerRegistry}; called for the packet types it lists.
 *
 * <h2>Netty-thread rules</h2>
 * Handlers run on the connection's Netty event-loop thread (verified for PacketEvents by M0-NET-01: never
 * the server or a region thread). Therefore a handler:
 * <ul>
 *   <li><b>never touches the world</b>: no Bukkit block/entity/world/inventory API, no CraftEngine/BetterModel
 *       calls, no scheduler joins. It may read only immutable snapshots published elsewhere through
 *       volatile/atomic fields (the global clock, season remap tables, registries, per-item caches);</li>
 *   <li><b>never blocks</b>: no I/O, no locks shared with tick threads, no waiting on futures;</li>
 *   <li><b>stays cheap and allocation-light</b>: precompute tables off-thread, cache by bucketed keys.
 *       Per-handler time is measured ({@link PacketHandlerRegistry#metrics()});</li>
 *   <li>mutates only the packet's own copy, through the library wrapper, and returns
 *       {@link PacketDecision#MODIFIED} when it did. The server-side item stacks are never changed;</li>
 *   <li>is thread-safe: several Netty threads call it at once (one per connection group).</li>
 * </ul>
 * A handler that throws is counted and logged (rate-limited), and the packet continues as
 * {@link PacketDecision#PASS}.
 */
public interface PacketHandler {
    /** Wildcard for {@link #sendTypes()}/{@link #receiveTypes()}: every packet. Expensive; avoid. */
    String ALL = "*";

    /** Stable name, used in metrics and logs. */
    String name();

    /** Clientbound packet type names to receive in {@link #onSend} (PacketEvents enum names). */
    default Set<String> sendTypes() {
        return Set.of();
    }

    /** Serverbound packet type names to receive in {@link #onReceive}. */
    default Set<String> receiveTypes() {
        return Set.of();
    }

    /** A clientbound packet of one of {@link #sendTypes()}. Netty thread. */
    default PacketDecision onSend(PacketContext packet) {
        return PacketDecision.PASS;
    }

    /** A serverbound packet of one of {@link #receiveTypes()}. Netty thread. */
    default PacketDecision onReceive(PacketContext packet) {
        return PacketDecision.PASS;
    }
}
