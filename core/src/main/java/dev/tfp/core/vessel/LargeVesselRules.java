/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/LargeVesselBlockEntity.java @ c3b4d9536 (SLOTS, isItemValid,
 *       canModify, onSeal, onUnseal), src/main/java/net/dries007/tfc/common/blocks/LargeVesselBlock.java @ c3b4d9536 (toggleSeal),
 *       src/main/java/net/dries007/tfc/common/component/food/FoodCapability.java @ c3b4d9536 (applyTrait, removeTrait)
 */
package dev.tfp.core.vessel;

import dev.tfp.core.food.FoodConfig;
import dev.tfp.core.food.FoodState;
import dev.tfp.core.food.FoodTrait;
import dev.tfp.core.item.ContainerRules;
import dev.tfp.core.item.ItemSizing;

/**
 * The pure rules of a large vessel. A large vessel holds {@link #SLOTS} stacks of items, and it has no fluid and no heat.
 * Sealing is instant in TFC: it toggles the sealed block state and applies the {@code preserved} food trait to each stack
 * (unsealing removes it). Nothing takes time, so there is no seal progress to evaluate. The preservation itself is lazy:
 * the food's creation date moves once, at the seal tick, and its decay is then read from that date at any tick.
 */
public final class LargeVesselRules {
    /** {@code LargeVesselBlockEntity.SLOTS}. */
    public static final int SLOTS = 9;

    private LargeVesselRules() {
    }

    /**
     * {@code LargeVesselBlockEntity.VesselInventory.isItemValid}: an item goes in only while unsealed, and only when it is
     * strictly smaller than {@code LARGE}.
     */
    public static boolean acceptsItem(boolean sealed, ItemSizing item) {
        return !sealed && ContainerRules.largeVessel(item);
    }

    /** {@code VesselInventory.extractItem}: items come out only while unsealed. */
    public static boolean canExtract(boolean sealed) {
        return !sealed;
    }

    /**
     * {@code LargeVesselBlockEntity.onSeal} and {@code onUnseal}, for one stack. Sealing applies {@code preserved} and
     * unsealing removes it, each moving the creation date to keep the remaining decay fraction at {@code now}.
     *
     * @param food               the food in the slot
     * @param sealing           {@code true} to seal, {@code false} to unseal
     * @param foodDecayModifier  the food definition's {@code decay_modifier}
     * @param now                the calendar tick of the change
     * @param config             the food config
     */
    public static FoodState sealFood(FoodState food, boolean sealing, float foodDecayModifier, long now, FoodConfig config) {
        return sealing
            ? food.withTraitApplied(FoodTrait.PRESERVED, foodDecayModifier, now, config)
            : food.withTraitRemoved(FoodTrait.PRESERVED, foodDecayModifier, now, config);
    }
}
