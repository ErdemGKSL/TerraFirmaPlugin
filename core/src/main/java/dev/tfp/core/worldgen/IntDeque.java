/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.worldgen;

import java.util.Arrays;

/**
 * A growable ring buffer of ints with the operations of fastutil's {@code IntArrayFIFOQueue} that TFC's region tasks
 * use: {@link #enqueue} appends, {@link #enqueueFirst} prepends (a breadth-first search that keeps a batch together),
 * and {@link #dequeueInt} removes from the front. Not thread-safe; each region task owns its queue.
 */
public final class IntDeque {
    private int[] items = new int[64];
    private int head;
    private int size;

    public boolean isEmpty() {
        return size == 0;
    }

    public int size() {
        return size;
    }

    /** Appends {@code value} at the back (fastutil {@code enqueue}). */
    public void enqueue(int value) {
        grow();
        items[(head + size) & (items.length - 1)] = value;
        size++;
    }

    /** Prepends {@code value} at the front (fastutil {@code enqueueFirst}). */
    public void enqueueFirst(int value) {
        grow();
        head = (head - 1) & (items.length - 1);
        items[head] = value;
        size++;
    }

    /** Removes and returns the front value (fastutil {@code dequeueInt}). The queue must not be empty. */
    public int dequeueInt() {
        if (size == 0) {
            throw new IllegalStateException("empty queue");
        }
        final int value = items[head];
        head = (head + 1) & (items.length - 1);
        size--;
        return value;
    }

    /** @return The value at {@code index} counted from the front, without removing it. */
    public int get(int index) {
        return items[(head + index) & (items.length - 1)];
    }

    public void clear() {
        head = 0;
        size = 0;
    }

    private void grow() {
        if (size < items.length) {
            return;
        }
        final int[] grown = new int[items.length * 2];
        for (int i = 0; i < size; i++) {
            grown[i] = items[(head + i) & (items.length - 1)];
        }
        items = grown;
        head = 0;
    }

    @Override
    public String toString() {
        final int[] copy = new int[size];
        for (int i = 0; i < size; i++) {
            copy[i] = get(i);
        }
        return Arrays.toString(copy);
    }
}
