/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/AnvilBlock.java @ c3b4d9536 (getStateForPlacement: the
 * horizontal direction turned clockwise; SHAPE_X / SHAPE_Z),
 * file: src/main/java/net/dries007/tfc/common/blocks/rock/RockConvertableToAnvilBlock.java @ c3b4d9536 (useItemOn: the
 * conversion's conditions)
 */
package dev.tfp.core.anvil;

import java.util.List;

/**
 * Where a metal anvil faces after it is placed, and when a raw igneous rock turns into a rock anvil (PAR-METAL-03). Pure; any
 * thread.
 */
public final class AnvilPlacement {
    /** The four facings in vanilla's 2D data order (south 0, west 1, north 2, east 3). */
    public static final List<String> FACINGS_2D = List.of("south", "west", "north", "east");
    /** The igneous rocks (TFC {@code RockCategory.IGNEOUS_EXTRUSIVE / IGNEOUS_INTRUSIVE}), which have a rock anvil. */
    public static final List<String> IGNEOUS = List.of("granite", "diorite", "gabbro", "rhyolite", "basalt", "andesite", "dacite");

    private AnvilPlacement() {
    }

    /**
     * Vanilla {@code Direction.fromYRot}: the horizontal direction a player with {@code yaw} degrees looks in, as a name.
     */
    public static String horizontalDirection(double yaw) {
        return FACINGS_2D.get(Math.floorMod((int) Math.floor(yaw / 90.0 + 0.5), 4));
    }

    /** TFC {@code getStateForPlacement}: {@code context.getHorizontalDirection().getClockWise()}. */
    public static String facing(double yaw) {
        return clockWise(horizontalDirection(yaw));
    }

    /** {@code Direction.getClockWise()} of a horizontal direction seen from above: north east south west north. */
    public static String clockWise(String direction) {
        return switch (direction) {
            case "north" -> "east";
            case "east" -> "south";
            case "south" -> "west";
            case "west" -> "north";
            default -> throw new IllegalArgumentException("not a horizontal direction: " + direction);
        };
    }

    /** TFC {@code AnvilBlock.SHAPE_X} for east and west, {@code SHAPE_Z} for north and south. */
    public static String axis(String facing) {
        return facing.equals("east") || facing.equals("west") ? "x" : "z";
    }

    /**
     * TFC {@code RockConvertableToAnvilBlock.useItemOn}: a hammer in the used hand, no chisel in the other, the top face
     * clicked and air above.
     */
    public static boolean convertsToAnvil(boolean hammerInHand, boolean chiselInOtherHand, boolean topFace, boolean airAbove) {
        return hammerInHand && !chiselInOtherHand && topFace && airAbove;
    }
}
