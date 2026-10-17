/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/fluids/FluidHelpers.java @ c3b4d9536 (isAirOrEmptyFluid), with
 * src/main/resources/data/tfc/tags/block/cold_ocean_blocks.json and cancels_flood_fill_lake.json (the block tags the
 * features test), and the vanilla 1.21 block properties they read (isAir, liquid, canBeReplaced, isSolid, the fluid)
 */
package dev.tfp.core.worldgen.volcano;

import dev.tfp.core.worldgen.tree.TreeGround;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The world as one chunk's volcano features see it: the terrain of a {@link VolcanoView} with the blocks the features
 * placed on top, each position once with the last block placed there, in first-placed order. The heightmaps are the
 * terrain's (blocks placed here do not move them). Confined to one decision.
 */
final class VolcanoLevel {
    /** The fluid of a position, as the flood fill tests it: none, {@code minecraft:water}, or another fluid. */
    enum Fluid { EMPTY, WATER, OTHER }

    private final VolcanoView view;
    private final Map<Long, VolcanoBlock> placed = new LinkedHashMap<>();

    VolcanoLevel(VolcanoView view) {
        this.view = view;
    }

    VolcanoView view() {
        return view;
    }

    static long key(int x, int y, int z) {
        return ((long) x & 0x3FFFFFFL) << 38 | ((long) z & 0x3FFFFFFL) << 12 | (y & 0xFFFL);
    }

    void set(int x, int y, int z, VolcanoKind kind, int rock, Gem gem) {
        placed.put(key(x, y, z), new VolcanoBlock(x, y, z, kind, kind.hasRock() ? rock : -1, gem));
    }

    void set(int x, int y, int z, VolcanoKind kind) {
        set(x, y, z, kind, -1, null);
    }

    List<VolcanoBlock> blocks() {
        return new ArrayList<>(placed.values());
    }

    private VolcanoBlock placed(int x, int y, int z) {
        return placed.get(key(x, y, z));
    }

    /** {@code BlockState.isAir} (and NeoForge's {@code isEmpty}, which is true for the air blocks). */
    boolean isAir(int x, int y, int z) {
        final VolcanoBlock b = placed(x, y, z);
        return b != null ? b.kind() == VolcanoKind.AIR : view.ground(x, y, z).isAir();
    }

    /** {@code FluidHelpers.isAirOrEmptyFluid}: air or a fluid's own source block. */
    boolean isAirOrEmptyFluid(int x, int y, int z) {
        final VolcanoBlock b = placed(x, y, z);
        return b != null ? b.kind() == VolcanoKind.AIR || b.kind().isFluid() : view.ground(x, y, z).isAirOrEmptyFluid();
    }

    /** {@code BlockState.liquid}: a fluid block. */
    boolean liquid(int x, int y, int z) {
        final VolcanoBlock b = placed(x, y, z);
        return b != null ? b.kind().isFluid() : view.ground(x, y, z).hasFluid();
    }

    /** {@code BlockState.canBeReplaced}: air and the fluid blocks (no plant is in the terrain these features see). */
    boolean canBeReplaced(int x, int y, int z) {
        return isAirOrEmptyFluid(x, y, z);
    }

    /** {@code BlockState.isSolid}: everything but air and fluids (the terrain has no plants). */
    boolean isSolid(int x, int y, int z) {
        return !isAirOrEmptyFluid(x, y, z);
    }

    /** {@code getBlock() instanceof ILeavesBlock}. */
    boolean isLeaves(int x, int y, int z) {
        final VolcanoBlock b = placed(x, y, z);
        return b == null && view.ground(x, y, z).isLeaves();
    }

    /** The fluid of the block at a position. */
    Fluid fluid(int x, int y, int z) {
        final VolcanoBlock b = placed(x, y, z);
        if (b != null) {
            return b.kind().isFluid() ? Fluid.OTHER : Fluid.EMPTY;
        }
        final TreeGround g = view.ground(x, y, z);
        return g == TreeGround.WATER ? Fluid.WATER : g == TreeGround.SALT_WATER ? Fluid.OTHER : Fluid.EMPTY;
    }

    /** {@code tfc:cold_ocean_blocks} (powder snow and sea ice): the terrain holds neither. */
    boolean isColdOceanBlock(int x, int y, int z) {
        return false;
    }

    /** {@code tfc:cancels_flood_fill_lake} (bamboo): never in the terrain. */
    boolean cancelsFloodFill(int x, int y, int z) {
        return false;
    }

    /** {@code IGrassBlock}: TFC's grass, which a caldera turns into its dirt. */
    boolean isGrass(int x, int y, int z) {
        return placed(x, y, z) == null && view.ground(x, y, z) == TreeGround.GRASS;
    }

    /** The rock index of the raw rock block at a position ({@code tfc:rock/raw/<rock>}), or -1 for any other block. */
    int rawRock(int x, int y, int z) {
        final VolcanoBlock b = placed(x, y, z);
        if (b != null) {
            return b.kind() == VolcanoKind.RAW_ROCK ? b.rock() : -1;
        }
        return view.ground(x, y, z) == TreeGround.STONE ? view.rockAt(x, y, z) : -1;
    }

    /** A vanilla {@code BlockPos.MutableBlockPos}: the features' scratch position, whose last value TFC's code reads. */
    static final class Pos {
        int x;
        int y;
        int z;

        Pos set(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
            return this;
        }

        Pos move(int dx, int dy, int dz) {
            return set(x + dx, y + dy, z + dz);
        }

        Pos setWithOffset(int[] p, int dx, int dy, int dz) {
            return set(p[0] + dx, p[1] + dy, p[2] + dz);
        }

        int[] immutable() {
            return new int[] {x, y, z};
        }
    }
}
