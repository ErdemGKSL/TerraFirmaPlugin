/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.collapse;

import dev.tfp.core.collapse.CollapseCell;
import java.util.Objects;

/**
 * What the collapse pipeline reads and writes at one position (PAR-PHYS-02). A {@link Cell} is the block as the collapse rules
 * see it, read on the owner thread with the live tags and recipes; a {@link Fall} is the decided change, applied by
 * {@link CollapseBlocks}. Value equality: the commit queue drops a change whose cell no longer matches.
 */
public sealed interface CollapseState {
    /**
     * @param id                 block id without properties (the kind the tags, recipes and supports match)
     * @param state              the full state, {@code BlockStateRef} text
     * @param full               a full collision block
     * @param sturdyUp           the upper face is sturdy
     * @param toughness          TFC {@code getBlockToughness}
     * @param unbreakable        destroy speed -1 or structure void
     * @param canStart           {@code #can_start_collapse}
     * @param canTrigger         {@code #can_trigger_collapse}
     * @param notSolidSupporting {@code #not_solid_supporting}
     * @param rubble             the collapse recipe's result state, the state itself for {@code #can_collapse}, null when the
     *                           block does not collapse
     */
    record Cell(String id, String state, boolean full, boolean sturdyUp, int toughness, boolean unbreakable,
            boolean canStart, boolean canTrigger, boolean notSolidSupporting, String rubble) implements CollapseState {
        public static final Cell AIR = new Cell("minecraft:air", "minecraft:air", false, false, 0, false, false, false,
                false, null);
        /** What an unloaded position reads as: a sturdy, unbreakable full block that never collapses. */
        public static final Cell UNLOADED = new Cell(CollapseService.UNLOADED, CollapseService.UNLOADED, true, true, 0, true,
                false, false, false, null);

        public Cell {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(state, "state");
        }

        public CollapseCell toCore() {
            return new CollapseCell(id, full, sturdyUp);
        }
    }

    /** TFC {@code collapseBlock}: break the block below first when {@code breakBelow}, then the block falls as {@code result}. */
    record Fall(String result, boolean breakBelow) implements CollapseState {
        public Fall {
            Objects.requireNonNull(result, "result");
        }
    }
}
