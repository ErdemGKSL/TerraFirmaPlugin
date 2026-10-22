/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.critter;

import java.util.ArrayList;
import java.util.List;

import dev.tfp.core.spawn.SpawnRandom;

/**
 * A random source that returns scripted values in order and logs each call as {@code i:bound} or {@code f}, so a test can
 * check both the results and the draw order. Running past the script fails the test.
 */
final class ScriptedRandom implements SpawnRandom {
    private final int[] ints;
    private final float[] floats;
    private int intIndex;
    private int floatIndex;
    final List<String> log = new ArrayList<>();

    /** A script whose {@code nextInt} draws are {@code ints} and whose {@code nextFloat} draws are {@code floats}. */
    ScriptedRandom(int[] ints, float[] floats) {
        this.ints = ints;
        this.floats = floats;
    }

    @Override
    public int nextInt(int bound) {
        if (intIndex >= ints.length) {
            throw new AssertionError("no scripted nextInt left, bound " + bound);
        }
        int value = ints[intIndex++];
        if (value < 0 || value >= bound) {
            throw new AssertionError("scripted value " + value + " is outside [0, " + bound + ")");
        }
        log.add("i:" + bound);
        return value;
    }

    @Override
    public float nextFloat() {
        if (floatIndex >= floats.length) {
            throw new AssertionError("no scripted nextFloat left");
        }
        log.add("f");
        return floats[floatIndex++];
    }
}
