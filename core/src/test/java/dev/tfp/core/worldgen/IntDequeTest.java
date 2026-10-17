/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.worldgen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayDeque;
import java.util.Random;
import org.junit.jupiter.api.Test;

class IntDequeTest {
    @Test
    void behavesLikeADequeUnderRandomOperations() {
        final Random random = new Random(7);
        final IntDeque queue = new IntDeque();
        final ArrayDeque<Integer> model = new ArrayDeque<>();
        for (int step = 0; step < 20_000; step++) {
            final int op = random.nextInt(10);
            if (op < 4) {
                final int value = random.nextInt();
                queue.enqueue(value);
                model.addLast(value);
            } else if (op < 6) {
                final int value = random.nextInt();
                queue.enqueueFirst(value);
                model.addFirst(value);
            } else if (op < 9 && !model.isEmpty()) {
                assertThat(queue.dequeueInt()).isEqualTo(model.pollFirst());
            } else if (op == 9 && step % 997 == 0) {
                queue.clear();
                model.clear();
            }
            assertThat(queue.size()).isEqualTo(model.size());
            assertThat(queue.isEmpty()).isEqualTo(model.isEmpty());
        }
        int i = 0;
        for (int value : model) {
            assertThat(queue.get(i++)).isEqualTo(value);
        }
    }

    @Test
    void enqueueFirstPutsTheBatchAtTheFront() {
        final IntDeque queue = new IntDeque();
        queue.enqueue(1);
        queue.enqueue(2);
        queue.enqueueFirst(0);
        assertThat(queue.dequeueInt()).isZero();
        assertThat(queue.dequeueInt()).isEqualTo(1);
        assertThat(queue.dequeueInt()).isEqualTo(2);
        assertThat(queue.isEmpty()).isTrue();
    }
}
