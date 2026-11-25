/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/wood/TFCChestBlock.java @ c3b4d9536 (the combiner: a double chest is
 *        the first and the second chest as one 36 slot container),
 *        src/main/java/net/dries007/tfc/common/blockentities/TFCChestBlockEntity.java @ c3b4d9536 (getContainerSize 18),
 *        vanilla net.minecraft.world.level.block.ChestBlock (getStateForPlacement, candidatePartnerFacing, updateShape,
 *        getConnectedDirection, getBlockType: TFCChestBlock extends it without overriding them)
 */
package dev.tfp.core.chest;

import java.util.Objects;
import java.util.function.Function;

/**
 * PAR-DECO-04: how chests pair into a double chest. Pure rules over the neighbours' facing and type; the plugin applies the
 * results to the blocks.
 */
public final class ChestRules {
    /** TFC {@code TFCChestBlockEntity.getContainerSize}: 18 slots, two rows of nine. */
    public static final int SINGLE_SLOTS = 18;
    /** Two chests as one container ({@code CompoundContainer}), four rows. */
    public static final int DOUBLE_SLOTS = 2 * SINGLE_SLOTS;
    public static final int COLUMNS = 9;

    private ChestRules() {
    }

    /** A chest block of this kind: its facing and type. */
    public record Chest(ChestSide facing, ChestType type) {
        public Chest {
            Objects.requireNonNull(facing, "facing");
            Objects.requireNonNull(type, "type");
        }
    }

    /** Where a chest's partner is: {@code ChestBlock.getConnectedDirection}, left to the clockwise side and right to the other. */
    public static ChestSide partnerSide(ChestSide facing, ChestType type) {
        if (type == ChestType.SINGLE) {
            throw new IllegalArgumentException("a single chest has no partner");
        }
        return type == ChestType.LEFT ? facing.clockwise() : facing.counterClockwise();
    }

    /**
     * {@code ChestBlock.candidatePartnerFacing}: the facing of the single chest (of the same wood and kind) on {@code side}
     * of the new chest, or null when there is none (no chest, another kind of block, or a chest that is already half of a
     * double chest).
     */
    static ChestSide candidatePartnerFacing(Function<ChestSide, Chest> neighbours, ChestSide side) {
        Chest c = neighbours.apply(side);
        return c != null && c.type() == ChestType.SINGLE ? c.facing() : null;
    }

    /**
     * {@code ChestBlock.getStateForPlacement}: the facing and type of a chest placed by a player.
     *
     * @param looking     the direction the player looks ({@code getHorizontalDirection}); the chest faces back at them
     * @param clickedFace the clicked face, or null when it was a top or bottom face
     * @param sneaking    {@code isSecondaryUseActive}
     * @param neighbours  the chest next to the new one on a side, or null when that block is no chest of this kind
     */
    public static Chest placement(ChestSide looking, ChestSide clickedFace, boolean sneaking, Function<ChestSide, Chest> neighbours) {
        ChestSide facing = looking.opposite();
        ChestType type = ChestType.SINGLE;
        if (clickedFace != null && sneaking) {
            ChestSide d = candidatePartnerFacing(neighbours, clickedFace.opposite());
            if (d != null && !d.sameAxis(clickedFace)) {
                facing = d;
                type = facing.counterClockwise() == clickedFace.opposite() ? ChestType.RIGHT : ChestType.LEFT;
            }
        }
        if (type == ChestType.SINGLE && !sneaking) {
            if (facing == candidatePartnerFacing(neighbours, facing.clockwise())) {
                type = ChestType.LEFT;
            } else if (facing == candidatePartnerFacing(neighbours, facing.counterClockwise())) {
                type = ChestType.RIGHT;
            }
        }
        return new Chest(facing, type);
    }

    /**
     * {@code ChestBlock.updateShape}: the type a chest takes after the block on {@code side} of it changed, or {@code self}'s
     * own type when nothing changes.
     *
     * @param neighbour the block on {@code side} when it is a chest of this kind, else null
     */
    public static ChestType afterNeighbourChange(Chest self, ChestSide side, Chest neighbour) {
        if (neighbour != null) {
            if (self.type() == ChestType.SINGLE && neighbour.type() != ChestType.SINGLE && self.facing() == neighbour.facing()
                && partnerSide(neighbour.facing(), neighbour.type()) == side.opposite()) {
                return neighbour.type().opposite();
            }
            return self.type();
        }
        if (self.type() != ChestType.SINGLE && partnerSide(self.facing(), self.type()) == side) {
            return ChestType.SINGLE;
        }
        return self.type();
    }

    /**
     * {@code ChestBlock.getBlockType}: in a double chest the right half is the first container (slots 0 to 17 of the
     * 36) and the left half the second; a single chest is its own container.
     */
    public static boolean isFirst(ChestType type) {
        return type == ChestType.RIGHT;
    }
}
