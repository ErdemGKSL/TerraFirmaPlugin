/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.registry;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;

/**
 * Immutable id → value map of one data kind, in load order. Safe to share between threads.
 * Built with {@link Builder}, which rejects duplicate ids.
 */
public final class Registry<T> {
    private final RegistryKey<T> key;
    private final Map<String, T> entries;

    private Registry(RegistryKey<T> key, Map<String, T> entries) {
        this.key = key;
        this.entries = Collections.unmodifiableMap(new LinkedHashMap<>(entries));
    }

    public static <T> Registry<T> empty(RegistryKey<T> key) {
        return new Registry<>(key, Map.of());
    }

    public static <T> Builder<T> builder(RegistryKey<T> key) {
        return new Builder<>(key);
    }

    public RegistryKey<T> key() {
        return key;
    }

    public Optional<T> get(String id) {
        return Optional.ofNullable(entries.get(id));
    }

    public T getOrThrow(String id) {
        T value = entries.get(id);
        if (value == null) {
            throw new NoSuchElementException("no " + key.name() + " '" + id + "'");
        }
        return value;
    }

    public boolean contains(String id) {
        return entries.containsKey(id);
    }

    public Set<String> ids() {
        return entries.keySet();
    }

    public Collection<T> values() {
        return entries.values();
    }

    public Map<String, T> asMap() {
        return entries;
    }

    public int size() {
        return entries.size();
    }

    public static final class Builder<T> {
        private final RegistryKey<T> key;
        private final Map<String, T> entries = new LinkedHashMap<>();

        private Builder(RegistryKey<T> key) {
            this.key = key;
        }

        /** @throws IllegalArgumentException on a duplicate id or a value of the wrong type */
        public Builder<T> register(String id, T value) {
            if (!key.type().isInstance(value)) {
                throw new IllegalArgumentException(key.name() + " '" + id + "' is not a " + key.type().getSimpleName());
            }
            if (entries.putIfAbsent(id, value) != null) {
                throw new IllegalArgumentException("duplicate " + key.name() + " '" + id + "'");
            }
            return this;
        }

        public boolean contains(String id) {
            return entries.containsKey(id);
        }

        public Registry<T> build() {
            return new Registry<>(key, entries);
        }
    }
}
