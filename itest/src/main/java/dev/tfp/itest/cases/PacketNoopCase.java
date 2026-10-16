/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.itest.cases;

import dev.tfp.itest.SelfTestCase;
import dev.tfp.itest.SelfTestContext;
import dev.tfp.paper.TfpPlugin;
import dev.tfp.paper.packet.NoopPacketHandler;
import dev.tfp.paper.packet.PacketHandlerRegistry;
import dev.tfp.paper.packet.PacketHandlerRegistry.HandlerMetrics;
import dev.tfp.paper.packet.PacketLayer;
import dev.tfp.paper.packet.PacketLayers;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;

/**
 * {@code packet.noop_measured} (M1-SVC-03): with PacketEvents installed, our layer has its one listener
 * registered, the no-op keep-alive handler is in the registry, and after {@code tfp.selftest.packetSeconds}
 * (default 20, more than the 15 s keep-alive interval) the registry reports per-handler counts and nanos.
 * Keep-alives only flow to connected players: with none online the case still passes on the installed listener
 * and the metrics row, and says that no packet was measured; with a player online it requires calls &gt; 0.
 */
public final class PacketNoopCase implements SelfTestCase {
    @Override
    public String name() {
        return "packet.noop_measured: one PacketEvents listener, no-op handler measured (M1-SVC-03)";
    }

    static long seconds() {
        return Long.getLong("tfp.selftest.packetSeconds", 20);
    }

    @Override
    public Duration timeout() {
        return Duration.ofSeconds(seconds() + 15);
    }

    @Override
    public CompletableFuture<String> run(SelfTestContext ctx) {
        TfpPlugin tfp = ctx.tfp();
        if (!Bukkit.getPluginManager().isPluginEnabled(PacketLayers.PACKETEVENTS)) {
            throw new Skip("PacketEvents is not installed (the packet layer is optional)");
        }
        PacketLayer layer = tfp.packetLayer();
        PacketHandlerRegistry registry = tfp.packets();
        return OnThread.script("packets", () -> {
            Thread.sleep(seconds() * 1000);
            return check(layer, registry, !Bukkit.getOnlinePlayers().isEmpty(), seconds());
        });
    }

    /** The case body after the wait (unit-tested with a fake layer and a real registry). */
    static String check(PacketLayer layer, PacketHandlerRegistry registry, boolean playersOnline, long waited) {
        if (!layer.installed()) {
            throw new AssertionError("the packet layer has no listener installed: " + layer.describe());
        }
        if (registry == null || !registry.handlers().contains(NoopPacketHandler.NAME)) {
            throw new AssertionError("the no-op handler is not registered: "
                    + (registry == null ? "no registry" : registry.handlers()));
        }
        List<String> rows = new ArrayList<>();
        HandlerMetrics noop = null;
        for (HandlerMetrics m : registry.metrics()) {
            rows.add(String.format(Locale.ROOT, "%s calls=%d total=%dns avg=%dns max=%dns errors=%d", m.name(), m.calls(),
                    m.totalNanos(), m.averageNanos(), m.maxNanos(), m.errors()));
            if (m.name().equals(NoopPacketHandler.NAME)) {
                noop = m;
            }
        }
        if (noop == null) {
            throw new AssertionError("no metrics row for " + NoopPacketHandler.NAME + ": " + rows);
        }
        if (noop.errors() > 0) {
            throw new AssertionError(NoopPacketHandler.NAME + " threw " + noop.errors() + " time(s): " + rows);
        }
        if (playersOnline && noop.calls() == 0) {
            throw new AssertionError("players are online but no keep-alive reached the no-op handler in " + waited
                    + " s: " + rows);
        }
        return layer.describe() + ", listener installed; after " + waited + " s: " + String.join(" | ", rows)
                + (playersOnline ? "" : " (no players online: keep-alives are only sent to connected players)");
    }
}
