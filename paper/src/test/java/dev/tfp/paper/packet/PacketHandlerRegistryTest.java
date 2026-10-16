/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.packet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

/**
 * M1-SVC-03 without a server: the handler registry and its metrics, driven by fake packet contexts (the
 * PacketEvents listener itself needs a running server; its install is wired in {@code TfpPlugin#onEnable}).
 */
class PacketHandlerRegistryTest {
    record FakePacket(PacketDirection direction, String packetType, UUID playerId, Object event) implements PacketContext {
        @Override
        public <T> T nativeEvent(Class<T> type) {
            return type.cast(event);
        }
    }

    static FakePacket send(String type) {
        return new FakePacket(PacketDirection.SEND, type, UUID.randomUUID(), new Object());
    }

    static FakePacket receive(String type) {
        return new FakePacket(PacketDirection.RECEIVE, type, null, new Object());
    }

    /** A handler built from lambdas, recording what it saw. */
    static final class TestHandler implements PacketHandler {
        final String name;
        final Set<String> send;
        final Set<String> receive;
        final Function<PacketContext, PacketDecision> action;
        final List<String> seen = new ArrayList<>();

        TestHandler(String name, Set<String> send, Set<String> receive, Function<PacketContext, PacketDecision> action) {
            this.name = name;
            this.send = send;
            this.receive = receive;
            this.action = action;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public Set<String> sendTypes() {
            return send;
        }

        @Override
        public Set<String> receiveTypes() {
            return receive;
        }

        @Override
        public PacketDecision onSend(PacketContext p) {
            seen.add("send " + p.packetType());
            return action.apply(p);
        }

        @Override
        public PacketDecision onReceive(PacketContext p) {
            seen.add("receive " + p.packetType());
            return action.apply(p);
        }
    }

    static final Logger LOG = Logger.getLogger("PacketHandlerRegistryTest");

    @Test
    void routesByDirectionAndTypeAndCombinesDecisions() {
        PacketHandlerRegistry r = new PacketHandlerRegistry(LOG);
        TestHandler biome = new TestHandler("biome", Set.of("CHUNK_DATA"), Set.of(), p -> PacketDecision.MODIFIED);
        TestHandler lore = new TestHandler("lore", Set.of("SET_SLOT", "WINDOW_ITEMS"), Set.of(), p -> PacketDecision.PASS);
        TestHandler creative = new TestHandler("creative", Set.of(), Set.of("CREATIVE_INVENTORY_ACTION"), p -> PacketDecision.MODIFIED);
        r.register(biome);
        r.register(lore);
        r.register(creative);

        assertThat(r.wants(PacketDirection.SEND, "CHUNK_DATA")).isTrue();
        assertThat(r.wants(PacketDirection.RECEIVE, "CHUNK_DATA")).isFalse();
        assertThat(r.wants(PacketDirection.SEND, "ENTITY_VELOCITY")).isFalse();

        assertThat(r.dispatch(send("CHUNK_DATA"))).isEqualTo(PacketDecision.MODIFIED);
        assertThat(r.dispatch(send("SET_SLOT"))).isEqualTo(PacketDecision.PASS);
        assertThat(r.dispatch(send("ENTITY_VELOCITY"))).isEqualTo(PacketDecision.PASS);
        assertThat(r.dispatch(receive("CREATIVE_INVENTORY_ACTION"))).isEqualTo(PacketDecision.MODIFIED);

        assertThat(biome.seen).containsExactly("send CHUNK_DATA");
        assertThat(lore.seen).containsExactly("send SET_SLOT");
        assertThat(creative.seen).containsExactly("receive CREATIVE_INVENTORY_ACTION");
    }

    @Test
    void cancelStopsTheChainAndWildcardsRunAfterTypedHandlers() {
        PacketHandlerRegistry r = new PacketHandlerRegistry(LOG);
        List<String> order = new ArrayList<>();
        r.register(new TestHandler("all", Set.of(PacketHandler.ALL), Set.of(), p -> {
            order.add("all");
            return PacketDecision.PASS;
        }));
        r.register(new TestHandler("typed", Set.of("SYSTEM_CHAT_MESSAGE"), Set.of(), p -> {
            order.add("typed");
            return PacketDecision.MODIFIED;
        }));
        assertThat(r.dispatch(send("SYSTEM_CHAT_MESSAGE"))).isEqualTo(PacketDecision.MODIFIED);
        assertThat(order).containsExactly("typed", "all");

        r.register(new TestHandler("blocker", Set.of("SYSTEM_CHAT_MESSAGE"), Set.of(), p -> PacketDecision.CANCEL));
        order.clear();
        assertThat(r.dispatch(send("SYSTEM_CHAT_MESSAGE"))).isEqualTo(PacketDecision.CANCEL);
        assertThat(order).containsExactly("typed"); // "all" never ran
        assertThat(r.wants(PacketDirection.SEND, "ANYTHING")).isTrue();
    }

    @Test
    void metricsCountCallsTimeAndOutcomes() {
        AtomicLong now = new AtomicLong();
        PacketHandlerRegistry r = new PacketHandlerRegistry(LOG, now::get);
        r.register(new TestHandler("slow", Set.of("CHUNK_DATA"), Set.of(), p -> {
            now.addAndGet(p.playerId() == null ? 0 : 1_500); // each call "takes" 1.5 µs
            return PacketDecision.MODIFIED;
        }));
        r.register(new NoopPacketHandler());
        for (int i = 0; i < 4; i++) {
            r.dispatch(send("CHUNK_DATA"));
        }
        r.dispatch(send("KEEP_ALIVE"));
        r.dispatch(receive("KEEP_ALIVE"));

        PacketHandlerRegistry.HandlerMetrics slow = r.metrics().get(0);
        assertThat(slow.name()).isEqualTo("slow");
        assertThat(slow.calls()).isEqualTo(4);
        assertThat(slow.totalNanos()).isEqualTo(6_000);
        assertThat(slow.maxNanos()).isEqualTo(1_500);
        assertThat(slow.averageNanos()).isEqualTo(1_500);
        assertThat(slow.modified()).isEqualTo(4);
        PacketHandlerRegistry.HandlerMetrics noop = r.metrics().get(1);
        assertThat(noop.name()).isEqualTo(NoopPacketHandler.NAME);
        assertThat(noop.calls()).isEqualTo(2);
        assertThat(noop.modified() + noop.cancelled() + noop.errors()).isZero();
    }

    @Test
    void aFailingHandlerIsCountedLoggedAndThePacketPasses() {
        List<LogRecord> logged = new ArrayList<>();
        Logger log = Logger.getLogger("PacketHandlerRegistryTest.errors");
        log.setUseParentHandlers(false);
        Handler capture = new Handler() {
            @Override
            public void publish(LogRecord record) {
                logged.add(record);
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        log.addHandler(capture);
        try {
            PacketHandlerRegistry r = new PacketHandlerRegistry(log);
            r.register(new TestHandler("broken", Set.of("SET_SLOT"), Set.of(), p -> {
                throw new IllegalStateException("boom");
            }));
            r.register(new TestHandler("after", Set.of("SET_SLOT"), Set.of(), p -> PacketDecision.MODIFIED));
            for (int i = 0; i < 3; i++) {
                assertThat(r.dispatch(send("SET_SLOT"))).isEqualTo(PacketDecision.MODIFIED);
            }
            assertThat(r.metrics().get(0).errors()).isEqualTo(3);
            assertThat(r.metrics().get(1).calls()).isEqualTo(3);
            assertThat(logged).hasSize(1); // rate-limited: first error only (then every 1000th)
        } finally {
            log.removeHandler(capture);
        }
    }

    @Test
    void registrationRulesAndConcurrentDispatch() throws Exception {
        PacketHandlerRegistry r = new PacketHandlerRegistry(LOG);
        r.register(new NoopPacketHandler());
        assertThatThrownBy(() -> r.register(new NoopPacketHandler())).isInstanceOf(IllegalArgumentException.class);

        // Netty threads dispatch while a handler is added: counts stay exact.
        int threads = 4;
        int each = 20_000;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            for (int t = 0; t < threads; t++) {
                pool.submit(() -> {
                    for (int i = 0; i < each; i++) {
                        r.dispatch(send("KEEP_ALIVE"));
                    }
                });
            }
            r.register(new TestHandler("late", Set.of("CHUNK_DATA"), Set.of(), p -> PacketDecision.PASS));
        } finally {
            pool.shutdown();
            assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(r.metrics().getFirst().calls()).isEqualTo((long) threads * each);
        assertThat(r.handlers()).containsExactly(NoopPacketHandler.NAME, "late");
        assertThat(r.unregister("late")).isTrue();
        assertThat(r.wants(PacketDirection.SEND, "CHUNK_DATA")).isFalse();
    }

    @Test
    void layerFactoryDegradesWithoutPacketEvents() {
        PacketLayer none = PacketLayers.create(false);
        none.install(new PacketHandlerRegistry(LOG));
        assertThat(none.installed()).isFalse();
        none.uninstall();
        assertThat(none.describe()).startsWith("none");
    }
}
