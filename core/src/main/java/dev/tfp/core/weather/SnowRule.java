/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/tracker/WeatherHelpers.java @ c3b4d9536
 * (onTickChunk: the per-event accumulation and melting conditions, lines 280-297; handleSnowAccumulation, lines 396-446;
 * placeSnowOrSnowPile and placeSnowOrSnowPileAt, lines 451-491; findOptimalSnowLocation, lines 496-520;
 * findIcicleLocation, lines 525-563; removeSnowAt, lines 568-610; isSnow and isIce, lines 612-620), and
 * src/main/java/net/dries007/tfc/common/blocks/SnowPileBlock.java (canPlaceSnowPile, placeSnowPile, removePileOrSnow),
 * src/main/java/net/dries007/tfc/common/blocks/IcePileBlock.java (placeIcePileOrIce, removeIcePileOrIce),
 * src/main/java/net/dries007/tfc/util/EnvironmentHelpers.java (isAdjacentToNotWater),
 * src/main/java/net/dries007/tfc/util/Helpers.java (hash(long, int, int, int)).
 */
package dev.tfp.core.weather;

import dev.tfp.core.world.BlockPos;
import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * Snow, snow piles, ice, ice piles and icicles at one surface position (M2-WTH-02, M2-WTH-03), as TFC's weather code
 * decides them for one accumulation or melt event.
 *
 * <p>TFC works on chunks: each event takes one surface position of the chunk ({@code getHeightmapPos(MOTION_BLOCKING)},
 * the first position above the highest motion-blocking block) and runs {@code handleSnowAccumulation} there, or melts
 * one snow, ice or icicle block of the chunk. {@link #event} is the condition of the event, {@link #accumulate} is
 * {@code handleSnowAccumulation} with everything it calls, and {@link #melt} is {@code removeSnowAt} at a position this
 * rule picks in the column (TFC picks a random snow block of the chunk instead; see the ledger).
 *
 * <p>The block reads come through a {@link View} of {@link Cell}s, so the rule is platform-free: the glue classifies
 * each block (what TFC's tags and {@code canSurvive} would say) and the rule only reads those classes. A {@code null}
 * cell means the block is not known (an unloaded chunk): the decision is then empty, never a guess.
 *
 * <p>Pure and thread-safe. The randomness is TFC's {@code level.random} draws, in the same order, from the given
 * generator.
 */
public final class SnowRule {
    /** TFC: accumulation needs {@code realTemperature < -2f} (strict). */
    public static final float FREEZE_BELOW = -2f;
    /** TFC: melting needs {@code temperature > 2f} (strict). */
    public static final float MELT_ABOVE = 2f;
    /** Vanilla snow layers: 1 to 8. */
    public static final int MAX_SNOW_LAYERS = 8;
    /** TFC: icicles are attempted on one accumulation in 16 ({@code level.random.nextInt(16) == 0}). */
    public static final int ICICLE_CHANCE = 16;
    /** TFC's salt of the icicle length hash ({@code Helpers.hash(7189237951231L, x, 0, z)}). */
    static final long ICICLE_LENGTH_SALT = 7189237951231L;
    /** No scan limit: TFC's own icicle search. */
    public static final int UNLIMITED = Integer.MAX_VALUE;

    /** {@code Direction.Plane.HORIZONTAL} in Minecraft's order: north, east, south, west. */
    private static final int[][] HORIZONTAL = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};

    private SnowRule() {
    }

    /** Which event a position gets, from TFC's {@code onTickChunk}. */
    public enum Event {
        NONE,
        /** {@code realTemperature < -2} and precipitating: {@code handleSnowAccumulation}. */
        ACCUMULATE,
        /** {@code temperature > 2}: {@code handleSnowMelting}. */
        MELT
    }

    /**
     * TFC's event condition: accumulation when the temperature is below {@value #FREEZE_BELOW} and precipitation falls
     * (with the sky gate left out, as TFC's {@code isPrecipitating}); otherwise melting when it is above
     * {@value #MELT_ABOVE}. A cold dry position gets nothing.
     */
    public static Event event(float temperature, PrecipitationType precipitation) {
        if (temperature < FREEZE_BELOW && precipitation != PrecipitationType.NONE) {
            return Event.ACCUMULATE;
        }
        if (temperature > MELT_ABOVE) {
            return Event.MELT;
        }
        return Event.NONE;
    }

    /** A block as TFC's snow code sees it. */
    public enum Block {
        /** {@code isAir()} (air, cave air, void air). */
        AIR,
        /** A vanilla snow layer ({@link Cell#layers()} gives its count). */
        SNOW,
        /** TFC's snow pile: snow layers over a remembered block. */
        SNOW_PILE,
        /** A block of {@code tfc:can_be_snow_piled} (plants, loose rocks, twigs, fallen leaves, groundcover). */
        SNOW_PILEABLE,
        /** {@code Blocks.WATER} holding a water source ({@code Fluids.WATER}). */
        WATER,
        /** A block of {@code tfc:can_be_ice_piled} holding a water source (a water plant in fresh water). */
        ICE_PILEABLE_WATER,
        /** A block of {@code tfc:can_be_ice_piled} without a water source (the part above the water). */
        ICE_PILEABLE,
        /** Another block holding a water source (waterlogged). It does not freeze. */
        WATERLOGGED,
        /** Any other {@code LiquidBlock}: flowing water, salt water, river water, lava. */
        LIQUID,
        /** {@code Blocks.ICE}. */
        ICE,
        /** TFC's ice pile: ice over a remembered block. */
        ICE_PILE,
        /** TFC's sea ice. Weather never melts it. */
        SEA_ICE,
        /** Other blocks of {@code minecraft:ice} (packed, blue, frosted ice). */
        OTHER_ICE,
        /** TFC's icicle. */
        ICICLE,
        /** Anything else. */
        OTHER
    }

    /**
     * One block, classified.
     *
     * @param block        its class
     * @param layers       snow layers (1 to 8) for {@link Block#SNOW} and {@link Block#SNOW_PILE}, otherwise 0
     * @param waterFluid   its fluid is in {@code tfc:any_infinite_water} (fresh, salt or spring water, source or flowing):
     *                     {@code EnvironmentHelpers.isWaterAt}
     * @param snowSurvives a snow layer (or pile) would survive at this position: vanilla {@code SnowLayerBlock.canSurvive}
     *                     on the block below, with TFC's tags and its single-layer-on-leaves mixin
     * @param sturdyDown   its bottom face is sturdy ({@code isFaceSturdy(DOWN)}): an icicle can hang under it
     * @param holdsAbove   it blocks motion or is a liquid ({@code blocksMotion() || liquid()}): melted ice above becomes water
     * @param sky          the position can see the sky ({@code level.canSeeSky})
     */
    public record Cell(Block block, int layers, boolean waterFluid, boolean snowSurvives, boolean sturdyDown,
            boolean holdsAbove, boolean sky) {
        public Cell {
            if (block == null) {
                throw new NullPointerException("block");
            }
            boolean snow = block == Block.SNOW || block == Block.SNOW_PILE;
            if (snow && (layers < 1 || layers > MAX_SNOW_LAYERS)) {
                throw new IllegalArgumentException("snow layers must be 1 to " + MAX_SNOW_LAYERS + ": " + layers);
            }
            if (!snow && layers != 0) {
                throw new IllegalArgumentException("layers only apply to snow: " + block);
            }
        }
    }

    /** The blocks around the decision. Pure reads of a snapshot. */
    public interface View {
        /** @return the cell at {@code pos}, or {@code null} when it is not known */
        Cell at(BlockPos pos);

        /**
         * @return the {@code MOTION_BLOCKING} heightmap height of the column ({@code level.getHeight}: the first y
         *         above the highest motion-blocking block), or {@link Integer#MIN_VALUE} when it is not known
         */
        int height(int x, int z);
    }

    /** What changes at one position. */
    public enum Kind {
        /** Air becomes a snow layer of one. */
        PLACE_SNOW,
        /** A snow-pileable block becomes a snow pile of one layer that remembers it; with {@link Change#withAbove()},
         * the snow-pileable block above is remembered too and removed. Also sets {@code snowy} on the block below. */
        PLACE_SNOW_PILE,
        /** Water becomes ice. */
        FREEZE,
        /** Water or an ice-pileable block becomes an ice pile that remembers it; with {@link Change#withAbove()}, the
         * ice-pileable block above is remembered too and removed. */
        PLACE_ICE_PILE,
        /** Air becomes an icicle tip. */
        PLACE_ICICLE,
        /** An icicle tip above a new icicle becomes a middle piece ({@code TIP = false}). */
        ICICLE_TO_BASE,
        /** Snow (or a snow pile) loses one layer: {@link Change#layers()} is the new count. */
        SET_SNOW_LAYERS,
        /** The block is removed (a single snow layer, ice with nothing below it, an icicle). */
        REMOVE,
        /** A snow or ice pile gives back the block it remembers; with {@link Change#withAbove()} the remembered block
         * above is put back too (the position above is air). */
        RESTORE_PILE,
        /** Ice becomes water. */
        MELT_TO_WATER,
        /** The icicle above a removed one becomes a tip ({@code TIP = true}). */
        ICICLE_TO_TIP
    }

    /** One decided change at {@code pos}. {@code layers} only for {@link Kind#SET_SNOW_LAYERS}. */
    public record Change(BlockPos pos, Kind kind, int layers, boolean withAbove) {
        static Change of(BlockPos pos, Kind kind) {
            return new Change(pos, kind, 0, false);
        }
    }

    /** Thrown inside a decision when a needed block is not known; the decision is then empty. */
    private static final class Unknown extends RuntimeException {
        private static final Unknown INSTANCE = new Unknown();

        private Unknown() {
            super(null, null, false, false);
        }
    }

    private static Cell at(View v, BlockPos pos) {
        Cell c = v.at(pos);
        if (c == null) {
            throw Unknown.INSTANCE;
        }
        return c;
    }

    /**
     * TFC {@code handleSnowAccumulation(level, surfacePos)}: snow or a snow pile at the surface or one or two below it,
     * otherwise ice or an ice pile on water, then, one time in {@value #ICICLE_CHANCE}, an icicle under an overhang next
     * to the surface.
     *
     * @param surface    the heightmap position ({@code getHeightmapPos(MOTION_BLOCKING)}) of the column
     * @param random     TFC's {@code level.random}
     * @param icicleScan the most positions the icicle search looks at above the surface (TFC: the neighbour's
     *                   heightmap height; {@link #UNLIMITED} keeps TFC's bound only)
     * @return the changes in TFC's order, or nothing when a block it needs is not known
     */
    public static List<Change> accumulate(View v, BlockPos surface, RandomGenerator random, int icicleScan) {
        List<Change> out = new ArrayList<>(2);
        try {
            accumulate(v, surface, random, icicleScan, out);
            return List.copyOf(out);
        } catch (Unknown u) {
            return List.of();
        }
    }

    private static void accumulate(View v, BlockPos surfacePos, RandomGenerator random, int icicleScan, List<Change> out) {
        // Handle up to two block tall plants if they can be piled: three levels deep
        BlockPos groundPos = surfacePos.offset(0, -1, 0);
        BlockPos belowGroundPos = surfacePos.offset(0, -2, 0);
        if (placeSnowOrSnowPile(v, surfacePos, random, out)) {
            return;
        }
        if (placeSnowOrSnowPile(v, groundPos, random, out)) {
            return;
        }
        if (placeSnowOrSnowPile(v, belowGroundPos, random, out)) {
            return;
        }

        // Otherwise an ice pile. Water with one block above it is reached from one level lower; ice and other liquids stop.
        Cell groundState = at(v, groundPos);
        if (isIce(groundState)) {
            return;
        }
        if (!isWaterSource(groundState)) {
            if (isLiquidBlock(groundState)) {
                return;
            }
            groundPos = belowGroundPos;
            groundState = at(v, groundPos);
        }
        placeIcePileOrIce(v, groundPos, groundState, out);

        // Icicles under overhangs, at a lower rate
        if (random.nextInt(ICICLE_CHANCE) == 0) {
            BlockPos iciclePos = findIcicleLocation(v, surfacePos, random, icicleScan);
            if (iciclePos != null) {
                BlockPos posAbove = iciclePos.offset(0, 1, 0);
                Cell stateAbove = at(v, posAbove);
                if (isIceTag(stateAbove)) {
                    return; // TFC also stops on tfc:no_icicle_generation, which no block is in at the pinned commit
                }
                if (stateAbove.block() == Block.ICICLE) {
                    out.add(Change.of(posAbove, Kind.ICICLE_TO_BASE));
                }
                out.add(Change.of(iciclePos, Kind.PLACE_ICICLE));
            }
        }
    }

    /** TFC {@code placeSnowOrSnowPile}: smooth sideways from snow, then the sky gate if it did not move. */
    private static boolean placeSnowOrSnowPile(View v, BlockPos initialPos, RandomGenerator random, List<Change> out) {
        BlockPos pos = findOptimalSnowLocation(v, initialPos, at(v, initialPos), random);
        Cell state = at(v, pos);
        // A sideways move may go under an overhang from a valid position: only the unmoved position needs the sky
        if (initialPos.equals(pos) && !state.sky()) {
            return false;
        }
        return placeSnowOrSnowPileAt(v, pos, state, out);
    }

    /** TFC {@code placeSnowOrSnowPileAt}. Krummholz freezing and cauldron filling are not ported (see the ledger). */
    private static boolean placeSnowOrSnowPileAt(View v, BlockPos pos, Cell state, List<Change> out) {
        if (state.block() == Block.SNOW_PILEABLE && state.snowSurvives()) {
            // SnowPileBlock.placeSnowPile: a snow-pileable block above is absorbed into the pile
            boolean above = at(v, pos.offset(0, 1, 0)).block() == Block.SNOW_PILEABLE;
            out.add(new Change(pos, Kind.PLACE_SNOW_PILE, 0, above));
            return true;
        }
        if (state.block() == Block.AIR && state.snowSurvives()) {
            out.add(Change.of(pos, Kind.PLACE_SNOW));
            return true;
        }
        return false;
    }

    /** TFC {@code findOptimalSnowLocation}: from snow, a random horizontal neighbour that is air or snow-pileable and holds snow. */
    private static BlockPos findOptimalSnowLocation(View v, BlockPos pos, Cell state, RandomGenerator random) {
        BlockPos targetPos = null;
        int found = 0;
        if (isSnow(state)) {
            for (int[] d : HORIZONTAL) {
                BlockPos adjPos = pos.offset(d[0], 0, d[1]);
                Cell adjState = at(v, adjPos);
                if ((adjState.block() == Block.AIR || adjState.block() == Block.SNOW_PILEABLE) && adjState.snowSurvives()) {
                    found++;
                    if (targetPos == null || random.nextInt(found) == 0) {
                        targetPos = adjPos;
                    }
                }
            }
            if (targetPos != null) {
                return targetPos;
            }
        }
        return pos;
    }

    /** TFC {@code IcePileBlock.placeIcePileOrIce(level, groundPos, groundState, false)}. */
    private static void placeIcePileOrIce(View v, BlockPos groundPos, Cell groundState, List<Change> out) {
        boolean icePileAtGround = isIcePileable(groundState);
        if (isWaterSource(groundState) && (icePileAtGround || groundState.block() == Block.WATER)
                && isAdjacentToNotWater(v, groundPos)) {
            boolean icePileAtSurface = isIcePileable(at(v, groundPos.offset(0, 1, 0)));
            if (icePileAtGround || icePileAtSurface) {
                out.add(new Change(groundPos, Kind.PLACE_ICE_PILE, 0, icePileAtSurface));
            } else {
                out.add(Change.of(groundPos, Kind.FREEZE));
            }
        }
    }

    /** TFC {@code EnvironmentHelpers.isAdjacentToNotWater}. */
    private static boolean isAdjacentToNotWater(View v, BlockPos pos) {
        for (int[] d : HORIZONTAL) {
            if (!at(v, pos.offset(d[0], 0, d[1])).waterFluid()) {
                return true;
            }
        }
        return false;
    }

    /**
     * TFC {@code findIcicleLocation}: in a random horizontal neighbour column, from the surface's height up, the air
     * positions under an icicle or a sturdy bottom face; one of them at random, unless the icicle there would pass the
     * length of this column (1 + hash % 3 blocks, which the remainder of a negative hash makes 0 or -1 as in TFC).
     */
    private static BlockPos findIcicleLocation(View v, BlockPos pos, RandomGenerator random, int icicleScan) {
        int[] side = HORIZONTAL[random.nextInt(HORIZONTAL.length)];
        BlockPos adjacentPos = pos.offset(side[0], 0, side[1]);
        int adjacentHeight = v.height(adjacentPos.x(), adjacentPos.z());
        if (adjacentHeight == Integer.MIN_VALUE) {
            throw Unknown.INSTANCE;
        }
        BlockPos foundPos = null;
        int found = 0;
        for (int y = 0; y < adjacentHeight && y < icicleScan; y++) {
            Cell stateAt = at(v, adjacentPos);
            BlockPos posAbove = adjacentPos.offset(0, 1, 0);
            Cell stateAbove = at(v, posAbove);
            if (stateAt.block() == Block.AIR && (stateAbove.block() == Block.ICICLE || stateAbove.sturdyDown())) {
                found++;
                if (foundPos == null || random.nextInt(found) == 0) {
                    foundPos = adjacentPos;
                }
            }
            adjacentPos = posAbove;
        }
        if (foundPos == null) {
            return null;
        }
        int maxLength = 1 + (hash(ICICLE_LENGTH_SALT, pos.x(), 0, pos.z()) % 3);
        if (at(v, foundPos.offset(0, maxLength, 0)).block() == Block.ICICLE) {
            return null;
        }
        return foundPos;
    }

    /**
     * The melt of one column: TFC's {@code removeSnowAt} at the first of the surface snow (or snow pile), the ice at the
     * top (ice, ice pile or sea ice), or the first icicle hanging below the top within {@code icicleDepth} blocks.
     *
     * @param surface the heightmap position of the column
     * @return the changes, or nothing when nothing melts here or a needed block is not known
     */
    public static List<Change> melt(View v, BlockPos surface, int icicleDepth) {
        try {
            Cell surfaceState = at(v, surface);
            if (isSnow(surfaceState)) {
                return removeSnowAt(v, surface);
            }
            BlockPos top = surface.offset(0, -1, 0);
            Cell topState = at(v, top);
            if (isIce(topState)) {
                return removeSnowAt(v, top);
            }
            for (int i = 1; i <= icicleDepth; i++) {
                BlockPos p = top.offset(0, -i, 0);
                if (at(v, p).block() == Block.ICICLE) {
                    return removeSnowAt(v, p);
                }
            }
            return List.of();
        } catch (Unknown u) {
            return List.of();
        }
    }

    /**
     * TFC {@code removeSnowAt}: one snow layer, a pile back to its block, ice to water or air, or the lowest icicle of
     * the hanging column (the one above it becomes the tip). Krummholz is not ported.
     *
     * @return the changes, or nothing when the block does not melt or a needed block is not known
     */
    public static List<Change> removeSnowAt(View v, BlockPos pos) {
        try {
            Cell state = at(v, pos);
            if (isSnow(state)) {
                return removePileOrSnow(v, pos, state);
            }
            if (isIce(state)) {
                return removeIcePileOrIce(v, pos, state);
            }
            if (state.block() == Block.ICICLE) {
                BlockPos cursor = pos.offset(0, -1, 0);
                while (at(v, cursor).block() == Block.ICICLE) {
                    cursor = cursor.offset(0, -1, 0);
                }
                BlockPos lowest = cursor.offset(0, 1, 0);
                BlockPos above = lowest.offset(0, 1, 0);
                if (at(v, above).block() == Block.ICICLE) {
                    return List.of(Change.of(lowest, Kind.REMOVE), Change.of(above, Kind.ICICLE_TO_TIP));
                }
                return List.of(Change.of(lowest, Kind.REMOVE));
            }
            return List.of();
        } catch (Unknown u) {
            return List.of();
        }
    }

    /** TFC {@code SnowPileBlock.removePileOrSnow}: a layer above one goes; a single vanilla layer goes; a pile of one gives its block back. */
    private static List<Change> removePileOrSnow(View v, BlockPos pos, Cell state) {
        int layers = state.layers();
        if (layers > 1) {
            return List.of(new Change(pos, Kind.SET_SNOW_LAYERS, layers - 1, false));
        }
        if (state.block() == Block.SNOW) {
            return List.of(Change.of(pos, Kind.REMOVE));
        }
        boolean aboveEmpty = at(v, pos.offset(0, 1, 0)).block() == Block.AIR;
        return List.of(new Change(pos, Kind.RESTORE_PILE, 0, aboveEmpty));
    }

    /**
     * TFC {@code IcePileBlock.removeIcePileOrIce} (overworld, not ultrawarm): held from below, ice becomes water and an
     * ice pile gives its blocks back; otherwise ice and ice piles are removed. Sea ice takes the first branch (it is not
     * {@code Blocks.ICE} and has no pile to give back) and so never melts here, as in TFC.
     */
    private static List<Change> removeIcePileOrIce(View v, BlockPos pos, Cell state) {
        Cell belowState = at(v, pos.offset(0, -1, 0));
        if (belowState.holdsAbove()) {
            if (state.block() == Block.ICE) {
                return List.of(Change.of(pos, Kind.MELT_TO_WATER));
            }
            if (state.block() == Block.ICE_PILE) {
                boolean aboveEmpty = at(v, pos.offset(0, 1, 0)).block() == Block.AIR;
                return List.of(new Change(pos, Kind.RESTORE_PILE, 0, aboveEmpty));
            }
            return List.of();
        }
        if (state.block() == Block.ICE || state.block() == Block.ICE_PILE) {
            return List.of(Change.of(pos, Kind.REMOVE));
        }
        return List.of();
    }

    /** TFC {@code WeatherHelpers.isSnow}: snow layers or a snow pile. */
    public static boolean isSnow(Cell c) {
        return c.block() == Block.SNOW || c.block() == Block.SNOW_PILE;
    }

    /** TFC {@code WeatherHelpers.isIce}: ice, an ice pile or sea ice. */
    public static boolean isIce(Cell c) {
        return c.block() == Block.ICE || c.block() == Block.ICE_PILE || c.block() == Block.SEA_ICE;
    }

    /** {@code minecraft:ice} with TFC's additions (sea ice, ice pile). */
    private static boolean isIceTag(Cell c) {
        return isIce(c) || c.block() == Block.OTHER_ICE;
    }

    private static boolean isIcePileable(Cell c) {
        return c.block() == Block.ICE_PILEABLE || c.block() == Block.ICE_PILEABLE_WATER;
    }

    /** {@code getFluidState().getType() == Fluids.WATER}: a fresh water source. */
    private static boolean isWaterSource(Cell c) {
        return c.block() == Block.WATER || c.block() == Block.ICE_PILEABLE_WATER || c.block() == Block.WATERLOGGED;
    }

    private static boolean isLiquidBlock(Cell c) {
        return c.block() == Block.WATER || c.block() == Block.LIQUID;
    }

    /** TFC {@code Helpers.hash(long salt, int x, int y, int z)}. */
    static int hash(long salt, int x, int y, int z) {
        long hash = salt ^ ((long) x * 501125321) ^ ((long) y * 1136930381) ^ z;
        hash *= 0x27d4eb2d;
        return (int) hash;
    }
}
