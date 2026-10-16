/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/recipes/ingredients/TFCIngredients.java @ c3b4d9536
 */
package dev.tfp.core.recipe.ingredient;

import com.google.gson.JsonElement;
import dev.tfp.core.json.Codec;
import dev.tfp.core.json.Codecs;
import dev.tfp.core.json.DecodeContext;
import dev.tfp.core.json.MapCodec;
import dev.tfp.core.json.ObjectReader;
import dev.tfp.core.json.TypeDispatch;
import dev.tfp.core.recipe.fluid.FluidAmount;
import dev.tfp.core.recipe.fluid.SizedFluidIngredient;
import dev.tfp.core.tag.TagType;
import java.util.List;
import java.util.Optional;

/**
 * Item ingredient: vanilla 1.21.1 syntax ({@code {"item": id}}, {@code {"tag": tag}}, an array =
 * any of), NeoForge's {@code neoforge:compound}/{@code neoforge:intersection}/{@code neoforge:difference},
 * and every TFC custom type from {@code common/recipes/ingredients/}:
 * {@code tfc:and}, {@code tfc:not_rotten}, {@code tfc:rotten}, {@code tfc:has_trait},
 * {@code tfc:lacks_trait}, {@code tfc:heat}, {@code tfc:fluid_content}.
 *
 * <p>Matching is pure; food/heat/fluid state comes from the {@link IngredientContext} hooks, which
 * later systems implement. TFC's {@code PreciseIngredient}s (conditions on item data, not on the
 * item) report {@link #precise()}; their display-stack modification is a GUI concern and lives
 * with the recipe viewer, not here.
 */
public sealed interface Ingredient {
    <S> boolean test(S stack, IngredientContext<S> ctx);

    /** TFC {@code PreciseIngredient}: a condition on item data that matches any item by itself. */
    default boolean precise() {
        return false;
    }

    /** {@code {"item": "ns:id"}}. */
    record Item(String item) implements Ingredient {
        @Override
        public <S> boolean test(S stack, IngredientContext<S> ctx) {
            return item.equals(ctx.itemId(stack));
        }
    }

    /** {@code {"tag": "ns:tag"}} (item tag, without {@code #}). */
    record Tag(String tag) implements Ingredient {
        @Override
        public <S> boolean test(S stack, IngredientContext<S> ctx) {
            return ctx.tags().contains(TagType.ITEM, tag, ctx.itemId(stack));
        }
    }

    /** A JSON array: matches if any child matches. */
    record AnyOf(List<Ingredient> children) implements Ingredient {
        public AnyOf {
            children = List.copyOf(children);
        }

        @Override
        public <S> boolean test(S stack, IngredientContext<S> ctx) {
            return any(children, stack, ctx);
        }
    }

    /** {@code neoforge:compound}: matches if any child matches. */
    record Compound(List<Ingredient> children) implements Ingredient {
        public Compound {
            children = List.copyOf(children);
        }

        @Override
        public <S> boolean test(S stack, IngredientContext<S> ctx) {
            return any(children, stack, ctx);
        }
    }

    /** {@code tfc:and} (TFC {@code AndIngredient}): every child matches. */
    record And(List<Ingredient> children) implements Ingredient {
        public And {
            children = List.copyOf(children);
        }

        @Override
        public <S> boolean test(S stack, IngredientContext<S> ctx) {
            return all(children, stack, ctx);
        }
    }

    /** {@code neoforge:intersection}: every child matches. */
    record Intersection(List<Ingredient> children) implements Ingredient {
        public Intersection {
            children = List.copyOf(children);
        }

        @Override
        public <S> boolean test(S stack, IngredientContext<S> ctx) {
            return all(children, stack, ctx);
        }
    }

    /** {@code neoforge:difference}: {@code base} matches and {@code subtracted} doesn't. */
    record Difference(Ingredient base, Ingredient subtracted) implements Ingredient {
        @Override
        public <S> boolean test(S stack, IngredientContext<S> ctx) {
            return base.test(stack, ctx) && !subtracted.test(stack, ctx);
        }
    }

    /** {@code tfc:not_rotten} (TFC {@code NotRottenIngredient}). */
    enum NotRotten implements Ingredient {
        INSTANCE;

        @Override
        public <S> boolean test(S stack, IngredientContext<S> ctx) {
            return !ctx.food().isRotten(stack);
        }

        @Override
        public boolean precise() {
            return true;
        }
    }

    /** {@code tfc:rotten} (TFC {@code RottenIngredient}). */
    enum Rotten implements Ingredient {
        INSTANCE;

        @Override
        public <S> boolean test(S stack, IngredientContext<S> ctx) {
            return ctx.food().isRotten(stack);
        }

        @Override
        public boolean precise() {
            return true;
        }
    }

    /** {@code tfc:has_trait} (TFC {@code HasTraitIngredient}). */
    record HasTrait(String trait) implements Ingredient {
        @Override
        public <S> boolean test(S stack, IngredientContext<S> ctx) {
            return ctx.food().hasTrait(stack, trait);
        }

        @Override
        public boolean precise() {
            return true;
        }
    }

    /** {@code tfc:lacks_trait} (TFC {@code LacksTraitIngredient}). */
    record LacksTrait(String trait) implements Ingredient {
        @Override
        public <S> boolean test(S stack, IngredientContext<S> ctx) {
            return !ctx.food().hasTrait(stack, trait);
        }

        @Override
        public boolean precise() {
            return true;
        }
    }

    /**
     * {@code tfc:heat} (TFC {@code HeatIngredient}): {@code min <= temperature <= max}; a missing bound
     * is -/+ infinity, and at least one bound is required.
     */
    record Heat(float min, float max) implements Ingredient {
        @Override
        public <S> boolean test(S stack, IngredientContext<S> ctx) {
            float temperature = ctx.heat().temperature(stack);
            return temperature >= min && temperature <= max;
        }

        @Override
        public boolean precise() {
            return true;
        }
    }

    /** {@code tfc:fluid_content} (TFC {@code FluidContentIngredient}): the item contains enough of a matching fluid. */
    record FluidContent(SizedFluidIngredient fluid) implements Ingredient {
        @Override
        public <S> boolean test(S stack, IngredientContext<S> ctx) {
            Optional<FluidAmount> contained = ctx.fluids().containedFluid(stack);
            return contained.isPresent() && fluid.test(contained.get(), ctx.tags());
        }
    }

    // ---- codecs ----

    /** Registry name used for food-trait references (resolved against the id table). */
    String FOOD_TRAIT = "food_trait";

    /** The typed ingredients, keyed by {@code "type"}. */
    TypeDispatch<Ingredient> TYPES = TypeDispatch.<Ingredient>builder("ingredient")
            .register("tfc:and", r -> new And(r.required("children", Codecs.lazy(() -> Ingredient.CODEC).listOf(1, Integer.MAX_VALUE))))
            .unit("tfc:not_rotten", NotRotten.INSTANCE)
            .unit("tfc:rotten", Rotten.INSTANCE)
            .register("tfc:has_trait", r -> new HasTrait(r.required("trait", Codecs.ref(FOOD_TRAIT))))
            .register("tfc:lacks_trait", r -> new LacksTrait(r.required("trait", Codecs.ref(FOOD_TRAIT))))
            .register("tfc:heat", r -> {
                float min = r.optional("min", Codecs.POSITIVE_FLOAT, Float.NEGATIVE_INFINITY);
                float max = r.optional("max", Codecs.POSITIVE_FLOAT, Float.POSITIVE_INFINITY);
                if (min == Float.NEGATIVE_INFINITY && max == Float.POSITIVE_INFINITY) {
                    throw r.context().error("tfc:heat must have one of min or max");
                }
                return new Heat(min, max);
            })
            .register("tfc:fluid_content", r -> new FluidContent(r.required("fluid", SizedFluidIngredient.CODEC)))
            .register("neoforge:compound", r -> new Compound(r.required("children", Codecs.lazy(() -> Ingredient.CODEC).listOf(1, Integer.MAX_VALUE))))
            .register("neoforge:intersection", r -> new Intersection(r.required("children", Codecs.lazy(() -> Ingredient.CODEC).listOf(1, Integer.MAX_VALUE))))
            .register("neoforge:difference", r -> new Difference(
                    r.required("base", Codecs.lazy(() -> Ingredient.CODEC)),
                    r.required("subtracted", Codecs.lazy(() -> Ingredient.CODEC))))
            .build();

    /**
     * Object form, reading only its own keys (so {@link SizedIngredient} can add {@code count}).
     * {@code type} selects a typed ingredient; otherwise exactly one of {@code item}/{@code tag}.
     */
    MapCodec<Ingredient> MAP_CODEC = r -> {
        if (r.has("type")) {
            return TYPES.decode(r);
        }
        if (r.has("item")) {
            return new Item(r.required("item", Codecs.ref(TagType.ITEM.directory())));
        }
        if (r.has("tag")) {
            return new Tag(r.required("tag", Codecs.tagRef(TagType.ITEM.directory())));
        }
        throw r.context().error("expected an ingredient with 'item', 'tag' or 'type'");
    };

    /** Any ingredient: an object, or a non-empty array (any of). */
    Codec<Ingredient> CODEC = new Codec<>() {
        @Override
        public Ingredient decode(JsonElement json, DecodeContext ctx) {
            if (json.isJsonArray()) {
                return new AnyOf(this.listOf(1, Integer.MAX_VALUE).decode(json, ctx));
            }
            ObjectReader r = ObjectReader.of(json, ctx);
            Ingredient value = MAP_CODEC.decode(r);
            r.finish();
            return value;
        }
    };

    private static <S> boolean any(List<Ingredient> children, S stack, IngredientContext<S> ctx) {
        for (Ingredient c : children) {
            if (c.test(stack, ctx)) {
                return true;
            }
        }
        return false;
    }

    private static <S> boolean all(List<Ingredient> children, S stack, IngredientContext<S> ctx) {
        for (Ingredient c : children) {
            if (!c.test(stack, ctx)) {
                return false;
            }
        }
        return true;
    }
}
