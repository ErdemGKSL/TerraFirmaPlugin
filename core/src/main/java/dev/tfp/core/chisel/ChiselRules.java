/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/recipes/ChiselRecipe.java (computeResult, getRecipe),
 * src/main/java/net/dries007/tfc/common/items/ChiselItem.java (useOn),
 * src/main/java/net/dries007/tfc/common/player/ChiselMode.java (SLAB placement) @ c3b4d9536
 */
package dev.tfp.core.chisel;

import java.util.List;
import java.util.Optional;

/**
 * The chisel decisions: which recipe a use performs, whether the use places anything, and which half a slab takes. Pure and
 * thread-safe.
 *
 * <p>TFC's rules, all checked against the pinned commit:
 * <ul>
 *   <li>A use needs a chisel in the main hand and a hammer in the off hand. Without both the use passes.</li>
 *   <li>The first recipe, in lookup order, that matches the block and mode is used. No two shipped recipes share a block and
 *       mode (see the golden test).</li>
 *   <li>With no matching recipe the use passes. When the recipe is found but the mode cannot place its state, the use fails.</li>
 *   <li>A performed use places the recipe's result, gives its extra item (if any), damages the chisel by one, and starts a
 *       cooldown of {@link #COOLDOWN_TICKS} ticks. The platform does these steps; this class reports the numbers.</li>
 * </ul>
 *
 * <p>Not here: the collapse check (a platform world query on {@code CAN_TRIGGER_COLLAPSE} blocks), the stair and fluid placement
 * that TFC's modes compute from world state, and the sounds and advancements.
 */
public final class ChiselRules {
    /** Damage the chisel takes per performed use. TFC's {@code Helpers.damageItem} default. */
    public static final int DAMAGE_PER_USE = 1;

    /** Cooldown after a performed use, in ticks. */
    public static final int COOLDOWN_TICKS = 10;

    private ChiselRules() {}

    /** Which half of a block a slab takes. */
    public enum SlabHalf {
        BOTTOM,
        TOP
    }

    /** The face of the target block the player hit. */
    public enum HitFace {
        DOWN,
        UP,
        NORTH,
        SOUTH,
        WEST,
        EAST
    }

    /** The outcome of a chisel use, before the platform applies it. */
    public enum Status {
        /** Not holding a chisel and a hammer, or the use is not chiseling. The use passes. */
        NOT_CHISELING,
        /** No recipe matches the block and mode. The use passes. */
        NO_RECIPE,
        /** A recipe matched, but the mode could not place its state. The use fails. */
        CANNOT_PLACE,
        /** The recipe is performed: place its result and apply the costs. */
        CHISELED
    }

    /**
     * The result of a use.
     *
     * @param status the outcome
     * @param recipe the matched recipe, or {@code null} unless the status is {@link Status#CHISELED}
     */
    public record Use(Status status, ChiselRecipe recipe) {
        public Use {
            if (status == null) {
                throw new IllegalArgumentException("status must not be null");
            }
            if (status == Status.CHISELED && recipe == null) {
                throw new IllegalArgumentException("a CHISELED use needs its recipe");
            }
        }
    }

    /**
     * TFC {@code ChiselRecipe.getRecipe}: the first recipe, in the given order, that matches the block and mode.
     *
     * @param recipes the chisel recipes, in lookup order
     * @param block   the block id of the target
     * @param mode    the player's chisel mode
     */
    public static Optional<ChiselRecipe> find(List<ChiselRecipe> recipes, String block, ChiselMode mode) {
        for (ChiselRecipe recipe : recipes) {
            if (recipe.matches(block, mode)) {
                return Optional.of(recipe);
            }
        }
        return Optional.empty();
    }

    /**
     * TFC {@code ChiselRecipe.computeResult} with its {@code ChiselItem} inputs. The caller reads the held items and passes the
     * flags.
     *
     * @param recipes            the chisel recipes, in lookup order
     * @param block              the block id of the target
     * @param mode               the player's chisel mode
     * @param holdingChisel      whether the main hand holds a chisel
     * @param holdingHammer      whether the off hand holds a hammer
     * @param placementPossible  whether the mode's placement succeeds for the matched recipe (TFC's {@code modifyStateForPlacement}
     *                           returns non-null). Only consulted when a recipe matches.
     */
    public static Use use(List<ChiselRecipe> recipes, String block, ChiselMode mode, boolean holdingChisel, boolean holdingHammer,
                          boolean placementPossible) {
        if (!holdingChisel || !holdingHammer) {
            return new Use(Status.NOT_CHISELING, null);
        }
        Optional<ChiselRecipe> recipe = find(recipes, block, mode);
        if (recipe.isEmpty()) {
            return new Use(Status.NO_RECIPE, null);
        }
        if (!placementPossible) {
            return new Use(Status.CANNOT_PLACE, null);
        }
        return new Use(Status.CHISELED, recipe.get());
    }

    /**
     * TFC {@code SLAB} placement: the half a slab takes for a hit on the given face. A hit on the bottom face takes the top half, a
     * hit on the top face takes the bottom half, and a side hit takes the top half when the hit is above the middle of the block.
     *
     * @param face        the face that was hit
     * @param hitYOffset  the hit's height within the target block, in {@code [0, 1]}
     */
    public static SlabHalf slabHalf(HitFace face, double hitYOffset) {
        if (face == HitFace.DOWN) {
            return SlabHalf.TOP;
        }
        if (face == HitFace.UP) {
            return SlabHalf.BOTTOM;
        }
        return hitYOffset > 0.5 ? SlabHalf.TOP : SlabHalf.BOTTOM;
    }
}
