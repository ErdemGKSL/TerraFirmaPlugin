/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/component/glass/GlassOperations.java @ c3b4d9536 (the step limit, the no-op
 * rule without a batch, and that a new batch keeps the steps)
 */
package dev.tfp.core.glass;

import java.util.List;

/**
 * The glassworking state carried by a blowpipe: the batch item and the operations applied so far, in order. An empty batch
 * ({@code ""}) means there is no glass; then there are no steps either, and {@link GlassRules#apply} does nothing.
 *
 * @param steps the operations applied, in order, at most {@link #LIMIT}
 * @param batch the batch item id, or {@code ""} for no batch
 */
public record GlassState(List<GlassOperation> steps, String batch) {

    /** The most operations a blowpipe can hold (TFC {@code GlassOperations.LIMIT}). */
    public static final int LIMIT = 24;

    /** The state of a blowpipe with no glass. */
    public static final GlassState EMPTY = new GlassState(List.of(), "");

    public GlassState {
        if (steps == null) throw new IllegalArgumentException("steps must not be null");
        if (batch == null) throw new IllegalArgumentException("batch must not be null, use \"\" for none");
        if (steps.size() > LIMIT) throw new IllegalArgumentException("too many steps: " + steps.size());
        if (batch.isEmpty() && !steps.isEmpty()) throw new IllegalArgumentException("steps without a batch");
        steps = List.copyOf(steps);
    }

    /** Whether there is no batch. */
    public boolean isEmpty() {
        return batch.isEmpty();
    }
}
