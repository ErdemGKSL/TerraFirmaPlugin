/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.integration;

import java.util.Optional;
import org.bukkit.inventory.ItemStack;

/**
 * Custom item lookups. Implemented by {@code integration/craftengine}. Item ids are namespaced
 * ({@code tfp:ingot/bronze}). Safe on any thread once CraftEngine has loaded; empty before that.
 */
public interface CustomItems {
    boolean exists(String id);

    /** A new stack of {@code amount} of the custom item, or empty if {@code id} is unknown. */
    Optional<ItemStack> create(String id, int amount);

    /** The custom item id of {@code stack}, or empty for a vanilla (or empty) stack. */
    Optional<String> idOf(ItemStack stack);
}
