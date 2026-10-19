/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.mount;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * A {@link MountRandom} that replays a fixed sequence of draws. Each call must match the next scripted draw in kind
 * ({@code Double} for {@code nextDouble}, {@code Integer} for {@code nextInt}) and fall inside the bound, so a test also
 * checks the order and the number of draws. {@link #assertConsumed()} fails if draws are left over.
 */
final class ScriptedMountRandom implements MountRandom {

    private final Deque<Object> draws = new ArrayDeque<>();

    ScriptedMountRandom(Object... scripted) {
        for (Object draw : scripted) {
            if (!(draw instanceof Double) && !(draw instanceof Integer)) {
                throw new IllegalArgumentException("only Double and Integer draws: " + draw);
            }
            draws.add(draw);
        }
    }

    @Override
    public double nextDouble() {
        final Object next = draws.poll();
        assertThat(next).as("a nextDouble draw was scripted").isInstanceOf(Double.class);
        return (Double) next;
    }

    @Override
    public int nextInt(int bound) {
        final Object next = draws.poll();
        assertThat(next).as("a nextInt(%d) draw was scripted", bound).isInstanceOf(Integer.class);
        final int value = (Integer) next;
        assertThat(value).as("nextInt(%d) draw", bound).isBetween(0, bound - 1);
        return value;
    }

    /** Fails unless every scripted draw was taken. */
    void assertConsumed() {
        assertThat(draws).as("scripted draws left over").isEmpty();
    }
}
