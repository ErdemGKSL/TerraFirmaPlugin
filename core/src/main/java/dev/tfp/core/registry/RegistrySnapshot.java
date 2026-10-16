/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.registry;

import dev.tfp.core.tag.TagRegistry;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * One complete, immutable set of loaded data: every registry, the resolved tags and the ids of
 * recipes disabled by data (NeoForge {@code neoforge:conditions} that are false, which is how TFC
 * removes vanilla recipes). Gameplay code takes one snapshot per operation from {@link Registries}
 * and never mixes values from two snapshots.
 */
public final class RegistrySnapshot {
    public static final RegistrySnapshot EMPTY = new RegistrySnapshot(Map.of(), TagRegistry.EMPTY, Set.of());

    private final Map<String, Registry<?>> registries;
    private final TagRegistry tags;
    private final Set<String> disabledRecipes;

    public RegistrySnapshot(Map<String, Registry<?>> registries, TagRegistry tags, Set<String> disabledRecipes) {
        this.registries = Collections.unmodifiableMap(new LinkedHashMap<>(registries));
        this.tags = tags;
        this.disabledRecipes = Set.copyOf(disabledRecipes);
    }

    /** The registry for {@code key}; empty if this snapshot has none of that name. */
    @SuppressWarnings("unchecked")
    public <T> Registry<T> get(RegistryKey<T> key) {
        Registry<?> r = registries.get(key.name());
        if (r == null) {
            return Registry.empty(key);
        }
        if (!r.key().type().equals(key.type())) {
            throw new IllegalArgumentException("registry " + key.name() + " holds " + r.key().type().getSimpleName() + ", not " + key.type().getSimpleName());
        }
        return (Registry<T>) r;
    }

    public Collection<Registry<?>> registries() {
        return registries.values();
    }

    public TagRegistry tags() {
        return tags;
    }

    public Set<String> disabledRecipes() {
        return disabledRecipes;
    }
}
