/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.weather;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.weather.SnowCatchUp;
import dev.tfp.core.world.BlockPos;
import dev.tfp.paper.testing.MockServerTest;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;

/** The chunk-time record round trip in the chunk's persistent data, and the catch-up schedule on a fake clock (M2-WTH-04). */
class ChunkSnowTimeMockBukkitTest extends MockServerTest {
    @Test
    void theStampRoundTripsThroughTheChunkAndNeverRemovesTheEntry() {
        World world = server.addSimpleWorld("tfp_snow_time");
        PersistentDataContainer chunkData = world.getChunkAt(2, 3).getPersistentDataContainer();
        assertThat(ChunkSnowTime.read(chunkData)).isEqualTo(ChunkSnowTime.never());

        new ChunkSnowTime(123_456L).write(chunkData);
        assertThat(ChunkSnowTime.read(chunkData)).isEqualTo(new ChunkSnowTime(123_456L));

        ChunkSnowTime.never().write(chunkData);
        assertThat(chunkData.getKeys()).isEmpty();
        assertThat(ChunkSnowTime.read(chunkData).lastCalendarTick()).isEqualTo(SnowCatchUp.NEVER);
    }

    @Test
    void aNegativeEntryReadsAsNeverUpdated() {
        World world = server.addSimpleWorld("tfp_snow_time_neg");
        PersistentDataContainer chunkData = world.getChunkAt(0, 0).getPersistentDataContainer();
        chunkData.set(NamespacedKey.fromString(ChunkSnowTime.KEY), PersistentDataType.LONG, -5L);
        assertThat(ChunkSnowTime.read(chunkData)).isEqualTo(ChunkSnowTime.never());
    }

    @Test
    void theScheduleCatchesUpOnlyWhenTheStampIsOld() {
        World world = server.addSimpleWorld("tfp_snow_schedule");
        PersistentDataContainer chunkData = world.getChunkAt(4, 4).getPersistentDataContainer();
        long[] clock = {1_000_000L}; // fake clock
        SnowCatchUpSchedule schedule = new SnowCatchUpSchedule(() -> clock[0]);

        // A chunk never updated is due, and is stamped now.
        assertThat(schedule.onLoad(chunkData)).isEqualTo(new SnowCatchUpSchedule.Load(true, SnowCatchUp.NEVER, 1_000_000L));
        assertThat(ChunkSnowTime.read(chunkData)).isEqualTo(new ChunkSnowTime(1_000_000L));

        // Unloaded at 1 010 000, reloaded 4 000 later: not due (the threshold is strict), stamped again.
        clock[0] = 1_010_000L;
        schedule.onUnload(chunkData);
        clock[0] = 1_014_000L;
        assertThat(schedule.onLoad(chunkData).catchUp()).isFalse();
        assertThat(ChunkSnowTime.read(chunkData)).isEqualTo(new ChunkSnowTime(1_014_000L));

        // Unloaded at 1 014 000, reloaded 4 001 later: due, from the unload stamp.
        clock[0] = 1_018_001L;
        assertThat(schedule.onLoad(chunkData)).isEqualTo(new SnowCatchUpSchedule.Load(true, 1_014_000L, 1_018_001L));
    }

    @Test
    void theLiveTimeBetweenLoadAndUnloadIsNotCaughtUpTwice() {
        World world = server.addSimpleWorld("tfp_snow_live");
        PersistentDataContainer chunkData = world.getChunkAt(6, 6).getPersistentDataContainer();
        long[] clock = {500_000L};
        SnowCatchUpSchedule schedule = new SnowCatchUpSchedule(() -> clock[0]);
        schedule.onLoad(chunkData);
        clock[0] = 900_000L; // loaded for a long time; the live samples cover it, and the unload stamp takes it over
        schedule.onUnload(chunkData);
        clock[0] = 900_500L;
        assertThat(schedule.onLoad(chunkData).catchUp()).isFalse();
    }

    @Test
    void thePileCountIsTheTrackedPilesOfTheChunk() {
        World world = server.addSimpleWorld("tfp_snow_piles");
        WorldBlocks blocks = new WorldBlocks(world);
        assertThat(blocks.pileCount(0, 0)).isZero();
        BlockPos pos = new BlockPos(3, 70, 5);
        BlockData fern = Material.FERN.createBlockData();
        blocks.write(pos, new SnowBlock(Material.SNOW.createBlockData(), new SnowBlock.Pile(fern, null), null));
        assertThat(blocks.pileCount(0, 0)).isEqualTo(1);
    }
}
