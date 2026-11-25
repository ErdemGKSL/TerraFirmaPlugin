/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.sapling;

import dev.tfp.core.worldgen.tree.BranchDirection;
import dev.tfp.core.worldgen.tree.TreeBlock;
import java.util.Locale;
import java.util.Optional;

/** The CraftEngine block of a tree block, as a state reference string ({@code tfp:wood/log/oak[axis=y]}). Pure. */
public final class SaplingTreeRefs {
    private SaplingTreeRefs() {}

    /**
     * The reference of the block a tree places, or empty for kinds that have no CraftEngine block yet (roots, krummholz): those
     * are skipped, a divergence the ledger records.
     */
    public static Optional<String> ref(TreeBlock block) {
        final String name = block.wood().serializedName();
        return switch (block.kind()) {
            case LOG -> Optional.of("tfp:wood/log/" + name + properties(block));
            case WOOD -> Optional.of("tfp:wood/wood/" + name + properties(block));
            case LEAVES -> Optional.of("tfp:wood/leaves/" + name);
            default -> Optional.empty();
        };
    }

    /** {@code [axis=y,branch_direction=down]}: the natural direction TFC's tree templates set (PAR-GATHER-09); {@code none} is the default. */
    private static String properties(TreeBlock block) {
        String axis = "axis=" + block.axis().name().toLowerCase(Locale.ROOT);
        return block.branch() == BranchDirection.NONE ? "[" + axis + "]" : "[" + axis + ",branch_direction=" + block.branch().serializedName() + "]";
    }
}
