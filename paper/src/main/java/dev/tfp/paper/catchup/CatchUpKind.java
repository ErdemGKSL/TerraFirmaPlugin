/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.catchup;

import dev.tfp.core.catchup.CatchUpAdvance;
import dev.tfp.core.catchup.LazyBlockIndex;
import dev.tfp.core.world.BlockPos;
import java.util.Objects;
import org.bukkit.World;

/**
 * One kind of lazy block the catch-up service brings up to date (PAR-INFRA-11): crops, bushes, torches, drying bricks,
 * weathering, saplings. "Decide async, apply sync":
 * <ol>
 *   <li>{@link #read} on the region thread captures the block's lazy state (the snapshot);</li>
 *   <li>{@link #advance} on the worker pool computes the state at the snapshot's {@code now} (pure);</li>
 *   <li>if it differs, the region commit queue re-reads the block and calls {@link #write} only when the block still
 *       equals the snapshot, under the commit budget.</li>
 * </ol>
 *
 * @param <S> the lazy state; value equality, immutable
 */
public interface CatchUpKind<S> {
    /** The kind id stored in the chunk index ({@link LazyBlockIndex#KIND_ID}). */
    String id();

    /**
     * The block's lazy state. Region thread of {@code pos}; its chunk is loaded. Returns {@code null} when the block is
     * no longer of this kind (broken, exploded, replaced): its index entry is then dropped.
     */
    S read(World world, BlockPos pos);

    /** Writes the caught-up state. Region thread of {@code pos}; only after {@link #read} returned the expected state. */
    void write(World world, BlockPos pos, S state);

    /** The pure catch-up. */
    CatchUpAdvance<S> advance();

    /** Region-thread read; see {@link #read}. */
    @FunctionalInterface
    interface Reader<S> {
        S read(World world, BlockPos pos);
    }

    /** Region-thread write; see {@link #write}. */
    @FunctionalInterface
    interface Writer<S> {
        void write(World world, BlockPos pos, S state);
    }

    /** A kind from its parts. */
    static <S> CatchUpKind<S> of(String id, Reader<S> reader, Writer<S> writer, CatchUpAdvance<S> advance) {
        LazyBlockIndex.checkKind(id);
        Objects.requireNonNull(reader, "reader");
        Objects.requireNonNull(writer, "writer");
        Objects.requireNonNull(advance, "advance");
        return new CatchUpKind<>() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public S read(World world, BlockPos pos) {
                return reader.read(world, pos);
            }

            @Override
            public void write(World world, BlockPos pos, S state) {
                writer.write(world, pos, state);
            }

            @Override
            public CatchUpAdvance<S> advance() {
                return advance;
            }

            @Override
            public String toString() {
                return "CatchUpKind[" + id + "]";
            }
        };
    }

    /**
     * A kind that catches a block up synchronously, in one call on the region thread, for blocks whose framework
     * already owns the read-advance-write cycle (devices: {@code DeviceService.catchUp(Block)}). Runs under the feed
     * budget, one block per call.
     */
    @FunctionalInterface
    interface Direct {
        /**
         * Region thread of {@code pos}; its chunk is loaded. Returns false when the block is no longer of this kind
         * (its index entry is then dropped).
         */
        boolean catchUp(World world, BlockPos pos);
    }
}
