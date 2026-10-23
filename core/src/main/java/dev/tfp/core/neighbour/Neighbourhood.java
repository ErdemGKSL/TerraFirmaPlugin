/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.neighbour;

import dev.tfp.core.world.Direction;
import java.util.Arrays;
import java.util.Objects;

/**
 * Immutable copy of the 3x3x3 block states around one position (PAR-INFRA-10), the only input of
 * {@link ShapeRule#decide}. Offsets run from -1 to 1 on each axis. A {@code null} entry means "unknown" (the chunk was
 * not loaded when the snapshot was taken): rules must not read it as air. Safe to read on any thread.
 *
 * @param <S> block state type (on Paper: {@code BlockStateRef})
 */
public final class Neighbourhood<S> {
    /** Reads the state at an offset from the centre; {@code null} for unknown. */
    @FunctionalInterface
    public interface Source<S> {
        S at(int dx, int dy, int dz);
    }

    private final Object[] states;

    private Neighbourhood(Object[] states) {
        this.states = states;
    }

    /** Copies the 27 states from {@code source}. */
    public static <S> Neighbourhood<S> of(Source<S> source) {
        Objects.requireNonNull(source, "source");
        Object[] a = new Object[27];
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    a[index(dx, dy, dz)] = source.at(dx, dy, dz);
                }
            }
        }
        return new Neighbourhood<>(a);
    }

    private static int index(int dx, int dy, int dz) {
        if (dx < -1 || dx > 1 || dy < -1 || dy > 1 || dz < -1 || dz > 1) {
            throw new IndexOutOfBoundsException("offset outside the 3x3x3 neighbourhood: " + dx + "," + dy + "," + dz);
        }
        return (dx + 1) * 9 + (dy + 1) * 3 + (dz + 1);
    }

    /** The state at an offset, or {@code null} if unknown. */
    @SuppressWarnings("unchecked")
    public S at(int dx, int dy, int dz) {
        return (S) states[index(dx, dy, dz)];
    }

    /** The face neighbour in {@code direction}, or {@code null} if unknown. */
    public S at(Direction direction) {
        return at(direction.dx(), direction.dy(), direction.dz());
    }

    /** The state being decided; {@code null} if unknown. */
    public S center() {
        return at(0, 0, 0);
    }

    public boolean known(int dx, int dy, int dz) {
        return at(dx, dy, dz) != null;
    }

    public boolean known(Direction direction) {
        return at(direction) != null;
    }

    /** True if every face neighbour is known. */
    public boolean facesKnown() {
        for (Direction d : Direction.values()) {
            if (!known(d)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Neighbourhood<?> n && Arrays.equals(states, n.states);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(states);
    }

    @Override
    public String toString() {
        return "Neighbourhood" + Arrays.toString(states);
    }
}
