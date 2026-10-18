/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/ComposterBlockEntity.java @ c3b4d9536 (randomTick, the ready branch)
 */
package dev.tfp.core.compost;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.compost.CompostAddition.Kind;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * The lazy settle must agree with TFC's per-tick random ticks. The stepped run settles every calendar tick between two
 * interactions; the lazy run settles only at the interaction tick. Both must hold the same bin after every interaction.
 */
class CompostLazyVsSteppedTest {

    private static final long[] READY_TICKS = {20L, 500L, 28_800L};
    private static final int SEEDS = 25;
    private static final int EVENTS = 120;

    /** One scripted interaction at a tick. */
    private record Event(long tick, Kind kind, int amount, boolean creative, boolean takeOutput, boolean extract) {
    }

    /** A seeded script: gaps of 1..2R ticks, mostly fills, with poison, extraction and output removal now and then. */
    private static List<Event> script(long seed, long readyTicks) {
        final Random random = new Random(seed);
        final List<Event> events = new ArrayList<>();
        long tick = 0;
        for (int i = 0; i < EVENTS; i++) {
            tick += 1 + random.nextInt((int) (2 * readyTicks));
            final int roll = random.nextInt(100);
            final boolean creative = random.nextInt(10) == 0;
            if (roll < 40) {
                events.add(new Event(tick, Kind.GREEN, 1 + random.nextInt(4), creative, false, false));
            } else if (roll < 80) {
                events.add(new Event(tick, Kind.BROWN, 1 + random.nextInt(4), creative, false, false));
            } else if (roll < 84) {
                events.add(new Event(tick, Kind.POISON, 0, creative, false, false));
            } else if (roll < 88) {
                events.add(new Event(tick, Kind.NOTHING, 0, creative, false, false));
            } else if (roll < 94) {
                events.add(new Event(tick, Kind.NOTHING, 0, creative, false, true));
            } else {
                events.add(new Event(tick, Kind.NOTHING, 0, creative, true, false));
            }
        }
        return events;
    }

    /** Applies one interaction to a settled bin and returns the bin after it. */
    private static CompostBin apply(CompostBin bin, Event event) {
        if (event.extract()) {
            return bin.extract(event.tick()).bin();
        }
        if (event.takeOutput()) {
            return bin.outputChanged(0, event.tick());
        }
        return bin.add(new CompostAddition(event.kind(), event.amount()), event.creative(), event.tick()).bin();
    }

    /** Lazy: settle at each interaction tick only. */
    private static List<CompostBin> lazy(List<Event> events, long readyTicks) {
        final List<CompostBin> out = new ArrayList<>();
        CompostBin bin = CompostBin.empty();
        for (Event event : events) {
            bin = bin.settle(event.tick(), readyTicks);
            bin = apply(bin, event);
            out.add(bin);
        }
        return out;
    }

    /** Stepped: settle every calendar tick from the previous interaction on, as random ticks would. */
    private static List<CompostBin> stepped(List<Event> events, long readyTicks) {
        final List<CompostBin> out = new ArrayList<>();
        CompostBin bin = CompostBin.empty();
        long cursor = 0;
        for (Event event : events) {
            for (long t = cursor + 1; t <= event.tick(); t++) {
                bin = bin.settle(t, readyTicks);
            }
            cursor = event.tick();
            bin = apply(bin, event);
            out.add(bin);
        }
        return out;
    }

    @Test
    void steppedAndLazyHoldTheSameBinAfterEveryInteraction() {
        for (long readyTicks : READY_TICKS) {
            for (long seed = 1; seed <= SEEDS; seed++) {
                final List<Event> events = script(seed, readyTicks);
                assertThat(stepped(events, readyTicks)).as("R=%d seed=%d", readyTicks, seed)
                    .isEqualTo(lazy(events, readyTicks));
            }
        }
    }

    /** A full bin ripens exactly at {@code ripenedAt}: the stepped run turns READY on that tick and not one tick earlier. */
    @Test
    void fullBinRipensOnTheTickTheFormulaGives() {
        for (long readyTicks : READY_TICKS) {
            CompostBin bin = CompostBin.empty();
            long tick = 0;
            for (int i = 0; i < 4; i++) {
                tick += 10;
                bin = bin.add(new CompostAddition(Kind.GREEN, 4), false, tick).bin();
            }
            for (int i = 0; i < 4; i++) {
                tick += 10;
                bin = bin.add(new CompostAddition(Kind.BROWN, 4), false, tick).bin();
            }
            assertThat(bin.isFull()).isTrue();
            final long ripenedAt = bin.ripenedAt(readyTicks);
            assertThat(ripenedAt).isEqualTo(tick + readyTicks + 1);

            CompostBin stepped = bin;
            long firstReady = -1;
            for (long t = tick + 1; t <= tick + readyTicks + 2 && firstReady < 0; t++) {
                stepped = stepped.settle(t, readyTicks);
                if (stepped.type() == CompostBin.Type.READY) {
                    firstReady = t;
                }
            }
            assertThat(firstReady).as("R=%d", readyTicks).isEqualTo(ripenedAt);
            assertThat(bin.settle(ripenedAt - 1, readyTicks).type()).isEqualTo(CompostBin.Type.NORMAL);
            assertThat(bin.settle(ripenedAt, readyTicks).type()).isEqualTo(CompostBin.Type.READY);
        }
    }

    /** Settling twice at the same tick changes nothing, so the caller may settle as often as it likes. */
    @Test
    void settleIsIdempotentAtAGivenTick() {
        final CompostBin bin = CompostBin.empty()
            .add(new CompostAddition(Kind.GREEN, 4), false, 100).bin();
        for (long t = 100; t < 2_000; t += 97) {
            final CompostBin once = bin.settle(t, 500);
            assertThat(once.settle(t, 500)).isEqualTo(once);
        }
    }
}
