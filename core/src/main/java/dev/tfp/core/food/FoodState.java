/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/component/food/FoodComponent.java @ c3b4d9536
 */
package dev.tfp.core.food;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The persisted decay state of one food stack (the decay part of TFC {@code FoodComponent}): the creation
 * date (player ticks, or one of the {@link FoodDecay} flags) and the applied traits, in application order.
 * Immutable; every derived value (rotten, rotten date, stage) is computed from it and the current tick.
 *
 * <p>The food definition's {@code decay_modifier} is not stored: it comes from the food data of the item,
 * as in TFC, and is passed in by the caller.
 */
public record FoodState(long creationDate, List<FoodTrait> traits) {
    public FoodState {
        traits = List.copyOf(traits);
    }

    /** A food created at tick {@code now}, with the rounded creation date (TFC {@code new FoodComponent(parent)}). */
    public static FoodState createdAt(long now, FoodConfig config) {
        return new FoodState(FoodDecay.roundedCreationDate(now, config.stackTicks()), List.of());
    }

    /** TFC {@code IFood.getDecayDateModifier} for this food's traits. */
    public float decayDateModifier(float foodDecayModifier, FoodConfig config) {
        return FoodDecay.decayDateModifier(foodDecayModifier, config.decayModifier(), traits);
    }

    /** The stage at {@code now}. */
    public FoodStage stage(float foodDecayModifier, long now, FoodConfig config) {
        return FoodDecay.stage(creationDate, decayDateModifier(foodDecayModifier, config), now);
    }

    /** TFC {@code isRotten} at {@code now}. */
    public boolean isRotten(float foodDecayModifier, long now, FoodConfig config) {
        return FoodDecay.isRotten(creationDate, decayDateModifier(foodDecayModifier, config), now);
    }

    public boolean hasTrait(FoodTrait trait) {
        return traits.contains(trait);
    }

    /**
     * TFC {@code FoodComponent.sanitize}: a dated food that is rotten at {@code now} gets the
     * {@link FoodDecay#ROTTEN_FLAG}, which it keeps. Flags and fresh foods are returned unchanged.
     */
    public FoodState sanitize(float foodDecayModifier, long now, FoodConfig config) {
        if (creationDate >= 0 && isRotten(foodDecayModifier, now, config)) {
            return new FoodState(FoodDecay.ROTTEN_FLAG, traits);
        }
        return this;
    }

    /**
     * TFC {@code FoodCapability.applyTrait}: adds {@code trait} and moves the creation date so the remaining
     * decay fraction is kept. No change when the trait is present or the food is rotten.
     */
    public FoodState withTraitApplied(FoodTrait trait, float foodDecayModifier, long now, FoodConfig config) {
        FoodState s = sanitize(foodDecayModifier, now, config);
        if (s.hasTrait(trait) || s.isRotten(foodDecayModifier, now, config)) {
            return s;
        }
        List<FoodTrait> next = new ArrayList<>(s.traits);
        next.add(trait);
        long date = FoodDecay.newCreationDate(s.creationDate, 1f / trait.decayModifier(), now, config.stackTicks());
        return new FoodState(date, next);
    }

    /**
     * TFC {@code FoodCapability.removeTrait}: removes {@code trait} and moves the creation date to keep the
     * remaining decay fraction. Applies to rotten food too (TFC: rotting food can still be preserved).
     */
    public FoodState withTraitRemoved(FoodTrait trait, float foodDecayModifier, long now, FoodConfig config) {
        FoodState s = sanitize(foodDecayModifier, now, config);
        if (!s.hasTrait(trait)) {
            return s;
        }
        List<FoodTrait> next = new ArrayList<>(s.traits);
        next.remove(trait);
        long date = FoodDecay.newCreationDate(s.creationDate, trait.decayModifier(), now, config.stackTicks());
        return new FoodState(date, next);
    }

    /**
     * The food-component part of TFC's stack check ({@code ItemStackHooks}): both states sanitized at
     * {@code now}, the same traits in the same order, and creation dates that
     * {@linkplain FoodDecay#creationDatesStack stack}. The caller also requires the same item and food data.
     */
    public boolean stacksWith(FoodState other, float foodDecayModifier, long now, FoodConfig config) {
        FoodState a = sanitize(foodDecayModifier, now, config);
        FoodState b = other.sanitize(foodDecayModifier, now, config);
        return a.traits.equals(b.traits) && FoodDecay.creationDatesStack(a.creationDate, b.creationDate, config.stackTicks());
    }

    /** TFC {@code FoodCapability.mergeItemStacks}: the merged stack keeps the earlier creation date. */
    public FoodState mergedWith(FoodState other) {
        Objects.requireNonNull(other, "other");
        return new FoodState(FoodDecay.mergedCreationDate(creationDate, other.creationDate), traits);
    }
}
