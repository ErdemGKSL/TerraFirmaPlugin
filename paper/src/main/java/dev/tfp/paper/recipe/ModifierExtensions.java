/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.recipe;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.bukkit.inventory.ItemStack;

/**
 * Output modifiers whose item data belongs to another feature: the forging bonus (anvil), the bowl of a soup (pot),
 * the plant of a flower cutting, the bait on a rod, glass batches and powders, meals, crafting remainders of custom
 * items. The feature that owns the data registers its handler on {@link Recipes#extensions()}; until then the modifier
 * does what TFC does for a stack without that data (see each {@link Kind}). Immutable; {@link #with} returns a copy.
 */
public final class ModifierExtensions {
    /** The modifiers that can be extended. */
    public enum Kind {
        /** {@code tfc:copy_forging_bonus}: no bonus on the input copies nothing. */
        COPY_FORGING_BONUS,
        /** {@code tfc:empty_bowl}: the bowl stored on the input; without one TFC gives {@code Bowl.DISPLAY} (a bowl). */
        EMPTY_BOWL,
        /** {@code tfc:flower_cutting}: two of the plant stored on the input; none gives an empty stack. */
        FLOWER_CUTTING,
        /** {@code tfc:add_bait_to_rod}: put the first bait among the crafting inputs on the rod. */
        ADD_BAIT_TO_ROD,
        /** {@code tfc:add_glass}: start a glass batch from the input. */
        ADD_GLASS,
        /** {@code tfc:add_powder}: apply the first glass powder among the crafting inputs. */
        ADD_POWDER,
        /** {@code tfc:meal}: the meal's food data from the inputs and portions. */
        MEAL,
        /** {@code tfc:crafting_remainder} of a custom (CraftEngine) item; vanilla items use their material's. */
        CRAFTING_REMAINDER
    }

    /**
     * One handler.
     *
     * @param stack    the current output
     * @param input    the recipe input (do not modify it)
     * @param inputs   all crafting inputs ({@code RecipeHelpers.getCraftingInput}), empty outside crafting
     * @param modifier the modifier being applied (for {@code tfc:meal}: its food and portions)
     * @return the new output; empty for none
     */
    @FunctionalInterface
    public interface Handler {
        Optional<ItemStack> apply(ItemStack stack, ItemStack input, List<ItemStack> inputs, Object modifier);
    }

    public static final ModifierExtensions NONE = new ModifierExtensions(Map.of());

    private final Map<Kind, Handler> handlers;

    private ModifierExtensions(Map<Kind, Handler> handlers) {
        this.handlers = handlers.isEmpty() ? Map.of() : Map.copyOf(new EnumMap<>(handlers));
    }

    /** A copy with {@code handler} for {@code kind} (replacing any previous one). */
    public ModifierExtensions with(Kind kind, Handler handler) {
        Map<Kind, Handler> next = new EnumMap<>(Kind.class);
        next.putAll(handlers);
        next.put(Objects.requireNonNull(kind, "kind"), Objects.requireNonNull(handler, "handler"));
        return new ModifierExtensions(next);
    }

    public Optional<Handler> get(Kind kind) {
        return Optional.ofNullable(handlers.get(kind));
    }
}
