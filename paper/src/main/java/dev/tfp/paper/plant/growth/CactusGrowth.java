/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/plant/GrowingBranchingCactusBlock.java @ c3b4d9536 (randomTick, growthTick,
 *       ensureNeighborhoodForBranch, canGrowInto, tryFruit),
 *       src/main/java/net/dries007/tfc/common/blocks/plant/BranchingCactusBlock.java @ c3b4d9536 (testDown, testUp,
 *       testHorizontal, canGrowLongSideways),
 *       src/main/java/net/dries007/tfc/common/blocks/plant/PipePlantBlock.java @ c3b4d9536 (getStateForPlacement)
 */
package dev.tfp.paper.plant.growth;

import dev.tfp.core.plant.PlantGrowth;
import dev.tfp.paper.integration.BlockStateRef;
import java.util.Optional;
import java.util.random.RandomGenerator;

/**
 * PAR-FARM-29: the saguaro ({@code GrowingBranchingCactusBlock}). The top block grows the cactus up one block per growth tick and
 * sometimes a side arm, and turns into a body block at the height limit (7 for the trunk, 3 for an arm); the arms end in a top
 * with {@code grows_branches} false. Pure over a {@link GrowthWorld}.
 */
public final class CactusGrowth {
    public static final String TOP = "tfp:plant/saguaro";
    public static final String BODY = "tfp:plant/saguaro_plant";
    /** The fruit {@code tryFruit} places on top ({@code SAGUARO_FRUIT}); datagen writes no block for it yet, so none is placed. */
    public static final String FRUIT = "tfp:plant/saguaro_fruit";
    static final String DRY_PLANTABLE_ON = "tfc:dry_plant_plantable_on";

    private CactusGrowth() {
    }

    /** {@code BranchingCactusBlock}: the saguaro body or top. */
    public static boolean isCactus(String id) {
        return TOP.equals(id) || BODY.equals(id);
    }

    static boolean cactusAt(GrowthWorld w, int x, int y, int z) {
        return isCactus(w.id(x, y, z));
    }

    /** {@code GrowingBranchingCactusBlock.randomTick}: {@code nextFloat() > plantGrowthChance} runs {@link #growthTick}. */
    public static boolean tick(GrowthWorld w, int x, int y, int z, BlockStateRef top, RandomGenerator random) {
        if (!PlantGrowth.branchingCactusGrows(PlantGrowth.GROWTH_CHANCE, random)) {
            return false;
        }
        return growthTick(w, x, y, z, top, random);
    }

    /** {@code growthTick}: returns whether any block was written. */
    public static boolean growthTick(GrowthWorld w, int x, int y, int z, BlockStateRef state, RandomGenerator random) {
        boolean growsBranches = "true".equals(state.properties().getOrDefault("grows_branches", "false"));
        if (cactusAt(w, x, y + 1, z)) {
            return w.set(x, y, z, body(state));
        }
        if (growsBranches || random.nextFloat() < 0.1f) {
            if (cactusAt(w, x, y - 7, z)) {
                boolean wrote = w.set(x, y, z, body(state));
                tryFruit(w, x, y + 1, z);
                return wrote;
            }
        } else {
            if (cactusAt(w, x, y - 3, z) || random.nextFloat() < 0.5f) {
                boolean wrote = w.set(x, y, z, body(state));
                tryFruit(w, x, y + 1, z);
                return wrote;
            }
        }
        Face dir = Face.randomHorizontal(random);
        boolean willBranch = random.nextFloat() < 0.7f && growsBranches;
        if (willBranch) {
            willBranch = ensureNeighborhoodForBranch(w, x, y, z, dir);
        }
        boolean wrote = w.set(x, y + 1, z, topAt(w, x, y + 1, z, growsBranches));
        if (TOP.equals(w.id(x, y, z))) {
            wrote |= w.set(x, y, z, body(w.custom(x, y, z).orElse(state)));
        }
        if (willBranch) {
            int bx = x + dir.dx;
            int bz = z + dir.dz;
            int ex = bx + dir.dx;
            int ez = bz + dir.dz;
            // TFC's cursor is one above the trunk top here: it tests (dir, +1), (2 dir, +1), (2 dir, +2) and places at +0
            if (canGrowInto(w, bx, y + 1, bz, dir) && canGrowInto(w, ex, y + 1, ez, dir) && canGrowInto(w, ex, y + 2, ez, Face.UP)) {
                wrote |= w.set(bx, y, bz, bodyAt(w, bx, y, bz));
                wrote |= w.set(ex, y, ez, topAt(w, ex, y, ez, false).with(Face.UP.key, "true"));
            }
        }
        return wrote;
    }

    /** {@code ensureNeighborhoodForBranch}: no branch when the trunk is not yet above the ground or other cactus is near. */
    static boolean ensureNeighborhoodForBranch(GrowthWorld w, int x, int y, int z, Face dir) {
        boolean willBranch = true;
        if (!cactusAt(w, x, y - 1, z)) {
            willBranch = false;
        }
        for (int i = 1; i <= 2; i++) {
            if (cactusAt(w, x + dir.dx, y - i, z + dir.dz)) {
                willBranch = false;
            }
        }
        for (int i = 1; i <= 3; i++) {
            if (cactusAt(w, x + dir.dx, y + i, z + dir.dz)) {
                willBranch = false;
            }
        }
        return willBranch;
    }

    /** {@code canGrowInto}: air with no cactus beside it (except behind it, the direction it comes from). */
    static boolean canGrowInto(GrowthWorld w, int x, int y, int z, Face direction) {
        if (!w.air(x, y, z)) {
            return false;
        }
        for (Face face : Face.values()) {
            int nx = x + face.dx;
            int ny = y + face.dy;
            int nz = z + face.dz;
            if (!w.loaded(nx, ny, nz) || face == direction.opposite()) {
                continue;
            }
            if (cactusAt(w, nx, ny, nz)) {
                return false;
            }
        }
        return true;
    }

    /** {@code tryFruit}: the fruit goes onto a replaceable block (air) when its block exists. */
    static void tryFruit(GrowthWorld w, int x, int y, int z) {
        if (w.air(x, y, z)) {
            w.set(x, y, z, BlockStateRef.of(FRUIT));
        }
    }

    /** {@code Helpers.copyProperties(body.defaultState, state)}: the body keeps the six connections of the top. */
    static BlockStateRef body(BlockStateRef top) {
        BlockStateRef body = BlockStateRef.of(BODY);
        for (Face face : Face.values()) {
            String value = top.properties().get(face.key);
            if (value != null) {
                body = body.with(face.key, value);
            }
        }
        return body;
    }

    /** {@code bodyBlock.getStateForPlacement}: the body with its connections. */
    static BlockStateRef bodyAt(GrowthWorld w, int x, int y, int z) {
        return connections(w, x, y, z, BlockStateRef.of(BODY));
    }

    /** {@code getStateForPlacement(level, pos).setValue(GROWS_BRANCHES, ..)}: a top with its connections. */
    static BlockStateRef topAt(GrowthWorld w, int x, int y, int z, boolean growsBranches) {
        return connections(w, x, y, z, BlockStateRef.of(TOP)).with("grows_branches", Boolean.toString(growsBranches));
    }

    static BlockStateRef connections(GrowthWorld w, int x, int y, int z, BlockStateRef state) {
        return PipePlants.connections(w, x, y, z, state, CactusGrowth::testDown, CactusGrowth::testUp, CactusGrowth::testHorizontal);
    }

    /** {@code BranchingCactusBlock.testDown}: dry plantable ground or a cactus. */
    static boolean testDown(GrowthWorld w, int x, int y, int z) {
        return w.tagged(x, y, z, DRY_PLANTABLE_ON) || cactusAt(w, x, y, z);
    }

    /** {@code testUp}: a cactus or any plant block. */
    static boolean testUp(GrowthWorld w, int x, int y, int z) {
        String id = w.id(x, y, z);
        return id != null && id.startsWith("tfp:plant/");
    }

    static boolean testHorizontal(GrowthWorld w, int x, int y, int z) {
        return cactusAt(w, x, y, z);
    }

    /** {@code BranchingCactusBlock.canSurvive} (a pipe plant that grows long sideways). */
    public static boolean survives(GrowthWorld w, int x, int y, int z) {
        return PipePlants.survives(w, x, y, z, true, CactusGrowth::testDown, CactusGrowth::testHorizontal);
    }

    /** The saguaro top at the position, if it is one. */
    public static Optional<BlockStateRef> topAt(GrowthWorld w, int x, int y, int z) {
        return w.custom(x, y, z).filter(s -> s.id().equals(TOP));
    }
}
