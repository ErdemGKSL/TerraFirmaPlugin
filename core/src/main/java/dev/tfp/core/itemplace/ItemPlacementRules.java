/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/InteractionManager.java @ c3b4d9536 (groundcover BlockItemPlacement, Items.SNOW),
 *       src/main/java/net/dries007/tfc/util/BlockItemPlacement.java @ c3b4d9536 (canPlace),
 *       src/main/java/net/dries007/tfc/common/blocks/GroundcoverBlock.java @ c3b4d9536 (canSurvive, getStateForPlacement),
 *       src/main/java/net/dries007/tfc/common/blocks/SnowPileBlock.java @ c3b4d9536 (canPlaceSnowPile)
 */
package dev.tfp.core.itemplace;

import dev.tfp.core.gather.GroundcoverType;
import java.util.Locale;
import java.util.Optional;

/**
 * The decisions of TFC's item-use placements: a vanilla item (stick, bone, feather, flint, rotten flesh) or TFC salt places the
 * matching groundcover block, and a snow item used on a snow-pileable block places a snow pile. Pure: the caller snapshots the
 * clicked face and its neighbours.
 */
public final class ItemPlacementRules {
    /** Snow layers of a full vanilla snow block stack: the pile holds at most this many. */
    public static final int MAX_PILE_LAYERS = 8;
    public static final String SNOW_ITEM = "minecraft:snow";

    private ItemPlacementRules() {}

    /** What stands at the position a block would be placed at. */
    public record Spot(boolean replaceable, boolean waterSource) {}

    /** A groundcover block to place: the CraftEngine block id and whether it is waterlogged (the target holds a water source). */
    public record Groundcover(GroundcoverType type, String blockId, boolean waterlogged) {}

    /** The groundcover type a held item places, empty for every other item. {@code tfp:} and {@code tfc:} ids are equal. */
    public static Optional<GroundcoverType> typeOf(String itemId) {
        String id = itemId.replaceFirst("^tfc:", "tfp:");
        for (GroundcoverType type : GroundcoverType.values()) {
            String own = type.itemId().replaceFirst("^tfc:", "tfp:");
            if (own.equals(id) && isVanillaItemPlacement(type)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }

    /** TFC registers {@code BlockItemPlacement} only for types with a vanilla item (or TFC salt), not for types with their own block item. */
    private static boolean isVanillaItemPlacement(GroundcoverType type) {
        return type.itemId().startsWith("minecraft:") || type == GroundcoverType.SALT_LICK;
    }

    /** The CraftEngine block id of a groundcover type. */
    public static String blockId(GroundcoverType type) {
        return "tfp:groundcover/" + type.name().toLowerCase(Locale.ROOT);
    }

    /**
     * {@code BlockItemPlacement.place}: the target must be replaceable ({@code BlockPlaceContext.canPlace}) and the block below must
     * have a sturdy top face ({@code GroundcoverBlock.canSurvive}). A water source in the target waterlogs the block.
     * Groundcover has no collision, so entities never obstruct it.
     */
    public static Optional<Groundcover> placeGroundcover(String itemId, Spot target, boolean belowSturdy) {
        Optional<GroundcoverType> type = typeOf(itemId);
        if (type.isEmpty() || !target.replaceable() || !belowSturdy) {
            return Optional.empty();
        }
        return Optional.of(new Groundcover(type.get(), blockId(type.get()), target.waterSource()));
    }

    /** What a snow item does on the clicked block. */
    public sealed interface SnowAction {
        /** Replace the clicked block with a snow pile that remembers it ({@code SnowPileBlock.placeSnowPile}). */
        record Pile() implements SnowAction {}

        /** Add one layer to the clicked snow pile of fewer than 8 layers (vanilla layering through the block's replacement). */
        record AddLayer(int layers) implements SnowAction {}

        /** Nothing special: vanilla places or stacks the snow item itself. */
        record Defer() implements SnowAction {}
    }

    /**
     * {@code InteractionManager} snow item: {@code SnowPileBlock.canPlaceSnowPile} (the clicked block is in
     * {@code tfc:can_be_snow_piled} and a snow pile can survive there, i.e. a sturdy top face below) places a pile; a clicked snow
     * pile below 8 layers gains one; anything else is vanilla.
     *
     * @param clickedSnowPileLayers layers of the clicked block when it is a snow pile, else 0
     */
    public static SnowAction snow(boolean clickedSnowPileable, boolean belowSturdy, int clickedSnowPileLayers) {
        if (clickedSnowPileLayers > 0) {
            return clickedSnowPileLayers < MAX_PILE_LAYERS ? new SnowAction.AddLayer(clickedSnowPileLayers + 1) : new SnowAction.Defer();
        }
        return clickedSnowPileable && belowSturdy ? new SnowAction.Pile() : new SnowAction.Defer();
    }
}
