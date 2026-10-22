/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.device;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The registered {@link DeviceKind}s by block id (PAR-INFRA-02). A lookup tries the block id itself, then each parent
 * path ({@code tfp:a/b/c} → {@code tfp:a/b} → {@code tfp:a}), so the longest registered id wins. Thread-safe; lookups
 * do not lock.
 */
public final class DeviceKinds {
    private final Map<String, DeviceKind<?>> byId = new ConcurrentHashMap<>();

    /** Adds {@code kind}; a second kind with the same id is refused. */
    public <S> DeviceKind<S> register(DeviceKind<S> kind) {
        DeviceKind<?> previous = byId.putIfAbsent(kind.id(), kind);
        if (previous != null) {
            throw new IllegalArgumentException("device kind " + kind.id() + " is already registered: " + previous);
        }
        return kind;
    }

    /** The kind covering {@code blockId}, if any. Any thread. */
    public Optional<DeviceKind<?>> find(String blockId) {
        String id = blockId;
        while (true) {
            DeviceKind<?> kind = byId.get(id);
            if (kind != null) {
                return Optional.of(kind);
            }
            int slash = id.lastIndexOf('/');
            if (slash <= id.indexOf(':')) {
                return Optional.empty();
            }
            id = id.substring(0, slash);
        }
    }

    public List<DeviceKind<?>> all() {
        return List.copyOf(byId.values());
    }

    public int size() {
        return byId.size();
    }
}
