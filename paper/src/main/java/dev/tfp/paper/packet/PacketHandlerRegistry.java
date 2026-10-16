/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.packet;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.LongSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * All our {@link PacketHandler}s behind the one library listener ({@link PacketLayer}). Dispatch is
 * lock-free: handlers are indexed by direction and packet type in an immutable table behind a volatile
 * field, rebuilt on (rare) registration. For each packet the typed handlers run in registration order, then
 * the {@link PacketHandler#ALL wildcard} ones; {@link PacketDecision#CANCEL} stops the chain.
 *
 * <p>Metrics per handler (calls, total and max nanoseconds, modified, cancelled, errors) use
 * {@link LongAdder}s, so concurrent Netty threads do not contend.
 */
public final class PacketHandlerRegistry {
    /** Counters of one handler, read as a {@link HandlerMetrics} snapshot. */
    static final class Entry {
        final PacketHandler handler;
        final LongAdder calls = new LongAdder();
        final LongAdder nanos = new LongAdder();
        final AtomicLong maxNanos = new AtomicLong();
        final LongAdder modified = new LongAdder();
        final LongAdder cancelled = new LongAdder();
        final LongAdder errors = new LongAdder();

        Entry(PacketHandler handler) {
            this.handler = handler;
        }
    }

    /** A point-in-time copy of one handler's counters. */
    public record HandlerMetrics(String name, long calls, long totalNanos, long maxNanos, long modified,
            long cancelled, long errors) {
        public long averageNanos() {
            return calls == 0 ? 0 : totalNanos / calls;
        }
    }

    private record Table(List<Entry> all, Map<String, Entry[]> send, Map<String, Entry[]> receive,
            Entry[] sendAll, Entry[] receiveAll) {
        static final Table EMPTY = new Table(List.of(), Map.of(), Map.of(), new Entry[0], new Entry[0]);
    }

    private static final Entry[] NONE = new Entry[0];
    private static final long ERROR_LOG_EVERY = 1000;

    private final Logger log;
    private final LongSupplier clock;
    private volatile Table table = Table.EMPTY;

    public PacketHandlerRegistry(Logger log) {
        this(log, System::nanoTime);
    }

    PacketHandlerRegistry(Logger log, LongSupplier clock) {
        this.log = log;
        this.clock = clock;
    }

    /** Adds a handler. Any thread; takes effect for the next packet. */
    public synchronized void register(PacketHandler handler) {
        Objects.requireNonNull(handler, "handler");
        for (Entry e : table.all()) {
            if (e.handler.name().equals(handler.name())) {
                throw new IllegalArgumentException("duplicate packet handler " + handler.name());
            }
        }
        List<Entry> all = new ArrayList<>(table.all());
        all.add(new Entry(handler));
        table = build(all);
    }

    /** Removes a handler by name; its metrics are dropped. @return whether it was registered */
    public synchronized boolean unregister(String name) {
        List<Entry> all = new ArrayList<>(table.all());
        boolean removed = all.removeIf(e -> e.handler.name().equals(name));
        if (removed) {
            table = build(all);
        }
        return removed;
    }

    private static Table build(List<Entry> all) {
        Map<String, List<Entry>> send = new HashMap<>();
        Map<String, List<Entry>> receive = new HashMap<>();
        List<Entry> sendAll = new ArrayList<>();
        List<Entry> receiveAll = new ArrayList<>();
        for (Entry e : all) {
            index(e, e.handler.sendTypes(), send, sendAll);
            index(e, e.handler.receiveTypes(), receive, receiveAll);
        }
        return new Table(List.copyOf(all), freeze(send), freeze(receive), sendAll.toArray(NONE), receiveAll.toArray(NONE));
    }

    private static void index(Entry e, java.util.Set<String> types, Map<String, List<Entry>> byType, List<Entry> wildcard) {
        for (String t : types) {
            if (t.equals(PacketHandler.ALL)) {
                wildcard.add(e);
            } else {
                byType.computeIfAbsent(t, k -> new ArrayList<>()).add(e);
            }
        }
    }

    private static Map<String, Entry[]> freeze(Map<String, List<Entry>> m) {
        Map<String, Entry[]> out = new HashMap<>();
        m.forEach((k, v) -> out.put(k, v.toArray(NONE)));
        return Map.copyOf(out);
    }

    /** Cheap pre-check for the library bridge: false means no handler wants the packet (skip all work). */
    public boolean wants(PacketDirection direction, String packetType) {
        Table t = table;
        return direction == PacketDirection.SEND
                ? t.sendAll().length > 0 || t.send().containsKey(packetType)
                : t.receiveAll().length > 0 || t.receive().containsKey(packetType);
    }

    /** Runs the handlers for one packet. Netty thread. */
    public PacketDecision dispatch(PacketContext packet) {
        Table t = table;
        boolean send = packet.direction() == PacketDirection.SEND;
        Entry[] typed = (send ? t.send() : t.receive()).getOrDefault(packet.packetType(), NONE);
        Entry[] wildcard = send ? t.sendAll() : t.receiveAll();
        PacketDecision typedResult = run(typed, packet, send);
        if (typedResult == PacketDecision.CANCEL || wildcard.length == 0) {
            return typedResult;
        }
        PacketDecision wildResult = run(wildcard, packet, send);
        return wildResult == PacketDecision.PASS ? typedResult : wildResult;
    }

    private PacketDecision run(Entry[] entries, PacketContext packet, boolean send) {
        PacketDecision result = PacketDecision.PASS;
        for (Entry e : entries) {
            PacketDecision d = call(e, packet, send);
            if (d == PacketDecision.CANCEL) {
                return PacketDecision.CANCEL;
            }
            if (d == PacketDecision.MODIFIED) {
                result = PacketDecision.MODIFIED;
            }
        }
        return result;
    }

    private PacketDecision call(Entry e, PacketContext packet, boolean send) {
        long start = clock.getAsLong();
        PacketDecision d;
        try {
            d = send ? e.handler.onSend(packet) : e.handler.onReceive(packet);
            if (d == null) {
                d = PacketDecision.PASS;
            }
        } catch (RuntimeException | LinkageError ex) {
            e.errors.increment();
            long n = e.errors.sum();
            if (n == 1 || n % ERROR_LOG_EVERY == 0) {
                log.log(Level.SEVERE, "Packet handler " + e.handler.name() + " failed on " + packet.direction() + " "
                        + packet.packetType() + " (" + n + " error(s) so far); the packet passes unchanged", ex);
            }
            d = PacketDecision.PASS;
        }
        long took = clock.getAsLong() - start;
        e.calls.increment();
        e.nanos.add(took);
        e.maxNanos.accumulateAndGet(took, Math::max);
        if (d == PacketDecision.MODIFIED) {
            e.modified.increment();
        } else if (d == PacketDecision.CANCEL) {
            e.cancelled.increment();
        }
        return d;
    }

    /** Snapshot of every handler's counters, in registration order. */
    public List<HandlerMetrics> metrics() {
        List<HandlerMetrics> out = new ArrayList<>();
        for (Entry e : table.all()) {
            out.add(new HandlerMetrics(e.handler.name(), e.calls.sum(), e.nanos.sum(), e.maxNanos.get(),
                    e.modified.sum(), e.cancelled.sum(), e.errors.sum()));
        }
        return out;
    }

    /** Names of the registered handlers, in order. */
    public List<String> handlers() {
        return table.all().stream().map(e -> e.handler.name()).toList();
    }
}
