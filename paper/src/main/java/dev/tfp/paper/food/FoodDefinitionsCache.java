/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.food;

import dev.tfp.core.food.FoodDefinitions;
import dev.tfp.core.registry.Registries;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * The food definitions of the current registry snapshot (M3-SURV-02), rebuilt once per published snapshot
 * (start, {@code /tfp reload data}). {@link FoodDefinitions#EMPTY} until the registries have loaded, so every
 * food falls back to the default decay modifier 1 until then.
 *
 * <p>Thread-safe: a volatile pair read per call; two threads that see a new snapshot at once may both build
 * the table, and either result is the same.
 */
public final class FoodDefinitionsCache implements Supplier<FoodDefinitions> {
    private record Entry(long version, FoodDefinitions definitions) {
    }

    private final Supplier<Registries> registries;
    private volatile Entry entry = new Entry(0, FoodDefinitions.EMPTY);

    /** @param registries the plugin's registries, or a supplier that returns null before they exist */
    public FoodDefinitionsCache(Supplier<Registries> registries) {
        this.registries = Objects.requireNonNull(registries, "registries");
    }

    @Override
    public FoodDefinitions get() {
        Registries r = registries.get();
        if (r == null) {
            return FoodDefinitions.EMPTY;
        }
        long version = r.version();
        Entry e = entry;
        if (e.version() == version) {
            return e.definitions();
        }
        FoodDefinitions defs = version == 0 ? FoodDefinitions.EMPTY : FoodDefinitions.fromRegistry(r.get());
        entry = new Entry(version, defs);
        return defs;
    }
}
