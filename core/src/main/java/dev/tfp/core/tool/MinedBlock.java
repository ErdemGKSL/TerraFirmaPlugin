/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.tool;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * The facts about a block that the tool rules read: the tool types that dig it, the incorrect-for tags it sits in, whether it needs the
 * correct tool for drops, whether the {@code consumes_tool_durability} tag holds, and its destroy speed (hardness).
 *
 * @param mineableWith          the digging types whose mineable tag holds this block
 * @param incorrectFor          the incorrect-for tags that hold this block
 * @param requiresCorrectTool   {@code requiresCorrectToolForDrops}
 * @param consumesDurabilityTag whether the block is in {@code tfc:consumes_tool_durability}
 * @param destroySpeed          the block's hardness; {@code -1} is unbreakable and {@code 0} is instant
 */
public record MinedBlock(
    Set<ToolType> mineableWith,
    Set<IncorrectTag> incorrectFor,
    boolean requiresCorrectTool,
    boolean consumesDurabilityTag,
    float destroySpeed
) {
    public MinedBlock {
        Objects.requireNonNull(mineableWith, "mineableWith");
        Objects.requireNonNull(incorrectFor, "incorrectFor");
        mineableWith = mineableWith.isEmpty() ? Collections.emptySet() : Collections.unmodifiableSet(EnumSet.copyOf(mineableWith));
        incorrectFor = incorrectFor.isEmpty() ? Collections.emptySet() : Collections.unmodifiableSet(EnumSet.copyOf(incorrectFor));
    }
}
