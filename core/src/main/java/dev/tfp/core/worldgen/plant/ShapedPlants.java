/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/feature/plant/CreepingPlantFeature.java @ c3b4d9536, with
 * src/main/java/net/dries007/tfc/world/feature/plant/{CreepingOceanPlantFeature,EpiphytePlantFeature,
 * RotatableWaterPlantFeature,TFCTwistingVinesFeature,TFCWeepingVinesFeature,TFCKelpFeature,TFCVinesFeature,GiantKelpFeature,
 * BranchingCactusFeature}.java, src/main/java/net/dries007/tfc/common/blocks/plant/{CreepingPlantBlock,
 * CreepingWaterPlantBlock (updateStateFromSides),KelpTreeFlowerBlock (generatePlant, growTreeRecursive),KelpTreeBlock and
 * PipePlantBlock (getStateForPlacement),GrowingBranchingCactusBlock (growRecursively, canGrowInto, tryFruit)}.java,
 * src/main/java/net/dries007/tfc/util/Helpers.java (triangle) and Minecraft 1.21's Mth.nextInt, Direction.allShuffled,
 * Direction.Plane.getRandomDirection and VineBlock.isAcceptableNeighbour
 */
package dev.tfp.core.worldgen.plant;

import dev.tfp.core.worldgen.random.XoroshiroRandomSource;
import dev.tfp.core.worldgen.tree.TreeGround;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The plant shape features of PAR-WORLD-13: creeping plants and ocean creepers, epiphytes and rotatable ocean plants, the
 * growing columns (twisting, weeping, kelp), vines, giant kelp trees and saguaros. Each keeps TFC's order of random draws and
 * reads the world through the {@link PlantLevel}. Pure apart from the level it writes to.
 */
final class ShapedPlants {
    private static final int DOWN = 0;
    private static final int UP = 1;
    private static final int[] DX = PlantLevel.DX;
    private static final int[] DY = PlantLevel.DY;
    private static final int[] DZ = PlantLevel.DZ;
    private static final int[] HORIZONTAL = PlantLevel.HORIZONTAL;
    /** {@code Direction.values()} as indices, the order {@code Direction.allShuffled} shuffles. */
    private static final List<Integer> ALL = List.of(0, 1, 2, 3, 4, 5);

    private ShapedPlants() {}

    /** {@code CreepingPlantFeature} and {@code CreepingOceanPlantFeature}. */
    static boolean creeping(PlantFeature.Creeping c, PlantLevel level, XoroshiroRandomSource random, int x, int y, int z) {
        final int r = c.radius();
        boolean placed = false;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dy = 0; dy < c.height(); dy++) {
                    // CreepingPlantFeature tests x * x + z + z (sic), the ocean version x * x + z * z
                    final int disc = c.ocean() ? dx * dx + dz * dz : dx * dx + dz + dz;
                    if (disc < r * r && random.nextFloat() < c.integrity()) {
                        final int px = x + dx;
                        final int py = y + dy;
                        final int pz = z + dz;
                        if (!level.replaceable(px, py, pz)) {
                            continue;
                        }
                        final double tide = level.view().tideLevel(px, pz);
                        if (c.ocean() ? py > c.tideHeight() + tide : py <= c.tideHeight() + 0.5 + tide) {
                            continue;
                        }
                        final int faces = level.creepingFaces(c.state(), px, py, pz);
                        if (faces == 0) {
                            continue;
                        }
                        final PlantState state = c.state().withExtra(faces);
                        if (!c.ocean()) {
                            level.set(px, py, pz, state);
                            continue;
                        }
                        final PlantState filled = PlantLevel.fill(state, level.fluid(px, py, pz));
                        if (filled != null) {
                            level.set(px, py, pz, filled);
                            placed = true;
                        }
                    }
                }
            }
        }
        // CreepingPlantFeature always reports false
        return placed;
    }

    /**
     * {@code EpiphytePlantFeature} (the horizontal facings in order) and {@code RotatableWaterPlantFeature} (all six,
     * shuffled, then filled with the position's fluid), up to 11 blocks above the origin.
     */
    static boolean attached(PlantFeature.Attached a, PlantLevel level, XoroshiroRandomSource random, int x, int y, int z) {
        final int py = y + random.nextInt(12);
        if (!level.replaceable(x, py, z)) {
            return false;
        }
        if (!a.rotatable()) {
            for (int d : HORIZONTAL) {
                final PlantState state = a.state().withExtra(d);
                if (level.canSurvive(state, x, py, z)) {
                    level.set(x, py, z, state);
                    return true;
                }
            }
            return false;
        }
        for (int d : Corals.shuffled(ALL, random)) {
            final PlantState state = a.state().withExtra(d);
            if (level.canSurvive(state, x, py, z)) {
                final PlantState filled = PlantLevel.fill(state, level.fluid(x, py, z));
                if (filled != null) {
                    level.set(x, py, z, filled);
                    return true;
                }
            }
        }
        return false;
    }

    /** {@code Helpers.triangle}. */
    private static int triangle(XoroshiroRandomSource random, int range) {
        return random.nextInt(range) - random.nextInt(range);
    }

    /** {@code Mth.nextInt(random, min, max)}. */
    private static int between(XoroshiroRandomSource random, int min, int max) {
        return min >= max ? min : random.nextInt(max - min + 1) + min;
    }

    /** The {@code ColumnPlantConfig} features: {@code TFCTwistingVinesFeature}, {@code TFCWeepingVinesFeature}, {@code TFCKelpFeature}. */
    static boolean column(PlantFeature.Column c, PlantLevel level, XoroshiroRandomSource random, int x, int y, int z) {
        boolean placedAny = false;
        final int r = c.radius();
        for (int i = 0; i < c.tries(); i++) {
            switch (c.kind()) {
                case TWISTING -> {
                    final int px = x + triangle(random, r);
                    final int pz = z + triangle(random, r);
                    // tfc:grass_plantable_on below, else the whole feature stops
                    final TreeGround below = level.ground(px, y - 1, pz);
                    if (below == null || !below.treeGrowsOn()) {
                        return false;
                    }
                    if (level.replaceable(px, y, pz)) {
                        twistingColumn(level, random, px, y, pz, random.nextInt(c.maxHeight() - c.minHeight()) + c.minHeight(), c);
                        placedAny = true;
                    }
                }
                case WEEPING -> {
                    final int px = x + triangle(random, r);
                    final int py = y + triangle(random, 4) + 10;
                    final int pz = z + triangle(random, r);
                    // minecraft:leaves, minecraft:logs, minecraft:base_stone_overworld above
                    final TreeGround above = level.ground(px, py + 1, pz);
                    if (above != null && (above.isLeaves() || above == TreeGround.LOG || above == TreeGround.STONE)
                        && level.air(px, py, pz)) {
                        weepingColumn(level, random, px, py, pz, random.nextInt(c.maxHeight() - c.minHeight()) + c.minHeight(), c);
                        placedAny = true;
                    }
                }
                case KELP -> {
                    final int px = x + random.nextInt(r) - random.nextInt(r);
                    final int pz = z + random.nextInt(r) - random.nextInt(r);
                    final int py = level.view().oceanFloor(px, pz);
                    final PlantState.Fluid fluid = level.fluid(px, py, pz);
                    if (fluid == PlantState.Fluid.EMPTY) {
                        continue;
                    }
                    final PlantState body = PlantLevel.fill(c.body(), fluid);
                    final PlantState head = PlantLevel.fill(c.head(), fluid);
                    if (body != null && head != null && level.fluidBlock(px, py, pz)) {
                        kelpColumn(level, random, px, py, pz, between(random, c.minHeight(), c.maxHeight()), body, head);
                        placedAny = true;
                    }
                }
            }
        }
        return placedAny;
    }

    /** {@code TFCTwistingVinesFeature.placeColumn}: upwards while replaceable, the head (age 17..25) where it ends. */
    private static void twistingColumn(PlantLevel level, XoroshiroRandomSource random, int x, int y, int z, int height,
                                       PlantFeature.Column c) {
        int cy = y;
        for (int i = 1; i <= height; i++) {
            if (level.replaceable(x, cy, z)) {
                if (i == height || !level.air(x, cy + 1, z)) {
                    level.set(x, cy, z, c.head().withAge(between(random, 17, 25)));
                    break;
                }
                level.set(x, cy, z, c.body());
            }
            cy++;
        }
    }

    /** {@code TFCWeepingVinesFeature.placeColumn}: downwards while air, the head (age 17..25) where it ends. */
    private static void weepingColumn(PlantLevel level, XoroshiroRandomSource random, int x, int y, int z, int height,
                                      PlantFeature.Column c) {
        int cy = y;
        for (int i = 0; i <= height; i++) {
            if (level.air(x, cy, z)) {
                if (i == height || !level.air(x, cy - 1, z)) {
                    level.set(x, cy, z, c.head().withAge(between(random, 17, 25)));
                    break;
                }
                level.set(x, cy, z, c.body());
            }
            cy--;
        }
    }

    /** {@code TFCKelpFeature.placeColumn}: upwards through the water, the head (age 22..25) below the surface. */
    private static void kelpColumn(PlantLevel level, XoroshiroRandomSource random, int x, int y, int z, int height,
                                   PlantState body, PlantState head) {
        int cy = y;
        for (int i = 1; i <= height; i++) {
            if (level.canSurvive(body, x, cy, z) && level.fluidBlock(x, cy, z)) {
                if (i == height || !level.fluidBlock(x, cy + 1, z)) {
                    final PlantState below = level.placed(x, cy - 1, z);
                    if (below == null || !below.block().equals(head.block())) {
                        level.set(x, cy, z, head.withAge(between(random, 22, 25)));
                    }
                    return;
                }
                level.set(x, cy, z, body);
            }
            cy++;
        }
    }

    /**
     * {@code TFCVinesFeature}: in air, the vine on the first horizontal face whose neighbour has a full face (terrain blocks
     * and leaves), then the same vine down through up to {@code nextInt(14)} air blocks.
     */
    static boolean vines(PlantFeature.Vines v, PlantLevel level, XoroshiroRandomSource random, int x, int y, int z) {
        if (!level.air(x, y, z)) {
            return false;
        }
        for (int d : HORIZONTAL) {
            final TreeGround neighbour = level.ground(x + DX[d], y, z + DZ[d]);
            if (neighbour != null && (neighbour.sturdyTop() || neighbour.isLeaves())) {
                final PlantState state = v.state().withExtra(1 << d);
                level.set(x, y, z, state);
                final int length = random.nextInt(14);
                for (int i = 1; i <= length; i++) {
                    if (!level.air(x, y - i, z)) {
                        break;
                    }
                    level.set(x, y - i, z, state);
                }
                return true;
            }
        }
        return false;
    }

    /** {@code GiantKelpFeature}: {@code KelpTreeFlowerBlock.generatePlant} with the sea level lowered by up to 5. */
    static boolean giantKelp(PlantFeature.GiantKelp k, PlantLevel level, XoroshiroRandomSource random, int x, int y, int z) {
        final PlantState.Fluid fluid = level.fluid(x, y, z);
        final int seaLevel = level.view().seaLevel() - random.nextInt(6);
        // SALT_WATER's canContain: empty and salt water
        if (fluid == PlantState.Fluid.WATER) {
            return false;
        }
        final PlantState original = level.placed(x, y, z);
        setBody(level, k, x, y, z, fluid);
        if (level.canSurvive(level.placed(x, y, z), x, y, z) && growTree(level, k, random, x, y, z, x, z, 0, fluid, seaLevel)) {
            return true;
        }
        if (original == null) {
            level.clear(x, y, z);
        } else {
            level.set(x, y, z, original);
        }
        return false;
    }

    /** {@code setBodyBlockWithFluid}: the body with the connections of {@code PipePlantBlock.getStateForPlacement}. */
    private static void setBody(PlantLevel level, PlantFeature.GiantKelp k, int x, int y, int z, PlantState.Fluid fluid) {
        final String body = k.body().block();
        int mask = 0;
        if (level.kelpDown(body, x, y - 1, z)) {
            mask |= 1 << DOWN;
        }
        if (level.kelpTree(body, x, y + 1, z)) {
            mask |= 1 << UP;
        }
        for (int d = 2; d < 6; d++) {
            if (level.kelpTree(body, x + DX[d], y, z + DZ[d])) {
                mask |= 1 << d;
            }
        }
        level.set(x, y, z, k.body().withExtra(mask).withFluid(fluid));
    }

    /** {@code allNeighborsEmpty}: salt water blocks on the horizontal sides (but the excluded one, or -1). */
    private static boolean allNeighborsEmpty(PlantLevel level, int x, int y, int z, int excluding) {
        for (int d : HORIZONTAL) {
            if (d != excluding && !level.saltWaterBlock(x + DX[d], y, z + DZ[d])) {
                return false;
            }
        }
        return true;
    }

    /** {@code KelpTreeFlowerBlock.growTreeRecursive}. */
    private static boolean growTree(PlantLevel level, PlantFeature.GiantKelp k, XoroshiroRandomSource random, int bx, int by, int bz,
                                    int ox, int oz, int iterations, PlantState.Fluid fluid, int seaLevel) {
        int i = random.nextInt(5) + 1;
        if (iterations == 0) {
            i++;
        }
        for (int j = 0; j < i; j++) {
            final int py = by + j + 1;
            if (!level.saltWaterBlock(bx, py, bz) || !allNeighborsEmpty(level, bx, py, bz, -1) || py >= seaLevel - 2) {
                return false;
            }
            if (py == seaLevel - 3) {
                // placeGrownFlower: age 5, facing up, filled with the position's fluid, when it survives
                final PlantState.Fluid at = level.fluid(bx, py, bz);
                if (at == PlantState.Fluid.WATER) {
                    return false;
                }
                final PlantState flower = k.flower().withAge(5).withFluid(at);
                if (!level.canSurvive(flower, bx, py, bz)) {
                    return false;
                }
                level.set(bx, py, bz, flower);
            } else {
                setBody(level, k, bx, py, bz, fluid);
            }
            setBody(level, k, bx, py - 1, bz, fluid);
        }
        boolean willContinue = false;
        if (iterations < 4) {
            int attempts = random.nextInt(4);
            if (iterations == 0) {
                attempts++;
            }
            for (int n = 0; n < attempts; n++) {
                final int d = HORIZONTAL[random.nextInt(4)];
                final int ax = bx + DX[d];
                final int ay = by + i;
                final int az = bz + DZ[d];
                if (Math.abs(ax - ox) < 8 && Math.abs(az - oz) < 8 && level.saltWaterBlock(ax, ay, az)
                    && level.saltWaterBlock(ax, ay - 1, az) && allNeighborsEmpty(level, ax, ay, az, PlantLevel.opposite(d))) {
                    willContinue = true;
                    setBody(level, k, ax, ay, az, fluid);
                    setBody(level, k, bx, ay, bz, fluid);
                    growTree(level, k, random, ax, ay, az, ox, oz, iterations + 1, fluid, seaLevel);
                }
            }
        }
        if (!willContinue) {
            level.set(bx, by + i, bz, k.flower().withAge(random.nextInt(10) == 1 ? 3 : 5).withFluid(fluid));
        }
        return true;
    }

    /** {@code BranchingCactusFeature}: {@code growRecursively} of height 8, drawing from the region's random. */
    static boolean branchingCactus(PlantFeature.BranchingCactus b, PlantLevel level, int x, int y, int z) {
        return grow(b, level, level.regionRandom(), x, y, z, b.head(), 8);
    }

    /** {@code GrowingBranchingCactusBlock.growRecursively} ({@code testDown} of the block's own state is always true). */
    private static boolean grow(PlantFeature.BranchingCactus b, PlantLevel level, XoroshiroRandomSource random, int x, int y, int z,
                                PlantState state, int height) {
        if (!canGrowInto(level, x, y, z, UP) || !canGrowInto(level, x, y + 1, z, UP)) {
            return false;
        }
        final Set<Integer> used = new HashSet<>();
        final int column = 1 << DOWN | 1 << UP;
        for (int i = 0; i < height; i++) {
            final int cy = y + i;
            if (i == height - 1 && level.replaceable(x, cy + 1, z)) {
                // tryFruit: canBeReplaced above the last block
                level.set(x, cy + 1, z, b.fruit());
            }
            if (!canGrowInto(level, x, cy, z, UP)) {
                return true;
            }
            level.set(x, cy, z, b.body().withExtra(column));
            if ((state.extra() & 64) != 0 && i > 2 && random.nextFloat() < 0.6f && i < height - 1) {
                final int d = HORIZONTAL[random.nextInt(4)];
                if (!used.contains(d)) {
                    final int x1 = x + DX[d];
                    final int z1 = z + DZ[d];
                    if (canGrowInto(level, x1, cy, z1, d) && canGrowInto(level, x1 + DX[d], cy, z1 + DZ[d], d)
                        && canGrowInto(level, x1 + DX[d], cy + 1, z1 + DZ[d], UP)) {
                        used.add(d);
                        final int opposite = PlantLevel.opposite(d);
                        level.set(x1, cy, z1, b.body().withExtra(1 << d | 1 << opposite));
                        final PlantState branch = state.withExtra((state.extra() & ~64) | 1 << opposite | 1 << UP);
                        level.set(x1 + DX[d], cy, z1 + DZ[d], branch);
                        grow(b, level, random, x1 + DX[d], cy + 1, z1 + DZ[d], branch, 3);
                        // go back and fix the branch connection
                        level.set(x, cy, z, b.body().withExtra(column | 1 << d));
                    }
                }
            }
        }
        return true;
    }

    /** {@code canGrowInto}: air with no branching cactus on any side but the one grown from. */
    private static boolean canGrowInto(PlantLevel level, int x, int y, int z, int direction) {
        if (!level.air(x, y, z)) {
            return false;
        }
        for (int d = 0; d < 6; d++) {
            if (d != PlantLevel.opposite(direction) && level.branchingCactus(x + DX[d], y + DY[d], z + DZ[d])) {
                return false;
            }
        }
        return true;
    }
}
