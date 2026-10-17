/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/feature/DynamicDensityRandomPatchFeature.java @ c3b4d9536, with
 * src/main/java/net/dries007/tfc/world/feature/NoisyMultipleFeature.java,
 * src/main/java/net/dries007/tfc/world/feature/plant/{BlockWithFluidFeature,TallPlantFeature,EmergentPlantFeature,
 * SubmergedTallPlantFeature,TallWildCropFeature,SpreadingCropFeature}.java,
 * src/main/java/net/dries007/tfc/common/blocks/plant/{TFCTallGrassBlock,TallWaterPlantBlock}.java (placeTwoHalves),
 * src/main/java/net/dries007/tfc/common/blocks/crop/WildDoubleCropBlock.java (placeTwoHalves),
 * src/main/java/net/dries007/tfc/world/stateprovider/RandomPropertyProvider.java,
 * src/main/java/net/dries007/tfc/world/placement/{ClimatePlacement,IntertidalPlacement,NoSolidNeighborsPlacement,
 * ShallowWaterPlacement}.java, src/main/java/net/dries007/tfc/world/blockpredicate/{AirOrEmptyFluidPredicate,
 * ReplaceablePredicate,WouldSurviveWithFluidPredicate}.java, and the Minecraft 1.21 steps they run with (PlacedFeature.place,
 * RarityFilter, InSquarePlacement, HeightmapPlacement, CountPlacement, NoiseBasedCountPlacement, BlockPredicateFilter,
 * WouldSurvivePredicate, RandomPatchFeature, SimpleBlockFeature)
 */
package dev.tfp.core.worldgen.plant;

import dev.tfp.core.worldgen.Mth;
import dev.tfp.core.worldgen.groundcover.ClimateFilter;
import dev.tfp.core.worldgen.noise.BiomeInfoNoise;
import dev.tfp.core.worldgen.random.XoroshiroRandomSource;
import dev.tfp.core.worldgen.tree.TreeGround;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Runs a {@link PlantFeature.Placed} the way Minecraft runs a placed feature: each modifier maps the positions (drawing from
 * the feature's random when it is reached, depth first, as vanilla's position stream), then the configured feature is placed
 * at every position left. Pure apart from the {@link PlantLevel} it writes to.
 */
final class PlantPlacer {
    /** TFC's world preset {@code grass_density} (0.5), which scales the dynamic patches of sparse forests. */
    static final float GRASS_DENSITY = 0.5f;
    /** {@code Direction.Plane.HORIZONTAL}: north, east, south, west, as x and z steps. */
    private static final int[] DX = {0, 1, 0, -1};
    private static final int[] DZ = {-1, 0, 1, 0};

    private PlantPlacer() {}

    /** {@code PlacedFeature.place} at a position: whether the feature placed at any position the modifiers gave. */
    static boolean place(PlantFeature.Placed placed, PlantLevel level, XoroshiroRandomSource random, int x, int y, int z) {
        return apply(placed, 0, level, random, x, y, z);
    }

    private static boolean apply(PlantFeature.Placed placed, int index, PlantLevel level, XoroshiroRandomSource random,
                                 int x, int y, int z) {
        if (index == placed.modifiers().size()) {
            return feature(placed.feature(), level, random, x, y, z);
        }
        final PlantFeature.Modifier modifier = placed.modifiers().get(index);
        final int next = index + 1;
        switch (modifier) {
            case PlantFeature.Rarity r -> {
                return random.nextFloat() < 1f / r.chance() && apply(placed, next, level, random, x, y, z);
            }
            case PlantFeature.InSquare s -> {
                final int nx = random.nextInt(16) + x;
                final int nz = random.nextInt(16) + z;
                return apply(placed, next, level, random, nx, y, nz);
            }
            case PlantFeature.Heightmap h -> {
                final int ny = h.oceanFloor() ? level.view().oceanFloor(x, z) : level.view().worldSurface(x, z);
                return ny > level.view().minY() && apply(placed, next, level, random, x, ny, z);
            }
            case PlantFeature.Climate c -> {
                final int density = level.view().forestType(x >> 4, z >> 4).density();
                return c.filter().isValid(level.view(), x, y, z, random, density) && apply(placed, next, level, random, x, y, z);
            }
            case PlantFeature.Count c -> {
                final int count = c.min() == c.max() ? c.min() : random.nextInt(c.max() - c.min() + 1) + c.min();
                return repeat(placed, next, level, random, x, y, z, count);
            }
            case PlantFeature.NoiseCount c -> {
                final double noise = BiomeInfoNoise.INSTANCE.getValue(x / c.factor(), z / c.factor());
                final int count = (int) Math.ceil((noise + c.offset()) * c.ratio());
                return repeat(placed, next, level, random, x, y, z, count);
            }
            case PlantFeature.Filter f -> {
                return test(f.predicate(), level, x, y, z) && apply(placed, next, level, random, x, y, z);
            }
            case PlantFeature.Intertidal t -> {
                final int heightDiff = (int) (y - level.view().tideLevel(x, z));
                return t.min() <= heightDiff && heightDiff <= t.max() && apply(placed, next, level, random, x, y, z);
            }
            case PlantFeature.NoSolidNeighbors n -> {
                for (int d = 0; d < 4; d++) {
                    if (!level.nonSolid(x + DX[d], y, z + DZ[d])) {
                        return false;
                    }
                }
                return apply(placed, next, level, random, x, y, z);
            }
            case PlantFeature.ShallowWater w -> {
                return shallowWater(w, level, random, x, y, z) && apply(placed, next, level, random, x, y, z);
            }
        }
    }

    private static boolean repeat(PlantFeature.Placed placed, int next, PlantLevel level, XoroshiroRandomSource random,
                                  int x, int y, int z, int count) {
        boolean any = false;
        for (int i = 0; i < count; i++) {
            any |= apply(placed, next, level, random, x, y, z);
        }
        return any;
    }

    /** {@code ShallowWaterPlacement.getPositions}: the depth of the water below, then one float against it. */
    private static boolean shallowWater(PlantFeature.ShallowWater w, PlantLevel level, XoroshiroRandomSource random, int x, int y, int z) {
        for (int i = 0; i < w.maxDepth(); i++) {
            // tfc:any_infinite_water: fresh or salt water
            if (level.fluid(x, y - 1 - i, z) == PlantState.Fluid.EMPTY) {
                if (i < w.minDepth()) {
                    return false;
                }
                return random.nextFloat() > (double) i / w.maxDepth();
            }
        }
        return false;
    }

    static boolean test(PlantFeature.Predicate predicate, PlantLevel level, int x, int y, int z) {
        return switch (predicate) {
            case PlantFeature.AirOrEmptyFluid a -> level.airOrEmptyFluid(x, y, z);
            case PlantFeature.Replaceable r -> level.replaceable(x, y, z);
            case PlantFeature.WouldSurvive s -> level.canSurvive(s.state(), x, y, z);
            case PlantFeature.WouldSurviveWithFluid s -> {
                final PlantState filled = PlantLevel.fill(s.state(), level.fluid(x, y, z));
                yield filled != null && level.canSurvive(filled, x, y, z);
            }
        };
    }

    /** {@code Feature.place} of a configured feature: whether it placed. */
    private static boolean feature(PlantFeature.Configured configured, PlantLevel level, XoroshiroRandomSource random,
                                   int x, int y, int z) {
        return switch (configured) {
            case PlantFeature.Patch p -> patch(p, level, random, x, y, z);
            case PlantFeature.SimpleBlock s -> simpleBlock(s, level, random, x, y, z);
            case PlantFeature.TallPlant t -> {
                tallPlant(t.state(), level, random, x, y, z);
                yield true;
            }
            case PlantFeature.EmergentPlant e -> {
                if (level.fluid(x, y + 1, z) == PlantState.Fluid.EMPTY && level.replaceable(x, y, z) && level.replaceable(x, y + 1, z)) {
                    tallWaterPlant(e.state(), level, x, y, z);
                }
                yield true;
            }
            case PlantFeature.SubmergedTallPlant s -> {
                if (level.fluid(x, y, z) == level.fluid(x, y + 1, z) && level.replaceable(x, y, z) && level.replaceable(x, y + 1, z)) {
                    tallWaterPlant(s.state(), level, x, y, z);
                }
                yield true;
            }
            case PlantFeature.TallWildCrop t -> {
                level.set(x, y, z, t.state().withPart(PlantState.Part.LOWER));
                level.set(x, y + 1, z, t.state().withPart(PlantState.Part.UPPER));
                yield true;
            }
            case PlantFeature.SpreadingCrop s -> {
                spreadingCrop(s.state(), level, random, x, y, z);
                yield true;
            }
            case PlantFeature.NoisyMultiple n -> noisyMultiple(n, level, random, x, y, z);
            case PlantFeature.Coral c -> Corals.place(level, random, x, y, z, c.shape());
        };
    }

    /**
     * {@code RandomPatchFeature.place} and {@code DynamicDensityRandomPatchFeature.place}: the tries (scaled for a dynamic
     * patch), each at an offset of {@code [-spread, spread]} drawn x, y, z as two draws each.
     */
    private static boolean patch(PlantFeature.Patch patch, PlantLevel level, XoroshiroRandomSource random, int x, int y, int z) {
        final int tries = patch.dynamic() ? dynamicTries(level, x, y, z, patch.tries()) : patch.tries();
        final int width = patch.xzSpread() + 1;
        final int height = patch.ySpread() + 1;
        int placed = 0;
        for (int i = 0; i < tries; i++) {
            final int dx = random.nextInt(width) - random.nextInt(width);
            final int dy = random.nextInt(height) - random.nextInt(height);
            final int dz = random.nextInt(width) - random.nextInt(width);
            if (place(patch.feature(), level, random, x + dx, y + dy, z + dz)) {
                placed++;
            }
        }
        return placed > 0;
    }

    /** {@code DynamicDensityRandomPatchFeature.getTries}: fewer tries high up and in dense forests. */
    static int dynamicTries(PlantLevel level, int x, int y, int z, int tries) {
        final int density = level.view().forestType(x >> 4, z >> 4).density();
        final int seaLevel = level.view().seaLevel();
        if (y > seaLevel + 25) {
            tries = (int) (tries * (1f - Mth.clampedMap(y, seaLevel + 25, seaLevel + 100, 0f, 0.8f)));
        }
        return switch (density) {
            case 4 -> Math.min(tries, 8);
            case 3 -> Math.min(tries, 14);
            case 2 -> Math.min(tries, 40);
            default -> (int) (tries * (GRASS_DENSITY * 2f));
        };
    }

    /**
     * {@code SimpleBlockFeature} ({@code minecraft:simple_block}) or {@code BlockWithFluidFeature} ({@code tfc:block_with_fluid}):
     * the provider's state (a random age when the block has one), filled with the position's fluid for the latter, placed when
     * it survives.
     */
    private static boolean simpleBlock(PlantFeature.SimpleBlock s, PlantLevel level, XoroshiroRandomSource random, int x, int y, int z) {
        PlantState state = s.state();
        if (s.randomAge() && hasAge(state)) {
            // RandomPropertyProvider: one of the age property's 4 values
            state = state.withAge(random.nextInt(4));
        }
        if (s.withFluid()) {
            state = PlantLevel.fill(state, level.fluid(x, y, z));
        }
        if (state != null && level.canSurvive(state, x, y, z)) {
            level.set(x, y, z, state);
            return true;
        }
        return false;
    }

    /** Whether the block has an {@code age} property ({@code Plant.getAgeProperty}). */
    static boolean hasAge(PlantState state) {
        return state.kind() == PlantState.Kind.PLANT && state.type().hasAge();
    }

    /**
     * {@code TallPlantFeature}: the block's {@code placeTwoHalves}. A tall water plant has its own (filled with the water of
     * each half, no draw); the others ({@code TFCTallGrassBlock}) draw an age of 1..3 when the plant has one.
     */
    private static void tallPlant(PlantState state, PlantLevel level, XoroshiroRandomSource random, int x, int y, int z) {
        if (state.type() == PlantType.TALL_WATER || state.type() == PlantType.TALL_WATER_FRESH) {
            tallWaterPlant(state, level, x, y, z);
            return;
        }
        PlantState lower = defaultState(state).withPart(PlantState.Part.LOWER);
        PlantState upper = defaultState(state).withPart(PlantState.Part.UPPER);
        if (hasAge(state)) {
            final int age = random.nextInt(3) + 1;
            lower = lower.withAge(age);
            upper = upper.withAge(age);
        }
        level.set(x, y, z, lower);
        level.set(x, y + 1, z, upper);
    }

    /** {@code TallWaterPlantBlock.placeTwoHalves}: both halves filled with their position's fluid, when there is water below. */
    private static void tallWaterPlant(PlantState state, PlantLevel level, int x, int y, int z) {
        final PlantState.Fluid bottom = level.fluid(x, y, z);
        final PlantState.Fluid top = level.fluid(x, y + 1, z);
        if (bottom != PlantState.Fluid.EMPTY) {
            final PlantState lower = PlantLevel.fill(defaultState(state).withPart(PlantState.Part.LOWER), bottom);
            final PlantState upper = PlantLevel.fill(defaultState(state).withPart(PlantState.Part.UPPER), top);
            if (lower != null && upper != null) {
                level.set(x, y, z, lower);
                level.set(x, y + 1, z, upper);
            }
        }
    }

    /** The block's default state: age 0 when it has an age, no fluid. */
    private static PlantState defaultState(PlantState state) {
        return state.withAge(hasAge(state) ? 0 : -1).withFluid(PlantState.Fluid.EMPTY);
    }

    /**
     * {@code SpreadingCropFeature}: a random horizontal side; when the block below it is {@code tfc:spreading_fruit_grows_on}
     * and the side is replaceable, the fruit goes there and the crop at the position, pointing at it.
     */
    private static void spreadingCrop(PlantState state, PlantLevel level, XoroshiroRandomSource random, int x, int y, int z) {
        final int d = random.nextInt(4);
        final int fx = x + DX[d];
        final int fz = z + DZ[d];
        final TreeGround below = level.ground(fx, y - 1, fz);
        if (below != null && below.bushPlantableOn() && level.replaceable(fx, y, fz)) {
            level.set(fx, y, fz, PlantState.of(WildCrop.fruit(state.block()), PlantState.Kind.WILD_CROP_FRUIT, null));
            level.set(x, y, z, state.withExtra(d));
        }
    }

    /**
     * {@code NoisyMultipleFeature}: the features rotated by {@code ceil(temperature * 10 * count)}, each placed at the
     * position until two have placed.
     */
    private static boolean noisyMultiple(PlantFeature.NoisyMultiple n, PlantLevel level, XoroshiroRandomSource random, int x, int y, int z) {
        final int rotation = (int) Math.ceil(level.view().averageTemperature(x, z) * 10 * n.features().size());
        final List<PlantFeature.Placed> features = new ArrayList<>(n.features());
        Collections.rotate(features, rotation);
        int placed = 0;
        for (PlantFeature.Placed feature : features) {
            if (place(feature, level, random, x, y, z)) {
                placed++;
                if (placed > 1) {
                    return true;
                }
            }
        }
        return placed > 0;
    }

    /** The climate filter of a modifier list, for tests. */
    static ClimateFilter climateOf(PlantFeature.Placed placed) {
        for (PlantFeature.Modifier m : placed.modifiers()) {
            if (m instanceof PlantFeature.Climate c) {
                return c.filter();
            }
        }
        return null;
    }
}
