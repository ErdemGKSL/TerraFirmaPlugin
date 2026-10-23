/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.catchup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.tfp.core.device.CatchUp;
import dev.tfp.core.device.DeviceRecord;
import dev.tfp.core.persist.DataMap;
import dev.tfp.core.persist.DataMapBinary;
import dev.tfp.core.persist.PersistException;
import dev.tfp.core.world.BlockPos;
import dev.tfp.core.world.ChunkPos;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** PAR-INFRA-11: the lazy block index, its codec, the sweep area and the device adapter of the pure advance. */
class CatchUpCoreTest {
    @Test
    void indexKeepsYzxOrderAndIsImmutable() {
        LazyBlockIndex i = LazyBlockIndex.empty(-1, 2)
                .with(new BlockPos(-1, 70, 33), "tfp:crop")
                .with(new BlockPos(-16, -64, 47), "tfp:bush")
                .with(new BlockPos(-5, 70, 32), "tfp:crop");
        assertThat(i.entries().keySet()).containsExactly(new BlockPos(-16, -64, 47), new BlockPos(-5, 70, 32),
                new BlockPos(-1, 70, 33));
        assertThat(i.kindAt(new BlockPos(-5, 70, 32))).contains("tfp:crop");
        assertThat(i.with(new BlockPos(-5, 70, 32), "tfp:crop")).isSameAs(i);
        assertThat(i.without(new BlockPos(0, 0, 0))).isSameAs(i);
        assertThat(i.without(new BlockPos(-5, 70, 32)).size()).isEqualTo(2);
        assertThat(i.size()).isEqualTo(3);
        assertThatThrownBy(() -> i.entries().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void indexRefusesForeignPositionsAndBadKinds() {
        LazyBlockIndex i = LazyBlockIndex.empty(0, 0);
        assertThatThrownBy(() -> i.with(new BlockPos(16, 0, 0), "tfp:crop")).hasMessageContaining("not in chunk");
        assertThatThrownBy(() -> i.with(new BlockPos(0, 0, 0), "Crop")).hasMessageContaining("bad catch-up kind");
    }

    @Test
    void codecRoundTripsNegativeChunksAndDepths() {
        Map<BlockPos, String> m = new LinkedHashMap<>();
        for (int y = -64; y < 320; y += 37) {
            m.put(new BlockPos(-48 + ((y + 64) & 15), y, 95), y % 2 == 0 ? "tfp:crop" : "tfp:torch");
        }
        m.put(new BlockPos(-48, 319, 80), "tfp:sapling");
        LazyBlockIndex i = LazyBlockIndex.of(-3, 5, m);
        byte[] bytes = LazyBlockIndex.CODEC.toBytes(i);
        assertThat(LazyBlockIndex.CODEC.fromBytes(bytes)).isEqualTo(i);
        // deterministic: same entries in another insertion order give the same bytes
        List<Map.Entry<BlockPos, String>> rev = new ArrayList<>(m.entrySet());
        java.util.Collections.reverse(rev);
        Map<BlockPos, String> r = new LinkedHashMap<>();
        rev.forEach(e -> r.put(e.getKey(), e.getValue()));
        assertThat(LazyBlockIndex.CODEC.toBytes(LazyBlockIndex.of(-3, 5, r))).isEqualTo(bytes);
    }

    @Test
    void codecLayoutV1() {
        LazyBlockIndex i = LazyBlockIndex.empty(1, -1).with(new BlockPos(17, -2, -15), "tfp:crop");
        DataMap map = LazyBlockIndex.CODEC.encode(i);
        assertThat(map.getInt("v", 0)).isEqualTo(1);
        assertThat(map.getInt("cx", 0)).isEqualTo(1);
        assertThat(map.getInt("cz", 0)).isEqualTo(-1);
        assertThat(map.getList("kinds")).hasSize(1);
        // kind 0; packed = y << 8 | localZ << 4 | localX = -2 << 8 | 1 << 4 | 1
        assertThat(map.getBytes("e", null)).containsExactly(0, 0, 0, 0, 0xff, 0xff, 0xfe, 0x11);
    }

    @Test
    void corruptEntriesFailAsPersistErrors() {
        DataMap map = LazyBlockIndex.CODEC.encode(LazyBlockIndex.empty(0, 0).with(new BlockPos(1, 2, 3), "tfp:crop"));
        DataMap odd = map.copy().putBytes("e", new byte[] {1, 2, 3});
        assertThatThrownBy(() -> LazyBlockIndex.CODEC.decode(odd)).isInstanceOf(PersistException.class);
        DataMap badKind = map.copy().putBytes("e", new byte[] {0, 0, 0, 5, 0, 0, 0, 0});
        assertThatThrownBy(() -> LazyBlockIndex.CODEC.decode(badKind)).isInstanceOf(PersistException.class);
        DataMap newer = map.copy().putInt("v", 2);
        assertThatThrownBy(() -> LazyBlockIndex.CODEC.fromBytes(DataMapBinary.encode(newer)))
                .isInstanceOf(PersistException.NewerVersion.class);
    }

    @Test
    void sweepAreaIsNearestFirstAndComplete() {
        List<ChunkPos> a = SweepArea.around(10, -4, 2);
        assertThat(a).hasSize(25).doesNotHaveDuplicates();
        assertThat(a.getFirst()).isEqualTo(new ChunkPos(10, -4));
        assertThat(a.subList(1, 5)).containsExactlyInAnyOrder(new ChunkPos(9, -4), new ChunkPos(11, -4),
                new ChunkPos(10, -5), new ChunkPos(10, -3));
        assertThat(a.getLast()).isIn(new ChunkPos(8, -6), new ChunkPos(12, -2), new ChunkPos(8, -2), new ChunkPos(12, -6));
        assertThat(SweepArea.around(0, 0, 0)).containsExactly(new ChunkPos(0, 0));
    }

    @Test
    void sweepDue() {
        assertThat(SweepArea.due(Long.MIN_VALUE, 5, 600)).isTrue();
        assertThat(SweepArea.due(100, 699, 600)).isFalse();
        assertThat(SweepArea.due(100, 700, 600)).isTrue();
        assertThat(SweepArea.due(100, 50, 600)).isTrue();
    }

    @Test
    void deviceAdapterFollowsTheCatchUpRules() {
        List<long[]> calls = new ArrayList<>();
        CatchUp.Advance<Long> add = (rec, from, to) -> {
            calls.add(new long[] {from, to});
            return rec.withState(rec.state() + to - from);
        };
        CatchUpAdvance<DeviceRecord<Long>> a = CatchUpAdvance.device(add);
        DeviceRecord<Long> fresh = DeviceRecord.fresh(0L, 0);
        DeviceRecord<Long> stamped = a.advance(fresh, 100);
        assertThat(stamped.lastUpdateTick()).isEqualTo(100);
        assertThat(calls).isEmpty();
        DeviceRecord<Long> later = a.advance(stamped, 350);
        assertThat(later.state()).isEqualTo(250L);
        assertThat(calls).hasSize(1);
        // lazy = stepped: one call over the gap equals many small ones for an additive rule
        DeviceRecord<Long> stepped = stamped;
        for (long t = 110; t <= 350; t += 10) {
            stepped = a.advance(stepped, t);
        }
        assertThat(stepped.state()).isEqualTo(later.state());
        assertThat(a.advance(later, 350)).isEqualTo(later);
    }
}
