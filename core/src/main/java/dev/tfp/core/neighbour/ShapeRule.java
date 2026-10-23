/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.neighbour;

import java.util.Set;

/**
 * A block shape rule (PAR-INFRA-10): the platform-free equivalent of a TFC block's {@code updateShape} (for example
 * {@code VerticalSupportBlock} connecting to beams, {@code ConnectedGrassBlock} turning snowy). Where TFC's
 * {@code updateShape} runs inline for each notified side, here the neighbour-update runner snapshots the 3x3x3
 * neighbourhood when any neighbour changes and calls {@link #decide} once, off the region thread; the result is
 * applied on the owner region under the commit budget.
 *
 * <p>Implementations must be pure and thread-safe: {@link #decide} runs on worker threads, reads only the snapshot
 * and immutable data, and must be idempotent ({@code decide} of a neighbourhood whose centre already is the decided
 * state returns that state or {@code null}), otherwise the runner never settles.
 *
 * @param <S> block state type (on Paper: {@code BlockStateRef})
 */
public interface ShapeRule<S> {
    /** Stable id for logs and metrics. */
    String id();

    /**
     * Block ids this rule may apply to, used by the platform layer as a cheap filter before it reads a full state
     * (an empty set means "ask {@link #appliesTo} for every block", which is expensive on a real server).
     */
    Set<String> blockIds();

    /** True if this rule decides the shape of {@code state}. */
    boolean appliesTo(S state);

    /**
     * The new state of the centre block of {@code n}, or {@code null} to keep it (also when the decision needs a
     * neighbour that is unknown). Pure; called on a worker thread.
     */
    S decide(Neighbourhood<S> n);

    /**
     * The state written at once when a block of this rule is placed, before the async decision ("decide async, apply
     * sync"): cheap and known without reading neighbours, e.g. "unconnected". Defaults to the placed state.
     */
    default S provisional(S placed) {
        return placed;
    }
}
