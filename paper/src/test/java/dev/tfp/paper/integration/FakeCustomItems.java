/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.integration;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntFunction;
import org.bukkit.inventory.ItemStack;

/**
 * In-memory {@link CustomItems}. Real {@code ItemStack}s need a server, so tests register a factory per
 * id; stacks created here are remembered by identity for {@link #idOf}.
 */
public final class FakeCustomItems implements CustomItems {
    private final Map<String, IntFunction<ItemStack>> factories = new ConcurrentHashMap<>();
    private final Map<ItemStack, String> created = Collections.synchronizedMap(new IdentityHashMap<>());

    /** {@code factory} gets the amount. */
    public FakeCustomItems define(String id, IntFunction<ItemStack> factory) {
        factories.put(id, factory);
        return this;
    }

    @Override
    public boolean exists(String id) {
        return factories.containsKey(id);
    }

    @Override
    public Optional<ItemStack> create(String id, int amount) {
        IntFunction<ItemStack> f = factories.get(id);
        if (f == null) {
            return Optional.empty();
        }
        ItemStack stack = f.apply(amount);
        created.put(stack, id);
        return Optional.of(stack);
    }

    @Override
    public Optional<String> idOf(ItemStack stack) {
        return Optional.ofNullable(stack == null ? null : created.get(stack));
    }
}
