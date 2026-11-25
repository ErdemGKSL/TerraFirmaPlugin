/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/PlacedItemBlockEntity.java @ c3b4d9536 (isHoldingLargeItem,
 *       rotations, loadAdditional/saveAdditional)
 */
package dev.tfp.core.placed;

import dev.tfp.core.persist.VersionedCodec;

/**
 * The block-entity state of one placed item block besides its four item slots (which the device framework keeps): whether
 * slot 0 holds one large item alone, and the yaw of each slot in degrees (TFC {@code rotations}, multiples of 15).
 */
public record PlacedState(boolean large, float rotation0, float rotation1, float rotation2, float rotation3) {
    /** A block just placed: four small slots, no rotation. */
    public static final PlacedState EMPTY = new PlacedState(false, 0f, 0f, 0f, 0f);

    /** TFC NBT keys {@code isHoldingLargeItem}, {@code rotation1..4}. */
    public static final VersionedCodec<PlacedState> CODEC = VersionedCodec.<PlacedState>builder("placed_item", 1)
        .build((s, out) -> out.putBoolean("large", s.large()).putFloat("rotation1", s.rotation0())
                .putFloat("rotation2", s.rotation1()).putFloat("rotation3", s.rotation2()).putFloat("rotation4", s.rotation3()),
            in -> new PlacedState(in.getBoolean("large", false), in.getFloat("rotation1", 0f), in.getFloat("rotation2", 0f),
                in.getFloat("rotation3", 0f), in.getFloat("rotation4", 0f)));

    /** TFC {@code getRotations(slot)}. */
    public float rotation(int slot) {
        return switch (slot) {
            case 0 -> rotation0;
            case 1 -> rotation1;
            case 2 -> rotation2;
            case 3 -> rotation3;
            default -> throw new IllegalArgumentException("slot must be 0..3: " + slot);
        };
    }

    /** The state with {@code slot} turned to {@code degrees}. */
    public PlacedState withRotation(int slot, float degrees) {
        return switch (slot) {
            case 0 -> new PlacedState(large, degrees, rotation1, rotation2, rotation3);
            case 1 -> new PlacedState(large, rotation0, degrees, rotation2, rotation3);
            case 2 -> new PlacedState(large, rotation0, rotation1, degrees, rotation3);
            case 3 -> new PlacedState(large, rotation0, rotation1, rotation2, degrees);
            default -> throw new IllegalArgumentException("slot must be 0..3: " + slot);
        };
    }

    /** The state with the large flag set to {@code value}. */
    public PlacedState withLarge(boolean value) {
        return new PlacedState(value, rotation0, rotation1, rotation2, rotation3);
    }
}
