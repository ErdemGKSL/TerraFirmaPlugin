/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/plant/KelpTreeFlowerBlock.java @ c3b4d9536 (randomTick, canSurvive,
 *       placeGrownFlower, placeDeadFlower, setBodyBlockWithFluid),
 *       src/main/java/net/dries007/tfc/common/blocks/plant/KelpTreeBlock.java @ c3b4d9536 (testDown, testUp, testHorizontal),
 *       src/main/java/net/dries007/tfc/common/blocks/plant/PipePlantBlock.java @ c3b4d9536 (getStateForPlacement, canSurvive)
 */
package dev.tfp.paper.plant.growth;

import dev.tfp.core.plant.PlantGrowth;
import dev.tfp.paper.integration.BlockStateRef;
import java.util.Optional;
import java.util.random.RandomGenerator;

/**
 * PAR-FARM-29: the giant kelp tree. The flower is the growing tip ({@code KelpTreeFlowerBlock.randomTick}): it turns into a body
 * block and grows a new flower above (or sideways branches), up to age 5. The body ({@code KelpTreeBlock}) is a pipe plant that
 * connects to the kelp tree blocks around it and stands on the sea floor or a kelp branch. Pure over a {@link GrowthWorld}.
 */
public final class KelpTreeGrowth {
    public static final String BODY = "tfp:plant/giant_kelp_plant";
    public static final String FLOWER = "tfp:plant/giant_kelp_flower";
    static final String SEA_BUSH_PLANTABLE_ON = "tfc:sea_bush_plantable_on";
    static final String KELP_BRANCHES = "tfc:kelp_branches";
    static final String KELP_TREES = "tfc:kelp_trees";
    /** {@code KelpTreeFlowerBlock.AGE}: {@code AGE_5}. */
    static final int MAX_AGE = PlantGrowth.KELP_FLOWER_MAX_AGE;

    private KelpTreeGrowth() {
    }

    /** One random tick of the flower at the position, whose state is {@code flower}. Returns whether a block was written. */
    public static boolean tick(GrowthWorld w, int x, int y, int z, BlockStateRef flower, RandomGenerator random) {
        String fluid = flower.properties().getOrDefault("fluid", "empty");
        boolean waterAbove = w.water(x, y + 1, z);
        boolean belowMax = y + 1 < w.maxY();
        if (!PlantGrowth.kelpTreeGrows(waterAbove, belowMax, PlantGrowth.LONG_GROWTH_CHANCE, random)) {
            return false;
        }
        int age = intProp(flower, "age");
        if (age >= MAX_AGE) {
            return false;
        }
        boolean placeNewBody = false;
        boolean groundFurtherDown = false;
        boolean belowIsGround = w.tagged(x, y - 1, z, SEA_BUSH_PLANTABLE_ON);
        boolean belowIsBody = BODY.equals(w.id(x, y - 1, z));
        if (belowIsGround) {
            placeNewBody = true;
        } else if (belowIsBody) {
            int j = 1;
            for (int k = 0; k < 4; k++) {
                int by = y - (j + 1);
                if (!BODY.equals(w.id(x, by, z))) {
                    if (w.tagged(x, by, z, SEA_BUSH_PLANTABLE_ON)) {
                        groundFurtherDown = true;
                    }
                    break;
                }
                j++;
            }
            if (j < 2 || j <= random.nextInt(groundFurtherDown ? 5 : 4)) {
                placeNewBody = true;
            }
        } else if (w.water(x, y - 1, z)) {
            placeNewBody = true;
        }
        boolean wrote = false;
        if (placeNewBody && allNeighborsEmpty(w, x, y + 1, z, null) && w.water(x, y + 2, z)) {
            wrote |= setBody(w, x, y, z, fluid);
            wrote |= placeGrownFlower(w, x, y + 1, z, age, Face.UP);
        } else if (age < 4) {
            int l = random.nextInt(4);
            if (groundFurtherDown) {
                l++;
            }
            boolean foundValidGrowthSpace = false;
            for (int i = 0; i < l; i++) {
                Face direction = Face.randomHorizontal(random);
                int rx = x + direction.dx;
                int rz = z + direction.dz;
                if (w.water(rx, y, rz) && w.water(rx, y - 1, rz) && allNeighborsEmpty(w, rx, y, rz, direction.opposite())) {
                    wrote |= placeGrownFlower(w, rx, y, rz, age + 1, direction);
                    foundValidGrowthSpace = true;
                }
            }
            if (foundValidGrowthSpace) {
                wrote |= setBody(w, x, y, z, fluid);
            } else {
                wrote |= placeDeadFlower(w, x, y, z, flower);
            }
        } else {
            wrote |= placeDeadFlower(w, x, y, z, flower);
        }
        return wrote;
    }

    static boolean allNeighborsEmpty(GrowthWorld w, int x, int y, int z, Face excluding) {
        for (Face face : Face.HORIZONTAL) {
            if (face != excluding && !w.water(x + face.dx, y, z + face.dz)) {
                return false;
            }
        }
        return true;
    }

    /** {@code placeGrownFlower}: written only when the flower would survive and the position holds water. */
    static boolean placeGrownFlower(GrowthWorld w, int x, int y, int z, int age, Face facing) {
        if (!w.water(x, y, z)) {
            return false;
        }
        BlockStateRef state = BlockStateRef.of(FLOWER).with("fluid", "salt_water").with("age", Integer.toString(age))
            .with("facing", facing.key);
        return flowerSurvives(w, x, y, z, state) && w.set(x, y, z, state);
    }

    /** {@code placeDeadFlower}: the flower at the position becomes age 5 (the position holds the flower, so its fluid is kept). */
    static boolean placeDeadFlower(GrowthWorld w, int x, int y, int z, BlockStateRef flower) {
        BlockStateRef state = flower.with("age", Integer.toString(MAX_AGE));
        return flowerSurvives(w, x, y, z, state) && w.set(x, y, z, state);
    }

    /** {@code setBodyBlockWithFluid}: the body with the connections of {@link #bodyAt}. */
    static boolean setBody(GrowthWorld w, int x, int y, int z, String fluid) {
        return w.set(x, y, z, bodyAt(w, x, y, z, fluid));
    }

    /** {@code PipePlantBlock.getStateForPlacement}: the connections to the blocks around. */
    public static BlockStateRef bodyAt(GrowthWorld w, int x, int y, int z, String fluid) {
        BlockStateRef body = BlockStateRef.of(BODY).with("fluid", fluid);
        for (Face face : Face.values()) {
            body = body.with(face.key, Boolean.toString(connects(w, x + face.dx, y + face.dy, z + face.dz, face)));
        }
        return body;
    }

    /** {@code KelpTreeBlock.testDown} for down, {@code testHorizontal} (the kelp tree tag) for the other five faces. */
    static boolean connects(GrowthWorld w, int x, int y, int z, Face face) {
        return face == Face.DOWN ? testDown(w, x, y, z) : w.tagged(x, y, z, KELP_TREES);
    }

    static boolean testDown(GrowthWorld w, int x, int y, int z) {
        return w.tagged(x, y, z, KELP_BRANCHES) || w.tagged(x, y, z, SEA_BUSH_PLANTABLE_ON);
    }

    /** {@code KelpTreeFlowerBlock.canSurvive}. */
    public static boolean flowerSurvives(GrowthWorld w, int x, int y, int z, BlockStateRef state) {
        if ("empty".equals(state.properties().getOrDefault("fluid", "empty"))) {
            return false;
        }
        if (BODY.equals(w.id(x, y - 1, z)) || w.tagged(x, y - 1, z, SEA_BUSH_PLANTABLE_ON)) {
            return true;
        }
        if (!w.water(x, y - 1, z)) {
            return false;
        }
        boolean valid = false;
        for (Face face : Face.HORIZONTAL) {
            int nx = x + face.dx;
            int nz = z + face.dz;
            if (BODY.equals(w.id(nx, y, nz))) {
                if (valid) {
                    return false;
                }
                valid = true;
            } else if (!w.water(nx, y, nz)) {
                return false;
            }
        }
        return valid;
    }

    /** {@code PipePlantBlock.canSurvive} of the body (no long sideways), which also needs water in the block. */
    public static boolean bodySurvives(GrowthWorld w, int x, int y, int z, BlockStateRef state) {
        return !"empty".equals(state.properties().getOrDefault("fluid", "empty")) && PipePlants.survives(w, x, y, z, false,
            KelpTreeGrowth::testDown, (world, hx, hy, hz) -> world.tagged(hx, hy, hz, KELP_TREES));
    }

    static int intProp(BlockStateRef state, String name) {
        try {
            return Integer.parseInt(state.properties().getOrDefault(name, "0"));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** The flower state at the position, when it is the giant kelp flower. */
    public static Optional<BlockStateRef> flowerAt(GrowthWorld w, int x, int y, int z) {
        return w.custom(x, y, z).filter(s -> s.id().equals(FLOWER));
    }
}
