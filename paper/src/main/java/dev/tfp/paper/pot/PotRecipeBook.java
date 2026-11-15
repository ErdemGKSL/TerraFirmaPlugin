/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/PotBlockEntity.java @ c3b4d9536 (updateCachedRecipe: the
 *       matching recipe, fallbacks last; handleCooking: the output, the inputs replaced by their crafting remainders,
 *       onFinish; PotInventory.canInsertFluid),
 *       src/main/java/net/dries007/tfc/common/recipes/PotRecipe.java @ c3b4d9536 (matches),
 *       src/main/java/net/dries007/tfc/common/recipes/SimplePotRecipe.java @ c3b4d9536 (getOutput, favorNewFluidStack,
 *       SimpleOutput.onFinish),
 *       src/main/java/net/dries007/tfc/common/recipes/SoupPotRecipe.java @ c3b4d9536 (getOutput: clearFluid, the food
 *       ingredients, the soup item of the largest nutrient, INGREDIENTS, setFoodForDynamicItemOnCreate),
 *       src/main/java/net/dries007/tfc/common/recipes/JamPotRecipe.java @ c3b4d9536 (getOutput: clearFluid, copies of the
 *       unsealed and sealed results)
 */
package dev.tfp.paper.pot;

import dev.tfp.core.cooking.SoupMix;
import dev.tfp.core.food.Nutrient;
import dev.tfp.core.pot.PotData;
import dev.tfp.core.pot.PotMatch;
import dev.tfp.core.pot.PotOutput;
import dev.tfp.core.recipe.food.FoodData;
import dev.tfp.core.recipe.output.ItemStackProvider;
import dev.tfp.core.recipe.output.ItemStackTemplate;
import dev.tfp.core.tag.TagType;
import dev.tfp.core.vessel.FluidAmount;
import dev.tfp.paper.device.StackCodec;
import dev.tfp.paper.recipe.ItemIngredientContext;
import dev.tfp.paper.recipe.RecipeEntry;
import dev.tfp.paper.recipe.RecipeIndex;
import dev.tfp.paper.recipe.Recipes;
import dev.tfp.paper.salad.SaladPdc;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * The pot's recipes on real stacks (PAR-HEAT-09): TFC's 145 simple, 66 jam and 3 soup recipes of the recipe runtime,
 * decoded once per recipe index, their matching and their outputs as TFC's recipe classes make them. Any thread; stacks
 * belong to the caller.
 */
public final class PotRecipeBook {
    /** TFC {@code TFCTags.Fluids.USABLE_IN_POT}. */
    public static final String USABLE_IN_POT = "tfc:usable_in_pot";
    /** The soup items, {@code tfp:food/<nutrient>_soup} (TFC {@code TFCItems.SOUPS}). */
    public static final String SOUP_PREFIX = "tfp:food/";

    /**
     * What the book reads from the rest of the plugin.
     *
     * @param recipes the recipe runtime
     * @param food    TFC {@code FoodCapability.get(stack).getData()}: dynamic food first, else the item's definition
     * @param rotten  TFC {@code IFood.isRotten} now
     * @param stamp   TFC {@code setFoodForDynamicItemOnCreate}: the creation date of a new food stack
     * @param itemIds the item id of a stack
     * @param remainder TFC {@code getCraftingRemainingItem}: what a used input leaves, empty for nothing
     */
    public record Services(Recipes recipes, Function<ItemStack, Optional<FoodData>> food, Predicate<ItemStack> rotten,
            Consumer<ItemStack> stamp, Function<ItemStack, Optional<String>> itemIds,
            Function<ItemStack, Optional<ItemStack>> remainder) {
        public Services {
            Objects.requireNonNull(recipes, "recipes");
            Objects.requireNonNull(food, "food");
            Objects.requireNonNull(rotten, "rotten");
            Objects.requireNonNull(stamp, "stamp");
            Objects.requireNonNull(itemIds, "itemIds");
            Objects.requireNonNull(remainder, "remainder");
        }
    }

    /**
     * The pot after a recipe finished.
     *
     * @param inputs the five input slots (the outputs of a simple recipe, the crafting remainders otherwise)
     * @param fluid  the tank
     * @param output what waits to be taken
     */
    public record Finished(List<ItemStack> inputs, FluidAmount fluid, PotOutput output) {
    }

    private record Decoded(RecipeIndex index, List<PotRecipeView> views) {
    }

    private final Services services;
    private final AtomicReference<Decoded> decoded = new AtomicReference<>();

    public PotRecipeBook(Services services) {
        this.services = Objects.requireNonNull(services, "services");
    }

    /** Every pot recipe: the simple ones, then the jams, then the soups (the fallbacks), each in index order. */
    public List<PotRecipeView> all() {
        RecipeIndex index = services.recipes().index();
        Decoded d = decoded.get();
        if (d == null || d.index() != index) {
            List<PotRecipeView> list = new ArrayList<>();
            for (PotRecipeView.Kind k : PotRecipeView.Kind.values()) {
                for (RecipeEntry e : index.all(k.type())) {
                    list.add(PotRecipeView.of(e, k));
                }
            }
            d = new Decoded(index, List.copyOf(list));
            decoded.set(d);
        }
        return d.views();
    }

    /** The item id of a stack (CraftEngine id, else the material key). */
    public Optional<String> itemId(ItemStack stack) {
        return services.itemIds().apply(stack);
    }

    /** TFC {@code PotInventory.canInsertFluid} (without the output check): {@code #tfc:usable_in_pot}. */
    public boolean usable(String fluid) {
        return services.recipes().ingredients().tags().contains(TagType.FLUID, USABLE_IN_POT, fluid);
    }

    /**
     * TFC {@code updateCachedRecipe}: the first recipe that matches the five input slots and the tank, a non-fallback
     * one before any fallback ({@code min(isFallback)} keeps the first of each).
     */
    public Optional<PotRecipeView> select(List<ItemStack> inputs, FluidAmount fluid) {
        ItemIngredientContext ctx = services.recipes().ingredients();
        List<ItemStack> stacks = new ArrayList<>(inputs.size());
        for (ItemStack s : inputs) {
            if (!StackCodec.isEmpty(s)) {
                stacks.add(s);
            }
        }
        dev.tfp.core.recipe.fluid.FluidAmount tank = new dev.tfp.core.recipe.fluid.FluidAmount(fluid.fluid(), fluid.amount());
        PotRecipeView fallback = null;
        for (PotRecipeView r : all()) {
            if (fallback != null && r.fallback()) {
                continue;
            }
            if (r.ingredients().size() != stacks.size() || !r.fluidIngredient().test(tank, ctx.tags())) {
                continue;
            }
            if (!PotMatch.matches(stacks, r.ingredients(), (ingredient, stack) -> ingredient.test(stack, ctx))) {
                continue;
            }
            if (!r.fallback()) {
                return Optional.of(r);
            }
            fallback = r;
        }
        return Optional.ofNullable(fallback);
    }

    /**
     * TFC {@code handleCooking} once the boil is done: {@code recipe.getOutput} on the contents, the inputs replaced by
     * their crafting remainders, then {@code output.onFinish}; a non-empty output is kept.
     *
     * @param inputs the five input slots
     * @param stacks the codec of the output stacks
     */
    public Finished finish(PotRecipeView recipe, List<ItemStack> inputs, FluidAmount fluid, StackCodec stacks) {
        List<ItemStack> in = new ArrayList<>(inputs.size());
        for (ItemStack s : inputs) {
            in.add(StackCodec.isEmpty(s) ? ItemStack.empty() : s.clone());
        }
        return switch (recipe.kind()) {
            case SIMPLE -> simple(recipe, in, fluid);
            case SOUP -> new Finished(remainders(in), FluidAmount.EMPTY, soup(in, stacks));
            case JAM -> new Finished(remainders(in), FluidAmount.EMPTY, new PotOutput.Jam(
                stacks.encode(template(recipe.unsealedResult())), stacks.encode(template(recipe.sealedResult())),
                recipe.texture()));
        };
    }

    /** TFC {@code SimplePotRecipe.getOutput} and {@code SimpleOutput.onFinish}. */
    private Finished simple(PotRecipeView recipe, List<ItemStack> in, FluidAmount fluid) {
        // the outputs are computed before the inputs are cleared
        List<ItemStack> outputs = new ArrayList<>(5);
        List<ItemStackProvider> providers = new ArrayList<>(recipe.itemOutputs());
        for (ItemStack input : in) {
            if (providers.isEmpty()) {
                break;
            }
            if (!StackCodec.isEmpty(input) || recipe.ingredients().isEmpty()) {
                outputs.add(services.recipes().singleOutput(providers.removeFirst(), input));
            }
        }
        FluidAmount out = recipe.usesAllFluid() ? outputFluid(recipe) : favorNewFluidStack(recipe, fluid);
        List<ItemStack> slots = remainders(in);
        for (int i = 0; i < outputs.size(); i++) {
            ItemStack o = outputs.get(i);
            slots.set(i, StackCodec.isEmpty(o) ? ItemStack.empty() : o);
        }
        // clearFluid, then fill: the tank takes the fluid when it is usable in a pot, up to its capacity
        FluidAmount tank = FluidAmount.EMPTY;
        if (!out.isEmpty() && usable(out.fluid())) {
            tank = new FluidAmount(out.fluid(), Math.min(PotData.CAPACITY, out.amount()));
        }
        return new Finished(slots, tank, PotOutput.NONE);
    }

    /** TFC {@code favorNewFluidStack}: the input fluid less the recipe's amount when there is no output fluid. */
    private static FluidAmount favorNewFluidStack(PotRecipeView recipe, FluidAmount input) {
        if (input.amount() > recipe.fluidIngredient().amount() && recipe.fluidOutput() == null) {
            return new FluidAmount(input.fluid(), input.amount() - recipe.fluidIngredient().amount());
        }
        return outputFluid(recipe);
    }

    private static FluidAmount outputFluid(PotRecipeView recipe) {
        return recipe.fluidOutput() == null ? FluidAmount.EMPTY
            : new FluidAmount(fluidId(recipe.fluidOutput().id()), recipe.fluidOutput().amount());
    }

    /** TFC {@code SoupPotRecipe.getOutput}: the soup of the food in the pot, or none without food. */
    private PotOutput soup(List<ItemStack> in, StackCodec stacks) {
        List<SoupMix.Ingredient> food = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        for (ItemStack stack : in) {
            if (StackCodec.isEmpty(stack)) {
                continue;
            }
            Optional<FoodData> data = services.food().apply(stack);
            if (data.isEmpty()) {
                continue; // TFC: FoodCapability.get is null, the stack is no ingredient
            }
            food.add(new SoupMix.Ingredient(data.get(), services.rotten().test(stack)));
            ids.add(services.itemIds().apply(stack).orElse(stack.getType().getKey().toString()));
        }
        Optional<SoupMix.Soup> mix = SoupMix.mix(food);
        if (mix.isEmpty()) {
            return PotOutput.NONE;
        }
        ItemStack soup = template(new ItemStackTemplate(soupItem(mix.get().item()), mix.get().servings(), Optional.empty()));
        if (StackCodec.isEmpty(soup)) {
            return PotOutput.NONE;
        }
        // TFC INGREDIENTS and the dynamic FoodData; the bowl is set when a serving is taken
        SaladPdc.write(soup, mix.get().food(), ids, "");
        services.stamp().accept(soup);
        return new PotOutput.Soup(stacks.encode(soup));
    }

    /** The soup item of a nutrient: {@code tfp:food/grain_soup} ... */
    public static String soupItem(Nutrient nutrient) {
        return SOUP_PREFIX + nutrient.name().toLowerCase(Locale.ROOT) + "_soup";
    }

    /** A stack of a recipe template (count and components through the recipe runtime). */
    ItemStack template(ItemStackTemplate t) {
        return services.recipes().output(new ItemStackProvider(Optional.of(t), List.of()), ItemStack.empty());
    }

    /** TFC: each input slot becomes {@code getCraftingRemainingItem()}. */
    private List<ItemStack> remainders(List<ItemStack> in) {
        List<ItemStack> out = new ArrayList<>(in.size());
        for (ItemStack s : in) {
            out.add(StackCodec.isEmpty(s) ? ItemStack.empty() : services.remainder().apply(s).orElse(ItemStack.empty()));
        }
        return out;
    }

    /**
     * Vanilla's crafting remainders (a milk bucket leaves a bucket) for vanilla items; our CraftEngine items have none.
     */
    public static Optional<ItemStack> vanillaRemainder(ItemStack stack, Function<ItemStack, Optional<String>> itemIds) {
        if (!itemIds.apply(stack).map(id -> id.startsWith("minecraft:")).orElse(true)) {
            return Optional.empty();
        }
        Material m = stack.getType().getCraftingRemainingItem();
        return m == null || m.isAir() ? Optional.empty() : Optional.of(new ItemStack(m));
    }

    /** A recipe's fluid id as the tank stores it (the registry's ids, {@code tfc:} renamed to {@code tfp:}). */
    static String fluidId(String id) {
        return id.startsWith("tfc:") ? "tfp:" + id.substring("tfc:".length()) : id;
    }
}
