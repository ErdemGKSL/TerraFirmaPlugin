/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.registry;

import java.util.Objects;

/**
 * Typed handle of one data registry in a {@link RegistrySnapshot}.
 *
 * @param name registry name, also the name used in {@link dev.tfp.core.json.Reference}s to its entries
 *             (e.g. {@code recipe}, {@code item_heat}, {@code worldgen/rock_settings})
 * @param type value type, checked on lookup
 */
public record RegistryKey<T>(String name, Class<T> type) {
    public RegistryKey {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
    }

    public static <T> RegistryKey<T> of(String name, Class<T> type) {
        return new RegistryKey<>(name, type);
    }
}
