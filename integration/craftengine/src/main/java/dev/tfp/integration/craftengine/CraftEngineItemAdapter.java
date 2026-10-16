/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.integration.craftengine;

import dev.tfp.paper.integration.CustomItems;
import java.util.Optional;
import net.momirealms.craftengine.bukkit.api.CraftEngineItems;
import net.momirealms.craftengine.bukkit.item.BukkitItemDefinition;
import net.momirealms.craftengine.core.util.Key;
import org.bukkit.inventory.ItemStack;

/** {@link CustomItems} over {@link CraftEngineItems}. Stateless; empty results until CraftEngine has loaded. */
final class CraftEngineItemAdapter implements CustomItems {
    @Override
    public boolean exists(String id) {
        return definition(id) != null;
    }

    @Override
    public Optional<ItemStack> create(String id, int amount) {
        BukkitItemDefinition def = definition(id);
        if (def == null) {
            return Optional.empty();
        }
        ItemStack stack = def.buildBukkitItem();
        stack.setAmount(amount);
        return Optional.of(stack);
    }

    @Override
    public Optional<String> idOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return Optional.empty();
        }
        Key key = CraftEngineItems.getCustomItemId(stack);
        return key == null ? Optional.empty() : Optional.of(key.toString());
    }

    private static BukkitItemDefinition definition(String id) {
        int colon = id.indexOf(':');
        return colon > 0 ? CraftEngineItems.byId(Key.of(id.substring(0, colon), id.substring(colon + 1))) : null;
    }
}
