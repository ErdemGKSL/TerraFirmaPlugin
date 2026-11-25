/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/EnvironmentHelpers.java @ c3b4d9536 (isWorldgenReplaceable), with
 * src/main/java/net/dries007/tfc/common/fluids/FluidHelpers.java (isAirOrEmptyFluid, fillWithFluid),
 * src/main/java/net/dries007/tfc/world/placement/NoSolidNeighborsPlacement.java (isNonSolid), the fluid properties of
 * src/main/java/net/dries007/tfc/common/blocks/TFCBlockStateProperties.java (SALT_WATER, FRESH_WATER) and the canSurvive
 * rules of the plant and wild crop blocks (see PlantType and WildCrop); PAR-WORLD-13: the canSurvive of
 * src/main/java/net/dries007/tfc/common/blocks/plant/{CreepingPlantBlock,CreepingWaterPlantBlock,EpiphytePlantBlock,
 * RotatableWaterPlantBlock,TFCCactusBlock,BodyPlantBlock,TopPlantBlock,TFCKelpTopBlock,KelpTreeBlock,KelpTreeFlowerBlock,
 * PipePlantBlock,BranchingCactusBlock}.java and PlantBlock.createPerchedEpiphyte, with the block tags they read
 * (tfc:creeping_plant_not_plantable_on, tfc:creeping_stone_plantable_on, tfc:anemone_plantable_on,
 * tfc:epiphyte_plantable_on, tfc:kelp_trees, tfc:kelp_branches, minecraft:leaves)
 */
package dev.tfp.core.worldgen.plant;

import dev.tfp.core.worldgen.random.XoroshiroRandomSource;
import dev.tfp.core.worldgen.tree.TreeGround;
import dev.tfp.core.worldgen.tree.TreeView;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * The world as one chunk's plant features see it: the terrain of a {@link TreeView} with the blocks these features placed
 * on top. Later features and tries see the earlier ones, as in TFC; blocks of other chunks' features are not seen (ledger).
 * Confined to the thread that runs the chunk's features.
 */
final class PlantLevel {
    private final TreeView view;
    private final Set<String> singleBlockReplaceable;
    private final Set<String> snowPileable;
    /** PAR-WORLD-22: the world's {@code grass_density} ({@code DynamicDensityRandomPatchFeature}). */
    private final float grassDensity;
    private final Map<Long, PlantBlock> placed = new LinkedHashMap<>();
    /** PAR-WORLD-13: the atoll cells of the world, or null when no feature reads them. */
    private Atolls atolls;
    /** PAR-WORLD-13: {@code WorldGenRegion.getRandom} of the decorated chunk ({@code GrowingBranchingCactusBlock}). */
    private XoroshiroRandomSource regionRandom = new XoroshiroRandomSource(0L);

    /** Directions by {@code get3DDataValue}: down, up, north, south, west, east, as x, y, z steps. */
    static final int[] DX = {0, 0, 0, 0, -1, 1};
    static final int[] DY = {-1, 1, 0, 0, 0, 0};
    static final int[] DZ = {0, 0, -1, 1, 0, 0};
    /** {@code Direction.Plane.HORIZONTAL} (north, east, south, west) as indices. */
    static final int[] HORIZONTAL = {2, 5, 3, 4};

    /**
     * @param view                   the terrain
     * @param singleBlockReplaceable the block ids of {@code tfc:single_block_replaceable}
     * @param snowPileable           the block ids of {@code tfc:can_be_snow_piled}
     */
    PlantLevel(TreeView view, Set<String> singleBlockReplaceable, Set<String> snowPileable) {
        this(view, singleBlockReplaceable, snowPileable, PlantPlacer.GRASS_DENSITY);
    }

    PlantLevel(TreeView view, Set<String> singleBlockReplaceable, Set<String> snowPileable, float grassDensity) {
        this.view = view;
        this.singleBlockReplaceable = singleBlockReplaceable;
        this.snowPileable = snowPileable;
        this.grassDensity = grassDensity;
    }

    /** Sets the atoll cells and the region random (PAR-WORLD-13). */
    PlantLevel with(Atolls atolls, XoroshiroRandomSource regionRandom) {
        this.atolls = atolls;
        this.regionRandom = regionRandom;
        return this;
    }

    Atolls atolls() {
        return atolls;
    }

    XoroshiroRandomSource regionRandom() {
        return regionRandom;
    }

    /** {@code Direction.getOpposite} of an index. */
    static int opposite(int direction) {
        return direction ^ 1;
    }

    float grassDensity() {
        return grassDensity;
    }

    TreeView view() {
        return view;
    }

    static long key(int x, int y, int z) {
        return ((long) x & 0x3FFFFFFL) << 38 | ((long) z & 0x3FFFFFFL) << 12 | (long) y & 0xFFFL;
    }

    /** The placed block at a position, or null where the terrain is. */
    PlantState placed(int x, int y, int z) {
        final PlantBlock block = placed.get(key(x, y, z));
        return block == null ? null : block.state();
    }

    /** Restores the terrain at a position (a feature putting the original block back). */
    void clear(int x, int y, int z) {
        placed.remove(key(x, y, z));
    }

    /** {@code isAir}/{@code isEmptyBlock}: terrain air. */
    boolean air(int x, int y, int z) {
        return placed(x, y, z) == null && view.ground(x, y, z).isAir();
    }

    /** {@code FluidHelpers.isEmptyFluid}: a fluid's own source block (fresh or salt water), never a placed block. */
    boolean fluidBlock(int x, int y, int z) {
        return placed(x, y, z) == null && view.ground(x, y, z).hasFluid();
    }

    /**
     * {@code BlockState.isSolid}: the terrain's blocks other than air and water, and the solid plants (cacti, branching
     * cacti, the solid twisting columns).
     */
    boolean solid(int x, int y, int z) {
        final PlantState p = placed(x, y, z);
        if (p == null) {
            return !view.ground(x, y, z).isAirOrEmptyFluid();
        }
        return p.kind() == PlantState.Kind.PLANT && switch (p.type()) {
            case CACTUS, BRANCHING_CACTUS, BRANCHING_CACTUS_TOP, TWISTING_SOLID, TWISTING_SOLID_TOP -> true;
            default -> false;
        };
    }

    /** {@code isFaceSturdy}: a full terrain block (not leaves or krummholz); no placed plant is. */
    boolean sturdy(int x, int y, int z) {
        return placed(x, y, z) == null && view.ground(x, y, z).sturdyTop();
    }

    /** Whether the placed block at a position is one of the given block ids. */
    boolean placedIs(int x, int y, int z, String a, String b) {
        final PlantState p = placed(x, y, z);
        return p != null && (p.block().equals(a) || p.block().equals(b));
    }

    /** Whether the placed block at a position is a branching cactus ({@code instanceof BranchingCactusBlock}). */
    boolean branchingCactus(int x, int y, int z) {
        final PlantState p = placed(x, y, z);
        return p != null && p.kind() == PlantState.Kind.PLANT
            && (p.type() == PlantType.BRANCHING_CACTUS || p.type() == PlantType.BRANCHING_CACTUS_TOP);
    }

    /** Sets a block (TFC's {@code level.setBlock}); a later block at the same position replaces the earlier one. */
    void set(int x, int y, int z, PlantState state) {
        placed.put(key(x, y, z), new PlantBlock(x, y, z, state));
    }

    /** The blocks placed so far, each position once with its last state, in the order the positions were first set. */
    Collection<PlantBlock> blocks() {
        return placed.values();
    }

    /** The terrain block class at a position, or null where a placed block is. */
    TreeGround ground(int x, int y, int z) {
        return placed(x, y, z) != null ? null : view.ground(x, y, z);
    }

    /** The fluid at a position: a placed block's own fluid, or the terrain's water. */
    PlantState.Fluid fluid(int x, int y, int z) {
        final PlantState p = placed(x, y, z);
        if (p != null) {
            return p.fluid();
        }
        return switch (view.ground(x, y, z)) {
            case WATER -> PlantState.Fluid.WATER;
            case SALT_WATER -> PlantState.Fluid.SALT_WATER;
            default -> PlantState.Fluid.EMPTY;
        };
    }

    /** {@code FluidHelpers.isAirOrEmptyFluid}: air or a fluid's own block; never a placed block. */
    boolean airOrEmptyFluid(int x, int y, int z) {
        return placed(x, y, z) == null && view.ground(x, y, z).isAirOrEmptyFluid();
    }

    /** Whether the position is the {@code minecraft:water} block (fresh water; never a placed block). */
    boolean freshWaterBlock(int x, int y, int z) {
        return placed(x, y, z) == null && view.ground(x, y, z) == TreeGround.WATER;
    }

    /** Whether the position is TFC's salt water block ({@code TFCBlocks.SALT_WATER}). */
    boolean saltWaterBlock(int x, int y, int z) {
        return placed(x, y, z) == null && view.ground(x, y, z) == TreeGround.SALT_WATER;
    }

    /** {@code EnvironmentHelpers.isWorldgenReplaceable}: air or empty fluid, or a {@code tfc:single_block_replaceable} block. */
    boolean replaceable(int x, int y, int z) {
        final PlantState p = placed(x, y, z);
        return p == null ? view.ground(x, y, z).isWorldgenReplaceable() : singleBlockReplaceable.contains(p.block());
    }

    /** {@code NoSolidNeighborsPlacement.isNonSolid}: replaceable or {@code tfc:can_be_snow_piled}. */
    boolean nonSolid(int x, int y, int z) {
        final PlantState p = placed(x, y, z);
        return replaceable(x, y, z) || p != null && snowPileable.contains(p.block());
    }

    /**
     * {@code FluidHelpers.fillWithFluid}: the state when it already holds the fluid, the state filled when its fluid property
     * can hold the fluid, else null.
     */
    static PlantState fill(PlantState state, PlantState.Fluid fluid) {
        if (state.fluid() == fluid) {
            return state;
        }
        final PlantState.Fluid property = fluidProperty(state);
        if (property != null && (fluid == PlantState.Fluid.EMPTY || fluid == property)) {
            return state.withFluid(fluid);
        }
        return null;
    }

    /** The non-empty fluid a block's fluid property holds ({@code SALT_WATER} or {@code FRESH_WATER}), or null. */
    private static PlantState.Fluid fluidProperty(PlantState state) {
        return switch (state.kind()) {
            case PLANT -> state.type().fluidProperty();
            case WILD_CROP -> WildCrop.of(state.block()) == WildCrop.FLOODED ? PlantState.Fluid.WATER : null;
            case BERRY_BUSH -> Berries.cranberry(state) ? PlantState.Fluid.WATER : null;
            case CORAL, CORAL_FAN, CORAL_WALL_FAN, SEA_PICKLE -> PlantState.Fluid.SALT_WATER;
            default -> null;
        };
    }

    /** The block's {@code canSurvive} at a position. */
    boolean canSurvive(PlantState state, int x, int y, int z) {
        final PlantState below = placed(x, y - 1, z);
        return switch (state.kind()) {
            case PLANT -> {
                if (!state.type().survivable()) {
                    yield shapeSurvives(state, x, y, z);
                }
                if (state.part() == PlantState.Part.UPPER) {
                    // The upper half of a tall plant needs its own lower half below
                    yield below != null && below.block().equals(state.block()) && below.part() == PlantState.Part.LOWER;
                }
                yield state.type().survives(state, below == null ? view.ground(x, y - 1, z) : null, fluid(x, y - 1, z));
            }
            case WILD_CROP -> {
                final WildCrop crop = WildCrop.of(state.block());
                if (state.part() == PlantState.Part.UPPER) {
                    yield below != null && below.block().equals(state.block()) && below.part() == PlantState.Part.LOWER;
                }
                // tfc:wild_crop_grows_on = minecraft:dirt
                final boolean ground = below == null && view.ground(x, y - 1, z).treeGrowsOn();
                yield crop == WildCrop.FLOODED ? ground && fluid(x, y + 1, z) == PlantState.Fluid.EMPTY : ground;
            }
            case BERRY_BUSH, SPREADING_BUSH -> Berries.survives(this, state, x, y, z);
            default -> false;
        };
    }

    /** The {@code canSurvive} of the plant shapes that look around (PAR-WORLD-13). */
    private boolean shapeSurvives(PlantState state, int x, int y, int z) {
        return switch (state.type()) {
            case CACTUS -> {
                // TFCCactusBlock: no solid horizontal neighbour; the lower half on tfc:dry_plant_plantable_on
                for (int d : HORIZONTAL) {
                    if (solid(x + DX[d], y, z + DZ[d])) {
                        yield false;
                    }
                }
                if (state.part() == PlantState.Part.UPPER) {
                    final PlantState below = placed(x, y - 1, z);
                    yield below != null && below.block().equals(state.block()) && below.part() == PlantState.Part.LOWER;
                }
                final TreeGround below = ground(x, y - 1, z);
                yield below != null && PlantType.dryPlantableOn(below);
            }
            case CREEPING, CREEPING_STONE, OCEAN_CREEPING, OCEAN_ROCK_CREEPING -> creepingFaces(state, x, y, z) != 0;
            case EPIPHYTE -> {
                final int d = opposite(state.extra());
                yield epiphytePlantable(ground(x + DX[d], y + DY[d], z + DZ[d]));
            }
            case PERCHED_EPIPHYTE -> epiphytePlantable(ground(x, y - 1, z));
            case OCEAN_ROTATABLE -> {
                final int d = opposite(state.extra());
                yield anemonePlantable(ground(x + DX[d], y + DY[d], z + DZ[d]));
            }
            case KELP, TWISTING, TWISTING_SOLID -> growingSurvives(state, x, y, z, true);
            case KELP_TOP -> state.fluid() != PlantState.Fluid.EMPTY && growingSurvives(state, x, y, z, true);
            case TWISTING_TOP, TWISTING_TOP_SHORT, TWISTING_SOLID_TOP -> growingSurvives(state, x, y, z, true);
            case WEEPING, WEEPING_TOP -> growingSurvives(state, x, y, z, false);
            case KELP_TREE -> state.fluid() != PlantState.Fluid.EMPTY && kelpTreeSurvives(state.block(), x, y, z);
            case KELP_TREE_FLOWER -> state.fluid() != PlantState.Fluid.EMPTY && kelpFlowerSurvives(state.block(), x, y, z);
            case BRANCHING_CACTUS, BRANCHING_CACTUS_TOP -> cactusPipeSurvives(x, y, z);
            default -> false;
        };
    }

    /**
     * {@code CreepingPlantBlock.updateStateFromSides} (and its water version): the faces ({@code 1 << index}) whose neighbour
     * the plant can creep on, in {@code UPDATE_SHAPE_ORDER}; 0 when there is none (the plant becomes air).
     */
    int creepingFaces(PlantState state, int x, int y, int z) {
        int faces = 0;
        for (int d = 0; d < 6; d++) {
            if (canCreepOn(state.type(), x + DX[d], y + DY[d], z + DZ[d])) {
                faces |= 1 << d;
            }
        }
        return faces;
    }

    /** {@code canCreepOn}: a sturdy face of a block in the type's tags. */
    private boolean canCreepOn(PlantType type, int x, int y, int z) {
        final TreeGround g = ground(x, y, z);
        if (g == null || !sturdy(x, y, z)) {
            return false;
        }
        return switch (type) {
            // tfc:creeping_plant_not_plantable_on: packed ice, snow, blue ice, sea ice, powder snow (our SOLID)
            case CREEPING -> g != TreeGround.SOLID;
            // tfc:creeping_stone_plantable_on: stones, smooth stones, cobblestones, sandstones
            case CREEPING_STONE, OCEAN_ROCK_CREEPING -> g == TreeGround.STONE || g == TreeGround.SANDSTONE;
            case OCEAN_CREEPING -> anemonePlantable(g);
            default -> false;
        };
    }

    /** {@code tfc:epiphyte_plantable_on}: logs, raw and hardened stones, raw sandstones. */
    static boolean epiphytePlantable(TreeGround g) {
        return g == TreeGround.LOG || g == TreeGround.STONE || g == TreeGround.SANDSTONE;
    }

    /** {@code tfc:anemone_plantable_on}: stones, cobblestones, gravels, sands, sandstones. */
    static boolean anemonePlantable(TreeGround g) {
        return g == TreeGround.STONE || g == TreeGround.GRAVEL || g == TreeGround.SAND || g == TreeGround.SANDSTONE;
    }

    /**
     * {@code BodyPlantBlock}/{@code TopPlantBlock.canSurvive}: the block behind the growth (below for upward columns) is the
     * same plant's head or body, leaves, or has a sturdy face towards the plant.
     */
    private boolean growingSurvives(PlantState state, int x, int y, int z, boolean up) {
        final int ay = up ? y - 1 : y + 1;
        final PlantState behind = placed(x, ay, z);
        if (behind != null) {
            return behind.kind() == PlantState.Kind.PLANT && samePlant(state.block(), behind.block());
        }
        final TreeGround g = view.ground(x, ay, z);
        return g.isLeaves() || g.sturdyTop();
    }

    /** Whether two ids are the head and body ({@code <name>} and {@code <name>_plant}) of one growing plant, or the same. */
    static boolean samePlant(String a, String b) {
        return a.equals(b) || a.equals(b + "_plant") || b.equals(a + "_plant");
    }

    /** {@code KelpTreeBlock}'s {@code testDown}: {@code tfc:kelp_branches} or {@code tfc:sea_bush_plantable_on}. */
    boolean kelpDown(String body, int x, int y, int z) {
        final PlantState p = placed(x, y, z);
        return p != null ? p.block().equals(body) : view.ground(x, y, z).seaBushPlantableOn();
    }

    /** {@code KelpTreeBlock}'s {@code testHorizontal} and {@code testUp}: {@code tfc:kelp_trees}. */
    boolean kelpTree(String body, int x, int y, int z) {
        return placedIs(x, y, z, body, flowerOf(body));
    }

    static String flowerOf(String body) {
        return body.replace("_plant", "_flower");
    }

    /** {@code PipePlantBlock.canSurvive} of the kelp tree (no long sideways growth). */
    private boolean kelpTreeSurvives(String body, int x, int y, int z) {
        for (int d : HORIZONTAL) {
            if (kelpTree(body, x + DX[d], y, z + DZ[d]) && kelpDown(body, x + DX[d], y - 1, z + DZ[d])) {
                return true;
            }
        }
        return kelpDown(body, x, y - 1, z);
    }

    /** {@code KelpTreeFlowerBlock.canSurvive} for a flower holding fluid. */
    private boolean kelpFlowerSurvives(String flower, int x, int y, int z) {
        final String body = flower.replace("_flower", "_plant");
        final PlantState below = placed(x, y - 1, z);
        if (below != null && below.block().equals(body) || below == null && view.ground(x, y - 1, z).seaBushPlantableOn()) {
            return true;
        }
        if (!saltWaterBlock(x, y - 1, z)) {
            return false;
        }
        boolean valid = false;
        for (int d : HORIZONTAL) {
            if (placedIs(x + DX[d], y, z + DZ[d], body, body)) {
                if (valid) {
                    return false;
                }
                valid = true;
            } else if (!saltWaterBlock(x + DX[d], y, z + DZ[d])) {
                return false;
            }
        }
        return valid;
    }

    /** {@code BranchingCactusBlock}'s {@code testDown}: {@code tfc:dry_plant_plantable_on} or a branching cactus. */
    boolean cactusDown(int x, int y, int z) {
        final PlantState p = placed(x, y, z);
        return p == null ? PlantType.dryPlantableOn(view.ground(x, y, z)) : branchingCactus(x, y, z);
    }

    /** {@code PipePlantBlock.canSurvive} of a branching cactus (it grows long sideways). */
    private boolean cactusPipeSurvives(int x, int y, int z) {
        for (int d : HORIZONTAL) {
            final int nx = x + DX[d];
            final int nz = z + DZ[d];
            if (branchingCactus(nx, y, nz)) {
                if (cactusDown(nx, y - 1, nz) || branchingCactus(nx + DX[d], y, nz + DZ[d])) {
                    return true;
                }
            }
        }
        return cactusDown(x, y - 1, z);
    }
}
