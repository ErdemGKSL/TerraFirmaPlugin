/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/plant/TopPlantBlock.java @ c3b4d9536 (canSurvive),
 *       src/main/java/net/dries007/tfc/common/blocks/plant/BodyPlantBlock.java @ c3b4d9536 (canSurvive),
 *       src/main/java/net/dries007/tfc/common/blocks/plant/TFCKelpTopBlock.java @ c3b4d9536 (canSurvive, canAttachTo),
 *       src/main/java/net/dries007/tfc/common/blocks/plant/CreepingPlantBlock.java @ c3b4d9536 (canSurvive, updateShape,
 *       canCreepOn, createStone),
 *       src/main/java/net/dries007/tfc/common/blocks/plant/EpiphytePlantBlock.java @ c3b4d9536 (canSurvive, updateShape),
 *       src/main/java/net/dries007/tfc/common/blocks/plant/KelpTreeBlock.java and KelpTreeFlowerBlock.java @ c3b4d9536 (canSurvive),
 *       src/main/java/net/dries007/tfc/common/blocks/plant/PipePlantBlock.java @ c3b4d9536 (canSurvive, updateShape),
 *       net.minecraft.world.level.block.VineBlock (1.21.1: canSurvive, getUpdatedState)
 */
package dev.tfp.paper.plant.growth;

import dev.tfp.core.worldgen.plant.PlantType;
import dev.tfp.paper.integration.BlockStateRef;
import java.util.Map;
import java.util.Optional;

/**
 * PAR-FARM-29: whether a placed growing-shape plant keeps standing. One pure rule per shape over a {@link GrowthWorld}: the
 * columns and kelp (anchored behind them: the same plant, leaves or a sturdy face), the vines (one supported face at least), the
 * creeping plants (a face to creep on), the epiphytes (a tagged trunk behind them), the giant kelp and the saguaro (the pipe-plant
 * rule). A plant that stays may still have its face or connection properties corrected ({@link Verdict#update}).
 */
public final class ShapeSurvival {
    static final String LEAVES = "minecraft:leaves";
    static final String CREEPING_NOT_ON = "tfc:creeping_plant_not_plantable_on";
    static final String CREEPING_STONE_ON = "tfc:creeping_stone_plantable_on";
    static final String EPIPHYTE_ON = "tfc:epiphyte_plantable_on";
    /** Every block tag the rules ask about (a {@link CubeSnapshot} captures these). */
    public static final java.util.List<String> TAGS = java.util.List.of(LEAVES, CREEPING_NOT_ON, CREEPING_STONE_ON, EPIPHYTE_ON,
        KelpTreeGrowth.SEA_BUSH_PLANTABLE_ON, KelpTreeGrowth.KELP_BRANCHES, KelpTreeGrowth.KELP_TREES, CactusGrowth.DRY_PLANTABLE_ON);
    /** The vanilla blocks the rules ask about by key. */
    public static final java.util.List<String> KEYS = java.util.List.of("minecraft:magma_block");

    /** What to do with a plant. */
    public record Verdict(boolean keep, BlockStateRef update) {
        static final Verdict KEEP = new Verdict(true, null);
        static final Verdict REMOVE = new Verdict(false, null);

        static Verdict update(BlockStateRef state, BlockStateRef current) {
            return state.equals(current) ? KEEP : new Verdict(true, state);
        }
    }

    /** How a column block hangs: its direction and its partner. */
    private record Column(String head, String body, int dy, boolean kelp, boolean isHead) {
    }

    private static final Map<String, Column> COLUMNS = columns();

    private ShapeSurvival() {
    }

    private static Map<String, Column> columns() {
        java.util.LinkedHashMap<String, Column> m = new java.util.LinkedHashMap<>();
        ColumnGrowth.HEADS.forEach((head, kind) -> {
            m.put(head, new Column(head, kind.body(), kind.dy(), kind.water(), true));
            m.put(kind.body(), new Column(head, kind.body(), kind.dy(), kind.water(), false));
        });
        return Map.copyOf(m);
    }

    /** Whether {@link #check} has a rule for the block id. */
    public static boolean handles(String id) {
        return COLUMNS.containsKey(id) || type(id) != null;
    }

    private static PlantType type(String id) {
        PlantType type = PlantType.of(id);
        if (type == null) {
            return null;
        }
        return switch (type) {
            case VINE, CREEPING, CREEPING_STONE, EPIPHYTE, KELP_TREE, KELP_TREE_FLOWER, BRANCHING_CACTUS, BRANCHING_CACTUS_TOP -> type;
            default -> null;
        };
    }

    /** The verdict for the plant at the position whose state is {@code state}; keep when the block has no rule. */
    public static Verdict check(GrowthWorld w, int x, int y, int z, BlockStateRef state) {
        Column column = COLUMNS.get(state.id());
        if (column != null) {
            return column(w, x, y, z, state, column) ? Verdict.KEEP : Verdict.REMOVE;
        }
        PlantType type = type(state.id());
        if (type == null) {
            return Verdict.KEEP;
        }
        return switch (type) {
            case VINE -> vine(w, x, y, z, state);
            case CREEPING, CREEPING_STONE -> creeping(w, x, y, z, state, type == PlantType.CREEPING_STONE);
            case EPIPHYTE -> epiphyte(w, x, y, z, state) ? Verdict.KEEP : Verdict.REMOVE;
            case KELP_TREE -> KelpTreeGrowth.bodySurvives(w, x, y, z, state)
                ? Verdict.update(PipeRefresh.connections(w, x, y, z, state), state) : Verdict.REMOVE;
            case KELP_TREE_FLOWER -> KelpTreeGrowth.flowerSurvives(w, x, y, z, state) ? Verdict.KEEP : Verdict.REMOVE;
            case BRANCHING_CACTUS, BRANCHING_CACTUS_TOP -> CactusGrowth.survives(w, x, y, z)
                ? Verdict.update(PipeRefresh.connections(w, x, y, z, state), state) : Verdict.REMOVE;
            default -> Verdict.KEEP;
        };
    }

    /**
     * {@code TopPlantBlock.canSurvive} / {@code BodyPlantBlock.canSurvive}: the block behind the plant (against its growth direction)
     * is the head or body of the same plant, leaves, or has a sturdy face toward it. A kelp head also needs water in its block
     * and not magma behind it.
     */
    private static boolean column(GrowthWorld w, int x, int y, int z, BlockStateRef state, Column c) {
        if (c.kelp() && c.isHead() && "empty".equals(state.properties().getOrDefault("fluid", "empty"))) {
            return false;
        }
        int ay = y - c.dy();
        if (c.kelp() && c.isHead() && w.is(x, ay, z, "minecraft:magma_block")) {
            return false;
        }
        String id = w.id(x, ay, z);
        if (c.head().equals(id) || c.body().equals(id) || w.tagged(x, ay, z, LEAVES)) {
            return true;
        }
        return w.sturdy(x, ay, z, c.dy() < 0 ? Face.DOWN : Face.UP);
    }

    /** {@code VineBlock.canSurvive}: the faces still supported; none removes the vine. */
    private static Verdict vine(GrowthWorld w, int x, int y, int z, BlockStateRef state) {
        BlockStateRef next = state;
        boolean any = false;
        for (Face face : new Face[] {Face.UP, Face.NORTH, Face.EAST, Face.SOUTH, Face.WEST}) {
            if (!VineGrowth.has(state, face)) {
                continue;
            }
            boolean supported = VineGrowth.canSupportAtFace(w, x, y, z, face);
            next = next.with(face.key, Boolean.toString(supported));
            any |= supported;
        }
        return any ? Verdict.update(next, state) : Verdict.REMOVE;
    }

    /** {@code CreepingPlantBlock}: a face is set when the block on it can be crept on; none removes the plant. */
    private static Verdict creeping(GrowthWorld w, int x, int y, int z, BlockStateRef state, boolean stone) {
        BlockStateRef next = state;
        boolean any = false;
        for (Face face : Face.values()) {
            int nx = x + face.dx;
            int ny = y + face.dy;
            int nz = z + face.dz;
            boolean on = (stone ? w.tagged(nx, ny, nz, CREEPING_STONE_ON) : !w.tagged(nx, ny, nz, CREEPING_NOT_ON))
                && w.sturdy(nx, ny, nz, face.opposite());
            next = next.with(face.key, Boolean.toString(on));
            any |= on;
        }
        return any ? Verdict.update(next, state) : Verdict.REMOVE;
    }

    /** {@code EpiphytePlantBlock.canSurvive}: the block behind it (against its facing) is {@code tfc:epiphyte_plantable_on}. */
    private static boolean epiphyte(GrowthWorld w, int x, int y, int z, BlockStateRef state) {
        Optional<Face> facing = java.util.Arrays.stream(Face.values()).filter(f -> f.key.equals(state.properties().get("facing")))
            .findFirst();
        Face back = facing.orElse(Face.NORTH).opposite();
        return w.tagged(x + back.dx, y, z + back.dz, EPIPHYTE_ON);
    }
}
