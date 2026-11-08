/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/recipes/BarrelRecipe.java @ c3b4d9536 (matches, assembleOutputs),
 * src/main/java/net/dries007/tfc/common/recipes/SealedBarrelRecipe.java @ c3b4d9536 (matches with moreFluidThanItems,
 * onSealed, onUnsealed),
 * src/main/java/net/dries007/tfc/common/recipes/InstantBarrelRecipe.java @ c3b4d9536 (matches with moreItemsThanFluid),
 * src/main/java/net/dries007/tfc/common/recipes/InstantFluidBarrelRecipe.java @ c3b4d9536 (matches, assembleOutputs),
 * src/main/java/net/dries007/tfc/common/blockentities/BarrelBlockEntity.java @ c3b4d9536 (getRecipe: none with an excess;
 * serverTick: the instant recipe, then the instant fluid recipe, only with an empty excess),
 * src/main/java/net/dries007/tfc/util/Helpers.java @ c3b4d9536 (consumeInStackSizeIncrements)
 */
package dev.tfp.paper.barrel;

import dev.tfp.core.barrel.BarrelAssembly;
import dev.tfp.core.barrel.BarrelFluidTransfer;
import dev.tfp.core.barrel.BarrelSimulation;
import dev.tfp.core.recipe.fluid.SizedFluidIngredient;
import dev.tfp.core.recipe.output.ItemStackProvider;
import dev.tfp.core.tag.TagLookup;
import dev.tfp.core.tag.TagType;
import dev.tfp.core.vessel.FluidAmount;
import dev.tfp.paper.device.StackCodec;
import dev.tfp.paper.recipe.ItemIngredientContext;
import dev.tfp.paper.recipe.RecipeEntry;
import dev.tfp.paper.recipe.RecipeIndex;
import dev.tfp.paper.recipe.Recipes;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.inventory.ItemStack;

/**
 * The barrel's recipes on real stacks (PAR-PROC-08): the 309 sealed, 20 instant and 2 instant fluid recipes of the recipe
 * runtime, decoded once per recipe index, and their matching and assembly as TFC's recipe classes do it. A {@link Session}
 * pins one ingredient context for one operation. Any thread; stacks belong to the caller.
 */
public final class BarrelRecipeBook {
    /** TFC {@code TFCTags.Fluids.USABLE_IN_BARREL}. */
    public static final String USABLE_IN_BARREL = "tfc:usable_in_barrel";
    /** Instant recipes applied in a row at most (TFC applies one per tick while the contents keep changing). */
    static final int INSTANT_LIMIT = 16;

    private record Decoded(RecipeIndex index, Map<BarrelRecipeView.Kind, List<BarrelRecipeView>> views) {
    }

    private final Recipes recipes;
    private final BarrelItemTanks tanks;
    private final int capacity;
    private final AtomicReference<Decoded> decoded = new AtomicReference<>();

    public BarrelRecipeBook(Recipes recipes, BarrelItemTanks tanks, int capacity) {
        this.recipes = Objects.requireNonNull(recipes, "recipes");
        this.tanks = Objects.requireNonNull(tanks, "tanks");
        this.capacity = capacity;
    }

    public int capacity() {
        return capacity;
    }

    public BarrelItemTanks tanks() {
        return tanks;
    }

    /** The recipes of one kind, in index order. */
    public List<BarrelRecipeView> all(BarrelRecipeView.Kind kind) {
        RecipeIndex index = recipes.index();
        Decoded d = decoded.get();
        if (d == null || d.index() != index) {
            Map<BarrelRecipeView.Kind, List<BarrelRecipeView>> views = new EnumMap<>(BarrelRecipeView.Kind.class);
            for (BarrelRecipeView.Kind k : BarrelRecipeView.Kind.values()) {
                List<BarrelRecipeView> list = new ArrayList<>();
                for (RecipeEntry e : index.all(k.type())) {
                    list.add(BarrelRecipeView.of(e, k));
                }
                views.put(k, List.copyOf(list));
            }
            d = new Decoded(index, views);
            decoded.set(d);
        }
        return d.views().get(kind);
    }

    /** One operation's matching context. */
    public Session session() {
        return new Session(recipes.ingredients());
    }

    /** An instant recipe that applied, with its completion sound. */
    public record Applied(BarrelContents contents, String sound) {
    }

    /** The recipe rules over one ingredient context (one tag snapshot). */
    public final class Session implements BarrelSimulation.Rules<BarrelContents, BarrelRecipeView> {
        private final ItemIngredientContext ctx;

        Session(ItemIngredientContext ctx) {
            this.ctx = ctx;
        }

        private TagLookup tags() {
            return ctx.tags();
        }

        /** The barrel's tank validator. */
        public boolean usable(String fluid) {
            return tags().contains(TagType.FLUID, USABLE_IN_BARREL, fluid);
        }

        private boolean fluidOk(SizedFluidIngredient ingredient, FluidAmount fluid) {
            return !fluid.isEmpty()
                && ingredient.test(new dev.tfp.core.recipe.fluid.FluidAmount(fluid.fluid(), fluid.amount()), tags());
        }

        private boolean itemOk(BarrelRecipeView r, ItemStack main) {
            if (r.inputItem() == null) {
                return true;
            }
            return !StackCodec.isEmpty(main) && r.inputItem().test(main, main.getAmount(), ctx);
        }

        /** TFC {@code BarrelRecipe.matches} and the kind's own ratio rule. */
        public boolean matches(BarrelRecipeView r, BarrelContents c) {
            FluidAmount fluid = c.fluid();
            ItemStack main = c.main();
            int count = StackCodec.isEmpty(main) ? 0 : main.getAmount();
            return switch (r.kind()) {
                case SEALED -> itemOk(r, main) && fluidOk(r.inputFluid(), fluid)
                    && BarrelAssembly.moreFluidThanItems(r.infinite(), fluid.amount(), r.inputFluid().amount(), count,
                        r.inputItemCount());
                case INSTANT -> itemOk(r, main) && fluidOk(r.inputFluid(), fluid)
                    && BarrelAssembly.moreItemsThanFluid(r.outputFluid() != null, fluid.amount(), r.inputFluid().amount(),
                        count, r.inputItemCount());
                case INSTANT_FLUID -> matchesFluid(r, main, fluid) || matchesFluid(r, c.containerIn(), fluid);
            };
        }

        private boolean matchesFluid(BarrelRecipeView r, ItemStack stack, FluidAmount fluid) {
            return fluidOk(r.inputFluid(), fluid) && fluidOk(r.addedFluid(), tanks.content(stack));
        }

        /** TFC {@code getRecipe}: the first sealed recipe that matches; none while an excess is held. */
        @Override
        public BarrelRecipeView select(BarrelContents c) {
            if (!c.excess().isEmpty()) {
                return null;
            }
            for (BarrelRecipeView r : all(BarrelRecipeView.Kind.SEALED)) {
                if (matches(r, c)) {
                    return r;
                }
            }
            return null;
        }

        @Override
        public int duration(BarrelRecipeView recipe) {
            return recipe.duration();
        }

        @Override
        public BarrelContents flush(BarrelContents c) {
            return c.flushed();
        }

        /** TFC {@code BarrelRecipe.assembleOutputs} (sealed and instant recipes). */
        @Override
        public BarrelContents assemble(BarrelRecipeView r, BarrelContents c, long at) {
            if (r.kind() == BarrelRecipeView.Kind.INSTANT_FLUID) {
                return assembleFluid(r, c);
            }
            ItemStack stack = c.main();
            FluidAmount fluid = c.fluid();
            int count = StackCodec.isEmpty(stack) ? 0 : stack.getAmount();
            boolean same = r.outputFluid() != null && fluidId(r.outputFluid().id()).equals(fluid.fluid());
            int multiplier = BarrelAssembly.multiplier(fluid.amount(), r.inputFluid().amount(), count, r.inputItemCount(),
                r.outputFluidAmount(), same, capacity);
            BarrelContents out = c.withMain(ItemStack.empty()).withFluid(FluidAmount.EMPTY);
            if (r.outputItem() != null) {
                ItemStack output = recipes.singleOutput(r.outputItem(), stack);
                if (!StackCodec.isEmpty(output)) {
                    // Helpers.consumeInStackSizeIncrements: max-stack-size pieces into the main slot, then the excess
                    int total = multiplier * output.getAmount();
                    int size = Math.max(1, output.getMaxStackSize());
                    while (total > 0) {
                        ItemStack piece = output.clone();
                        piece.setAmount(Math.min(size, total));
                        total -= piece.getAmount();
                        out = out.insertWithOverflow(piece);
                    }
                }
            }
            int remaining = BarrelAssembly.remainingItems(count, r.inputItemCount(), multiplier);
            if (remaining > 0) {
                ItemStack rest = stack.clone();
                rest.setAmount(remaining);
                out = out.insertWithOverflow(rest);
            }
            if (r.outputFluid() == null) {
                int retain = BarrelAssembly.retainedFluid(fluid.amount(), r.inputFluid().amount(), multiplier);
                out = out.withFluid(retain > 0 ? new FluidAmount(fluid.fluid(), retain) : FluidAmount.EMPTY);
            } else {
                int amount = BarrelAssembly.outputFluid(fluid.amount(), r.outputFluid().amount(), multiplier, same, capacity);
                out = out.withFluid(amount > 0 ? new FluidAmount(fluidId(r.outputFluid().id()), amount) : FluidAmount.EMPTY);
            }
            return out;
        }

        /** TFC {@code InstantFluidBarrelRecipe.assembleOutputs}. */
        private BarrelContents assembleFluid(BarrelRecipeView r, BarrelContents c) {
            FluidAmount primary = c.fluid();
            boolean itemSlot = matchesFluid(r, c.main(), primary);
            ItemStack original = itemSlot ? c.main() : c.containerIn();
            Optional<BarrelFluidTransfer.ItemTank> handler = tanks.tank(original);
            BarrelContents out = c.withFluid(FluidAmount.EMPTY);
            if (handler.isEmpty()) {
                return out;
            }
            FluidAmount added = handler.get().content();
            int multiplier = BarrelAssembly.instantFluidMultiplier(primary.amount(), r.inputFluid().amount(),
                added.amount(), r.addedFluid().amount());
            int target = multiplier * r.addedFluid().amount();
            // drain exactly the target when the item allows it (a vanilla bucket gives all or nothing), else all of it
            boolean exact = target > 0 && target <= added.amount()
                && (handler.get().mode() == BarrelFluidTransfer.Mode.TANK || target == added.amount());
            int left = exact ? added.amount() - target : 0;
            FluidAmount itemAfter = left > 0 ? new FluidAmount(added.fluid(), left) : FluidAmount.EMPTY;
            if (r.outputFluid() != null) {
                int amount = BarrelAssembly.instantFluidOutput(r.outputFluid().amount(), multiplier, capacity);
                out = out.withFluid(amount > 0 ? new FluidAmount(fluidId(r.outputFluid().id()), amount) : FluidAmount.EMPTY);
            }
            // FluidHelpers.updateContainerItem
            ItemStack container = tanks.withContent(original, itemAfter);
            ItemStack newOriginal;
            ItemStack extra = ItemStack.empty();
            if (original.getAmount() == 1) {
                newOriginal = container;
            } else {
                newOriginal = original.clone();
                newOriginal.setAmount(original.getAmount() - 1);
                extra = container;
            }
            out = itemSlot ? out.withMain(newOriginal) : out.withContainers(ItemStack.empty(), newOriginal);
            return out.insertWithOverflow(extra);
        }

        /** TFC {@code SealedBarrelRecipe.onSealed}: {@code on_seal} on the whole main stack. */
        @Override
        public BarrelContents onSealed(BarrelRecipeView r, BarrelContents c, long at) {
            return apply(r.onSeal(), c);
        }

        /** TFC {@code SealedBarrelRecipe.onUnsealed}. */
        public BarrelContents onUnsealed(BarrelRecipeView r, BarrelContents c) {
            return apply(r.onUnseal(), c);
        }

        private BarrelContents apply(ItemStackProvider provider, BarrelContents c) {
            if (provider == null) {
                return c;
            }
            ItemStack stack = c.main();
            ItemStack result = recipes.output(provider, stack);
            // insertItem(SLOT_ITEM, ...) into the emptied slot: up to the max stack size, the rest is lost as in TFC
            if (!StackCodec.isEmpty(result) && result.getAmount() > Math.min(64, result.getMaxStackSize())) {
                result.setAmount(Math.min(64, result.getMaxStackSize()));
            }
            return c.withMain(result);
        }

        /** The first instant, then instant fluid, recipe that matches (TFC: only with an empty excess). */
        public Optional<BarrelRecipeView> instantRecipe(BarrelContents c) {
            if (!c.excess().isEmpty()) {
                return Optional.empty();
            }
            for (BarrelRecipeView.Kind k : List.of(BarrelRecipeView.Kind.INSTANT, BarrelRecipeView.Kind.INSTANT_FLUID)) {
                for (BarrelRecipeView r : all(k)) {
                    if (matches(r, c)) {
                        return Optional.of(r);
                    }
                }
            }
            return Optional.empty();
        }

        /** The instant recipes after a change, applied until none matches or nothing changes; the last sound. */
        public Optional<Applied> applyInstant(BarrelContents c) {
            BarrelContents current = c;
            String sound = null;
            for (int i = 0; i < INSTANT_LIMIT; i++) {
                Optional<BarrelRecipeView> r = instantRecipe(current);
                if (r.isEmpty()) {
                    break;
                }
                BarrelContents next = assemble(r.get(), current, 0);
                if (next.equals(current)) {
                    break;
                }
                current = next;
                sound = r.get().sound();
            }
            return sound == null ? Optional.empty() : Optional.of(new Applied(current, sound));
        }

        @Override
        public BarrelContents instant(BarrelContents c, long at) {
            return applyInstant(c).map(Applied::contents).orElse(c);
        }
    }

    /** A recipe's fluid id as the tank stores it (the registry's ids, {@code tfc:} renamed to {@code tfp:}). */
    static String fluidId(String id) {
        return id.startsWith("tfc:") ? "tfp:" + id.substring("tfc:".length()) : id;
    }
}
