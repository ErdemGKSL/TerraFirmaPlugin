/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.persist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** M1-SVC-01: the shared codec layer (DataMap, binary form, versions and migrations). */
class VersionedCodecTest {
    /** Toy crop state across three layouts. v1: "planted" (int ticks). v2: renamed to "plantedTick" (long). v3: adds "stage". */
    record Crop(long plantedTick, int stage) {
    }

    static VersionedCodec<Crop> v1() {
        return VersionedCodec.<Crop>builder("crop", 1)
                .build((c, out) -> out.putInt("planted", (int) c.plantedTick()), in -> new Crop(in.requireInt("planted"), 0));
    }

    static VersionedCodec<Crop> v3() {
        return VersionedCodec.<Crop>builder("crop", 3)
                .migrate(1, d -> d.putLong("plantedTick", d.requireInt("planted")).remove("planted"))
                .migrate(2, d -> d.putInt("stage", 0))
                .build((c, out) -> out.putLong("plantedTick", c.plantedTick()).putInt("stage", c.stage()),
                        in -> new Crop(in.requireLong("plantedTick"), in.requireInt("stage")));
    }

    @Test
    void roundTripsThroughBytes() {
        VersionedCodec<Crop> codec = v3();
        Crop c = new Crop(123_456_789_012L, 4);
        byte[] bytes = codec.toBytes(c);
        assertThat(codec.fromBytes(bytes)).isEqualTo(c);
        assertThat(codec.encode(c).requireInt(VersionedCodec.VERSION_KEY)).isEqualTo(3);
    }

    @Test
    void migratesOldDataStepByStepWithoutTouchingTheInput() {
        DataMap old = v1().encode(new Crop(42, 0));
        DataMap before = old.copy();
        Crop c = v3().decode(old);
        assertThat(c).isEqualTo(new Crop(42, 0));
        assertThat(old).isEqualTo(before);
        // and the old bytes too
        assertThat(v3().fromBytes(v1().toBytes(new Crop(7, 0)))).isEqualTo(new Crop(7, 0));
        // re-encoding writes the current layout
        assertThat(v3().encode(c).keys()).containsExactly("v", "plantedTick", "stage");
    }

    @Test
    void refusesNewerData() {
        DataMap newer = v3().encode(new Crop(1, 2));
        assertThatThrownBy(() -> v1().decode(newer)).isInstanceOf(PersistException.NewerVersion.class)
                .hasMessageContaining("newer");
    }

    @Test
    void reportsMissingVersionAndBadFields() {
        assertThatThrownBy(() -> v3().decode(new DataMap().putLong("plantedTick", 1)))
                .isInstanceOf(PersistException.class).hasMessageContaining("missing key 'v'");
        assertThatThrownBy(() -> v3().decode(new DataMap().putInt("v", 3).putString("plantedTick", "x").putInt("stage", 1)))
                .isInstanceOf(PersistException.class).hasMessageContaining("plantedTick");
        assertThatThrownBy(() -> v3().decode(new DataMap().putInt("v", 0)))
                .isInstanceOf(PersistException.class);
    }

    @Test
    void builderRequiresEveryMigration() {
        assertThatThrownBy(() -> VersionedCodec.<Crop>builder("crop", 3).migrate(1, d -> { })
                .build((c, o) -> { }, in -> null)).isInstanceOf(IllegalStateException.class).hasMessageContaining("2 to 3");
        assertThatThrownBy(() -> VersionedCodec.<Crop>builder("crop", 2).migrate(2, d -> { }))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void binaryFormCoversEveryTypeAndIsStrict() {
        DataMap m = new DataMap().putInt("i", -5).putLong("l", Long.MIN_VALUE).putFloat("f", 0.25f)
                .putDouble("d", Math.PI).putBoolean("b", true).putString("s", "çğıöşü İ")
                .putBytes("bytes", new byte[] {1, 2, 3})
                .putMap("m", new DataMap().putInt("x", 1))
                .putList("list", List.of(new DataMap().putString("a", "b"), new DataMap()));
        byte[] blob = DataMapBinary.encode(m);
        assertThat(DataMapBinary.decode(blob)).isEqualTo(m);

        // every truncation fails cleanly (crash mid-write of a raw blob)
        for (int n = 0; n < blob.length; n++) {
            byte[] cut = Arrays.copyOf(blob, n);
            assertThatThrownBy(() -> DataMapBinary.decode(cut)).isInstanceOf(PersistException.class);
        }
        byte[] trailing = Arrays.copyOf(blob, blob.length + 1);
        assertThatThrownBy(() -> DataMapBinary.decode(trailing)).hasMessageContaining("trailing");
        byte[] badMagic = blob.clone();
        badMagic[0] = 'X';
        assertThatThrownBy(() -> DataMapBinary.decode(badMagic)).hasMessageContaining("magic");
        byte[] badType = DataMapBinary.encode(new DataMap().putInt("k", 1));
        badType[9] = 99; // first entry's type tag: magic(4) format(1) count(4)
        assertThatThrownBy(() -> DataMapBinary.decode(badType)).hasMessageContaining("unknown type tag 99");
    }

    @Test
    void dataMapGettersAreTyped() {
        DataMap m = new DataMap().putInt("i", 1);
        assertThat(m.getInt("missing", 9)).isEqualTo(9);
        assertThatThrownBy(() -> m.getLong("i", 0)).isInstanceOf(PersistException.class);
        assertThat(m.rename("i", "j").keys()).containsExactly("j");
        byte[] b = {1};
        m.putBytes("b", b);
        b[0] = 2;
        assertThat(m.getBytes("b", null)).containsExactly(1);
    }
}
