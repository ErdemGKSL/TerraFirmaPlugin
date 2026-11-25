/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.quern.powered;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * What the powered quern service needs from the server, as a seam so the decisions run in tests with no server. Methods marked
 * "region" must be called on the quern's region thread (Folia) and are; {@link #speed} may complete on any thread.
 */
public interface PoweredQuernAccess {

    /** A quern block. */
    record Key(UUID world, int x, int y, int z) {
    }

    /**
     * The quern as stored, not caught up.
     *
     * @param now        the player tick
     * @param started    a grind is stored (running or due)
     * @param finishTick the stored grind's finish tick (meaningful when {@code started})
     * @param handstone  the handstone slot is filled
     * @param grindable  the input slot holds an item with a quern recipe
     */
    record View(long now, boolean started, long finishTick, boolean handstone, boolean grindable) {
        /** Whether the stored grind has reached its finish tick. */
        public boolean due() {
            return started && now >= finishTick;
        }
    }

    /** Region: the quern at {@code key}, empty when that block is no quern (broken, replaced). */
    Optional<View> view(Key key);

    /**
     * The rotation the quern gets from the block above (TFC's {@code SinkNode(pos, UP)}): the network's signed speed in radians
     * per tick, or empty when the block above does not connect down or is on no network. Any thread; the answer is the
     * network's state after every change submitted before.
     */
    CompletableFuture<Optional<Float>> speed(Key key);

    /** Region: finishes the stored grind if it is due, as the manual quern does (result, input used, handstone damage, sounds). */
    void settle(Key key);

    /** Region: stores a grind that finishes at {@code finishTick} (a start, or a re-plan of the running one). */
    void setFinish(Key key, long finishTick);

    /** Runs {@code task} on the quern's region thread after {@code delayTicks} (at least 1). */
    void later(Key key, long delayTicks, Runnable task);

    /** Runs {@code task} on the quern's region thread as soon as possible (from the async speed answer). */
    void onRegion(Key key, Runnable task);

    /** Persists that this quern is being watched, so a chunk load picks it up again. Region. */
    void track(Key key);

    /** The reverse of {@link #track}. Region. */
    void untrack(Key key);
}
