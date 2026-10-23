/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.catchup;

import dev.tfp.core.catchup.CatchUpAdvance;
import dev.tfp.core.catchup.LazyBlockIndex;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The registered catch-up kinds (PAR-INFRA-11). Feature modules register theirs in {@code enable}, through
 * {@code services().require(CatchUpService.class).kinds()}. Thread-safe. An index entry whose kind is not registered
 * (its module is disabled) is kept and skipped.
 */
public final class CatchUpKinds {
    /** A registered kind: an async {@link CatchUpKind} or a {@link CatchUpKind.Direct}. */
    sealed interface Entry permits Async, Sync {
        String id();
    }

    record Async<S>(CatchUpKind<S> kind) implements Entry {
        @Override
        public String id() {
            return kind.id();
        }
    }

    record Sync(String id, CatchUpKind.Direct direct) implements Entry {
    }

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    /** Registers {@code kind}; a second kind with the same id is refused. */
    public <S> CatchUpKind<S> register(CatchUpKind<S> kind) {
        add(new Async<>(Objects.requireNonNull(kind, "kind")));
        return kind;
    }

    /** {@code CatchUpKinds.register(kind, pureAdvance)}: a kind from its region-thread read/write and pure advance. */
    public <S> CatchUpKind<S> register(String id, CatchUpKind.Reader<S> reader, CatchUpKind.Writer<S> writer,
            CatchUpAdvance<S> advance) {
        return register(CatchUpKind.of(id, reader, writer, advance));
    }

    /** Registers a synchronous kind (see {@link CatchUpKind.Direct}). */
    public void registerDirect(String id, CatchUpKind.Direct direct) {
        add(new Sync(LazyBlockIndex.checkKind(id), Objects.requireNonNull(direct, "direct")));
    }

    private void add(Entry entry) {
        LazyBlockIndex.checkKind(entry.id());
        Entry previous = entries.putIfAbsent(entry.id(), entry);
        if (previous != null) {
            throw new IllegalStateException("catch-up kind " + entry.id() + " is already registered");
        }
    }

    /** Removes the kind {@code id} (tests, module disable). */
    public void unregister(String id) {
        entries.remove(id);
    }

    Optional<Entry> find(String id) {
        return Optional.ofNullable(entries.get(id));
    }

    public boolean isRegistered(String id) {
        return entries.containsKey(id);
    }

    /** The registered ids, sorted. */
    public Set<String> ids() {
        return new TreeSet<>(entries.keySet());
    }
}
