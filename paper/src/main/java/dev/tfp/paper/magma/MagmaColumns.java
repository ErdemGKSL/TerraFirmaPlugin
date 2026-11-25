/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/TFCBubbleColumnBlock.java @ c3b4d9536 (updateColumnForFluid, canExistIn)
 *       src/main/java/net/dries007/tfc/common/blocks/TFCMagmaBlock.java @ c3b4d9536 (tick)
 */
package dev.tfp.paper.magma;

import dev.tfp.core.world.BlockPos;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.pipeline.BlockChange;
import java.util.ArrayList;
import java.util.List;

/**
 * PAR-WORLD-54: TFC's drag-down bubble column above a magma block ({@code TFCBubbleColumnBlock.updateColumnForFluid}), as a
 * pure decision over a snapshot of the column. Here the column is vanilla's {@code bubble_column[drag=true]}: TFC's three
 * column blocks (fresh, salt, spring water) differ only in their fluid, and all our water is vanilla water.
 */
public final class MagmaColumns {
    /** The column TFC places: drag down. */
    public static final BlockStateRef DRAG_COLUMN = BlockStateRef.parse("minecraft:bubble_column[drag=true]");
    static final String BUBBLE_COLUMN = "minecraft:bubble_column";
    static final String WATER = "minecraft:water";

    private MagmaColumns() {}

    /**
     * A snapshot: the block at {@code base} and the blocks above it, bottom up, read until the first one a column cannot
     * exist in (that one included, or the build limit).
     */
    public record Column(BlockPos base, BlockStateRef baseState, List<BlockStateRef> above, long nanos) {
        public Column {
            above = List.copyOf(above);
        }
    }

    /**
     * TFC's {@code canExistIn}: a bubble column already, or a fluid-only block (not air) of a fluid with a column, which is
     * the water source ({@code Fluids.WATER.getSource()}; flowing water and waterlogged blocks are not).
     */
    public static boolean canExistIn(BlockStateRef state) {
        if (state == null) {
            return false;
        }
        if (BUBBLE_COLUMN.equals(state.id())) {
            return true;
        }
        return WATER.equals(state.id()) && "0".equals(state.properties().getOrDefault("level", "0"));
    }

    /**
     * TFC's {@code updateColumnForFluid}: nothing unless the base is magma; then from the block above it upwards, every block a
     * column can exist in becomes a drag-down column (blocks that already are a column are left as they are, TFC's
     * {@code Helpers.isBlock} test), stopping at the first other block or magma. TFC's branch that turns a column at the
     * stop back into fluid cannot run (a column always passes {@code canExistIn}), so it has no counterpart.
     *
     * @return the changes, bottom up, each expecting the snapshot's state
     */
    public static List<BlockChange<BlockStateRef>> decide(Column c) {
        if (c.baseState() == null || !MagmaRules.isMagma(c.baseState().id())) {
            return List.of();
        }
        final List<BlockChange<BlockStateRef>> out = new ArrayList<>();
        for (int i = 0; i < c.above().size(); i++) {
            final BlockStateRef state = c.above().get(i);
            if (!canExistIn(state) || MagmaRules.isMagma(state.id())) {
                break;
            }
            if (!BUBBLE_COLUMN.equals(state.id())) {
                out.add(new BlockChange<>(c.base().offset(0, i + 1, 0), state, DRAG_COLUMN, c.nanos()));
            }
        }
        return out;
    }
}
