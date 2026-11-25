/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.magma;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.world.BlockPos;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.pipeline.BlockChange;
import java.util.List;
import org.junit.jupiter.api.Test;

/** PAR-WORLD-54: TFC's updateColumnForFluid over a snapshot. */
class MagmaColumnsTest {
    static final BlockStateRef MAGMA = BlockStateRef.of("tfp:rock/magma/basalt");
    static final BlockStateRef SOURCE = BlockStateRef.parse("minecraft:water[level=0]");
    static final BlockStateRef FLOWING = BlockStateRef.parse("minecraft:water[level=3]");
    static final BlockStateRef UP = BlockStateRef.parse("minecraft:bubble_column[drag=false]");
    static final BlockStateRef AIR = BlockStateRef.of("minecraft:air");
    static final BlockStateRef WET_SLAB = BlockStateRef.parse("minecraft:stone_slab[type=bottom,waterlogged=true]");
    static final BlockPos BASE = new BlockPos(3, 40, -7);

    static List<BlockChange<BlockStateRef>> decide(BlockStateRef base, BlockStateRef... above) {
        return MagmaColumns.decide(new MagmaColumns.Column(BASE, base, List.of(above), 5L));
    }

    @Test
    void waterSourcesAboveMagmaBecomeDragColumnsUpToTheFirstOtherBlock() {
        List<BlockChange<BlockStateRef>> changes = decide(MAGMA, SOURCE, SOURCE, MagmaColumns.DRAG_COLUMN, SOURCE, AIR);
        assertThat(changes).extracting(BlockChange::pos)
            .containsExactly(BASE.offset(0, 1, 0), BASE.offset(0, 2, 0), BASE.offset(0, 4, 0));
        assertThat(changes).allSatisfy(c -> {
            assertThat(c.next()).isEqualTo(MagmaColumns.DRAG_COLUMN);
            assertThat(c.expectedOld()).isEqualTo(SOURCE);
            assertThat(c.snapshotNanos()).isEqualTo(5L);
        });
    }

    @Test
    void onlyMagmaStartsAColumn() {
        assertThat(decide(BlockStateRef.of("tfp:rock/hardened/basalt"), SOURCE, SOURCE)).isEmpty();
        assertThat(decide(BlockStateRef.of("minecraft:stone"), SOURCE)).isEmpty();
        assertThat(MagmaColumns.decide(new MagmaColumns.Column(BASE, null, List.of(SOURCE), 0L))).isEmpty();
    }

    @Test
    void flowingWaterWaterloggedBlocksAndMagmaStopTheColumn() {
        assertThat(decide(MAGMA, FLOWING, SOURCE)).isEmpty();
        assertThat(decide(MAGMA, WET_SLAB, SOURCE)).isEmpty();
        assertThat(decide(MAGMA, SOURCE, MAGMA, SOURCE)).hasSize(1);
        assertThat(decide(MAGMA)).isEmpty();
    }

    @Test
    void anExistingColumnIsLeftAsItIs() {
        // TFC's Helpers.isBlock test ignores the drag property
        assertThat(decide(MAGMA, UP, SOURCE)).extracting(BlockChange::pos).containsExactly(BASE.offset(0, 2, 0));
        assertThat(MagmaColumns.canExistIn(UP)).isTrue();
        assertThat(MagmaColumns.canExistIn(AIR)).isFalse();
    }
}
