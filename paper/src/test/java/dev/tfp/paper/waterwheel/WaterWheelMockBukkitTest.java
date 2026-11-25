/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.waterwheel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.tfp.core.power.ShaftAxis;
import dev.tfp.core.power.WaterWheel;
import dev.tfp.core.waterwheel.WaterWheelSample;
import dev.tfp.core.world.BlockPos;
import dev.tfp.core.world.Direction;
import dev.tfp.core.worldgen.river.Flow;
import dev.tfp.core.worldwater.WaterKind;
import dev.tfp.paper.blockdata.PdcChunkDataStore;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.FakeCustomBlocks;
import dev.tfp.paper.testing.MockServerTest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * PAR-MECH-03 on MockBukkit with a fake CraftEngine adapter and a fake window: a wheel in a river reaches TFC's top speed
 * and reports it to the network on its axis; a solid block stops it; an unreadable window keeps the target; a broken wheel
 * stops and leaves the network; the chunk index restarts wheels.
 */
class WaterWheelMockBukkitTest extends MockServerTest {
    private final FakeCustomBlocks blocks = new FakeCustomBlocks().known("tfp:wood/water_wheel/oak");
    private final InlineScheduler scheduler = new InlineScheduler();
    private final Map<String, WaterWheel.Cell> cells = new HashMap<>();
    private final Map<String, WaterWheelSample> stored = new HashMap<>();
    private final List<String> reports = new ArrayList<>();
    private WaterWheelService service;
    private World world;

    private final WaterWheelNetwork network = new WaterWheelNetwork() {
        @Override
        public void source(UUID w, BlockPos pos, ShaftAxis axis, float speed) {
            reports.add("source " + pos.x() + "," + pos.y() + "," + pos.z() + " " + axis + " " + speed);
        }

        @Override
        public void remove(UUID w, BlockPos pos) {
            reports.add("remove " + pos.x() + "," + pos.y() + "," + pos.z());
        }
    };

    private static String key(Block b) {
        return b.getX() + "," + b.getY() + "," + b.getZ();
    }

    @BeforeEach
    void setUp() {
        world = server.addSimpleWorld("wheel");
        world.getChunkAt(0, 0);
        WaterWheelService.Records records = new WaterWheelService.Records() {
            @Override
            public WaterWheelSample load(Block block) {
                return stored.getOrDefault(key(block), WaterWheelSample.FRESH);
            }

            @Override
            public void save(Block block, WaterWheelSample next) {
                stored.put(key(block), next);
            }
        };
        WaterWheelCells.Reader reader = b -> cells.containsKey(key(b)) ? Optional.ofNullable(cells.get(key(b)))
            : Optional.of(WaterWheel.Cell.of(WaterWheel.Kind.OPEN));
        service = new WaterWheelService(scheduler, blocks, reader, records, () -> network);
    }

    private Block wheel(int x, int y, int z, String axis) {
        Block b = world.getBlockAt(x, y, z);
        blocks.place(b, BlockStateRef.of("tfp:wood/water_wheel/oak").with(WaterWheelBlocks.AXIS, axis), false);
        return b;
    }

    /** River water flowing {@code flow} in the bottom two rows of the window of an X wheel (the z plane) at (8, 64, 8). */
    private void riverUnder(Flow flow) {
        for (int dz = -2; dz <= 2; dz++) {
            for (int dy = -2; dy < 0; dy++) {
                cells.put(8 + "," + (64 + dy) + "," + (8 + dz), WaterWheel.Cell.river(flow));
            }
        }
    }

    private void samples(int n) {
        for (int i = 0; i < n; i++) {
            scheduler.repeating.get(0).run();
        }
    }

    /** The done criterion's wheel side: in a full river it ramps to TFC's MAX_SPEED (-2 pi / 80 for a NNN flow on X). */
    @Test
    void aWheelInARiverReachesTopSpeedAndReportsIt() {
        Block b = wheel(8, 64, 8, "x");
        riverUnder(Flow.NNN);
        service.start(b);
        assertThat(service.running()).isEqualTo(1);
        assertThat(scheduler.repeating).hasSize(1);
        samples(1);
        assertThat(stored.get("8,64,8").target()).isCloseTo(-WaterWheel.MAX_SPEED, within(1e-7f));
        samples(10);
        assertThat(stored.get("8,64,8").speed()).isCloseTo(-WaterWheel.MAX_SPEED, within(1e-6f));
        assertThat(service.reported(world.getUID(), new BlockPos(8, 64, 8))).isCloseTo(-WaterWheel.MAX_SPEED, within(1e-6f));
        assertThat(reports).last().asString().startsWith("source 8,64,8 X -0.0785");
        // at full speed nothing changes: no new report
        int before = reports.size();
        samples(3);
        assertThat(reports).hasSize(before);
    }

    /** The window lies in the wheel's plane: a river in the x plane does not drive an X wheel. */
    @Test
    void theWindowFollowsTheAxis() {
        for (int dx = -2; dx <= 2; dx++) {
            cells.put((8 + dx) + ",62,8", WaterWheel.Cell.river(Flow.NNN));
        }
        cells.remove("8,62,8");
        Block b = wheel(8, 64, 8, "x");
        service.start(b);
        samples(1);
        assertThat(stored.getOrDefault("8,64,8", WaterWheelSample.FRESH).target()).isCloseTo(0f, within(1e-9f));
        // the same river drives a Z wheel (TFC: flowRate = vector.x; NNN has x = 0, so use an east flow)
        for (int dx = -2; dx <= 2; dx++) {
            cells.put((8 + dx) + ",62,8", WaterWheel.Cell.river(Flow.EEE));
        }
        blocks.place(b, BlockStateRef.of("tfp:wood/water_wheel/oak").with(WaterWheelBlocks.AXIS, "z"), false);
        samples(1);
        assertThat(stored.get("8,64,8").target()).isNegative();
    }

    /** TFC: any solid block in the window stops the wheel at once and keeps its target. */
    @Test
    void aSolidBlockStopsTheWheel() {
        Block b = wheel(8, 64, 8, "x");
        riverUnder(Flow.NNN);
        service.start(b);
        samples(6);
        assertThat(stored.get("8,64,8").speed()).isNegative();
        cells.put("8,66,9", WaterWheel.Cell.of(WaterWheel.Kind.SOLID));
        samples(1);
        assertThat(stored.get("8,64,8").obstructed()).isTrue();
        samples(1);
        assertThat(stored.get("8,64,8").speed()).isZero();
        assertThat(reports).last().asString().endsWith(" 0.0");
    }

    /** A cell that cannot be read (unloaded chunk, river flow not cached yet) skips the sample: only the ramp runs. */
    @Test
    void anUnreadableWindowKeepsTheTarget() {
        Block b = wheel(8, 64, 8, "x");
        riverUnder(Flow.NNN);
        service.start(b);
        samples(1);
        cells.put("8,65,6", null);
        cells.put("8,63,6", WaterWheel.Cell.of(WaterWheel.Kind.SOLID)); // would obstruct, but the sample is skipped
        samples(2);
        WaterWheelSample s = stored.get("8,64,8");
        assertThat(s.obstructed()).isFalse();
        assertThat(s.target()).isCloseTo(-WaterWheel.MAX_SPEED, within(1e-7f));
        assertThat(s.speed()).isCloseTo(-WaterWheel.MAX_SPEED * 0.2f, within(1e-6f));
    }

    /** A wheel that is gone stops at its next sample and leaves the network (TFC REMOVE). */
    @Test
    void aBrokenWheelStopsAndLeavesTheNetwork() {
        Block b = wheel(8, 64, 8, "x");
        service.start(b);
        blocks.remove(b);
        samples(1);
        assertThat(service.running()).isZero();
        assertThat(reports).containsExactly("remove 8,64,8");
    }

    /** The chunk index lists placed wheels; a chunk load starts them and drops stale entries. */
    @Test
    void theChunkIndexRestartsWheels() {
        WaterWheelChunkIndex index = new WaterWheelChunkIndex(new PdcChunkDataStore());
        Block b = wheel(8, 64, 8, "z");
        Block gone = world.getBlockAt(9, 64, 8);
        index.track(b);
        index.track(gone);
        WaterWheelModule.chunkLoaded(b.getChunk(), blocks, index, service);
        assertThat(service.running()).isEqualTo(1);
        assertThat(index.positions(b.getChunk())).containsExactly(new BlockPos(8, 64, 8));
    }

    /** TFC calculateFlowRateAndObstruction's classes from the world water service's answers. */
    @Test
    void cellsClassifyAsTfc() {
        assertThat(WaterWheelCells.classify(true, Optional.of(WaterKind.RIVER), Optional.of(Flow.SSS), true))
            .contains(WaterWheel.Cell.river(Flow.SSS));
        assertThat(WaterWheelCells.classify(true, Optional.of(WaterKind.FRESH), Optional.of(Flow.NONE), true))
            .contains(WaterWheel.Cell.of(WaterWheel.Kind.STILL_WATER));
        assertThat(WaterWheelCells.classify(true, Optional.of(WaterKind.SALT), Optional.of(Flow.NONE), true))
            .contains(WaterWheel.Cell.of(WaterWheel.Kind.OPEN));
        assertThat(WaterWheelCells.classify(true, Optional.empty(), Optional.empty(), true)).isEmpty();
        assertThat(WaterWheelCells.classify(true, Optional.of(WaterKind.RIVER), Optional.empty(), true)).isEmpty();
        assertThat(WaterWheelCells.classify(false, Optional.empty(), Optional.empty(), true))
            .contains(WaterWheel.Cell.of(WaterWheel.Kind.OPEN));
        assertThat(WaterWheelCells.classify(false, Optional.empty(), Optional.empty(), false))
            .contains(WaterWheel.Cell.of(WaterWheel.Kind.SOLID));
    }

    /** TFC getStateForPlacement: the axis of the player's horizontal facing; the record codec round-trips. */
    @Test
    void placementAxisAndCodec() {
        assertThat(WaterWheelBlocks.placementAxis(WaterWheelBlocks.horizontal(-90f))).isEqualTo(ShaftAxis.X);
        assertThat(WaterWheelBlocks.horizontal(-90f)).isEqualTo(Direction.EAST);
        assertThat(WaterWheelBlocks.placementAxis(WaterWheelBlocks.horizontal(0f))).isEqualTo(ShaftAxis.Z);
        WaterWheelSample s = new WaterWheelSample(-0.01f, -0.07f, true);
        assertThat(WaterWheelBlocks.CODEC.fromBytes(WaterWheelBlocks.CODEC.toBytes(s))).isEqualTo(s);
        assertThat(WaterWheelBlocks.axis(BlockStateRef.of("tfp:wood/axle/oak").with("axis", "x"))).isEmpty();
    }
}
