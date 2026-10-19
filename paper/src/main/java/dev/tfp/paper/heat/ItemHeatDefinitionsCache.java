/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.heat;

import dev.tfp.core.heat.ItemHeatDefinitions;
import dev.tfp.core.registry.Registries;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * The item heat definitions of the plugin's registries, rebuilt only when the registries change (their version).
 * Thread-safe: the entry is replaced as a whole, and a rebuild of the same version by two threads gives equal results.
 */
public final class ItemHeatDefinitionsCache implements Supplier<ItemHeatDefinitions> {
    private record Entry(long version, ItemHeatDefinitions definitions) {
    }

    private final Supplier<Registries> registries;
    private volatile Entry entry = new Entry(0, ItemHeatDefinitions.EMPTY);

    /** @param registries the plugin's registries, or a supplier that returns null before they exist */
    public ItemHeatDefinitionsCache(Supplier<Registries> registries) {
        this.registries = Objects.requireNonNull(registries, "registries");
    }

    @Override
    public ItemHeatDefinitions get() {
        Registries r = registries.get();
        if (r == null) {
            return ItemHeatDefinitions.EMPTY;
        }
        long version = r.version();
        Entry e = entry;
        if (e.version() == version) {
            return e.definitions();
        }
        ItemHeatDefinitions defs = version == 0 ? ItemHeatDefinitions.EMPTY : ItemHeatDefinitions.fromRegistry(r.get());
        entry = new Entry(version, defs);
        return defs;
    }
}
