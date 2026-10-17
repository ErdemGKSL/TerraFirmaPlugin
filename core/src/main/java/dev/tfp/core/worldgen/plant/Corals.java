/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/feature/coral/CoralHelpers.java @ c3b4d9536 (placeCoralBlock), with
 * TFCCoralTreeFeature, TFCCoralMushroomFeature and TFCCoralClawFeature in the same package, which run Minecraft 1.21's
 * CoralFeature.place, CoralTreeFeature, CoralMushroomFeature and CoralClawFeature.placeFeature (followed here from the 1.21.11
 * bytecode, with Util.shuffle, Util.getRandom and Direction.Plane), and the tags
 * src/generated/resources/data/tfc/tags/block/{salt_water_corals,salt_water_coral_plants,salt_water_wall_corals}.json and
 * vanilla's minecraft:coral_blocks
 */
package dev.tfp.core.worldgen.plant;

import dev.tfp.core.worldgen.random.XoroshiroRandomSource;
import java.util.ArrayList;
import java.util.List;

/**
 * TFC's corals: vanilla's coral tree, mushroom and claw shapes of one random vanilla coral block, where each block goes only
 * into salt water (or onto a coral) with salt water above, and brings a coral, fan or sea pickle on top and wall fans on its
 * sides. Pure; the draws are TFC's and vanilla's, in their order.
 */
public final class Corals {
    /** The coral feature shapes. */
    public enum Shape { TREE, MUSHROOM, CLAW }

    /** {@code minecraft:coral_blocks}, in tag order. */
    static final List<String> CORAL_BLOCKS = List.of("minecraft:tube_coral_block", "minecraft:brain_coral_block",
        "minecraft:bubble_coral_block", "minecraft:fire_coral_block", "minecraft:horn_coral_block");
    private static final List<String> COLOURS = List.of("tube", "brain", "bubble", "fire", "horn");

    /** {@code Direction.Plane.HORIZONTAL}: north, east, south, west, as x and z steps. */
    private static final int[] DX = {0, 1, 0, -1};
    private static final int[] DZ = {-1, 0, 1, 0};
    private static final int UP = 4;

    private Corals() {}

    /** {@code CoralFeature.place}: a coral block from {@code minecraft:coral_blocks}, then the shape. */
    static boolean place(PlantLevel level, XoroshiroRandomSource random, int x, int y, int z, Shape shape) {
        final String block = CORAL_BLOCKS.get(random.nextInt(CORAL_BLOCKS.size()));
        final PlantState state = PlantState.of(block, PlantState.Kind.CORAL_BLOCK, null);
        return switch (shape) {
            case TREE -> tree(level, random, x, y, z, state);
            case MUSHROOM -> mushroom(level, random, x, y, z, state);
            case CLAW -> claw(level, random, x, y, z, state);
        };
    }

    /** {@code CoralTreeFeature.placeFeature}. */
    private static boolean tree(PlantLevel level, XoroshiroRandomSource random, int x, int y, int z, PlantState state) {
        final int[] m = {x, y, z};
        final int height = random.nextInt(3) + 1;
        for (int j = 0; j < height; j++) {
            if (!placeCoralBlock(level, random, m[0], m[1], m[2], state)) {
                return true;
            }
            m[1]++;
        }
        final int bx = m[0];
        final int by = m[1];
        final int bz = m[2];
        final int branches = random.nextInt(3) + 2;
        final List<Integer> directions = shuffled(List.of(0, 1, 2, 3), random);
        for (int d : directions.subList(0, branches)) {
            m[0] = bx + DX[d];
            m[1] = by;
            m[2] = bz + DZ[d];
            final int length = random.nextInt(5) + 2;
            int sinceStep = 0;
            for (int j = 0; j < length && placeCoralBlock(level, random, m[0], m[1], m[2], state); j++) {
                sinceStep++;
                m[1]++;
                if (j == 0 || sinceStep >= 2 && random.nextFloat() < 0.25f) {
                    m[0] += DX[d];
                    m[2] += DZ[d];
                    sinceStep = 0;
                }
            }
        }
        return true;
    }

    /** {@code CoralMushroomFeature.placeFeature}. */
    private static boolean mushroom(PlantLevel level, XoroshiroRandomSource random, int x, int y, int z, PlantState state) {
        final int sizeY = random.nextInt(3) + 3;
        final int sizeX = random.nextInt(3) + 3;
        final int sizeZ = random.nextInt(3) + 3;
        final int down = random.nextInt(3) + 1;
        for (int i = 0; i <= sizeX; i++) {
            for (int j = 0; j <= sizeY; j++) {
                for (int k = 0; k <= sizeZ; k++) {
                    final boolean xEdge = i == 0 || i == sizeX;
                    final boolean yEdge = j == 0 || j == sizeY;
                    final boolean zEdge = k == 0 || k == sizeZ;
                    if ((!xEdge || !yEdge) && (!zEdge || !yEdge) && (!xEdge || !zEdge) && (xEdge || yEdge || zEdge)
                        && !(random.nextFloat() < 0.1f)) {
                        placeCoralBlock(level, random, x + i, y + j - down, z + k, state);
                    }
                }
            }
        }
        return true;
    }

    /** {@code CoralClawFeature.placeFeature}. */
    private static boolean claw(PlantLevel level, XoroshiroRandomSource random, int x, int y, int z, PlantState state) {
        if (!placeCoralBlock(level, random, x, y, z, state)) {
            return false;
        }
        final int direction = random.nextInt(4);
        final int count = random.nextInt(2) + 2;
        final List<Integer> directions = shuffled(List.of(direction, clockWise(direction), counterClockWise(direction)), random);
        for (int d : directions.subList(0, count)) {
            final int[] m = {x, y, z};
            final int first = random.nextInt(2) + 1;
            move(m, d);
            final int step;
            final int rest;
            if (d == direction) {
                step = direction;
                rest = random.nextInt(3) + 2;
            } else {
                move(m, UP);
                step = new int[] {d, UP}[random.nextInt(2)];
                rest = random.nextInt(3) + 3;
            }
            for (int l = 0; l < first && placeCoralBlock(level, random, m[0], m[1], m[2], state); l++) {
                move(m, step);
            }
            move(m, opposite(step));
            move(m, UP);
            for (int l = 0; l < rest; l++) {
                move(m, direction);
                if (!placeCoralBlock(level, random, m[0], m[1], m[2], state)) {
                    break;
                }
                if (random.nextFloat() < 0.25f) {
                    move(m, UP);
                }
            }
        }
        return true;
    }

    /**
     * {@code CoralHelpers.placeCoralBlock}: the coral block goes where salt water or a coral is, under salt water; then a coral
     * or fan above (one in four), else a sea pickle (one in twenty), and a wall fan on each side that holds salt water (one in
     * five per side, north, east, south, west).
     */
    static boolean placeCoralBlock(PlantLevel level, XoroshiroRandomSource random, int x, int y, int z, PlantState coral) {
        final PlantState at = level.placed(x, y, z);
        if ((level.saltWaterBlock(x, y, z) || at != null && at.isCoral()) && level.saltWaterBlock(x, y + 1, z)) {
            level.set(x, y, z, coral);
            if (random.nextFloat() < 0.25f) {
                // tfc:salt_water_corals: #tfc:salt_water_coral_plants (5), then the 5 fans
                final int index = random.nextInt(10);
                final String colour = COLOURS.get(index % 5);
                final PlantState top = index < 5
                    ? PlantState.of(PlantState.NS + "coral/" + colour + "_coral", PlantState.Kind.CORAL, null)
                    : PlantState.of(PlantState.NS + "coral/" + colour + "_coral_fan", PlantState.Kind.CORAL_FAN, null);
                level.set(x, y + 1, z, top.withFluid(PlantState.Fluid.SALT_WATER));
            } else if (random.nextFloat() < 0.05f) {
                level.set(x, y + 1, z, PlantState.of(PlantState.NS + "sea_pickle", PlantState.Kind.SEA_PICKLE, null)
                    .withFluid(PlantState.Fluid.SALT_WATER).withExtra(random.nextInt(4) + 1));
            }
            for (int d = 0; d < 4; d++) {
                if (random.nextFloat() < 0.2f) {
                    final int rx = x + DX[d];
                    final int rz = z + DZ[d];
                    if (level.saltWaterBlock(rx, y, rz)) {
                        final String colour = COLOURS.get(random.nextInt(5));
                        level.set(rx, y, rz, PlantState.of(PlantState.NS + "coral/" + colour + "_coral_wall_fan",
                            PlantState.Kind.CORAL_WALL_FAN, null).withFluid(PlantState.Fluid.SALT_WATER).withExtra(d));
                    }
                }
            }
            return true;
        }
        return false;
    }

    /** {@code Util.shuffle} of a copy: from the end, swap each slot with a random earlier (or the same) one. */
    static List<Integer> shuffled(List<Integer> values, XoroshiroRandomSource random) {
        final List<Integer> list = new ArrayList<>(values);
        for (int i = list.size(); i > 1; i--) {
            final int j = random.nextInt(i);
            list.set(i - 1, list.set(j, list.get(i - 1)));
        }
        return list;
    }

    /** Moves a position one step in a horizontal direction (0..3) or {@link #UP}; -1 is down. */
    private static void move(int[] m, int direction) {
        if (direction == UP) {
            m[1]++;
        } else if (direction == -1) {
            m[1]--;
        } else {
            m[0] += DX[direction];
            m[2] += DZ[direction];
        }
    }

    private static int opposite(int direction) {
        return direction == UP ? -1 : (direction + 2) & 3;
    }

    private static int clockWise(int direction) {
        return (direction + 1) & 3;
    }

    private static int counterClockWise(int direction) {
        return (direction + 3) & 3;
    }
}
