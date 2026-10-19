/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/component/glass/GlassWorking.java @ c3b4d9536 (apply, createNewBatch, clear),
 * file: src/main/java/net/dries007/tfc/common/component/glass/GlassOperation.java @ c3b4d9536 (hasRequiredTemperature),
 * file: src/main/java/net/dries007/tfc/common/recipes/GlassworkingRecipe.java @ c3b4d9536 (matches, and the recipe lookup)
 */
package dev.tfp.core.glass;

import dev.tfp.core.craft.CraftOutput;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * The glassworking rules: how a blowpipe's state changes with each operation, when an operation may be taken, and which recipe
 * a finished blowpipe matches.
 *
 * <p>Everything here is pure and deterministic. Recipe lookup does not depend on the order of the recipe collection: when more
 * than one recipe matches, the one with the smallest id wins (TFC has no overlaps in its data, which the golden test checks).
 */
public final class GlassRules {

    private GlassRules() {}

    /**
     * Whether a blowpipe at {@code temperature} may take {@code op}. TFC requires the temperature to be strictly above the
     * operation's minimum.
     */
    public static boolean isHotEnough(GlassOperation op, float temperature) {
        return temperature > op.minTemperature();
    }

    /**
     * Appends {@code op} to the steps, as TFC's {@code GlassWorking.apply}. Returns {@code state} unchanged when there is no batch
     * or when the blowpipe already holds {@link GlassState#LIMIT} steps.
     */
    public static GlassState apply(GlassState state, GlassOperation op) {
        if (state.isEmpty() || state.steps().size() >= GlassState.LIMIT) {
            return state;
        }
        final var steps = new ArrayList<>(state.steps());
        steps.add(op);
        return new GlassState(steps, state.batch());
    }

    /**
     * Applies the powder for {@code item}, if {@code item} is a powder. A non-powder item returns {@code state} unchanged, as TFC's
     * {@code AddPowderModifier} does.
     */
    public static GlassState applyPowder(GlassState state, String item) {
        return GlassOperation.byPowder(item).map(op -> apply(state, op)).orElse(state);
    }

    /**
     * Sets a new batch and keeps the steps, as TFC's {@code GlassWorking.createNewBatch} does.
     *
     * @param batch the batch item id, not empty
     */
    public static GlassState withBatch(GlassState state, String batch) {
        if (batch == null || batch.isEmpty()) throw new IllegalArgumentException("batch must not be empty");
        return new GlassState(state.steps(), batch);
    }

    /**
     * The recipe a blowpipe state matches: its steps equal the recipe's operations in order and the recipe's batch ingredient
     * accepts its batch. Empty when the state has no batch, or no recipe matches.
     */
    public static Optional<GlassRecipe> match(Collection<GlassRecipe> recipes, GlassState state) {
        if (state.isEmpty()) {
            return Optional.empty();
        }
        final List<String> steps = state.steps().stream().map(GlassOperation::id).toList();
        return recipes.stream()
            .filter(r -> r.operations().equals(steps) && GlassBatches.accepts(r.batch(), state.batch()))
            .min(Comparator.comparing(GlassRecipe::id));
    }

    /** The result of a finished blowpipe, or empty if no recipe matches. */
    public static Optional<CraftOutput> output(Collection<GlassRecipe> recipes, GlassState state) {
        return match(recipes, state).map(GlassRecipe::result);
    }
}
