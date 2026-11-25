/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/plant/PipePlantBlock.java @ c3b4d9536 (updateShape)
 */
package dev.tfp.paper.plant.growth;

import dev.tfp.paper.integration.BlockStateRef;
import java.util.Optional;

/**
 * PAR-FARM-29: {@code PipePlantBlock.updateShape}: a pipe plant (giant kelp body, saguaro body and top) keeps one connection
 * property per face, true when the block on that face is one it joins. After a block changes, the pipe plants next to it (and it
 * itself) recompute them.
 */
final class PipeRefresh {
    private PipeRefresh() {
    }

    /** Recomputes the connections of the block at the position and of its six neighbours; writes only real changes. */
    static void around(GrowthWorld w, int x, int y, int z) {
        one(w, x, y, z);
        for (Face face : Face.values()) {
            one(w, x + face.dx, y + face.dy, z + face.dz);
        }
    }

    static void one(GrowthWorld w, int x, int y, int z) {
        Optional<BlockStateRef> state = w.custom(x, y, z);
        if (state.isEmpty()) {
            return;
        }
        BlockStateRef now = state.get();
        BlockStateRef next = connections(w, x, y, z, now);
        if (next != null && !next.equals(now)) {
            w.set(x, y, z, next);
        }
    }

    /** The state with its connections recomputed, or null when the block is not a pipe plant. */
    static BlockStateRef connections(GrowthWorld w, int x, int y, int z, BlockStateRef state) {
        String id = state.id();
        if (CactusGrowth.isCactus(id)) {
            return CactusGrowth.connections(w, x, y, z, state);
        }
        if (id.equals(KelpTreeGrowth.BODY)) {
            return PipePlants.connections(w, x, y, z, state, KelpTreeGrowth::testDown,
                (world, hx, hy, hz) -> world.tagged(hx, hy, hz, KelpTreeGrowth.KELP_TREES),
                (world, hx, hy, hz) -> world.tagged(hx, hy, hz, KelpTreeGrowth.KELP_TREES));
        }
        return null;
    }
}
