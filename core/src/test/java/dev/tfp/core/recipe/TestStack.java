/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.recipe;

import dev.tfp.core.recipe.fluid.FluidAmount;
import dev.tfp.core.recipe.hooks.FluidHooks;
import dev.tfp.core.recipe.hooks.FoodHooks;
import dev.tfp.core.recipe.hooks.HeatHooks;
import dev.tfp.core.recipe.hooks.ModifierHooks;
import dev.tfp.core.recipe.ingredient.IngredientContext;
import dev.tfp.core.recipe.output.ItemStackModifier;
import dev.tfp.core.recipe.output.ItemStackTemplate;
import dev.tfp.core.tag.TagLookup;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.DoubleSupplier;

/**
 * Immutable fake item stack for recipe tests. {@code log} records which hooks touched it, so
 * tests can check that each modifier calls the right later-system hook.
 */
record TestStack(String item, int count, boolean rotten, Set<String> traits, float temperature,
                 Optional<FluidAmount> fluid, List<String> log) {
    static final TestStack EMPTY = new TestStack("minecraft:air", 0, false, Set.of(), 0f, Optional.empty(), List.of());

    static TestStack of(String item) {
        return new TestStack(item, 1, false, Set.of(), 0f, Optional.empty(), List.of());
    }

    TestStack count(int n) {
        return new TestStack(item, n, rotten, traits, temperature, fluid, log);
    }

    TestStack rotten(boolean r) {
        return new TestStack(item, count, r, traits, temperature, fluid, log);
    }

    TestStack traits(String... t) {
        return new TestStack(item, count, rotten, Set.of(t), temperature, fluid, log);
    }

    TestStack heat(float t) {
        return new TestStack(item, count, rotten, traits, t, fluid, log);
    }

    TestStack fluid(String id, int amount) {
        return new TestStack(item, count, rotten, traits, temperature, Optional.of(new FluidAmount(id, amount)), log);
    }

    TestStack logged(String entry) {
        List<String> l = new ArrayList<>(log);
        l.add(entry);
        return new TestStack(item, count, rotten, traits, temperature, fluid, List.copyOf(l));
    }

    boolean isEmpty() {
        return count == 0;
    }

    static IngredientContext<TestStack> context(TagLookup tags) {
        return new IngredientContext<>() {
            @Override
            public String itemId(TestStack stack) {
                return stack.item();
            }

            @Override
            public TagLookup tags() {
                return tags;
            }

            @Override
            public FoodHooks<TestStack> food() {
                return new FoodHooks<>() {
                    @Override
                    public boolean isRotten(TestStack stack) {
                        return stack.rotten();
                    }

                    @Override
                    public boolean hasTrait(TestStack stack, String trait) {
                        return stack.traits().contains(trait);
                    }
                };
            }

            @Override
            public HeatHooks<TestStack> heat() {
                return TestStack::temperature;
            }

            @Override
            public FluidHooks<TestStack> fluids() {
                return TestStack::fluid;
            }
        };
    }

    /** Hooks whose feature methods only log; stack basics are real. */
    static final class Hooks implements ModifierHooks<TestStack> {
        final List<TestStack> given = new ArrayList<>();
        final List<TestStack> craftingInputs;
        final DoubleSupplier random;

        Hooks(DoubleSupplier random, List<TestStack> craftingInputs) {
            this.random = random;
            this.craftingInputs = craftingInputs;
        }

        Hooks() {
            this(() -> 0.5, List.of());
        }

        @Override
        public TestStack empty() {
            return EMPTY;
        }

        @Override
        public boolean isEmpty(TestStack stack) {
            return stack.isEmpty();
        }

        @Override
        public int count(TestStack stack) {
            return stack.count();
        }

        @Override
        public TestStack withCount(TestStack stack, int count) {
            return stack.count(count);
        }

        @Override
        public TestStack copy(TestStack stack) {
            return stack;
        }

        @Override
        public TestStack create(ItemStackTemplate template) {
            return TestStack.of(template.id()).count(template.count());
        }

        @Override
        public double nextRandom() {
            return random.getAsDouble();
        }

        @Override
        public List<TestStack> craftingInputs() {
            return craftingInputs;
        }

        @Override
        public void giveToCrafter(TestStack stack) {
            given.add(stack);
        }

        @Override
        public TestStack copyFood(TestStack stack, TestStack input) {
            return stack.logged("copyFood<" + input.item());
        }

        @Override
        public TestStack copyOldestFood(TestStack stack) {
            return stack.logged("copyOldestFood");
        }

        @Override
        public TestStack resetFood(TestStack stack) {
            return stack.logged("resetFood");
        }

        @Override
        public TestStack roundCreationDate(TestStack stack) {
            return stack.logged("roundCreationDate");
        }

        @Override
        public TestStack addTrait(TestStack stack, String trait) {
            return stack.logged("addTrait " + trait);
        }

        @Override
        public TestStack removeTrait(TestStack stack, String trait) {
            return stack.logged("removeTrait " + trait);
        }

        @Override
        public TestStack applyMeal(TestStack stack, ItemStackModifier.Meal meal) {
            return stack.logged("meal " + meal.portions().size());
        }

        @Override
        public TestStack copyHeat(TestStack stack, TestStack input) {
            return stack.logged("copyHeat<" + input.item());
        }

        @Override
        public TestStack addHeat(TestStack stack, float temperature) {
            return stack.logged("addHeat " + temperature);
        }

        @Override
        public TestStack copyForgingBonus(TestStack stack, TestStack input) {
            return stack.logged("copyForgingBonus");
        }

        @Override
        public TestStack craftingRemainder(TestStack input) {
            return EMPTY.logged("craftingRemainder");
        }

        @Override
        public TestStack damageCraftingRemainder(TestStack stack, TestStack input) {
            return input.logged("damaged");
        }

        @Override
        public TestStack emptyBowl(TestStack stack, TestStack input) {
            return TestStack.of("tfc:ceramic/bowl").logged("emptyBowl");
        }

        @Override
        public TestStack flowerCutting(TestStack stack, TestStack input) {
            return stack.logged("flowerCutting");
        }

        @Override
        public TestStack addBaitToRod(TestStack stack) {
            return stack.logged("addBaitToRod");
        }

        @Override
        public TestStack addGlass(TestStack stack, TestStack input) {
            return stack.logged("addGlass");
        }

        @Override
        public TestStack addPowder(TestStack stack) {
            return stack.logged("addPowder");
        }

        @Override
        public TestStack dyeLeather(TestStack stack, String color) {
            return stack.logged("dye " + color);
        }

        @Override
        public TestStack removeDye(TestStack stack, TestStack input) {
            return input.logged("removeDye");
        }

        @Override
        public TestStack appendChanceLore(TestStack stack, float chance) {
            return stack.logged("chanceLore " + chance);
        }
    }
}
