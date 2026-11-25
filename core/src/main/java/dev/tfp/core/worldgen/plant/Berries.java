/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/feature/plant/SpreadingBushFeature.java @ c3b4d9536,
 *       src/main/java/net/dries007/tfc/common/blocks/plant/fruit/FruitBlocks.java @ c3b4d9536 (the stationary and spreading bushes),
 *       src/main/java/net/dries007/tfc/common/blocks/plant/fruit/{StationaryBerryBushBlock,SpreadingBushBlock,SeasonalPlantBlock,WaterloggedBerryBushBlock}.java @ c3b4d9536 (canSurvive),
 *       src/main/java/net/dries007/tfc/util/EnvironmentHelpers.java @ c3b4d9536 (isWorldgenReplaceable)
 */
package dev.tfp.core.worldgen.plant;

import dev.tfp.core.worldgen.random.XoroshiroRandomSource;
import dev.tfp.core.worldgen.tree.TreeGround;
import java.util.Set;

/**
 * The berry bushes the plant features place (PAR-FARM-18, PAR-FARM-19): which TFC plant ids are bushes, their CraftEngine blocks, and
 * {@code SpreadingBushFeature}. TFC names every bush {@code tfc:plant/<species>_bush}; ours are {@code tfp:berry_bush/<species>} (the six
 * stationary bushes), {@code tfp:spreading_bush/<species>} with {@code <species>_cane} (the four spreading ones) and
 * {@code tfp:spreading_bush/cranberry} (the waterlogged one, a stationary bush that holds fresh water).
 */
public final class Berries {
    /** The stationary bushes ({@code FruitBlocks.StationaryBush}). */
    static final Set<String> STATIONARY = Set.of("bunchberry", "cloudberry", "gooseberry", "snowberry", "strawberry", "wintergreen_berry");
    /** The spreading bushes ({@code FruitBlocks.SpreadingBush}). */
    static final Set<String> SPREADING = Set.of("blackberry", "blueberry", "elderberry", "raspberry");
    static final String CRANBERRY = "cranberry";

    private Berries() {
    }

    /** A bush: the block id it is written as and the kind of {@link PlantState}. */
    record Bush(String block, PlantState.Kind kind) {
    }

    /** The bush of a TFC plant id ({@code tfp:plant/blackberry_bush}), or null for any other id. */
    static Bush of(String id) {
        final String prefix = PlantState.NS + "plant/";
        if (!id.startsWith(prefix) || !id.endsWith("_bush")) {
            return null;
        }
        final String species = id.substring(prefix.length(), id.length() - "_bush".length());
        if (STATIONARY.contains(species)) {
            return new Bush(PlantState.NS + "berry_bush/" + species, PlantState.Kind.BERRY_BUSH);
        }
        if (SPREADING.contains(species)) {
            return new Bush(PlantState.NS + "spreading_bush/" + species, PlantState.Kind.SPREADING_BUSH);
        }
        return species.equals(CRANBERRY) ? new Bush(PlantState.NS + "spreading_bush/cranberry", PlantState.Kind.BERRY_BUSH) : null;
    }

    /** The cane block of a spreading bush block ({@code getCane}). */
    static String cane(String bushBlock) {
        return bushBlock + "_cane";
    }

    /** Whether the block is the waterlogged cranberry bush, which holds fresh water. */
    static boolean cranberry(PlantState state) {
        return state.kind() == PlantState.Kind.BERRY_BUSH && state.block().equals(PlantState.NS + "spreading_bush/cranberry");
    }

    /**
     * The bushes' {@code canSurvive} at a position: the stationary ones on {@code tfc:bush_plantable_on} (the cranberry also on
     * {@code tfc:sea_bush_plantable_on}), the spreading ones on {@code tfc:spreading_fruit_grows_on} (= bush_plantable_on) or on a
     * bush of their own block above stage 0.
     */
    static boolean survives(PlantLevel level, PlantState state, int x, int y, int z) {
        final PlantState belowPlaced = level.placed(x, y - 1, z);
        final TreeGround ground = belowPlaced == null ? level.view().ground(x, y - 1, z) : null;
        final boolean onGround = ground != null && (ground.bushPlantableOn() || cranberry(state) && ground.seaBushPlantableOn());
        if (state.kind() == PlantState.Kind.SPREADING_BUSH) {
            return onGround || belowPlaced != null && belowPlaced.block().equals(state.block()) && belowPlaced.age() != 0;
        }
        return onGround;
    }

    /**
     * {@code SpreadingBushFeature.place}: five tries at an offset of {@code [-2, 2]} by {@code [-1, 1]} by {@code [-2, 2]}; where the
     * bush survives and the block below is not a bush of its own block, a column of one to three bushes (the stage counts down to 0 at
     * the top), each with a cane on a random side while the stage is above 0, as long as the cell is replaceable.
     */
    static boolean place(PlantFeature.SpreadingBush feature, PlantLevel level, XoroshiroRandomSource random, int x, int y, int z) {
        final PlantState bush = feature.state().withAge(0);
        final String cane = cane(bush.block());
        boolean any = false;
        for (int tries = 0; tries < 5; tries++) {
            final int cx = x + random.nextInt(3) - random.nextInt(3);
            int cy = y + random.nextInt(2) - random.nextInt(2);
            final int cz = z + random.nextInt(3) - random.nextInt(3);
            final PlantState below = level.placed(cx, cy - 1, cz);
            if (survives(level, bush, cx, cy, cz) && (below == null || !below.block().equals(bush.block()))) {
                final int height = 1 + random.nextInt(3);
                for (int i = 0; i < height; i++) {
                    if (!level.replaceable(cx, cy, cz)) {
                        break;
                    }
                    any = true;
                    final int stage = height - i - 1;
                    level.set(cx, cy, cz, bush.withAge(stage));
                    // Direction.Plane.HORIZONTAL.getRandomDirection: north, east, south, west
                    final int d = PlantLevel.HORIZONTAL[random.nextInt(4)];
                    final int sx = cx + PlantLevel.DX[d];
                    final int sz = cz + PlantLevel.DZ[d];
                    if (stage > 0 && level.replaceable(sx, cy, sz)) {
                        level.set(sx, cy, sz, PlantState.of(cane, PlantState.Kind.SPREADING_CANE, null).withExtra(d));
                    }
                    cy++;
                }
            }
        }
        return any;
    }
}
