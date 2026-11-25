/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/TripHammerBlock.java @ c3b4d9536 (FACING, getStateForPlacement),
 *       src/main/java/net/dries007/tfc/common/blockentities/rotation/TripHammerBlockEntity.java @ c3b4d9536 (getRotation: the
 *       bladed axle above; the anvil in front: an AnvilBlockEntity of an AnvilBlock, not a rock anvil)
 */
package dev.tfp.paper.triphammer;

import dev.tfp.core.rotation.MechanicalBlock;
import dev.tfp.core.world.Direction;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.rotation.RotationBlockStates;
import java.util.Locale;
import java.util.Optional;

/**
 * The trip hammer block {@code tfp:trip_hammer} ({@code facing=north|east|south|west}) and the blocks it reads: the bladed axle
 * above it and the metal anvil in front of it. Pure, any thread.
 */
public final class TripHammerBlocks {
    public static final String ID = "tfp:trip_hammer";
    public static final String FACING = "facing";
    /** TFC {@code TFCTags.Items.TRIP_HAMMERS} ({@code tfc:trip_hammers}: the metal hammers). */
    public static final String TRIP_HAMMERS = "tfp:trip_hammers";
    /** The metal anvils ({@code AnvilDevice.METAL_ID}); the rock anvil is not an {@code AnvilBlock}. */
    public static final String METAL_ANVIL = "tfp:metal/anvil";

    private TripHammerBlocks() {
    }

    /** The facing of a trip hammer state; empty for another block or a bad value. */
    public static Optional<Direction> facing(BlockStateRef state) {
        if (!state.id().equals(ID)) {
            return Optional.empty();
        }
        String v = state.properties().getOrDefault(FACING, "north");
        return switch (v) {
            case "north" -> Optional.of(Direction.NORTH);
            case "east" -> Optional.of(Direction.EAST);
            case "south" -> Optional.of(Direction.SOUTH);
            case "west" -> Optional.of(Direction.WEST);
            default -> Optional.empty();
        };
    }

    public static String name(Direction d) {
        return d.name().toLowerCase(Locale.ROOT);
    }

    /** TFC {@code getStateForPlacement}: {@code facing = context.getHorizontalDirection().getOpposite()}. */
    public static Direction placementFacing(Direction horizontalLook) {
        return horizontalLook.opposite();
    }

    /** TFC {@code level.getBlockEntity(pos.above()) instanceof BladedAxleBlockEntity}. */
    public static boolean isBladedAxle(BlockStateRef state) {
        return RotationBlockStates.block(state.id()).orElse(null) == MechanicalBlock.BLADED_AXLE;
    }

    /** TFC {@code getBlockEntity(anvilPos) instanceof AnvilBlockEntity && getBlock() instanceof AnvilBlock}: a metal anvil. */
    public static boolean isMetalAnvil(BlockStateRef state) {
        return state.id().startsWith(METAL_ANVIL + "/");
    }

    /** Vanilla {@code Direction.fromYRot}: {@code floor(yaw / 90 + 0.5) & 3} is south, west, north, east. */
    public static Direction horizontal(float yaw) {
        return switch (((int) Math.floor(yaw / 90.0 + 0.5)) & 3) {
            case 0 -> Direction.SOUTH;
            case 1 -> Direction.WEST;
            case 2 -> Direction.NORTH;
            default -> Direction.EAST;
        };
    }
}
