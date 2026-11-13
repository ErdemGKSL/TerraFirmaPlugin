/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/recipes/HeatingRecipe.java @ c3b4d9536 (getRecipe, assembleItem,
 *       assembleFluid, getTemperature)
 */
package dev.tfp.paper.heat;

import dev.tfp.core.heat.HeatConfig;
import dev.tfp.core.heat.HeatState;
import dev.tfp.core.heat.ItemHeatDefinitions;
import dev.tfp.core.recipe.fluid.FluidAmount;
import dev.tfp.core.recipe.fluid.FluidStackTemplate;
import dev.tfp.core.recipe.hooks.FluidHooks;
import dev.tfp.core.recipe.hooks.FoodHooks;
import dev.tfp.core.recipe.hooks.HeatHooks;
import dev.tfp.core.recipe.ingredient.IngredientContext;
import dev.tfp.core.recipe.output.ItemStackProvider;
import dev.tfp.core.recipe.output.ItemStackTemplate;
import dev.tfp.core.tag.TagLookup;
import dev.tfp.paper.recipe.RecipeEntry;
import dev.tfp.paper.recipe.RecipeIndex;
import dev.tfp.paper.tooltip.TooltipData;
import dev.tfp.paper.tooltip.TooltipItem;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * PAR-HEAT-02: the heating recipe of an outgoing item stack, as far as its tooltip needs it (TFC
 * {@code HeatingRecipe.getRecipe(stack)}): the recipe temperature, the fluid one item melts into and whether the item
 * is lost (an empty item result, TFC's "Danger!!" case).
 *
 * <p>Netty thread: reads the immutable recipe index and one tag snapshot per lookup. The stack is the packet's
 * {@link TooltipItem}: its id is the CraftEngine id (else the vanilla id) and its temperature is the lazy one of its
 * {@code tfp:heat} record. Food state is not read here: a {@code tfc:not_rotten} ingredient sees a fresh item (the
 * heating recipes that check it all have an item result, so neither line changes).
 */
public final class HeatTooltipRecipes {
    /**
     * What the tooltip shows of one heating recipe.
     *
     * @param temperature the recipe temperature (TFC {@code getTemperature})
     * @param fluid       the fluid one item melts into, empty when the recipe has none
     * @param destroys    whether the recipe's item result is empty (the item is lost when heated to the temperature)
     */
    public record Heating(float temperature, Optional<FluidAmount> fluid, boolean destroys) {
        public Heating {
            Objects.requireNonNull(fluid, "fluid");
        }
    }

    private final Function<String, List<RecipeEntry>> candidates;
    private final Supplier<TagLookup> tags;

    /**
     * @param candidates the heating recipes that may accept an item id, in id order
     *                   ({@code index.byItem(RecipeTypes.HEATING, id)})
     * @param tags       the tag lookup of the current tag snapshot (taken once per lookup)
     */
    public HeatTooltipRecipes(Function<String, List<RecipeEntry>> candidates, Supplier<TagLookup> tags) {
        this.candidates = Objects.requireNonNull(candidates, "candidates");
        this.tags = Objects.requireNonNull(tags, "tags");
    }

    /** The heating recipe of {@code item} at {@code temperature}: the first candidate whose ingredient accepts it. */
    public Optional<Heating> find(TooltipItem item, float temperature) {
        List<RecipeEntry> list = candidates.apply(item.id());
        if (list == null || list.isEmpty()) {
            return Optional.empty();
        }
        IngredientContext<TooltipItem> ctx = context(tags.get(), temperature);
        return RecipeIndex.first(list, r -> RecipeIndex.accepts(r.recipe().values().get("ingredient"), item, ctx))
                .map(HeatTooltipRecipes::heating);
    }

    /** The tooltip facts of one {@code tfc:heating} recipe. */
    public static Heating heating(RecipeEntry recipe) {
        float temperature = recipe.recipe().require("temperature", Number.class).floatValue();
        Optional<FluidAmount> fluid = field(recipe, "result_fluid")
                .filter(FluidStackTemplate.class::isInstance)
                .map(FluidStackTemplate.class::cast)
                .filter(f -> f.amount() > 0)
                .map(f -> new FluidAmount(f.id(), f.amount()));
        Optional<ItemStackProvider> result = field(recipe, "result_item")
                .filter(ItemStackProvider.class::isInstance)
                .map(ItemStackProvider.class::cast);
        return new Heating(temperature, fluid, result.map(HeatTooltipRecipes::isEmpty).orElse(true));
    }

    /** An optional field's value, whether the schema stores it bare or wrapped in an {@link Optional}. */
    private static Optional<Object> field(RecipeEntry recipe, String name) {
        Object value = recipe.recipe().values().get(name);
        return value instanceof Optional<?> o ? o.map(Object.class::cast) : Optional.ofNullable(value);
    }

    /**
     * Whether an item result is always empty (TFC {@code outputItem.getSingleStack(input).isEmpty()}): no base stack
     * (or air) and no modifier that could make one from the input.
     */
    static boolean isEmpty(ItemStackProvider provider) {
        Optional<ItemStackTemplate> stack = provider.stack();
        boolean base = stack.isPresent() && stack.get().count() > 0 && !stack.get().id().equals("minecraft:air");
        return !base && provider.modifiers().isEmpty();
    }

    /**
     * The lazy temperature of the packet stack at {@code now} (player ticks): its {@code tfp:heat} record cooled with the
     * item's heat capacity. Empty when there is no readable record or the item cannot be heated.
     */
    public static Optional<Float> temperature(TooltipItem item, ItemHeatDefinitions definitions, HeatConfig config,
            long now) {
        TooltipData heat = item.values().compound(HeatPdc.KEY);
        if (heat == null || item.customId() == null) {
            return Optional.empty();
        }
        Number version = heat.number("tfp:v");
        Number temperature = heat.number("tfp:temperature");
        Number tick = heat.number("tfp:tick");
        if (version == null || version.intValue() > HeatPdc.VERSION || temperature == null || tick == null) {
            return Optional.empty();
        }
        float capacity = definitions.heatCapacity(item.customId());
        if (!(capacity > 0f)) {
            return Optional.empty();
        }
        return Optional.of(new HeatState(temperature.floatValue(), tick.longValue()).temperatureAt(now, capacity, config));
    }

    private static IngredientContext<TooltipItem> context(TagLookup tags, float temperature) {
        return new IngredientContext<>() {
            @Override
            public String itemId(TooltipItem stack) {
                return stack.id();
            }

            @Override
            public TagLookup tags() {
                return tags;
            }

            @Override
            public FoodHooks<TooltipItem> food() {
                return new FoodHooks<>() {
                    @Override
                    public boolean isRotten(TooltipItem stack) {
                        return false;
                    }

                    @Override
                    public boolean hasTrait(TooltipItem stack, String trait) {
                        return false;
                    }
                };
            }

            @Override
            public HeatHooks<TooltipItem> heat() {
                return stack -> temperature;
            }

            @Override
            public FluidHooks<TooltipItem> fluids() {
                return stack -> Optional.empty();
            }
        };
    }
}
