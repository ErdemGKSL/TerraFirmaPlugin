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
 * rules of the plant and wild crop blocks (see PlantType and WildCrop)
 */
package dev.tfp.core.worldgen.plant;

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
    private final Map<Long, PlantBlock> placed = new LinkedHashMap<>();

    /**
     * @param view                   the terrain
     * @param singleBlockReplaceable the block ids of {@code tfc:single_block_replaceable}
     * @param snowPileable           the block ids of {@code tfc:can_be_snow_piled}
     */
    PlantLevel(TreeView view, Set<String> singleBlockReplaceable, Set<String> snowPileable) {
        this.view = view;
        this.singleBlockReplaceable = singleBlockReplaceable;
        this.snowPileable = snowPileable;
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
            case CORAL, CORAL_FAN, CORAL_WALL_FAN, SEA_PICKLE -> PlantState.Fluid.SALT_WATER;
            default -> null;
        };
    }

    /** The block's {@code canSurvive} at a position. */
    boolean canSurvive(PlantState state, int x, int y, int z) {
        final PlantState below = placed(x, y - 1, z);
        return switch (state.kind()) {
            case PLANT -> {
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
            default -> false;
        };
    }
}
