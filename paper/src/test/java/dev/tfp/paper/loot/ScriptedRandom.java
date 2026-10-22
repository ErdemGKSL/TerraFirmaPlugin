/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.loot;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * A {@link LootRandom} that returns scripted values in order and fails on a draw of the wrong kind or past the script, so
 * a test pins both the values and the order and number of draws ({@link #assertExhausted()}).
 */
final class ScriptedRandom implements LootRandom {
    private final Deque<Object> script = new ArrayDeque<>();

    /** Floats are queued as {@code Float}, ints as {@code Integer}. */
    static ScriptedRandom of(Object... values) {
        ScriptedRandom r = new ScriptedRandom();
        for (Object v : values) {
            if (!(v instanceof Float) && !(v instanceof Integer)) {
                throw new IllegalArgumentException("script values are Float or Integer, got " + v);
            }
            r.script.add(v);
        }
        return r;
    }

    @Override
    public float nextFloat() {
        Object v = script.poll();
        if (!(v instanceof Float f)) {
            throw new AssertionError("unexpected nextFloat(); next scripted value: " + v);
        }
        return f;
    }

    @Override
    public int nextInt(int bound) {
        Object v = script.poll();
        if (!(v instanceof Integer i)) {
            throw new AssertionError("unexpected nextInt(" + bound + "); next scripted value: " + v);
        }
        if (i < 0 || i >= bound) {
            throw new AssertionError("scripted int " + i + " outside [0, " + bound + ")");
        }
        return i;
    }

    void assertExhausted() {
        if (!script.isEmpty()) {
            throw new AssertionError("draws left unused: " + script);
        }
    }
}
