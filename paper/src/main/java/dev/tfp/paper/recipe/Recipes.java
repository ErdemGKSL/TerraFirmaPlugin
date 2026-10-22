/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.recipe;

import dev.tfp.core.recipe.output.ItemStackModifier;
import dev.tfp.core.recipe.output.ItemStackProvider;
import dev.tfp.core.registry.Registries;
import dev.tfp.core.registry.RegistrySnapshot;
import dev.tfp.core.tag.TagType;
import dev.tfp.paper.food.ItemFoodHooks;
import dev.tfp.paper.heat.ItemHeatHooks;
import dev.tfp.paper.tags.TagRules;
import dev.tfp.paper.tags.TagSnapshot;
import dev.tfp.paper.tags.Tags;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.bukkit.inventory.ItemStack;

/**
 * The recipe runtime (PAR-INFRA-06): the current {@link RecipeIndex}, ingredient contexts over real stacks and the
 * output-modifier hooks. Any thread.
 *
 * <p>The index is an immutable snapshot of one registry load and one tag-rules load. When either changes (startup,
 * {@code /tfp reload data}) the next {@link #index()} call starts one rebuild on the CPU executor and keeps serving the
 * previous index until it is published (the index carries its own registry snapshot, so a lookup is always
 * self-consistent). Nothing is built on a region thread.
 */
public final class Recipes {
    /** What an index was built from: compared by identity. */
    private record Source(RegistrySnapshot registry, TagRules rules) {
        boolean same(Source o) {
            return o != null && o.registry == registry && o.rules == rules;
        }
    }

    private record Built(Source source, RecipeIndex index) {
    }

    private final Registries registries;
    private final Tags tags;
    private final Supplier<? extends Map<TagType, ? extends Collection<String>>> vanillaIds;
    private final Executor executor;
    private final Consumer<Throwable> onBuildError;
    private final ItemModifierHooks.Services modifierServices;
    private final ItemFoodHooks food;
    private final ItemHeatHooks heat;

    private final AtomicReference<Built> current = new AtomicReference<>(new Built(null, RecipeIndex.EMPTY));
    private final AtomicReference<Source> building = new AtomicReference<>();
    private final AtomicReference<ModifierExtensions> extensions = new AtomicReference<>(ModifierExtensions.NONE);

    /**
     * @param vanillaIds every vanilla item, block and fluid id of the server (read on {@code executor} per build)
     * @param executor   where the index is built (our CPU pool)
     */
    public Recipes(Registries registries, Tags tags, Supplier<? extends Map<TagType, ? extends Collection<String>>> vanillaIds,
            Executor executor, Consumer<Throwable> onBuildError, ItemModifierHooks.Services modifierServices) {
        this.registries = Objects.requireNonNull(registries, "registries");
        this.tags = Objects.requireNonNull(tags, "tags");
        this.vanillaIds = Objects.requireNonNull(vanillaIds, "vanillaIds");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.onBuildError = Objects.requireNonNull(onBuildError, "onBuildError");
        this.modifierServices = Objects.requireNonNull(modifierServices, "modifierServices");
        this.food = modifierServices.food();
        this.heat = modifierServices.heat();
    }

    /** The current index; starts a rebuild when the data or the tag rules changed since it was built. */
    public RecipeIndex index() {
        Built b = current.get();
        Source now = source();
        if (!now.same(b.source())) {
            startBuild(now);
        }
        return b.index();
    }

    /** Builds the index for the current data now (off-thread) and publishes it; completes with it. */
    public CompletableFuture<RecipeIndex> rebuild() {
        Source s = source();
        return CompletableFuture.supplyAsync(() -> build(s), executor);
    }

    private Source source() {
        TagSnapshot t = tags.snapshot();
        return new Source(registries.get(), t.rules());
    }

    private void startBuild(Source s) {
        Source previous = building.get();
        if (s.same(previous) || !building.compareAndSet(previous, s)) {
            return;
        }
        CompletableFuture.supplyAsync(() -> build(s), executor).whenComplete((idx, err) -> {
            if (err != null) {
                building.compareAndSet(s, null);
                onBuildError.accept(err);
            }
        });
    }

    private RecipeIndex build(Source s) {
        TagSnapshot snap = tags.snapshot();
        RecipeIndex idx = RecipeIndex.build(s.registry(), new TagMembers(snap, vanillaIds.get()));
        // Publish unless a build of the live data already did and this one is of older data.
        Source live = source();
        current.updateAndGet(b -> live.same(b.source()) && !live.same(s) ? b : new Built(s, idx));
        return idx;
    }

    /** An ingredient context over real stacks for one operation (pins the current tag snapshot). */
    public ItemIngredientContext ingredients() {
        return new ItemIngredientContext(tags::itemId, tags.snapshot(), food, heat::temperature);
    }

    /** Output-modifier hooks for one operation (gameplay randomness from {@link ThreadLocalRandom}). */
    public ItemModifierHooks modifiers() {
        return new ItemModifierHooks(modifierServices, ItemIngredientContext.lookup(tags.snapshot()), extensions.get(),
                () -> ThreadLocalRandom.current().nextDouble());
    }

    /** The registered modifier extensions; owners add theirs with {@link #extend}. */
    public ModifierExtensions extensions() {
        return extensions.get();
    }

    /** Registers the handler of an extensible output modifier (one owner per kind). */
    public void extend(ModifierExtensions.Kind kind, ModifierExtensions.Handler handler) {
        extensions.updateAndGet(e -> e.with(kind, handler));
    }

    /** TFC {@code ItemStackProvider.getStack(input)}: the provider's output for {@code input}, gameplay context. */
    public ItemStack output(ItemStackProvider provider, ItemStack input) {
        return provider.getStack(input == null ? ItemStack.empty() : input, ItemStackModifier.Context.DEFAULT,
                modifiers());
    }

    /** TFC {@code ItemStackProvider.getSingleStack(input)}. */
    public ItemStack singleOutput(ItemStackProvider provider, ItemStack input) {
        return provider.getSingleStack(input == null ? ItemStack.empty() : input, modifiers());
    }

    /** The first recipe of {@code type} whose item ingredient accepts {@code stack}. */
    public Optional<RecipeEntry> first(String type, ItemStack stack) {
        return index().first(type, stack, ingredients());
    }
}
