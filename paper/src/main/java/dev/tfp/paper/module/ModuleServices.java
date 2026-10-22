/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.module;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Typed service lookup shared by the plugin and the feature modules (PAR-INFRA-01). The plugin provides its own
 * services (food and heat hooks, definition caches) before the modules are enabled; a module may provide services of
 * its own for the modules after it in the service file. Thread-safe; one instance per value type.
 */
public final class ModuleServices {
    private final Map<Class<?>, Object> services = new ConcurrentHashMap<>();

    /** Registers {@code service} as the {@code type}. A second service of the same type is refused. */
    public <T> void provide(Class<T> type, T service) {
        if (!type.isInstance(service)) {
            throw new IllegalArgumentException(service + " is not a " + type.getName());
        }
        Object previous = services.putIfAbsent(type, service);
        if (previous != null) {
            throw new IllegalStateException("a " + type.getName() + " is already provided: " + previous);
        }
    }

    public <T> Optional<T> get(Class<T> type) {
        return Optional.ofNullable(type.cast(services.get(type)));
    }

    /** The service of {@code type}; fails when nobody provided it (a missing dependency between modules). */
    public <T> T require(Class<T> type) {
        return get(type).orElseThrow(() -> new IllegalStateException("no " + type.getName()
                + " is provided; is the module that provides it listed earlier in the service file?"));
    }
}
