/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.landslide;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The block-kind data behind the landslide rules: the {@code can_landslide} fallback tag, the {@code supports_landslide} tag and
 * the landslide recipes (ingredient kind to result kind). Toughness and unbreakable kinds are shared with collapse
 * ({@link dev.tfp.core.collapse.CollapseKinds}). Immutable after {@link Builder#build}.
 */
public final class LandslideKinds {
    private final Set<String> canLandslide;
    private final Set<String> supportsLandslide;
    private final Map<String, String> recipes;

    private LandslideKinds(Builder b) {
        this.canLandslide = Set.copyOf(b.canLandslide);
        this.supportsLandslide = Set.copyOf(b.supportsLandslide);
        this.recipes = Map.copyOf(b.recipes);
    }

    public static Builder builder() {
        return new Builder();
    }

    /** TFC {@code LandslideRecipe.canLandslide}: the kind has a landslide recipe, or is in {@code can_landslide}. */
    public boolean canLandslide(String kind) {
        return recipes.containsKey(kind) || canLandslide.contains(kind);
    }

    /**
     * The kind a slide leaves behind: the recipe's result when the kind has a recipe, otherwise the kind itself (the fallback recipe
     * has no output, so TFC keeps the block).
     */
    public String result(String kind) {
        return recipes.getOrDefault(kind, kind);
    }

    /** A side block of this kind holds up a landslide even when it is not sturdy on the face toward the block. */
    public boolean supportsLandslide(String kind) {
        return supportsLandslide.contains(kind);
    }

    public static final class Builder {
        private final Set<String> canLandslide = new HashSet<>();
        private final Set<String> supportsLandslide = new HashSet<>();
        private final Map<String, String> recipes = new HashMap<>();

        private Builder() {}

        /** Kinds in the {@code can_landslide} tag. */
        public Builder canLandslide(String... kinds) {
            canLandslide.addAll(List.of(kinds));
            return this;
        }

        /** Kinds in the {@code supports_landslide} tag. */
        public Builder supportsLandslide(String... kinds) {
            supportsLandslide.addAll(List.of(kinds));
            return this;
        }

        /** A landslide recipe: each ingredient slides and leaves {@code result}. A kind may appear in one recipe only. */
        public Builder slides(String result, String... ingredients) {
            for (String ingredient : ingredients) {
                if (recipes.putIfAbsent(ingredient, result) != null) {
                    throw new IllegalArgumentException("Two landslide recipes for " + ingredient);
                }
            }
            return this;
        }

        public LandslideKinds build() {
            return new LandslideKinds(this);
        }
    }
}
