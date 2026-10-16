/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.concurrent;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Lock-free holder of an immutable value that is replaced as a whole (registries, calendar state).
 * Readers call {@link #get()} once per operation and work on that value; writers build a complete
 * new value off-thread and publish it. Publication goes through an {@link AtomicReference}, so a
 * reader that sees the new value also sees every write made before {@link #publish} (JMM
 * happens-before via the volatile write/read).
 *
 * <p>Every value carries a version. {@link #publishIfNewer} lets concurrent producers (two
 * overlapping reloads) race safely: an older result never replaces a newer one.
 *
 * @param <T> the value type; must be immutable or effectively immutable after publication
 */
public final class Snapshot<T> {
    /** A published value with its version. */
    public record Versioned<T>(long version, T value) {
        public Versioned {
            Objects.requireNonNull(value, "value");
        }
    }

    private final AtomicReference<Versioned<T>> ref;

    public Snapshot(T initial) {
        this.ref = new AtomicReference<>(new Versioned<>(0L, initial));
    }

    /** The current value. Take it once per operation. */
    public T get() {
        return ref.get().value();
    }

    /** The current value together with its version (both from the same publication). */
    public Versioned<T> current() {
        return ref.get();
    }

    public long version() {
        return ref.get().version();
    }

    /** Publishes {@code value} unconditionally with version = current + 1; returns that version. */
    public long publish(T value) {
        Objects.requireNonNull(value, "value");
        while (true) {
            Versioned<T> old = ref.get();
            Versioned<T> next = new Versioned<>(old.version() + 1, value);
            if (ref.compareAndSet(old, next)) {
                return next.version();
            }
        }
    }

    /**
     * Publishes {@code value} as {@code version} only if that is greater than the current version.
     *
     * @return {@code true} if published, {@code false} if a newer (or equal) version is already there
     */
    public boolean publishIfNewer(long version, T value) {
        Objects.requireNonNull(value, "value");
        Versioned<T> next = new Versioned<>(version, value);
        while (true) {
            Versioned<T> old = ref.get();
            if (old.version() >= version) {
                return false;
            }
            if (ref.compareAndSet(old, next)) {
                return true;
            }
        }
    }
}
