/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/recipes/ingredients @ c3b4d9536 (what NotRotten/HasTrait/Heat/
 * FluidContent ingredients read from a stack)
 */
package dev.tfp.paper.recipe;

import dev.tfp.core.recipe.fluid.FluidAmount;
import dev.tfp.core.recipe.hooks.FluidHooks;
import dev.tfp.core.recipe.hooks.FoodHooks;
import dev.tfp.core.recipe.hooks.HeatHooks;
import dev.tfp.core.recipe.ingredient.IngredientContext;
import dev.tfp.core.tag.TagLookup;
import dev.tfp.core.tag.TagType;
import dev.tfp.paper.itemdata.ItemFluids;
import dev.tfp.paper.tags.TagSnapshot;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import org.bukkit.inventory.ItemStack;

/**
 * TFC's ingredients evaluated against real {@link ItemStack}s: the item id (CraftEngine id, else the vanilla key),
 * tags from one {@link TagSnapshot} (PAR-INFRA-07), food state ({@code tfc:not_rotten}, {@code tfc:has_trait}), item
 * temperature ({@code tfc:heat}) and item fluid contents ({@code tfc:fluid_content}, PAR-INFRA-04). One instance per
 * operation (it pins the tag snapshot); safe on any thread that may read the stacks.
 */
public final class ItemIngredientContext implements IngredientContext<ItemStack> {
    /** The id of an empty stack: no item ingredient accepts it. */
    public static final String AIR = "minecraft:air";

    private final Function<ItemStack, Optional<String>> itemIds;
    private final TagLookup tags;
    private final FoodHooks<ItemStack> food;
    private final HeatHooks<ItemStack> heat;
    private final FluidHooks<ItemStack> fluids;

    public ItemIngredientContext(Function<ItemStack, Optional<String>> itemIds, TagSnapshot tags,
            FoodHooks<ItemStack> food, HeatHooks<ItemStack> heat) {
        this(itemIds, lookup(tags), food, heat, ItemIngredientContext::containedFluid);
    }

    public ItemIngredientContext(Function<ItemStack, Optional<String>> itemIds, TagLookup tags,
            FoodHooks<ItemStack> food, HeatHooks<ItemStack> heat, FluidHooks<ItemStack> fluids) {
        this.itemIds = Objects.requireNonNull(itemIds, "itemIds");
        this.tags = Objects.requireNonNull(tags, "tags");
        this.food = Objects.requireNonNull(food, "food");
        this.heat = Objects.requireNonNull(heat, "heat");
        this.fluids = Objects.requireNonNull(fluids, "fluids");
    }

    /** A {@link TagLookup} over a tag snapshot: our tags, server tags and common tags, {@code tfc:} names accepted. */
    public static TagLookup lookup(TagSnapshot tags) {
        return new TagLookup() {
            @Override
            public boolean contains(TagType type, String tag, String id) {
                return tags.is(type, id, tag);
            }

            @Override
            public boolean exists(TagType type, String tag) {
                return tags.exists(type, tag);
            }
        };
    }

    /** TFC {@code FluidHelpers.getContainedFluid}: the fluid stored on the item (PAR-INFRA-04), empty if none. */
    public static Optional<FluidAmount> containedFluid(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return Optional.empty();
        }
        dev.tfp.core.vessel.FluidAmount content = ItemFluids.content(stack);
        return content.isEmpty() ? Optional.empty() : Optional.of(new FluidAmount(content.fluid(), content.amount()));
    }

    @Override
    public String itemId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return AIR;
        }
        return itemIds.apply(stack).orElse(AIR);
    }

    @Override
    public TagLookup tags() {
        return tags;
    }

    @Override
    public FoodHooks<ItemStack> food() {
        return food;
    }

    @Override
    public HeatHooks<ItemStack> heat() {
        return heat;
    }

    @Override
    public FluidHooks<ItemStack> fluids() {
        return fluids;
    }
}
