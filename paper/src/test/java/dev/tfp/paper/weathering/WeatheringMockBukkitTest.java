/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.weathering;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.clock.GlobalClockHolder;
import dev.tfp.core.weathering.WeatheringRules.Age;
import dev.tfp.core.world.BlockPos;
import dev.tfp.paper.blockdata.PdcChunkDataStore;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.FakeCustomBlocks;
import dev.tfp.paper.testing.MockServerTest;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * PAR-METAL-17 on MockBukkit with fakes of the CraftEngine adapter: a block weathers over the calendar time that passed
 * (a copper block over two years reaches the last stage and leaves the index), a short window changes nothing, other
 * metals and the grate stay, properties carry over, a stale decision is dropped, and large changes are written in hops.
 */
class WeatheringMockBukkitTest extends MockServerTest {
    /** A year of TFC's default calendar: 12 months of 8 days. */
    private static final long YEAR = 12L * 8 * 24_000;

    private final FakeCustomBlocks blocks = new FakeCustomBlocks();
    private final TestScheduler scheduler = new TestScheduler();
    private final GlobalClockHolder clock = new GlobalClockHolder();
    private final WeatheringChunk chunks = new WeatheringChunk(new PdcChunkDataStore());
    private World world;
    private WeatheringService service;

    @BeforeEach
    void setUp() {
        for (String metal : new String[] {"copper", "steel", "bismuth_bronze"}) {
            for (String folder : new String[] {"block", "exposed_block", "weathered_block", "oxidized_block"}) {
                blocks.known("tfp:metal/" + folder + "/" + metal, "tfp:metal/" + folder + "/" + metal + "_slab");
            }
        }
        blocks.known("tfp:metal/grate/copper");
        world = server.addSimpleWorld("weathering");
        world.getChunkAt(0, 0);
        clock.publish(0L, 0L, 8, true);
        service = new WeatheringService(scheduler, blocks, (b, next) -> blocks.place(b, next, false), chunks, clock,
            (w, p, c, wind) -> 1f, 3);
    }

    private Block place(int x, int y, int z, String id) {
        Block b = world.getBlockAt(x, y, z);
        blocks.place(b, BlockStateRef.parse(id), false);
        service.track(b);
        return b;
    }

    private String id(Block b) {
        return blocks.stateAt(b).orElseThrow().id();
    }

    @Test
    void aCopperBlockWeathersToTheLastStageOverTwoYearsAndLeavesTheIndex() {
        Block b = place(1, 70, 1, "tfp:metal/block/copper");
        assertThat(chunks.positions(world.getChunkAt(0, 0))).hasSize(1);
        clock.publish(0L, 2 * YEAR, 8, true);
        service.pass(world.getChunkAt(0, 0), 1L);
        assertThat(id(b)).isEqualTo("tfp:metal/oxidized_block/copper");
        assertThat(chunks.positions(world.getChunkAt(0, 0))).isEmpty();
    }

    @Test
    void aShortWindowChangesNothingAndTheSweepWaits() {
        Block b = place(1, 70, 1, "tfp:metal/block/copper");
        clock.publish(0L, 20L, 8, true);
        service.pass(world.getChunkAt(0, 0), 1L);
        assertThat(id(b)).isEqualTo("tfp:metal/block/copper");
        clock.publish(0L, WeatheringService.MIN_WINDOW - 1, 8, true);
        service.pass(world.getChunkAt(0, 0), WeatheringService.MIN_WINDOW);
        assertThat(chunks.read(world.getChunkAt(0, 0)).orElseThrow().stamp()).as("the sweep did not restamp").isEqualTo(20L);
    }

    @Test
    void propertiesCarryOver() {
        Block slab = place(2, 70, 2, "tfp:metal/block/copper_slab[type=top,waterlogged=false]");
        clock.publish(0L, 2 * YEAR, 8, true);
        service.pass(world.getChunkAt(0, 0), 1L);
        BlockStateRef now = blocks.stateAt(slab).orElseThrow();
        assertThat(now.id()).isEqualTo("tfp:metal/oxidized_block/copper_slab");
        assertThat(now.properties()).containsEntry("type", "top").containsEntry("waterlogged", "false");
    }

    /** Resistance 0.9 (steel) weathers ten times slower than 0.0 (copper): over 40 blocks each the counts are far apart. */
    @Test
    void aResistantMetalWeathersLessOften() {
        List<Block> copper = new ArrayList<>();
        List<Block> steel = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            copper.add(place(i % 8 * 2, 70 + i / 8 * 6, 0, "tfp:metal/block/copper"));
            steel.add(place(i % 8 * 2, 70 + i / 8 * 6, 8, "tfp:metal/block/steel"));
        }
        clock.publish(0L, 100_000L, 8, true);
        service.pass(world.getChunkAt(0, 0), 1L);
        long copperMoved = copper.stream().filter(b -> !id(b).equals("tfp:metal/block/copper")).count();
        long steelMoved = steel.stream().filter(b -> !id(b).equals("tfp:metal/block/steel")).count();
        assertThat(copperMoved).as("copper").isGreaterThan(steelMoved + 10);
        assertThat(steelMoved).as("steel").isLessThan(20);
    }

    @Test
    void metalsThatDoNotWeatherAndGratesAreNeverListed() {
        Block grate = world.getBlockAt(3, 70, 3);
        blocks.place(grate, BlockStateRef.of("tfp:metal/grate/copper"), false);
        Block bismuth = world.getBlockAt(4, 70, 4);
        blocks.place(bismuth, BlockStateRef.of("tfp:metal/block/bismuth_bronze"), false);
        assertThat(service.weathers(grate)).isFalse();
        assertThat(service.weathers(bismuth)).isFalse();
        assertThat(service.weathers(world.getBlockAt(9, 70, 9))).isFalse();
    }

    @Test
    void aStaleDecisionIsDropped() {
        Block b = place(1, 70, 1, "tfp:metal/block/copper");
        blocks.place(b, BlockStateRef.of("tfp:metal/exposed_block/copper"), false); // changed since the decision
        service.apply(world, 0, 0, List.of(new WeatheringService.Change(new BlockPos(1, 70, 1), "tfp:metal/block/copper",
            "tfp:metal/weathered_block/copper", Age.WEATHERED)), 0);
        assertThat(id(b)).isEqualTo("tfp:metal/exposed_block/copper");
    }

    @Test
    void manyChangesAreWrittenInHops() {
        List<WeatheringService.Change> changes = new ArrayList<>();
        List<Block> bs = new ArrayList<>();
        for (int i = 0; i < WeatheringService.BATCH * 2 + 1; i++) {
            Block b = world.getBlockAt(i % 16, 70 + i / 16, 0);
            blocks.place(b, BlockStateRef.of("tfp:metal/block/copper"), false);
            bs.add(b);
            changes.add(new WeatheringService.Change(new BlockPos(b.getX(), b.getY(), b.getZ()), "tfp:metal/block/copper",
                "tfp:metal/exposed_block/copper", Age.EXPOSED));
        }
        service.apply(world, 0, 0, changes, 0);
        assertThat(scheduler.delays).as("two more hops").containsExactly(1L, 1L);
        assertThat(bs).allSatisfy(b -> assertThat(id(b)).isEqualTo("tfp:metal/exposed_block/copper"));
    }

    @Test
    void anUnloadedChunkCatchesUpOnItsNextLoad() {
        Block b = place(1, 70, 1, "tfp:metal/block/copper");
        service.onUnload(world.getChunkAt(0, 0));
        assertThat(service.trackedChunks(world)).isZero();
        clock.publish(0L, 2 * YEAR, 8, true);
        service.onLoad(world.getChunkAt(0, 0));
        assertThat(id(b)).isEqualTo("tfp:metal/oxidized_block/copper");
    }

    @Test
    void aBlockPlacedLaterDoesNotInheritTheTimeBeforeIt() {
        Block first = place(1, 70, 1, "tfp:metal/block/copper");
        clock.publish(0L, 2 * YEAR, 8, true);
        Block second = place(2, 70, 2, "tfp:metal/block/copper"); // the chunk settles first: the first block weathers
        assertThat(id(first)).isEqualTo("tfp:metal/oxidized_block/copper");
        assertThat(id(second)).isEqualTo("tfp:metal/block/copper");
        service.pass(world.getChunkAt(0, 0), 1L);
        assertThat(id(second)).isEqualTo("tfp:metal/block/copper");
    }

    @Test
    void theSweepGivesEachLoadedTrackedChunkItsPass() {
        Block b = place(1, 70, 1, "tfp:metal/block/copper");
        clock.publish(0L, 2 * YEAR, 8, true);
        service.sweep(java.util.Map.of(world.getUID(), world));
        assertThat(id(b)).isEqualTo("tfp:metal/oxidized_block/copper");
    }

    @Test
    void theDrawsAreAFixedFunctionOfBlockAndTime() {
        assertThat(WeatheringService.draws(7L, new BlockPos(1, 2, 3), 99L)).containsExactly(
            WeatheringService.draws(7L, new BlockPos(1, 2, 3), 99L));
        assertThat(WeatheringService.draws(7L, new BlockPos(1, 2, 3), 99L)).isNotEqualTo(
            WeatheringService.draws(7L, new BlockPos(1, 2, 4), 99L));
    }
}
