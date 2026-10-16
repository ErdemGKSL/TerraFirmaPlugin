/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.integration.craftengine;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.persist.DataMap;
import dev.tfp.core.persist.VersionedCodec;
import java.util.List;
import net.momirealms.craftengine.libraries.nbt.CompoundTag;
import net.momirealms.craftengine.libraries.nbt.NBT;
import net.momirealms.craftengine.libraries.nbt.StringTag;
import org.junit.jupiter.api.Test;

/**
 * M1-SVC-01: the CraftEngine block-entity controller base and its NBT codec, without a server. "Restart"
 * = save into a block-entity tag, serialise it with CraftEngine's NBT writer, read it back into a new
 * controller. The in-server unload/reload/restart self-test is M1-TEST-01.
 */
class TfpBlockEntityControllerTest {
    record Crop(long plantedTick, int stage) {
    }

    static VersionedCodec<Crop> codecV1() {
        return VersionedCodec.<Crop>builder("test_crop", 1)
                .build((c, out) -> out.putInt("planted", (int) c.plantedTick()), in -> new Crop(in.requireInt("planted"), 0));
    }

    static VersionedCodec<Crop> codecV2() {
        return VersionedCodec.<Crop>builder("test_crop", 2)
                .migrate(1, d -> d.putLong("plantedTick", d.requireInt("planted")).putInt("stage", 0).remove("planted"))
                .build((c, out) -> out.putLong("plantedTick", c.plantedTick()).putInt("stage", c.stage()),
                        in -> new Crop(in.requireLong("plantedTick"), in.requireInt("stage")));
    }

    static final class CropController extends TfpBlockEntityController<Crop> {
        CropController(VersionedCodec<Crop> codec) {
            super(null, "test_crop", codec, new Crop(Long.MIN_VALUE, 0));
        }

        void plant(long tick, int stage) {
            setData(new Crop(tick, stage));
        }
    }

    /** Save → bytes (CraftEngine's NBT writer) → tag, as a chunk save + server restart does. */
    static CompoundTag restart(CompoundTag tag) {
        return NBT.readCompressed(NBT.writeCompressed(tag, false), false);
    }

    @Test
    void saveAndLoadRoundTripThroughNbtBytes() {
        CropController a = new CropController(codecV2());
        a.plant(123_456_789_000L, 3);
        CompoundTag be = new CompoundTag();
        be.putString("id", "tfp:test_crop"); // CraftEngine's own keys share the tag
        a.saveCustomData(be);
        assertThat(be.keySet()).contains("tfp:test_crop", "id");

        CropController b = new CropController(codecV2());
        b.loadCustomData(restart(be));
        assertThat(b.data()).isEqualTo(new Crop(123_456_789_000L, 3));
        assertThat(b.hasUnreadableData()).isFalse();
    }

    @Test
    void blockWithoutSavedDataKeepsInitialValue() {
        CropController c = new CropController(codecV2());
        c.loadCustomData(new CompoundTag());
        assertThat(c.data()).isEqualTo(new Crop(Long.MIN_VALUE, 0));
    }

    @Test
    void oldVersionIsMigratedOnLoadAndSavedInTheNewLayout() {
        CropController old = new CropController(codecV1());
        old.plant(42, 0);
        CompoundTag be = new CompoundTag();
        old.saveCustomData(be);

        CropController upgraded = new CropController(codecV2());
        upgraded.loadCustomData(restart(be));
        assertThat(upgraded.data()).isEqualTo(new Crop(42, 0));
        CompoundTag again = new CompoundTag();
        upgraded.saveCustomData(again);
        CompoundTag ours = again.getCompound("tfp:test_crop");
        assertThat(ours.getInt("v")).isEqualTo(2);
        assertThat(ours.getLong("plantedTick")).isEqualTo(42L);
        assertThat(ours.containsKey("planted")).isFalse();
    }

    @Test
    void newerOrCorruptDataIsPreservedVerbatimUntilChangedInGame() {
        CropController newer = new CropController(codecV2());
        newer.plant(7, 1);
        CompoundTag fromNewer = new CompoundTag();
        newer.saveCustomData(fromNewer);

        // A downgraded server (v1 code) loads v2 data: it must not overwrite it with defaults.
        CropController downgraded = new CropController(codecV1());
        downgraded.loadCustomData(restart(fromNewer));
        assertThat(downgraded.hasUnreadableData()).isTrue();
        assertThat(downgraded.data()).isEqualTo(new Crop(Long.MIN_VALUE, 0));
        CompoundTag saved = new CompoundTag();
        downgraded.saveCustomData(saved);
        assertThat(saved.get("tfp:test_crop")).isEqualTo(fromNewer.get("tfp:test_crop"));

        // Corrupt (wrong tag type) is kept too.
        CropController corrupt = new CropController(codecV2());
        CompoundTag bad = new CompoundTag();
        bad.put("tfp:test_crop", new StringTag("garbage"));
        corrupt.loadCustomData(bad);
        CompoundTag kept = new CompoundTag();
        corrupt.saveCustomData(kept);
        assertThat(kept.get("tfp:test_crop")).isEqualTo(new StringTag("garbage"));

        // Once the player changes the block, the new value replaces the preserved data.
        corrupt.plant(99, 2);
        CompoundTag replaced = new CompoundTag();
        corrupt.saveCustomData(replaced);
        assertThat(replaced.getCompound("tfp:test_crop").getLong("plantedTick")).isEqualTo(99L);
    }

    @Test
    void nbtMappingCoversEveryDataMapType() {
        DataMap m = new DataMap().putInt("i", 1).putLong("l", 2L).putFloat("f", 3f).putDouble("d", 4d)
                .putBoolean("b", true).putString("s", "x").putBytes("bytes", new byte[] {5, 6})
                .putMap("m", new DataMap().putInt("n", 7))
                .putList("list", List.of(new DataMap().putString("a", "b"), new DataMap().putInt("c", 8)));
        assertThat(NbtDataMaps.fromNbt(restart(NbtDataMaps.toNbt(m)))).isEqualTo(m);
    }
}
