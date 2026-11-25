/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.powerloom;

import dev.tfp.paper.powerloom.PowerLoomIndex.Key;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * What the power loom service needs from the server, as a seam so the decisions run in tests with no server. Methods marked
 * "region" run on the loom's region thread; {@link #speed} may complete on any thread.
 */
interface PowerLoomAccess {
    /**
     * The loom as stored.
     *
     * @param active whether a rotation turns it or a step waits ({@code PowerLoomState.active})
     * @param pushes the pushes made so far
     */
    record View(boolean active, int pushes) {
    }

    /** Region: the loom at {@code key}, empty when that block is no power loom (broken, replaced). */
    Optional<View> view(Key key);

    /**
     * The rotation the loom gets on its sink face (TFC's {@code SinkNode(pos, FACING.getClockWise())}): the network's signed speed in
     * radians per tick, or empty when the neighbour on that side does not connect to the loom or is on no network. Any thread.
     */
    CompletableFuture<Optional<Float>> speed(Key key);

    /** Region: catches the loom up at the old speed, then stores {@code speed} (0 for none). */
    void apply(Key key, float speed);

    /** Region: the weave sound (TFC plays it on every push). */
    void weave(Key key);

    /** Runs {@code task} on the loom's region thread after {@code delayTicks} (at least 1). */
    void later(Key key, long delayTicks, Runnable task);

    /** Runs {@code task} on the loom's region thread as soon as possible. */
    void onRegion(Key key, Runnable task);

    /** Persists that this loom is watched, so a chunk load picks it up again. Region. */
    void track(Key key);

    /** The reverse of {@link #track}. Region. */
    void untrack(Key key);
}
