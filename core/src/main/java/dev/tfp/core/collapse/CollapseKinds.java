/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/recipes/CollapseRecipe.java @ c3b4d9536 (the tag checks and the recipe lookup),
 * with the tags can_collapse, can_start_collapse, can_trigger_collapse and not_solid_supporting, the toughness tags of
 * common/entities/misc/TFCFallingBlockEntity.java (getBlockToughness) and the collapse recipes data/tfc/recipe/collapse/
 */
package dev.tfp.core.collapse;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The block-kind data behind the collapse rules: the tag memberships, toughness, unbreakable kinds and the collapse recipes
 * (ingredient kind to result kind). Filled by the platform adapter from the tags and recipes. Immutable after {@link Builder#build}.
 */
public final class CollapseKinds {
    /** Vanilla bedrock. TFC gives it a fixed toughness of 4 and it cannot be broken. */
    public static final String BEDROCK = "minecraft:bedrock";

    private final Set<String> canStart;
    private final Set<String> canTrigger;
    private final Set<String> notSolidSupporting;
    private final Set<String> canCollapse;
    private final Set<String> unbreakable;
    private final Map<String, Integer> toughness;
    private final Map<String, String> recipes;

    private CollapseKinds(Builder b) {
        this.canStart = Set.copyOf(b.canStart);
        this.canTrigger = Set.copyOf(b.canTrigger);
        this.notSolidSupporting = Set.copyOf(b.notSolidSupporting);
        this.canCollapse = Set.copyOf(b.canCollapse);
        this.unbreakable = Set.copyOf(b.unbreakable);
        this.toughness = Map.copyOf(b.toughness);
        this.recipes = Map.copyOf(b.recipes);
    }

    public static Builder builder() {
        return new Builder();
    }

    /** {@code can_start_collapse}: a block that can be the locus of a collapse. */
    public boolean canStart(String kind) {
        return canStart.contains(kind);
    }

    /** {@code can_trigger_collapse}: mining this block may trigger a collapse. */
    public boolean canTrigger(String kind) {
        return canTrigger.contains(kind);
    }

    /** {@code not_solid_supporting}: a block a collapse may start above even when it is a full block. */
    public boolean notSolidSupporting(String kind) {
        return notSolidSupporting.contains(kind);
    }

    /** Destroy speed of -1 in TFC terms: the block cannot be broken, so a falling block cannot pass through it. */
    public boolean unbreakable(String kind) {
        return unbreakable.contains(kind);
    }

    /** TFC {@code getBlockToughness}: bedrock is 4 (fixed), otherwise the toughness tag value, default 0. */
    public int toughness(String kind) {
        if (BEDROCK.equals(kind)) {
            return 4;
        }
        return toughness.getOrDefault(kind, 0);
    }

    /**
     * The rubble yield of one collapsed block: the recipe's result when the kind has a recipe, otherwise the kind itself when it is
     * in {@code can_collapse} (the fallback recipe keeps the block), otherwise empty (the block does not collapse).
     */
    public Optional<String> rubble(String kind) {
        String result = recipes.get(kind);
        if (result != null) {
            return Optional.of(result);
        }
        return canCollapse.contains(kind) ? Optional.of(kind) : Optional.empty();
    }

    public static final class Builder {
        private final Set<String> canStart = new HashSet<>();
        private final Set<String> canTrigger = new HashSet<>();
        private final Set<String> notSolidSupporting = new HashSet<>();
        private final Set<String> canCollapse = new HashSet<>();
        private final Set<String> unbreakable = new HashSet<>(Set.of(BEDROCK));
        private final Map<String, Integer> toughness = new HashMap<>();
        private final Map<String, String> recipes = new HashMap<>();

        private Builder() {}

        public Builder canStart(String... kinds) {
            canStart.addAll(List.of(kinds));
            return this;
        }

        public Builder canTrigger(String... kinds) {
            canTrigger.addAll(List.of(kinds));
            return this;
        }

        public Builder notSolidSupporting(String... kinds) {
            notSolidSupporting.addAll(List.of(kinds));
            return this;
        }

        /** Kinds in the {@code can_collapse} tag. They collapse into themselves unless a recipe says otherwise. */
        public Builder canCollapse(String... kinds) {
            canCollapse.addAll(List.of(kinds));
            return this;
        }

        public Builder unbreakable(String... kinds) {
            unbreakable.addAll(List.of(kinds));
            return this;
        }

        public Builder toughness(String kind, int value) {
            toughness.put(kind, value);
            return this;
        }

        /** A collapse recipe: each ingredient collapses into {@code result}. A kind may appear in one recipe only. */
        public Builder recipe(String result, String... ingredients) {
            for (String ingredient : ingredients) {
                if (recipes.putIfAbsent(ingredient, result) != null) {
                    throw new IllegalArgumentException("Two collapse recipes for " + ingredient);
                }
            }
            return this;
        }

        public CollapseKinds build() {
            return new CollapseKinds(this);
        }
    }
}
