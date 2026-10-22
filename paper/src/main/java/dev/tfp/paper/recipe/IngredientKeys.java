/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/recipes/RecipeHelpers.java @ c3b4d9536 (itemKeys, fluidKeys: the keys
 * of the IndirectHashCollection caches; AndIngredient/PreciseIngredient.getItems)
 */
package dev.tfp.paper.recipe;

import dev.tfp.core.json.SchemaRecord;
import dev.tfp.core.recipe.block.BlockIngredient;
import dev.tfp.core.recipe.fluid.FluidIngredient;
import dev.tfp.core.recipe.fluid.SizedFluidIngredient;
import dev.tfp.core.recipe.ingredient.Ingredient;
import dev.tfp.core.recipe.ingredient.SizedIngredient;
import dev.tfp.core.tag.TagType;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The index keys of an ingredient: a superset of the ids it can match, or {@link Keys#ANY} when it may match any id.
 * The invariant the index relies on: whenever an ingredient accepts a stack, the stack's id is in its keys (or the keys
 * are {@code ANY}). TFC's keys are {@code Ingredient.getItems()}; a top-level precise ingredient
 * ({@code tfc:not_rotten}, {@code tfc:heat}, ...) lists every item there, which is our {@code ANY}.
 */
public final class IngredientKeys {
    private IngredientKeys() {
    }

    /** A key set: {@code any} or the listed ids. */
    public record Keys(boolean any, Set<String> ids) {
        public static final Keys ANY = new Keys(true, Set.of());
        public static final Keys NONE = new Keys(false, Set.of());

        public Keys {
            ids = any ? Set.of() : Set.copyOf(ids);
        }

        static Keys of(Set<String> ids) {
            return new Keys(false, ids);
        }

        Keys union(Keys other) {
            if (any || other.any) {
                return ANY;
            }
            Set<String> s = new HashSet<>(ids);
            s.addAll(other.ids);
            return of(s);
        }

        Keys intersect(Keys other) {
            if (any) {
                return other;
            }
            if (other.any) {
                return this;
            }
            Set<String> s = new HashSet<>(ids);
            s.retainAll(other.ids);
            return of(s);
        }
    }

    /** Item keys of an ingredient. */
    public static Keys item(Ingredient ingredient, TagMembers tags) {
        return switch (ingredient) {
            case Ingredient.Item i -> Keys.of(Set.of(i.item()));
            case Ingredient.Tag t -> Keys.of(tags.members(TagType.ITEM, t.tag()));
            case Ingredient.AnyOf a -> union(a.children(), tags);
            case Ingredient.Compound c -> union(c.children(), tags);
            // TFC AndIngredient.getItems: the first child's items (precise children only modify them); intersecting
            // every child is tighter and still holds every match.
            case Ingredient.And a -> intersection(a.children(), tags);
            case Ingredient.Intersection i -> intersection(i.children(), tags);
            case Ingredient.Difference d -> item(d.base(), tags);
            // Precise ingredients and fluid contents may hold for any item.
            case Ingredient.NotRotten n -> Keys.ANY;
            case Ingredient.Rotten r -> Keys.ANY;
            case Ingredient.HasTrait h -> Keys.ANY;
            case Ingredient.LacksTrait l -> Keys.ANY;
            case Ingredient.Heat h -> Keys.ANY;
            case Ingredient.FluidContent f -> Keys.ANY;
        };
    }

    private static Keys union(Collection<Ingredient> children, TagMembers tags) {
        Keys k = Keys.NONE;
        for (Ingredient c : children) {
            k = k.union(item(c, tags));
            if (k.any()) {
                return k;
            }
        }
        return k;
    }

    private static Keys intersection(Collection<Ingredient> children, TagMembers tags) {
        Keys k = Keys.ANY;
        for (Ingredient c : children) {
            k = k.intersect(item(c, tags));
        }
        return k;
    }

    /**
     * Item keys of a recipe field value: an ingredient, a sized ingredient, an optional, a list of them or a shaped
     * key map. An empty list (a pot with no item ingredients) is {@code ANY}.
     */
    public static Keys itemValue(Object value, TagMembers tags) {
        return switch (value) {
            case null -> Keys.ANY;
            case Ingredient i -> item(i, tags);
            case SizedIngredient s -> item(s.ingredient(), tags);
            case Optional<?> o -> o.isEmpty() ? Keys.ANY : itemValue(o.get(), tags);
            case Map<?, ?> m -> values(m.values(), tags, IngredientKeys::itemValue);
            case Collection<?> c -> values(c, tags, IngredientKeys::itemValue);
            default -> throw new IllegalArgumentException("not an item ingredient: " + value.getClass().getName());
        };
    }

    /** Block keys of a {@code BlockIngredient} field value. */
    public static Keys blockValue(Object value, TagMembers tags) {
        return switch (value) {
            case null -> Keys.ANY;
            case BlockIngredient b -> b.tag().map(t -> Keys.of(tags.members(TagType.BLOCK, t)))
                    .orElseGet(() -> Keys.of(Set.copyOf(b.blocks())));
            case Optional<?> o -> o.isEmpty() ? Keys.ANY : blockValue(o.get(), tags);
            case Collection<?> c -> values(c, tags, IngredientKeys::blockValue);
            default -> throw new IllegalArgumentException("not a block ingredient: " + value.getClass().getName());
        };
    }

    /** Fluid keys of a fluid field value: a (sized) fluid ingredient, a fluid id, an alloy range, or a list of them. */
    public static Keys fluidValue(Object value, TagMembers tags) {
        return switch (value) {
            case null -> Keys.ANY;
            case SizedFluidIngredient s -> fluid(s.ingredient(), tags);
            case FluidIngredient f -> fluid(f, tags);
            case String id -> Keys.of(Set.of(id));
            case SchemaRecord r -> fluidValue(r.values().get("fluid"), tags);
            case Optional<?> o -> o.isEmpty() ? Keys.ANY : fluidValue(o.get(), tags);
            case Collection<?> c -> values(c, tags, IngredientKeys::fluidValue);
            default -> throw new IllegalArgumentException("not a fluid ingredient: " + value.getClass().getName());
        };
    }

    private static Keys fluid(FluidIngredient ingredient, TagMembers tags) {
        return switch (ingredient) {
            case FluidIngredient.Single s -> Keys.of(Set.of(s.fluid()));
            case FluidIngredient.Tag t -> Keys.of(tags.members(TagType.FLUID, t.tag()));
            case FluidIngredient.AnyOf a -> {
                Keys k = Keys.NONE;
                for (FluidIngredient c : a.children()) {
                    k = k.union(fluid(c, tags));
                }
                yield k;
            }
        };
    }

    private interface Walker {
        Keys keys(Object value, TagMembers tags);
    }

    private static Keys values(Collection<?> values, TagMembers tags, Walker walker) {
        if (values.isEmpty()) {
            return Keys.ANY;
        }
        Keys k = Keys.NONE;
        for (Object v : values) {
            k = k.union(walker.keys(v, tags));
            if (k.any()) {
                return k;
            }
        }
        return k;
    }
}
