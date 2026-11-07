/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.itemsize;

import dev.tfp.core.item.ItemSizeDefinitions;
import dev.tfp.core.registry.Registries;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * The item size definitions of the plugin's registries, rebuilt only when the registries change (their version). The
 * same object is returned while the version stays. Thread-safe: the entry is replaced as a whole.
 */
public final class ItemSizeDefinitionsCache implements Supplier<ItemSizeDefinitions> {
    private record Entry(long version, ItemSizeDefinitions definitions) {
    }

    private final Supplier<Registries> registries;
    private volatile Entry entry = new Entry(0, ItemSizeDefinitions.EMPTY);

    /** @param registries the plugin's registries, or a supplier that returns null before they exist */
    public ItemSizeDefinitionsCache(Supplier<Registries> registries) {
        this.registries = Objects.requireNonNull(registries, "registries");
    }

    @Override
    public ItemSizeDefinitions get() {
        Registries r = registries.get();
        if (r == null) {
            return ItemSizeDefinitions.EMPTY;
        }
        long version = r.version();
        Entry e = entry;
        if (e.version() == version) {
            return e.definitions();
        }
        ItemSizeDefinitions defs = version == 0 ? ItemSizeDefinitions.EMPTY : ItemSizeDefinitions.fromRegistry(r.get());
        entry = new Entry(version, defs);
        return defs;
    }
}
