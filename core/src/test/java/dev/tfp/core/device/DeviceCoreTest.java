/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.device;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.tfp.core.persist.DataMap;
import dev.tfp.core.persist.PersistException;
import dev.tfp.core.persist.VersionedCodec;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * PAR-INFRA-02: the platform-free half of the device framework. Catch-up follows TFC's
 * {@code ICalendarTickable.checkForCalendarUpdate} (src/main/java/net/dries007/tfc/util/calendar/ICalendarTickable.java
 * @ c3b4d9536) with the lazy substitute of NP-SIM-01; the record codec keeps every slot.
 */
class DeviceCoreTest {
    /** A fake burning device: {@code fuel} ticks left, {@code burned} ticks burned so far. */
    record Burner(long fuel, long burned) {
    }

    static final CatchUp.Advance<Burner> BURN = (rec, from, to) -> {
        long gap = to - from;
        long burn = Math.min(gap, rec.state().fuel());
        return rec.withState(new Burner(rec.state().fuel() - burn, rec.state().burned() + burn));
    };

    static VersionedCodec<Burner> burnerV1() {
        return VersionedCodec.<Burner>builder("burner", 1)
                .build((b, out) -> out.putLong("fuel", b.fuel()), in -> new Burner(in.requireLong("fuel"), 0));
    }

    static VersionedCodec<Burner> burnerV2() {
        return VersionedCodec.<Burner>builder("burner", 2)
                .migrate(1, d -> d.putLong("burned", 0))
                .build((b, out) -> out.putLong("fuel", b.fuel()).putLong("burned", b.burned()),
                        in -> new Burner(in.requireLong("fuel"), in.requireLong("burned")));
    }

    @Test
    void neverUpdatedIsStampedWithoutAdvancing() {
        DeviceRecord<Burner> fresh = DeviceRecord.fresh(new Burner(100, 0), 2);
        assertThat(fresh.neverUpdated()).isTrue();
        CatchUp.Result<Burner> r = CatchUp.apply(fresh, 5_000, (rec, a, b) -> {
            throw new AssertionError("advance must not run on the first stamp");
        });
        assertThat(r.elapsedTicks()).isZero();
        assertThat(r.record().lastUpdateTick()).isEqualTo(5_000);
        assertThat(r.record().state()).isEqualTo(new Burner(100, 0));
    }

    @Test
    void sameTickIsANoOpAndGapIsPassedWhole() {
        DeviceRecord<Burner> rec = DeviceRecord.fresh(new Burner(100, 0), 0).withLastUpdateTick(1_000);
        CatchUp.Result<Burner> same = CatchUp.apply(rec, 1_000, BURN);
        assertThat(same.record()).isSameAs(rec);
        assertThat(same.advanced()).isFalse();

        List<long[]> calls = new ArrayList<>();
        CatchUp.Result<Burner> later = CatchUp.apply(rec, 1_030, (r, a, b) -> {
            calls.add(new long[] {a, b});
            return BURN.advance(r, a, b);
        });
        assertThat(calls).hasSize(1);
        assertThat(calls.getFirst()).containsExactly(1_000, 1_030);
        assertThat(later.elapsedTicks()).isEqualTo(30);
        assertThat(later.record().lastUpdateTick()).isEqualTo(1_030);
        assertThat(later.record().state()).isEqualTo(new Burner(70, 30));
    }

    @Test
    void calendarSetBackRestampsWithoutAdvancing() {
        DeviceRecord<Burner> rec = DeviceRecord.fresh(new Burner(100, 0), 0).withLastUpdateTick(9_000);
        CatchUp.Result<Burner> r = CatchUp.apply(rec, 4_000, BURN);
        assertThat(r.elapsedTicks()).isZero();
        assertThat(r.record().lastUpdateTick()).isEqualTo(4_000);
        assertThat(r.record().state()).isEqualTo(rec.state());
        assertThat(CatchUp.elapsed(DeviceRecord.NEVER, 10)).isZero();
        assertThat(CatchUp.elapsed(10, 25)).isEqualTo(15);
    }

    /** Lazy (one catch-up at the end) equals stepped (a catch-up at random intermediate ticks) for an additive rule. */
    @Test
    void lazyEqualsStepped() {
        Random random = new Random(42);
        for (int trial = 0; trial < 200; trial++) {
            long start = random.nextInt(1_000_000);
            long end = start + random.nextInt(50_000);
            DeviceRecord<Burner> base = DeviceRecord.fresh(new Burner(random.nextInt(40_000), 0), 1)
                    .withLastUpdateTick(start);
            DeviceRecord<Burner> lazy = CatchUp.apply(base, end, BURN).record();
            DeviceRecord<Burner> stepped = base;
            long t = start;
            while (t < end) {
                t = Math.min(end, t + 1 + random.nextInt(5_000));
                stepped = CatchUp.apply(stepped, t, BURN).record();
            }
            assertThat(stepped).isEqualTo(lazy);
        }
    }

    @Test
    void recordCodecRoundTripsSlotsAndState() {
        VersionedCodec<DeviceRecord<Burner>> codec = DeviceRecordCodec.of("device/burner", burnerV2(), 3);
        DeviceRecord<Burner> rec = DeviceRecord.fresh(new Burner(12, 34), 3)
                .withSlot(1, ItemBlob.of(new byte[] {1, 2, 3}))
                .withLastUpdateTick(777);
        DataMap encoded = codec.encode(rec);
        assertThat(encoded.requireInt("v")).isEqualTo(DeviceRecordCodec.VERSION);
        assertThat(encoded.requireMap("state").requireInt("v")).isEqualTo(2);
        assertThat(codec.decode(encoded)).isEqualTo(rec);
        assertThat(codec.fromBytes(codec.toBytes(rec))).isEqualTo(rec);
        assertThat(rec.nonEmptySlots()).containsExactly(ItemBlob.of(new byte[] {1, 2, 3}));
        assertThat(rec.cleared().nonEmptySlots()).isEmpty();
    }

    @Test
    void stateMigratesInsideTheEnvelope() {
        DataMap old = DeviceRecordCodec.of("device/burner", burnerV1(), 1)
                .encode(DeviceRecord.fresh(new Burner(50, 0), 1).withLastUpdateTick(10));
        DeviceRecord<Burner> read = DeviceRecordCodec.of("device/burner", burnerV2(), 1).decode(old);
        assertThat(read.state()).isEqualTo(new Burner(50, 0));
        assertThat(read.lastUpdateTick()).isEqualTo(10);
    }

    @Test
    void slotCountChangesNeverLoseItems() {
        ItemBlob stone = ItemBlob.of(new byte[] {9});
        DeviceRecord<Burner> four = DeviceRecord.fresh(new Burner(0, 0), 4).withSlot(3, stone);
        DataMap stored = DeviceRecordCodec.of("d", burnerV2(), 4).encode(four);
        // the kind shrank to 2 slots: the stored 4 are kept
        assertThat(DeviceRecordCodec.of("d", burnerV2(), 2).decode(stored).slots()).hasSize(4).endsWith(stone);
        // the kind grew to 6 slots: padded with empties
        DeviceRecord<Burner> six = DeviceRecordCodec.of("d", burnerV2(), 6).decode(stored);
        assertThat(six.slots()).hasSize(6);
        assertThat(six.slot(3)).isEqualTo(stone);
        assertThat(six.slot(5).isEmpty()).isTrue();
    }

    @Test
    void newerEnvelopeOrStateIsRefused() {
        VersionedCodec<DeviceRecord<Burner>> codec = DeviceRecordCodec.of("d", burnerV1(), 1);
        DataMap fromV2 = DeviceRecordCodec.of("d", burnerV2(), 1).encode(DeviceRecord.fresh(new Burner(1, 1), 1));
        // the envelope wraps the state's error: callers keep any unreadable record untouched
        assertThatThrownBy(() -> codec.decode(fromV2)).isInstanceOf(PersistException.class).hasMessageContaining("burner");
        DataMap future = codec.encode(DeviceRecord.fresh(new Burner(1, 0), 1)).putInt("v", 99);
        assertThatThrownBy(() -> codec.decode(future)).isInstanceOf(PersistException.NewerVersion.class);
        assertThatThrownBy(() -> DeviceRecordCodec.of("d", burnerV1(), DeviceRecordCodec.MAX_SLOTS + 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void blobsCompareByContentAndCopy() {
        byte[] raw = {1, 2};
        ItemBlob a = ItemBlob.of(raw);
        raw[0] = 7;
        assertThat(a).isEqualTo(ItemBlob.of(new byte[] {1, 2})).hasSameHashCodeAs(ItemBlob.of(new byte[] {1, 2}));
        assertThat(ItemBlob.of(null)).isSameAs(ItemBlob.EMPTY);
        assertThat(ItemBlob.of(new byte[0]).isEmpty()).isTrue();
        assertThat(a.bytes()).containsExactly(1, 2);
    }
}
