/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.collapse;

import java.util.List;
import dev.tfp.core.world.BlockPos;

/** What mining one block decided: nothing, a real collapse from one start, or a fake one that only shows where it would start. */
public sealed interface TriggerDecision {
    /** No collapse and no fake collapse. */
    record None() implements TriggerDecision {}

    /** A real collapse starts at {@code start}. */
    record Real(BlockPos start) implements TriggerDecision {}

    /**
     * A fake collapse. {@code starts} lists every startable position in the search box (sorted), {@code display} is the sample
     * the client shows (all of them when there are fewer than four).
     */
    record Fake(List<BlockPos> starts, List<BlockPos> display) implements TriggerDecision {
        public Fake {
            starts = List.copyOf(starts);
            display = List.copyOf(display);
        }
    }
}
