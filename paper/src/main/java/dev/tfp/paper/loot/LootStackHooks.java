/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.loot;

import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;

/**
 * The item-data side of loot ({@link LootFunction.StackOp}): {@code tfc:rotten}, {@code tfc:apply_stack_size},
 * {@code tfc:copy_fluid} and {@code minecraft:copy_components}. The feature that owns the data (food, item size, device
 * fluids and contents) provides an implementation through {@code ModuleServices.provide(LootStackHooks.class, ...)};
 * until one is provided, {@link #NONE} leaves stacks unchanged (a recorded divergence).
 */
public interface LootStackHooks {
    LootStackHooks NONE = (stack, op, captured) -> stack;

    /**
     * Captures what the ops may need from the block before it is gone (block entity contents, fluid). Region thread of
     * {@code block}, at break time. The result is handed back to {@link #apply}; null when nothing is needed.
     */
    default Object capture(Block block) {
        return null;
    }

    /** Applies one op to {@code stack} and returns the stack to drop (may be another instance). Region thread. */
    ItemStack apply(ItemStack stack, LootFunction.StackOp op, Object captured);
}
