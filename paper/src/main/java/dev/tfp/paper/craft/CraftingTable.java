/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/recipes/AdvancedShapedRecipe.java @ c3b4d9536 (assemble,
 * getRemainingItems), AdvancedShapelessRecipe.java (assemble, getRemainingItems, getPrimaryInput),
 * FoodCombiningCraftingRecipe.java, CastingCraftingRecipe.java, CastingRecipe.java (assemble) and RecipeHelpers.java
 * (getRemainderItemsWithProvider)
 */
package dev.tfp.paper.craft;

import dev.tfp.core.craft.AdvancedCraftGrid;
import dev.tfp.core.craft.AdvancedCraftRules;
import dev.tfp.core.crucible.CrucibleRules;
import dev.tfp.core.food.FoodDefinitions;
import dev.tfp.core.food.FoodState;
import dev.tfp.core.food.FoodTrait;
import dev.tfp.core.recipe.fluid.FluidAmount;
import dev.tfp.core.recipe.fluid.SizedFluidIngredient;
import dev.tfp.core.recipe.ingredient.Ingredient;
import dev.tfp.core.recipe.output.ItemStackModifier;
import dev.tfp.core.recipe.output.ItemStackProvider;
import dev.tfp.paper.food.FoodPdc;
import dev.tfp.paper.food.ItemFoodHooks;
import dev.tfp.paper.heat.ItemHeatHooks;
import dev.tfp.paper.itemdata.ItemFluids;
import dev.tfp.paper.recipe.ItemIngredientContext;
import dev.tfp.paper.recipe.ItemModifierHooks;
import dev.tfp.paper.recipe.RecipeEntry;
import dev.tfp.paper.recipe.RecipeIndex;
import dev.tfp.paper.recipe.RecipeTypes;
import dev.tfp.paper.recipe.Recipes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * The crafting grid on our recipe runtime: finds the recipe of a grid among the six crafting types of TFC's data and
 * computes what a craft gives (the output, each slot's remainder, extra products). Never touches an inventory; the
 * {@link CraftListener} applies the {@link Craft}. Runs on the thread that owns the grid (the player's).
 *
 * <p>Lookup: the candidates of every crafting type for the first non-empty slot's item (PAR-INFRA-06 index), in recipe-id
 * order; the first that matches wins (the recorded id-order divergence of the index).
 */
public final class CraftingTable {
    /** What a grid matched. */
    public record Match(CraftRecipe recipe, AdvancedCraftGrid.Input<ItemStack> input, List<ItemStack> grid,
            int primarySlot, Optional<RecipeEntry> casting) {
    }

    /**
     * What one craft gives.
     *
     * @param output     the crafted stack
     * @param remainders per grid slot, what is left in it after one item is taken ({@code null} for nothing)
     * @param extras     stacks given to the crafter ({@code tfc:extra_products})
     */
    public record Craft(ItemStack output, List<ItemStack> remainders, List<ItemStack> extras) {
    }

    private record Compiled(RecipeIndex index, Map<String, CraftRecipe> byId) {
    }

    private static final NamespacedKey FOOD_KEY = Objects.requireNonNull(NamespacedKey.fromString(FoodPdc.KEY));

    private final Recipes recipes;
    private final ItemFoodHooks food;
    private final Supplier<FoodDefinitions> foodDefinitions;
    private final ItemHeatHooks heat;
    private final DoubleSupplier random;
    private final AtomicReference<Compiled> compiled = new AtomicReference<>(new Compiled(null, Map.of()));

    /**
     * @param random uniform draws in {@code [0, 1)} for the casting break roll (TFC: the player's random)
     */
    public CraftingTable(Recipes recipes, ItemFoodHooks food, Supplier<FoodDefinitions> foodDefinitions, ItemHeatHooks heat,
            DoubleSupplier random) {
        this.recipes = Objects.requireNonNull(recipes, "recipes");
        this.food = Objects.requireNonNull(food, "food");
        this.foodDefinitions = Objects.requireNonNull(foodDefinitions, "foodDefinitions");
        this.heat = Objects.requireNonNull(heat, "heat");
        this.random = Objects.requireNonNull(random, "random");
    }

    /** The recipe runtime this table reads. */
    public Recipes recipes() {
        return recipes;
    }

    static boolean empty(ItemStack stack) {
        return stack == null || stack.isEmpty();
    }

    /** The compiled crafting recipes of {@code index}, keyed by id; compiled once per index. */
    Map<String, CraftRecipe> compiled(RecipeIndex index) {
        Compiled c = compiled.get();
        if (c.index() == index) {
            return c.byId();
        }
        Map<String, CraftRecipe> byId = new HashMap<>();
        for (String type : CraftRecipe.TYPES) {
            for (RecipeEntry e : index.all(type)) {
                byId.put(e.id(), CraftRecipe.compile(e));
            }
        }
        Compiled next = new Compiled(index, Map.copyOf(byId));
        compiled.set(next);
        return next.byId();
    }

    /** Whether the current data holds a crafting recipe with this id (a TFC override of a vanilla recipe, say). */
    public boolean hasRecipe(String id) {
        RecipeIndex index = recipes.index();
        return compiled(index).containsKey(id);
    }

    /** Whether TFC disables the vanilla recipe {@code id} (a false {@code neoforge:conditions}). */
    public boolean disabled(String id) {
        return recipes.index().registry().disabledRecipes().contains(id);
    }

    /**
     * The first crafting recipe of the data that matches the grid.
     *
     * @param grid  every slot, row-major ({@code null} or air for empty)
     * @param width the grid's width
     */
    public Optional<Match> find(List<ItemStack> grid, int width) {
        AdvancedCraftGrid.Input<ItemStack> input = AdvancedCraftGrid.positioned(width, grid, CraftingTable::empty);
        if (input.width() == 0) {
            return Optional.empty();
        }
        ItemStack first = input.items().stream().filter(s -> !empty(s)).findFirst().orElseThrow();
        RecipeIndex index = recipes.index();
        Map<String, CraftRecipe> byId = compiled(index);
        ItemIngredientContext ctx = recipes.ingredients();
        String firstId = ctx.itemId(first);
        Set<String> ids = new LinkedHashSet<>();
        for (String type : CraftRecipe.TYPES) {
            for (RecipeEntry e : index.byItem(type, firstId)) {
                ids.add(e.id());
            }
        }
        List<String> ordered = new ArrayList<>(ids);
        ordered.sort(Comparator.naturalOrder());
        for (String id : ordered) {
            CraftRecipe recipe = byId.get(id);
            if (recipe != null) {
                Optional<Match> m = match(recipe, input, grid, ctx, index);
                if (m.isPresent()) {
                    return m;
                }
            }
        }
        return Optional.empty();
    }

    private Optional<Match> match(CraftRecipe recipe, AdvancedCraftGrid.Input<ItemStack> input, List<ItemStack> grid,
            ItemIngredientContext ctx, RecipeIndex index) {
        return switch (recipe.kind()) {
            case SHAPED, ADVANCED_SHAPED -> {
                int t = AdvancedCraftGrid.shapedMatch(recipe.ingredients(), recipe.width(), recipe.height(), input,
                        (Ingredient i, ItemStack s) -> i.test(s, ctx), CraftingTable::empty, recipe.inputSlot());
                yield t < 0 ? Optional.empty()
                        : Optional.of(new Match(recipe, input, grid, input.fullIndex(t), Optional.empty()));
            }
            case SHAPELESS, ADVANCED_SHAPELESS -> {
                if (!AdvancedCraftGrid.shapelessMatches(recipe.ingredients(), grid,
                        (Ingredient i, ItemStack s) -> i.test(s, ctx), CraftingTable::empty)) {
                    yield Optional.empty();
                }
                int primary = -1;
                if (recipe.primary().isPresent()) {
                    List<Boolean> hits = new ArrayList<>(grid.size());
                    for (ItemStack s : grid) {
                        hits.add(!empty(s) && recipe.primary().get().test(s, ctx));
                    }
                    primary = AdvancedCraftRules.primaryIndex(hits);
                }
                yield Optional.of(new Match(recipe, input, grid, primary, Optional.empty()));
            }
            case FOOD_COMBINING -> AdvancedCraftRules.foodCombiningMatches(grid, CraftingTable::empty,
                    s -> isFood(s, ctx), this::stackableExceptCreationDate)
                    ? Optional.of(new Match(recipe, input, grid, -1, Optional.empty())) : Optional.empty();
            case CASTING -> {
                int slot = AdvancedCraftRules.castingMoldIndex(grid, CraftingTable::empty, s -> isMold(s, ctx, index));
                if (slot < 0) {
                    yield Optional.empty();
                }
                ItemStack mold = grid.get(slot);
                Optional<FluidAmount> content = ItemIngredientContext.containedFluid(mold);
                if (content.isEmpty() || isMolten(mold, content.get())) {
                    yield Optional.empty();
                }
                yield castingRecipe(mold, content.get(), ctx, index)
                        .map(c -> new Match(recipe, input, grid, slot, Optional.of(c)));
            }
        };
    }

    // ---- food combining ----

    /** TFC {@code FoodCapability.has}: the item has food data (a food definition, or a food record on the stack). */
    boolean isFood(ItemStack stack, ItemIngredientContext ctx) {
        return FoodPdc.has(stack) || foodDefinitions.get().get(ctx.itemId(stack)).isPresent();
    }

    /** TFC {@code FoodCapability.areStacksStackableExceptCreationDate}: equal but for the creation date. */
    boolean stackableExceptCreationDate(ItemStack a, ItemStack b) {
        List<FoodTrait> ta = FoodPdc.read(a).map(FoodState::traits).orElse(List.of());
        List<FoodTrait> tb = FoodPdc.read(b).map(FoodState::traits).orElse(List.of());
        return ta.equals(tb) && withoutFood(a).isSimilar(withoutFood(b));
    }

    private static ItemStack withoutFood(ItemStack stack) {
        ItemStack copy = stack.clone();
        ItemMeta meta = copy.getItemMeta();
        if (meta != null && meta.getPersistentDataContainer().has(FOOD_KEY)) {
            meta.getPersistentDataContainer().remove(FOOD_KEY);
            copy.setItemMeta(meta);
        }
        copy.setAmount(1);
        return copy;
    }

    private long creationDate(ItemStack stack) {
        return FoodPdc.read(stack).map(FoodState::creationDate)
                .orElseGet(() -> FoodState.createdAt(food.now(), food.config()).creationDate());
    }

    // ---- casting ----

    private static boolean isMold(ItemStack stack, ItemIngredientContext ctx, RecipeIndex index) {
        for (RecipeEntry e : index.byItem(RecipeTypes.CASTING, ctx.itemId(stack))) {
            if (e.recipe().values().get("mold") instanceof Ingredient i && i.test(stack, ctx)) {
                return true;
            }
        }
        return false;
    }

    /** TFC {@code Mold.isMolten}: the content is at or above its melt temperature. */
    private boolean isMolten(ItemStack mold, FluidAmount content) {
        Optional<Float> melt = CrucibleRules.meltTemperature(content.fluid())
                .or(() -> CrucibleRules.meltTemperature(content.fluid().replaceFirst("^tfp:", "tfc:")));
        return melt.isPresent() && heat.temperature(mold) >= melt.get();
    }

    /** TFC {@code CastingRecipe.get(mold)}: the first casting recipe of the mold item and its fluid. */
    private static Optional<RecipeEntry> castingRecipe(ItemStack mold, FluidAmount content, ItemIngredientContext ctx,
            RecipeIndex index) {
        return index.first(RecipeTypes.CASTING, mold, ctx,
                r -> r.recipe().values().get("fluid") instanceof SizedFluidIngredient f
                        && f.test(content, ctx.tags()));
    }

    private static float breakChance(RecipeEntry casting) {
        return casting.recipe().get("break_chance", Float.class).orElse(1f);
    }

    // ---- results ----

    /** TFC {@code assemble}: the output shown in the result slot (no extra products are given). */
    public ItemStack preview(Match m) {
        return output(m, recipes.modifiers().forCrafting(m.grid(), s -> {
        }));
    }

    private ItemStack output(Match m, ItemModifierHooks hooks) {
        CraftRecipe r = m.recipe();
        return switch (r.kind()) {
            case SHAPED, SHAPELESS -> hooks.create(r.template().orElseThrow());
            case ADVANCED_SHAPED, ADVANCED_SHAPELESS -> {
                ItemStack primary = m.primarySlot() < 0 ? ItemStack.empty() : m.grid().get(m.primarySlot()).clone();
                yield r.result().orElseThrow().getSingleStack(primary, hooks);
            }
            case FOOD_COMBINING -> {
                ItemStack result = null;
                int count = 0;
                List<Long> dates = new ArrayList<>();
                for (ItemStack s : m.grid()) {
                    if (!empty(s)) {
                        count++;
                        dates.add(creationDate(s));
                        if (result == null) {
                            result = s.clone();
                        }
                    }
                }
                Objects.requireNonNull(result, "a food combining match has stacks");
                result.setAmount(count);
                List<FoodTrait> traits = FoodPdc.read(result).map(FoodState::traits).orElse(List.of());
                FoodPdc.write(result, new FoodState(AdvancedCraftRules.combinedCreationDate(dates), traits));
                yield result;
            }
            case CASTING -> {
                ItemStack mold = m.grid().get(m.primarySlot());
                ItemStackProvider provider = m.casting().orElseThrow().recipe().require("result", ItemStackProvider.class);
                ItemStack out = provider.getSingleStack(mold.clone(), hooks);
                if (!empty(out) && heat.temperature(mold) > heat.temperature(out)) {
                    out = hooks.copyHeat(out, mold);
                }
                yield out;
            }
        };
    }

    /**
     * One craft: the output, each slot's remainder ({@code getRemainingItems}) and the extra products. Rolls the casting
     * break chance and Unbreaking on damaged tools, so call it once per craft actually taken.
     */
    public Craft craft(Match m) {
        List<ItemStack> extras = new ArrayList<>();
        ItemModifierHooks hooks = recipes.modifiers().forCrafting(m.grid(), extras::add);
        ItemStack output = output(m, hooks);
        ItemStack[] remainders = new ItemStack[m.grid().size()];
        CraftRecipe r = m.recipe();
        switch (r.kind()) {
            case SHAPED, SHAPELESS -> vanillaRemainders(m.grid(), remainders, hooks);
            case ADVANCED_SHAPED, ADVANCED_SHAPELESS -> {
                if (r.remainder().isPresent()) {
                    providerRemainders(m, r.remainder().get(), remainders, hooks);
                } else {
                    vanillaRemainders(m.grid(), remainders, hooks);
                }
            }
            case FOOD_COMBINING -> {
                // TFC: no remainders at all (container items moved to the output, TerraFirmaCraft#2788)
            }
            case CASTING -> {
                ItemStack mold = m.grid().get(m.primarySlot());
                // Kept when the roll is above the break chance (CastingCraftingRecipe.getRemainingItems)
                if (random.getAsDouble() > breakChance(m.casting().orElseThrow())) {
                    ItemStack kept = mold.clone();
                    kept.setAmount(1);
                    ItemFluids.drain(kept, Integer.MAX_VALUE);
                    remainders[m.primarySlot()] = kept;
                }
            }
        }
        return new Craft(output, Arrays.asList(remainders), List.copyOf(extras));
    }

    private static void vanillaRemainders(List<ItemStack> grid, ItemStack[] out, ItemModifierHooks hooks) {
        for (int i = 0; i < grid.size(); i++) {
            ItemStack s = grid.get(i);
            if (!empty(s)) {
                ItemStack rem = hooks.craftingRemainder(s);
                out[i] = empty(rem) ? null : rem;
            }
        }
    }

    /** TFC {@code RecipeHelpers.getRemainderItemsWithProvider}. */
    private void providerRemainders(Match m, ItemStackProvider provider, ItemStack[] out, ItemModifierHooks hooks) {
        ItemIngredientContext ctx = recipes.ingredients();
        ItemStack primary = m.primarySlot() < 0 ? null : m.grid().get(m.primarySlot());
        String primaryId = primary == null ? null : ctx.itemId(primary);
        List<ItemStack> grid = m.grid();
        for (int i = 0; i < grid.size(); i++) {
            ItemStack s = grid.get(i);
            if (empty(s)) {
                continue;
            }
            ItemStack rem;
            if (primaryId != null && primaryId.equals(ctx.itemId(s))) {
                ItemStack single = s.clone();
                single.setAmount(1);
                rem = provider.getStack(single, ItemStackModifier.Context.DEFAULT, hooks);
            } else {
                rem = hooks.craftingRemainder(s);
            }
            out[i] = empty(rem) ? null : rem;
        }
    }
}
