/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/recipes/LandslideRecipe.java @ c3b4d9536 (getLandslidePos, isSupportedOnSide, tryLandslide),
 * file: src/main/java/net/dries007/tfc/common/entities/misc/TFCFallingBlockEntity.java @ c3b4d9536 (canFallThrough)
 */
package dev.tfp.core.landslide;

import dev.tfp.core.collapse.CollapseCell;
import dev.tfp.core.collapse.CollapseKinds;
import dev.tfp.core.collapse.CollapseView;
import dev.tfp.core.collapse.SupportTable;
import dev.tfp.core.world.BlockPos;
import dev.tfp.core.world.Direction;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The pure landslide rules. A block whose support is gone falls straight down when the block below is open, otherwise it slides into
 * one open side, chosen by {@link LandslideRandom} among the open sides. {@link #decide} gives the decision from a snapshot;
 * {@link #step} gives what the platform must write for it. Nothing here writes blocks or spawns entities.
 *
 * <p>TFC has no slope threshold and no slide chance: a block either passes the support and open-side tests or it stays. The only
 * random draw is the choice of side. The sides are tried in the order north, east, south, west, which is the order of vanilla's
 * {@code Direction.Plane.HORIZONTAL}.
 *
 */
public final class LandslideRules {
    /** The air kind. TFC's {@code FluidHelpers.isAirOrEmptyFluid} is checked by the adapter, which reports an empty fluid as air. */
    public static final String AIR = "minecraft:air";

    private static final Direction[] HORIZONTAL = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

    /**
     * One landslide step. {@code from} is the block that moves and {@code to} its new position; for a fall both are the same position.
     * {@code clearFrom} removes the block at {@code from} first (a slide), and {@code destroyTo} breaks the block at {@code to} first when
     * it is not air. {@code result} is the kind the block becomes; the platform starts a falling block at {@code to}.
     */
    public record LandslideStep(BlockPos from, BlockPos to, String result, boolean clearFrom, boolean destroyTo) {}

    private final LandslideKinds kinds;
    private final SupportTable supports;
    private final CollapseKinds blocks;

    public LandslideRules(LandslideKinds kinds, SupportTable supports, CollapseKinds blocks) {
        this.kinds = Objects.requireNonNull(kinds, "kinds");
        this.supports = Objects.requireNonNull(supports, "supports");
        this.blocks = Objects.requireNonNull(blocks, "blocks");
    }

    /** TFC {@code LandslideRecipe.canLandslide}. */
    public boolean canLandslide(String kind) {
        return kinds.canLandslide(kind);
    }

    /** TFC {@code Support.isSupported}: some support beam within reach holds the block at {@code pos} up. */
    public boolean isSupported(LandslideView view, BlockPos pos) {
        CollapseView support = p -> new CollapseCell(view.cellAt(p).kind(), false, false);
        return supports.isSupported(support, pos);
    }

    /**
     * TFC {@code isSupportedOnSide}: the neighbour in {@code side} is sturdy on the face toward the block, or is a
     * {@code supports_landslide} kind.
     */
    public boolean sideSupported(LandslideView view, BlockPos pos, Direction side) {
        LandslideCell neighbour = view.cellAt(pos.offset(side));
        return neighbour.sturdy(side.opposite()) || kinds.supportsLandslide(neighbour.kind());
    }

    /**
     * TFC {@code TFCFallingBlockEntity.canFallThrough} with a falling block of kind {@code fallingKind} moving in
     * {@code fallingDirection} into the block at {@code pos}: the block there is not sturdy on the face toward the fall, the falling
     * block is at least as tough, and the block can be broken.
     */
    public boolean canFallThrough(LandslideView view, BlockPos pos, Direction fallingDirection, String fallingKind) {
        LandslideCell state = view.cellAt(pos);
        return !state.sturdy(fallingDirection.opposite())
            && blocks.toughness(fallingKind) >= blocks.toughness(state.kind())
            && !blocks.unbreakable(state.kind());
    }

    /**
     * TFC {@code getLandslidePos} for the block at {@code pos}. A supported block stays. A block over an open block falls. Otherwise it
     * needs at most one sturdy or supporting side, and slides into one open side whose block below is also open. A kind with no
     * landslide recipe still gets a decision here, as in TFC; {@link #step} then gives nothing for it.
     */
    public LandslideDecision decide(LandslideView view, BlockPos pos, LandslideRandom random) {
        String falling = view.cellAt(pos).kind();
        if (isSupported(view, pos)) {
            return new LandslideDecision.None();
        }
        if (canFallThrough(view, pos.offset(Direction.DOWN), Direction.DOWN, falling)) {
            return new LandslideDecision.Fall();
        }
        if (sideSupported(view, pos, Direction.UP)) {
            return new LandslideDecision.None();
        }
        int supportedSides = 0;
        List<Direction> open = new ArrayList<>();
        for (Direction side : HORIZONTAL) {
            if (sideSupported(view, pos, side)) {
                supportedSides++;
                if (supportedSides >= 2) {
                    return new LandslideDecision.None();
                }
            } else {
                BlockPos beside = pos.offset(side);
                if (canFallThrough(view, beside, side, falling) && canFallThrough(view, beside.offset(Direction.DOWN), Direction.DOWN, falling)) {
                    open.add(side);
                }
            }
        }
        if (open.isEmpty()) {
            return new LandslideDecision.None();
        }
        return new LandslideDecision.Slide(open.get(random.nextInt(open.size())));
    }

    /**
     * The landslide effects of {@code decision} on the block at {@code pos}, as TFC's {@code tryLandslide} writes them. Empty when there is
     * nothing to do or the kind has no landslide recipe.
     */
    public Optional<LandslideStep> step(LandslideView view, BlockPos pos, LandslideDecision decision) {
        String kind = view.cellAt(pos).kind();
        if (decision instanceof LandslideDecision.None || !kinds.canLandslide(kind)) {
            return Optional.empty();
        }
        String result = kinds.result(kind);
        if (decision instanceof LandslideDecision.Fall) {
            return Optional.of(new LandslideStep(pos, pos, result, false, false));
        }
        LandslideDecision.Slide slide = (LandslideDecision.Slide) decision;
        BlockPos to = pos.offset(slide.side());
        boolean destroyTo = !AIR.equals(view.cellAt(to).kind());
        return Optional.of(new LandslideStep(pos, to, result, true, destroyTo));
    }
}
